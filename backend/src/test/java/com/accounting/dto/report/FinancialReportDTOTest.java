package com.accounting.dto.report;

import static org.junit.jupiter.api.Assertions.*;

import com.accounting.enums.ReportType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for financial report DTOs.
 * Tests the structure and behavior of FinancialReportDTO, ReportSectionDTO, and ReportLineDTO.
 */
class FinancialReportDTOTest {

  private FinancialReportDTO report;
  private UUID testPeriodId;
  private UUID testReportId;

  @BeforeEach
  void setUp() {
    report = new FinancialReportDTO();
    testPeriodId = UUID.randomUUID();
    testReportId = UUID.randomUUID();
  }

  @Test
  @DisplayName("Should create empty financial report with default values")
  void testEmptyReportCreation() {
    assertNotNull(report);
    assertEquals("VND", report.getCurrency());
    assertEquals(BigDecimal.ZERO, report.getTotalAssets());
    assertEquals(BigDecimal.ZERO, report.getTotalLiabilities());
    assertEquals(BigDecimal.ZERO, report.getTotalEquity());
    assertEquals(BigDecimal.ZERO, report.getTotalRevenue());
    assertEquals(BigDecimal.ZERO, report.getTotalExpenses());
    assertEquals(BigDecimal.ZERO, report.getNetIncome());
    assertNotNull(report.getSections());
    assertTrue(report.getSections().isEmpty());
  }

  @Test
  @DisplayName("Should populate report metadata correctly")
  void testReportMetadata() {
    report.setReportId(testReportId);
    report.setCompanyId(1L);
    report.setCompanyName("Test Company");
    report.setTaxCode("0123456789");
    report.setPeriodId(testPeriodId);
    report.setStartDate(LocalDate.of(2025, 1, 1));
    report.setEndDate(LocalDate.of(2025, 12, 31));
    report.setReportType(ReportType.BALANCE_SHEET);
    report.setGenerationDate(Instant.now());
    report.setGeneratedByName("John Doe");

    assertEquals(testReportId, report.getReportId());
    assertEquals(1L, report.getCompanyId());
    assertEquals("Test Company", report.getCompanyName());
    assertEquals("0123456789", report.getTaxCode());
    assertEquals(testPeriodId, report.getPeriodId());
    assertEquals(LocalDate.of(2025, 1, 1), report.getStartDate());
    assertEquals(LocalDate.of(2025, 12, 31), report.getEndDate());
    assertEquals(ReportType.BALANCE_SHEET, report.getReportType());
    assertNotNull(report.getGenerationDate());
    assertEquals("John Doe", report.getGeneratedByName());
  }

  @Test
  @DisplayName("Should create and populate report line with TT200 code")
  void testReportLineCreation() {
    ReportLineDTO line =
        new ReportLineDTO(
            "100",
            "Total Assets",
            "1",
            new BigDecimal("1000000"),
            new BigDecimal("900000"),
            1);

    assertEquals("100", line.getCode());
    assertEquals("Total Assets", line.getName());
    assertEquals("1", line.getNote());
    assertEquals(new BigDecimal("1000000"), line.getCurrentAmount());
    assertEquals(new BigDecimal("900000"), line.getPreviousAmount());
    assertEquals(1, line.getLevel());
  }

  @Test
  @DisplayName("Should handle null values in report line with defaults")
  void testReportLineNullHandling() {
    ReportLineDTO line = new ReportLineDTO("100", "Assets", null, null, null, null);

    assertEquals("100", line.getCode());
    assertEquals("Assets", line.getName());
    assertNull(line.getNote());
    assertEquals(BigDecimal.ZERO, line.getCurrentAmount());
    assertEquals(BigDecimal.ZERO, line.getPreviousAmount());
    assertEquals(1, line.getLevel());
  }

  @Test
  @DisplayName("Should create report section with lines")
  void testReportSectionCreation() {
    ReportSectionDTO section = new ReportSectionDTO();
    section.setSectionCode("ASSETS");
    section.setSectionName("Assets");

    ReportLineDTO line1 =
        new ReportLineDTO("110", "Cash", null, new BigDecimal("500000"), BigDecimal.ZERO, 2);
    ReportLineDTO line2 =
        new ReportLineDTO("120", "Receivables", null, new BigDecimal("300000"), BigDecimal.ZERO, 2);

    section.addLine(line1);
    section.addLine(line2);

    assertEquals("ASSETS", section.getSectionCode());
    assertEquals("Assets", section.getSectionName());
    assertEquals(2, section.getLines().size());
  }

