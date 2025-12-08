package com.accounting.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.security.CompanyContext;

@RestController
@RequestMapping("/api/v1/_context")
public class ContextController {

  @GetMapping("/company")
  public ResponseEntity<Map<String, Object>> getCompanyContext() {
    Long companyId = CompanyContext.getCompanyId();
    Map<String, Object> body = new HashMap<>();
    body.put("companyId", companyId); // allows null
    return ResponseEntity.ok(body);
  }

  @PostMapping("/company")
  public ResponseEntity<Map<String, Object>> setCompanyContext(@RequestBody(required = false) Map<String, Object> input) {
    // This endpoint relies on CompanyContextFilter to read X-Company-Id header.
    // If body contains companyId, prefer header for authoritative source.
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null && input != null) {
      Object v = input.get("companyId");
      if (v instanceof Number) {
        companyId = ((Number) v).longValue();
      }
    }
    Map<String, Object> body = new HashMap<>();
    body.put("companyId", companyId);
    return ResponseEntity.ok(body);
  }
}
