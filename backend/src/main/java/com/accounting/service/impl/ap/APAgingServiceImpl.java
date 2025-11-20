package com.accounting.service.impl.ap;

import com.accounting.dto.AgingBillDetailsDTO;
import com.accounting.dto.APAgingBucketDTO;
import com.accounting.dto.APAgingReportDTO;
import com.accounting.dto.OverdueCountDTO;
import com.accounting.dto.OverdueSupplierDTO;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.Supplier;
import com.accounting.entity.PaymentAllocation;
import com.accounting.repository.APPaymentRepository;
import com.accounting.repository.PaymentAllocationRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.APAgingService;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of APAgingService for AP aging report operations.
 * Handles aging bucket calculation, overdue detection, and drill-down functionality.
 */
@Service
@Transactional(readOnly = true)
public class APAgingServiceImpl implements APAgingService {

  private static final Logger logger = LoggerFactory.getLogger(APAgingServiceImpl.class);

  private final PurchaseBillRepository purchaseBillRepository;
  private final PaymentAllocationRepository allocationRepository;
  private final APPaymentRepository paymentRepository;
  private final SupplierRepository supplierRepository;

  public APAgingServiceImpl(
      PurchaseBillRepository purchaseBillRepository,
      PaymentAllocationRepository allocationRepository,
      APPaymentRepository paymentRepository,
      SupplierRepository supplierRepository) {
    this.purchaseBillRepository = purchaseBillRepository;
    this.allocationRepository = allocationRepository;
    this.paymentRepository = paymentRepository;
    this.supplierRepository = supplierRepository;
  }

  @Override
  @Cacheable(value = "ap-aging", key = "'ap-aging:' + (#supplierId != null ? #supplierId : 'all') + ':' + (#periodId != null ? #periodId : 'all') + ':' + (#asOfDate != null ? #asOfDate : T(java.time.LocalDate).now())")
  public APAgingBucketDTO calculateAgingBuckets(Long supplierId, LocalDate asOfDate, Long periodId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    // Get POSTED bills with remaining balance > 0
    List<PurchaseBill> bills = getPostedBillsWithRemainingBalance(companyId, supplierId);

    APAgingBucketDTO buckets = new APAgingBucketDTO();
    LocalDate currentDate = asOfDate != null ? asOfDate : LocalDate.now();

    for (PurchaseBill bill : bills) {
      BigDecimal remainingBalance = calculateRemainingBalance(bill.getId());
      if (remainingBalance.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }

      LocalDate dueDate = bill.getDueDate();

      // Calculate overdue days as number of days currentDate is after dueDate.
      // Positive values mean the bill is overdue; zero/negative means current or future.
      long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(dueDate, currentDate);

      if (!dueDate.isAfter(currentDate)) {
        // Bill is due today or already overdue
        long overdueDays = Math.max(daysDiff, 0);

        if (overdueDays == 0) {
          // Treat due-today bills as current
          buckets.setCurrent(buckets.getCurrent().add(remainingBalance));
        } else if (overdueDays <= 30) {
          buckets.setDays1To30(buckets.getDays1To30().add(remainingBalance));
        } else if (overdueDays <= 60) {
          buckets.setDays31To60(buckets.getDays31To60().add(remainingBalance));
        } else if (overdueDays <= 90) {
          buckets.setDays61To90(buckets.getDays61To90().add(remainingBalance));
        } else {
          buckets.setDaysOver90(buckets.getDaysOver90().add(remainingBalance));
        }
      } else {
        // Future-dated bill → Current bucket
        buckets.setCurrent(buckets.getCurrent().add(remainingBalance));
      }
    }

    // Calculate total
    BigDecimal total =
        buckets.getCurrent()
            .add(buckets.getDays1To30())
            .add(buckets.getDays31To60())
            .add(buckets.getDays61To90())
            .add(buckets.getDaysOver90());
    buckets.setTotal(total);

    return buckets;
  }

  @Override
  public Page<APAgingReportDTO> getAgingReport(
      Long supplierId,
      Long periodId,
      LocalDate asOfDate,
      String status,
      String bucket,
      Pageable pageable) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    LocalDate currentDate = asOfDate != null ? asOfDate : LocalDate.now();

    // Get all suppliers (filtered by RBAC)
    List<Supplier> suppliers = getSuppliersForCurrentUser(companyId);
    if (supplierId != null) {
      suppliers =
          suppliers.stream()
              .filter(s -> s.getId().equals(supplierId))
              .collect(Collectors.toList());
    }

    List<APAgingReportDTO> reportItems = new ArrayList<>();

