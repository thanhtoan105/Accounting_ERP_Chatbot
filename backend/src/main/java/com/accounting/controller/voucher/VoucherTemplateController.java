package com.accounting.controller.voucher;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
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

import com.accounting.dto.VoucherTemplateDTO;
import com.accounting.dto.VoucherTemplateRequest;
import com.accounting.dto.VoucherTemplateSummaryDTO;
import com.accounting.service.VoucherTemplateService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/voucher-templates")
@PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO')")
public class VoucherTemplateController {

  private final VoucherTemplateService voucherTemplateService;

  public VoucherTemplateController(VoucherTemplateService voucherTemplateService) {
    this.voucherTemplateService = voucherTemplateService;
  }

  @GetMapping
  public ResponseEntity<Map<String, Object>> list(
      @RequestParam(name = "isActive", required = false) Boolean isActive) {
    List<VoucherTemplateSummaryDTO> templates = voucherTemplateService.list(isActive);
    Map<String, Object> body = new HashMap<>();
    body.put("data", templates);
    body.put("total", templates.size());
    return ResponseEntity.ok(body);
  }

  @GetMapping("/{templateId}")
  public ResponseEntity<Map<String, Object>> getById(@PathVariable UUID templateId) {
    return voucherTemplateService
        .getById(templateId)
        .map(
            dto -> {
              Map<String, Object> body = new HashMap<>();
              body.put("data", dto);
              return ResponseEntity.ok(body);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> create(
      @Valid @RequestBody VoucherTemplateRequest request) {
    VoucherTemplateDTO template = voucherTemplateService.create(request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", template);
    URI location = URI.create("/api/v1/voucher-templates/" + template.getId());
    return ResponseEntity.created(Objects.requireNonNull(location)).body(body);
  }

  @PutMapping("/{templateId}")
  public ResponseEntity<Map<String, Object>> update(
      @PathVariable UUID templateId, @Valid @RequestBody VoucherTemplateRequest request) {
    VoucherTemplateDTO template = voucherTemplateService.update(templateId, request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", template);
    return ResponseEntity.ok(body);
  }

  @DeleteMapping("/{templateId}")
  public ResponseEntity<Void> delete(@PathVariable UUID templateId) {
    voucherTemplateService.delete(templateId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{templateId}/activate")
  public ResponseEntity<Map<String, Object>> activate(@PathVariable UUID templateId) {
    VoucherTemplateDTO template = voucherTemplateService.activate(templateId);
    Map<String, Object> body = new HashMap<>();
    body.put("data", template);
    return ResponseEntity.ok(body);
  }

  @PatchMapping("/{templateId}/deactivate")
  public ResponseEntity<Map<String, Object>> deactivate(@PathVariable UUID templateId) {
    VoucherTemplateDTO template = voucherTemplateService.deactivate(templateId);
    Map<String, Object> body = new HashMap<>();
    body.put("data", template);
    return ResponseEntity.ok(body);
  }
}
