package com.accounting.service;

import com.accounting.dto.report.AccountContributionDTO;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for drill-down navigation from report lines to accounts to vouchers.
 * Provides paginated results for exploring report line details.
 */
public interface DrillDownService {

  /**
   * Get accounts contributing to a specific report line.
   *
   * @param reportType report type ('B01', 'B02', 'B03')
   * @param lineCode TT200 line code
   * @param periodId accounting period ID
   * @param pageable pagination settings
   * @return page of account contributions
   */
  Page<AccountContributionDTO> getAccountsForLine(
      String reportType, String lineCode, UUID periodId, Pageable pageable);

  /**
   * Get voucher summary for a specific account in a period.
   *
   * @param accountCode account code
   * @param periodId accounting period ID
   * @param pageable pagination settings
   * @return page of voucher summaries
   */
  Page<VoucherSummaryDTO> getVouchersForAccount(
      String accountCode, UUID periodId, Pageable pageable);

  /**
   * Get detailed voucher information including attachments.
   *
   * @param voucherId voucher ID
   * @return voucher detail DTO
   */
  VoucherDetailDTO getVoucherDetail(UUID voucherId);

  /**
   * Summary DTO for voucher list in drill-down.
   */
  record VoucherSummaryDTO(
      UUID voucherId,
      String voucherNumber,
      java.time.LocalDate voucherDate,
      String description,
      java.math.BigDecimal debit,
      java.math.BigDecimal credit,
      String status
  ) {}

  /**
   * Detail DTO for individual voucher with attachments.
   */
  record VoucherDetailDTO(
      UUID voucherId,
      String voucherNumber,
      java.time.LocalDate voucherDate,
      String voucherType,
      String description,
      java.util.List<VoucherLineDetailDTO> lines,
      java.util.List<AttachmentDTO> attachments,
      String status,
      String createdByName,
      java.time.Instant createdAt
  ) {}

  /**
   * Voucher line detail for drill-down view.
   */
  record VoucherLineDetailDTO(
      Integer lineNumber,
      String accountCode,
      String accountName,
      String description,
      java.math.BigDecimal debit,
      java.math.BigDecimal credit
  ) {}

  /**
   * Attachment summary for voucher detail.
   */
  record AttachmentDTO(
      UUID attachmentId,
      String fileName,
      String mimeType,
      Long fileSize,
      String downloadUrl
  ) {}
}
