package com.accounting.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accounting.exception.CompanyScopeViolationException;
import com.accounting.repository.CompanyScopedEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CompanyScopeEnforcerTest {

  static class DummyEntity implements CompanyScopedEntity {
    private final Long companyId;

    DummyEntity(Long companyId) {
      this.companyId = companyId;
    }

    @Override
    public Long getCompanyId() {
      return companyId;
    }
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void throwsWhenMissingContext() {
    DummyEntity e = new DummyEntity(1L);
    assertThrows(CompanyScopeViolationException.class, () -> CompanyScopeEnforcer.enforceForWrite(e));
  }

  @Test
  void throwsWhenMismatch() {
    CompanyContext.setCompanyId(2L);
    DummyEntity e = new DummyEntity(1L);
    assertThrows(CompanyScopeViolationException.class, () -> CompanyScopeEnforcer.enforceForWrite(e));
  }

  @Test
  void okWhenMatch() {
    CompanyContext.setCompanyId(5L);
    DummyEntity e = new DummyEntity(5L);
    assertDoesNotThrow(() -> CompanyScopeEnforcer.enforceForWrite(e));
  }
}


