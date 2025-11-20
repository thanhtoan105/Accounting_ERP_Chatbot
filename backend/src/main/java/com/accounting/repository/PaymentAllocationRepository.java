package com.accounting.repository;

import com.accounting.entity.PaymentAllocation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAllocationRepository
    extends JpaRepository<PaymentAllocation, UUID> {

  /**
   * Find all allocations for a payment.
   *
   * @param paymentId payment ID
   * @return list of allocations ordered by allocation_order
   */
  @Query(
      "SELECT pa FROM PaymentAllocation pa WHERE pa.paymentId = :paymentId "
          + "ORDER BY pa.allocationOrder ASC")
  List<PaymentAllocation> findByPaymentIdOrderByAllocationOrder(@Param("paymentId") UUID paymentId);

  /**
   * Find all allocations for a purchase bill.
   *
   * @param purchaseBillId purchase bill ID
   * @return list of allocations
   */
  List<PaymentAllocation> findByPurchaseBillId(UUID purchaseBillId);

  /**
   * Find all allocations for a payment and company.
   *
   * @param companyId company ID
   * @param paymentId payment ID
   * @return list of allocations ordered by allocation_order
   */
  @Query(
      "SELECT pa FROM PaymentAllocation pa WHERE pa.companyId = :companyId "
          + "AND pa.paymentId = :paymentId ORDER BY pa.allocationOrder ASC")
  List<PaymentAllocation> findByCompanyIdAndPaymentIdOrderByAllocationOrder(
      @Param("companyId") Long companyId, @Param("paymentId") UUID paymentId);

  /**
   * Calculate total allocated amount for a purchase bill.
   *
   * @param purchaseBillId purchase bill ID
   * @return total allocated amount
   */
  @Query(
      "SELECT COALESCE(SUM(pa.allocatedAmount), 0) FROM PaymentAllocation pa "
          + "WHERE pa.purchaseBillId = :purchaseBillId")
  java.math.BigDecimal calculateTotalAllocatedAmount(@Param("purchaseBillId") UUID purchaseBillId);

  /**
   * Delete all allocations for a payment.
   *
   * @param paymentId payment ID
   */
  void deleteByPaymentId(UUID paymentId);
}

