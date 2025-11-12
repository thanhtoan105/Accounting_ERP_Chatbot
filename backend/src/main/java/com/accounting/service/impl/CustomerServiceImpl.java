package com.accounting.service.impl;

import com.accounting.dto.CustomerARSummaryDTO;
import com.accounting.dto.CustomerCreateRequest;
import com.accounting.dto.CustomerDTO;
import com.accounting.dto.CustomerUpdateRequest;
import com.accounting.entity.Customer;
import com.accounting.repository.CustomerRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.CustomerService;
import com.accounting.service.util.CustomerCodeGenerator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of CustomerService for customer CRUD operations.
 */
@Service
@Transactional
public class CustomerServiceImpl implements CustomerService {

  private final CustomerRepository customerRepository;
  private final CustomerCodeGenerator codeGenerator;
  private final AuditService auditService;

  public CustomerServiceImpl(
      CustomerRepository customerRepository,
      CustomerCodeGenerator codeGenerator,
      AuditService auditService) {
    this.customerRepository = customerRepository;
    this.codeGenerator = codeGenerator;
    this.auditService = auditService;
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
   */
  private Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      return null;
    }
    try {
      return Long.parseLong(authentication.getPrincipal().toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public Page<CustomerDTO> findAll(Pageable pageable, Boolean status, String search) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // If search is provided, use native query with unaccent function
    if (search != null && !search.isBlank()) {
      String trimmedSearch = search.trim();
      List<Customer> customers = customerRepository.searchByCodeOrNameNative(companyId, trimmedSearch);

      // Apply status filter in-memory
      if (status != null) {
        customers = customers.stream()
            .filter(c -> c.getActive().equals(status))
            .toList();
      }

      // Convert to DTO and create page
      // NOTE: Search uses native query which returns all matching results, then pagination is applied in-memory.
      // This is acceptable for MVP as search typically returns small result sets. For production with large datasets,
      // consider implementing database-level pagination in the native query (LIMIT/OFFSET) or using a full-text search solution.
      List<CustomerDTO> dtos = customers.stream().map(this::toDTO).toList();
      return new org.springframework.data.domain.PageImpl<>(dtos, pageable, dtos.size());
    }

    // Build specification with company scope and optional filters
    Specification<Customer> spec = (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Always filter by company
      predicates.add(criteriaBuilder.equal(root.get("companyId"), companyId));

      // Filter by active status
      if (status != null) {
        predicates.add(criteriaBuilder.equal(root.get("active"), status));
      }

      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };

    Page<Customer> customers = customerRepository.findAll(spec, pageable);
    return customers.map(this::toDTO);
  }

  @Override
  public Optional<CustomerDTO> getCustomerById(Long customerId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    return customerRepository
        .findByCompanyIdAndId(companyId, customerId)
        .map(this::toDTO);
  }

  @Override
  public CustomerDTO create(CustomerCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Check for duplicates
    Optional<Customer> duplicate = checkDuplicate(
        request.getTaxCode(), request.getEmail(), request.getPhone(), null);
    if (duplicate.isPresent()) {
      Customer dup = duplicate.get();
      String conflict = "";
      if (request.getTaxCode() != null && dup.getTaxCode() != null && dup.getTaxCode().equals(request.getTaxCode())) {
        conflict = "tax code: " + dup.getTaxCode();
      } else if (request.getEmail() != null && dup.getEmail() != null && dup.getEmail().equals(request.getEmail())) {
        conflict = "email: " + dup.getEmail();
      } else if (request.getPhone() != null && dup.getPhone() != null && dup.getPhone().equals(request.getPhone())) {
        conflict = "phone: " + dup.getPhone();
      }
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Duplicate customer found with " + conflict + " (Code: " + dup.getCode() + ", Name: " + dup.getName() + ")");
    }

    // Generate customer code if not provided
    String code = request.getCode() != null && !request.getCode().trim().isEmpty()
        ? request.getCode().trim()
        : codeGenerator.generateCode(companyId);

    // Create customer entity
    Customer customer = new Customer();
    customer.setCompanyId(companyId);
    customer.setCode(code);
    customer.setName(request.getName());
    customer.setTaxCode(request.getTaxCode());
    customer.setAddress(request.getAddress());
    customer.setEmail(request.getEmail());
    customer.setPhone(request.getPhone());
    customer.setActive(request.getActive() != null ? request.getActive() : true);

    Customer saved = customerRepository.save(customer);
    
    // Audit log
    auditService.logCustomerCreated(saved.getId(), saved.getCode(), getCurrentUserId(), getCurrentRequest());
    
    return toDTO(saved);
  }

  @Override
  public CustomerDTO update(Long customerId, CustomerUpdateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    Customer customer = customerRepository
        .findByCompanyIdAndId(companyId, customerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

    // Check for duplicates (excluding current customer)
    if (request.getTaxCode() != null || request.getEmail() != null || request.getPhone() != null) {
      Optional<Customer> duplicate = checkDuplicate(
          request.getTaxCode(), request.getEmail(), request.getPhone(), customerId);
      if (duplicate.isPresent()) {
        Customer dup = duplicate.get();
        String conflict = "";
        if (request.getTaxCode() != null && dup.getTaxCode() != null && dup.getTaxCode().equals(request.getTaxCode())) {
          conflict = "tax code: " + dup.getTaxCode();
        } else if (request.getEmail() != null && dup.getEmail() != null && dup.getEmail().equals(request.getEmail())) {
          conflict = "email: " + dup.getEmail();
        } else if (request.getPhone() != null && dup.getPhone() != null && dup.getPhone().equals(request.getPhone())) {
          conflict = "phone: " + dup.getPhone();
        }
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Duplicate customer found with " + conflict + " (Code: " + dup.getCode() + ", Name: " + dup.getName() + ")");
      }
    }

    // Capture old values for audit BEFORE updating
    Map<String, String> oldValues = new HashMap<>();
    oldValues.put("name", customer.getName());
    oldValues.put("taxCode", customer.getTaxCode() != null ? customer.getTaxCode() : "");
    oldValues.put("email", customer.getEmail() != null ? customer.getEmail() : "");
    oldValues.put("phone", customer.getPhone() != null ? customer.getPhone() : "");
    oldValues.put("address", customer.getAddress() != null ? customer.getAddress() : "");
    oldValues.put("active", String.valueOf(customer.getActive()));

    // Update fields (only non-null fields)
    if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
      customer.setCode(request.getCode().trim());
    }
    if (request.getName() != null) {
      customer.setName(request.getName());
    }
    if (request.getTaxCode() != null) {
      customer.setTaxCode(request.getTaxCode());
    }
    if (request.getAddress() != null) {
      customer.setAddress(request.getAddress());
    }
    if (request.getEmail() != null) {
      customer.setEmail(request.getEmail());
    }
    if (request.getPhone() != null) {
      customer.setPhone(request.getPhone());
    }
    if (request.getActive() != null) {
      customer.setActive(request.getActive());
    }

    Customer updated = customerRepository.save(customer);
    
    // Capture new values for audit
    Map<String, String> newValues = new HashMap<>();
    newValues.put("name", updated.getName());
    newValues.put("taxCode", updated.getTaxCode() != null ? updated.getTaxCode() : "");
    newValues.put("email", updated.getEmail() != null ? updated.getEmail() : "");
    newValues.put("phone", updated.getPhone() != null ? updated.getPhone() : "");
    newValues.put("address", updated.getAddress() != null ? updated.getAddress() : "");
    newValues.put("active", String.valueOf(updated.getActive()));
    
    // Audit log
    auditService.logCustomerUpdated(updated.getId(), updated.getCode(), getCurrentUserId(), oldValues, newValues, getCurrentRequest());
    
    return toDTO(updated);
  }

  @Override
  public void delete(Long customerId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    Customer customer = customerRepository
        .findByCompanyIdAndId(companyId, customerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

    // Referential integrity check: Cannot delete customer with linked invoices/payments
    // Note: Epic 5 (AR Module) is not yet implemented, so we cannot check for actual references
    // For MVP, we block deletion and recommend deactivation instead
    String customerCode = customer.getCode();
    
    // Log blocked deletion attempt
    auditService.logCustomerDeleted(
        customerId, 
        customerCode, 
        "Deletion blocked: Referential integrity check requires Epic 5 (AR Module) data", 
        getCurrentUserId(), 
        getCurrentRequest());
    
    throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Cannot delete customer: Referential integrity check requires Epic 5 (AR Module) data. "
            + "Please deactivate the customer instead. Deactivation preserves linked AR data.");
  }

  @Override
  public void activate(Long customerId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    Customer customer = customerRepository
        .findByCompanyIdAndId(companyId, customerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

    customer.setActive(true);
    customerRepository.save(customer);
    
    // Audit log
    auditService.logCustomerActivated(customerId, customer.getCode(), getCurrentUserId(), getCurrentRequest());
  }

  @Override
  public void deactivate(Long customerId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    Customer customer = customerRepository
        .findByCompanyIdAndId(companyId, customerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

    customer.setActive(false);
    customerRepository.save(customer);
    
    // Audit log
    auditService.logCustomerDeactivated(customerId, customer.getCode(), getCurrentUserId(), getCurrentRequest());
  }

  @Override
  public Optional<Customer> checkDuplicate(String taxCode, String email, String phone, Long excludeId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      return Optional.empty();
    }

    // Only check if at least one field is provided
    if (taxCode == null && email == null && phone == null) {
      return Optional.empty();
    }

    return customerRepository.findDuplicate(companyId, taxCode, email, phone)
        .filter(c -> excludeId == null || !c.getId().equals(excludeId));
  }

  @Override
  public CustomerARSummaryDTO getCustomerARSummary(Long customerId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Verify customer exists and belongs to company
    customerRepository
        .findByCompanyIdAndId(companyId, customerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

    // TODO: Implement AR summary when Epic 5 (AR Module) is ready
    // For MVP, return placeholder values
    return new CustomerARSummaryDTO(0, BigDecimal.ZERO, 0);
  }

  /**
   * Convert Customer entity to DTO.
   */
  private CustomerDTO toDTO(Customer customer) {
    return new CustomerDTO(
        customer.getId(),
        customer.getCompanyId(),
        customer.getCode(),
        customer.getName(),
        customer.getTaxCode(),
        customer.getAddress(),
        customer.getEmail(),
        customer.getPhone(),
        customer.getActive(),
        customer.getCreatedAt(),
        customer.getUpdatedAt());
  }
}

