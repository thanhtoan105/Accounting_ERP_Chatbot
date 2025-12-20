package com.accounting.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.ARReminderConfigDTO;
import com.accounting.entity.ARReminderConfiguration;
import com.accounting.entity.Customer;
import com.accounting.entity.SalesInvoice;
import com.accounting.repository.ARReminderConfigurationRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ARReminderService;
import com.accounting.service.AuditService;
import com.accounting.service.EmailService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Implementation of ARReminderService.
 * Manages reminder configuration and triggers email reminders.
 * TODO: Integrate with email service for actual reminder sending.
 */
@Service
@Transactional
public class ARReminderServiceImpl implements ARReminderService {

    private static final Logger logger = LoggerFactory.getLogger(ARReminderServiceImpl.class);

    private final ARReminderConfigurationRepository reminderConfigRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final CustomerRepository customerRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final HttpServletRequest httpServletRequest;

    @Autowired
    public ARReminderServiceImpl(
            ARReminderConfigurationRepository reminderConfigRepository,
            SalesInvoiceRepository salesInvoiceRepository,
            CustomerRepository customerRepository,
            EmailService emailService,
            AuditService auditService,
            HttpServletRequest httpServletRequest) {
        this.reminderConfigRepository = reminderConfigRepository;
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.customerRepository = customerRepository;
        this.emailService = emailService;
        this.auditService = auditService;
        this.httpServletRequest = httpServletRequest;
    }

    @Override
    @Transactional(readOnly = true)
    public ARReminderConfigDTO getConfiguration() {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Missing company context");
        }

        ARReminderConfiguration config = reminderConfigRepository
                .findByCompanyId(companyId)
                .orElseGet(() -> createDefaultConfiguration(companyId));

