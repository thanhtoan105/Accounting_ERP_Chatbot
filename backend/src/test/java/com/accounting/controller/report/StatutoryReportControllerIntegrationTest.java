package com.accounting.controller.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accounting.dto.report.AccountContributionDTO;
import com.accounting.dto.report.DetailedLedgerDTO;
import com.accounting.dto.report.StatutoryReportDTO;
import com.accounting.dto.report.StatutoryReportLineDTO;
import com.accounting.service.DrillDownService;
import com.accounting.service.DrillDownService.VoucherDetailDTO;
import com.accounting.service.DrillDownService.VoucherSummaryDTO;
import com.accounting.service.StatutoryReportService;
import com.accounting.service.StatutoryReportService.ReportValidationResult;
import com.accounting.service.impl.report.StatutoryReportExportService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for StatutoryReportController.
 * Tests all endpoints for B01, B02, B03, F01 reports, drill-down, and validation.
 */
@ExtendWith(MockitoExtension.class)
class StatutoryReportControllerIntegrationTest {

  @Mock
  private StatutoryReportService statutoryReportService;

  @Mock
  private StatutoryReportExportService exportService;

  @Mock
  private DrillDownService drillDownService;

  @InjectMocks
  private StatutoryReportController controller;

  private UUID periodId;
  private UUID comparisonPeriodId;
  private StatutoryReportDTO mockReport;

  @BeforeEach
  void setUp() {
    periodId = UUID.randomUUID();
    comparisonPeriodId = UUID.randomUUID();
    mockReport = createMockReport("B01");
  }

  private StatutoryReportDTO createMockReport(String reportType) {
    StatutoryReportDTO report = new StatutoryReportDTO();
    report.setReportType(reportType);
    report.setReportName("Bảng cân đối kế toán");
    report.setReportNameEnglish("Balance Sheet");
    report.setPeriodId(periodId);
    report.setPeriodName("Q4 2024");
    report.setPeriodStartDate(LocalDate.of(2024, 10, 1));
    report.setPeriodEndDate(LocalDate.of(2024, 12, 31));
    report.setCompanyName("Test Company");
    report.setCompanyTaxCode("0123456789");
    report.setCompanyAddress("123 Test Street");
    report.setDraft(false);
    report.setGeneratedAt(Instant.now());
    report.setMappingVersion(1);

    StatutoryReportLineDTO line = new StatutoryReportLineDTO();
    line.setLineCode("100");
    line.setLineName("Tài sản ngắn hạn");
    line.setLevel(1);
    line.setCurrentAmount(new BigDecimal("1000000"));
    line.setHasDrillDown(true);

    report.setLines(List.of(line));
    return report;
  }

  // ==================== B01 Balance Sheet Tests ====================

  @Nested
  @DisplayName("B01 Balance Sheet Endpoints")
  class B01BalanceSheetTests {

    @Test
    @DisplayName("GET /b01 - should return balance sheet")
    void generateBalanceSheet_ok() {
      when(statutoryReportService.generateBalanceSheet(eq(periodId), any()))
          .thenReturn(mockReport);

      ResponseEntity<StatutoryReportDTO> response = controller.generateBalanceSheet(periodId, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getReportType()).isEqualTo("B01");
      verify(statutoryReportService).generateBalanceSheet(periodId, null);
    }

    @Test
    @DisplayName("GET /b01 - should accept comparison period")
    void generateBalanceSheet_withComparison_ok() {
      mockReport.setComparisonPeriodId(comparisonPeriodId);
      mockReport.setComparisonPeriodName("Q4 2023");

      when(statutoryReportService.generateBalanceSheet(periodId, comparisonPeriodId))
          .thenReturn(mockReport);

      ResponseEntity<StatutoryReportDTO> response = controller.generateBalanceSheet(periodId, comparisonPeriodId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getComparisonPeriodId()).isEqualTo(comparisonPeriodId);
      verify(statutoryReportService).generateBalanceSheet(periodId, comparisonPeriodId);
    }