  @Test
  @DisplayName("Should calculate section total from line items")
  void testSectionTotalCalculation() {
    ReportSectionDTO section = new ReportSectionDTO();
    section.setSectionCode("ASSETS");
    section.setSectionName("Assets");

    section.addLine(
        new ReportLineDTO("110", "Cash", null, new BigDecimal("500000"), BigDecimal.ZERO, 2));
    section.addLine(
        new ReportLineDTO("120", "Receivables", null, new BigDecimal("300000"), BigDecimal.ZERO, 2));
    section.addLine(
        new ReportLineDTO("130", "Inventory", null, new BigDecimal("200000"), BigDecimal.ZERO, 2));

    section.calculateTotal();

    assertEquals(new BigDecimal("1000000"), section.getTotal());
  }

  @Test
  @DisplayName("Should add sections to financial report")
  void testAddingSectionsToReport() {
    ReportSectionDTO assetsSection = new ReportSectionDTO();
    assetsSection.setSectionCode("ASSETS");
    assetsSection.setSectionName("Assets");
    assetsSection.setTotal(new BigDecimal("1000000"));

    ReportSectionDTO liabilitiesSection = new ReportSectionDTO();
    liabilitiesSection.setSectionCode("LIABILITIES");
    liabilitiesSection.setSectionName("Liabilities");
    liabilitiesSection.setTotal(new BigDecimal("600000"));

    report.addSection(assetsSection);
    report.addSection(liabilitiesSection);

    assertEquals(2, report.getSections().size());
    assertEquals("ASSETS", report.getSections().get(0).getSectionCode());
    assertEquals("LIABILITIES", report.getSections().get(1).getSectionCode());
  }

  @Test
  @DisplayName("Should calculate net income from revenue and expenses")
  void testNetIncomeCalculation() {
    report.setReportType(ReportType.INCOME_STATEMENT);
    report.setTotalRevenue(new BigDecimal("5000000"));
    report.setTotalExpenses(new BigDecimal("3000000"));

    report.calculateNetIncome();

    assertEquals(new BigDecimal("2000000"), report.getNetIncome());
  }

  @Test
  @DisplayName("Should validate balance sheet equation")
  void testBalanceSheetEquationValidation() {
    report.setReportType(ReportType.BALANCE_SHEET);
    report.setTotalAssets(new BigDecimal("1000000"));
    report.setTotalLiabilities(new BigDecimal("600000"));
    report.setTotalEquity(new BigDecimal("400000"));

    assertTrue(report.validateBalanceSheetEquation());
  }

  @Test
  @DisplayName("Should detect invalid balance sheet equation")
  void testInvalidBalanceSheetEquation() {
    report.setReportType(ReportType.BALANCE_SHEET);
    report.setTotalAssets(new BigDecimal("1000000"));
    report.setTotalLiabilities(new BigDecimal("600000"));
    report.setTotalEquity(new BigDecimal("300000")); // Wrong! Should be 400000

    assertFalse(report.validateBalanceSheetEquation());
  }

  @Test
  @DisplayName("Should pass validation for income statement (equation not applicable)")
  void testValidationNotApplicableForIncomeStatement() {
    report.setReportType(ReportType.INCOME_STATEMENT);
    report.setTotalAssets(new BigDecimal("1000000")); // These don't matter for income statement
    report.setTotalLiabilities(new BigDecimal("999999"));

    assertTrue(report.validateBalanceSheetEquation()); // Should return true as not applicable
  }

