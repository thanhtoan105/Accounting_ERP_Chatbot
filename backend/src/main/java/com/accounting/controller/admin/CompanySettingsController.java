package com.accounting.controller.admin;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.accounting.dto.UpdateBasicCompanySettingsRequest;
import com.accounting.entity.Company;
import com.accounting.service.CompanyService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/company")
public class CompanySettingsController {

  private final CompanyService companyService;

  public CompanySettingsController(CompanyService companyService) {
    this.companyService = companyService;
  }

  @GetMapping("/settings")
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> getSettings() {
    Company company = companyService.getCurrentCompanySettings();
    Map<String, Object> body = new HashMap<>();
    body.put("data", company);
    return ResponseEntity.ok(body);
  }

  @PutMapping(value = "/settings", consumes = { MediaType.APPLICATION_JSON_VALUE })
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> updateSettingsJson(
      @Valid @RequestBody UpdateBasicCompanySettingsRequest request) {
    Company updated = companyService.updateCurrentCompanySettings(request, null);
    Map<String, Object> body = new HashMap<>();
    body.put("data", updated);
    return ResponseEntity.ok(body);
  }

  @PutMapping(value = "/settings", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> updateSettingsMultipart(
      @RequestPart("payload") @Valid UpdateBasicCompanySettingsRequest request,
      @RequestPart(value = "logo", required = false) MultipartFile logo) {
    Company updated = companyService.updateCurrentCompanySettings(request, logo);
    Map<String, Object> body = new HashMap<>();
    body.put("data", updated);
    return ResponseEntity.ok(body);
  }
}
