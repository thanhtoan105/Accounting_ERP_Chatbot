package com.accounting.service.admin;

import com.accounting.dto.admin.TenantProvisionRequest;
import com.accounting.dto.admin.TenantProvisionResponse;

import jakarta.servlet.http.HttpServletRequest;

public interface TenantProvisioningService {
    TenantProvisionResponse provisionTenant(
            TenantProvisionRequest request, Long createdByUserId, HttpServletRequest httpRequest);
}
