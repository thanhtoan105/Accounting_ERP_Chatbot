package com.accounting.service.impl;

import com.accounting.enums.Role;
import com.accounting.service.RoleService;
import org.springframework.stereotype.Service;

/**
 * Implementation of RoleService for role validation and management utilities.
 */
@Service
public class RoleServiceImpl implements RoleService {

  @Override
  public boolean isValidRole(String role) {
    return Role.isValid(role);
  }

  @Override
  public Role toRoleEnum(String role) {
    return Role.fromString(role);
  }

  @Override
  public Role getDefaultRole() {
    return Role.ACCOUNTANT;
  }

  @Override
  public String toAuthority(Role role) {
    if (role == null) {
      return null;
    }
    return role.toAuthority();
  }
}

