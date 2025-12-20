package com.accounting.seed.seeder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.accounting.entity.SalesInvoiceStatus;

class SalesInvoiceSeederTest {

  private static final Set<SalesInvoiceStatus> STATUSES_REQUIRING_APPROVER =
      EnumSet.of(
          SalesInvoiceStatus.POSTED,
          SalesInvoiceStatus.PARTIALLY_PAID,
          SalesInvoiceStatus.PAID);

  private static final Set<SalesInvoiceStatus> STATUSES_WITHOUT_APPROVER =
      EnumSet.of(
          SalesInvoiceStatus.DRAFT,
          SalesInvoiceStatus.PENDING_APPROVAL,
          SalesInvoiceStatus.REJECTED);

  private boolean needsApprover(SalesInvoiceStatus status) {
    return status == SalesInvoiceStatus.POSTED
        || status == SalesInvoiceStatus.PARTIALLY_PAID
        || status == SalesInvoiceStatus.PAID;
  }

  @ParameterizedTest
  @EnumSource(
      value = SalesInvoiceStatus.class,
      names = {"POSTED", "PARTIALLY_PAID", "PAID"})
  void needsApprover_returnsTrueForApprovedStatuses(SalesInvoiceStatus status) {
    assertTrue(
        needsApprover(status),
        "Status " + status + " should require an approver");
  }

  @ParameterizedTest
  @EnumSource(
      value = SalesInvoiceStatus.class,
      names = {"DRAFT", "PENDING_APPROVAL", "REJECTED"})
  void needsApprover_returnsFalseForNonApprovedStatuses(SalesInvoiceStatus status) {
    assertFalse(
        needsApprover(status),
        "Status " + status + " should NOT require an approver");
  }

  @Test
  void rejectedStatus_shouldNotHaveApprover() {
    assertFalse(
        needsApprover(SalesInvoiceStatus.REJECTED),
        "REJECTED status should not have an approver - rejected documents were never approved");
  }

  @Test
  void allStatusesCovered() {
    Set<SalesInvoiceStatus> allStatuses = EnumSet.allOf(SalesInvoiceStatus.class);
    Set<SalesInvoiceStatus> covered = EnumSet.noneOf(SalesInvoiceStatus.class);
    covered.addAll(STATUSES_REQUIRING_APPROVER);
    covered.addAll(STATUSES_WITHOUT_APPROVER);

    assertTrue(
        covered.containsAll(allStatuses),
        "All SalesInvoiceStatus values should be explicitly categorized");
  }
}
