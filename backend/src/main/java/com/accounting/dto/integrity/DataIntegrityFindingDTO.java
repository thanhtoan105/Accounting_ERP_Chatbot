package com.accounting.dto.integrity;

import com.accounting.entity.DataIntegritySeverity;
import java.util.Map;

public record DataIntegrityFindingDTO(
    String entityType,
    String entityId,
    String issueType,
    String description,
    DataIntegritySeverity severity,
    Map<String, Object> metadata) {}



