package com.accounting.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.accounting.dto.audit.AuditActorDTO;
import com.accounting.dto.audit.AuditLogFilter;
import com.accounting.dto.audit.AuditLogListItemDTO;
import com.accounting.dto.audit.AuditLogPageResponse;
import com.accounting.entity.AuditLog;
import com.accounting.repository.AuditLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AuditLogQueryService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;

  public AuditLogQueryService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
  }

  public AuditLogPageResponse search(AuditLogFilter filter) {
    Pageable pageable = PageRequest.of(
        Math.max(filter.page(), 0),
        Math.min(Math.max(filter.size(), 1), 200),
        Sort.by(Sort.Direction.DESC, "createdAt"));

    Specification<AuditLog> spec = specificationFor(filter);
    Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);

    List<AuditLogListItemDTO> rows = page.getContent().stream().map(this::toDto).toList();

    AuditLogPageResponse.PageMeta meta = new AuditLogPageResponse.PageMeta(
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext(),
        page.hasPrevious());

    return new AuditLogPageResponse(rows, meta);
  }

  public Specification<AuditLog> specificationFor(AuditLogFilter filter) {
    return (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

      if (filter.companyId() != null) {
        predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
      }

      if (StringUtils.hasText(filter.entityType())) {
        predicates.add(cb.equal(cb.lower(root.get("entityType")), filter.entityType().toLowerCase()));
      }

      if (StringUtils.hasText(filter.entityId())) {
        predicates.add(cb.equal(root.get("entityId"), filter.entityId()));
      }

      if (StringUtils.hasText(filter.action())) {
        predicates.add(cb.equal(cb.lower(root.get("action")), filter.action().toLowerCase()));
      }

      if (StringUtils.hasText(filter.eventType())) {
        predicates.add(cb.equal(cb.lower(root.get("eventType")), filter.eventType().toLowerCase()));
      }

      if (StringUtils.hasText(filter.userEmail())) {
        predicates.add(cb.like(cb.lower(root.get("email")), "%" + filter.userEmail().toLowerCase() + "%"));
      }

      if (StringUtils.hasText(filter.actorRole())) {
        predicates.add(cb.equal(cb.lower(root.get("actorRole")), filter.actorRole().toLowerCase()));
      }

      if (filter.success() != null) {
        predicates.add(cb.equal(root.get("success"), filter.success()));
      }

      Instant from = filter.from();
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
      }

      Instant to = filter.to();
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
      }

      query.orderBy(cb.desc(root.get("createdAt")));
      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
  }

  public AuditLogListItemDTO toDto(AuditLog entity) {
    AuditActorDTO actor = new AuditActorDTO(entity.getUserId(), entity.getEmail(), entity.getActorRole());
    Map<String, Object> changes = asMap(entity.getChanges());
    Map<String, Object> metadata = asMap(entity.getMetadata());
    return new AuditLogListItemDTO(
        entity.getId(),
        entity.getCreatedAt(),
        actor,
        entity.getAction(),
        entity.getEventType(),
        entity.getSuccess(),
        entity.getFailureReason(),
        entity.getEntityType(),
        entity.getEntityId(),
        entity.getEntityDisplay(),
        changes,
        metadata,
        entity.getIpAddress(),
        entity.getUserAgent(),
        entity.getTraceId());
  }

  private Map<String, Object> asMap(JsonNode node) {
    if (node == null || node.isNull()) {
      return Map.of();
    }
    return objectMapper.convertValue(node, MAP_TYPE);
  }
}
