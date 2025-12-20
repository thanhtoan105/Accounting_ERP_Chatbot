package com.accounting.dto.integrity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.accounting.entity.DataIntegrityJobStatus;

public record DataIntegrityJobResponse(
    UUID jobId,
    Instant startedAt,
    Instant completedAt,
    DataIntegrityJobStatus status,
    int findingsCount,
    Map<String, String> triggeredBy,
    String summary,
    Map<String, Object> warnings,
    List<DataIntegrityFindingDTO> findings) {}
