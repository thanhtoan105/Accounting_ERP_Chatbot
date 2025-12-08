package com.accounting.dto;

import java.time.Instant;
import java.util.Map;

import com.accounting.service.VoucherHistoryService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * DTO for voucher history entry.
 */
public class VoucherHistoryEntryDTO {
    private Long id;
    private String action;
    private Instant timestamp;
    private Long userId;
    private String userEmail;
    private String userRole;
    private String ipAddress;
    private String userAgent;
    private Boolean success;
    private String failureReason;
    private String summary;
    private Map<String, VoucherHistoryService.FieldDiff> diff;
    private String diffHash;
    private JsonNode changes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
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

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Map<String, VoucherHistoryService.FieldDiff> getDiff() {
        return diff;
    }

    public void setDiff(Map<String, VoucherHistoryService.FieldDiff> diff) {
        this.diff = diff;
    }

    public String getDiffHash() {
        return diffHash;
    }

    public void setDiffHash(String diffHash) {
        this.diffHash = diffHash;
    }

    public JsonNode getChanges() {
        return changes;
    }

    public void setChanges(JsonNode changes) {
        this.changes = changes;
    }
}
