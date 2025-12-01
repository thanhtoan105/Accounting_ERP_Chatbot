package com.accounting.service.impl;

import com.accounting.dto.ARAgingBucketDTO;
import com.accounting.dto.ARAgingDrillDownDTO;
import com.accounting.dto.ARAgingReportDTO;
import com.accounting.dto.ARInvoiceDetailDTO;
import com.accounting.entity.ARPayment;
import com.accounting.entity.Customer;
import com.accounting.entity.ReceiptAllocation;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.repository.ARPaymentRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.ReceiptAllocationRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ARAgingCalculationService;
import com.accounting.service.ARAgingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of ARAgingService with Redis caching.
 * Mirrors AP aging patterns but for customers and sales invoices.
 * Cache TTL: 1 hour (vs 5 minutes for AP aging).
 */
@Service
@Transactional(readOnly = true)
public class ARAgingServiceImpl implements ARAgingService {

    private static final Logger logger = LoggerFactory.getLogger(ARAgingServiceImpl.class);

    private final ARAgingCalculationService agingCalculationService;
    private final CustomerRepository customerRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final SalesInvoiceLineRepository salesInvoiceLineRepository;
    private final ARPaymentRepository arPaymentRepository;

    @Autowired
    public ARAgingServiceImpl(
            ARAgingCalculationService agingCalculationService,
            CustomerRepository customerRepository,
            SalesInvoiceRepository salesInvoiceRepository,
            ReceiptAllocationRepository receiptAllocationRepository,
            SalesInvoiceLineRepository salesInvoiceLineRepository,
            ARPaymentRepository arPaymentRepository) {
        this.agingCalculationService = agingCalculationService;
        this.customerRepository = customerRepository;
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.receiptAllocationRepository = receiptAllocationRepository;
        this.salesInvoiceLineRepository = salesInvoiceLineRepository;
        this.arPaymentRepository = arPaymentRepository;
    }

