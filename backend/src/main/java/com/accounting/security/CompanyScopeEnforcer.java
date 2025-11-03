package com.accounting.security;

import com.accounting.exception.CompanyScopeViolationException;
import com.accounting.repository.CompanyScopedEntity;

public final class CompanyScopeEnforcer {

  private CompanyScopeEnforcer() {}

  public static void enforceForWrite(CompanyScopedEntity entity) {
    Long entityCompanyId = entity.getCompanyId();
    
    // Allow entities with null companyId (e.g., new user registration)
    // They will be assigned a company later by admin
    if (entityCompanyId == null) {
      return;
    }
    
    // If entity has companyId, require company context
    Long contextCompanyId = CompanyContext.getCompanyId();
    if (contextCompanyId == null) {
      throw new CompanyScopeViolationException("Missing company context (X-Company-Id)");
    }
    
    // Entity companyId must match active company context
    if (!contextCompanyId.equals(entityCompanyId)) {
      throw new CompanyScopeViolationException(
          "Entity companyId does not match active company context");
    }
  }
}


