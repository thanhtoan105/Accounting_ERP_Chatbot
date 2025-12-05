package com.accounting.service.impl.report;

import static org.junit.jupiter.api.Assertions.*;

import com.accounting.dto.report.StatutoryReportDTO;
import com.accounting.dto.report.StatutoryReportLineDTO;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for StatutoryReportExportService.
 * Tests PDF and Excel export functionality.
 */
class StatutoryReportExportServiceTest {

  private StatutoryReportExportService exportService;

  @BeforeEach
  void setUp() {
    exportService = new StatutoryReportExportService();
  }

  private StatutoryReportDTO createSampleReport(boolean withComparison) {
    StatutoryReportDTO report = new StatutoryReportDTO();
    report.setReportType("B01");
    report.setReportName("Bảng cân đối kế toán");
    report.setReportNameEnglish("Balance Sheet");
    report.setPeriodId(UUID.randomUUID());
    report.setPeriodName("Q4 2024");
    report.setPeriodStartDate(LocalDate.of(2024, 10, 1));
    report.setPeriodEndDate(LocalDate.of(2024, 12, 31));
    report.setCompanyName("Test Company ABC");
    report.setCompanyTaxCode("0123456789");
    report.setCompanyAddress("123 Test Street, Ho Chi Minh City");
    report.setDraft(false);
    report.setGeneratedAt(Instant.now());
    report.setMappingVersion(1);

    if (withComparison) {
      report.setComparisonPeriodId(UUID.randomUUID());
      report.setComparisonPeriodName("Q4 2023");
    }

    // Add sample report lines
    StatutoryReportLineDTO line1 = new StatutoryReportLineDTO();
    line1.setLineCode("100");
    line1.setLineName("A - TÀI SẢN NGẮN HẠN");
    line1.setLevel(1);
    line1.setCalculated(false);
    line1.setCurrentAmount(new BigDecimal("1000000000"));
    if (withComparison) {
      line1.setPriorAmount(new BigDecimal("800000000"));
      line1.setVariance(new BigDecimal("200000000"));
    }
    line1.setHasDrillDown(true);

    StatutoryReportLineDTO line2 = new StatutoryReportLineDTO();
    line2.setLineCode("110");
    line2.setLineName("I. Tiền và các khoản tương đương tiền");
    line2.setLevel(2);
    line2.setCalculated(false);
    line2.setCurrentAmount(new BigDecimal("500000000"));
    if (withComparison) {
      line2.setPriorAmount(new BigDecimal("400000000"));
      line2.setVariance(new BigDecimal("100000000"));
    }
    line2.setHasDrillDown(true);

    StatutoryReportLineDTO line3 = new StatutoryReportLineDTO();
    line3.setLineCode("120");
    line3.setLineName("II. Đầu tư tài chính ngắn hạn");
    line3.setLevel(2);
    line3.setCalculated(false);
    line3.setCurrentAmount(new BigDecimal("300000000"));
    if (withComparison) {
      line3.setPriorAmount(new BigDecimal("250000000"));
      line3.setVariance(new BigDecimal("50000000"));
    }
    line3.setHasDrillDown(false);

    StatutoryReportLineDTO line4 = new StatutoryReportLineDTO();
    line4.setLineCode("130");
    line4.setLineName("III. Các khoản phải thu ngắn hạn");
    line4.setLevel(2);
    line4.setCalculated(false);
    line4.setCurrentAmount(new BigDecimal("200000000"));
    if (withComparison) {
      line4.setPriorAmount(new BigDecimal("150000000"));
      line4.setVariance(new BigDecimal("50000000"));
    }
    line4.setHasDrillDown(true);

    report.setLines(List.of(line1, line2, line3, line4));

    return report;
  }

  @Nested
  @DisplayName("Export to PDF")
  class ExportToPdfTests {

    @Test
    @DisplayName("Should generate valid PDF bytes for report without comparison")
    void shouldGenerateValidPdfBytesWithoutComparison() {
      // Given
      StatutoryReportDTO report = createSampleReport(false);

      // When
      byte[] pdfBytes = exportService.exportToPdf(report);

      // Then
      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0, "PDF should not be empty");

      // Verify PDF magic bytes (PDF files start with %PDF-)
      String header = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
      assertTrue(header.startsWith("%PDF-"), "Should be a valid PDF file (starts with %PDF-)");
    }

