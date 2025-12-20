package com.accounting.service;

import java.util.List;
import java.util.UUID;

import com.accounting.dto.ARReminderConfigDTO;

/**
 * Service interface for AR reminder operations.
 * Manages reminder configuration and manual reminder triggering.
 */
public interface ARReminderService {

    /**
     * Get reminder configuration for current company.
     *
     * @return reminder configuration
     */
    ARReminderConfigDTO getConfiguration();

    /**
     * Update reminder configuration for current company.
     *
     * @param config reminder configuration
     * @return updated configuration
     */
    ARReminderConfigDTO updateConfiguration(ARReminderConfigDTO config);

    /**
     * Trigger manual reminders for specific customers and/or invoices.
     * Queues email jobs asynchronously.
     *
     * @param customerIds optional list of customer IDs
     * @param invoiceIds  optional list of invoice IDs
     */
    void triggerManualReminders(List<Long> customerIds, List<UUID> invoiceIds);
}
