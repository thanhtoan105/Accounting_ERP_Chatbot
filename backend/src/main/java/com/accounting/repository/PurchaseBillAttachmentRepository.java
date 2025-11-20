package com.accounting.repository;

import com.accounting.entity.PurchaseBillAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseBillAttachmentRepository
    extends JpaRepository<PurchaseBillAttachment, UUID> {

  /**
   * Find all attachments for a purchase bill (company-scoped).
   *
   * @param purchaseBillId purchase bill ID
   * @param companyId company ID
   * @return list of attachments
   */
  List<PurchaseBillAttachment> findByPurchaseBillIdAndCompanyId(UUID purchaseBillId, Long companyId);

  /**
   * Find attachment by ID and company ID.
   *
   * @param attachmentId attachment ID
   * @param companyId company ID
   * @return optional attachment
   */
  Optional<PurchaseBillAttachment> findByIdAndCompanyId(UUID attachmentId, Long companyId);

  /**
   * Find attachment by purchase bill ID, attachment ID, and company ID.
   *
   * @param purchaseBillId purchase bill ID
   * @param attachmentId attachment ID
   * @param companyId company ID
   * @return optional attachment
   */
  Optional<PurchaseBillAttachment> findByPurchaseBillIdAndIdAndCompanyId(
      UUID purchaseBillId, UUID attachmentId, Long companyId);

  /**
   * Count attachments for a purchase bill (company-scoped).
   *
   * @param purchaseBillId purchase bill ID
   * @param companyId company ID
   * @return count of attachments
   */
  long countByPurchaseBillIdAndCompanyId(UUID purchaseBillId, Long companyId);
}

