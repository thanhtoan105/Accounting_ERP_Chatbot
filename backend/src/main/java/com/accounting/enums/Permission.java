package com.accounting.enums;

/**
 * System permissions for granular access control.
 * Permissions are combined with roles to define access rules.
 * This provides foundation for future permission-based access control.
 */
public class Permission {
  // User management permissions
  public static final String VIEW_USERS = "VIEW_USERS";
  public static final String EDIT_USERS = "EDIT_USERS";
  public static final String INVITE_USERS = "INVITE_USERS";
  public static final String CHANGE_ROLES = "CHANGE_ROLES";

  // Report permissions
  public static final String VIEW_REPORTS = "VIEW_REPORTS";
  public static final String EXPORT_REPORTS = "EXPORT_REPORTS";

  // Voucher permissions
  public static final String VIEW_VOUCHERS = "VIEW_VOUCHERS";
  public static final String EDIT_VOUCHERS = "EDIT_VOUCHERS";
  public static final String APPROVE_VOUCHERS = "APPROVE_VOUCHERS";
  public static final String DELETE_VOUCHERS = "DELETE_VOUCHERS";

  // Period management permissions
  public static final String VIEW_PERIODS = "VIEW_PERIODS";
  public static final String CLOSE_PERIODS = "CLOSE_PERIODS";
  public static final String REOPEN_PERIODS = "REOPEN_PERIODS";

  // Company settings permissions
  public static final String VIEW_COMPANY_SETTINGS = "VIEW_COMPANY_SETTINGS";
  public static final String EDIT_COMPANY_SETTINGS = "EDIT_COMPANY_SETTINGS";

  // Audit log permissions
  public static final String VIEW_AUDIT_LOGS = "VIEW_AUDIT_LOGS";

  private Permission() {
    // Utility class - prevent instantiation
  }
}
