package com.accounting.repository;

import com.accounting.entity.Attachment;
import com.accounting.entity.AttachmentEntityType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Unified repository for attachments across all entity types.
 * Replaces VoucherAttachmentRepository, PurchaseBillAttachmentRepository,
 * and SalesInvoiceAttachmentRepository.
 */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Find all attachments for an entity (company-scoped).
     *
     * @param entityType type of entity
     * @param entityId   entity ID
     * @param companyId  company ID
     * @return list of attachments
     */
    List<Attachment> findByEntityTypeAndEntityIdAndCompanyId(
            AttachmentEntityType entityType, UUID entityId, Long companyId);

    /**
     * Find attachment by ID and company ID.
     *
     * @param attachmentId attachment ID
     * @param companyId    company ID
     * @return optional attachment
     */
    Optional<Attachment> findByIdAndCompanyId(UUID attachmentId, Long companyId);

    /**
     * Find attachment by entity type, entity ID, attachment ID, and company ID.
     *
     * @param entityType   type of entity
     * @param entityId     entity ID
     * @param attachmentId attachment ID
     * @param companyId    company ID
     * @return optional attachment
     */
    Optional<Attachment> findByEntityTypeAndEntityIdAndIdAndCompanyId(
            AttachmentEntityType entityType, UUID entityId, UUID attachmentId, Long companyId);

    /**
     * Count attachments for an entity (company-scoped).
     *
     * @param entityType type of entity
     * @param entityId   entity ID
     * @param companyId  company ID
     * @return count of attachments
     */
    long countByEntityTypeAndEntityIdAndCompanyId(
            AttachmentEntityType entityType, UUID entityId, Long companyId);

    /**
     * Calculate total file size for an entity's attachments.
     *
     * @param entityType type of entity
     * @param entityId   entity ID
     * @param companyId  company ID
     * @return total size in bytes
     */
    @Query("SELECT COALESCE(SUM(a.fileSize), 0) FROM Attachment a " +
            "WHERE a.entityType = :entityType AND a.entityId = :entityId AND a.companyId = :companyId")
    long sumFileSizeByEntityTypeAndEntityIdAndCompanyId(
            @Param("entityType") AttachmentEntityType entityType,
            @Param("entityId") UUID entityId,
            @Param("companyId") Long companyId);

    /**
     * Delete all attachments for an entity.
     *
     * @param entityType type of entity
     * @param entityId   entity ID
     * @param companyId  company ID
     */
    void deleteByEntityTypeAndEntityIdAndCompanyId(
            AttachmentEntityType entityType, UUID entityId, Long companyId);

    // ==================== Convenience methods for specific entity types
    // ====================

    /**
     * Find all attachments for a voucher (company-scoped).
     */
    default List<Attachment> findByVoucherIdAndCompanyId(UUID voucherId, Long companyId) {
        return findByEntityTypeAndEntityIdAndCompanyId(AttachmentEntityType.VOUCHER, voucherId, companyId);
    }

    /**
     * Find all attachments for a purchase bill (company-scoped).
     */
    default List<Attachment> findByPurchaseBillIdAndCompanyId(UUID purchaseBillId, Long companyId) {
        return findByEntityTypeAndEntityIdAndCompanyId(AttachmentEntityType.PURCHASE_BILL, purchaseBillId, companyId);
    }

    /**
     * Find all attachments for a sales invoice (company-scoped).
     */
    default List<Attachment> findBySalesInvoiceIdAndCompanyId(UUID salesInvoiceId, Long companyId) {
        return findByEntityTypeAndEntityIdAndCompanyId(AttachmentEntityType.SALES_INVOICE, salesInvoiceId, companyId);
    }

    /**
     * Find voucher attachment by voucher ID, attachment ID, and company ID.
     */
    default Optional<Attachment> findByVoucherIdAndIdAndCompanyId(UUID voucherId, UUID attachmentId, Long companyId) {
        return findByEntityTypeAndEntityIdAndIdAndCompanyId(AttachmentEntityType.VOUCHER, voucherId, attachmentId,
                companyId);
    }

    /**
     * Find purchase bill attachment by bill ID, attachment ID, and company ID.
     */
    default Optional<Attachment> findByPurchaseBillIdAndIdAndCompanyId(UUID billId, UUID attachmentId, Long companyId) {
        return findByEntityTypeAndEntityIdAndIdAndCompanyId(AttachmentEntityType.PURCHASE_BILL, billId, attachmentId,
                companyId);
    }

    /**
     * Find sales invoice attachment by invoice ID, attachment ID, and company ID.
     */
    default Optional<Attachment> findBySalesInvoiceIdAndIdAndCompanyId(UUID invoiceId, UUID attachmentId,
            Long companyId) {
        return findByEntityTypeAndEntityIdAndIdAndCompanyId(AttachmentEntityType.SALES_INVOICE, invoiceId, attachmentId,
                companyId);
    }

    /**
     * Count attachments for a voucher (company-scoped).
     */
    default long countByVoucherIdAndCompanyId(UUID voucherId, Long companyId) {
        return countByEntityTypeAndEntityIdAndCompanyId(AttachmentEntityType.VOUCHER, voucherId, companyId);
    }

    /**
     * Count attachments for a purchase bill (company-scoped).
     */
    default long countByPurchaseBillIdAndCompanyId(UUID purchaseBillId, Long companyId) {
        return countByEntityTypeAndEntityIdAndCompanyId(AttachmentEntityType.PURCHASE_BILL, purchaseBillId, companyId);
    }

    /**
     * Count attachments for a sales invoice (company-scoped).
     */
    default long countBySalesInvoiceIdAndCompanyId(UUID salesInvoiceId, Long companyId) {
        return countByEntityTypeAndEntityIdAndCompanyId(AttachmentEntityType.SALES_INVOICE, salesInvoiceId, companyId);
    }
}