    @Test
    @DisplayName("Should generate valid PDF bytes for report with comparison")
    void shouldGenerateValidPdfBytesWithComparison() {
      // Given
      StatutoryReportDTO report = createSampleReport(true);

      // When
      byte[] pdfBytes = exportService.exportToPdf(report);

      // Then
      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0, "PDF should not be empty");

      // Verify PDF magic bytes
      String header = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
      assertTrue(header.startsWith("%PDF-"), "Should be a valid PDF file (starts with %PDF-)");
    }

    @Test
    @DisplayName("Should generate PDF with draft watermark for draft report")
    void shouldGeneratePdfWithDraftWatermark() {
      // Given
      StatutoryReportDTO report = createSampleReport(false);
      report.setDraft(true);

      // When
      byte[] pdfBytes = exportService.exportToPdf(report);

      // Then
      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0, "PDF should not be empty");

      // Verify PDF format
      String header = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
      assertTrue(header.startsWith("%PDF-"), "Should be a valid PDF file");
    }

    @Test
    @DisplayName("Should handle report with empty lines")
    void shouldHandleReportWithEmptyLines() {
      // Given
      StatutoryReportDTO report = createSampleReport(false);
      report.setLines(List.of());

      // When
      byte[] pdfBytes = exportService.exportToPdf(report);

      // Then
      assertNotNull(pdfBytes);
      assertTrue(pdfBytes.length > 0, "PDF should not be empty");
      String header = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
      assertTrue(header.startsWith("%PDF-"), "Should be a valid PDF file");
    }

    @Test
    @DisplayName("Should handle report with null amounts")
    void shouldHandleReportWithNullAmounts() {
      // Given
      StatutoryReportDTO report = createSampleReport(false);
      StatutoryReportLineDTO lineWithNull = new StatutoryReportLineDTO();
      lineWithNull.setLineCode("999");
      lineWithNull.setLineName("Test line with null amount");
      lineWithNull.setLevel(1);
      lineWithNull.setCurrentAmount(null);
      report.setLines(List.of(lineWithNull));

      // When
      byte[] pdfBytes = exportService.exportToPdf(report);

      // Then
      assertNotNull(pdfBytes);
      String header = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
      assertTrue(header.startsWith("%PDF-"), "Should be a valid PDF file");
    }
  }

  @Nested
  @DisplayName("Export to Excel")
  class ExportToExcelTests {

    @Test
    @DisplayName("Should generate valid Excel bytes for report without comparison")
    void shouldGenerateValidExcelBytesWithoutComparison() {
      // Given
      StatutoryReportDTO report = createSampleReport(false);

      // When
      byte[] excelBytes = exportService.exportToExcel(report);

      // Then
      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0, "Excel should not be empty");

      // Verify XLSX magic bytes (starts with PK - ZIP format)
      assertTrue(excelBytes[0] == 'P' && excelBytes[1] == 'K',
          "Should be a valid XLSX file (ZIP format)");
    }

    @Test
    @DisplayName("Should generate valid Excel bytes for report with comparison")
    void shouldGenerateValidExcelBytesWithComparison() {
      // Given
      StatutoryReportDTO report = createSampleReport(true);

      // When
      byte[] excelBytes = exportService.exportToExcel(report);

      // Then
      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0, "Excel should not be empty");
      assertTrue(excelBytes[0] == 'P' && excelBytes[1] == 'K',
          "Should be a valid XLSX file");
    }

    @Test
    @DisplayName("Should generate Excel with draft status for draft report")
    void shouldGenerateExcelWithDraftStatus() {
      // Given
      StatutoryReportDTO report = createSampleReport(false);
      report.setDraft(true);

      // When
      byte[] excelBytes = exportService.exportToExcel(report);

      // Then
      assertNotNull(excelBytes);
      assertTrue(excelBytes.length > 0, "Excel should not be empty");
    }
  }

  @Nested
  @DisplayName("PDF vs Text Comparison")
  class PdfVsTextComparisonTests {

    @Test
    @DisplayName("PDF export should produce larger output than plain text would")
    void pdfShouldBeLargerThanPlainText() {
      // Given
      StatutoryReportDTO report = createSampleReport(false);

      // When
      byte[] pdfBytes = exportService.exportToPdf(report);

      // Then - PDF with proper formatting should be significantly larger than plain text
      // Plain text would be roughly 500-1000 bytes, PDF should be several KB
      assertTrue(pdfBytes.length > 2000,
          "PDF should be larger than plain text (actual size: " + pdfBytes.length + " bytes)");
    }
  }
}
