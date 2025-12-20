package com.accounting.service.analytics.impl;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

import com.accounting.dto.analytics.ExportMetadata;
import com.accounting.dto.analytics.ExportResponse;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.SecurityUtils;
import com.accounting.service.analytics.AnalyticsExportService;
import com.accounting.service.analytics.AnalyticsWidgetService;
import com.accounting.service.analytics.AnalyticsWidgetService.ARAPBalanceData;
import com.accounting.service.analytics.AnalyticsWidgetService.CashPositionData;
import com.accounting.service.analytics.AnalyticsWidgetService.DailyRevenueExpense;
import com.accounting.service.analytics.AnalyticsWidgetService.PeriodSummaryData;
import com.accounting.service.analytics.AnalyticsWidgetService.RevenueExpenseData;
import com.accounting.service.analytics.AnalyticsWidgetService.TopDebtorCreditor;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.Columns;
import net.sf.dynamicreports.report.builder.component.Components;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.constant.HorizontalTextAlignment;
import net.sf.dynamicreports.report.constant.PageOrientation;
import net.sf.dynamicreports.report.constant.PageType;
import net.sf.dynamicreports.report.datasource.DRDataSource;
import net.sf.jasperreports.engine.JRDataSource;

@Service
public class AnalyticsExportServiceImpl implements AnalyticsExportService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsExportServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AnalyticsWidgetService widgetService;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final Map<String, ExportJobInfo> exportJobs = new ConcurrentHashMap<>();

    public AnalyticsExportServiceImpl(
            AnalyticsWidgetService widgetService,
            CompanyRepository companyRepository,
            UserRepository userRepository) {
        this.widgetService = widgetService;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @Override
    public byte[] exportDashboardToExcel(Long companyId, Long periodId, String dashboardType) {
        Company company = getCompany(companyId);
        String username = getCurrentUsername();
        String watermark = buildWatermark(company.getName(), username);

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            DashboardType type = DashboardType.valueOf(dashboardType);
            int rowCount = 0;

            switch (type) {
                case FINANCIAL_OVERVIEW -> rowCount = createFinancialOverviewSheet(workbook, company, watermark);
                case AR_AP_AGING -> rowCount = createARAPAgingSheet(workbook, company, watermark);
                case CASH_FLOW -> rowCount = createCashFlowSheet(workbook, company, watermark);
                case PERIOD_SUMMARY -> rowCount = createPeriodSummarySheet(workbook, company, periodId, watermark);
            }

            Sheet metadataSheet = workbook.createSheet("Export Metadata");
            createMetadataSheet(metadataSheet, companyId, periodId, dashboardType, "EXCEL", rowCount, username);

            workbook.write(outputStream);
            log.info(
                    "Excel export completed for company {} dashboard {} with {} rows",
                    companyId,
                    dashboardType,
                    rowCount);
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Failed to export dashboard to Excel: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Excel export", e);
        }
    }

    @Override
    public byte[] exportDashboardToPdf(Long companyId, Long periodId, String dashboardType) {
        Company company = getCompany(companyId);
        String username = getCurrentUsername();
        String watermark = buildWatermark(company.getName(), username);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            DashboardType type = DashboardType.valueOf(dashboardType);

            JasperReportBuilder report =
                    switch (type) {
                        case FINANCIAL_OVERVIEW -> buildFinancialOverviewPdfReport(company, watermark);
                        case AR_AP_AGING -> buildARAPAgingPdfReport(company, watermark);
                        case CASH_FLOW -> buildCashFlowPdfReport(company, watermark);
                        case PERIOD_SUMMARY -> buildPeriodSummaryPdfReport(company, periodId, watermark);
                    };

            report.toPdf(outputStream);
            log.info("PDF export completed for company {} dashboard {}", companyId, dashboardType);
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Failed to export dashboard to PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF export", e);
        }
    }

    @Override
    public ExportResponse getExportStatus(String jobId) {
        ExportJobInfo jobInfo = exportJobs.get(jobId);
        if (jobInfo == null) {
            return null;
        }
        return new ExportResponse(
                jobId, jobInfo.status, jobInfo.downloadUrl, jobInfo.expiresAt, jobInfo.metadata);
    }

    private Company getCompany(Long companyId) {
        return companyRepository
                .findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));
    }

    private String getCurrentUsername() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userRepository.findById(userId).map(User::getFullName).orElse("Unknown");
    }

    private String buildWatermark(String companyName, String username) {
        return String.format(
                "CONFIDENTIAL - %s - Generated: %s by %s",
                companyName, LocalDateTime.now().format(DATE_TIME_FORMATTER), username);
    }

    private int createFinancialOverviewSheet(Workbook workbook, Company company, String watermark) {
        Sheet sheet = workbook.createSheet("Financial Overview");
        setSheetHeader(sheet, watermark);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(1);
        RevenueExpenseData data = widgetService.getRevenueVsExpenses(startDate, endDate);

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Financial Overview - " + company.getName());
        titleCell.setCellStyle(headerStyle);

        rowNum++;

        Row summaryHeaderRow = sheet.createRow(rowNum++);
        createCell(summaryHeaderRow, 0, "Metric", headerStyle);
        createCell(summaryHeaderRow, 1, "Amount (VND)", headerStyle);

        createCell(sheet.createRow(rowNum), 0, "Total Revenue", dataStyle);
        createCell(sheet.createRow(rowNum++), 1, formatCurrency(data.totalRevenue()), dataStyle);

        createCell(sheet.createRow(rowNum), 0, "Total Expenses", dataStyle);
        createCell(sheet.createRow(rowNum++), 1, formatCurrency(data.totalExpenses()), dataStyle);

        createCell(sheet.createRow(rowNum), 0, "Net Income", dataStyle);
        createCell(sheet.createRow(rowNum++), 1, formatCurrency(data.netIncome()), dataStyle);

        rowNum++;

        Row detailHeaderRow = sheet.createRow(rowNum++);
        createCell(detailHeaderRow, 0, "Date", headerStyle);
        createCell(detailHeaderRow, 1, "Revenue", headerStyle);
        createCell(detailHeaderRow, 2, "Expenses", headerStyle);

        for (DailyRevenueExpense dailyData : data.dailyData()) {
            Row dataRow = sheet.createRow(rowNum++);
            createCell(dataRow, 0, dailyData.date().toString(), dataStyle);
            createCell(dataRow, 1, formatCurrency(dailyData.revenue()), dataStyle);
            createCell(dataRow, 2, formatCurrency(dailyData.expenses()), dataStyle);
        }

        autoSizeColumns(sheet, 3);
        return data.dailyData().size() + 3;
    }

    private int createARAPAgingSheet(Workbook workbook, Company company, String watermark) {
        Sheet sheet = workbook.createSheet("AR AP Aging");
        setSheetHeader(sheet, watermark);

        ARAPBalanceData data = widgetService.getARAPBalances(LocalDate.now());

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        createCell(titleRow, 0, "AR/AP Aging Report - " + company.getName(), headerStyle);
        rowNum++;

        Row summaryHeaderRow = sheet.createRow(rowNum++);
        createCell(summaryHeaderRow, 0, "Category", headerStyle);
        createCell(summaryHeaderRow, 1, "Amount (VND)", headerStyle);

        createDataRow(sheet, rowNum++, "Total AR", data.totalAR(), dataStyle);
        createDataRow(sheet, rowNum++, "Total AP", data.totalAP(), dataStyle);
        createDataRow(sheet, rowNum++, "Net Position", data.netPosition(), dataStyle);

        rowNum++;
        createCell(sheet.createRow(rowNum++), 0, "AR Aging Breakdown", headerStyle);

        Row arHeaderRow = sheet.createRow(rowNum++);
        createCell(arHeaderRow, 0, "Bucket", headerStyle);
        createCell(arHeaderRow, 1, "Amount (VND)", headerStyle);

        for (Map.Entry<String, BigDecimal> entry : data.arByAgingBucket().entrySet()) {
            createDataRow(sheet, rowNum++, entry.getKey(), entry.getValue(), dataStyle);
        }

        rowNum++;
        createCell(sheet.createRow(rowNum++), 0, "AP Aging Breakdown", headerStyle);

        Row apHeaderRow = sheet.createRow(rowNum++);
        createCell(apHeaderRow, 0, "Bucket", headerStyle);
        createCell(apHeaderRow, 1, "Amount (VND)", headerStyle);

        for (Map.Entry<String, BigDecimal> entry : data.apByAgingBucket().entrySet()) {
            createDataRow(sheet, rowNum++, entry.getKey(), entry.getValue(), dataStyle);
        }

        rowNum++;
        createCell(sheet.createRow(rowNum++), 0, "Top 5 Debtors", headerStyle);

        Row debtorHeaderRow = sheet.createRow(rowNum++);
        createCell(debtorHeaderRow, 0, "Name", headerStyle);
        createCell(debtorHeaderRow, 1, "Code", headerStyle);
        createCell(debtorHeaderRow, 2, "Balance (VND)", headerStyle);
        createCell(debtorHeaderRow, 3, "Rank", headerStyle);

        List<TopDebtorCreditor> debtors = widgetService.getTop5Debtors(LocalDate.now());
        for (TopDebtorCreditor debtor : debtors) {
            Row row = sheet.createRow(rowNum++);
            createCell(row, 0, debtor.partnerName(), dataStyle);
            createCell(row, 1, debtor.partnerCode(), dataStyle);
            createCell(row, 2, formatCurrency(debtor.balance()), dataStyle);
            createCell(row, 3, String.valueOf(debtor.rank()), dataStyle);
        }

        rowNum++;
        createCell(sheet.createRow(rowNum++), 0, "Top 5 Creditors", headerStyle);

        Row creditorHeaderRow = sheet.createRow(rowNum++);
        createCell(creditorHeaderRow, 0, "Name", headerStyle);
        createCell(creditorHeaderRow, 1, "Code", headerStyle);
        createCell(creditorHeaderRow, 2, "Balance (VND)", headerStyle);
        createCell(creditorHeaderRow, 3, "Rank", headerStyle);

        List<TopDebtorCreditor> creditors = widgetService.getTop5Creditors(LocalDate.now());
        for (TopDebtorCreditor creditor : creditors) {
            Row row = sheet.createRow(rowNum++);
            createCell(row, 0, creditor.partnerName(), dataStyle);
            createCell(row, 1, creditor.partnerCode(), dataStyle);
            createCell(row, 2, formatCurrency(creditor.balance()), dataStyle);
            createCell(row, 3, String.valueOf(creditor.rank()), dataStyle);
        }

        autoSizeColumns(sheet, 4);
        return rowNum;
    }

    private int createCashFlowSheet(Workbook workbook, Company company, String watermark) {
        Sheet sheet = workbook.createSheet("Cash Flow");
        setSheetHeader(sheet, watermark);

        CashPositionData data = widgetService.getCashPosition(LocalDate.now());

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        createCell(titleRow, 0, "Cash Flow Report - " + company.getName(), headerStyle);
        rowNum++;

        Row headerRow = sheet.createRow(rowNum++);
        createCell(headerRow, 0, "Category", headerStyle);
        createCell(headerRow, 1, "Amount (VND)", headerStyle);

        createDataRow(sheet, rowNum++, "Total Cash", data.totalCash(), dataStyle);
        createDataRow(sheet, rowNum++, "Cash in Bank", data.cashInBank(), dataStyle);
        createDataRow(sheet, rowNum++, "Cash on Hand", data.cashOnHand(), dataStyle);
        createDataRow(sheet, rowNum++, "Net Cash Flow", data.netCashFlow(), dataStyle);

        autoSizeColumns(sheet, 2);
        return 4;
    }

    private int createPeriodSummarySheet(Workbook workbook, Company company, Long periodId, String watermark) {
        Sheet sheet = workbook.createSheet("Period Summary");
        setSheetHeader(sheet, watermark);

        PeriodSummaryData data = widgetService.getPeriodSummary(periodId);

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        createCell(titleRow, 0, "Period Summary - " + company.getName(), headerStyle);
        rowNum++;

        createDataRow(sheet, rowNum++, "Period ID", String.valueOf(data.periodId()), dataStyle);
        createDataRow(sheet, rowNum++, "Start Date", data.periodStart().toString(), dataStyle);
        createDataRow(sheet, rowNum++, "End Date", data.periodEnd().toString(), dataStyle);
        createDataRow(sheet, rowNum++, "Total Revenue", formatCurrency(data.totalRevenue()), dataStyle);
        createDataRow(sheet, rowNum++, "Total Expense", formatCurrency(data.totalExpense()), dataStyle);
        createDataRow(sheet, rowNum++, "AR Balance", formatCurrency(data.arBalance()), dataStyle);
        createDataRow(sheet, rowNum++, "AP Balance", formatCurrency(data.apBalance()), dataStyle);
        createDataRow(sheet, rowNum++, "Cash Balance", formatCurrency(data.cashBalance()), dataStyle);
        createDataRow(sheet, rowNum++, "Voucher Count", String.valueOf(data.voucherCount()), dataStyle);

        autoSizeColumns(sheet, 2);
        return 9;
    }

    private void createMetadataSheet(
            Sheet sheet, Long companyId, Long periodId, String dashboardType, String format, int rowCount,
            String generatedBy) {
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());

        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        createCell(titleRow, 0, "Export Metadata", headerStyle);
        rowNum++;

        createDataRow(sheet, rowNum++, "Company ID", companyId.toString(), dataStyle);
        createDataRow(sheet, rowNum++, "Period ID", periodId != null ? periodId.toString() : "N/A", dataStyle);
        createDataRow(sheet, rowNum++, "Dashboard Type", dashboardType, dataStyle);
        createDataRow(sheet, rowNum++, "Export Format", format, dataStyle);
        createDataRow(sheet, rowNum++, "Row Count", String.valueOf(rowCount), dataStyle);
        createDataRow(
                sheet, rowNum++, "Generated At", LocalDateTime.now().format(DATE_TIME_FORMATTER), dataStyle);
        createDataRow(sheet, rowNum++, "Generated By", generatedBy, dataStyle);

        autoSizeColumns(sheet, 2);
    }

    private JasperReportBuilder buildFinancialOverviewPdfReport(Company company, String watermark) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(1);
        RevenueExpenseData data = widgetService.getRevenueVsExpenses(startDate, endDate);

        StyleBuilder titleStyle = DynamicReports.stl.style().bold().setFontSize(16);
        StyleBuilder columnHeaderStyle =
                DynamicReports.stl.style().bold().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER);

        DRDataSource dataSource = new DRDataSource("date", "revenue", "expenses");
        for (DailyRevenueExpense dailyData : data.dailyData()) {
            dataSource.add(dailyData.date().toString(), dailyData.revenue(), dailyData.expenses());
        }

        return DynamicReports.report()
                .setPageFormat(PageType.A4, PageOrientation.PORTRAIT)
                .title(
                        Components.text("Financial Overview - " + company.getName()).setStyle(titleStyle),
                        Components.text("Total Revenue: " + formatCurrency(data.totalRevenue())),
                        Components.text("Total Expenses: " + formatCurrency(data.totalExpenses())),
                        Components.text("Net Income: " + formatCurrency(data.netIncome())),
                        Components.verticalGap(20))
                .columns(
                        Columns.column("Date", "date", DynamicReports.type.stringType()),
                        Columns.column("Revenue", "revenue", DynamicReports.type.bigDecimalType()),
                        Columns.column("Expenses", "expenses", DynamicReports.type.bigDecimalType()))
                .setColumnTitleStyle(columnHeaderStyle)
                .pageHeader(Components.text(watermark).setStyle(DynamicReports.stl.style().setFontSize(8)))
                .pageFooter(Components.pageXofY())
                .setDataSource(dataSource);
    }

    private JasperReportBuilder buildARAPAgingPdfReport(Company company, String watermark) {
        ARAPBalanceData data = widgetService.getARAPBalances(LocalDate.now());

        StyleBuilder titleStyle = DynamicReports.stl.style().bold().setFontSize(16);

        DRDataSource dataSource = new DRDataSource("category", "amount");
        dataSource.add("Total AR", data.totalAR());
        dataSource.add("Total AP", data.totalAP());
        dataSource.add("Net Position", data.netPosition());
        for (Map.Entry<String, BigDecimal> entry : data.arByAgingBucket().entrySet()) {
            dataSource.add("AR - " + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, BigDecimal> entry : data.apByAgingBucket().entrySet()) {
            dataSource.add("AP - " + entry.getKey(), entry.getValue());
        }

        return DynamicReports.report()
                .setPageFormat(PageType.A4, PageOrientation.PORTRAIT)
                .title(
                        Components.text("AR/AP Aging Report - " + company.getName()).setStyle(titleStyle),
                        Components.verticalGap(20))
                .columns(
                        Columns.column("Category", "category", DynamicReports.type.stringType()),
                        Columns.column("Amount (VND)", "amount", DynamicReports.type.bigDecimalType()))
                .pageHeader(Components.text(watermark).setStyle(DynamicReports.stl.style().setFontSize(8)))
                .pageFooter(Components.pageXofY())
                .setDataSource(dataSource);
    }

    private JasperReportBuilder buildCashFlowPdfReport(Company company, String watermark) {
        CashPositionData data = widgetService.getCashPosition(LocalDate.now());

        StyleBuilder titleStyle = DynamicReports.stl.style().bold().setFontSize(16);

        DRDataSource dataSource = new DRDataSource("category", "amount");
        dataSource.add("Total Cash", data.totalCash());
        dataSource.add("Cash in Bank", data.cashInBank());
        dataSource.add("Cash on Hand", data.cashOnHand());
        dataSource.add("Net Cash Flow", data.netCashFlow());

        return DynamicReports.report()
                .setPageFormat(PageType.A4, PageOrientation.PORTRAIT)
                .title(
                        Components.text("Cash Flow Report - " + company.getName()).setStyle(titleStyle),
                        Components.verticalGap(20))
                .columns(
                        Columns.column("Category", "category", DynamicReports.type.stringType()),
                        Columns.column("Amount (VND)", "amount", DynamicReports.type.bigDecimalType()))
                .pageHeader(Components.text(watermark).setStyle(DynamicReports.stl.style().setFontSize(8)))
                .pageFooter(Components.pageXofY())
                .setDataSource(dataSource);
    }

    private JasperReportBuilder buildPeriodSummaryPdfReport(Company company, Long periodId, String watermark) {
        PeriodSummaryData data = widgetService.getPeriodSummary(periodId);

        StyleBuilder titleStyle = DynamicReports.stl.style().bold().setFontSize(16);

        DRDataSource dataSource = new DRDataSource("field", "value");
        dataSource.add("Period ID", String.valueOf(data.periodId()));
        dataSource.add("Start Date", data.periodStart().toString());
        dataSource.add("End Date", data.periodEnd().toString());
        dataSource.add("Total Revenue", formatCurrency(data.totalRevenue()));
        dataSource.add("Total Expense", formatCurrency(data.totalExpense()));
        dataSource.add("AR Balance", formatCurrency(data.arBalance()));
        dataSource.add("AP Balance", formatCurrency(data.apBalance()));
        dataSource.add("Cash Balance", formatCurrency(data.cashBalance()));
        dataSource.add("Voucher Count", String.valueOf(data.voucherCount()));

        return DynamicReports.report()
                .setPageFormat(PageType.A4, PageOrientation.PORTRAIT)
                .title(
                        Components.text("Period Summary - " + company.getName()).setStyle(titleStyle),
                        Components.verticalGap(20))
                .columns(
                        Columns.column("Field", "field", DynamicReports.type.stringType()),
                        Columns.column("Value", "value", DynamicReports.type.stringType()))
                .pageHeader(Components.text(watermark).setStyle(DynamicReports.stl.style().setFontSize(8)))
                .pageFooter(Components.pageXofY())
                .setDataSource((JRDataSource) dataSource);
    }

    private void setSheetHeader(Sheet sheet, String watermark) {
        Header header = sheet.getHeader();
        header.setCenter(watermark);
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

    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createDataRow(Sheet sheet, int rowNum, String label, String value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        createCell(row, 0, label, style);
        createCell(row, 1, value, style);
    }

    private void createDataRow(Sheet sheet, int rowNum, String label, BigDecimal value, CellStyle style) {
        createDataRow(sheet, rowNum, label, formatCurrency(value), style);
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

    private record ExportJobInfo(
            String status,
            String downloadUrl,
            LocalDateTime expiresAt,
            ExportMetadata metadata) {}
}
