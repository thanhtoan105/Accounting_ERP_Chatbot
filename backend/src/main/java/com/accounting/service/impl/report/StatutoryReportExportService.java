package com.accounting.service.impl.report;

import static net.sf.dynamicreports.report.builder.DynamicReports.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.accounting.dto.report.MultiPeriodLineDTO;
import com.accounting.dto.report.MultiPeriodReportDTO;
import com.accounting.dto.report.PeriodColumnDTO;
import com.accounting.dto.report.StatutoryReportDTO;
import com.accounting.dto.report.StatutoryReportLineDTO;
import com.accounting.dto.report.VarianceDTO;
import com.accounting.security.SecurityUtils;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.component.ComponentBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.constant.HorizontalTextAlignment;
import net.sf.dynamicreports.report.constant.PageOrientation;
import net.sf.dynamicreports.report.constant.PageType;
import net.sf.dynamicreports.report.constant.VerticalTextAlignment;
import net.sf.dynamicreports.report.datasource.DRDataSource;
import net.sf.jasperreports.engine.JRDataSource;

/**
 * Service for exporting statutory reports to PDF and Excel formats.
 * Uses Apache POI for Excel and JasperReports for PDF generation.
 */
@Service
public class StatutoryReportExportService {

  private static final Logger logger = LoggerFactory.getLogger(StatutoryReportExportService.class);
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

  // Report type to sheet name mapping
  private static final Map<String, String> SHEET_NAMES = Map.of(
      "B01", "Bang can doi ke toan (B01-DN)",
      "B02", "Bao cao KQHDKD (B02-DN)",
      "B03", "Bao cao luu chuyen tien te (B03-DN)"
  );

  /**
   * Export a statutory report to Excel format.
   *
   * @param report the report to export
   * @return Excel file as byte array
   */
  public byte[] exportToExcel(StatutoryReportDTO report) {
    try (Workbook workbook = new XSSFWorkbook()) {
      String sheetName = SHEET_NAMES.getOrDefault(report.getReportType(), report.getReportType());
      Sheet sheet = workbook.createSheet(sheetName);

      // Create styles
      CellStyle headerStyle = createHeaderStyle(workbook);
      CellStyle titleStyle = createTitleStyle(workbook);
      CellStyle numberStyle = createNumberStyle(workbook);
      CellStyle level1Style = createLevel1Style(workbook);
      CellStyle level2Style = createLevel2Style(workbook);

      int rowNum = 0;

      // Title
      rowNum = addTitleSection(sheet, rowNum, report, titleStyle);

      // Company info
      rowNum = addCompanyInfo(sheet, rowNum, report);

      // Period info
      rowNum = addPeriodInfo(sheet, rowNum, report);

      rowNum++; // Blank row

      // Column headers
      rowNum = addColumnHeaders(sheet, rowNum, report, headerStyle);

      // Data rows
      rowNum = addDataRows(sheet, rowNum, report, numberStyle, level1Style, level2Style);

      // Auto-size columns
      autoSizeColumns(sheet, report.hasComparison() ? 5 : 3);

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      return outputStream.toByteArray();

    } catch (IOException e) {
      logger.error("Failed to export report to Excel: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to export report to Excel", e);
    }
  }

  /**
   * Export a statutory report to PDF format.
   * Uses DynamicReports for professional PDF generation with proper formatting.
   *
   * @param report the report to export
   * @return PDF file as byte array
   */
  public byte[] exportToPdf(StatutoryReportDTO report) {
    try {
      return generatePdfWithDynamicReports(report);
    } catch (Exception e) {
      logger.error("Failed to export report to PDF: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to export report to PDF", e);
    }
  }

  // ==================== Excel Helper Methods ====================

