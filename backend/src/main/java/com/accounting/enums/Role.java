package com.accounting.enums;

/**
 * User roles in the system. Values match database storage (lowercase).
 * Used for role-based access control (RBAC).
 */
public enum Role {
  SUPER_ADMIN("super_admin"),
  ADMIN("admin"),
  ACCOUNTANT("accountant"),
  CHIEF_ACCOUNTANT("chief_accountant"),
  CFO("cfo");

  private final String value;

  Role(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  /**
   * Convert string value to Role enum. Case-sensitive.
   *
   * @param value the role string value
   * @return Role enum or null if not found
   */
  public static Role fromString(String value) {
    if (value == null) {
      return null;
    }
    for (Role role : Role.values()) {
      if (role.value.equals(value)) {
        return role;
      }
    }
    return null;
  }

  /**
   * Check if a string value is a valid role.
   *
   * @param value the role string value
   * @return true if valid, false otherwise
   */
  public static boolean isValid(String value) {
    return fromString(value) != null;
  }

  /**
   * Convert Role enum to Spring Security authority format (ROLE_ADMIN, etc.).
   *
   * @return Spring Security authority string
   */
  public String toAuthority() {
    return "ROLE_" + name();
  }

  /**
   * Check if this role has higher or equal privilege than another role.
   * Hierarchy: ADMIN > CHIEF_ACCOUNTANT > CFO > ACCOUNTANT
   *
   * @param other the role to compare with
   * @return true if this role has higher or equal privilege
   */
  public boolean hasHigherOrEqualPrivilege(Role other) {
    if (other == null) {
      return true;
    }
    // Get hierarchy level (higher number = higher privilege)
    int thisLevel = getHierarchyLevel();
    int otherLevel = other.getHierarchyLevel();
    return thisLevel >= otherLevel;
  }

  /**
   * Check if this role can manage another role.
   * Rules:
   * - ADMIN can manage everyone except themselves
   * - CHIEF_ACCOUNTANT can only manage lower roles (ACCOUNTANT, CFO)
   * - Others cannot manage anyone
   *
   * @param targetRole the role to check if can be managed
   * @return true if this role can manage the target role
   */
  public boolean canManageRole(Role targetRole) {
    if (targetRole == null) {
      return false;
    }
    if (this == SUPER_ADMIN) {
      return true; // SUPER_ADMIN can manage everyone
    }
    if (this == ADMIN) {
      return targetRole != SUPER_ADMIN; // ADMIN can manage everyone except SUPER_ADMIN
    }
    if (this == CHIEF_ACCOUNTANT) {
      // CHIEF_ACCOUNTANT can only manage lower roles (ACCOUNTANT, CFO)
      return targetRole == ACCOUNTANT || targetRole == CFO;
    }
    return false; // ACCOUNTANT and CFO cannot manage anyone
  }

  /**
   * Get hierarchy level for comparison.
   * Higher number = higher privilege.
   *
   * @return hierarchy level
   */
  private int getHierarchyLevel() {
    return switch (this) {
      case SUPER_ADMIN -> 5;
      case ADMIN -> 4;
      case CHIEF_ACCOUNTANT -> 3;
      case CFO -> 2;
      case ACCOUNTANT -> 1;
    };
  }
}