    for (Supplier supplier : suppliers) {
      APAgingBucketDTO buckets = calculateAgingBuckets(supplier.getId(), currentDate, periodId);

      // Apply bucket filter if specified
      if (bucket != null && !bucket.isEmpty()) {
        BigDecimal bucketAmount = getBucketAmount(buckets, bucket);
        if (bucketAmount.compareTo(BigDecimal.ZERO) <= 0) {
          continue;
        }
      }

      APAgingReportDTO reportItem = new APAgingReportDTO();
      reportItem.setSupplierId(supplier.getId());
      reportItem.setSupplierName(supplier.getName());
      reportItem.setSupplierCode(supplier.getCode());
      reportItem.setBuckets(buckets);
      reportItem.setTotalOutstanding(buckets.getTotal());

      // Check if has overdue (any amount in 1-30d, 31-60d, 61-90d, or >90d buckets)
      boolean hasOverdue =
          buckets.getDays1To30().compareTo(BigDecimal.ZERO) > 0
              || buckets.getDays31To60().compareTo(BigDecimal.ZERO) > 0
              || buckets.getDays61To90().compareTo(BigDecimal.ZERO) > 0
              || buckets.getDaysOver90().compareTo(BigDecimal.ZERO) > 0;
      reportItem.setHasOverdue(hasOverdue);

      if (reportItem.getTotalOutstanding().compareTo(BigDecimal.ZERO) > 0) {
        reportItems.add(reportItem);
      }
    }

