package com.accounting.entity.audit;

import java.time.Instant;
import java.time.LocalDate;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "audit_chain_checkpoints")
public class AuditChainCheckpoint implements CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @NotNull
    @Column(name = "event_date_utc", nullable = false)
    private LocalDate eventDateUtc;

    @Column(name = "first_sequence")
    private Long firstSequence;

    @Column(name = "last_sequence")
    private Long lastSequence;

    @Column(name = "record_count")
    private Long recordCount;

    @Column(name = "merkle_root", length = 64)
    private String merkleRoot;

    @Column(name = "chain_head_hash", length = 64)
    private String chainHeadHash;

    @Column(name = "chain_tail_hash", length = 64)
    private String chainTailHash;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "verification_error", columnDefinition = "TEXT")
    private String verificationError;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Override
    public Long getCompanyId() {
        return companyId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public LocalDate getEventDateUtc() {
        return eventDateUtc;
    }

    public void setEventDateUtc(LocalDate eventDateUtc) {
        this.eventDateUtc = eventDateUtc;
    }

    public Long getFirstSequence() {
        return firstSequence;
    }

    public void setFirstSequence(Long firstSequence) {
        this.firstSequence = firstSequence;
    }

    public Long getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(Long lastSequence) {
        this.lastSequence = lastSequence;
    }

    public Long getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Long recordCount) {
        this.recordCount = recordCount;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public void setMerkleRoot(String merkleRoot) {
        this.merkleRoot = merkleRoot;
    }

    public String getChainHeadHash() {
        return chainHeadHash;
    }

    public void setChainHeadHash(String chainHeadHash) {
        this.chainHeadHash = chainHeadHash;
    }

    public String getChainTailHash() {
        return chainTailHash;
    }

    public void setChainTailHash(String chainTailHash) {
        this.chainTailHash = chainTailHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(Instant lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public String getVerificationError() {
        return verificationError;
    }

    public void setVerificationError(String verificationError) {
        this.verificationError = verificationError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