    @Test
    @DisplayName("GET /b01/export/excel - should export Excel file")
    void exportBalanceSheetToExcel_ok() {
      byte[] mockExcel = new byte[]{0x50, 0x4B, 0x03, 0x04}; // ZIP header
      when(statutoryReportService.generateBalanceSheet(eq(periodId), any()))
          .thenReturn(mockReport);
      when(exportService.exportToExcel(any())).thenReturn(mockExcel);

      ResponseEntity<byte[]> response = controller.exportBalanceSheetToExcel(periodId, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
      assertThat(response.getHeaders().getContentDisposition().toString())
          .contains("B01-DN_" + periodId + ".xlsx");
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody()[0]).isEqualTo((byte) 0x50);
    }

    @Test
    @DisplayName("GET /b01/export/pdf - should export PDF file")
    void exportBalanceSheetToPdf_ok() {
      byte[] mockPdf = "%PDF-1.4".getBytes();
      when(statutoryReportService.generateBalanceSheet(eq(periodId), any()))
          .thenReturn(mockReport);
      when(exportService.exportToPdf(any())).thenReturn(mockPdf);

      ResponseEntity<byte[]> response = controller.exportBalanceSheetToPdf(periodId, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
      assertThat(response.getHeaders().getContentDisposition().toString())
          .contains("B01-DN_" + periodId + ".pdf");
      assertThat(new String(response.getBody())).startsWith("%PDF");
    }
  }

  // ==================== B02 Income Statement Tests ====================

  @Nested
  @DisplayName("B02 Income Statement Endpoints")
  class B02IncomeStatementTests {

    @Test
    @DisplayName("GET /b02 - should return income statement")
    void generateIncomeStatement_ok() {
      StatutoryReportDTO incomeReport = createMockReport("B02");
      incomeReport.setReportName("Báo cáo kết quả hoạt động kinh doanh");

      when(statutoryReportService.generateIncomeStatement(eq(periodId), any()))
          .thenReturn(incomeReport);

      ResponseEntity<StatutoryReportDTO> response = controller.generateIncomeStatement(periodId, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getReportType()).isEqualTo("B02");
    }

    @Test
    @DisplayName("GET /b02/export/excel - should export Excel file")
    void exportIncomeStatementToExcel_ok() {
      byte[] mockExcel = new byte[]{0x50, 0x4B, 0x03, 0x04};
      when(statutoryReportService.generateIncomeStatement(eq(periodId), any()))
          .thenReturn(mockReport);
      when(exportService.exportToExcel(any())).thenReturn(mockExcel);

      ResponseEntity<byte[]> response = controller.exportIncomeStatementToExcel(periodId, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentDisposition().toString())
          .contains("B02-DN_" + periodId + ".xlsx");
    }

    @Test
    @DisplayName("GET /b02/export/pdf - should export PDF file")
    void exportIncomeStatementToPdf_ok() {
      byte[] mockPdf = "%PDF-1.4".getBytes();
      when(statutoryReportService.generateIncomeStatement(eq(periodId), any()))
          .thenReturn(mockReport);
      when(exportService.exportToPdf(any())).thenReturn(mockPdf);

      ResponseEntity<byte[]> response = controller.exportIncomeStatementToPdf(periodId, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    }
  }

  // ==================== B03 Cash Flow Statement Tests ====================

  @Nested
  @DisplayName("B03 Cash Flow Statement Endpoints")
  class B03CashFlowStatementTests {

    @Test
    @DisplayName("GET /b03 - should return cash flow statement")
    void generateCashFlowStatement_ok() {
      StatutoryReportDTO cashFlowReport = createMockReport("B03");
      cashFlowReport.setReportName("Báo cáo lưu chuyển tiền tệ");

      when(statutoryReportService.generateCashFlowStatement(periodId))
          .thenReturn(cashFlowReport);

      ResponseEntity<StatutoryReportDTO> response = controller.generateCashFlowStatement(periodId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getReportType()).isEqualTo("B03");
    }