    @Override
    @Cacheable(value = "ar-aging", key = "'ar-aging:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':' + (#customerId != null ? #customerId : 'all') + ':' + (#asOfDate != null ? #asOfDate : T(java.time.LocalDate).now())")
    public Page<ARAgingReportDTO> getAgingReport(
            Long customerId,
            LocalDate asOfDate,
            String status,
            String bucket,
            Pageable pageable) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Missing company context");
        }

        LocalDate currentDate = asOfDate != null ? asOfDate : LocalDate.now();

        logger.debug(
                "Generating AR aging report for company {} customer {} as of {}",
                companyId,
                customerId,
                currentDate);

        // Get all customers (filtered by RBAC if needed)
        List<Customer> customers = getCustomersForCurrentUser(companyId);
        if (customerId != null) {
            customers = customers.stream()
                    .filter(c -> c.getId().equals(customerId))
                    .collect(Collectors.toList());
        }

        List<ARAgingReportDTO> reportItems = new ArrayList<>();

        for (Customer customer : customers) {
            try {
                ARAgingBucketDTO buckets = agingCalculationService.calculateAgingBuckets(customer.getId(), currentDate);
                
                if (buckets == null) {
                    logger.warn("Null buckets returned for customer {} on date {}", customer.getId(), currentDate);
                    continue;
                }

            // Apply bucket filter if specified
            if (bucket != null && !bucket.isEmpty()) {
                BigDecimal bucketAmount = getBucketAmount(buckets, bucket);
                if (bucketAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
            }

            // Skip customers with zero balance unless explicitly requested
            if (buckets.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            ARAgingReportDTO reportItem = new ARAgingReportDTO();
            reportItem.setCustomerId(customer.getId());
            reportItem.setCustomerName(customer.getName());
            reportItem.setCustomerCode(customer.getCode());
            reportItem.setBuckets(buckets);
            reportItem.setTotalOutstanding(buckets.getTotal());

            // Check if customer has overdue amounts
            boolean hasOverdue = buckets.getDays1To30().compareTo(BigDecimal.ZERO) > 0
                    || buckets.getDays31To60().compareTo(BigDecimal.ZERO) > 0
                    || buckets.getDays61To90().compareTo(BigDecimal.ZERO) > 0
                    || buckets.getDaysOver90().compareTo(BigDecimal.ZERO) > 0;
            reportItem.setHasOverdue(hasOverdue);

            reportItems.add(reportItem);
            } catch (Exception e) {
                logger.error("Error calculating aging buckets for customer {} on date {}", 
                    customer.getId(), currentDate, e);
                // Continue with next customer instead of failing entire report
            }
        }

        logger.debug(
                "AR aging report generated: {} customers with outstanding balances", reportItems.size());

        // Apply sorting if specified
        if (pageable.getSort().isSorted()) {
            Sort sort = pageable.getSort();
            for (Sort.Order order : sort) {
                String property = order.getProperty();
                Comparator<ARAgingReportDTO> comparator = getComparator(property);
                if (comparator != null) {
                    if (order.isDescending()) {
                        reportItems.sort(comparator.reversed());
                    } else {
                        reportItems.sort(comparator);
                    }
                    break; // Only apply first sort order
                }
            }
        }

        // Apply pagination (handle Unpaged case)
        if (pageable.isUnpaged()) {
            return new PageImpl<>(reportItems, pageable, reportItems.size());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), reportItems.size());
        List<ARAgingReportDTO> pageContent = start < reportItems.size() ? reportItems.subList(start, end)
                : new ArrayList<>();

        return new PageImpl<>(pageContent, pageable, reportItems.size());
    }

    @Override
    @Transactional
    public void refreshAgingCache(LocalDate asOfDate) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Missing company context");
        }

        LocalDate currentDate = asOfDate != null ? asOfDate : LocalDate.now();

        logger.info("Manually refreshing AR aging cache for company {} as of {}", companyId, currentDate);

        // Invalidate existing cache
        invalidateAgingCache();

        // Trigger recalculation by calling getAgingReport
        // This will populate the cache with fresh data
        getAgingReport(null, currentDate, null, null, Pageable.unpaged());

        logger.info("AR aging cache refreshed successfully");
    }

    @Override
    @CacheEvict(value = "ar-aging", allEntries = true)
    public void invalidateAgingCache() {
        logger.debug("AR aging cache invalidated due to invoice/receipt change");
    }

    /**
     * Get customers for the current user based on RBAC.
     * Currently returns all customers for the company.
     * TODO: Implement customer-level RBAC filtering for AR clerks.
     *
     * @param companyId company ID
     * @return list of customers
     */
    private List<Customer> getCustomersForCurrentUser(Long companyId) {
        // For now, return all customers for the company
        // In future, filter based on user role and customer assignments
        return customerRepository.findByCompanyId(companyId);
    }

    /**
     * Get comparator for sorting ARAgingReportDTO by property name.
     *
     * @param property property name to sort by
     * @return comparator or null if property not supported
     */
    private Comparator<ARAgingReportDTO> getComparator(String property) {
        return switch (property) {
            case "customerName" -> Comparator.comparing(ARAgingReportDTO::getCustomerName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "customerCode" -> Comparator.comparing(ARAgingReportDTO::getCustomerCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "totalOutstanding" -> Comparator.comparing(ARAgingReportDTO::getTotalOutstanding, Comparator.nullsLast(Comparator.naturalOrder()));
            case "buckets.current" -> Comparator.comparing(dto -> dto.getBuckets().getCurrent(), Comparator.nullsLast(Comparator.naturalOrder()));
            case "buckets.days1To30" -> Comparator.comparing(dto -> dto.getBuckets().getDays1To30(), Comparator.nullsLast(Comparator.naturalOrder()));
            case "buckets.days31To60" -> Comparator.comparing(dto -> dto.getBuckets().getDays31To60(), Comparator.nullsLast(Comparator.naturalOrder()));
            case "buckets.days61To90" -> Comparator.comparing(dto -> dto.getBuckets().getDays61To90(), Comparator.nullsLast(Comparator.naturalOrder()));
            case "buckets.daysOver90" -> Comparator.comparing(dto -> dto.getBuckets().getDaysOver90(), Comparator.nullsLast(Comparator.naturalOrder()));
            default -> {
                logger.warn("Unsupported sort property: {}, defaulting to customerName", property);
                yield Comparator.comparing(ARAgingReportDTO::getCustomerName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            }
        };
    }

    /**
     * Get amount for a specific aging bucket.
     *
     * @param buckets   aging buckets
     * @param bucketKey bucket key (CURRENT, DAYS_1_30, DAYS_31_60, DAYS_61_90,
     *                  DAYS_OVER_90)
     * @return bucket amount
     */
    private BigDecimal getBucketAmount(ARAgingBucketDTO buckets, String bucketKey) {
        return switch (bucketKey.toUpperCase()) {
            case "CURRENT" -> buckets.getCurrent();
            case "DAYS_1_30" -> buckets.getDays1To30();
            case "DAYS_31_60" -> buckets.getDays31To60();
            case "DAYS_61_90" -> buckets.getDays61To90();
            case "DAYS_OVER_90" -> buckets.getDaysOver90();
            default -> BigDecimal.ZERO;
        };
    }

    @Override
    public Page<ARAgingDrillDownDTO> getDrillDownDetail(
            Long customerId, String agingBucketKey, LocalDate asOfDate, Pageable pageable) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Missing company context");
        }

        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required for drill-down");
        }

        LocalDate currentDate = asOfDate != null ? asOfDate : LocalDate.now();

        logger.debug(
                "Getting drill-down detail for company {} customer {} bucket {} as of {}",
                companyId,
                customerId,
                agingBucketKey,
                currentDate);

        // Get open invoices for the customer
        List<SalesInvoice> openInvoices =
                salesInvoiceRepository.findOpenInvoicesByCustomerId(companyId, customerId);

        // Filter invoices by aging bucket
        List<SalesInvoice> filteredInvoices = new ArrayList<>();
        for (SalesInvoice invoice : openInvoices) {
            LocalDate dueDate = invoice.getDueDate();
            long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(dueDate, currentDate);
            long overdueDays = Math.max(daysDiff, 0);

            String invoiceBucket = determineAgingBucket(dueDate, currentDate, overdueDays);
            if (invoiceBucket.equals(agingBucketKey)) {
                filteredInvoices.add(invoice);
            }
        }

        // Get customer name
        Customer customer =
                customerRepository
                        .findByCompanyIdAndId(companyId, customerId)
                        .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        // Get last payment dates for invoices
        // Query allocations for each invoice (could be optimized with a custom repository method)
        List<UUID> invoiceIds =
                filteredInvoices.stream().map(SalesInvoice::getId).collect(Collectors.toList());
        List<ReceiptAllocation> allocations = new ArrayList<>();
        for (UUID invoiceId : invoiceIds) {
            allocations.addAll(receiptAllocationRepository.findBySalesInvoiceId(invoiceId));
        }

        // Map to DTOs
        List<ARAgingDrillDownDTO> drillDownItems = new ArrayList<>();
        for (SalesInvoice invoice : filteredInvoices) {
            ARAgingDrillDownDTO dto = new ARAgingDrillDownDTO();
            dto.setInvoiceId(invoice.getId());
            dto.setInvoiceNumber(invoice.getInvoiceNumber());
            dto.setInvoiceDate(invoice.getInvoiceDate());
            dto.setDueDate(invoice.getDueDate());
            dto.setTotalAmount(invoice.getTotalAmount());
            dto.setAmountPaid(invoice.getAmountPaid());
            dto.setOutstandingAmount(invoice.getRemainingBalance());
            dto.setStatus(invoice.getStatus().name());
            dto.setCustomerName(customer.getName());

            // Calculate days overdue
            long daysDiff =
                    java.time.temporal.ChronoUnit.DAYS.between(invoice.getDueDate(), currentDate);
            dto.setDaysOverdue(Math.max(daysDiff, 0));

            // Get last payment date from allocations
            List<ReceiptAllocation> invoiceAllocations =
                    allocations.stream()
                            .filter(alloc -> alloc.getSalesInvoiceId().equals(invoice.getId()))
                            .collect(Collectors.toList());

            LocalDate lastPaymentDate = null;
            if (!invoiceAllocations.isEmpty()) {
                List<UUID> receiptIds =
                        invoiceAllocations.stream()
                                .map(ReceiptAllocation::getReceiptId)
                                .distinct()
                                .collect(Collectors.toList());

                // Query ARPayment repository for these receipt IDs
                List<ARPayment> receipts =
                        receiptIds.stream()
                                .map(
                                        receiptId ->
                                                arPaymentRepository
                                                        .findByCompanyIdAndId(companyId, receiptId)
                                                        .orElse(null))
                                .filter(r -> r != null)
                                .collect(Collectors.toList());

                lastPaymentDate =
                        receipts.stream()
                                .map(ARPayment::getReceiptDate)
                                .max(Comparator.naturalOrder())
                                .orElse(null);
            }

            dto.setLastPaymentDate(lastPaymentDate);
            drillDownItems.add(dto);
        }

        // Apply pagination
        if (pageable.isUnpaged()) {
            return new PageImpl<>(drillDownItems, pageable, drillDownItems.size());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), drillDownItems.size());
        List<ARAgingDrillDownDTO> pageContent =
                start < drillDownItems.size() ? drillDownItems.subList(start, end) : new ArrayList<>();

        return new PageImpl<>(pageContent, pageable, drillDownItems.size());
    }

    @Override
    public ARInvoiceDetailDTO getInvoiceDetail(UUID invoiceId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Missing company context");
        }

        logger.debug("Getting invoice detail for company {} invoice {}", companyId, invoiceId);

        // Get invoice
        SalesInvoice invoice =
                salesInvoiceRepository
                        .findByCompanyIdAndId(companyId, invoiceId)
                        .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        // Get customer
        Customer customer =
                customerRepository
                        .findByCompanyIdAndId(companyId, invoice.getCustomerId())
                        .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        // Build DTO
        ARInvoiceDetailDTO dto = new ARInvoiceDetailDTO();
        dto.setInvoiceId(invoice.getId());
        dto.setInvoiceNumber(invoice.getInvoiceNumber());
        dto.setInvoiceDate(invoice.getInvoiceDate());
        dto.setDueDate(invoice.getDueDate());
        dto.setReference(invoice.getReference());
        dto.setDescription(invoice.getDescription());
        dto.setStatus(invoice.getStatus().name());
        dto.setCustomerId(customer.getId());
        dto.setCustomerName(customer.getName());
        dto.setCustomerCode(customer.getCode());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setVatAmount(invoice.getVatAmount());
        dto.setAmountPaid(invoice.getAmountPaid());
        dto.setRemainingBalance(invoice.getRemainingBalance());

        // Calculate days overdue and aging bucket
        LocalDate currentDate = LocalDate.now();
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(invoice.getDueDate(), currentDate);
        long overdueDays = Math.max(daysDiff, 0);
        dto.setDaysOverdue(overdueDays);
        dto.setAgingBucket(
                determineAgingBucket(invoice.getDueDate(), currentDate, overdueDays));

        // Get payment history
        List<ReceiptAllocation> allocations =
                receiptAllocationRepository.findBySalesInvoiceId(invoice.getId());

        List<ARInvoiceDetailDTO.PaymentHistoryItem> paymentHistory = new ArrayList<>();
        for (ReceiptAllocation allocation : allocations) {
            // Get receipt details from ARPaymentRepository
            Optional<ARPayment> receiptOpt =
                    arPaymentRepository.findByCompanyIdAndId(companyId, allocation.getReceiptId());

            if (receiptOpt.isPresent()) {
                ARPayment receipt = receiptOpt.get();
                ARInvoiceDetailDTO.PaymentHistoryItem historyItem =
                        new ARInvoiceDetailDTO.PaymentHistoryItem();
                historyItem.setReceiptId(receipt.getId());
                historyItem.setReceiptNumber(receipt.getReceiptNumber());
                historyItem.setReceiptDate(receipt.getReceiptDate());
                historyItem.setAllocatedAmount(allocation.getAllocatedAmount());
                historyItem.setPaymentMethod(
                        receipt.getPaymentMethod() != null
                                ? receipt.getPaymentMethod().name()
                                : null);
                paymentHistory.add(historyItem);
            }
        }

        // Sort payment history by receipt date descending
        paymentHistory.sort(
                Comparator.comparing(ARInvoiceDetailDTO.PaymentHistoryItem::getReceiptDate)
                        .reversed());

        dto.setPaymentHistory(paymentHistory);

        // Get line items
        List<SalesInvoiceLine> lines =
                salesInvoiceLineRepository.findByCompanyIdAndSalesInvoiceIdOrderByLineNumberAsc(
                        companyId, invoice.getId());

        List<ARInvoiceDetailDTO.InvoiceLineItem> lineItems = new ArrayList<>();
        for (SalesInvoiceLine line : lines) {
            ARInvoiceDetailDTO.InvoiceLineItem lineItem =
                    new ARInvoiceDetailDTO.InvoiceLineItem();
            lineItem.setLineNumber(line.getLineNumber());
            lineItem.setDescription(line.getDescription());
            lineItem.setQuantity(line.getQuantity());
            lineItem.setUnitPrice(line.getUnitPrice());
            lineItem.setAmount(line.getAmount());
            // Get account code - would need to query ChartOfAccount, but for now leave null
            // or add accountCode to SalesInvoiceLine if available
            lineItems.add(lineItem);
        }

        dto.setLineItems(lineItems);

        return dto;
    }

    /**
     * Determine aging bucket for an invoice based on due date and as-of date.
     *
     * @param dueDate invoice due date
     * @param asOfDate as-of date for aging calculation
     * @param overdueDays number of days overdue (0 if not overdue)
     * @return aging bucket key (CURRENT, DAYS_1_30, DAYS_31_60, DAYS_61_90, DAYS_OVER_90)
     */
    private String determineAgingBucket(LocalDate dueDate, LocalDate asOfDate, long overdueDays) {
        if (dueDate.isAfter(asOfDate) || overdueDays == 0) {
            return "CURRENT";
        } else if (overdueDays <= 30) {
            return "DAYS_1_30";
        } else if (overdueDays <= 60) {
            return "DAYS_31_60";
        } else if (overdueDays <= 90) {
            return "DAYS_61_90";
        } else {
            return "DAYS_OVER_90";
        }
    }
}
