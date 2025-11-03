package com.accounting.security;

public final class CompanyContext {

  private static final ThreadLocal<Long> CURRENT_COMPANY_ID = new ThreadLocal<>();

  private CompanyContext() {}

  public static void setCompanyId(Long companyId) {
    CURRENT_COMPANY_ID.set(companyId);
  }

  public static Long getCompanyId() {
    return CURRENT_COMPANY_ID.get();
  }

  public static void clear() {
    CURRENT_COMPANY_ID.remove();
  }
}