  private int addTitleSection(Sheet sheet, int rowNum, StatutoryReportDTO report, CellStyle titleStyle) {
    Row titleRow = sheet.createRow(rowNum++);
    Cell titleCell = titleRow.createCell(0);
    titleCell.setCellValue(report.getReportName().toUpperCase());
    titleCell.setCellStyle(titleStyle);
    sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 4));

    // English subtitle
    Row subtitleRow = sheet.createRow(rowNum++);
    Cell subtitleCell = subtitleRow.createCell(0);
    subtitleCell.setCellValue(report.getReportNameEnglish());
    sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 4));

    rowNum++; // Blank row
    return rowNum;
  }

  private int addCompanyInfo(Sheet sheet, int rowNum, StatutoryReportDTO report) {
    Row companyRow = sheet.createRow(rowNum++);
    companyRow.createCell(0).setCellValue("Doanh nghiệp:");
    companyRow.createCell(1).setCellValue(report.getCompanyName());

    if (report.getCompanyTaxCode() != null) {
      Row taxCodeRow = sheet.createRow(rowNum++);
      taxCodeRow.createCell(0).setCellValue("Mã số thuế:");
      taxCodeRow.createCell(1).setCellValue(report.getCompanyTaxCode());
    }

    if (report.getCompanyAddress() != null) {
      Row addressRow = sheet.createRow(rowNum++);
      addressRow.createCell(0).setCellValue("Địa chỉ:");
      addressRow.createCell(1).setCellValue(report.getCompanyAddress());
    }

    return rowNum;
  }

  private int addPeriodInfo(Sheet sheet, int rowNum, StatutoryReportDTO report) {
    Row periodRow = sheet.createRow(rowNum++);
    periodRow.createCell(0).setCellValue("Kỳ báo cáo:");
    periodRow.createCell(1).setCellValue(report.getPeriodName());

    Row dateRangeRow = sheet.createRow(rowNum++);
    dateRangeRow.createCell(0).setCellValue("Từ ngày - đến ngày:");
    dateRangeRow.createCell(1).setCellValue(
        formatDate(report.getPeriodStartDate()) + " - " + formatDate(report.getPeriodEndDate()));

    if (report.isDraft()) {
      Row draftRow = sheet.createRow(rowNum++);
      draftRow.createCell(0).setCellValue("Trạng thái:");
      draftRow.createCell(1).setCellValue("DỰ THẢO (Kỳ chưa đóng)");
    }

    Row generatedRow = sheet.createRow(rowNum++);
    generatedRow.createCell(0).setCellValue("Ngày lập:");
    generatedRow.createCell(1).setCellValue(
        report.getGeneratedAt() != null
            ? TIMESTAMP_FORMATTER.format(report.getGeneratedAt().atZone(DEFAULT_ZONE))
            : "");

    return rowNum;
  }

  private int addColumnHeaders(Sheet sheet, int rowNum, StatutoryReportDTO report, CellStyle headerStyle) {
    Row headerRow = sheet.createRow(rowNum++);

    int col = 0;
    createStyledCell(headerRow, col++, "Mã số", headerStyle);
    createStyledCell(headerRow, col++, "Chỉ tiêu", headerStyle);
    createStyledCell(headerRow, col++, "Số cuối kỳ", headerStyle);

    if (report.hasComparison()) {
      createStyledCell(headerRow, col++, "Số đầu năm", headerStyle);
      createStyledCell(headerRow, col++, "Chênh lệch", headerStyle);
    }

    return rowNum;
  }

  private int addDataRows(Sheet sheet, int rowNum, StatutoryReportDTO report,
      CellStyle numberStyle, CellStyle level1Style, CellStyle level2Style) {

    for (StatutoryReportLineDTO line : report.getLines()) {
      Row row = sheet.createRow(rowNum++);

      // Choose style based on level
      CellStyle textStyle = line.getLevel() != null && line.getLevel() == 1 ? level1Style : level2Style;

      int col = 0;

      // Line code
      Cell codeCell = row.createCell(col++);
      codeCell.setCellValue(line.getLineCode());
      codeCell.setCellStyle(textStyle);

      // Line name with indentation
      Cell nameCell = row.createCell(col++);
      String indent = "  ".repeat(Math.max(0, (line.getLevel() != null ? line.getLevel() : 1) - 1));
      nameCell.setCellValue(indent + line.getLineName());
      nameCell.setCellStyle(textStyle);

      // Current amount
      Cell currentCell = row.createCell(col++);
      if (line.getCurrentAmount() != null && line.getCurrentAmount().compareTo(BigDecimal.ZERO) != 0) {
        currentCell.setCellValue(line.getCurrentAmount().doubleValue());
        currentCell.setCellStyle(numberStyle);
      }

      // Prior amount and variance (if comparison)
      if (report.hasComparison()) {
        Cell priorCell = row.createCell(col++);
        if (line.getPriorAmount() != null && line.getPriorAmount().compareTo(BigDecimal.ZERO) != 0) {
          priorCell.setCellValue(line.getPriorAmount().doubleValue());
          priorCell.setCellStyle(numberStyle);
        }

        Cell varianceCell = row.createCell(col++);
        if (line.getVariance() != null && line.getVariance().compareTo(BigDecimal.ZERO) != 0) {
          varianceCell.setCellValue(line.getVariance().doubleValue());
          varianceCell.setCellStyle(numberStyle);
        }
      }
    }

    return rowNum;
  }

  private void autoSizeColumns(Sheet sheet, int numColumns) {
    for (int i = 0; i < numColumns; i++) {
      sheet.autoSizeColumn(i);
    }
    // Ensure minimum width for name column
    if (sheet.getColumnWidth(1) < 15000) {
      sheet.setColumnWidth(1, 15000);
    }
  }

  private void createStyledCell(Row row, int col, String value, CellStyle style) {
    Cell cell = row.createCell(col);
    cell.setCellValue(value);
    cell.setCellStyle(style);
  }

  private CellStyle createHeaderStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    style.setFont(font);
    style.setAlignment(HorizontalAlignment.CENTER);
    return style;
  }

  private CellStyle createTitleStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    font.setFontHeightInPoints((short) 14);
    style.setFont(font);
    style.setAlignment(HorizontalAlignment.CENTER);
    return style;
  }

  private CellStyle createNumberStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0"));
    style.setAlignment(HorizontalAlignment.RIGHT);
    return style;
  }

  private CellStyle createLevel1Style(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    style.setFont(font);
    return style;
  }

  private CellStyle createLevel2Style(Workbook workbook) {
    return workbook.createCellStyle();
  }

  private String formatDate(LocalDate date) {
    return date != null ? DATE_FORMATTER.format(date) : "-";
  }

  // ==================== PDF Helper Methods ====================

  /**
   * Generate a professional PDF using DynamicReports library.
   * Creates a properly formatted statutory financial report.
   */
  private byte[] generatePdfWithDynamicReports(StatutoryReportDTO report) throws Exception {
    // Define styles
    StyleBuilder boldStyle = stl.style().bold();
    StyleBuilder boldCenteredStyle = stl.style(boldStyle)
        .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER);
    StyleBuilder titleStyle = stl.style(boldCenteredStyle)
        .setFontSize(16)
        .setVerticalTextAlignment(VerticalTextAlignment.MIDDLE);
    StyleBuilder subtitleStyle = stl.style()
        .setFontSize(10)
        .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER);
    StyleBuilder columnTitleStyle = stl.style(boldStyle)
        .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)
        .setBackgroundColor(new Color(240, 240, 240))
        .setBorder(stl.pen1Point())
        .setPadding(5);
    StyleBuilder columnStyle = stl.style()
        .setBorder(stl.pen1Point())
        .setPadding(3);
    StyleBuilder numberStyle = stl.style(columnStyle)
        .setHorizontalTextAlignment(HorizontalTextAlignment.RIGHT)
        .setPattern("#,##0");
    StyleBuilder level1Style = stl.style(columnStyle).bold();
    StyleBuilder level1NumberStyle = stl.style(numberStyle).bold();

    // Define columns
    TextColumnBuilder<String> lineCodeColumn = col.column("Mã số", "lineCode", type.stringType())
        .setStyle(columnStyle)
        .setTitleStyle(columnTitleStyle)
        .setWidth(40);

    TextColumnBuilder<String> lineNameColumn = col.column("Chỉ tiêu", "lineName", type.stringType())
        .setStyle(columnStyle)
        .setTitleStyle(columnTitleStyle)
        .setWidth(200);

    TextColumnBuilder<BigDecimal> currentAmountColumn = col.column("Số cuối kỳ", "currentAmount", type.bigDecimalType())
        .setStyle(numberStyle)
        .setTitleStyle(columnTitleStyle)
        .setWidth(80);

    // Build report
    JasperReportBuilder reportBuilder = report()
        .setPageFormat(PageType.A4, PageOrientation.PORTRAIT)
        .setPageMargin(margin(20))
        .title(createTitleComponent(report, titleStyle, subtitleStyle))
        .pageFooter(
            cmp.horizontalList(
                cmp.text("Ngày lập: " + (report.getGeneratedAt() != null
                    ? TIMESTAMP_FORMATTER.format(report.getGeneratedAt().atZone(DEFAULT_ZONE))
                    : "-")),
                cmp.pageXofY().setHorizontalTextAlignment(HorizontalTextAlignment.RIGHT)
            )
        );

    // Add columns based on comparison mode
    if (report.hasComparison()) {
      TextColumnBuilder<BigDecimal> priorAmountColumn = col.column("Số đầu năm", "priorAmount", type.bigDecimalType())
          .setStyle(numberStyle)
          .setTitleStyle(columnTitleStyle)
          .setWidth(80);

      TextColumnBuilder<BigDecimal> varianceColumn = col.column("Chênh lệch", "variance", type.bigDecimalType())
          .setStyle(numberStyle)
          .setTitleStyle(columnTitleStyle)
          .setWidth(80);

      reportBuilder.columns(lineCodeColumn, lineNameColumn, currentAmountColumn, priorAmountColumn, varianceColumn);
    } else {
      reportBuilder.columns(lineCodeColumn, lineNameColumn, currentAmountColumn);
    }

    // Set data source
    reportBuilder.setDataSource(createDataSource(report));

    // Export to PDF
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    reportBuilder.toPdf(outputStream);
    return outputStream.toByteArray();
  }

  /**
   * Create the title component for the PDF report header.
   */
  private ComponentBuilder<?, ?> createTitleComponent(StatutoryReportDTO report,
      StyleBuilder titleStyle, StyleBuilder subtitleStyle) {

    String reportTitle = report.getReportName() != null ? report.getReportName().toUpperCase() : "";
    String reportSubtitle = report.getReportNameEnglish() != null ? report.getReportNameEnglish() : "";

    return cmp.verticalList(
        // Company header
        cmp.text(report.getCompanyName())
            .setStyle(stl.style().bold().setFontSize(12).setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)),
        cmp.text("MST: " + (report.getCompanyTaxCode() != null ? report.getCompanyTaxCode() : ""))
            .setStyle(stl.style().setFontSize(10).setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)),
        cmp.text(report.getCompanyAddress() != null ? report.getCompanyAddress() : "")
            .setStyle(stl.style().setFontSize(10).setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)),
        cmp.verticalGap(10),
        // Report title
        cmp.text(reportTitle).setStyle(titleStyle),
        cmp.text(reportSubtitle).setStyle(subtitleStyle),
        cmp.verticalGap(5),
        // Period info
        cmp.text("Kỳ báo cáo: " + report.getPeriodName()
            + " (Từ " + formatDate(report.getPeriodStartDate())
            + " đến " + formatDate(report.getPeriodEndDate()) + ")")
            .setStyle(stl.style().setFontSize(10).setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)),
        // Draft warning if applicable
        report.isDraft()
            ? cmp.text("*** DỰ THẢO - Kỳ chưa đóng ***")
                .setStyle(stl.style().bold().setForegroundColor(Color.RED)
                    .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER))
            : cmp.verticalGap(0),
        cmp.verticalGap(15)
    );
  }

  /**
   * Create DynamicReports data source from report lines.
   */
  private JRDataSource createDataSource(StatutoryReportDTO report) {
    DRDataSource dataSource;

    if (report.hasComparison()) {
      dataSource = new DRDataSource("lineCode", "lineName", "currentAmount", "priorAmount", "variance");
      for (StatutoryReportLineDTO line : report.getLines()) {
        String indent = "  ".repeat(Math.max(0, (line.getLevel() != null ? line.getLevel() : 1) - 1));
        dataSource.add(
            line.getLineCode(),
            indent + line.getLineName(),
            line.getCurrentAmount(),
            line.getPriorAmount(),
            line.getVariance()
        );
      }
    } else {
      dataSource = new DRDataSource("lineCode", "lineName", "currentAmount");
      for (StatutoryReportLineDTO line : report.getLines()) {
        String indent = "  ".repeat(Math.max(0, (line.getLevel() != null ? line.getLevel() : 1) - 1));
        dataSource.add(
            line.getLineCode(),
            indent + line.getLineName(),
            line.getCurrentAmount()
        );
      }
    }

    return dataSource;
  }

  // ==================== Multi-Period Export Methods ====================

  /**
   * Export a multi-period comparison report to Excel format.
   * Generates dynamic columns based on selected periods with variance columns.
   *
   * @param report the multi-period report to export
   * @return Excel file as byte array
   */
  public byte[] exportMultiPeriodToExcel(MultiPeriodReportDTO report) {
    try (Workbook workbook = new XSSFWorkbook()) {
      String sheetName = SHEET_NAMES.getOrDefault(report.reportType(), report.reportType()) + " - Comparison";
      Sheet sheet = workbook.createSheet(sheetName);

      CellStyle headerStyle = createHeaderStyle(workbook);
      CellStyle titleStyle = createTitleStyle(workbook);
      CellStyle numberStyle = createNumberStyle(workbook);
      CellStyle level1Style = createLevel1Style(workbook);
      CellStyle level2Style = createLevel2Style(workbook);
      CellStyle highlightStyle = createHighlightStyle(workbook);
      CellStyle highlightNumberStyle = createHighlightNumberStyle(workbook);

      int rowNum = 0;

      rowNum = addMultiPeriodTitleSection(sheet, rowNum, report, titleStyle);
      rowNum = addMultiPeriodCompanyInfo(sheet, rowNum, report);
      rowNum++;

      rowNum = addMultiPeriodColumnHeaders(sheet, rowNum, report, headerStyle);
      rowNum = addMultiPeriodDataRows(sheet, rowNum, report, numberStyle, level1Style, level2Style,
          highlightStyle, highlightNumberStyle);

      int totalColumns = 2 + report.periods().size() + (report.periods().size() - 1) * 2;
      autoSizeColumns(sheet, totalColumns);

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      return outputStream.toByteArray();

    } catch (IOException e) {
      logger.error("Failed to export multi-period report to Excel: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to export multi-period report to Excel", e);
    }
  }

  /**
   * Export a multi-period comparison report to PDF format.
   * Uses landscape orientation for reports with 4+ columns.
   *
   * @param report the multi-period report to export
   * @return PDF file as byte array
   */
  public byte[] exportMultiPeriodToPdf(MultiPeriodReportDTO report) {
    try {
      return generateMultiPeriodPdf(report);
    } catch (Exception e) {
      logger.error("Failed to export multi-period report to PDF: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to export multi-period report to PDF", e);
    }
  }

  private int addMultiPeriodTitleSection(Sheet sheet, int rowNum, MultiPeriodReportDTO report, CellStyle titleStyle) {
    Row titleRow = sheet.createRow(rowNum++);
    Cell titleCell = titleRow.createCell(0);
    titleCell.setCellValue(report.reportName().toUpperCase() + " - SO SÁNH NHIỀU KỲ");
    titleCell.setCellStyle(titleStyle);
    int totalColumns = 2 + report.periods().size() * 2;
    sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, totalColumns - 1));

    Row subtitleRow = sheet.createRow(rowNum++);
    Cell subtitleCell = subtitleRow.createCell(0);
    subtitleCell.setCellValue("Multi-Period Comparison Report");
    sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, totalColumns - 1));

    rowNum++;
    return rowNum;
  }

  private int addMultiPeriodCompanyInfo(Sheet sheet, int rowNum, MultiPeriodReportDTO report) {
    Row companyRow = sheet.createRow(rowNum++);
    companyRow.createCell(0).setCellValue("Doanh nghiệp:");
    companyRow.createCell(1).setCellValue(report.companyName());

    Row periodsRow = sheet.createRow(rowNum++);
    periodsRow.createCell(0).setCellValue("Các kỳ so sánh:");
    StringBuilder periodsStr = new StringBuilder();
    for (int i = 0; i < report.periods().size(); i++) {
      if (i > 0) periodsStr.append(", ");
      periodsStr.append(report.periods().get(i).periodName());
    }
    periodsRow.createCell(1).setCellValue(periodsStr.toString());

    if (report.hasDraftPeriod()) {
      Row draftRow = sheet.createRow(rowNum++);
      draftRow.createCell(0).setCellValue("Lưu ý:");
      draftRow.createCell(1).setCellValue("Báo cáo bao gồm kỳ chưa đóng (DỰ THẢO)");
    }

    Row generatedRow = sheet.createRow(rowNum++);
    generatedRow.createCell(0).setCellValue("Ngày lập:");
    generatedRow.createCell(1).setCellValue(
        report.generatedAt() != null
            ? TIMESTAMP_FORMATTER.format(report.generatedAt().atZone(DEFAULT_ZONE))
            : "");

    return rowNum;
  }

  private int addMultiPeriodColumnHeaders(Sheet sheet, int rowNum, MultiPeriodReportDTO report, CellStyle headerStyle) {
    Row headerRow = sheet.createRow(rowNum++);

    int col = 0;
    createStyledCell(headerRow, col++, "Mã số", headerStyle);
    createStyledCell(headerRow, col++, "Chỉ tiêu", headerStyle);

    for (int i = 0; i < report.periods().size(); i++) {
      PeriodColumnDTO period = report.periods().get(i);
      String periodHeader = period.periodName();
      if (period.isDraft()) {
        periodHeader += " (*)";
      }
      createStyledCell(headerRow, col++, periodHeader, headerStyle);

      if (i > 0) {
        createStyledCell(headerRow, col++, "Chênh lệch", headerStyle);
        createStyledCell(headerRow, col++, "Chênh lệch %", headerStyle);
      }
    }

    return rowNum;
  }

  private int addMultiPeriodDataRows(Sheet sheet, int rowNum, MultiPeriodReportDTO report,
      CellStyle numberStyle, CellStyle level1Style, CellStyle level2Style,
      CellStyle highlightStyle, CellStyle highlightNumberStyle) {

    for (MultiPeriodLineDTO line : report.lines()) {
      Row row = sheet.createRow(rowNum++);

      CellStyle textStyle = line.level() == 1 ? level1Style : level2Style;
      CellStyle numStyle = line.isMaterial() ? highlightNumberStyle : numberStyle;
      CellStyle lineTextStyle = line.isMaterial() ? highlightStyle : textStyle;

      int col = 0;

      Cell codeCell = row.createCell(col++);
      codeCell.setCellValue(line.lineCode());
      codeCell.setCellStyle(lineTextStyle);

      Cell nameCell = row.createCell(col++);
      String indent = "  ".repeat(Math.max(0, line.level() - 1));
      String displayName = indent + line.lineName();
      if (line.isMaterial()) {
        displayName = "⚠ " + displayName;
      }
      nameCell.setCellValue(displayName);
      nameCell.setCellStyle(lineTextStyle);

      int varianceIndex = 0;
      for (int i = 0; i < report.periods().size(); i++) {
        PeriodColumnDTO period = report.periods().get(i);
        BigDecimal value = line.periodValues().get(period.periodId());

        Cell valueCell = row.createCell(col++);
        if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
          valueCell.setCellValue(value.doubleValue());
          valueCell.setCellStyle(numStyle);
        }

        if (i > 0 && line.variances() != null && varianceIndex < line.variances().size()) {
          VarianceDTO variance = line.variances().get(varianceIndex++);
          
          Cell absoluteVarianceCell = row.createCell(col++);
          if (variance.absoluteVariance() != null && variance.absoluteVariance().compareTo(BigDecimal.ZERO) != 0) {
            absoluteVarianceCell.setCellValue(variance.absoluteVariance().doubleValue());
            absoluteVarianceCell.setCellStyle(numStyle);
          }

          Cell varianceCell = row.createCell(col++);

          if (variance.percentVariance() != null) {
            if (variance.percentVariance().isInfinite()) {
              varianceCell.setCellValue("∞");
            } else {
              varianceCell.setCellValue(String.format("%.1f%%", variance.percentVariance()));
            }
          } else {
            varianceCell.setCellValue("N/A");
          }
          varianceCell.setCellStyle(numStyle);
        }
      }
    }

    return rowNum;
  }

  private CellStyle createHighlightStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    font.setColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_RED.getIndex());
    style.setFont(font);
    style.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_YELLOW.getIndex());
    style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
    return style;
  }

  private CellStyle createHighlightNumberStyle(Workbook workbook) {
    CellStyle style = createHighlightStyle(workbook);
    style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0"));
    style.setAlignment(HorizontalAlignment.RIGHT);
    return style;
  }

  private byte[] generateMultiPeriodPdf(MultiPeriodReportDTO report) throws Exception {
    boolean useLandscape = report.periods().size() >= 3;

    StyleBuilder boldStyle = stl.style().bold();
    StyleBuilder boldCenteredStyle = stl.style(boldStyle)
        .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER);
    StyleBuilder titleStyle = stl.style(boldCenteredStyle)
        .setFontSize(14)
        .setVerticalTextAlignment(VerticalTextAlignment.MIDDLE);
    StyleBuilder columnTitleStyle = stl.style(boldStyle)
        .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)
        .setBackgroundColor(new Color(240, 240, 240))
        .setBorder(stl.pen1Point())
        .setPadding(3);
    StyleBuilder columnStyle = stl.style()
        .setBorder(stl.pen1Point())
        .setPadding(2);
    StyleBuilder numberStyle = stl.style(columnStyle)
        .setHorizontalTextAlignment(HorizontalTextAlignment.RIGHT)
        .setPattern("#,##0");
    StyleBuilder highlightStyle = stl.style(columnStyle)
        .bold()
        .setForegroundColor(new Color(139, 0, 0))
        .setBackgroundColor(new Color(255, 255, 200));

    String userIdentity = getUserIdentityForFooter();
    
    JasperReportBuilder reportBuilder = report()
        .setPageFormat(PageType.A4, useLandscape ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT)
        .setPageMargin(margin(15))
        .title(createMultiPeriodTitleComponent(report, titleStyle))
        .pageFooter(
            cmp.horizontalList(
                cmp.text("Ngày lập: " + (report.generatedAt() != null
                    ? TIMESTAMP_FORMATTER.format(report.generatedAt().atZone(DEFAULT_ZONE))
                    : "-") + " | Người lập: " + userIdentity),
                cmp.pageXofY().setHorizontalTextAlignment(HorizontalTextAlignment.RIGHT)
            )
        );

    TextColumnBuilder<String> lineCodeColumn = col.column("Mã số", "lineCode", type.stringType())
        .setStyle(columnStyle)
        .setTitleStyle(columnTitleStyle)
        .setWidth(30);

    TextColumnBuilder<String> lineNameColumn = col.column("Chỉ tiêu", "lineName", type.stringType())
        .setStyle(columnStyle)
        .setTitleStyle(columnTitleStyle)
        .setWidth(useLandscape ? 150 : 120);

    reportBuilder.columns(lineCodeColumn, lineNameColumn);

    for (int i = 0; i < report.periods().size(); i++) {
      PeriodColumnDTO period = report.periods().get(i);
      String periodName = period.periodName();
      if (period.isDraft()) {
        periodName += " (*)";
      }

      TextColumnBuilder<BigDecimal> periodColumn = col.column(periodName, "period" + i, type.bigDecimalType())
          .setStyle(numberStyle)
          .setTitleStyle(columnTitleStyle)
          .setWidth(useLandscape ? 60 : 50);
      reportBuilder.addColumn(periodColumn);

      if (i > 0) {
        TextColumnBuilder<BigDecimal> absVarianceColumn = col.column("Δ", "absVariance" + (i - 1), type.bigDecimalType())
            .setStyle(numberStyle)
            .setTitleStyle(columnTitleStyle)
            .setWidth(45);
        reportBuilder.addColumn(absVarianceColumn);
        
        TextColumnBuilder<String> varianceColumn = col.column("Δ%", "variance" + (i - 1), type.stringType())
            .setStyle(columnStyle)
            .setTitleStyle(columnTitleStyle)
            .setWidth(35);
        reportBuilder.addColumn(varianceColumn);
      }
    }

    reportBuilder.setDataSource(createMultiPeriodDataSource(report));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    reportBuilder.toPdf(outputStream);
    return outputStream.toByteArray();
  }

  private ComponentBuilder<?, ?> createMultiPeriodTitleComponent(MultiPeriodReportDTO report, StyleBuilder titleStyle) {
    StringBuilder periodsStr = new StringBuilder();
    for (int i = 0; i < report.periods().size(); i++) {
      if (i > 0) periodsStr.append(" | ");
      periodsStr.append(report.periods().get(i).periodName());
    }

    return cmp.verticalList(
        cmp.text(report.companyName())
            .setStyle(stl.style().bold().setFontSize(11).setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)),
        cmp.verticalGap(8),
        cmp.text(report.reportName().toUpperCase() + " - SO SÁNH NHIỀU KỲ").setStyle(titleStyle),
        cmp.text("Multi-Period Comparison Report")
            .setStyle(stl.style().setFontSize(9).setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)),
        cmp.verticalGap(5),
        cmp.text("Các kỳ: " + periodsStr)
            .setStyle(stl.style().setFontSize(9).setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)),
        report.hasDraftPeriod()
            ? cmp.text("*** Bao gồm kỳ DỰ THẢO ***")
                .setStyle(stl.style().bold().setForegroundColor(Color.RED)
                    .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER))
            : cmp.verticalGap(0),
        cmp.verticalGap(10)
    );
  }

  private JRDataSource createMultiPeriodDataSource(MultiPeriodReportDTO report) {
    int periodCount = report.periods().size();
    int varianceCount = Math.max(0, periodCount - 1);

    String[] columnNames = new String[2 + periodCount + varianceCount * 2];
    columnNames[0] = "lineCode";
    columnNames[1] = "lineName";

    for (int i = 0; i < periodCount; i++) {
      columnNames[2 + i] = "period" + i;
    }
    for (int i = 0; i < varianceCount; i++) {
      columnNames[2 + periodCount + i * 2] = "absVariance" + i;
      columnNames[2 + periodCount + i * 2 + 1] = "variance" + i;
    }

    DRDataSource dataSource = new DRDataSource(columnNames);

    for (MultiPeriodLineDTO line : report.lines()) {
      Object[] rowData = new Object[columnNames.length];

      String indent = "  ".repeat(Math.max(0, line.level() - 1));
      String displayName = indent + line.lineName();
      if (line.isMaterial()) {
        displayName = "⚠ " + displayName;
      }

      rowData[0] = line.lineCode();
      rowData[1] = displayName;

      for (int i = 0; i < periodCount; i++) {
        PeriodColumnDTO period = report.periods().get(i);
        rowData[2 + i] = line.periodValues().get(period.periodId());
      }

      for (int i = 0; i < varianceCount && i < (line.variances() != null ? line.variances().size() : 0); i++) {
        VarianceDTO variance = line.variances().get(i);
        rowData[2 + periodCount + i * 2] = variance.absoluteVariance();
        if (variance.percentVariance() != null) {
          if (variance.percentVariance().isInfinite()) {
            rowData[2 + periodCount + i * 2 + 1] = "∞";
          } else {
            rowData[2 + periodCount + i * 2 + 1] = String.format("%.1f%%", variance.percentVariance());
          }
        } else {
          rowData[2 + periodCount + i * 2 + 1] = "N/A";
        }
      }

      dataSource.add(rowData);
    }

    return dataSource;
  }

  private String getUserIdentityForFooter() {
    try {
      String email = SecurityUtils.getCurrentUserEmail();
      if (email != null && !email.isEmpty()) {
        return email;
      }
      Long userId = SecurityUtils.getCurrentUserId();
      return "User ID: " + userId;
    } catch (Exception e) {
      logger.debug("Could not get user identity for PDF footer: {}", e.getMessage());
      return "N/A";
    }
  }
}
