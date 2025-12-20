package com.accounting.service.analytics.impl;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Header;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.analytics.DashboardReconciliationReport;
import com.accounting.dto.analytics.ReconciliationReportDTO;
import com.accounting.dto.analytics.ReconciliationReportDTO.ReconciliationLineDTO;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.SecurityUtils;
import com.accounting.service.analytics.DashboardReconciliationService;
import com.accounting.service.analytics.ReconciliationReportService;

@Service
public class ReconciliationReportServiceImpl implements ReconciliationReportService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationReportServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DashboardReconciliationService reconciliationService;
    private final CompanyRepository companyRepository;
    private final AccountingPeriodRepository periodRepository;
    private final UserRepository userRepository;

    public ReconciliationReportServiceImpl(
            DashboardReconciliationService reconciliationService,
            CompanyRepository companyRepository,
            AccountingPeriodRepository periodRepository,
            UserRepository userRepository) {
        this.reconciliationService = reconciliationService;
        this.companyRepository = companyRepository;
        this.periodRepository = periodRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationReportDTO generateReport(Long companyId, UUID periodId) {
        log.info("Generating reconciliation report for company {} period {}", companyId, periodId);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));

        AccountingPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));

        DashboardReconciliationReport reconciliation =
                reconciliationService.runFullReconciliation(companyId, periodId);

        return ReconciliationReportDTO.from(reconciliation, company.getName(), period.getPeriodName());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportToExcel(Long companyId, UUID periodId) {
        log.info("Exporting reconciliation report to Excel for company {} period {}", companyId, periodId);

        ReconciliationReportDTO report = generateReport(companyId, periodId);
        String username = getCurrentUsername();
        String watermark = buildWatermark(report.companyName(), username);

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            createReconciliationSheet(workbook, report, watermark);
            createMetadataSheet(workbook, report, username);

            workbook.write(outputStream);

            log.info("Excel export completed for reconciliation report: company={}, period={}, lines={}",
                    companyId, periodId, report.lines().size());

            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Failed to export reconciliation report to Excel: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Excel export", e);
        }
    }

    private String getCurrentUsername() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("Unknown");
    }

    private String buildWatermark(String companyName, String username) {
        return String.format(
                "CONFIDENTIAL - %s - Generated: %s by %s",
                companyName,
                LocalDateTime.now().format(DATE_TIME_FORMATTER),
                username);
    }

    private void createReconciliationSheet(Workbook workbook, ReconciliationReportDTO report, String watermark) {
        Sheet sheet = workbook.createSheet("Reconciliation Report");
        setSheetHeader(sheet, watermark);

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        CellStyle passedStyle = createStatusStyle(workbook, IndexedColors.LIGHT_GREEN);
        CellStyle failedStyle = createStatusStyle(workbook, IndexedColors.CORAL);

        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Monthly Reconciliation Report - " + report.companyName());
        CellStyle titleStyle = createTitleStyle(workbook);
        titleCell.setCellStyle(titleStyle);

        rowNum++;

        Row periodRow = sheet.createRow(rowNum++);
        createCell(periodRow, 0, "Period:", headerStyle);
        createCell(periodRow, 1, report.periodName(), dataStyle);

        Row generatedRow = sheet.createRow(rowNum++);
        createCell(generatedRow, 0, "Generated At:", headerStyle);
        createCell(generatedRow, 1, report.generatedAt().format(DATE_TIME_FORMATTER), dataStyle);

        Row statusRow = sheet.createRow(rowNum++);
        createCell(statusRow, 0, "Overall Status:", headerStyle);
        Cell statusCell = statusRow.createCell(1);
        statusCell.setCellValue(report.overallStatus());
        statusCell.setCellStyle("PASSED".equals(report.overallStatus()) ? passedStyle : failedStyle);

        rowNum++;

        Row tableHeaderRow = sheet.createRow(rowNum++);
        createCell(tableHeaderRow, 0, "Category", headerStyle);
        createCell(tableHeaderRow, 1, "MV Total (VND)", headerStyle);
        createCell(tableHeaderRow, 2, "GL Total (VND)", headerStyle);
        createCell(tableHeaderRow, 3, "Variance (VND)", headerStyle);
        createCell(tableHeaderRow, 4, "Status", headerStyle);

        for (ReconciliationLineDTO line : report.lines()) {
            Row dataRow = sheet.createRow(rowNum++);
            createCell(dataRow, 0, line.category(), dataStyle);
            createCell(dataRow, 1, formatCurrency(line.mvTotal()), dataStyle);
            createCell(dataRow, 2, formatCurrency(line.glTotal()), dataStyle);
            createCell(dataRow, 3, formatCurrency(line.variance()), dataStyle);

            Cell lineStatusCell = dataRow.createCell(4);
            lineStatusCell.setCellValue(line.status());
            lineStatusCell.setCellStyle("PASSED".equals(line.status()) ? passedStyle : failedStyle);
        }

        autoSizeColumns(sheet, 5);
    }

    private void createMetadataSheet(Workbook workbook, ReconciliationReportDTO report, String generatedBy) {
        Sheet sheet = workbook.createSheet("Export Metadata");

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        createCell(titleRow, 0, "Export Metadata", headerStyle);
        rowNum++;

        createDataRow(sheet, rowNum++, "Company ID", report.companyId().toString(), dataStyle);
        createDataRow(sheet, rowNum++, "Company Name", report.companyName(), dataStyle);
        createDataRow(sheet, rowNum++, "Period ID", report.periodId().toString(), dataStyle);
        createDataRow(sheet, rowNum++, "Period Name", report.periodName(), dataStyle);
        createDataRow(sheet, rowNum++, "Report Type", "RECONCILIATION", dataStyle);
        createDataRow(sheet, rowNum++, "Export Format", "EXCEL", dataStyle);
        createDataRow(sheet, rowNum++, "Overall Status", report.overallStatus(), dataStyle);
        createDataRow(sheet, rowNum++, "Line Count", String.valueOf(report.lines().size()), dataStyle);
        createDataRow(sheet, rowNum++, "Generated At", LocalDateTime.now().format(DATE_TIME_FORMATTER), dataStyle);
        createDataRow(sheet, rowNum++, "Generated By", generatedBy, dataStyle);

        autoSizeColumns(sheet, 2);
    }

    private void setSheetHeader(Sheet sheet, String watermark) {
        Header header = sheet.getHeader();
        header.setCenter(watermark);
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createStatusStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createDataRow(Sheet sheet, int rowNum, String label, String value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
        createCell(row, 0, label, headerStyle);
        createCell(row, 1, value, style);
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String formatCurrency(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return String.format("%,.0f", value);
    }
}
