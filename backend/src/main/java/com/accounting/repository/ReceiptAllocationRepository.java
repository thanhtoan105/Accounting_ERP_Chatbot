package com.accounting.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.ReceiptAllocation;

public interface ReceiptAllocationRepository extends JpaRepository<ReceiptAllocation, UUID> {

  /**
   * Find all allocations for a receipt.
   *
   * @param receiptId receipt ID
   * @return list of allocations ordered by allocation_order
   */
  List<ReceiptAllocation> findByReceiptIdOrderByAllocationOrderAsc(UUID receiptId);

  /**
   * Find all allocations for an invoice.
   *
   * @param salesInvoiceId sales invoice ID
   * @return list of allocations
   */
  List<ReceiptAllocation> findBySalesInvoiceId(UUID salesInvoiceId);

  /**
   * Delete all allocations for a receipt.
   *
   * @param receiptId receipt ID
   */
  void deleteByReceiptId(UUID receiptId);

  /**
   * Calculate total allocated amount for a receipt.
   *
   * @param receiptId receipt ID
   * @return total allocated amount
   */
  @Query("SELECT COALESCE(SUM(ra.allocatedAmount), 0) FROM ReceiptAllocation ra WHERE ra.receiptId = :receiptId")
  BigDecimal sumAllocatedAmountByReceiptId(@Param("receiptId") UUID receiptId);

  /**
   * Calculate total allocated amount for an invoice from posted receipts.
   *
   * @param salesInvoiceId sales invoice ID
   * @return total allocated amount from posted receipts
   */
  @Query(
      "SELECT COALESCE(SUM(ra.allocatedAmount), 0) FROM ReceiptAllocation ra "
          + "JOIN ARPayment ar ON ra.receiptId = ar.id "
          + "WHERE ra.salesInvoiceId = :salesInvoiceId AND ar.status = 'POSTED'")
  BigDecimal sumAllocatedAmountBySalesInvoiceIdAndPostedReceipts(
      @Param("salesInvoiceId") UUID salesInvoiceId);

  /**
   * Find allocation by receipt and invoice (unique constraint).
   *
   * @param receiptId receipt ID
   * @param salesInvoiceId sales invoice ID
   * @return allocation if exists
   */
  ReceiptAllocation findByReceiptIdAndSalesInvoiceId(UUID receiptId, UUID salesInvoiceId);
}
