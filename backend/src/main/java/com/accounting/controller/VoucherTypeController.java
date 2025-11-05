package com.accounting.controller;

import com.accounting.dto.VoucherTypeDTO;
import com.accounting.dto.VoucherTypeCreateRequest;
import com.accounting.dto.VoucherTypeUpdateRequest;
import com.accounting.service.VoucherTypeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * REST controller for Voucher Type operations.
 * Requires admin or chief_accountant role.
 */
@RestController
@RequestMapping("/api/v1/voucher-types")
@PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
public class VoucherTypeController {

  private final VoucherTypeService voucherTypeService;

  public VoucherTypeController(VoucherTypeService voucherTypeService) {
    this.voucherTypeService = voucherTypeService;
  }

  /**
   * Get all voucher types with optional search and status filters.
   *
   * @param search search term for type code or name (optional)
   * @param status filter by status (ACTIVE/INACTIVE, optional)
   * @return list of voucher types
   */
  @GetMapping
  public ResponseEntity<Map<String, Object>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status) {
    List<VoucherTypeDTO> voucherTypes = voucherTypeService.findAll(search, status);
    Map<String, Object> body = new HashMap<>();
    body.put("data", voucherTypes);
    body.put("total", voucherTypes.size());
    return ResponseEntity.ok(body);
  }

  /**
   * Get voucher type by ID.
   *
   * @param id voucher type ID
   * @return voucher type details
   */
  @GetMapping("/{id}")
  public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
    return voucherTypeService.findById(id)
        .map(voucherType -> {
          Map<String, Object> body = new HashMap<>();
          body.put("data", voucherType);
          return ResponseEntity.ok(body);
        })
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Create a new voucher type.
   *
   * @param request create request
   * @return created voucher type
   */
  @PostMapping
  public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody VoucherTypeCreateRequest request) {
    VoucherTypeDTO voucherType = voucherTypeService.create(request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", voucherType);
    final URI location = URI.create("/api/v1/voucher-types/" + voucherType.getId());
    return ResponseEntity.created(Objects.requireNonNull(location)).body(body);
  }

  /**
   * Update an existing voucher type.
   *
   * @param id      voucher type ID
   * @param request update request
   * @return updated voucher type
   */
  @PutMapping("/{id}")
  public ResponseEntity<Map<String, Object>> update(
      @PathVariable Long id,
      @Valid @RequestBody VoucherTypeUpdateRequest request) {
    VoucherTypeDTO voucherType = voucherTypeService.update(id, request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", voucherType);
    return ResponseEntity.ok(body);
  }

  /**
   * Delete a voucher type.
   *
   * @param id voucher type ID
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    voucherTypeService.delete(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Deactivate a voucher type.
   *
   * @param id voucher type ID
   * @return updated voucher type
   */
  @PatchMapping("/{id}/deactivate")
  public ResponseEntity<Map<String, Object>> deactivate(@PathVariable Long id) {
    VoucherTypeDTO voucherType = voucherTypeService.deactivate(id);
    Map<String, Object> body = new HashMap<>();
    body.put("data", voucherType);
    return ResponseEntity.ok(body);
  }

  /**
   * Activate a voucher type.
   *
   * @param id voucher type ID
   * @return updated voucher type
   */
  @PatchMapping("/{id}/activate")
  public ResponseEntity<Map<String, Object>> activate(@PathVariable Long id) {
    VoucherTypeDTO voucherType = voucherTypeService.activate(id);
    Map<String, Object> body = new HashMap<>();
    body.put("data", voucherType);
    return ResponseEntity.ok(body);
  }
}
