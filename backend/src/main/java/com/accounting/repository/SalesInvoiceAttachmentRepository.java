package com.accounting.repository;

import com.accounting.entity.SalesInvoiceAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesInvoiceAttachmentRepository
    extends JpaRepository<SalesInvoiceAttachment, UUID> {

  /**
   * Find all attachments for a sales invoice (company-scoped).
   *
   * @param salesInvoiceId sales invoice ID
   * @param companyId company ID
   * @return list of attachments
   */
  List<SalesInvoiceAttachment> findBySalesInvoiceIdAndCompanyId(UUID salesInvoiceId, Long companyId);

  /**
   * Find attachment by ID and company ID.
   *
   * @param attachmentId attachment ID
   * @param companyId company ID
   * @return optional attachment
   */
  Optional<SalesInvoiceAttachment> findByIdAndCompanyId(UUID attachmentId, Long companyId);

  /**
   * Find attachment by sales invoice ID, attachment ID, and company ID.
   *
   * @param salesInvoiceId sales invoice ID
   * @param attachmentId attachment ID
   * @param companyId company ID
   * @return optional attachment
   */
  Optional<SalesInvoiceAttachment> findBySalesInvoiceIdAndIdAndCompanyId(
      UUID salesInvoiceId, UUID attachmentId, Long companyId);

  /**
   * Count attachments for a sales invoice (company-scoped).
   *
   * @param salesInvoiceId sales invoice ID
   * @param companyId company ID
   * @return count of attachments
   */
  long countBySalesInvoiceIdAndCompanyId(UUID salesInvoiceId, Long companyId);
}
