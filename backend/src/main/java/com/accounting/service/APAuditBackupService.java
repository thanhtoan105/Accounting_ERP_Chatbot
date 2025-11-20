package com.accounting.service;

import com.accounting.entity.APAuditBackup;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface APAuditBackupService {

    /**
     * Create a DR backup for the company's audit logs.
     *
     * @param companyId company ID
     * @return created backup entity
     */
    APAuditBackup createBackup(Long companyId);

    /**
     * List backups with filtering.
     *
     * @param status status filter (optional)
     * @param startDate start date filter (optional)
     * @param endDate end date filter (optional)
     * @param pageable pagination
     * @return page of backups
     */
    Page<APAuditBackup> listBackups(String status, String startDate, String endDate, Pageable pageable);

    /**
     * Download backup archive.
     *
     * @param backupId backup ID
     * @return byte array of archive
     */
    byte[] downloadBackup(Long backupId);
}
