package com.accounting.dto;

import java.time.Instant;
import java.util.List;

import lombok.Data;

@Data
public class AbuseDetectionResultDTO {
    private Long userId;
    private String userName;
    private String userEmail;
    private String patternType; // e.g. REPEATED_FAILURES, RAPID_OPERATIONS
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private int eventCount;
    private Instant firstEventTime;
    private Instant lastEventTime;
    private String description;
    private List<String> relatedEventIds; // IDs of events contributing to the pattern
}
