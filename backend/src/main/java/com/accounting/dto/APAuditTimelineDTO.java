package com.accounting.dto;
import java.time.Instant;
import lombok.Data;

@Data
public class APAuditTimelineDTO {
    private Long id; // AuditLog ID
    private String action;
    private String eventType;
    private Instant timestamp;
    private Long userId;
    private String userName;
    private String userRole;
    private String entityId; // ID of bill or payment
    private String entityDisplay; // e.g. Bill #BILL-123
    private String summary; // Brief description or diff summary
    private String status; // Success/Failure
    private String failureReason;
}
