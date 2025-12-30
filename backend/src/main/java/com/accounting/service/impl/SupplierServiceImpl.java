package com.accounting.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.SupplierAPSummaryDTO;
import com.accounting.dto.SupplierCreateRequest;
import com.accounting.dto.SupplierDTO;
import com.accounting.dto.SupplierUpdateRequest;
import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.entity.Supplier;
import com.accounting.repository.SupplierRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.EmbeddingTriggerService;
import com.accounting.service.SupplierService;
import com.accounting.service.util.SupplierCodeGenerator;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Implementation of SupplierService for supplier CRUD operations.
 */
@Service
@Transactional
public class SupplierServiceImpl implements SupplierService {

  private static final Logger log = LoggerFactory.getLogger(SupplierServiceImpl.class);

  private final SupplierRepository supplierRepository;
  private final SupplierCodeGenerator codeGenerator;
  private final AuditService auditService;
  private final EmbeddingTriggerService embeddingTriggerService;

  public SupplierServiceImpl(
      SupplierRepository supplierRepository,
      SupplierCodeGenerator codeGenerator,
      AuditService auditService,
      EmbeddingTriggerService embeddingTriggerService) {
    this.supplierRepository = supplierRepository;
    this.codeGenerator = codeGenerator;
    this.auditService = auditService;
    this.embeddingTriggerService = embeddingTriggerService;
  }

