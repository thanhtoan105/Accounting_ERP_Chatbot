package com.accounting.service.impl.sales;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.OutputVATReportDTO;
import com.accounting.entity.Customer;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.entity.VATReportHistory;
import com.accounting.entity.VatRate;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.VATReportHistoryRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ARVATReportService;
import com.accounting.service.AuditService;
import com.accounting.service.PeriodManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Implementation of ARVATReportService for output VAT report generation and export.
 * Handles ND123-compliant output VAT reports for sales invoices.
 */
@Service
@Transactional(readOnly = true)
public class ARVATReportServiceImpl implements ARVATReportService {

  private static final Logger logger = LoggerFactory.getLogger(ARVATReportServiceImpl.class);
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
  private static final String OUTPUT_VAT_SHEET_NAME = "Output VAT Report";
  private static final String ALL_CUSTOMERS_LABEL = "All Customers";
  private static final String ALL_VAT_CLASSES_LABEL = "All VAT Classes";

  private final SalesInvoiceRepository salesInvoiceRepository;
  private final SalesInvoiceLineRepository salesInvoiceLineRepository;
  private final CustomerRepository customerRepository;
  private final VATReportHistoryRepository vatReportHistoryRepository;
  private final PeriodManagementService periodManagementService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  @Autowired
  public ARVATReportServiceImpl(
      SalesInvoiceRepository salesInvoiceRepository,
      SalesInvoiceLineRepository salesInvoiceLineRepository,
      CustomerRepository customerRepository,
      VATReportHistoryRepository vatReportHistoryRepository,
      PeriodManagementService periodManagementService,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.salesInvoiceRepository = salesInvoiceRepository;
    this.salesInvoiceLineRepository = salesInvoiceLineRepository;
    this.customerRepository = customerRepository;
    this.vatReportHistoryRepository = vatReportHistoryRepository;
    this.periodManagementService = periodManagementService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public OutputVATReportDTO generateVATReport(
      UUID periodId, Long customerId, String vatClass, Map<String, Object> filters) {
    Long companyId = requireCompanyId();
    Map<String, Object> safeFilters = copyFilters(filters);
    VATReportHistory.ExportFormat exportFormat = resolveExportFormat(safeFilters);

    ReportComputation computation = buildReportData(companyId, periodId, customerId, vatClass, safeFilters);

    OutputVATReportDTO report = computation.report();
    report.setFormat(exportFormat);

    VATReportHistory history = buildReportHistory(
        null, // Don't set ID for new entities - let Hibernate generate it
        companyId,
        periodId,
        customerId,
        vatClass,
        computation.dateRange(),
        exportFormat,
        report);
    persistReportHistory(history);

    // Set the generated ID on the report DTO
    report.setReportId(history.getId());

    Map<String, Object> auditFilters = buildAuditFilters(periodId, customerId, vatClass, computation.dateRange(),
        safeFilters);
    auditService.logVatReportGenerated(
        companyId,
        getCurrentUserId(),
        history.getId(),
        VATReportHistory.ReportType.OUTPUT_VAT.name(),
        auditFilters);

    return report;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public byte[] exportVATReport(UUID reportId, String format) {
    Long companyId = requireCompanyId();
    VATReportHistory history = vatReportHistoryRepository
        .findByCompanyIdAndId(companyId, reportId)
        .orElseThrow(() -> new IllegalArgumentException("VAT report not found: " + reportId));

    VATReportHistory.ExportFormat exportFormat = parseExportFormat(format, history.getFormat());

    Map<String, Object> filters = new HashMap<>();
    filters.put("startDate", history.getStartDate());
    filters.put("endDate", history.getEndDate());

    ReportComputation computation = buildReportData(
        companyId, history.getPeriodId(), history.getCustomerId(), history.getVatClass(), filters);

    OutputVATReportDTO report = computation.report();
    report.setReportId(history.getId());
    report.setFormat(exportFormat);
    report.setGenerationDate(history.getGenerationDate());

    byte[] exported = exportFormat == VATReportHistory.ExportFormat.PDF
        ? exportReportToPdf(report, computation.dateRange(), history.getHash())
        : exportReportToExcel(report, computation.dateRange(), history.getHash());

    int currentDownloads = history.getDownloadCount() != null ? history.getDownloadCount() : 0;
    history.setDownloadCount(currentDownloads + 1);
    persistReportHistory(history);

    auditService.logReportExport(
        companyId, getCurrentUserId(), exportFormat.name(), getCurrentRequest());

    return exported;
  }

  private DateRange resolveDateRange(UUID periodId, Map<String, Object> filters) {
    LocalDate startDate = parseLocalDate(filters.get("startDate"));
    LocalDate endDate = parseLocalDate(filters.get("endDate"));

    if ((startDate == null || endDate == null) && periodId != null) {
      Optional<AccountingPeriodDTO> periodOpt = periodManagementService.getPeriodById(periodId);
      if (periodOpt.isEmpty()) {
        throw new IllegalArgumentException("Accounting period not found: " + periodId);
      }
      AccountingPeriodDTO period = periodOpt.get();
      startDate = period.getStartDate();
      endDate = period.getEndDate();
    }

    if (startDate == null || endDate == null) {
      LocalDate now = LocalDate.now();
      startDate = now.withDayOfMonth(1);
      endDate = now.withDayOfMonth(now.lengthOfMonth());
    }

    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("End date cannot be before start date");
    }

    return new DateRange(startDate, endDate);
  }

  private VATReportHistory.ExportFormat resolveExportFormat(Map<String, Object> filters) {
    Object formatObj = filters.get("format");
    if (formatObj instanceof VATReportHistory.ExportFormat exportFormat) {
      return exportFormat;
    }
    if (formatObj instanceof String formatStr && StringUtils.hasText(formatStr)) {
      try {
        return VATReportHistory.ExportFormat.valueOf(formatStr.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
        logger.warn("Unsupported VAT report format '{}', defaulting to EXCEL", formatStr);
      }
    }
    return VATReportHistory.ExportFormat.EXCEL;
  }

  private VATReportHistory.ExportFormat parseExportFormat(
      String requestedFormat, VATReportHistory.ExportFormat fallback) {
    if (StringUtils.hasText(requestedFormat)) {
      try {
        return VATReportHistory.ExportFormat.valueOf(requestedFormat.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        logger.warn(
            "Unsupported VAT report export format '{}', falling back to {}",
            requestedFormat,
            fallback != null ? fallback : VATReportHistory.ExportFormat.EXCEL);
      }
    }
    return fallback != null ? fallback : VATReportHistory.ExportFormat.EXCEL;
  }

  private ReportComputation buildReportData(
      Long companyId, UUID periodId, Long customerId, String vatClass, Map<String, Object> filters) {
    Map<String, Object> safeFilters = copyFilters(filters);
    DateRange dateRange = resolveDateRange(periodId, safeFilters);
    if (dateRange.start() == null || dateRange.end() == null) {
      throw new IllegalArgumentException(
          "Either accounting period or explicit start/end dates must be provided");
    }

    List<SalesInvoice> invoices = salesInvoiceRepository.findAll(
        buildReportSpecification(companyId, customerId, dateRange.start(), dateRange.end()));

    Map<UUID, List<SalesInvoiceLine>> linesByInvoice = loadLinesByInvoice(companyId, invoices);
    OutputVATReportDTO report = initializeReportDTO(
        companyId, periodId, customerId, vatClass, dateRange, VATReportHistory.ExportFormat.EXCEL);

    boolean filterByVatClass = StringUtils.hasText(vatClass);
    String normalizedVatClass = filterByVatClass ? vatClass.trim().toUpperCase() : null;

    for (SalesInvoice invoice : invoices) {
      List<SalesInvoiceLine> invoiceLines = linesByInvoice.getOrDefault(invoice.getId(), Collections.emptyList());
      Customer customer = resolveCustomer(invoice);

      for (SalesInvoiceLine line : invoiceLines) {
        if (filterByVatClass && !matchesVatClass(line, normalizedVatClass)) {
          continue;
        }
        OutputVATReportDTO.ReportLineItemDTO lineItem = toReportLineItem(line, invoice, customer);
        report.getItems().add(lineItem);

        VatRate vatRate = line.getVatRate() != null ? line.getVatRate() : VatRate.ZERO;
        BigDecimal vatAmount = safe(line.getVatAmount());
        BigDecimal revenueAmount = safe(line.getAmount());
        BigDecimal grossAmount = revenueAmount.add(vatAmount);

        // Aggregate by VAT rate
        report.getTotalVATByRate().merge(vatRate, vatAmount, BigDecimal::add);
        report.setGrandTotalVAT(report.getGrandTotalVAT().add(vatAmount));
        report.setGrandTotalAmount(report.getGrandTotalAmount().add(grossAmount));

        // Aggregate revenue by VAT rate (for ND123 format)
        if (vatRate == VatRate.ZERO) {
          report.setRevenue0pct(report.getRevenue0pct().add(revenueAmount));
        } else if (vatRate == VatRate.FIVE) {
          report.setRevenue5pct(report.getRevenue5pct().add(revenueAmount));
        } else if (vatRate == VatRate.TEN) {
          report.setRevenue10pct(report.getRevenue10pct().add(revenueAmount));
        } else if (vatRate == VatRate.EXEMPT) {
          report.setRevenueExempt(report.getRevenueExempt().add(revenueAmount));
        }
      }
    }

    // Set total VAT collected
    report.setTotalVatCollected(report.getGrandTotalVAT());

    return new ReportComputation(report, dateRange);
  }

  private Specification<SalesInvoice> buildReportSpecification(
      Long companyId, Long customerId, LocalDate startDate, LocalDate endDate) {
    return (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("companyId"), companyId));
      predicates.add(cb.equal(root.get("status"), SalesInvoiceStatus.POSTED));
      predicates.add(cb.between(root.get("invoiceDate"), startDate, endDate));
      if (customerId != null) {
        predicates.add(cb.equal(root.get("customerId"), customerId));
      }
      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
  }

  private Map<UUID, List<SalesInvoiceLine>> loadLinesByInvoice(
      Long companyId, List<SalesInvoice> invoices) {
    if (invoices.isEmpty()) {
      return Collections.emptyMap();
    }
    List<UUID> invoiceIds = invoices.stream().map(SalesInvoice::getId).toList();
    List<SalesInvoiceLine> lines = salesInvoiceLineRepository
        .findByCompanyIdAndSalesInvoiceIdInOrderBySalesInvoiceIdAscLineNumberAsc(companyId, invoiceIds);
    Map<UUID, List<SalesInvoiceLine>> grouped = new HashMap<>();
    for (SalesInvoiceLine line : lines) {
      grouped.computeIfAbsent(line.getSalesInvoiceId(), id -> new ArrayList<>()).add(line);
    }
    return grouped;
  }

  private byte[] exportReportToExcel(
      OutputVATReportDTO report, DateRange dateRange, String hashValue) {
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet(OUTPUT_VAT_SHEET_NAME);

      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);

      int rowNum = 0;
      Row titleRow = sheet.createRow(rowNum++);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue("OUTPUT VAT REPORT (ND123)");
      titleCell.setCellStyle(headerStyle);

      rowNum++;
      rowNum = createInfoRow(sheet, rowNum, "Company ID:", String.valueOf(report.getCompanyId()));
      rowNum = createInfoRow(sheet, rowNum, "Period:", formatDateRange(dateRange.start(), dateRange.end()));
      rowNum = createInfoRow(sheet, rowNum, "Customer:", formatReportCustomerLabel(report));
      rowNum = createInfoRow(sheet, rowNum, "VAT Class:", formatVatClass(report.getVatClass()));

      rowNum++;
      String[] headers = {
          "Invoice Number", "Invoice Date", "Customer Name", "Customer Tax Code",
          "Revenue (0%)", "Revenue (5%)", "Revenue (10%)", "Revenue (Exempt)", "Total VAT Collected"
      };
      Row headerRow = sheet.createRow(rowNum++);
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      for (OutputVATReportDTO.ReportLineItemDTO item : report.getItems()) {
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(
            item.getInvoiceNumber() != null ? item.getInvoiceNumber() : "-");
        row.createCell(1).setCellValue(formatDate(item.getInvoiceDate()));
        row.createCell(2).setCellValue(
            item.getCustomerName() != null ? item.getCustomerName() : "-");
        row.createCell(3).setCellValue(
            item.getCustomerTaxCode() != null ? item.getCustomerTaxCode() : "-");
        row.createCell(4).setCellValue(formatCurrency(item.getRevenue0pct()));
        row.createCell(5).setCellValue(formatCurrency(item.getRevenue5pct()));
        row.createCell(6).setCellValue(formatCurrency(item.getRevenue10pct()));
        row.createCell(7).setCellValue(formatCurrency(item.getRevenueExempt()));
        row.createCell(8).setCellValue(formatCurrency(item.getVatAmount()));
      }

      rowNum++;
      Row summaryHeader = sheet.createRow(rowNum++);
      Cell summaryCell = summaryHeader.createCell(0);
      summaryCell.setCellValue("Summary Totals");
      summaryCell.setCellStyle(headerStyle);

      createInfoRow(sheet, rowNum++, "Revenue (0%):", formatCurrency(report.getRevenue0pct()));
      createInfoRow(sheet, rowNum++, "Revenue (5%):", formatCurrency(report.getRevenue5pct()));
      createInfoRow(sheet, rowNum++, "Revenue (10%):", formatCurrency(report.getRevenue10pct()));
      createInfoRow(sheet, rowNum++, "Revenue (Exempt):", formatCurrency(report.getRevenueExempt()));
      createInfoRow(sheet, rowNum++, "Total VAT Collected:", formatCurrency(report.getTotalVatCollected()));

      rowNum++;
      String footer = "Generated: "
          + formatTimestamp(report.getGenerationDate())
          + " | Hash: "
          + (StringUtils.hasText(hashValue) ? hashValue : calculateReportHash(report));
      createInfoRow(sheet, rowNum, footer, "");

      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
      }

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to export Output VAT report to Excel", e);
    }
  }

  private byte[] exportReportToPdf(
      OutputVATReportDTO report, DateRange dateRange, String hashValue) {
    StringBuilder pdf = new StringBuilder();
    pdf.append("OUTPUT VAT REPORT (ND123)\n\n");
    pdf.append("Company ID: ").append(report.getCompanyId()).append("\n");
    pdf.append("Period: ")
        .append(formatDateRange(dateRange.start(), dateRange.end()))
        .append("\n");
    pdf.append("Customer: ").append(formatReportCustomerLabel(report)).append("\n");
    pdf.append("VAT Class: ").append(formatVatClass(report.getVatClass())).append("\n\n");

    pdf.append(
        String.format(
            "%-20s %-12s %-30s %-15s %15s %15s %15s %15s %15s%n",
            "Invoice #",
            "Date",
            "Customer",
            "Tax Code",
            "Rev (0%)",
            "Rev (5%)",
            "Rev (10%)",
            "Rev (Exempt)",
            "VAT"));
    pdf.append("-".repeat(150)).append("\n");

    for (OutputVATReportDTO.ReportLineItemDTO item : report.getItems()) {
      pdf.append(
          String.format(
              "%-20s %-12s %-30s %-15s %15s %15s %15s %15s %15s%n",
              truncate(item.getInvoiceNumber(), 20),
              formatDate(item.getInvoiceDate()),
              truncate(item.getCustomerName(), 30),
              truncate(item.getCustomerTaxCode(), 15),
              formatCurrency(item.getRevenue0pct()),
              formatCurrency(item.getRevenue5pct()),
              formatCurrency(item.getRevenue10pct()),
              formatCurrency(item.getRevenueExempt()),
              formatCurrency(item.getVatAmount())));
    }

    pdf.append("\nSummary Totals:\n");
    pdf.append(" - Revenue (0%): ").append(formatCurrency(report.getRevenue0pct())).append("\n");
    pdf.append(" - Revenue (5%): ").append(formatCurrency(report.getRevenue5pct())).append("\n");
    pdf.append(" - Revenue (10%): ").append(formatCurrency(report.getRevenue10pct())).append("\n");
    pdf.append(" - Revenue (Exempt): ").append(formatCurrency(report.getRevenueExempt())).append("\n");
    pdf.append(" - Total VAT Collected: ")
        .append(formatCurrency(report.getTotalVatCollected()))
        .append("\n\n");

    pdf.append("Generated: ")
        .append(formatTimestamp(report.getGenerationDate()))
        .append(" | Hash: ")
        .append(StringUtils.hasText(hashValue) ? hashValue : calculateReportHash(report))
        .append("\n");

    return pdf.toString().getBytes(StandardCharsets.UTF_8);
  }

  private int createInfoRow(Sheet sheet, int rowNum, String label, String value) {
    Row row = sheet.createRow(rowNum);
    row.createCell(0).setCellValue(label);
    row.createCell(1).setCellValue(value != null ? value : "");
    return rowNum + 1;
  }

  private String formatReportCustomerLabel(OutputVATReportDTO report) {
    if (StringUtils.hasText(report.getCustomerName())) {
      if (StringUtils.hasText(report.getCustomerTaxCode())) {
        return report.getCustomerName() + " (" + report.getCustomerTaxCode() + ")";
      }
      return report.getCustomerName();
    }
    if (report.getCustomerId() != null) {
      return "Customer #" + report.getCustomerId();
    }
    return ALL_CUSTOMERS_LABEL;
  }

  private String formatVatClass(String vatClass) {
    return StringUtils.hasText(vatClass) ? vatClass : ALL_VAT_CLASSES_LABEL;
  }

  private String formatDate(LocalDate date) {
    return date != null ? DATE_FORMATTER.format(date) : "-";
  }

  private String formatDateRange(LocalDate start, LocalDate end) {
    return formatDate(start) + " - " + formatDate(end);
  }

  private String formatTimestamp(Instant instant) {
    Instant value = instant != null ? instant : Instant.now();
    return TIMESTAMP_FORMATTER.format(value.atZone(DEFAULT_ZONE));
  }

  private String formatCurrency(BigDecimal amount) {
    DecimalFormat formatter = currencyFormatter();
    return formatter.format(amount != null ? amount : BigDecimal.ZERO);
  }

  private DecimalFormat currencyFormatter() {
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("vi", "VN"));
    symbols.setDecimalSeparator('.');
    symbols.setGroupingSeparator(',');
    DecimalFormat decimalFormat = new DecimalFormat("#,##0.00₫", symbols);
    decimalFormat.setRoundingMode(java.math.RoundingMode.HALF_UP);
    return decimalFormat;
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value != null ? value : "";
    }
    return value.substring(0, Math.max(0, maxLength - 3)) + "...";
  }

  private Map<String, Object> copyFilters(Map<String, Object> filters) {
    if (filters == null || filters.isEmpty()) {
      return new HashMap<>();
    }
    return new HashMap<>(filters);
  }

  private OutputVATReportDTO initializeReportDTO(
      Long companyId,
      UUID periodId,
      Long customerId,
      String vatClass,
      DateRange dateRange,
      VATReportHistory.ExportFormat format) {
    OutputVATReportDTO dto = new OutputVATReportDTO();
    dto.setCompanyId(companyId);
    dto.setPeriodId(periodId);
    dto.setCustomerId(customerId);
    dto.setVatClass(vatClass);
    dto.setStartDate(dateRange.start());
    dto.setEndDate(dateRange.end());
    dto.setGenerationDate(Instant.now());
    dto.setGeneratedByName(getCurrentUsername());
    dto.setFormat(format);
    dto.setGrandTotalVAT(BigDecimal.ZERO);
    dto.setGrandTotalAmount(BigDecimal.ZERO);
    dto.setTotalVATByRate(new EnumMap<>(VatRate.class));
    dto.setReportId(UUID.randomUUID());
    dto.setRevenue0pct(BigDecimal.ZERO);
    dto.setRevenue5pct(BigDecimal.ZERO);
    dto.setRevenue10pct(BigDecimal.ZERO);
    dto.setRevenueExempt(BigDecimal.ZERO);
    dto.setTotalVatCollected(BigDecimal.ZERO);

    if (customerId != null) {
      customerRepository
          .findByCompanyIdAndId(companyId, customerId)
          .ifPresent(
              customer -> {
                dto.setCustomerName(customer.getName());
                dto.setCustomerTaxCode(customer.getTaxCode());
              });
    }

    return dto;
  }

  private Customer resolveCustomer(SalesInvoice invoice) {
    if (invoice.getCustomer() != null) {
      return invoice.getCustomer();
    }
    Long customerId = invoice.getCustomerId();
    if (customerId == null) {
      return null;
    }
    return customerRepository
        .findByCompanyIdAndId(invoice.getCompanyId(), customerId)
        .orElse(null);
  }

  private boolean matchesVatClass(SalesInvoiceLine line, String normalizedVatClass) {
    if (!StringUtils.hasText(normalizedVatClass)) {
      return true;
    }
    VatRate rate = line.getVatRate();
    if (rate == null) {
      return false;
    }
    return rate.name().equalsIgnoreCase(normalizedVatClass)
        || rate.getDisplayName().equalsIgnoreCase(normalizedVatClass);
  }

  private OutputVATReportDTO.ReportLineItemDTO toReportLineItem(
      SalesInvoiceLine line, SalesInvoice invoice, Customer customer) {
    OutputVATReportDTO.ReportLineItemDTO lineItem = new OutputVATReportDTO.ReportLineItemDTO();
    lineItem.setInvoiceId(invoice.getId());
    lineItem.setInvoiceNumber(invoice.getInvoiceNumber());
    lineItem.setInvoiceDate(invoice.getInvoiceDate());
    lineItem.setCustomerId(invoice.getCustomerId());
    if (customer != null) {
      lineItem.setCustomerName(customer.getName());
      lineItem.setCustomerTaxCode(customer.getTaxCode());
    }
    lineItem.setVatRate(line.getVatRate());
    BigDecimal revenueAmount = safe(line.getAmount());
    BigDecimal vatAmount = safe(line.getVatAmount());
    lineItem.setBaseAmount(revenueAmount);
    lineItem.setVatAmount(vatAmount);
    lineItem.setTotalAmount(revenueAmount.add(vatAmount));

    // Set revenue by VAT rate (for ND123 format)
    VatRate vatRate = line.getVatRate() != null ? line.getVatRate() : VatRate.ZERO;
    if (vatRate == VatRate.ZERO) {
      lineItem.setRevenue0pct(revenueAmount);
    } else if (vatRate == VatRate.FIVE) {
      lineItem.setRevenue5pct(revenueAmount);
    } else if (vatRate == VatRate.TEN) {
      lineItem.setRevenue10pct(revenueAmount);
    } else if (vatRate == VatRate.EXEMPT) {
      lineItem.setRevenueExempt(revenueAmount);
    }

    return lineItem;
  }

  private VATReportHistory buildReportHistory(
      UUID reportId,
      Long companyId,
      UUID periodId,
      Long customerId,
      String vatClass,
      DateRange dateRange,
      VATReportHistory.ExportFormat format,
      OutputVATReportDTO report) {
    VATReportHistory history = new VATReportHistory();
    if (reportId != null) {
      history.setId(reportId);
    }
    history.setCompanyId(companyId);
    history.setReportType(VATReportHistory.ReportType.OUTPUT_VAT);
    history.setPeriodId(periodId);
    history.setCustomerId(customerId); // For OUTPUT_VAT reports
    history.setVatClass(vatClass);
    history.setGenerationDate(report.getGenerationDate());
    history.setGeneratedBy(getCurrentUserId());
    history.setFormat(format);
    history.setStartDate(dateRange.start());
    history.setEndDate(dateRange.end());
    history.setHash(calculateReportHash(report));
    return history;
  }

  @SuppressWarnings("null")
  private void persistReportHistory(VATReportHistory history) {
    if (history.getId() != null) {
      vatReportHistoryRepository.save(history);
    } else {
      VATReportHistory saved = vatReportHistoryRepository.save(history);
      history.setId(saved.getId());
    }
  }

  private String calculateReportHash(OutputVATReportDTO dto) {
    try {
      byte[] payload = objectMapper.writeValueAsBytes(dto);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(payload);
      return bytesToHex(hashed);
    } catch (Exception e) {
      logger.warn("Failed to calculate VAT report hash: {}", e.getMessage());
      return UUID.randomUUID().toString().replace("-", "");
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private Map<String, Object> buildAuditFilters(
      UUID periodId,
      Long customerId,
      String vatClass,
      DateRange dateRange,
      Map<String, Object> originalFilters) {
    Map<String, Object> auditFilters = new HashMap<>();
    if (periodId != null) {
      auditFilters.put("periodId", periodId);
    }
    if (customerId != null) {
      auditFilters.put("customerId", customerId);
    }
    if (StringUtils.hasText(vatClass)) {
      auditFilters.put("vatClass", vatClass);
    }
    auditFilters.put("startDate", dateRange.start());
    auditFilters.put("endDate", dateRange.end());
    if (originalFilters != null && !originalFilters.isEmpty()) {
      auditFilters.put("rawFilters", originalFilters);
    }
    return auditFilters;
  }

  private Long getCurrentUserId() {
    try {
      return SecurityUtils.getCurrentUserId();
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      logger.warn("Failed to get current user ID: {}", e.getMessage());
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Unable to determine current user for VAT report generation", e);
    }
  }

  private String getCurrentUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      return authentication.getName();
    }
    return "System";
  }

  private LocalDate parseLocalDate(Object value) {
    if (value instanceof LocalDate localDate) {
      return localDate;
    }
    if (value instanceof java.sql.Date sqlDate) {
      return sqlDate.toLocalDate();
    }
    if (value instanceof String text && StringUtils.hasText(text)) {
      return LocalDate.parse(text.trim());
    }
    return null;
  }

  private BigDecimal safe(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private Long requireCompanyId() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }
    return companyId;
  }

  private HttpServletRequest getCurrentRequest() {
    try {
      ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      return attributes != null ? attributes.getRequest() : null;
    } catch (Exception e) {
      logger.debug("Could not get current request from context", e);
      return null;
    }
  }

  private record DateRange(LocalDate start, LocalDate end) {
  }

  private record ReportComputation(OutputVATReportDTO report, DateRange dateRange) {
  }
}
