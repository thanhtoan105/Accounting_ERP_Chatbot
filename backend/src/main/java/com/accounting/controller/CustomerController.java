package com.accounting.controller;

import com.accounting.dto.CustomerARSummaryDTO;
import com.accounting.dto.CustomerCreateRequest;
import com.accounting.dto.CustomerDTO;
import com.accounting.dto.CustomerUpdateRequest;
import com.accounting.service.AuditService;
import com.accounting.service.CustomerImportExportService;
import com.accounting.service.CustomerService;
import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

/**
 * REST controller for Customer operations.
 * All authenticated users can view customers; edit requires admin/accountant roles.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

  private final CustomerService customerService;
  private final CustomerImportExportService importExportService;
  private final AuditService auditService;

  public CustomerController(
      CustomerService customerService, 
      CustomerImportExportService importExportService,
      AuditService auditService) {
    this.customerService = customerService;
    this.importExportService = importExportService;
    this.auditService = auditService;
  }

  /**
   * Get customers with pagination, sorting, and filters.
   * Requires authenticated user (any role).
   *
   * @param page page number (0-based, default: 0)
   * @param size page size (default: 20)
   * @param sort sort field and direction (e.g., "name,asc" or "code,desc", default: "name,asc")
   * @param status filter by active status (optional: true = active, false = inactive)
   * @param search search term for code or name (optional, supports unaccented Vietnamese matching)
   * @return paginated list of customers
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getCustomers(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "name,asc") String sort,
      @RequestParam(required = false) Boolean status,
      @RequestParam(required = false) String search) {

    // Parse sort parameter
    String[] sortParts = sort.split(",");
    String sortField = sortParts[0];
    Sort.Direction direction = sortParts.length > 1 && "desc".equalsIgnoreCase(sortParts[1])
        ? Sort.Direction.DESC
        : Sort.Direction.ASC;

    Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
    Page<CustomerDTO> customers = customerService.findAll(pageable, status, search);

    Map<String, Object> body = new HashMap<>();
    body.put("data", customers.getContent());
    body.put("total", customers.getTotalElements());
    body.put("page", customers.getNumber());
    body.put("size", customers.getSize());
    body.put("totalPages", customers.getTotalPages());
    return ResponseEntity.ok(body);
  }

  /**
   * Get single customer by ID.
   * Requires authenticated user (any role).
   *
   * @param id customer ID
   * @return customer details
   */
  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getCustomerById(@PathVariable Long id) {
    return customerService
        .getCustomerById(id)
        .map(
            customer -> {
              Map<String, Object> body = new HashMap<>();
              body.put("data", customer);
              return ResponseEntity.ok(body);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Get customer AR summary (open invoices, total owed, average payment days).
   * Requires authenticated user (any role).
   *
   * @param id customer ID
   * @return AR summary
   */
  @GetMapping("/{id}/ar-summary")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getCustomerARSummary(@PathVariable Long id) {
    CustomerARSummaryDTO summary = customerService.getCustomerARSummary(id);
    Map<String, Object> body = new HashMap<>();
    body.put("data", summary);
    return ResponseEntity.ok(body);
  }

  /**
   * Create a new customer with auto-generated code.
   * Requires admin or accountant role.
   *
   * @param request create request with customer details
   * @return created customer
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> createCustomer(
      @Valid @RequestBody CustomerCreateRequest request) {
    try {
      CustomerDTO customer = customerService.create(request);
      Map<String, Object> body = new HashMap<>();
      body.put("data", customer);
      return ResponseEntity.status(HttpStatus.CREATED).body(body);
    } catch (org.springframework.web.server.ResponseStatusException e) {
      if (e.getStatusCode() == HttpStatus.CONFLICT) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", e.getReason());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
      }
      throw e;
    }
  }

  /**
   * Update an existing customer.
   * Requires admin or accountant role.
   *
   * @param id customer ID
   * @param request update request with customer details
   * @return updated customer
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> updateCustomer(
      @PathVariable Long id, @Valid @RequestBody CustomerUpdateRequest request) {
    try {
      CustomerDTO customer = customerService.update(id, request);
      Map<String, Object> body = new HashMap<>();
      body.put("data", customer);
      return ResponseEntity.ok(body);
    } catch (org.springframework.web.server.ResponseStatusException e) {
      if (e.getStatusCode() == HttpStatus.CONFLICT) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", e.getReason());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
      }
      throw e;
    }
  }

  /**
   * Delete a customer (hard delete).
   * Blocks deletion if customer has linked invoices/payments.
   * Requires admin or accountant role.
   *
   * @param id customer ID
   * @return no content or conflict if has references
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> deleteCustomer(@PathVariable Long id) {
    try {
      customerService.delete(id);
      return ResponseEntity.noContent().build();
    } catch (org.springframework.web.server.ResponseStatusException e) {
      if (e.getStatusCode() == HttpStatus.CONFLICT) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", e.getReason());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
      }
      throw e;
    }
  }

  /**
   * Activate a customer (set active=true).
   * Requires admin or accountant role.
   *
   * @param id customer ID
   * @return no content
   */
  @PatchMapping("/{id}/activate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Void> activateCustomer(@PathVariable Long id) {
    customerService.activate(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Deactivate a customer (set active=false).
   * Requires admin or accountant role.
   *
   * @param id customer ID
   * @return no content
   */
  @PatchMapping("/{id}/deactivate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Void> deactivateCustomer(@PathVariable Long id) {
    customerService.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Export customers to Excel or CSV format.
   * Requires authenticated user (any role).
   *
   * @param format export format ("xlsx", "xls", or "csv", default: "xlsx")
   * @param status filter by active status (optional)
   * @param search search term (optional)
   * @return file download
   */
  @GetMapping("/export")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Resource> exportCustomers(
      @RequestParam(defaultValue = "xlsx") String format,
      @RequestParam(required = false) Boolean status,
      @RequestParam(required = false) String search,
      HttpServletRequest request) {
    try {
      // Get all customers matching filters (no pagination for export)
      Page<CustomerDTO> customers = customerService.findAll(
          org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE), status, search);

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      importExportService.exportCustomers(customers.getContent(), format, outputStream);

      String contentType = "xlsx".equalsIgnoreCase(format) || "xls".equalsIgnoreCase(format)
          ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          : "text/csv";
      String extension = "csv".equalsIgnoreCase(format) ? "csv" : "xlsx";
      String filename = "customers_export_" + System.currentTimeMillis() + "." + extension;

      // Get current user ID for audit logging
      Long currentUserId = getCurrentUserId();
      
      // Log export action
      auditService.logCustomerExport(
          customers.getContent().size(), 
          format.toUpperCase(), 
          currentUserId, 
          request);

      ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());

      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
          .contentType(MediaType.parseMediaType(contentType))
          .body(resource);
    } catch (Exception e) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          "Failed to export customers: " + e.getMessage());
    }
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

  /**
   * Import customers from Excel or CSV file.
   * Requires admin or accountant role.
   *
   * @param file uploaded file
   * @return import result with success/error counts and error details
   */
  @PostMapping("/import")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> importCustomers(
      @RequestPart("file") MultipartFile file,
      HttpServletRequest request) {
    try {
      CustomerImportExportService.ImportResult result =
          importExportService.importCustomers(file.getInputStream(), file.getOriginalFilename());

      // Get current user ID for audit logging
      Long currentUserId = getCurrentUserId();
      
      // Log import summary (individual rows are logged by customerService.create())
      auditService.logCustomerImport(
          result.getSuccessCount(), 
          result.getErrorCount(), 
          currentUserId, 
          request);

      Map<String, Object> body = new HashMap<>();
      body.put("successCount", result.getSuccessCount());
      body.put("errorCount", result.getErrorCount());
      body.put("errors", result.getErrors());

      return ResponseEntity.ok(body);
    } catch (Exception e) {
      Map<String, Object> body = new HashMap<>();
      body.put("error", "Failed to import customers: " + e.getMessage());
      return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
          .body(body);
    }
  }
}