  /**
   * Get current HTTP request from RequestContextHolder.
   */
  private HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }

  /**
   * Get current user ID from security context.
   * Returns null if authentication is not available.
   * Logs a warning if user ID cannot be retrieved (non-blocking).
   */
  private Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      log.warn("Cannot retrieve user ID: Authentication not available in SecurityContext");
      return null;
    }
    try {
      return Long.parseLong(authentication.getPrincipal().toString());
    } catch (NumberFormatException e) {
      log.warn("Cannot retrieve user ID: Invalid principal format - {}", authentication.getPrincipal(), e);
      return null;
    }
  }

  @Override
  public Page<SupplierDTO> findAll(Pageable pageable, Boolean status, String search) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // If search is provided, use native query with unaccent function
    if (search != null && !search.isBlank()) {
      String trimmedSearch = search.trim();
      List<Supplier> suppliers = supplierRepository.searchByCodeOrNameNative(companyId, trimmedSearch);

      // Apply status filter in-memory
      if (status != null) {
        suppliers = suppliers.stream()
            .filter(s -> s.getActive().equals(status))
            .toList();
      }

      // Convert to DTO and create page
      // NOTE: Search uses native query which returns all matching results, then pagination is applied in-memory.
      // This is acceptable for MVP as search typically returns small result sets. For production with large datasets,
      // consider implementing database-level pagination in the native query (LIMIT/OFFSET) or using a full-text search solution.
      List<SupplierDTO> dtos = suppliers.stream().map(this::toDTO).toList();
      return new org.springframework.data.domain.PageImpl<>(dtos, pageable, dtos.size());
    }

    // Build specification with company scope and optional filters
    Specification<Supplier> spec = (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Always filter by company
      predicates.add(criteriaBuilder.equal(root.get("companyId"), companyId));

      // Filter by active status
      if (status != null) {
        predicates.add(criteriaBuilder.equal(root.get("active"), status));
      }

      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };

    Page<Supplier> suppliers = supplierRepository.findAll(spec, pageable);
    return suppliers.map(this::toDTO);
  }

  @Override
  public Optional<SupplierDTO> getSupplierById(Long supplierId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    return supplierRepository
        .findByCompanyIdAndId(companyId, supplierId)
        .map(this::toDTO);
  }

  @Override
  public SupplierDTO create(SupplierCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Check for duplicates
    Optional<Supplier> duplicate = checkDuplicate(
        request.getTaxCode(), request.getEmail(), request.getPhone(), null);
    if (duplicate.isPresent()) {
      Supplier dup = duplicate.get();
      String conflictMessage = buildConflictMessage(dup, request.getTaxCode(), request.getEmail(), request.getPhone());
      throw new ResponseStatusException(HttpStatus.CONFLICT, conflictMessage);
    }

    // Generate supplier code if not provided
    String code = request.getCode() != null && !request.getCode().trim().isEmpty()
        ? request.getCode().trim()
        : codeGenerator.generateCode(companyId);

    // Create supplier entity
    Supplier supplier = new Supplier();
    supplier.setCompanyId(companyId);
    supplier.setCode(code);
    supplier.setName(request.getName());
    supplier.setTaxCode(request.getTaxCode());
    supplier.setAddress(request.getAddress());
    supplier.setEmail(request.getEmail());
    supplier.setPhone(request.getPhone());
    supplier.setActive(request.getActive() != null ? request.getActive() : true);

    Supplier saved = supplierRepository.save(supplier);
    
    Map<String, String> newValues = new HashMap<>();
    newValues.put("code", saved.getCode());
    newValues.put("name", saved.getName());
    newValues.put("taxCode", saved.getTaxCode() != null ? saved.getTaxCode() : "");
    newValues.put("email", saved.getEmail() != null ? saved.getEmail() : "");
    newValues.put("phone", saved.getPhone() != null ? saved.getPhone() : "");
    newValues.put("address", saved.getAddress() != null ? saved.getAddress() : "");
    newValues.put("active", String.valueOf(saved.getActive()));

    auditService.logSupplierCreated(
        saved.getId(), saved.getCode(), getCurrentUserId(), newValues, getCurrentRequest());
    
    embeddingTriggerService.triggerSupplierEmbedding(saved, EmbeddingAction.UPSERT);
    
    return toDTO(saved);
  }

  @Override
  public SupplierDTO update(Long supplierId, SupplierUpdateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    Supplier supplier = supplierRepository
        .findByCompanyIdAndId(companyId, supplierId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));

    // Check for duplicates (excluding current supplier)
    if (request.getTaxCode() != null || request.getEmail() != null || request.getPhone() != null) {
      Optional<Supplier> duplicate = checkDuplicate(
          request.getTaxCode(), request.getEmail(), request.getPhone(), supplierId);
      if (duplicate.isPresent()) {
        Supplier dup = duplicate.get();
        String conflictMessage = buildConflictMessage(dup, request.getTaxCode(), request.getEmail(), request.getPhone());
        throw new ResponseStatusException(HttpStatus.CONFLICT, conflictMessage);
      }
    }

    // Capture old values for audit BEFORE updating
    Map<String, String> oldValues = new HashMap<>();
    oldValues.put("name", supplier.getName());
    oldValues.put("taxCode", supplier.getTaxCode() != null ? supplier.getTaxCode() : "");
    oldValues.put("email", supplier.getEmail() != null ? supplier.getEmail() : "");
    oldValues.put("phone", supplier.getPhone() != null ? supplier.getPhone() : "");
    oldValues.put("address", supplier.getAddress() != null ? supplier.getAddress() : "");
    oldValues.put("active", String.valueOf(supplier.getActive()));

    // Update fields (only non-null fields)
    if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
      supplier.setCode(request.getCode().trim());
    }
    if (request.getName() != null) {
      supplier.setName(request.getName());
    }
    if (request.getTaxCode() != null) {
      supplier.setTaxCode(request.getTaxCode());
    }
    if (request.getAddress() != null) {
      supplier.setAddress(request.getAddress());
    }
    if (request.getEmail() != null) {
      supplier.setEmail(request.getEmail());
    }
    if (request.getPhone() != null) {
      supplier.setPhone(request.getPhone());
    }
    if (request.getActive() != null) {
      supplier.setActive(request.getActive());
    }

    Supplier updated = supplierRepository.save(supplier);
    
    // Capture new values for audit
    Map<String, String> newValues = new HashMap<>();
    newValues.put("name", updated.getName());
    newValues.put("taxCode", updated.getTaxCode() != null ? updated.getTaxCode() : "");
    newValues.put("email", updated.getEmail() != null ? updated.getEmail() : "");
    newValues.put("phone", updated.getPhone() != null ? updated.getPhone() : "");
    newValues.put("address", updated.getAddress() != null ? updated.getAddress() : "");
    newValues.put("active", String.valueOf(updated.getActive()));
    
    // Audit log
    auditService.logSupplierUpdated(updated.getId(), updated.getCode(), getCurrentUserId(), oldValues, newValues, getCurrentRequest());
    
    embeddingTriggerService.triggerSupplierEmbedding(updated, EmbeddingAction.UPSERT);
    
    return toDTO(updated);
  }

  @Override
  public void delete(Long supplierId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    Supplier supplier = supplierRepository
        .findByCompanyIdAndId(companyId, supplierId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));

    // Referential integrity check: Cannot delete supplier with linked bills/payments
    // Note: Epic 4 (AP Module) is not yet implemented, so we cannot check for actual references
    // For MVP, we block deletion and recommend deactivation instead
    String supplierCode = supplier.getCode();
    
    // Log blocked deletion attempt
    auditService.logSupplierDeleted(
        supplierId, 
        supplierCode, 
        "Deletion blocked: Referential integrity check requires Epic 4 (AP Module) data", 
        getCurrentUserId(), 
        getCurrentRequest());
    
    // TODO: When deletion is enabled, uncomment this line to trigger embedding delete:
    // embeddingTriggerService.triggerSupplierEmbedding(supplier, EmbeddingAction.DELETE);
    
    throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Cannot delete supplier: Referential integrity check requires Epic 4 (AP Module) data. "
            + "Please deactivate the supplier instead. Deactivation preserves linked AP data.");
  }

  @Override
  public void activate(Long supplierId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    Supplier supplier = supplierRepository
        .findByCompanyIdAndId(companyId, supplierId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));

    supplier.setActive(true);
    supplierRepository.save(supplier);
    
    // Audit log
    auditService.logSupplierActivated(supplierId, supplier.getCode(), getCurrentUserId(), getCurrentRequest());
  }

  @Override
  public void deactivate(Long supplierId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    Supplier supplier = supplierRepository
        .findByCompanyIdAndId(companyId, supplierId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));

    supplier.setActive(false);
    supplierRepository.save(supplier);
    
    // Audit log
    auditService.logSupplierDeactivated(supplierId, supplier.getCode(), getCurrentUserId(), getCurrentRequest());
  }

  @Override
  public Optional<Supplier> checkDuplicate(String taxCode, String email, String phone, Long excludeId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      return Optional.empty();
    }

    // Only check if at least one field is provided
    if (taxCode == null && email == null && phone == null) {
      return Optional.empty();
    }

    return supplierRepository.findDuplicate(companyId, taxCode, email, phone)
        .filter(s -> excludeId == null || !s.getId().equals(excludeId));
  }

  @Override
  public SupplierAPSummaryDTO getSupplierAPSummary(Long supplierId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Verify supplier exists and belongs to company
    supplierRepository
        .findByCompanyIdAndId(companyId, supplierId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));

    // TODO: Implement AP summary when Epic 4 (AP Module) is ready
    // For MVP, return placeholder values
    return new SupplierAPSummaryDTO(0, BigDecimal.ZERO, 0);
  }

  /**
   * Build conflict message for duplicate supplier detection.
   * 
   * @param duplicate the duplicate supplier found
   * @param taxCode the tax code being checked (may be null)
   * @param email the email being checked (may be null)
   * @param phone the phone being checked (may be null)
   * @return formatted conflict message
   */
  private String buildConflictMessage(Supplier duplicate, String taxCode, String email, String phone) {
    String conflict = "";
    if (taxCode != null && duplicate.getTaxCode() != null && duplicate.getTaxCode().equals(taxCode)) {
      conflict = "tax code: " + duplicate.getTaxCode();
    } else if (email != null && duplicate.getEmail() != null && duplicate.getEmail().equals(email)) {
      conflict = "email: " + duplicate.getEmail();
    } else if (phone != null && duplicate.getPhone() != null && duplicate.getPhone().equals(phone)) {
      conflict = "phone: " + duplicate.getPhone();
    }
    return "Duplicate supplier found with " + conflict + " (Code: " + duplicate.getCode() + ", Name: " + duplicate.getName() + ")";
  }

  /**
   * Convert Supplier entity to DTO.
   */
  private SupplierDTO toDTO(Supplier supplier) {
    return new SupplierDTO(
        supplier.getId(),
        supplier.getCompanyId(),
        supplier.getCode(),
        supplier.getName(),
        supplier.getTaxCode(),
        supplier.getAddress(),
        supplier.getEmail(),
        supplier.getPhone(),
        supplier.getActive(),
        supplier.getCreatedAt(),
        supplier.getUpdatedAt());
  }
}
