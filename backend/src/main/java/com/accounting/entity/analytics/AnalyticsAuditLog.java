package com.accounting.entity.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "analytics_audit_log")
public class AnalyticsAuditLog implements CompanyScopedEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @NotNull
    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @NotNull
    @Column(name = "event_date_utc", nullable = false)
    private LocalDate eventDateUtc;

    @NotNull
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_subtype", length = 64)
    private String eventSubtype;

    @Column(name = "principal_id")
    private Long principalId;

    @Column(name = "principal_type", length = 32)
    private String principalType;

    @Column(name = "object_type", length = 64)
    private String objectType;

    @Column(name = "object_id", length = 255)
    private String objectId;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "request_id", columnDefinition = "UUID")
    private UUID requestId;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @NotNull
    @Column(name = "sequence_in_company", nullable = false)
    private Long sequenceInCompany;

    @NotNull
    @Column(name = "sequence_in_day", nullable = false)
    private Long sequenceInDay;

    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    @NotNull
    @Column(name = "record_hash", nullable = false, length = 64)
    private String recordHash;

    @NotNull
    @Column(name = "merkle_leaf_hash", nullable = false, length = 64)
    private String merkleLeafHash;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (eventTime == null) {
            eventTime = Instant.now();
        }
        if (eventDateUtc == null) {
            eventDateUtc = LocalDate.now(java.time.ZoneOffset.UTC);
        }
    }

    @Override
    public Long getCompanyId() {
        return companyId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public LocalDate getEventDateUtc() {
        return eventDateUtc;
    }

    public void setEventDateUtc(LocalDate eventDateUtc) {
        this.eventDateUtc = eventDateUtc;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventSubtype() {
        return eventSubtype;
    }

    public void setEventSubtype(String eventSubtype) {
        this.eventSubtype = eventSubtype;
    }

    public Long getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(Long principalId) {
        this.principalId = principalId;
    }

    public String getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(String principalType) {
        this.principalType = principalType;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Long getSequenceInCompany() {
        return sequenceInCompany;
    }

    public void setSequenceInCompany(Long sequenceInCompany) {
        this.sequenceInCompany = sequenceInCompany;
    }

    public Long getSequenceInDay() {
        return sequenceInDay;
    }

    public void setSequenceInDay(Long sequenceInDay) {
        this.sequenceInDay = sequenceInDay;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public void setPrevHash(String prevHash) {
        this.prevHash = prevHash;
    }

    public String getRecordHash() {
        return recordHash;
    }

    public void setRecordHash(String recordHash) {
        this.recordHash = recordHash;
    }

    public String getMerkleLeafHash() {
        return merkleLeafHash;
    }

    public void setMerkleLeafHash(String merkleLeafHash) {
        this.merkleLeafHash = merkleLeafHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
