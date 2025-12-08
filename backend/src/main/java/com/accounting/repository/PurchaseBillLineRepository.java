package com.accounting.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.PurchaseBillLine;

public interface PurchaseBillLineRepository
    extends JpaRepository<PurchaseBillLine, UUID> {

  /**
   * Find all lines for a purchase bill.
   *
   * @param purchaseBillId purchase bill ID
   * @return list of purchase bill lines ordered by line number
   */
  List<PurchaseBillLine> findByPurchaseBillIdOrderByLineNumberAsc(UUID purchaseBillId);

  /**
   * Find all lines for a purchase bill by company ID (for company scoping).
   *
   * @param companyId company ID
   * @param purchaseBillId purchase bill ID
   * @return list of purchase bill lines ordered by line number
   */
  List<PurchaseBillLine> findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(
      Long companyId, UUID purchaseBillId);

  /**
   * Delete all lines for a purchase bill.
   *
   * @param purchaseBillId purchase bill ID
   */
  void deleteByPurchaseBillId(UUID purchaseBillId);

  /**
   * Count lines for a purchase bill.
   *
   * @param purchaseBillId purchase bill ID
   * @return count of lines
   */
  long countByPurchaseBillId(UUID purchaseBillId);

  /**
   * Find lines by account ID (for account-level queries).
   *
   * @param companyId company ID
   * @param accountId account ID
   * @return list of purchase bill lines
   */
  List<PurchaseBillLine> findByCompanyIdAndAccountId(Long companyId, Long accountId);

  /**
   * Find lines for a set of purchase bills (company-scoped).
   *
   * @param companyId company ID
   * @param purchaseBillIds list of purchase bill IDs
   * @return list of purchase bill lines ordered by bill and line number
   */
  List<PurchaseBillLine> findByCompanyIdAndPurchaseBillIdInOrderByPurchaseBillIdAscLineNumberAsc(
      Long companyId, List<java.util.UUID> purchaseBillIds);

  /**
   * Find specific line scoped by company.
   *
   * @param companyId company ID
   * @param id line ID
   * @return optional purchase bill line
   */
  java.util.Optional<PurchaseBillLine> findByCompanyIdAndId(Long companyId, UUID id);
}
