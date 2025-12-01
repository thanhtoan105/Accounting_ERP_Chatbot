package com.accounting.dto;

import org.springframework.data.domain.Page;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Wrapper DTO for AR aging report responses with snapshot metadata.
 * Includes the paginated report data plus metadata about the report generation.
 */
public class ARAgingReportResponse {

    private Page<ARAgingReportDTO> data;
    private SnapshotMetadata metadata;

    public Page<ARAgingReportDTO> getData() {
        return data;
    }

    public void setData(Page<ARAgingReportDTO> data) {
        this.data = data;
    }

    public SnapshotMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(SnapshotMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Nested class for snapshot metadata.
     */
    public static class SnapshotMetadata {
        private LocalDateTime computedAt;
        private LocalDate snapshotDate;
        private String cacheStatus; // HIT or MISS

        public SnapshotMetadata() {
        }

        public SnapshotMetadata(LocalDateTime computedAt, LocalDate snapshotDate, String cacheStatus) {
            this.computedAt = computedAt;
            this.snapshotDate = snapshotDate;
            this.cacheStatus = cacheStatus;
        }

        public LocalDateTime getComputedAt() {
            return computedAt;
        }

        public void setComputedAt(LocalDateTime computedAt) {
            this.computedAt = computedAt;
        }

        public LocalDate getSnapshotDate() {
            return snapshotDate;
        }

        public void setSnapshotDate(LocalDate snapshotDate) {
            this.snapshotDate = snapshotDate;
        }

        public String getCacheStatus() {
            return cacheStatus;
        }

        public void setCacheStatus(String cacheStatus) {
            this.cacheStatus = cacheStatus;
        }
    }
}