  @Test
  @DisplayName("Should create complete balance sheet report structure")
  void testCompleteBalanceSheetStructure() {
    // Setup report metadata
    report.setReportId(testReportId);
    report.setCompanyId(1L);
    report.setCompanyName("ABC Corporation");
    report.setTaxCode("0123456789");
    report.setPeriodId(testPeriodId);
    report.setStartDate(LocalDate.of(2025, 1, 1));
    report.setEndDate(LocalDate.of(2025, 12, 31));
    report.setCurrency("VND");
    report.setReportType(ReportType.BALANCE_SHEET);

    // Create Assets section
    ReportSectionDTO assetsSection = new ReportSectionDTO();
    assetsSection.setSectionCode("ASSETS");
    assetsSection.setSectionName("Assets");
    assetsSection.addLine(
        new ReportLineDTO("100", "Current Assets", null, new BigDecimal("5000000"), BigDecimal.ZERO, 1));
    assetsSection.addLine(
        new ReportLineDTO("110", "Cash", "1", new BigDecimal("2000000"), BigDecimal.ZERO, 2));
    assetsSection.addLine(
        new ReportLineDTO("120", "Receivables", "2", new BigDecimal("3000000"), BigDecimal.ZERO, 2));
    assetsSection.calculateTotal();

    // Create Liabilities section
    ReportSectionDTO liabilitiesSection = new ReportSectionDTO();
    liabilitiesSection.setSectionCode("LIABILITIES");
    liabilitiesSection.setSectionName("Liabilities");
    liabilitiesSection.addLine(
        new ReportLineDTO("200", "Current Liabilities", null, new BigDecimal("2000000"), BigDecimal.ZERO, 1));
    liabilitiesSection.calculateTotal();

    // Create Equity section
    ReportSectionDTO equitySection = new ReportSectionDTO();
    equitySection.setSectionCode("EQUITY");
    equitySection.setSectionName("Equity");
    equitySection.addLine(
        new ReportLineDTO("300", "Owner's Equity", null, new BigDecimal("3000000"), BigDecimal.ZERO, 1));
    equitySection.calculateTotal();

    // Add sections to report
    report.addSection(assetsSection);
    report.addSection(liabilitiesSection);
    report.addSection(equitySection);

    // Set totals
    report.setTotalAssets(assetsSection.getTotal());
    report.setTotalLiabilities(liabilitiesSection.getTotal());
    report.setTotalEquity(equitySection.getTotal());

    // Verify structure
    assertEquals(3, report.getSections().size());
    assertEquals(new BigDecimal("5000000"), report.getTotalAssets());
    assertEquals(new BigDecimal("2000000"), report.getTotalLiabilities());
    assertEquals(new BigDecimal("3000000"), report.getTotalEquity());
    assertTrue(report.validateBalanceSheetEquation());
  }

  @Test
  @DisplayName("Should create complete income statement report structure")
  void testCompleteIncomeStatementStructure() {
    // Setup report metadata
    report.setReportId(testReportId);
    report.setCompanyId(1L);
    report.setCompanyName("ABC Corporation");
    report.setReportType(ReportType.INCOME_STATEMENT);
    report.setPeriodId(testPeriodId);

    // Create Revenue section
    ReportSectionDTO revenueSection = new ReportSectionDTO();
    revenueSection.setSectionCode("REVENUE");
    revenueSection.setSectionName("Revenue");
    revenueSection.addLine(
        new ReportLineDTO("400", "Sales Revenue", null, new BigDecimal("10000000"), BigDecimal.ZERO, 1));
    revenueSection.addLine(
        new ReportLineDTO("410", "Service Revenue", null, new BigDecimal("2000000"), BigDecimal.ZERO, 1));
    revenueSection.calculateTotal();

    // Create Expenses section
    ReportSectionDTO expensesSection = new ReportSectionDTO();
    expensesSection.setSectionCode("EXPENSES");
    expensesSection.setSectionName("Expenses");
    expensesSection.addLine(
        new ReportLineDTO("500", "Cost of Sales", null, new BigDecimal("6000000"), BigDecimal.ZERO, 1));
    expensesSection.addLine(
        new ReportLineDTO("510", "Operating Expenses", null, new BigDecimal("2000000"), BigDecimal.ZERO, 1));
    expensesSection.calculateTotal();

    // Add sections to report
    report.addSection(revenueSection);
    report.addSection(expensesSection);

    // Set totals
    report.setTotalRevenue(revenueSection.getTotal());
    report.setTotalExpenses(expensesSection.getTotal());
    report.calculateNetIncome();

    // Verify structure
    assertEquals(2, report.getSections().size());
    assertEquals(new BigDecimal("12000000"), report.getTotalRevenue());
    assertEquals(new BigDecimal("8000000"), report.getTotalExpenses());
    assertEquals(new BigDecimal("4000000"), report.getNetIncome());
  }
}
