package com.accounting.repository;

import com.accounting.entity.VoucherLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherLineRepository
    extends JpaRepository<VoucherLine, UUID> {

  /**
   * Find all lines for a voucher.
   *
   * @param voucherId voucher ID
   * @return list of voucher lines ordered by line number
   */
  List<VoucherLine> findByVoucherIdOrderByLineNumberAsc(UUID voucherId);

  /**
   * Find all lines for a voucher by company ID (for company scoping).
   *
   * @param companyId company ID
   * @param voucherId voucher ID
   * @return list of voucher lines ordered by line number
   */
  List<VoucherLine> findByCompanyIdAndVoucherIdOrderByLineNumberAsc(
      Long companyId, UUID voucherId);

  /**
   * Delete all lines for a voucher.
   *
   * @param voucherId voucher ID
   */
  void deleteByVoucherId(UUID voucherId);

  /**
   * Count lines for a voucher.
   *
   * @param voucherId voucher ID
   * @return count of lines
   */
  long countByVoucherId(UUID voucherId);

  /**
   * Find lines by account ID (for account-level queries).
   *
   * @param companyId company ID
   * @param accountId account ID
   * @return list of voucher lines
   */
  List<VoucherLine> findByCompanyIdAndAccountId(Long companyId, Long accountId);
}

