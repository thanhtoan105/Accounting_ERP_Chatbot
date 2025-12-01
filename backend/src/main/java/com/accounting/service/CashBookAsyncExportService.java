package com.accounting.service;

import com.accounting.dto.cashbook.CashBookExportJobDTO;
import com.accounting.dto.cashbook.CashBookFilterDTO;
import java.util.Optional;

/**
 * Service for async cash book export operations.
 * Handles large dataset exports (> 10,000 records) asynchronously (AC6.4-05).
 */
public interface CashBookAsyncExportService {

    /**
     * Queue an async export job for a large dataset.
     *
     * @param bankAccountId bank account ID
     * @param filter        export filters
     * @param format        export format (excel/pdf)
     * @param recordCount   total record count
     * @return job DTO with job ID for status polling
     */
    CashBookExportJobDTO queueExportJob(
            Long bankAccountId,
            CashBookFilterDTO filter,
            String format,
            long recordCount);

    /**
     * Get the status of an export job.
     *
     * @param jobId job ID
     * @return job status DTO if found
     */
    Optional<CashBookExportJobDTO> getJobStatus(String jobId);

    /**
     * Get the exported file bytes for a completed job.
     *
     * @param jobId job ID
     * @return file bytes if job is completed
     */
    Optional<byte[]> getExportedFile(String jobId);

    /**
     * Cancel an export job.
     *
     * @param jobId job ID
     * @return true if job was cancelled
     */
    boolean cancelJob(String jobId);
}
