package com.accounting.dto.audit;

import java.util.List;

public record AuditLogPageResponse(
    List<AuditLogListItemDTO> data,
    PageMeta meta) {

  public record PageMeta(int page, int size, long totalElements, int totalPages, boolean hasNext, boolean hasPrevious) {}
}