        return convertToDTO(config);
    }

    @Override
    public ARReminderConfigDTO updateConfiguration(ARReminderConfigDTO configDTO) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Missing company context");
        }

        ARReminderConfiguration existing = reminderConfigRepository
                .findByCompanyId(companyId)
                .orElse(null);

        // Build old/new values for audit
        Map<String, String> oldValues = new java.util.HashMap<>();
        Map<String, String> newValues = new java.util.HashMap<>();

        if (existing != null) {
            oldValues.put("preDueDays", String.valueOf(existing.getPreDueDays()));
            oldValues.put("dueDateEnabled", String.valueOf(existing.getDueDateEnabled()));
            oldValues.put("postDueCadenceDays", String.valueOf(existing.getPostDueCadenceDays()));
        }

        newValues.put("preDueDays", String.valueOf(configDTO.getPreDueDays()));
        newValues.put("dueDateEnabled", String.valueOf(configDTO.getDueDateEnabled()));
        newValues.put("postDueCadenceDays", String.valueOf(configDTO.getPostDueCadenceDays()));

        ARReminderConfiguration config = existing != null ? existing : new ARReminderConfiguration();
        config.setCompanyId(companyId);
        config.setPreDueDays(configDTO.getPreDueDays());
        config.setDueDateEnabled(configDTO.getDueDateEnabled());
        config.setPostDueCadenceDays(configDTO.getPostDueCadenceDays());

        config = reminderConfigRepository.save(config);

        // Log audit event
        Long userId = SecurityUtils.getCurrentUserId();
        auditService.logARReminderConfigUpdated(companyId, userId, oldValues, newValues, httpServletRequest);

        logger.info("Updated AR reminder configuration for company {}", companyId);

        return convertToDTO(config);
    }

    @Override
    @Async
    public void triggerManualReminders(List<Long> customerIds, List<UUID> invoiceIds) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Missing company context");
        }

        logger.info(
                "Triggering manual AR reminders for company {} - customers: {}, invoices: {}",
                companyId,
                customerIds != null ? customerIds.size() : 0,
                invoiceIds != null ? invoiceIds.size() : 0);

        // Query overdue invoices
        List<SalesInvoice> overdueInvoices = findOverdueInvoices(companyId, customerIds, invoiceIds);

        if (overdueInvoices.isEmpty()) {
            logger.info("No overdue invoices found for reminder sending");
            return;
        }

        // Group invoices by customer
        Map<Long, List<SalesInvoice>> invoicesByCustomer = overdueInvoices.stream()
                .collect(Collectors.groupingBy(SalesInvoice::getCustomerId));

        // Send reminder email to each customer
        int successCount = 0;
        int failureCount = 0;

        for (Map.Entry<Long, List<SalesInvoice>> entry : invoicesByCustomer.entrySet()) {
            Long customerId = entry.getKey();
            List<SalesInvoice> customerInvoices = entry.getValue();

            try {
                sendReminderToCustomer(customerId, customerInvoices);
                successCount++;
            } catch (Exception e) {
                logger.error("Failed to send reminder to customer {}: {}", customerId, e.getMessage(), e);
                failureCount++;
            }
        }

        // Log batch audit event
        Long userId = SecurityUtils.getCurrentUserId();
        int totalInvoices = overdueInvoices.size();
        auditService.logARReminderBatchTriggered(
                companyId,
                userId,
                invoicesByCustomer.size(),
                totalInvoices,
                successCount,
                failureCount,
                httpServletRequest);

        logger.info(
                "AR reminder sending complete: {} successful, {} failed, {} total customers",
                successCount,
                failureCount,
                invoicesByCustomer.size());
    }

    private List<SalesInvoice> findOverdueInvoices(
            Long companyId, List<Long> customerIds, List<UUID> invoiceIds) {
        LocalDate today = LocalDate.now();

        // Build query based on provided filters
        if (invoiceIds != null && !invoiceIds.isEmpty()) {
            // Query specific invoices
            return salesInvoiceRepository.findAllById(invoiceIds).stream()
                    .filter(inv -> inv.getCompanyId().equals(companyId))
                    .filter(inv -> "POSTED".equals(inv.getStatus()))
                    .filter(inv -> inv.getRemainingBalance().compareTo(BigDecimal.ZERO) > 0)
                    .filter(inv -> inv.getDueDate() != null && inv.getDueDate().isBefore(today))
                    .collect(Collectors.toList());
        } else if (customerIds != null && !customerIds.isEmpty()) {
            // Query invoices for specific customers
            return salesInvoiceRepository.findAll().stream()
                    .filter(inv -> inv.getCompanyId().equals(companyId))
                    .filter(inv -> customerIds.contains(inv.getCustomerId()))
                    .filter(inv -> "POSTED".equals(inv.getStatus()))
                    .filter(inv -> inv.getRemainingBalance().compareTo(BigDecimal.ZERO) > 0)
                    .filter(inv -> inv.getDueDate() != null && inv.getDueDate().isBefore(today))
                    .collect(Collectors.toList());
        } else {
            // Query all overdue invoices for company
            return salesInvoiceRepository.findAll().stream()
                    .filter(inv -> inv.getCompanyId().equals(companyId))
                    .filter(inv -> "POSTED".equals(inv.getStatus()))
                    .filter(inv -> inv.getRemainingBalance().compareTo(BigDecimal.ZERO) > 0)
                    .filter(inv -> inv.getDueDate() != null && inv.getDueDate().isBefore(today))
                    .collect(Collectors.toList());
        }
    }

    private void sendReminderToCustomer(Long customerId, List<SalesInvoice> invoices) {
        // Get customer details
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            logger.warn("Customer {} has no email address, skipping reminder", customerId);
            return;
        }

        // Build invoice info list
        LocalDate today = LocalDate.now();
        List<EmailService.OverdueInvoiceInfo> invoiceInfoList = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (SalesInvoice invoice : invoices) {
            int daysOverdue = (int) ChronoUnit.DAYS.between(invoice.getDueDate(), today);
            invoiceInfoList.add(new EmailService.OverdueInvoiceInfo(
                    invoice.getInvoiceNumber(),
                    invoice.getDueDate(),
                    invoice.getRemainingBalance(),
                    daysOverdue));
            totalAmount = totalAmount.add(invoice.getRemainingBalance());
        }

        // Get company name from context (simplified - in production, query from Company
        // entity)
        String companyName = "Your Company"; // TODO: Get from Company entity

        // Send email
        emailService.sendARReminderEmail(
                customer.getEmail(),
                customer.getName(),
                companyName,
                invoiceInfoList,
                totalAmount);

        // Log individual reminder audit event
        Long userId = SecurityUtils.getCurrentUserId();
        Long companyId = CompanyContext.getCompanyId();
        auditService.logARReminderSent(
                companyId,
                userId,
                customerId,
                customer.getEmail(),
                invoices.size(),
                totalAmount,
                httpServletRequest);

        logger.info(
                "Sent AR reminder to customer {} ({}) for {} invoices totaling {}",
                customer.getName(),
                customer.getEmail(),
                invoices.size(),
                totalAmount);
    }

    private ARReminderConfiguration createDefaultConfiguration(Long companyId) {
        ARReminderConfiguration config = new ARReminderConfiguration();
        config.setCompanyId(companyId);
        config.setPreDueDays(3);
        config.setDueDateEnabled(true);
        config.setPostDueCadenceDays(7);
        return reminderConfigRepository.save(config);
    }

    private ARReminderConfigDTO convertToDTO(ARReminderConfiguration entity) {
        ARReminderConfigDTO dto = new ARReminderConfigDTO();
        dto.setId(entity.getId());
        dto.setCompanyId(entity.getCompanyId());
        dto.setPreDueDays(entity.getPreDueDays());
        dto.setDueDateEnabled(entity.getDueDateEnabled());
        dto.setPostDueCadenceDays(entity.getPostDueCadenceDays());
        return dto;
    }
}
