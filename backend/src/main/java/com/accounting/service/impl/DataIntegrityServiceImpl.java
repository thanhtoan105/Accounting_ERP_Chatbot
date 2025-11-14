package com.accounting.service.impl;

import com.accounting.dto.integrity.DataIntegrityFindingDTO;
import com.accounting.dto.integrity.DataIntegrityJobResponse;
import com.accounting.dto.integrity.DataIntegrityRequest;
import com.accounting.entity.BankAccount;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Customer;
import com.accounting.entity.DataIntegrityFinding;
import com.accounting.entity.DataIntegrityJob;
import com.accounting.entity.DataIntegrityJobStatus;
import com.accounting.entity.DataIntegritySeverity;
import com.accounting.entity.Supplier;
import com.accounting.entity.User;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.DataIntegrityFindingRepository;
import com.accounting.repository.DataIntegrityJobRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.DataIntegrityService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DataIntegrityServiceImpl implements DataIntegrityService {

  private static final Duration THROTTLE_DURATION = Duration.ofMinutes(10);
  private static final Set<String> SUPPORTED_ENTITIES = Set.of("CUSTOMER", "SUPPLIER", "CHART_OF_ACCOUNT", "BANK_ACCOUNT");
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final DataIntegrityJobRepository jobRepository;
  private final DataIntegrityFindingRepository findingRepository;
  private final CustomerRepository customerRepository;
  private final SupplierRepository supplierRepository;
  private final ChartOfAccountsRepository chartRepository;
  private final BankAccountRepository bankAccountRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public DataIntegrityServiceImpl(
      DataIntegrityJobRepository jobRepository,
      DataIntegrityFindingRepository findingRepository,
      CustomerRepository customerRepository,
      SupplierRepository supplierRepository,
      ChartOfAccountsRepository chartRepository,
      BankAccountRepository bankAccountRepository,
      UserRepository userRepository,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.jobRepository = jobRepository;
    this.findingRepository = findingRepository;
    this.customerRepository = customerRepository;
    this.supplierRepository = supplierRepository;
    this.chartRepository = chartRepository;
    this.bankAccountRepository = bankAccountRepository;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Override
  public DataIntegrityJobResponse triggerScan(DataIntegrityRequest request, HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    User actor = resolveCurrentUser();
    if (actor == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required");
    }

    // Check throttling BEFORE creating the job to avoid creating a job that will be throttled
    Instant now = Instant.now();
    if (!request.throttleBypass() && isThrottled(companyId, now)) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Integrity scan throttled. Please retry later or pass throttleBypass=true.");
    }

    DataIntegrityJob job = new DataIntegrityJob();
    job.setCompanyId(companyId);
    job.setTriggeredByUserId(actor.getId());
    job.setTriggeredByEmail(actor.getEmail());
    job.setTriggeredByRole(actor.getRole());
    job.setStatus(DataIntegrityJobStatus.RUNNING);
    jobRepository.save(job);

    Set<String> targets = resolveTargets(request.entities());
    List<DataIntegrityFinding> findings = new ArrayList<>();
    List<DataIntegrityFindingDTO> dtoFindings = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    if (targets.contains("CUSTOMER")) {
      scanCustomers(job, companyId, findings, dtoFindings);
    }
    if (targets.contains("SUPPLIER")) {
      scanSuppliers(job, companyId, findings, dtoFindings);
    }
    if (targets.contains("CHART_OF_ACCOUNT")) {
      scanChartOfAccounts(job, companyId, findings, dtoFindings);
    }
    if (targets.contains("BANK_ACCOUNT")) {
      scanBankAccounts(job, companyId, findings, dtoFindings);
    }

    findingRepository.saveAll(findings);

    job.setFindingsCount(findings.size());
    job.setStatus(DataIntegrityJobStatus.COMPLETED);
    job.setCompletedAt(Instant.now());
    if (!warnings.isEmpty()) {
      job.setWarnings(objectMapper.valueToTree(warnings));
    }
    job.setSummary(generateSummary(findings.size(), warnings));
    jobRepository.save(job);

    auditService.logDataIntegrityScan(job.getId(), companyId, actor.getId(), findings.size(), false, httpRequest);

    return toResponse(job, dtoFindings);
  }

  @Override
  @Transactional(readOnly = true)
  public DataIntegrityJobResponse getResults(UUID jobId) {
    DataIntegrityJob job = jobRepository.findById(jobId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integrity job not found"));
    List<DataIntegrityFinding> findings = findingRepository.findByJob(job);
    List<DataIntegrityFindingDTO> dtoFindings = findings.stream()
        .map(this::toFindingDto)
        .toList();
    return toResponse(job, dtoFindings);
  }

  private boolean isThrottled(Long companyId, Instant startedAt) {
    Optional<DataIntegrityJob> lastJobOpt = jobRepository.findTopByCompanyIdOrderByStartedAtDesc(companyId);
    if (lastJobOpt.isEmpty()) {
      return false;
    }
    DataIntegrityJob lastJob = lastJobOpt.get();
    if (lastJob.getStatus() == DataIntegrityJobStatus.THROTTLED) {
      return true;
    }
    Instant threshold = startedAt.minus(THROTTLE_DURATION);
    return lastJob.getStartedAt().isAfter(threshold);
  }

  private Set<String> resolveTargets(List<String> requestedEntities) {
    if (CollectionUtils.isEmpty(requestedEntities)) {
      return new HashSet<>(SUPPORTED_ENTITIES);
    }
    Set<String> normalized = new HashSet<>();
    for (String entity : requestedEntities) {
      if (entity == null) {
        continue;
      }
      String upper = entity.trim().toUpperCase();
      if (SUPPORTED_ENTITIES.contains(upper)) {
        normalized.add(upper);
      }
    }
    if (normalized.isEmpty()) {
      normalized.addAll(SUPPORTED_ENTITIES);
    }
    return normalized;
  }

  private void scanCustomers(
      DataIntegrityJob job,
      Long companyId,
      List<DataIntegrityFinding> findings,
      List<DataIntegrityFindingDTO> dtoFindings) {
    List<Customer> customers = customerRepository.findByCompanyId(companyId);
    Map<String, Long> taxCodeCounts = new HashMap<>();
    for (Customer customer : customers) {
      if (customer.getCompanyId() == null) {
        addFinding(job, findings, dtoFindings, "CUSTOMER", customer.getId(), "COMPANY_SCOPE",
            "Customer missing company association", DataIntegritySeverity.HIGH,
            Map.of("code", customer.getCode(), "name", customer.getName()));
      }
      if (StringUtils.hasText(customer.getTaxCode())) {
        taxCodeCounts.merge(customer.getTaxCode(), 1L, Long::sum);
      }
      if (!Boolean.TRUE.equals(customer.getActive()) && StringUtils.hasText(customer.getEmail())) {
        // Warning for inactive customer with email
      }
    }
    taxCodeCounts.forEach((taxCode, count) -> {
      if (count > 1) {
        addFinding(job, findings, dtoFindings, "CUSTOMER", null, "DUPLICATE_TAX_CODE",
            "Duplicate customer tax code detected: " + taxCode, DataIntegritySeverity.MEDIUM,
            Map.of("taxCode", taxCode, "occurrences", count));
      }
    });
  }

  private void scanSuppliers(
      DataIntegrityJob job,
      Long companyId,
      List<DataIntegrityFinding> findings,
      List<DataIntegrityFindingDTO> dtoFindings) {
    List<Supplier> suppliers = supplierRepository.findByCompanyId(companyId);
    Map<String, Long> taxCodeCounts = new HashMap<>();
    for (Supplier supplier : suppliers) {
      if (supplier.getCompanyId() == null) {
        addFinding(job, findings, dtoFindings, "SUPPLIER", supplier.getId(), "COMPANY_SCOPE",
            "Supplier missing company association", DataIntegritySeverity.HIGH,
            Map.of("code", supplier.getCode(), "name", supplier.getName()));
      }
      if (StringUtils.hasText(supplier.getTaxCode())) {
        taxCodeCounts.merge(supplier.getTaxCode(), 1L, Long::sum);
      }
    }
    taxCodeCounts.forEach((taxCode, count) -> {
      if (count > 1) {
        addFinding(job, findings, dtoFindings, "SUPPLIER", null, "DUPLICATE_TAX_CODE",
            "Duplicate supplier tax code detected: " + taxCode, DataIntegritySeverity.MEDIUM,
            Map.of("taxCode", taxCode, "occurrences", count));
      }
    });
  }

  private void scanChartOfAccounts(
      DataIntegrityJob job,
      Long companyId,
      List<DataIntegrityFinding> findings,
      List<DataIntegrityFindingDTO> dtoFindings) {
    List<ChartOfAccount> accounts = chartRepository.findByCompanyId(companyId);
    Map<Long, ChartOfAccount> accountById = new HashMap<>();
    for (ChartOfAccount account : accounts) {
      accountById.put(account.getId(), account);
    }

    for (ChartOfAccount account : accounts) {
      if (account.getParentId() != null) {
        ChartOfAccount parent = accountById.get(account.getParentId());
        if (parent == null) {
          addFinding(job, findings, dtoFindings, "CHART_OF_ACCOUNT", account.getId(), "ORPHANED_PARENT",
              "Account references missing parent", DataIntegritySeverity.HIGH,
              Map.of("code", account.getCode(), "parentId", account.getParentId()));
        } else if (!parent.getCompanyId().equals(account.getCompanyId())) {
          addFinding(job, findings, dtoFindings, "CHART_OF_ACCOUNT", account.getId(), "PARENT_SCOPE_MISMATCH",
              "Account parent belongs to different company", DataIntegritySeverity.HIGH,
              Map.of("code", account.getCode(), "parentCode", parent.getCode()));
        }
      }

      boolean hasChildren = accountById.values().stream()
          .anyMatch(child -> account.getId().equals(child.getParentId()));
      if (Boolean.TRUE.equals(account.getPostable()) && hasChildren) {
        addFinding(job, findings, dtoFindings, "CHART_OF_ACCOUNT", account.getId(), "POSTABLE_WITH_CHILDREN",
            "Postable account has child accounts", DataIntegritySeverity.MEDIUM,
            Map.of("code", account.getCode()));
      }
    }
  }

  private void scanBankAccounts(
      DataIntegrityJob job,
      Long companyId,
      List<DataIntegrityFinding> findings,
      List<DataIntegrityFindingDTO> dtoFindings) {
    List<BankAccount> accounts = bankAccountRepository.findByCompanyId(companyId);
    Map<String, Long> accountNumberCounts = new HashMap<>();
    for (BankAccount account : accounts) {
      if (!StringUtils.hasText(account.getAccountNumber())) {
        addFinding(job, findings, dtoFindings, "BANK_ACCOUNT", account.getId(), "MISSING_ACCOUNT_NUMBER",
            "Bank account missing account number", DataIntegritySeverity.HIGH,
            Map.of("bankName", account.getBankName()));
      } else {
        accountNumberCounts.merge(account.getAccountNumber(), 1L, Long::sum);
      }
    }
    accountNumberCounts.forEach((accNumber, count) -> {
      if (count > 1) {
        addFinding(job, findings, dtoFindings, "BANK_ACCOUNT", null, "DUPLICATE_ACCOUNT_NUMBER",
            "Duplicate bank account number detected: " + accNumber, DataIntegritySeverity.MEDIUM,
            Map.of("accountNumber", accNumber, "occurrences", count));
      }
    });
  }

  private void addFinding(
      DataIntegrityJob job,
      List<DataIntegrityFinding> entities,
      List<DataIntegrityFindingDTO> dtos,
      String entityType,
      Object entityId,
      String issueType,
      String description,
      DataIntegritySeverity severity,
      Map<String, Object> metadata) {
    DataIntegrityFinding finding = new DataIntegrityFinding();
    finding.setJob(job);
    finding.setEntityType(entityType);
    if (entityId != null) {
      finding.setEntityId(entityId.toString());
    }
    finding.setIssueType(issueType);
    finding.setDescription(description);
    finding.setSeverity(severity);
    finding.setMetadata(toJson(metadata));
    entities.add(finding);

    dtos.add(new DataIntegrityFindingDTO(
        entityType,
        entityId != null ? entityId.toString() : null,
        issueType,
        description,
        severity,
        metadata != null ? metadata : Map.of()));
  }

  private JsonNode toJson(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return null;
    }
    return objectMapper.valueToTree(metadata);
  }

  private DataIntegrityJobResponse toResponse(DataIntegrityJob job, List<DataIntegrityFindingDTO> findings) {
    Map<String, String> triggeredBy = Map.of(
        "userId", job.getTriggeredByUserId() != null ? job.getTriggeredByUserId().toString() : "",
        "email", job.getTriggeredByEmail() != null ? job.getTriggeredByEmail() : "",
        "role", job.getTriggeredByRole() != null ? job.getTriggeredByRole() : "");
    Map<String, Object> warnings = job.getWarnings() != null
        ? objectMapper.convertValue(job.getWarnings(), MAP_TYPE)
        : Map.of();
    return new DataIntegrityJobResponse(
        job.getId(),
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getStatus(),
        job.getFindingsCount(),
        triggeredBy,
        job.getSummary(),
        warnings,
        findings);
  }

  private DataIntegrityFindingDTO toFindingDto(DataIntegrityFinding finding) {
    Map<String, Object> metadata = finding.getMetadata() != null
        ? objectMapper.convertValue(finding.getMetadata(), MAP_TYPE)
        : Map.of();
    return new DataIntegrityFindingDTO(
        finding.getEntityType(),
        finding.getEntityId(),
        finding.getIssueType(),
        finding.getDescription(),
        finding.getSeverity(),
        metadata);
  }

  private String generateSummary(int findingsCount, List<String> warnings) {
    StringBuilder sb = new StringBuilder();
    if (findingsCount == 0) {
      sb.append("No integrity issues detected.");
    } else {
      sb.append(findingsCount).append(" potential issue(s) detected.");
    }
    if (!warnings.isEmpty()) {
      sb.append(" Warnings: ").append(String.join("; ", warnings));
    }
    return sb.toString();
  }

  private User resolveCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      return null;
    }
    try {
      Long userId = Long.parseLong(authentication.getPrincipal().toString());
      return userRepository.findById(userId).orElse(null);
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}

