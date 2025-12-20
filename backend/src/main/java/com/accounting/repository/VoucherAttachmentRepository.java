package com.accounting.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.VoucherAttachment;

public interface VoucherAttachmentRepository
    extends JpaRepository<VoucherAttachment, UUID> {

  /**
   * Find all attachments for a voucher (company-scoped).
   *
   * @param voucherId voucher ID
   * @param companyId company ID
   * @return list of attachments
   */
  List<VoucherAttachment> findByVoucherIdAndCompanyId(UUID voucherId, Long companyId);

  /**
   * Find attachment by ID and company ID.
   *
   * @param attachmentId attachment ID
   * @param companyId company ID
   * @return optional attachment
   */
  Optional<VoucherAttachment> findByIdAndCompanyId(UUID attachmentId, Long companyId);

  /**
   * Find attachment by voucher ID, attachment ID, and company ID.
   *
   * @param voucherId voucher ID
   * @param attachmentId attachment ID
   * @param companyId company ID
   * @return optional attachment
   */
  Optional<VoucherAttachment> findByVoucherIdAndIdAndCompanyId(
      UUID voucherId, UUID attachmentId, Long companyId);

  /**
   * Count attachments for a voucher (company-scoped).
   *
   * @param voucherId voucher ID
   * @param companyId company ID
   * @return count of attachments
   */
  long countByVoucherIdAndCompanyId(UUID voucherId, Long companyId);
}
