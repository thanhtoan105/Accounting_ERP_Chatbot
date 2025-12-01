package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * ARAgingCache entity for caching AR aging report calculations.
 * Stores aging bucket amounts per customer for performance optimization.
 * Cache is invalidated when invoices are approved or receipts are posted.
 */
@Entity
@Table(name = "ar_aging_cache")
public class ARAgingCache implements CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @NotNull
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @NotNull
    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @NotNull
    @PositiveOrZero
    @Column(name = "current_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentAmount = BigDecimal.ZERO;

    @NotNull
    @PositiveOrZero
    @Column(name = "days_1_30", nullable = false, precision = 19, scale = 2)
    private BigDecimal days1To30 = BigDecimal.ZERO;

    @NotNull
    @PositiveOrZero
    @Column(name = "days_31_60", nullable = false, precision = 19, scale = 2)
    private BigDecimal days31To60 = BigDecimal.ZERO;

    @NotNull
    @PositiveOrZero
    @Column(name = "days_61_90", nullable = false, precision = 19, scale = 2)
    private BigDecimal days61To90 = BigDecimal.ZERO;

    @NotNull
    @PositiveOrZero
    @Column(name = "days_over_90", nullable = false, precision = 19, scale = 2)
    private BigDecimal daysOver90 = BigDecimal.ZERO;

    @NotNull
    @PositiveOrZero
    @Column(name = "total_outstanding", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalOutstanding = BigDecimal.ZERO;

    @NotNull
    @PositiveOrZero
    @Column(name = "invoice_count", nullable = false)
    private Integer invoiceCount = 0;

    @NotNull
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @NotNull
    @Column(name = "last_refreshed_at", nullable = false)
    private Instant lastRefreshedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (lastRefreshedAt == null) {
            lastRefreshedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    public void setCurrentAmount(BigDecimal currentAmount) {
        this.currentAmount = currentAmount;
    }

    public BigDecimal getDays1To30() {
        return days1To30;
    }

    public void setDays1To30(BigDecimal days1To30) {
        this.days1To30 = days1To30;
    }

    public BigDecimal getDays31To60() {
        return days31To60;
    }

    public void setDays31To60(BigDecimal days31To60) {
        this.days31To60 = days31To60;
    }

    public BigDecimal getDays61To90() {
        return days61To90;
    }

    public void setDays61To90(BigDecimal days61To90) {
        this.days61To90 = days61To90;
    }

    public BigDecimal getDaysOver90() {
        return daysOver90;
    }

    public void setDaysOver90(BigDecimal daysOver90) {
        this.daysOver90 = daysOver90;
    }

    public BigDecimal getTotalOutstanding() {
        return totalOutstanding;
    }

    public void setTotalOutstanding(BigDecimal totalOutstanding) {
        this.totalOutstanding = totalOutstanding;
    }

    public Integer getInvoiceCount() {
        return invoiceCount;
    }

    public void setInvoiceCount(Integer invoiceCount) {
        this.invoiceCount = invoiceCount;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public Instant getLastRefreshedAt() {
        return lastRefreshedAt;
    }

    public void setLastRefreshedAt(Instant lastRefreshedAt) {
        this.lastRefreshedAt = lastRefreshedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
