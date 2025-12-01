package com.accounting.service.impl.cashbook;

import com.accounting.dto.cashbook.CashBookExportJobDTO;
import com.accounting.dto.cashbook.CashBookFilterDTO;
import com.accounting.dto.cashbook.CashBookResponseDTO;
import com.accounting.service.CashBookAsyncExportService;
import com.accounting.service.CashBookExportService;
import com.accounting.service.CashBookService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Implementation of CashBookAsyncExportService for large dataset exports.
 * Uses in-memory job tracking and temp file storage (AC6.4-05).
 *
 * <p>
 * Note: For production, consider using a database for job tracking
 * and cloud storage (S3) for exported files.
 */
@Service
public class CashBookAsyncExportServiceImpl implements CashBookAsyncExportService {

    private static final Logger logger = LoggerFactory.getLogger(CashBookAsyncExportServiceImpl.class);
    private static final int MAX_JOBS_RETAINED = 100;
    private static final long JOB_RETENTION_HOURS = 24;

    private final CashBookService cashBookService;
    private final CashBookExportService cashBookExportService;
    private final Map<String, CashBookExportJobDTO> jobRegistry = new ConcurrentHashMap<>();
    private final Map<String, Path> exportedFiles = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    @Value("${app.export.temp-dir:#{systemProperties['java.io.tmpdir']}}")
    private String tempDir;

    public CashBookAsyncExportServiceImpl(
            CashBookService cashBookService,
            CashBookExportService cashBookExportService) {
        this.cashBookService = cashBookService;
        this.cashBookExportService = cashBookExportService;

        // Schedule cleanup of old jobs every hour
        cleanupScheduler.scheduleAtFixedRate(this::cleanupOldJobs, 1, 1, TimeUnit.HOURS);
    }

    @Override
    public CashBookExportJobDTO queueExportJob(
            Long bankAccountId,
            CashBookFilterDTO filter,
            String format,
            long recordCount) {

        String jobId = UUID.randomUUID().toString();
        CashBookExportJobDTO job = new CashBookExportJobDTO(jobId, bankAccountId, format, recordCount);

        // Check capacity
        if (jobRegistry.size() >= MAX_JOBS_RETAINED) {
            cleanupOldJobs();
            if (jobRegistry.size() >= MAX_JOBS_RETAINED) {
                throw new IllegalStateException("Export queue is full. Please try again later.");
            }
        }

        jobRegistry.put(jobId, job);

        // Start async processing
        processExportAsync(jobId, bankAccountId, filter, format, recordCount);

        logger.info("Queued async export job: jobId={}, bankAccountId={}, format={}, recordCount={}",
                jobId, bankAccountId, format, recordCount);

        return job;
    }

    @Override
    public Optional<CashBookExportJobDTO> getJobStatus(String jobId) {
        return Optional.ofNullable(jobRegistry.get(jobId));
    }

    @Override
    public Optional<byte[]> getExportedFile(String jobId) {
        CashBookExportJobDTO job = jobRegistry.get(jobId);
        if (job == null || !"COMPLETED".equals(job.getStatus())) {
            return Optional.empty();
        }

        Path filePath = exportedFiles.get(jobId);
        if (filePath == null || !Files.exists(filePath)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Files.readAllBytes(filePath));
        } catch (IOException e) {
            logger.error("Failed to read exported file for job {}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean cancelJob(String jobId) {
        CashBookExportJobDTO job = jobRegistry.get(jobId);
        if (job == null) {
            return false;
        }

        if ("PROCESSING".equals(job.getStatus()) || "PENDING".equals(job.getStatus())) {
            job.setStatus("CANCELLED");
            logger.info("Cancelled export job: jobId={}", jobId);
            return true;
        }

        return false;
    }

    @Async
    protected void processExportAsync(
            String jobId,
            Long bankAccountId,
            CashBookFilterDTO filter,
            String format,
            long recordCount) {

        CashBookExportJobDTO job = jobRegistry.get(jobId);
        if (job == null) {
            return;
        }

        try {
            job.setStatus("PROCESSING");
            job.setProgress(10);

            // Check for cancellation
            if ("CANCELLED".equals(jobRegistry.get(jobId).getStatus())) {
                return;
            }

            // Fetch all data (may take time for large datasets)
            CashBookFilterDTO exportFilter = filter.withPagination(0, (int) recordCount + 1);
            job.setProgress(30);

            CashBookResponseDTO cashBookData = cashBookService.getCashBook(exportFilter);
            job.setProgress(60);

            // Check for cancellation
            if ("CANCELLED".equals(jobRegistry.get(jobId).getStatus())) {
                return;
            }

            // Generate export
            byte[] exportBytes;
            String extension;
            if ("pdf".equalsIgnoreCase(format)) {
                exportBytes = cashBookExportService.exportToPdf(cashBookData, filter);
                extension = ".pdf";
            } else {
                exportBytes = cashBookExportService.exportToExcel(cashBookData, filter);
                extension = ".xlsx";
            }
            job.setProgress(90);

            // Save to temp file
            Path tempFile = Files.createTempFile(
                    Path.of(tempDir),
                    "cash_book_export_" + jobId + "_",
                    extension);
            Files.write(tempFile, exportBytes);
            exportedFiles.put(jobId, tempFile);

            // Mark job as completed
            job.setStatus("COMPLETED");
            job.setProgress(100);
            job.setCompletedAt(Instant.now());
            job.setDownloadUrl("/api/v1/cash-book/exports/" + jobId + "/download");

            logger.info("Completed async export job: jobId={}, size={} bytes", jobId, exportBytes.length);

        } catch (Exception e) {
            logger.error("Failed async export job: jobId={}, error={}", jobId, e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
        }
    }

    private void cleanupOldJobs() {
        Instant cutoff = Instant.now().minusSeconds(JOB_RETENTION_HOURS * 3600);

        jobRegistry.entrySet().removeIf(entry -> {
            CashBookExportJobDTO job = entry.getValue();
            boolean isOld = job.getCreatedAt() != null && job.getCreatedAt().isBefore(cutoff);
            boolean isTerminal = "COMPLETED".equals(job.getStatus()) ||
                    "FAILED".equals(job.getStatus()) ||
                    "CANCELLED".equals(job.getStatus());

            if (isOld && isTerminal) {
                // Clean up temp file
                Path filePath = exportedFiles.remove(entry.getKey());
                if (filePath != null) {
                    try {
                        Files.deleteIfExists(filePath);
                    } catch (IOException e) {
                        logger.warn("Failed to delete temp file for job {}: {}", entry.getKey(), e.getMessage());
                    }
                }
                return true;
            }
            return false;
        });
    }
}
