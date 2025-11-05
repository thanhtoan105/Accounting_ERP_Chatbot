package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accounting.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleServiceImplTest {

  private RoleServiceImpl roleService;

  @BeforeEach
  void setUp() {
    roleService = new RoleServiceImpl();
  }

  @Test
  void isValidRole_shouldReturnTrueForValidRole() {
    assertTrue(roleService.isValidRole("admin"));
    assertTrue(roleService.isValidRole("accountant"));
    assertTrue(roleService.isValidRole("chief_accountant"));
    assertTrue(roleService.isValidRole("cfo"));
  }

  @Test
  void isValidRole_shouldReturnFalseForInvalidRole() {
    assertFalse(roleService.isValidRole("invalid"));
    assertFalse(roleService.isValidRole(null));
  }

  @Test
  void toRoleEnum_shouldConvertStringToRoleEnum() {
    assertEquals(Role.ADMIN, roleService.toRoleEnum("admin"));
    assertEquals(Role.ACCOUNTANT, roleService.toRoleEnum("accountant"));
    assertEquals(Role.CHIEF_ACCOUNTANT, roleService.toRoleEnum("chief_accountant"));
    assertEquals(Role.CFO, roleService.toRoleEnum("cfo"));
  }

  @Test
  void toRoleEnum_shouldReturnNullForInvalidRole() {
    assertNull(roleService.toRoleEnum("invalid"));
    assertNull(roleService.toRoleEnum(null));
  }

  @Test
  void getDefaultRole_shouldReturnAccountant() {
    assertEquals(Role.ACCOUNTANT, roleService.getDefaultRole());
  }

  @Test
  void toAuthority_shouldConvertRoleToSpringSecurityAuthority() {
    assertEquals("ROLE_ADMIN", roleService.toAuthority(Role.ADMIN));
    assertEquals("ROLE_ACCOUNTANT", roleService.toAuthority(Role.ACCOUNTANT));
    assertEquals("ROLE_CHIEF_ACCOUNTANT", roleService.toAuthority(Role.CHIEF_ACCOUNTANT));
    assertEquals("ROLE_CFO", roleService.toAuthority(Role.CFO));
  }

  @Test
  void toAuthority_shouldReturnNullForNullRole() {
    assertNull(roleService.toAuthority(null));
  }
}

