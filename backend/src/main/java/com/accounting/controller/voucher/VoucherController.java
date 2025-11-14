package com.accounting.controller.voucher;

import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherCountDTO;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherListDTO;
import com.accounting.dto.VoucherValidationResult;
import com.accounting.service.VoucherService;
import com.accounting.service.VoucherValidationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.OptimisticLockException;

/**
 * REST controller for Voucher operations.
 * All authenticated users with Accountant+ role can view vouchers; delete requires Accountant+ role.
 */
@RestController
@RequestMapping("/api/v1/vouchers")
public class VoucherController {

  private final VoucherService voucherService;
  private final VoucherValidationService voucherValidationService;

  public VoucherController(
      VoucherService voucherService, VoucherValidationService voucherValidationService) {
    this.voucherService = voucherService;
    this.voucherValidationService = voucherValidationService;
  }

  /**
   * Get paginated, filtered, sorted voucher list.
   * Supports query params: page, size, status, dateFrom, dateTo, search, sort.
   * Requires authenticated user with Accountant+ role.
   *
   * @param page page number (0-based, default: 0)
   * @param size page size (default: 20, max: 50)
   * @param status filter by status (optional: draft, posted, unposted)
   * @param dateFrom filter by date from (optional, format: YYYY-MM-DD)
   * @param dateTo filter by date to (optional, format: YYYY-MM-DD)
   * @param search search term for voucher number or description (optional, supports Vietnamese unaccented matching)
   * @param sort sort parameters (optional, format: field,direction e.g., date,desc or status,asc)
   * @return paginated voucher list
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getVouchers(
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate dateFrom,
      @RequestParam(required = false) LocalDate dateTo,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Long accountId,
      @RequestParam(required = false) String[] sort) {

    // Validate page size (max 50)
    if (size > 50) {
      size = 50;
    }

    // Build sort object
    Sort sortObj = Sort.unsorted();
    if (sort != null && sort.length > 0) {
      List<Sort.Order> orders = new ArrayList<>();
      for (String sortParam : sort) {
        String[] parts = sortParam.split(",");
        if (parts.length == 2) {
          String field = parts[0].trim();
          Sort.Direction direction =
              "desc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.DESC : Sort.Direction.ASC;
          orders.add(new Sort.Order(direction, field));
        }
      }
      if (!orders.isEmpty()) {
        sortObj = Sort.by(orders);
      }
    }

    // Build pageable
    Pageable pageable = PageRequest.of(page, size, sortObj);

    // Call service
    Page<VoucherListDTO> vouchers =
        voucherService.findAll(pageable, status, dateFrom, dateTo, search, accountId);

    // Build response matching spec format: { data: { content: VoucherDTO[], totalElements: number, totalPages: number }, meta: {...} }
    Map<String, Object> data = new HashMap<>();
    data.put("content", vouchers.getContent());
    data.put("totalElements", vouchers.getTotalElements());
    data.put("totalPages", vouchers.getTotalPages());
    
    Map<String, Object> meta = new HashMap<>();
    meta.put("page", vouchers.getNumber());
    meta.put("size", vouchers.getSize());
    meta.put("totalElements", vouchers.getTotalElements());
    meta.put("totalPages", vouchers.getTotalPages());
    
    Map<String, Object> body = new HashMap<>();
    body.put("data", data);
    body.put("meta", meta);

    return ResponseEntity.ok(body);
  }

  /**
   * Get voucher by ID (with lines).
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @return voucher details with lines array
   */
  @GetMapping("/{voucherId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getVoucherById(@PathVariable UUID voucherId) {
    return voucherService
        .getVoucherById(voucherId)
        .map(
            voucher -> {
              Map<String, Object> body = new HashMap<>();
              body.put("data", voucher);
              return ResponseEntity.ok(body);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Create a new voucher with line items.
   * Requires authenticated user with Accountant+ role.
   *
   * @param request voucher create request with line items
   * @return created voucher DTO
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> createVoucher(
      @Valid @RequestBody VoucherCreateRequest request) {
    VoucherDTO voucher = voucherService.create(request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", voucher);
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * Update an existing draft voucher.
   * Only draft vouchers can be updated.
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @param request voucher update request with line items
   * @return updated voucher DTO
   */
  @PutMapping("/{voucherId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> updateVoucher(
      @PathVariable UUID voucherId, @Valid @RequestBody VoucherCreateRequest request) {
    try {
    VoucherDTO voucher = voucherService.update(voucherId, request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", voucher);
    return ResponseEntity.ok(body);
    } catch (OptimisticLockException e) {
      // Handle optimistic locking conflict - voucher was modified by another user
      Map<String, Object> errorBody = new HashMap<>();
      errorBody.put("error", "Voucher was modified by another user. Please refresh and try again.");
      errorBody.put("code", "OPTIMISTIC_LOCK_EXCEPTION");
      return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody);
    }
  }

  /**
   * Validate a voucher (without saving).
   * Returns detailed validation errors for client-side display.
   * For new vouchers, use a placeholder UUID (e.g., all zeros).
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID (use placeholder UUID for new vouchers)
   * @param request voucher request to validate
   * @return validation result with errors (if any)
   */
  @PostMapping("/{voucherId}/validate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> validateVoucher(
      @PathVariable UUID voucherId,
      @Valid @RequestBody VoucherCreateRequest request) {
    // Note: voucherId is in path per story requirements, but validation doesn't require it
    // For new vouchers, clients should use a placeholder UUID
    VoucherValidationResult result = voucherValidationService.validate(request);
    Map<String, Object> body = new HashMap<>();
    body.put("valid", result.isValid());
    body.put("errors", result.getErrors());
    return ResponseEntity.ok(body);
  }

  /**
   * Get voucher counts by status (for badges).
   * Requires authenticated user with Accountant+ role.
   *
   * @return voucher counts
   */
  @GetMapping("/counts")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getVoucherCounts() {
    VoucherCountDTO counts = voucherService.getCounts();

    Map<String, Object> body = new HashMap<>();
    body.put("data", counts);

    return ResponseEntity.ok(body);
  }

  /**
   * Get voucher counts by status (for badges) - alias endpoint matching spec.
   * Requires authenticated user with Accountant+ role.
   *
   * @return voucher counts
   */
  @GetMapping("/count")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getVoucherCount() {
    return getVoucherCounts();
  }

  /**
   * Delete voucher with validation.
   * Only draft vouchers that are not referenced can be deleted.
   * Requires deletion reason.
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @param reason deletion reason (required)
   * @return success response
   */
  @DeleteMapping("/{voucherId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> deleteVoucher(
      @PathVariable UUID voucherId,
      @RequestParam(required = false) String reason,
      HttpServletRequest request) {

    // Validate reason is provided
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Deletion reason is required");
    }

    // Delete voucher (pass request for audit logging)
    voucherService.delete(voucherId, reason, request);

    Map<String, Object> body = new HashMap<>();
    body.put("message", "Voucher deleted successfully");
    body.put("voucherId", voucherId);

    return ResponseEntity.ok(body);
  }
}
