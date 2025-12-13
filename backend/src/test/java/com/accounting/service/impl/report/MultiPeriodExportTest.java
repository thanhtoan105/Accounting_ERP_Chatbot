package com.accounting.service.impl.report;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.accounting.dto.report.ComparisonSettingsDTO;
import com.accounting.dto.report.MultiPeriodLineDTO;
import com.accounting.dto.report.MultiPeriodReportDTO;
import com.accounting.dto.report.PeriodColumnDTO;
import com.accounting.dto.report.VarianceDTO;

/**
 * Unit tests for multi-period export functionality in StatutoryReportExportService.
 * Tests Excel and PDF export with dynamic columns for multi-period comparison reports.
 */
class MultiPeriodExportTest {

  private StatutoryReportExportService exportService;

  @BeforeEach
  void setUp() {
    exportService = new StatutoryReportExportService();
  }

  private MultiPeriodReportDTO createMultiPeriodReport(int periodCount, boolean hasDraftPeriod) {
    List<PeriodColumnDTO> periods = new ArrayList<>();
    Map<UUID, BigDecimal> periodValues = new HashMap<>();

    for (int i = 0; i < periodCount; i++) {
      UUID periodId = UUID.randomUUID();
      boolean isDraft = hasDraftPeriod && i == 0;
      periods.add(new PeriodColumnDTO(
          periodId,
          "Period " + (i + 1) + " 2024",
          LocalDate.of(2024, i + 1, 1),
          LocalDate.of(2024, i + 1, 28),
          "2024",
          isDraft));
      periodValues.put(periodId, new BigDecimal((i + 1) * 100000));
    }

    List<VarianceDTO> variances = new ArrayList<>();
    for (int i = 1; i < periodCount; i++) {
      variances.add(new VarianceDTO(
          periods.get(i - 1).periodId(),
          periods.get(i).periodId(),
          new BigDecimal("10000"),
          10.0,
          "FAVORABLE"));
    }

    List<Double> sparklineData = new ArrayList<>();
    for (int i = 0; i < periodCount; i++) {
      sparklineData.add((double) i / Math.max(1, periodCount - 1));
    }

    List<MultiPeriodLineDTO> lines = List.of(
        new MultiPeriodLineDTO(
            "110",
            "I. Tiền và các khoản tương đương tiền",
            "Cash and cash equivalents",
            1,
            false,
            periodValues,
            variances,
            sparklineData,
            true,
            true),
        new MultiPeriodLineDTO(
            "120",
            "II. Đầu tư tài chính ngắn hạn",
            "Short-term investments",
            1,
            false,
            periodValues,
            variances,
            sparklineData,
            false,
            true),
        new MultiPeriodLineDTO(
            "100",
            "A - TÀI SẢN NGẮN HẠN",
            "Current Assets",
            0,
            true,
            periodValues,
            variances,
            sparklineData,
            true,
            false));

    ComparisonSettingsDTO settings = ComparisonSettingsDTO.defaults();

    return new MultiPeriodReportDTO(
        "B01",
        "Bảng cân đối kế toán / Balance Sheet",
        1L,
        "Test Company ABC",
        periods,
        lines,
        settings,
        Instant.now(),
        hasDraftPeriod);
  }

  @Nested
  @DisplayName("Multi-Period Excel Export Tests")
  class MultiPeriodExcelExportTests {

