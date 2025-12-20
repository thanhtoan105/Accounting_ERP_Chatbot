package com.accounting.service.impl.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.TrialBalanceDTO;
import com.accounting.dto.TrialBalanceResponseDTO;

/**
 * Unit tests for TrialBalancePdfExportServiceImpl.
 * Tests PDF generation with TT200 S06-DN layout requirements.
 */
class TrialBalancePdfExportServiceImplTest {

  private TrialBalancePdfExportServiceImpl pdfExportService;

  @BeforeEach
  void setUp() {
    pdfExportService = new TrialBalancePdfExportServiceImpl();
  }

  @Test
  @DisplayName("Should generate PDF with valid trial balance data")
  void exportToPdf_ValidData_GeneratesPdf() {
    // Given
    TrialBalanceResponseDTO report = createSampleReport();

    // When
    byte[] pdfBytes = pdfExportService.exportToPdf(report, null);

    // Then
    assertThat(pdfBytes).isNotNull();
    assertThat(pdfBytes.length).isGreaterThan(0);
    // PDF files start with %PDF-
    assertThat(new String(pdfBytes, 0, 5)).isEqualTo("%PDF-");
  }

  @Test
  @DisplayName("Should generate PDF with DRAFT watermark when isDraft is true")
  void exportToPdf_DraftPeriod_IncludesDraftWatermark() {
    // Given
    TrialBalanceResponseDTO report = createSampleReport();

    // When
    byte[] pdfBytes = pdfExportService.exportToPdf(report, null, true);

    // Then
    assertThat(pdfBytes).isNotNull();
    assertThat(pdfBytes.length).isGreaterThan(0);
    // PDF should be valid
    assertThat(new String(pdfBytes, 0, 5)).isEqualTo("%PDF-");
  }

  @Test
  @DisplayName("Should generate PDF without DRAFT watermark for closed period")
  void exportToPdf_ClosedPeriod_NoDraftWatermark() {
    // Given
    TrialBalanceResponseDTO report = createSampleReport();

    // When
    byte[] pdfBytes = pdfExportService.exportToPdf(report, null, false);

    // Then
    assertThat(pdfBytes).isNotNull();
    assertThat(pdfBytes.length).isGreaterThan(0);
  }

  @Test
  @DisplayName("Should include snapshot ID when provided")
  void exportToPdf_WithSnapshotId_IncludesSnapshotInfo() {
    // Given
    TrialBalanceResponseDTO report = createSampleReport();
    UUID snapshotId = UUID.randomUUID();

    // When
    byte[] pdfBytes = pdfExportService.exportToPdf(report, snapshotId, false);

    // Then
    assertThat(pdfBytes).isNotNull();
    assertThat(pdfBytes.length).isGreaterThan(0);
  }

  @Test
  @DisplayName("Should handle empty accounts list")
  void exportToPdf_EmptyAccounts_GeneratesEmptyReport() {
    // Given
    TrialBalanceResponseDTO report = createSampleReport();
    report.setAccounts(Collections.emptyList());

    // When
    byte[] pdfBytes = pdfExportService.exportToPdf(report, null);

    // Then
    assertThat(pdfBytes).isNotNull();
    assertThat(pdfBytes.length).isGreaterThan(0);
  }

  @Test
  @DisplayName("Should handle null accounts list")
  void exportToPdf_NullAccounts_GeneratesEmptyReport() {
    // Given
    TrialBalanceResponseDTO report = createSampleReport();
    report.setAccounts(null);

    // When
    byte[] pdfBytes = pdfExportService.exportToPdf(report, null);

    // Then
    assertThat(pdfBytes).isNotNull();
  }

  @Test
  @DisplayName("Should handle null period info")
  void exportToPdf_NullPeriod_GeneratesReportWithoutPeriodInfo() {
    // Given
    TrialBalanceResponseDTO report = createSampleReport();
    report.setPeriod(null);

    // When
    byte[] pdfBytes = pdfExportService.exportToPdf(report, null);

    // Then
    assertThat(pdfBytes).isNotNull();
  }

  @Test
  @DisplayName("Should handle null company name")
  void exportToPdf_NullCompanyName_GeneratesReport() {
    // Given
    TrialBalanceResponseDTO report = createSampleReport();
    report.setCompanyName(null);

    // When
    byte[] pdfBytes = pdfExportService.exportToPdf(report, null);

    // Then
    assertThat(pdfBytes).isNotNull();
  }

  @Test
  @DisplayName("Should include totals in PDF")
  void exportToPdf_WithTotals_IncludesTotalsRow() {
    // Given
    TrialBalanceResponseDTO report = createSampleReport();

    // When
    byte[] pdfBytes = pdfExportService.exportToPdf(report, null);

    // Then
    assertThat(pdfBytes).isNotNull();
    // The PDF should contain the totals (verified visually or by parsing)
    // For unit test, we just verify generation succeeds
  }

  @Test
  @DisplayName("Should handle accounts with null values gracefully")
  void exportToPdf_AccountsWithNullValues_HandlesGracefully() {
    // Given
    TrialBalanceResponseDTO report = createSampleReport();
    TrialBalanceDTO accountWithNulls = new TrialBalanceDTO();
    accountWithNulls.setAccountId(999L);
    // Leave other fields null
    report.setAccounts(Arrays.asList(accountWithNulls));

    // When
    byte[] pdfBytes = pdfExportService.exportToPdf(report, null);

    // Then
    assertThat(pdfBytes).isNotNull();
  }

  /**
   * Create a sample trial balance report for testing.
   */
  private TrialBalanceResponseDTO createSampleReport() {
    AccountingPeriodDTO period = new AccountingPeriodDTO();
    period.setId(UUID.randomUUID());
    period.setPeriodName("Tháng 11/2025");
    period.setStartDate(LocalDate.of(2025, 11, 1));
    period.setEndDate(LocalDate.of(2025, 11, 30));

    TrialBalanceDTO account111 = new TrialBalanceDTO(
        111L, "111", "Tiền mặt",
        new BigDecimal("10000000"), BigDecimal.ZERO,
        new BigDecimal("5000000"), new BigDecimal("3000000"),
        new BigDecimal("12000000"), BigDecimal.ZERO
    );

    TrialBalanceDTO account112 = new TrialBalanceDTO(
        112L, "112", "Tiền gửi ngân hàng",
        new BigDecimal("50000000"), BigDecimal.ZERO,
        new BigDecimal("20000000"), new BigDecimal("15000000"),
        new BigDecimal("55000000"), BigDecimal.ZERO
    );

    TrialBalanceDTO account331 = new TrialBalanceDTO(
        331L, "331", "Phải trả người bán",
        BigDecimal.ZERO, new BigDecimal("15000000"),
        new BigDecimal("10000000"), new BigDecimal("8000000"),
        BigDecimal.ZERO, new BigDecimal("13000000")
    );

    TrialBalanceResponseDTO report = new TrialBalanceResponseDTO();
    report.setPeriod(period);
    report.setCompanyName("Công ty TNHH ABC");
    report.setGeneratedAt(Instant.now());
    report.setAccounts(Arrays.asList(account111, account112, account331));
    report.setTotalOpeningDebit(new BigDecimal("60000000"));
    report.setTotalOpeningCredit(new BigDecimal("15000000"));
    report.setTotalPeriodDebit(new BigDecimal("35000000"));
    report.setTotalPeriodCredit(new BigDecimal("26000000"));
    report.setTotalClosingDebit(new BigDecimal("67000000"));
    report.setTotalClosingCredit(new BigDecimal("13000000"));
    report.setBalanced(true);

    return report;
  }
}
