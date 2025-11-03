package com.accounting.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoleTest {

  @Test
  void fromString_shouldReturnRoleForValidLowercaseValue() {
    assertEquals(Role.ADMIN, Role.fromString("admin"));
    assertEquals(Role.ACCOUNTANT, Role.fromString("accountant"));
    assertEquals(Role.CHIEF_ACCOUNTANT, Role.fromString("chief_accountant"));
    assertEquals(Role.CFO, Role.fromString("cfo"));
  }

  @Test
  void fromString_shouldReturnNullForInvalidValue() {
    assertNull(Role.fromString("invalid"));
    assertNull(Role.fromString("ADMIN"));
    assertNull(Role.fromString(""));
    assertNull(Role.fromString(null));
  }

  @Test
  void isValid_shouldReturnTrueForValidRole() {
    assertTrue(Role.isValid("admin"));
    assertTrue(Role.isValid("accountant"));
    assertTrue(Role.isValid("chief_accountant"));
    assertTrue(Role.isValid("cfo"));
  }

  @Test
  void isValid_shouldReturnFalseForInvalidRole() {
    assertFalse(Role.isValid("invalid"));
    assertFalse(Role.isValid("ADMIN"));
    assertFalse(Role.isValid(null));
    assertFalse(Role.isValid(""));
  }

  @Test
  void getValue_shouldReturnLowercaseValue() {
    assertEquals("admin", Role.ADMIN.getValue());
    assertEquals("accountant", Role.ACCOUNTANT.getValue());
    assertEquals("chief_accountant", Role.CHIEF_ACCOUNTANT.getValue());
    assertEquals("cfo", Role.CFO.getValue());
  }

  @Test
  void toAuthority_shouldReturnSpringSecurityAuthorityFormat() {
    assertEquals("ROLE_ADMIN", Role.ADMIN.toAuthority());
    assertEquals("ROLE_ACCOUNTANT", Role.ACCOUNTANT.toAuthority());
    assertEquals("ROLE_CHIEF_ACCOUNTANT", Role.CHIEF_ACCOUNTANT.toAuthority());
    assertEquals("ROLE_CFO", Role.CFO.toAuthority());
  }
}