    @Test
    @DisplayName("GET /b03/export/excel - should export Excel file")
    void exportCashFlowStatementToExcel_ok() {
      byte[] mockExcel = new byte[]{0x50, 0x4B, 0x03, 0x04};
      when(statutoryReportService.generateCashFlowStatement(periodId))
          .thenReturn(mockReport);
      when(exportService.exportToExcel(any())).thenReturn(mockExcel);

      ResponseEntity<byte[]> response = controller.exportCashFlowStatementToExcel(periodId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentDisposition().toString())
          .contains("B03-DN_" + periodId + ".xlsx");
    }
  }

  // ==================== F01 Detailed Ledger Tests ====================

  @Nested
  @DisplayName("F01 Detailed Ledger Endpoints")
  class F01DetailedLedgerTests {

    @Test
    @DisplayName("GET /f01 - should return detailed ledger")
    void generateDetailedLedger_ok() {
      DetailedLedgerDTO mockLedger = new DetailedLedgerDTO();
      mockLedger.setAccountCode("1111");
      mockLedger.setAccountName("Tiền mặt");
      mockLedger.setPeriodId(periodId);
      mockLedger.setOpeningBalance(BigDecimal.ZERO);
      mockLedger.setClosingBalance(new BigDecimal("1000000"));

      when(statutoryReportService.generateDetailedLedger(
          any(String[].class), eq(periodId), any(), any()))
          .thenReturn(mockLedger);

      ResponseEntity<DetailedLedgerDTO> response = controller.generateDetailedLedger(
          "1111,1112", periodId, null, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getAccountCode()).isEqualTo("1111");
    }

    @Test
    @DisplayName("GET /f01 - should accept subsidiary filters")
    void generateDetailedLedger_withSubsidiary_ok() {
      DetailedLedgerDTO mockLedger = new DetailedLedgerDTO();
      mockLedger.setAccountCode("1311");

      when(statutoryReportService.generateDetailedLedger(
          any(String[].class), eq(periodId), eq("CUSTOMER"), eq(123L)))
          .thenReturn(mockLedger);

      ResponseEntity<DetailedLedgerDTO> response = controller.generateDetailedLedger(
          "1311", periodId, "CUSTOMER", 123L);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      verify(statutoryReportService).generateDetailedLedger(
          any(String[].class), eq(periodId), eq("CUSTOMER"), eq(123L));
    }
  }

  // ==================== Drill-Down Tests ====================

  @Nested
  @DisplayName("Drill-Down Endpoints")
  class DrillDownTests {

    @Test
    @DisplayName("GET /drill-down/line/{reportType}/{lineCode} - should return accounts")
    void drillDownToAccounts_ok() {
      AccountContributionDTO account = new AccountContributionDTO();
      account.setAccountCode("1111");
      account.setAccountName("Tiền mặt");
      account.setContributionAmount(new BigDecimal("500000"));

      Page<AccountContributionDTO> mockPage = new PageImpl<>(List.of(account));
      Pageable pageable = PageRequest.of(0, 20);

      when(drillDownService.getAccountsForLine(eq("B01"), eq("100"), eq(periodId), any(Pageable.class)))
          .thenReturn(mockPage);

      ResponseEntity<Page<AccountContributionDTO>> response = controller.drillDownToAccounts(
          "B01", "100", periodId, pageable);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getContent()).hasSize(1);
      assertThat(response.getBody().getContent().get(0).getAccountCode()).isEqualTo("1111");
    }

    @Test
    @DisplayName("GET /drill-down/account/{accountCode} - should return vouchers")
    void drillDownToVouchers_ok() {
      VoucherSummaryDTO voucher = new VoucherSummaryDTO(
          UUID.randomUUID(),
          "PC001",
          LocalDate.now(),
          "Chi tiền mua hàng",
          new BigDecimal("100000"),
          BigDecimal.ZERO,
          "POSTED"
      );

      Page<VoucherSummaryDTO> mockPage = new PageImpl<>(List.of(voucher));
      Pageable pageable = PageRequest.of(0, 20);

      when(drillDownService.getVouchersForAccount(eq("1111"), eq(periodId), any(Pageable.class)))
          .thenReturn(mockPage);

      ResponseEntity<Page<VoucherSummaryDTO>> response = controller.drillDownToVouchers(
          "1111", periodId, pageable);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getContent()).hasSize(1);
      assertThat(response.getBody().getContent().get(0).voucherNumber()).isEqualTo("PC001");
    }

