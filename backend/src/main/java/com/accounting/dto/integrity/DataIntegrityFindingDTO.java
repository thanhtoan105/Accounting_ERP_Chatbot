package com.accounting.dto.integrity;

import java.util.Map;

import com.accounting.entity.DataIntegritySeverity;

public record DataIntegrityFindingDTO(
    String entityType,
    String entityId,
    String issueType,
    String description,
    DataIntegritySeverity severity,
    Map<String, Object> metadata) {}
