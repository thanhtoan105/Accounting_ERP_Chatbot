package com.accounting.controller.admin;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.admin.TenantProvisionRequest;
import com.accounting.dto.admin.TenantProvisionResponse;
import com.accounting.service.admin.TenantProvisioningService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST controller for tenant (company) management operations. Only accessible by SUPER_ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantController {

  private static final Logger log = LoggerFactory.getLogger(TenantController.class);

  private final TenantProvisioningService tenantProvisioningService;

  public TenantController(TenantProvisioningService tenantProvisioningService) {
    this.tenantProvisioningService = tenantProvisioningService;
  }

  /**
   * Provision a new tenant (company) with initial admin user. Creates company, seeds COA, and
   * sends invitation to admin.
   */
  @PostMapping
  public ResponseEntity<Map<String, Object>> provisionTenant(
      @Valid @RequestBody TenantProvisionRequest request, HttpServletRequest httpRequest) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Long currentUserId = Long.parseLong(auth.getPrincipal().toString());

    log.info("Super admin {} provisioning tenant: {}", currentUserId, request.companyName());

    TenantProvisionResponse response =
        tenantProvisioningService.provisionTenant(request, currentUserId, httpRequest);

    Map<String, Object> body = new HashMap<>();
    body.put("data", response);

    return ResponseEntity.created(URI.create("/api/v1/admin/tenants/" + response.companyId()))
        .body(body);
  }
}
