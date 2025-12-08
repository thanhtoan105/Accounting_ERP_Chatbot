package com.accounting.controller.admin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.integrity.DataIntegrityJobResponse;
import com.accounting.dto.integrity.DataIntegrityRequest;
import com.accounting.security.CompanyContext;
import com.accounting.service.DataIntegrityService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/data-integrity")
public class DataIntegrityController {

  private final DataIntegrityService dataIntegrityService;

  public DataIntegrityController(DataIntegrityService dataIntegrityService) {
    this.dataIntegrityService = dataIntegrityService;
  }

  @PostMapping("/check")
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> runIntegrityScan(
      @Valid @RequestBody DataIntegrityRequest request,
      HttpServletRequest httpRequest) {
    requireCompanyContext();
    DataIntegrityJobResponse response = dataIntegrityService.triggerScan(request, httpRequest);
    return ResponseEntity.ok(Map.of("data", response));
  }

  @GetMapping("/results/{jobId}")
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> getResults(@PathVariable("jobId") UUID jobId) {
    requireCompanyContext();
    DataIntegrityJobResponse response = dataIntegrityService.getResults(jobId);
    Map<String, Object> body = new HashMap<>();
    body.put("data", response);
    return ResponseEntity.ok(body);
  }

  private void requireCompanyContext() {
    if (CompanyContext.getCompanyId() == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Missing company context (X-Company-Id header required)");
    }
  }
}
