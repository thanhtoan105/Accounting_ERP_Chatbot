package com.accounting.controller;

import com.accounting.dto.SupplierAPSummaryDTO;
import com.accounting.dto.SupplierCreateRequest;
import com.accounting.dto.SupplierDTO;
import com.accounting.dto.SupplierUpdateRequest;
import com.accounting.service.AuditService;
import com.accounting.service.SupplierImportExportService;
import com.accounting.service.SupplierService;
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
 * REST controller for Supplier operations.
 * All authenticated users can view suppliers; edit requires admin/accountant roles.
 */
@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

  private final SupplierService supplierService;
  private final SupplierImportExportService importExportService;
  private final AuditService auditService;

  public SupplierController(
      SupplierService supplierService, 
      SupplierImportExportService importExportService,
      AuditService auditService) {
    this.supplierService = supplierService;
    this.importExportService = importExportService;
    this.auditService = auditService;
  }

  /**
   * Get suppliers with pagination, sorting, and filters.
   * Requires authenticated user (any role).
   *
   * @param page page number (0-based, default: 0)
   * @param size page size (default: 20)
   * @param sort sort field and direction (e.g., "name,asc" or "code,desc", default: "name,asc")
   * @param status filter by active status (optional: true = active, false = inactive)
   * @param search search term for code or name (optional, supports unaccented Vietnamese matching)
   * @return paginated list of suppliers
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getSuppliers(
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
    Page<SupplierDTO> suppliers = supplierService.findAll(pageable, status, search);

    Map<String, Object> body = new HashMap<>();
    body.put("data", suppliers.getContent());
    body.put("total", suppliers.getTotalElements());
    body.put("page", suppliers.getNumber());
    body.put("size", suppliers.getSize());
    body.put("totalPages", suppliers.getTotalPages());
    return ResponseEntity.ok(body);
  }

  /**
   * Get single supplier by ID.
   * Requires authenticated user (any role).
   *
   * @param id supplier ID
   * @return supplier details
   */
  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getSupplierById(@PathVariable Long id) {
    return supplierService
        .getSupplierById(id)
        .map(
            supplier -> {
              Map<String, Object> body = new HashMap<>();
              body.put("data", supplier);
              return ResponseEntity.ok(body);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Get supplier AP summary (open bills, total owed, average payment days).
   * Requires authenticated user (any role).
   *
   * @param id supplier ID
   * @return AP summary
   */
  @GetMapping("/{id}/ap-summary")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getSupplierAPSummary(@PathVariable Long id) {
    SupplierAPSummaryDTO summary = supplierService.getSupplierAPSummary(id);
    Map<String, Object> body = new HashMap<>();
    body.put("data", summary);
    return ResponseEntity.ok(body);
  }

  /**
   * Create a new supplier with auto-generated code.
   * Requires admin or accountant role.
   *
   * @param request create request with supplier details
   * @return created supplier
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> createSupplier(
      @Valid @RequestBody SupplierCreateRequest request) {
    try {
      SupplierDTO supplier = supplierService.create(request);
      Map<String, Object> body = new HashMap<>();
      body.put("data", supplier);
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
   * Update an existing supplier.
   * Requires admin or accountant role.
   *
   * @param id supplier ID
   * @param request update request with supplier details
   * @return updated supplier
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> updateSupplier(
      @PathVariable Long id, @Valid @RequestBody SupplierUpdateRequest request) {
    try {
      SupplierDTO supplier = supplierService.update(id, request);
      Map<String, Object> body = new HashMap<>();
      body.put("data", supplier);
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
   * Delete a supplier (hard delete).
   * Blocks deletion if supplier has linked bills/payments.
   * Requires admin or accountant role.
   *
   * @param id supplier ID
   * @return no content or conflict if has references
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> deleteSupplier(@PathVariable Long id) {
    try {
      supplierService.delete(id);
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
   * Activate a supplier (set active=true).
   * Requires admin or accountant role.
   *
   * @param id supplier ID
   * @return no content
   */
  @PatchMapping("/{id}/activate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Void> activateSupplier(@PathVariable Long id) {
    supplierService.activate(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Deactivate a supplier (set active=false).
   * Requires admin or accountant role.
   *
   * @param id supplier ID
   * @return no content
   */
  @PatchMapping("/{id}/deactivate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Void> deactivateSupplier(@PathVariable Long id) {
    supplierService.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Export suppliers to Excel or CSV format.
   * Requires authenticated user (any role).
   *
   * @param format export format ("xlsx", "xls", or "csv", default: "xlsx")
   * @param status filter by active status (optional)
   * @param search search term (optional)
   * @return file download
   */
  @GetMapping("/export")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Resource> exportSuppliers(
      @RequestParam(defaultValue = "xlsx") String format,
      @RequestParam(required = false) Boolean status,
      @RequestParam(required = false) String search,
      HttpServletRequest request) {
    try {
      // Get all suppliers matching filters (no pagination for export)
      Page<SupplierDTO> suppliers = supplierService.findAll(
          org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE), status, search);

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      importExportService.exportSuppliers(suppliers.getContent(), format, outputStream);

      String contentType = "xlsx".equalsIgnoreCase(format) || "xls".equalsIgnoreCase(format)
          ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          : "text/csv";
      String extension = "csv".equalsIgnoreCase(format) ? "csv" : "xlsx";
      String filename = "suppliers_export_" + System.currentTimeMillis() + "." + extension;

      // Get current user ID for audit logging
      Long currentUserId = getCurrentUserId();
      
      // Log export action
      auditService.logSupplierExport(
          suppliers.getContent().size(), 
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
          "Failed to export suppliers: " + e.getMessage());
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
   * Import suppliers from Excel or CSV file.
   * Requires admin or accountant role.
   *
   * @param file uploaded file
   * @return import result with success/error counts and error details
   */
  @PostMapping("/import")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> importSuppliers(
      @RequestPart("file") MultipartFile file,
      HttpServletRequest request) {
    try {
      SupplierImportExportService.ImportResult result =
          importExportService.importSuppliers(file.getInputStream(), file.getOriginalFilename());

      // Get current user ID for audit logging
      Long currentUserId = getCurrentUserId();
      
      // Log import summary (individual rows are logged by supplierService.create())
      auditService.logSupplierImport(
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
      body.put("error", "Failed to import suppliers: " + e.getMessage());
      return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
          .body(body);
    }
  }
}

