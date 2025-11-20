package com.accounting.service;

import com.accounting.dto.APAuditEventDTO;
import com.accounting.dto.APAuditTimelineDTO;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for accessing and managing AP audit trail.
 */
public interface APAuditService {

    /**
     * Get filtered audit timeline.
     *
     * @param filters map of filters (user, action, amount, supplier, attachment, outcome, startDate, endDate)
     * @param pageable pagination information
     * @return page of timeline DTOs
     */
    Page<APAuditTimelineDTO> getAuditTimeline(Map<String, Object> filters, Pageable pageable);

    /**
     * Get detailed audit event.
     *
     * @param eventId audit log ID
     * @return full event details
     */
    APAuditEventDTO getAuditEventDetails(Long eventId);

    /**
     * Export audit timeline to PDF.
     *
     * @param filters map of filters
     * @return PDF byte array
     */
    byte[] exportAuditTimeline(Map<String, Object> filters);

    /**
     * Detect abuse patterns for a user within a time window.
     *
     * @param userId user ID to check (optional, if null checks all users)
     * @param timeWindowMinutes time window in minutes to look back
     * @return list of detected abuse patterns
     */
    java.util.List<com.accounting.dto.AbuseDetectionResultDTO> detectAbusePatterns(Long userId, int timeWindowMinutes);

    /**
     * Get unauthorized access attempts.
     *
     * @param filters filters (user, date range)
     * @param pageable pagination
     * @return page of unauthorized attempts (as AuditTimelineDTOs)
     */
    Page<APAuditTimelineDTO> getUnauthorizedAttempts(Map<String, Object> filters, Pageable pageable);
}