    @Test
    @DisplayName("GET /drill-down/voucher/{voucherId} - should return voucher detail")
    void getVoucherDetail_ok() {
      UUID voucherId = UUID.randomUUID();
      VoucherDetailDTO mockDetail = new VoucherDetailDTO(
          voucherId,
          "PC001",
          LocalDate.now(),
          "PAYMENT",
          "Chi tiền mua hàng",
          List.of(),
          List.of(),
          "POSTED",
          "admin",
          Instant.now()
      );

      when(drillDownService.getVoucherDetail(voucherId)).thenReturn(mockDetail);

      ResponseEntity<VoucherDetailDTO> response = controller.getVoucherDetail(voucherId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().voucherId()).isEqualTo(voucherId);
      assertThat(response.getBody().voucherNumber()).isEqualTo("PC001");
    }
  }

  // ==================== Validation Tests ====================

  @Nested
  @DisplayName("Validation Endpoints")
  class ValidationTests {

    @Test
    @DisplayName("GET /validate/{reportType} - should return valid result with OK status")
    void validateReport_valid_returnsOk() {
      ReportValidationResult validResult = new ReportValidationResult(
          true, List.of(), List.of(), true, false);

      when(statutoryReportService.validateForExport(periodId, "B01"))
          .thenReturn(validResult);

      ResponseEntity<ReportValidationResult> response = controller.validateReport("B01", periodId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().valid()).isTrue();
      assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    @DisplayName("GET /validate/{reportType} - should return 422 for invalid report")
    void validateReport_invalid_returnsUnprocessableEntity() {
      ReportValidationResult invalidResult = new ReportValidationResult(
          false,
          List.of("Line 100: Missing mapping for account 1111"),
          List.of(),
          false,
          false);

      when(statutoryReportService.validateForExport(periodId, "B01"))
          .thenReturn(invalidResult);

      ResponseEntity<ReportValidationResult> response = controller.validateReport("B01", periodId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
      assertThat(response.getBody().valid()).isFalse();
      assertThat(response.getBody().errors()).isNotEmpty();
    }

    @Test
    @DisplayName("GET /validate/{reportType} - should include draft warning")
    void validateReport_draft_includesWarning() {
      ReportValidationResult draftResult = new ReportValidationResult(
          true,
          List.of(),
          List.of("Report is DRAFT - period not closed"),
          true,
          true);

      when(statutoryReportService.validateForExport(periodId, "B01"))
          .thenReturn(draftResult);

      ResponseEntity<ReportValidationResult> response = controller.validateReport("B01", periodId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().isDraft()).isTrue();
      assertThat(response.getBody().warnings()).isNotEmpty();
    }
  }

  // ==================== Edge Cases Tests ====================

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Report with empty lines should still return successfully")
    void generateBalanceSheet_emptyLines_ok() {
      StatutoryReportDTO emptyReport = createMockReport("B01");
      emptyReport.setLines(List.of());

      when(statutoryReportService.generateBalanceSheet(eq(periodId), any()))
          .thenReturn(emptyReport);

      ResponseEntity<StatutoryReportDTO> response = controller.generateBalanceSheet(periodId, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getLines()).isEmpty();
    }

    @Test
    @DisplayName("Drill-down with empty results should return empty page")
    void drillDownToAccounts_empty_ok() {
      Page<AccountContributionDTO> emptyPage = new PageImpl<>(List.of());
      Pageable pageable = PageRequest.of(0, 20);

      when(drillDownService.getAccountsForLine(eq("B01"), eq("999"), eq(periodId), any(Pageable.class)))
          .thenReturn(emptyPage);

      ResponseEntity<Page<AccountContributionDTO>> response = controller.drillDownToAccounts(
          "B01", "999", periodId, pageable);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getContent()).isEmpty();
    }
  }
}
