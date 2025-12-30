package com.accounting.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing AR aging buckets for dashboard pie/bar charts.
 * Groups outstanding receivables by age categories.
 */
public class AgingBucketsDTO {

    private List<AgingBucket> buckets;
    private BigDecimal totalOutstanding;
    private LocalDate asOfDate;

    public AgingBucketsDTO() {
        this.buckets = new ArrayList<>();
    }

    public AgingBucketsDTO(List<AgingBucket> buckets, BigDecimal totalOutstanding, LocalDate asOfDate) {
        this.buckets = buckets != null ? buckets : new ArrayList<>();
        this.totalOutstanding = totalOutstanding;
        this.asOfDate = asOfDate;
    }

    public List<AgingBucket> getBuckets() {
        return buckets;
    }

    public void setBuckets(List<AgingBucket> buckets) {
        this.buckets = buckets;
    }

    public BigDecimal getTotalOutstanding() {
        return totalOutstanding;
    }

    public void setTotalOutstanding(BigDecimal totalOutstanding) {
        this.totalOutstanding = totalOutstanding;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    /**
     * Represents a single aging bucket with amount and percentage.
     */
    public static class AgingBucket {

        private String bucketKey;
        private BigDecimal amount;
        private BigDecimal percent;

        public AgingBucket() {
        }

        public AgingBucket(String bucketKey, BigDecimal amount, BigDecimal percent) {
            this.bucketKey = bucketKey;
            this.amount = amount;
            this.percent = percent;
        }

        /**
         * Gets the bucket key identifier.
         * Valid values: CURRENT, DAYS_1_30, DAYS_31_60, DAYS_61_90, DAYS_OVER_90
         *
         * @return the bucket key
         */
        public String getBucketKey() {
            return bucketKey;
        }

        public void setBucketKey(String bucketKey) {
            this.bucketKey = bucketKey;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        /**
         * Gets the percentage of total outstanding for this bucket.
         *
         * @return the percentage as a decimal (e.g., 25.50 for 25.5%)
         */
        public BigDecimal getPercent() {
            return percent;
        }

        public void setPercent(BigDecimal percent) {
            this.percent = percent;
        }
    }
}