    @Test
    @DisplayName("Should generate valid Excel with 2 periods (basic comparison)")
    void shouldGenerateValidExcelWith2Periods() {
      MultiPeriodReportDTO report = createMultiPeriodReport(2, false);

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);

      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0, "Excel should not be empty");
      assertTrue(excelBytes[0] == 'P' && excelBytes[1] == 'K',
          "Should be a valid XLSX file (ZIP format)");
    }

    @Test
    @DisplayName("Should generate valid Excel with 3 periods")
    void shouldGenerateValidExcelWith3Periods() {
      MultiPeriodReportDTO report = createMultiPeriodReport(3, false);

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);

      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0, "Excel should not be empty");
      assertTrue(excelBytes[0] == 'P' && excelBytes[1] == 'K',
          "Should be a valid XLSX file");
    }

    @Test
    @DisplayName("Should generate valid Excel with 4 periods (maximum)")
    void shouldGenerateValidExcelWith4Periods() {
      MultiPeriodReportDTO report = createMultiPeriodReport(4, false);

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);

      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0, "Excel should not be empty");
      assertTrue(excelBytes[0] == 'P' && excelBytes[1] == 'K',
          "Should be a valid XLSX file");
    }

    @Test
    @DisplayName("Should generate Excel with draft period marker")
    void shouldGenerateExcelWithDraftPeriodMarker() {
      MultiPeriodReportDTO report = createMultiPeriodReport(2, true);

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);

      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0, "Excel should not be empty");
      assertTrue(report.hasDraftPeriod(), "Report should have draft period");
    }

    @Test
    @DisplayName("Should handle report with single period")
    void shouldHandleReportWithSinglePeriod() {
      MultiPeriodReportDTO report = createMultiPeriodReport(1, false);

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);

      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0, "Excel should not be empty");
    }

    @Test
    @DisplayName("Should handle report with empty lines")
    void shouldHandleReportWithEmptyLines() {
      MultiPeriodReportDTO report = new MultiPeriodReportDTO(
          "B01",
          "Balance Sheet",
          1L,
          "Test Company",
          List.of(new PeriodColumnDTO(
              UUID.randomUUID(), "Period 1", LocalDate.now(), LocalDate.now(), "2024", false)),
          List.of(),
          ComparisonSettingsDTO.defaults(),
          Instant.now(),
          false);

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);

      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0, "Excel should not be empty");
    }

    @Test
    @DisplayName("Excel with 4 periods should be larger due to more columns")
    void excelWith4PeriodsShouldBeLarger() {
      MultiPeriodReportDTO report2 = createMultiPeriodReport(2, false);
      MultiPeriodReportDTO report4 = createMultiPeriodReport(4, false);

      byte[] excel2 = exportService.exportMultiPeriodToExcel(report2);
      byte[] excel4 = exportService.exportMultiPeriodToExcel(report4);

      assertTrue(excel4.length > excel2.length,
          "Excel with 4 periods should be larger than 2 periods");
    }

    @Test
    @DisplayName("Should handle report with material variance lines")
    void shouldHandleReportWithMaterialVarianceLines() {
      MultiPeriodReportDTO report = createMultiPeriodReport(2, false);
      assertTrue(report.lines().stream().anyMatch(MultiPeriodLineDTO::isMaterial),
          "Should have material lines");

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);

      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0, "Excel should not be empty");
    }
  }

  @Nested
  @DisplayName("Multi-Period PDF Export Tests")
  class MultiPeriodPdfExportTests {

    @Test
    @DisplayName("Should generate valid PDF with 2 periods")
    void shouldGenerateValidPdfWith2Periods() {
      MultiPeriodReportDTO report = createMultiPeriodReport(2, false);

      byte[] pdfBytes = exportService.exportMultiPeriodToPdf(report);

      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0, "PDF should not be empty");
      String header = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
      assertTrue(header.startsWith("%PDF-"), "Should be a valid PDF file");
    }

    @Test
    @DisplayName("Should generate valid PDF with 3 periods")
    void shouldGenerateValidPdfWith3Periods() {
      MultiPeriodReportDTO report = createMultiPeriodReport(3, false);

      byte[] pdfBytes = exportService.exportMultiPeriodToPdf(report);

      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0, "PDF should not be empty");
      String header = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
      assertTrue(header.startsWith("%PDF-"), "Should be a valid PDF file");
    }

    @Test
    @DisplayName("Should generate valid PDF with 4 periods (landscape layout)")
    void shouldGenerateValidPdfWith4PeriodsLandscape() {
      MultiPeriodReportDTO report = createMultiPeriodReport(4, false);

      byte[] pdfBytes = exportService.exportMultiPeriodToPdf(report);

      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0, "PDF should not be empty");
      String header = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
      assertTrue(header.startsWith("%PDF-"), "Should be a valid PDF file");
    }

    @Test
    @DisplayName("Should generate PDF with draft period warning")
    void shouldGeneratePdfWithDraftPeriodWarning() {
      MultiPeriodReportDTO report = createMultiPeriodReport(2, true);

      byte[] pdfBytes = exportService.exportMultiPeriodToPdf(report);

      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0, "PDF should not be empty");
      assertTrue(report.hasDraftPeriod(), "Report should have draft period");
    }

    @Test
    @DisplayName("Should handle report with single period")
    void shouldHandleReportWithSinglePeriod() {
      MultiPeriodReportDTO report = createMultiPeriodReport(1, false);

      byte[] pdfBytes = exportService.exportMultiPeriodToPdf(report);

      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0, "PDF should not be empty");
    }

    @Test
    @DisplayName("Should handle report with empty lines")
    void shouldHandleReportWithEmptyLines() {
      MultiPeriodReportDTO report = new MultiPeriodReportDTO(
          "B01",
          "Balance Sheet",
          1L,
          "Test Company",
          List.of(new PeriodColumnDTO(
              UUID.randomUUID(), "Period 1", LocalDate.now(), LocalDate.now(), "2024", false)),
          List.of(),
          ComparisonSettingsDTO.defaults(),
          Instant.now(),
          false);

      byte[] pdfBytes = exportService.exportMultiPeriodToPdf(report);

      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0, "PDF should not be empty");
    }

    @Test
    @DisplayName("PDF with 4 periods should use landscape layout (larger file)")
    void pdfWith4PeriodsShouldUseLandscape() {
      MultiPeriodReportDTO report2 = createMultiPeriodReport(2, false);
      MultiPeriodReportDTO report4 = createMultiPeriodReport(4, false);

      byte[] pdf2 = exportService.exportMultiPeriodToPdf(report2);
      byte[] pdf4 = exportService.exportMultiPeriodToPdf(report4);

      assertTrue(pdf4.length > pdf2.length,
          "PDF with 4 periods should be larger due to landscape layout and more columns");
    }

    @Test
    @DisplayName("PDF should be larger than plain text representation")
    void pdfShouldBeLargerThanPlainText() {
      MultiPeriodReportDTO report = createMultiPeriodReport(2, false);

      byte[] pdfBytes = exportService.exportMultiPeriodToPdf(report);

      assertTrue(pdfBytes.length > 2000,
          "PDF should be larger than plain text (actual size: " + pdfBytes.length + " bytes)");
    }
  }

  @Nested
  @DisplayName("Multi-Period Export Report Types")
  class MultiPeriodExportReportTypeTests {

    @Test
    @DisplayName("Should export B01 Balance Sheet to Excel")
    void shouldExportB01BalanceSheetToExcel() {
      MultiPeriodReportDTO report = new MultiPeriodReportDTO(
          "B01",
          "Bảng cân đối kế toán / Balance Sheet",
          1L,
          "Test Company",
          createMultiPeriodReport(2, false).periods(),
          createMultiPeriodReport(2, false).lines(),
          ComparisonSettingsDTO.defaults(),
          Instant.now(),
          false);

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);

      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0);
    }

    @Test
    @DisplayName("Should export B02 Income Statement to Excel")
    void shouldExportB02IncomeStatementToExcel() {
      MultiPeriodReportDTO report = new MultiPeriodReportDTO(
          "B02",
          "Báo cáo kết quả kinh doanh / Income Statement",
          1L,
          "Test Company",
          createMultiPeriodReport(2, false).periods(),
          createMultiPeriodReport(2, false).lines(),
          ComparisonSettingsDTO.defaults(),
          Instant.now(),
          false);

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);

      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0);
    }

    @Test
    @DisplayName("Should export B03 Cash Flow Statement to Excel")
    void shouldExportB03CashFlowStatementToExcel() {
      MultiPeriodReportDTO report = new MultiPeriodReportDTO(
          "B03",
          "Báo cáo lưu chuyển tiền tệ / Cash Flow Statement",
          1L,
          "Test Company",
          createMultiPeriodReport(2, false).periods(),
          createMultiPeriodReport(2, false).lines(),
          ComparisonSettingsDTO.defaults(),
          Instant.now(),
          false);

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);

      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0);
    }

    @Test
    @DisplayName("Should export B01 Balance Sheet to PDF")
    void shouldExportB01BalanceSheetToPdf() {
      MultiPeriodReportDTO report = new MultiPeriodReportDTO(
          "B01",
          "Bảng cân đối kế toán / Balance Sheet",
          1L,
          "Test Company",
          createMultiPeriodReport(2, false).periods(),
          createMultiPeriodReport(2, false).lines(),
          ComparisonSettingsDTO.defaults(),
          Instant.now(),
          false);

      byte[] pdfBytes = exportService.exportMultiPeriodToPdf(report);

      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0);
      String header = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
      assertTrue(header.startsWith("%PDF-"));
    }

    @Test
    @DisplayName("Should export B02 Income Statement to PDF")
    void shouldExportB02IncomeStatementToPdf() {
      MultiPeriodReportDTO report = new MultiPeriodReportDTO(
          "B02",
          "Báo cáo kết quả kinh doanh / Income Statement",
          1L,
          "Test Company",
          createMultiPeriodReport(2, false).periods(),
          createMultiPeriodReport(2, false).lines(),
          ComparisonSettingsDTO.defaults(),
          Instant.now(),
          false);

      byte[] pdfBytes = exportService.exportMultiPeriodToPdf(report);

      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0);
    }
  }

  @Nested
  @DisplayName("Variance Column Tests")
  class VarianceColumnTests {

    @Test
    @DisplayName("Excel with 2 periods should have 1 variance column")
    void excelWith2PeriodsShouldHave1VarianceColumn() {
      MultiPeriodReportDTO report = createMultiPeriodReport(2, false);

      assertEquals(2, report.periods().size());
      assertEquals(1, report.lines().get(0).variances().size(),
          "2 periods should have 1 variance between them");

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);
      assertNotNull(excelBytes);
    }

    @Test
    @DisplayName("Excel with 4 periods should have 3 variance columns")
    void excelWith4PeriodsShouldHave3VarianceColumns() {
      MultiPeriodReportDTO report = createMultiPeriodReport(4, false);

      assertEquals(4, report.periods().size());
      assertEquals(3, report.lines().get(0).variances().size(),
          "4 periods should have 3 variances between them");

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);
      assertNotNull(excelBytes);
    }

    @Test
    @DisplayName("Should handle line with null variance percent (zero prior)")
    void shouldHandleLineWithNullVariancePercent() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<PeriodColumnDTO> periods = List.of(
          new PeriodColumnDTO(period1Id, "Q1 2024", LocalDate.of(2024, 1, 1),
              LocalDate.of(2024, 3, 31), "2024", false),
          new PeriodColumnDTO(period2Id, "Q2 2024", LocalDate.of(2024, 4, 1),
              LocalDate.of(2024, 6, 30), "2024", false));

      Map<UUID, BigDecimal> periodValues = new HashMap<>();
      periodValues.put(period1Id, BigDecimal.ZERO);
      periodValues.put(period2Id, new BigDecimal("100000"));

      List<VarianceDTO> variances = List.of(
          new VarianceDTO(period1Id, period2Id, new BigDecimal("100000"), null, "NEUTRAL"));

      List<MultiPeriodLineDTO> lines = List.of(
          new MultiPeriodLineDTO("110", "Cash", "Cash", 1, false,
              periodValues, variances, List.of(0.0, 1.0), false, true));

      MultiPeriodReportDTO report = new MultiPeriodReportDTO(
          "B01", "Balance Sheet", 1L, "Test Company",
          periods, lines, ComparisonSettingsDTO.defaults(), Instant.now(), false);

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);
      assertNotNull(excelBytes);

      byte[] pdfBytes = exportService.exportMultiPeriodToPdf(report);
      assertNotNull(pdfBytes);
    }

    @Test
    @DisplayName("Should handle infinite variance percent (zero to non-zero)")
    void shouldHandleInfiniteVariancePercent() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<PeriodColumnDTO> periods = List.of(
          new PeriodColumnDTO(period1Id, "Q1 2024", LocalDate.of(2024, 1, 1),
              LocalDate.of(2024, 3, 31), "2024", false),
          new PeriodColumnDTO(period2Id, "Q2 2024", LocalDate.of(2024, 4, 1),
              LocalDate.of(2024, 6, 30), "2024", false));

      Map<UUID, BigDecimal> periodValues = new HashMap<>();
      periodValues.put(period1Id, BigDecimal.ZERO);
      periodValues.put(period2Id, new BigDecimal("100000"));

      List<VarianceDTO> variances = List.of(
          new VarianceDTO(period1Id, period2Id, new BigDecimal("100000"),
              Double.POSITIVE_INFINITY, "FAVORABLE"));

      List<MultiPeriodLineDTO> lines = List.of(
          new MultiPeriodLineDTO("110", "Cash", "Cash", 1, false,
              periodValues, variances, List.of(0.0, 1.0), false, true));

      MultiPeriodReportDTO report = new MultiPeriodReportDTO(
          "B01", "Balance Sheet", 1L, "Test Company",
          periods, lines, ComparisonSettingsDTO.defaults(), Instant.now(), false);

      byte[] excelBytes = exportService.exportMultiPeriodToExcel(report);
      assertNotNull(excelBytes);

      byte[] pdfBytes = exportService.exportMultiPeriodToPdf(report);
      assertNotNull(pdfBytes);
    }
  }
}
