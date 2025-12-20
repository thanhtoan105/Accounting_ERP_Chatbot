package com.accounting.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

@Data
public class APAuditEventDTO {
    private Long id;
    private String action;
    private String eventType;
    private Instant timestamp;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userRole;
    private String ipAddress;
    private String userAgent;
    
    private String entityType;
    private String entityId;
    private String entityDisplay;
    
    private JsonNode beforeSnapshot;
    private JsonNode afterSnapshot;
    private JsonNode changes; // Computed diff if available
    private JsonNode metadata;
    
    private String diffHash;
    private String chainHash; // For Task 5
    
    private boolean success;
    private String failureReason;
}
