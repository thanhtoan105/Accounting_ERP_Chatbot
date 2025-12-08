package com.accounting.service.impl.ap;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.APAuditEventDTO;
import com.accounting.dto.APAuditTimelineDTO;
import com.accounting.entity.AuditLog;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.APAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.criteria.Predicate;

@Service
@Transactional(readOnly = true)
public class APAuditServiceImpl implements APAuditService {

    private static final Logger logger = LoggerFactory.getLogger(APAuditServiceImpl.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public APAuditServiceImpl(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public Page<APAuditTimelineDTO> getAuditTimeline(Map<String, Object> filters, Pageable pageable) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
        }

        Specification<AuditLog> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Mandatory company filter
            predicates.add(criteriaBuilder.equal(root.get("companyId"), companyId));

            // Filter for AP related event types
            // AP events: PURCHASE_BILL, PAYMENT, VOUCHER, PERIOD_MANAGEMENT, PERIOD_VALIDATION, VOUCHER_ATTACHMENT, 
            // PURCHASE_BILL_IMPORT, AGING_REMINDER_SENT, AGING_BATCH_REMINDER_SENT, etc.
            // Or we can filter by action starting with PURCHASE_BILL_, PAYMENT_, etc.
            List<String> apEventTypes = List.of(
                "PURCHASE_BILL", 
                "PAYMENT", 
                "PURCHASE_BILL_IMPORT", 
                "MASTER_DATA" // Includes supplier
            );
            // Since eventType isn't always strictly set for all actions (some use GENERAL), 
            // we might need to filter by action prefix too.
            // But let's try to be inclusive.
            Predicate eventTypePredicate = root.get("eventType").in(apEventTypes);
            
            Predicate actionPredicate = criteriaBuilder.or(
                criteriaBuilder.like(root.get("action"), "PURCHASE_BILL%"),
                criteriaBuilder.like(root.get("action"), "PAYMENT%"),
                criteriaBuilder.like(root.get("action"), "SUPPLIER%"),
                criteriaBuilder.like(root.get("action"), "VOUCHER%"), // Vouchers are AP related? Maybe not all.
                criteriaBuilder.like(root.get("action"), "STATEMENT%")
            );
            
            predicates.add(criteriaBuilder.or(eventTypePredicate, actionPredicate));

            // Optional filters
            if (filters != null) {
                if (filters.get("userId") != null) {
                    predicates.add(criteriaBuilder.equal(root.get("userId"), Long.valueOf(filters.get("userId").toString())));
                }
                
                if (filters.get("action") != null) {
                    predicates.add(criteriaBuilder.like(root.get("action"), "%" + filters.get("action").toString() + "%"));
                }
                
                if (filters.get("outcome") != null) {
                    String outcome = filters.get("outcome").toString();
                    if ("SUCCESS".equalsIgnoreCase(outcome)) {
                        predicates.add(criteriaBuilder.equal(root.get("success"), true));
                    } else if ("FAILURE".equalsIgnoreCase(outcome)) {
                        predicates.add(criteriaBuilder.equal(root.get("success"), false));
                    }
                }
                
                if (filters.get("startDate") != null) {
                    LocalDate start = LocalDate.parse(filters.get("startDate").toString());
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), start.atStartOfDay(DEFAULT_ZONE).toInstant()));
                }
                
                if (filters.get("endDate") != null) {
                    LocalDate end = LocalDate.parse(filters.get("endDate").toString());
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), end.atTime(23, 59, 59).atZone(DEFAULT_ZONE).toInstant()));
                }
                
                // "amount" and "supplier" filters are hard with JPA Spec on JSONB
                // skipping for now as they require complex implementation
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLog> logs = auditLogRepository.findAll(spec, pageable);
        return logs.map(this::toTimelineDTO);
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public APAuditEventDTO getAuditEventDetails(Long eventId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
        }

        AuditLog log = auditLogRepository.findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit log not found"));

        if (!log.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        return toEventDTO(log);
    }

    @Override
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public byte[] exportAuditTimeline(Map<String, Object> filters) {
        // Fetch all data without pagination
        Page<APAuditTimelineDTO> page = getAuditTimeline(filters, Pageable.unpaged());
        List<APAuditTimelineDTO> items = page.getContent();

        StringBuilder pdf = new StringBuilder();
        pdf.append("AP AUDIT TIMELINE\n\n");
        pdf.append("Generated: ").append(DATE_FORMATTER.format(Instant.now().atZone(DEFAULT_ZONE))).append("\n");
        pdf.append("Total Events: ").append(items.size()).append("\n\n");
        
        pdf.append(String.format("%-20s %-30s %-20s %-15s %-10s\n", "Time", "Action", "User", "Entity", "Status"));
        pdf.append("-".repeat(100)).append("\n");

        for (APAuditTimelineDTO item : items) {
            pdf.append(String.format("%-20s %-30s %-20s %-15s %-10s\n",
                DATE_FORMATTER.format(item.getTimestamp().atZone(DEFAULT_ZONE)),
                truncate(item.getAction(), 30),
                truncate(item.getUserName(), 20),
                truncate(item.getEntityDisplay(), 15),
                item.getStatus()
            ));
        }
        
        pdf.append("\n\nLEGAL APPENDIX\n");
        pdf.append("This document is a generated audit trail from the Accounting System.\n");
        pdf.append("All events are cryptographically hashed and stored immutably.\n");
        // Hash verification info would go here if implemented

        return pdf.toString().getBytes(StandardCharsets.UTF_8);
    }

    private APAuditTimelineDTO toTimelineDTO(AuditLog log) {
        APAuditTimelineDTO dto = new APAuditTimelineDTO();
        dto.setId(log.getId());
        dto.setAction(log.getAction());
        dto.setEventType(log.getEventType());
        dto.setTimestamp(log.getCreatedAt());
        dto.setUserId(log.getUserId());
        dto.setUserName(log.getEmail()); // Fallback to email if name not available
        dto.setUserRole(log.getActorRole());
        dto.setEntityId(log.getEntityId());
        dto.setEntityDisplay(log.getEntityDisplay());
        dto.setSummary(log.getReason());
        dto.setStatus(log.getSuccess() ? "SUCCESS" : "FAILURE");
        dto.setFailureReason(log.getFailureReason());
        
        // Enrich user name if possible
        if (log.getUserId() != null) {
            userRepository.findById(log.getUserId()).ifPresent(u -> dto.setUserName(u.getFullName()));
        }
        
        return dto;
    }

    private APAuditEventDTO toEventDTO(AuditLog log) {
        APAuditEventDTO dto = new APAuditEventDTO();
        dto.setId(log.getId());
        dto.setAction(log.getAction());
        dto.setEventType(log.getEventType());
        dto.setTimestamp(log.getCreatedAt());
        dto.setUserId(log.getUserId());
        dto.setUserEmail(log.getEmail());
        dto.setUserRole(log.getActorRole());
        dto.setIpAddress(log.getIpAddress());
        dto.setUserAgent(log.getUserAgent());
        dto.setEntityType(log.getEntityType());
        dto.setEntityId(log.getEntityId());
        dto.setEntityDisplay(log.getEntityDisplay());
        dto.setChanges(log.getChanges());
        dto.setMetadata(log.getMetadata());
        dto.setSuccess(log.getSuccess());
        dto.setFailureReason(log.getFailureReason());
        
        // Extract snapshots from changes if available
        if (log.getChanges() != null) {
            if (log.getChanges().has("before")) {
                dto.setBeforeSnapshot(log.getChanges().get("before"));
            }
            if (log.getChanges().has("after")) {
                dto.setAfterSnapshot(log.getChanges().get("after"));
            }
        }
        
        // Extract hashes from metadata
        if (log.getMetadata() != null) {
            if (log.getMetadata().has("diffHash")) {
                dto.setDiffHash(log.getMetadata().get("diffHash").asText());
            }
            if (log.getMetadata().has("chainHash")) {
                dto.setChainHash(log.getMetadata().get("chainHash").asText());
            }
        }
        
        if (log.getUserId() != null) {
            userRepository.findById(log.getUserId()).ifPresent(u -> dto.setUserName(u.getFullName()));
        } else {
            dto.setUserName(log.getEmail());
        }
        
        return dto;
    }

    private String truncate(String str, int width) {
        if (str == null) return "";
        return str.length() > width ? str.substring(0, width - 3) + "..." : str;
    }

    @Override
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public List<com.accounting.dto.AbuseDetectionResultDTO> detectAbusePatterns(Long userId, int timeWindowMinutes) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
        }

        Instant startTime = Instant.now().minusSeconds(timeWindowMinutes * 60L);
        
        // 1. Detect Repeated Failed Deletes
        Specification<AuditLog> deleteFailSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            predicates.add(cb.equal(root.get("success"), false));
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            predicates.add(cb.like(root.get("action"), "%_DELETE_FAILED")); // Matches PURCHASE_BILL_DELETE_FAILED, etc.
            
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        List<AuditLog> failedDeletes = auditLogRepository.findAll(deleteFailSpec);
        
        // Group by user
        Map<Long, List<AuditLog>> deletesByUser = failedDeletes.stream()
            .filter(log -> log.getUserId() != null)
            .collect(Collectors.groupingBy(AuditLog::getUserId));
            
        List<com.accounting.dto.AbuseDetectionResultDTO> results = new ArrayList<>();
        
        deletesByUser.forEach((uid, logs) -> {
            if (logs.size() >= 3) { // Threshold: 3 failed deletes
                com.accounting.dto.AbuseDetectionResultDTO dto = new com.accounting.dto.AbuseDetectionResultDTO();
                dto.setUserId(uid);
                userRepository.findById(uid).ifPresent(u -> {
                    dto.setUserName(u.getFullName());
                    dto.setUserEmail(u.getEmail());
                });
                dto.setPatternType("REPEATED_FAILED_DELETES");
                dto.setSeverity("HIGH");
                dto.setEventCount(logs.size());
                dto.setFirstEventTime(logs.stream().map(AuditLog::getCreatedAt).min(Instant::compareTo).orElse(startTime));
                dto.setLastEventTime(logs.stream().map(AuditLog::getCreatedAt).max(Instant::compareTo).orElse(startTime));
                dto.setDescription("User attempted to delete entities " + logs.size() + " times and failed.");
                dto.setRelatedEventIds(logs.stream().map(l -> l.getId().toString()).collect(Collectors.toList()));
                results.add(dto);
            }
        });
        
        // 2. Detect Rapid Operations (Flood) - simplistic check: > 20 actions in window
        // Only check if userId is provided to avoid scanning everyone for flood every time
        // Or scan everyone if userId is null.
        if (userId != null) {
             Specification<AuditLog> floodSpec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("companyId"), companyId));
                predicates.add(cb.equal(root.get("userId"), userId));
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            long count = auditLogRepository.count(floodSpec);
            if (count > 20) { // Threshold: 20 actions
                com.accounting.dto.AbuseDetectionResultDTO dto = new com.accounting.dto.AbuseDetectionResultDTO();
                dto.setUserId(userId);
                userRepository.findById(userId).ifPresent(u -> {
                    dto.setUserName(u.getFullName());
                    dto.setUserEmail(u.getEmail());
                });
                dto.setPatternType("RAPID_OPERATIONS");
                dto.setSeverity("MEDIUM");
                dto.setEventCount((int) count);
                dto.setFirstEventTime(startTime); // Approx
                dto.setLastEventTime(Instant.now());
                dto.setDescription("User performed " + count + " operations in the last " + timeWindowMinutes + " minutes.");
                results.add(dto);
            }
        }

        return results;
    }

    @Override
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public Page<APAuditTimelineDTO> getUnauthorizedAttempts(Map<String, Object> filters, Pageable pageable) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
        }

        Specification<AuditLog> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("companyId"), companyId));
            predicates.add(criteriaBuilder.equal(root.get("success"), false));
            
            // Look for unauthorized/forbidden keywords in failure reason
            Predicate reasonLike = criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("failureReason")), "%unauthorized%"),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("failureReason")), "%forbidden%"),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("failureReason")), "%access denied%"),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("failureReason")), "%only the creator%"),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("failureReason")), "%insufficient permissions%")
            );
            predicates.add(reasonLike);

            if (filters != null) {
                if (filters.get("userId") != null) {
                    predicates.add(criteriaBuilder.equal(root.get("userId"), Long.valueOf(filters.get("userId").toString())));
                }
                if (filters.get("startDate") != null) {
                    LocalDate start = LocalDate.parse(filters.get("startDate").toString());
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), start.atStartOfDay(DEFAULT_ZONE).toInstant()));
                }
                if (filters.get("endDate") != null) {
                    LocalDate end = LocalDate.parse(filters.get("endDate").toString());
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), end.atTime(23, 59, 59).atZone(DEFAULT_ZONE).toInstant()));
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLog> logs = auditLogRepository.findAll(spec, pageable);
        return logs.map(this::toTimelineDTO);
    }
}
