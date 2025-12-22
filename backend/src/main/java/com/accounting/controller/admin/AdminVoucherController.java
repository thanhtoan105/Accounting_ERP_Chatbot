package com.accounting.controller.admin;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.admin.EmbeddingStatusResponse;
import com.accounting.security.CompanyContext;
import com.accounting.service.EmbeddingService;

@RestController
@RequestMapping("/api/v1/admin/vouchers")
public class AdminVoucherController {

  private final EmbeddingService embeddingService;

  public AdminVoucherController(EmbeddingService embeddingService) {
    this.embeddingService = embeddingService;
  }

  @GetMapping("/embedding-status")
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> getEmbeddingStatus() {
    requireCompanyContext();
    EmbeddingStatusResponse status = embeddingService.getEmbeddingStatus(CompanyContext.getCompanyId());
    return ResponseEntity.ok(Map.of("data", status));
  }

  @PostMapping("/embed/start")
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, String>> startBatchEmbedding() {
    requireCompanyContext();
    embeddingService.triggerBatchEmbedding(CompanyContext.getCompanyId());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(Map.of("message", "Batch embedding started"));
  }

  private void requireCompanyContext() {
    if (CompanyContext.getCompanyId() == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Missing company context (X-Company-Id header required)");
    }
  }
}