    // Apply pagination
    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), reportItems.size());
    List<APAgingReportDTO> pagedItems =
        start < reportItems.size() ? reportItems.subList(start, end) : new ArrayList<>();

    return new PageImpl<>(pagedItems, pageable, reportItems.size());
  }

  @Override
  public List<OverdueSupplierDTO> getOverdueSuppliers(
      Long periodId, LocalDate asOfDate, Integer limit) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    LocalDate currentDate = asOfDate != null ? asOfDate : LocalDate.now();
    List<Supplier> suppliers = getSuppliersForCurrentUser(companyId);

    List<OverdueSupplierDTO> overdueSuppliers = new ArrayList<>();

    for (Supplier supplier : suppliers) {
      APAgingBucketDTO buckets = calculateAgingBuckets(supplier.getId(), currentDate, periodId);

      // Calculate total overdue amount (1-30d + 31-60d + 61-90d + >90d)
      BigDecimal overdueAmount =
          buckets.getDays1To30()
              .add(buckets.getDays31To60())
              .add(buckets.getDays61To90())
              .add(buckets.getDaysOver90());

      if (overdueAmount.compareTo(BigDecimal.ZERO) > 0) {
        // Get oldest overdue bill to calculate overdue days
        List<PurchaseBill> bills =
            getPostedBillsWithRemainingBalance(companyId, supplier.getId());
        int maxOverdueDays = 0;

        for (PurchaseBill bill : bills) {
          BigDecimal remainingBalance = calculateRemainingBalance(bill.getId());
          if (remainingBalance.compareTo(BigDecimal.ZERO) > 0) {
            LocalDate dueDate = bill.getDueDate();
            if (dueDate.isBefore(currentDate)) {
              long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(dueDate, currentDate);
              maxOverdueDays = Math.max(maxOverdueDays, (int) daysDiff);
            }
          }
        }

        OverdueSupplierDTO overdue = new OverdueSupplierDTO();
        overdue.setSupplierId(supplier.getId());
        overdue.setSupplierName(supplier.getName());
        overdue.setOverdueAmount(overdueAmount);
        overdue.setOverdueDays(maxOverdueDays);
        overdueSuppliers.add(overdue);
      }
    }

    // Sort by overdue amount descending, then by overdue days descending
    overdueSuppliers.sort(
        Comparator.comparing(OverdueSupplierDTO::getOverdueAmount)
            .reversed()
            .thenComparing(OverdueSupplierDTO::getOverdueDays, Comparator.reverseOrder()));

    // Apply limit
    int maxResults = limit != null && limit > 0 ? limit : 5;
    return overdueSuppliers.stream().limit(maxResults).collect(Collectors.toList());
  }

  @Override
  public OverdueCountDTO getOverdueCount(Long periodId, LocalDate asOfDate) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    LocalDate currentDate = asOfDate != null ? asOfDate : LocalDate.now();
    List<Supplier> suppliers = getSuppliersForCurrentUser(companyId);

    long overdueCount = 0;

    for (Supplier supplier : suppliers) {
      APAgingBucketDTO buckets = calculateAgingBuckets(supplier.getId(), currentDate, periodId);

      // Count supplier as overdue if any amount in overdue buckets
      BigDecimal overdueAmount =
          buckets.getDays1To30()
              .add(buckets.getDays31To60())
              .add(buckets.getDays61To90())
              .add(buckets.getDaysOver90());

      if (overdueAmount.compareTo(BigDecimal.ZERO) > 0) {
        overdueCount++;
      }
    }

    return new OverdueCountDTO(overdueCount);
  }

  @Override
  public Page<AgingBillDetailsDTO> getAgingBillDetails(
      Long supplierId,
      String bucket,
      Long periodId,
      LocalDate asOfDate,
      String status,
      Pageable pageable) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    if (supplierId == null) {
      throw new IllegalArgumentException("Supplier ID is required for drill-down");
    }

    LocalDate currentDate = asOfDate != null ? asOfDate : LocalDate.now();

    // Get POSTED bills with remaining balance > 0 for supplier
    List<PurchaseBill> bills = getPostedBillsWithRemainingBalance(companyId, supplierId);

    List<AgingBillDetailsDTO> billDetails = new ArrayList<>();

    for (PurchaseBill bill : bills) {
      BigDecimal remainingBalance = calculateRemainingBalance(bill.getId());
      if (remainingBalance.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }

      LocalDate dueDate = bill.getDueDate();
      long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(dueDate, currentDate);

      // Determine which bucket this bill belongs to
      String billBucket = determineBucket(daysDiff, dueDate, currentDate);

      // Apply bucket filter
      if (bucket != null && !bucket.isEmpty() && !bucket.equals(billBucket)) {
        continue;
      }

      // Apply status filter
      if (status != null && !status.isEmpty() && !bill.getStatus().name().equals(status)) {
        continue;
      }

      AgingBillDetailsDTO detail = new AgingBillDetailsDTO();
      detail.setBillId(bill.getId());
      detail.setBillNumber(bill.getBillNumber());
      detail.setBillDate(bill.getBillDate());
      detail.setDueDate(bill.getDueDate());
      detail.setTotalAmount(bill.getTotalAmount());
      detail.setRemainingBalance(remainingBalance);
      detail.setStatus(bill.getStatus().name());
      detail.setReference(bill.getReference());

      // Get payment history
      List<PaymentAllocation> allocations = allocationRepository.findByPurchaseBillId(bill.getId());
      for (PaymentAllocation allocation : allocations) {
        AgingBillDetailsDTO.PaymentHistoryDTO paymentHistory =
            new AgingBillDetailsDTO.PaymentHistoryDTO();
        paymentHistory.setPaymentId(allocation.getPaymentId());
        paymentHistory.setAllocatedAmount(allocation.getAllocatedAmount());

        // Fetch payment details
        paymentRepository
            .findById(allocation.getPaymentId())
            .ifPresent(
                payment -> {
                  paymentHistory.setPaymentNumber(payment.getPaymentNumber());
                  paymentHistory.setPaymentDate(payment.getPaymentDate());
                  paymentHistory.setPaymentAmount(payment.getAmount());
                });

        detail.getPaymentHistory().add(paymentHistory);
      }

      billDetails.add(detail);
    }

    // Apply pagination
    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), billDetails.size());
    List<AgingBillDetailsDTO> pagedItems =
        start < billDetails.size() ? billDetails.subList(start, end) : new ArrayList<>();

    return new PageImpl<>(pagedItems, pageable, billDetails.size());
  }

  @Override
  public byte[] exportAgingReport(
      String format,
      Long supplierId,
      Long periodId,
      LocalDate asOfDate,
      String status,
      String bucket) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    LocalDate currentDate = asOfDate != null ? asOfDate : LocalDate.now();

    // Get aging report data (all suppliers, no pagination for export)
    Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
    Page<APAgingReportDTO> reportData =
        getAgingReport(supplierId, periodId, currentDate, status, bucket, pageable);

    if ("PDF".equalsIgnoreCase(format)) {
      return exportToPDF(reportData.getContent(), supplierId, periodId, currentDate, status, bucket);
    } else {
      // Default to Excel
      return exportToExcel(reportData.getContent(), supplierId, periodId, currentDate, status, bucket);
    }
  }

  /**
   * Export aging report to Excel format.
   */
  private byte[] exportToExcel(
      List<APAgingReportDTO> reportData,
      Long supplierId,
      Long periodId,
      LocalDate asOfDate,
      String status,
      String bucket) {
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("AP Aging Report");

      // Create styles
      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerFont.setFontHeightInPoints((short) 12);
      headerStyle.setFont(headerFont);

      CellStyle currencyStyle = workbook.createCellStyle();
      currencyStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0.00"));

      int rowNum = 0;

      // Title row
      Row titleRow = sheet.createRow(rowNum++);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue("AP Aging Report");
      titleCell.setCellStyle(headerStyle);

      // Report criteria row
      rowNum++;
      Row criteriaRow = sheet.createRow(rowNum++);
      criteriaRow.createCell(0).setCellValue("Report Date: " + asOfDate.format(DateTimeFormatter.ISO_DATE));
      if (supplierId != null) {
        criteriaRow.createCell(1).setCellValue("Supplier ID: " + supplierId);
      }
      if (periodId != null) {
        criteriaRow.createCell(2).setCellValue("Period ID: " + periodId);
      }
      if (status != null) {
        criteriaRow.createCell(3).setCellValue("Status: " + status);
      }
      if (bucket != null) {
        criteriaRow.createCell(4).setCellValue("Bucket: " + bucket);
      }

      rowNum++; // Empty row

      // Header row
      Row headerRow = sheet.createRow(rowNum++);
      String[] headers = {
        "Supplier Code", "Supplier Name", "Current", "1-30 Days", "31-60 Days", "61-90 Days",
        "Over 90 Days", "Total Outstanding", "Has Overdue"
      };
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data rows
      for (APAgingReportDTO item : reportData) {
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(item.getSupplierCode() != null ? item.getSupplierCode() : "");
        row.createCell(1).setCellValue(item.getSupplierName() != null ? item.getSupplierName() : "");

        APAgingBucketDTO buckets = item.getBuckets();
        Cell currentCell = row.createCell(2);
        currentCell.setCellValue(buckets.getCurrent().doubleValue());
        currentCell.setCellStyle(currencyStyle);

        Cell days1To30Cell = row.createCell(3);
        days1To30Cell.setCellValue(buckets.getDays1To30().doubleValue());
        days1To30Cell.setCellStyle(currencyStyle);

        Cell days31To60Cell = row.createCell(4);
        days31To60Cell.setCellValue(buckets.getDays31To60().doubleValue());
        days31To60Cell.setCellStyle(currencyStyle);

        Cell days61To90Cell = row.createCell(5);
        days61To90Cell.setCellValue(buckets.getDays61To90().doubleValue());
        days61To90Cell.setCellStyle(currencyStyle);

        Cell daysOver90Cell = row.createCell(6);
        daysOver90Cell.setCellValue(buckets.getDaysOver90().doubleValue());
        daysOver90Cell.setCellStyle(currencyStyle);

        Cell totalCell = row.createCell(7);
        totalCell.setCellValue(item.getTotalOutstanding().doubleValue());
        totalCell.setCellStyle(currencyStyle);

        row.createCell(8).setCellValue(item.isHasOverdue() ? "Yes" : "No");
      }

      // Auto-size columns
      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (Exception e) {
      logger.error("Failed to export aging report to Excel", e);
      throw new RuntimeException("Failed to export aging report to Excel: " + e.getMessage(), e);
    }
  }

  /**
   * Export aging report to PDF format.
   * <p>
   * For MVP, this generates a structured text-based document that can be
   * downloaded as a PDF file by the client. In production, this can be
   * replaced with a proper PDF engine (e.g. PDFBox, iText).
   */
  private byte[] exportToPDF(
      List<APAgingReportDTO> reportData,
      Long supplierId,
      Long periodId,
      LocalDate asOfDate,
      String status,
      String bucket) {
    StringBuilder sb = new StringBuilder();

    sb.append("========================================\n");
    sb.append("AP AGING REPORT\n");
    sb.append("========================================\n\n");

    sb.append("Report Date: ").append(asOfDate.format(DateTimeFormatter.ISO_DATE)).append("\n");
    if (supplierId != null) {
      sb.append("Supplier ID: ").append(supplierId).append("\n");
    }
    if (periodId != null) {
      sb.append("Period ID: ").append(periodId).append("\n");
    }
    if (status != null && !status.isBlank()) {
      sb.append("Status: ").append(status).append("\n");
    }
    if (bucket != null && !bucket.isBlank()) {
      sb.append("Bucket Filter: ").append(bucket).append("\n");
    }
    sb.append("Total Suppliers: ").append(reportData.size()).append("\n");
    sb.append("Generated At: ").append(java.time.ZonedDateTime.now()).append("\n");
    sb.append("========================================\n\n");

    for (APAgingReportDTO item : reportData) {
      sb.append("Supplier: ")
          .append(item.getSupplierCode() != null ? item.getSupplierCode() : "")
          .append(" - ")
          .append(item.getSupplierName() != null ? item.getSupplierName() : "")
          .append("\n");

      APAgingBucketDTO buckets = item.getBuckets();
      sb.append("  Current     : ").append(buckets.getCurrent()).append("\n");
      sb.append("  1-30 Days   : ").append(buckets.getDays1To30()).append("\n");
      sb.append("  31-60 Days  : ").append(buckets.getDays31To60()).append("\n");
      sb.append("  61-90 Days  : ").append(buckets.getDays61To90()).append("\n");
      sb.append("  Over 90 Days: ").append(buckets.getDaysOver90()).append("\n");
      sb.append("  TOTAL       : ").append(item.getTotalOutstanding()).append("\n");
      sb.append("  Has Overdue : ").append(item.isHasOverdue() ? "Yes" : "No").append("\n");
      sb.append("----------------------------------------\n");
    }

    return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Get POSTED bills with remaining balance > 0.
   */
  private List<PurchaseBill> getPostedBillsWithRemainingBalance(Long companyId, Long supplierId) {
    List<PurchaseBill> bills =
        purchaseBillRepository.findByCompanyIdAndStatus(companyId, PurchaseBillStatus.POSTED);

    if (supplierId != null) {
      bills =
          bills.stream()
              .filter(bill -> bill.getSupplierId().equals(supplierId))
              .collect(Collectors.toList());
    }

    // Filter bills with remaining balance > 0
    return bills.stream()
        .filter(
            bill -> {
              BigDecimal remainingBalance = calculateRemainingBalance(bill.getId());
              return remainingBalance.compareTo(BigDecimal.ZERO) > 0;
            })
        .collect(Collectors.toList());
  }

  /**
   * Calculate remaining balance for a purchase bill.
   */
  private BigDecimal calculateRemainingBalance(java.util.UUID billId) {
    BigDecimal totalAllocated = allocationRepository.calculateTotalAllocatedAmount(billId);
    PurchaseBill bill =
        purchaseBillRepository
            .findById(billId)
            .orElseThrow(() -> new IllegalStateException("Bill not found: " + billId));
    return bill.getTotalAmount().subtract(totalAllocated);
  }

  /**
   * Get suppliers for current user based on RBAC.
   * CFO and Chief Accountant see all suppliers.
   * AP Clerk sees all suppliers (supplier assignments not yet implemented).
   */
  private List<Supplier> getSuppliersForCurrentUser(Long companyId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return new ArrayList<>();
    }

    // For now, all authenticated users see all suppliers in their company
    // TODO: Implement supplier assignments for AP Clerk role
    // CFO and Chief Accountant see all suppliers
    // AP Clerk should be limited to assigned/own suppliers (when supplier assignments are implemented)
    return supplierRepository.findByCompanyId(companyId);
  }

  /**
   * Get bucket amount from buckets DTO.
   */
  private BigDecimal getBucketAmount(APAgingBucketDTO buckets, String bucket) {
    return switch (bucket.toUpperCase()) {
      case "CURRENT" -> buckets.getCurrent();
      case "DAYS_1_30", "1-30D" -> buckets.getDays1To30();
      case "DAYS_31_60", "31-60D" -> buckets.getDays31To60();
      case "DAYS_61_90", "61-90D" -> buckets.getDays61To90();
      case "DAYS_OVER_90", ">90D" -> buckets.getDaysOver90();
      default -> BigDecimal.ZERO;
    };
  }

  /**
   * Determine aging bucket for a bill based on days difference.
   */
  private String determineBucket(long daysDiff, LocalDate dueDate, LocalDate currentDate) {
    if (dueDate.isAfter(currentDate) || dueDate.isEqual(currentDate)) {
      return "CURRENT";
    } else if (daysDiff <= 30) {
      return "DAYS_1_30";
    } else if (daysDiff <= 60) {
      return "DAYS_31_60";
    } else if (daysDiff <= 90) {
      return "DAYS_61_90";
    } else {
      return "DAYS_OVER_90";
    }
  }

  /**
   * Invalidate aging cache when bill is posted or payment is made.
   */
  @CacheEvict(value = "ap-aging", allEntries = true)
  public void invalidateAgingCache() {
    logger.debug("Aging cache invalidated due to bill/payment change");
  }

  /**
   * Invalidate aging cache when an accounting period is closed.
   * <p>
   * This method is intended to be called from period close workflows to ensure
   * aging reports are recalculated for the next request.
   *
   * @param periodId closed period identifier (currently unused but included for auditability)
   */
  @CacheEvict(value = "ap-aging", allEntries = true)
  public void invalidateAgingCacheOnPeriodClose(java.util.UUID periodId) {
    logger.debug("Aging cache invalidated due to period close: {}", periodId);
  }
}

