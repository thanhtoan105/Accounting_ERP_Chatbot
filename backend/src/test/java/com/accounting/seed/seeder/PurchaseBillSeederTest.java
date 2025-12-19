package com.accounting.seed.seeder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.accounting.entity.PurchaseBillStatus;

class PurchaseBillSeederTest {

  private static final Set<PurchaseBillStatus> STATUSES_REQUIRING_APPROVER =
      EnumSet.of(
          PurchaseBillStatus.POSTED,
          PurchaseBillStatus.PARTIALLY_PAID,
          PurchaseBillStatus.PAID);

  private static final Set<PurchaseBillStatus> STATUSES_WITHOUT_APPROVER =
      EnumSet.of(
          PurchaseBillStatus.DRAFT,
          PurchaseBillStatus.PENDING_APPROVAL,
          PurchaseBillStatus.REJECTED);

  private boolean needsApprover(PurchaseBillStatus status) {
    return status == PurchaseBillStatus.POSTED
        || status == PurchaseBillStatus.PARTIALLY_PAID
        || status == PurchaseBillStatus.PAID;
  }

  @ParameterizedTest
  @EnumSource(
      value = PurchaseBillStatus.class,
      names = {"POSTED", "PARTIALLY_PAID", "PAID"})
  void needsApprover_returnsTrueForApprovedStatuses(PurchaseBillStatus status) {
    assertTrue(
        needsApprover(status),
        "Status " + status + " should require an approver");
  }

  @ParameterizedTest
  @EnumSource(
      value = PurchaseBillStatus.class,
      names = {"DRAFT", "PENDING_APPROVAL", "REJECTED"})
  void needsApprover_returnsFalseForNonApprovedStatuses(PurchaseBillStatus status) {
    assertFalse(
        needsApprover(status),
        "Status " + status + " should NOT require an approver");
  }

  @Test
  void rejectedStatus_shouldNotHaveApprover() {
    assertFalse(
        needsApprover(PurchaseBillStatus.REJECTED),
        "REJECTED status should not have an approver - rejected documents were never approved");
  }

  @Test
  void allStatusesCovered() {
    Set<PurchaseBillStatus> allStatuses = EnumSet.allOf(PurchaseBillStatus.class);
    Set<PurchaseBillStatus> covered = EnumSet.noneOf(PurchaseBillStatus.class);
    covered.addAll(STATUSES_REQUIRING_APPROVER);
    covered.addAll(STATUSES_WITHOUT_APPROVER);

    assertTrue(
        covered.containsAll(allStatuses),
        "All PurchaseBillStatus values should be explicitly categorized");
  }
}
