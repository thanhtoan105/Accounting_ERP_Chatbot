package com.accounting.entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * ChatbotQuery entity for storing RAG-based chatbot interactions.
 * Implements company-scoped audit logging for AI-assisted voucher queries.
 * Each query tracks the user's natural language input, RAG-generated response,
 * citations from retrieved vouchers, and metadata for compliance and analytics.
 */
@Entity
@Table(name = "chatbot_queries")
public class ChatbotQuery implements CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Company ID is required")
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotBlank(message = "Query text is required")
    @Size(max = 5000, message = "Query text must not exceed 5000 characters")
    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    /**
     * JSONB column storing array of citation objects.
     * Each citation contains: entityType, entityId, voucherNumber, excerpt, relevanceScore, link.
     * Example: [{"entityType":"VOUCHER","entityId":"123","voucherNumber":"VCH-001","excerpt":"...","relevanceScore":0.92,"link":"/vouchers/123"}]
     */
    @Column(name = "citations", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String citations;

    @Min(value = 0, message = "Confidence score must be between 0 and 1")
    @Max(value = 1, message = "Confidence score must be between 0 and 1")
    @Column(name = "confidence_score")
    private Float confidenceScore;

    @NotBlank(message = "Session ID is required")
    @Size(max = 100, message = "Session ID must not exceed 100 characters")
    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @NotBlank(message = "Language is required")
    @Size(max = 10, message = "Language code must not exceed 10 characters")
    @Column(name = "language", nullable = false, length = 10)
    private String language = "vi";

    @PositiveOrZero(message = "Response time must be positive or zero")
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @NotNull(message = "Created at timestamp is required")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Size(max = 64, message = "Audit hash must not exceed 64 characters")
    @Column(name = "audit_hash", length = 64)
    private String auditHash;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        auditHash = generateAuditHash();
    }

    /**
     * Generates SHA-256 hash of query text + answer text + citations for tamper detection.
     * Used for audit compliance to verify data integrity.
     *
     * @return SHA-256 hash as hexadecimal string (64 characters)
     */
    public String generateAuditHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder dataToHash = new StringBuilder();
            dataToHash.append(queryText != null ? queryText : "");
            dataToHash.append(answerText != null ? answerText : "");
            dataToHash.append(citations != null ? citations : "");

            byte[] hashBytes = digest.digest(dataToHash.toString().getBytes(StandardCharsets.UTF_8));

            // Convert byte array to hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies audit hash integrity by recalculating and comparing with stored hash.
     *
     * @return true if hash matches, false otherwise
     */
    public boolean verifyAuditHash() {
        if (auditHash == null) {
            return false;
        }
        String recalculatedHash = generateAuditHash();
        return auditHash.equals(recalculatedHash);
    }

    // Getters and Setters

    @Override
    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public String getCitations() {
        return citations;
    }

    public void setCitations(String citations) {
        this.citations = citations;
    }

    public Float getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Float confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Integer responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getAuditHash() {
        return auditHash;
    }

    public void setAuditHash(String auditHash) {
        this.auditHash = auditHash;
    }
}
