package com.accounting.service.impl.report;

import static net.sf.dynamicreports.report.builder.DynamicReports.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.accounting.dto.TrialBalanceDTO;
import com.accounting.dto.TrialBalanceResponseDTO;
import com.accounting.service.report.TrialBalancePdfExportService;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.component.ComponentBuilder;
import net.sf.dynamicreports.report.builder.component.VerticalListBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.constant.HorizontalTextAlignment;
import net.sf.dynamicreports.report.constant.PageOrientation;
import net.sf.dynamicreports.report.constant.PageType;
import net.sf.dynamicreports.report.constant.VerticalTextAlignment;
import net.sf.dynamicreports.report.datasource.DRDataSource;
import net.sf.jasperreports.engine.JRDataSource;

/**
 * Implementation of Trial Balance PDF export service using DynamicReports.
 * Generates TT200-compliant S06-DN (Bảng cân đối số phát sinh) reports.
 *
 * <p>Layout follows Vietnamese accounting standard TT200 with:
 * <ul>
 *   <li>Company header with name</li>
 *   <li>Report title: BẢNG CÂN ĐỐI SỐ PHÁT SINH</li>
 *   <li>Period info with date range</li>
 *   <li>8 columns: Account code, name, opening debit/credit, period debit/credit, closing debit/credit</li>
 *   <li>Totals row</li>
 *   <li>DRAFT watermark for open periods</li>
 *   <li>Footer with generation timestamp and signature blocks</li>
 * </ul>
 */
@Service
public class TrialBalancePdfExportServiceImpl implements TrialBalancePdfExportService {

  private static final Logger logger = LoggerFactory.getLogger(TrialBalancePdfExportServiceImpl.class);
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
  private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

  @Override
  public byte[] exportToPdf(TrialBalanceResponseDTO report, UUID snapshotId) {
    return exportToPdf(report, snapshotId, false);
  }

  @Override
  public byte[] exportToPdf(TrialBalanceResponseDTO report, UUID snapshotId, boolean isDraft) {
    try {
      return generatePdf(report, snapshotId, isDraft);
    } catch (Exception e) {
      logger.error("Failed to generate Trial Balance PDF: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to export Trial Balance report to PDF", e);
    }
  }

  private byte[] generatePdf(TrialBalanceResponseDTO report, UUID snapshotId, boolean isDraft) throws Exception {
    // 1. Define styles
    StyleBuilder boldStyle = stl.style().bold();
    StyleBuilder boldCentered = stl.style(boldStyle)
        .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER);
    StyleBuilder titleStyle = stl.style(boldCentered)
        .setFontSize(14)
        .setVerticalTextAlignment(VerticalTextAlignment.MIDDLE);
    StyleBuilder subtitleStyle = stl.style()
        .setFontSize(10)
        .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER);

    StyleBuilder columnTitleStyle = stl.style(boldStyle)
        .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)
        .setBackgroundColor(new Color(240, 240, 240))
        .setBorder(stl.pen1Point())
        .setPadding(4);

    StyleBuilder columnStyle = stl.style()
        .setBorder(stl.pen1Point())
        .setPadding(3);

    StyleBuilder numberStyle = stl.style(columnStyle)
        .setHorizontalTextAlignment(HorizontalTextAlignment.RIGHT)
        .setPattern("#,##0");

    StyleBuilder totalsStyle = stl.style(numberStyle).bold();

    // 2. Define columns (TT200 S06-DN layout)
    TextColumnBuilder<String> accountCodeColumn =
        col.column("Số hiệu TK", "accountCode", type.stringType())
            .setStyle(columnStyle)
            .setTitleStyle(columnTitleStyle)
            .setWidth(45);

    TextColumnBuilder<String> accountNameColumn =
        col.column("Tên tài khoản", "accountName", type.stringType())
            .setStyle(columnStyle)
            .setTitleStyle(columnTitleStyle)
            .setWidth(130);

    TextColumnBuilder<BigDecimal> openingDebitColumn =
        col.column("Dư đầu kỳ Nợ", "openingDebit", type.bigDecimalType())
            .setStyle(numberStyle)
            .setTitleStyle(columnTitleStyle)
            .setWidth(65);

    TextColumnBuilder<BigDecimal> openingCreditColumn =
        col.column("Dư đầu kỳ Có", "openingCredit", type.bigDecimalType())
            .setStyle(numberStyle)
            .setTitleStyle(columnTitleStyle)
            .setWidth(65);

    TextColumnBuilder<BigDecimal> periodDebitColumn =
        col.column("PS trong kỳ Nợ", "periodDebit", type.bigDecimalType())
            .setStyle(numberStyle)
            .setTitleStyle(columnTitleStyle)
            .setWidth(65);

    TextColumnBuilder<BigDecimal> periodCreditColumn =
        col.column("PS trong kỳ Có", "periodCredit", type.bigDecimalType())
            .setStyle(numberStyle)
            .setTitleStyle(columnTitleStyle)
            .setWidth(65);

    TextColumnBuilder<BigDecimal> closingDebitColumn =
        col.column("Dư cuối kỳ Nợ", "closingDebit", type.bigDecimalType())
            .setStyle(numberStyle)
            .setTitleStyle(columnTitleStyle)
            .setWidth(65);

    TextColumnBuilder<BigDecimal> closingCreditColumn =
        col.column("Dư cuối kỳ Có", "closingCredit", type.bigDecimalType())
            .setStyle(numberStyle)
            .setTitleStyle(columnTitleStyle)
            .setWidth(65);

    // 3. Build report
    JasperReportBuilder jasperReport = report()
        .setPageFormat(PageType.A4, PageOrientation.LANDSCAPE)
        .setPageMargin(margin(20))
        .title(createTitleComponent(report, titleStyle, subtitleStyle, snapshotId, isDraft))
        .columns(
            accountCodeColumn,
            accountNameColumn,
            openingDebitColumn,
            openingCreditColumn,
            periodDebitColumn,
            periodCreditColumn,
            closingDebitColumn,
            closingCreditColumn
        )
        .summary(createTotalsComponent(report, totalsStyle))
        .pageFooter(createFooterComponent(report));

    // 4. Set data source
    jasperReport.setDataSource(createDataSource(report));

    // 5. Export to PDF
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      jasperReport.toPdf(out);
      return out.toByteArray();
    }
  }

  /**
   * Create the title component with company header, report title, and period info.
   */
  private ComponentBuilder<?, ?> createTitleComponent(
      TrialBalanceResponseDTO report,
      StyleBuilder titleStyle,
      StyleBuilder subtitleStyle,
      UUID snapshotId,
      boolean isDraft) {

    String periodName = "";
    String fromDate = "-";
    String toDate = "-";

    if (report.getPeriod() != null) {
      periodName = report.getPeriod().getPeriodName() != null ? report.getPeriod().getPeriodName() : "";
      if (report.getPeriod().getStartDate() != null) {
        fromDate = formatDate(report.getPeriod().getStartDate());
      }
      if (report.getPeriod().getEndDate() != null) {
        toDate = formatDate(report.getPeriod().getEndDate());
      }
    }

    VerticalListBuilder titleComponent = cmp.verticalList(
        // Company header - left aligned
        cmp.text("Đơn vị: " + (report.getCompanyName() != null ? report.getCompanyName() : ""))
            .setStyle(stl.style().bold().setFontSize(11)),
        cmp.verticalGap(5),

        // Report title - centered
        cmp.text("BẢNG CÂN ĐỐI SỐ PHÁT SINH")
            .setStyle(titleStyle),
        cmp.text("(Trial Balance - Form S06-DN)")
            .setStyle(subtitleStyle),
        cmp.verticalGap(3),

        // Period info
        cmp.text("Kỳ: " + periodName + " (Từ " + fromDate + " đến " + toDate + ")")
            .setStyle(stl.style().setFontSize(10).setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)),
        cmp.text("Đơn vị tính: VNĐ")
            .setStyle(stl.style().setFontSize(9).setHorizontalTextAlignment(HorizontalTextAlignment.CENTER))
    );

    // Add DRAFT watermark if period is open
    if (isDraft) {
      titleComponent.add(
          cmp.text("*** DỰ THẢO - Kỳ chưa đóng ***")
              .setStyle(stl.style().bold()
                  .setForegroundColor(Color.RED)
                  .setFontSize(12)
                  .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER))
      );
    }

    // Add snapshot ID if provided
    if (snapshotId != null) {
      titleComponent.add(
          cmp.text("Snapshot ID: " + snapshotId.toString())
              .setStyle(stl.style().setFontSize(8).setForegroundColor(Color.GRAY)
                  .setHorizontalTextAlignment(HorizontalTextAlignment.LEFT))
      );
    }

    titleComponent.add(cmp.verticalGap(10));

    return titleComponent;
  }

  /**
   * Create totals row component showing grand totals.
   */
  private ComponentBuilder<?, ?> createTotalsComponent(TrialBalanceResponseDTO report, StyleBuilder totalsStyle) {
    // Create a horizontal list for the totals row
    StyleBuilder labelStyle = stl.style().bold().setBorder(stl.pen1Point()).setPadding(4);
    StyleBuilder numberTotalStyle = stl.style(totalsStyle).setBorder(stl.pen1Point()).setPadding(4);

    return cmp.horizontalList(
        cmp.text("").setStyle(labelStyle).setFixedWidth(45),  // Account code column
        cmp.text("TỔNG CỘNG").setStyle(labelStyle).setFixedWidth(130),
        cmp.text(formatNumber(report.getTotalOpeningDebit())).setStyle(numberTotalStyle).setFixedWidth(65),
        cmp.text(formatNumber(report.getTotalOpeningCredit())).setStyle(numberTotalStyle).setFixedWidth(65),
        cmp.text(formatNumber(report.getTotalPeriodDebit())).setStyle(numberTotalStyle).setFixedWidth(65),
        cmp.text(formatNumber(report.getTotalPeriodCredit())).setStyle(numberTotalStyle).setFixedWidth(65),
        cmp.text(formatNumber(report.getTotalClosingDebit())).setStyle(numberTotalStyle).setFixedWidth(65),
        cmp.text(formatNumber(report.getTotalClosingCredit())).setStyle(numberTotalStyle).setFixedWidth(65)
    );
  }

  /**
   * Create footer with timestamp and signature blocks per TT200.
   */
  private ComponentBuilder<?, ?> createFooterComponent(TrialBalanceResponseDTO report) {
    String generatedAt = report.getGeneratedAt() != null
        ? TIMESTAMP_FORMATTER.format(report.getGeneratedAt().atZone(DEFAULT_ZONE))
        : TIMESTAMP_FORMATTER.format(java.time.Instant.now().atZone(DEFAULT_ZONE));

    StyleBuilder footerStyle = stl.style().setFontSize(9);
    StyleBuilder signatureStyle = stl.style().bold().setFontSize(10)
        .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER);

    return cmp.verticalList(
        // Timestamp and page number
        cmp.horizontalList(
            cmp.text("Ngày lập: " + generatedAt).setStyle(footerStyle),
            cmp.pageXofY().setStyle(footerStyle)
                .setHorizontalTextAlignment(HorizontalTextAlignment.RIGHT)
        ),
        cmp.verticalGap(20),

        // Signature blocks (TT200 format)
        cmp.horizontalList(
            cmp.verticalList(
                cmp.text("Người lập biểu").setStyle(signatureStyle),
                cmp.text("(Ký, họ tên)").setStyle(stl.style().setFontSize(8)
                    .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER))
            ),
            cmp.horizontalGap(50),
            cmp.verticalList(
                cmp.text("Kế toán trưởng").setStyle(signatureStyle),
                cmp.text("(Ký, họ tên)").setStyle(stl.style().setFontSize(8)
                    .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER))
            ),
            cmp.horizontalGap(50),
            cmp.verticalList(
                cmp.text("Giám đốc").setStyle(signatureStyle),
                cmp.text("(Ký, họ tên, đóng dấu)").setStyle(stl.style().setFontSize(8)
                    .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER))
            )
        )
    );
  }

  /**
   * Create data source from trial balance accounts.
   */
  private JRDataSource createDataSource(TrialBalanceResponseDTO report) {
    DRDataSource dataSource = new DRDataSource(
        "accountCode", "accountName",
        "openingDebit", "openingCredit",
        "periodDebit", "periodCredit",
        "closingDebit", "closingCredit"
    );

    List<TrialBalanceDTO> accounts = report.getAccounts();
    if (accounts != null) {
      for (TrialBalanceDTO acc : accounts) {
        dataSource.add(
            acc.getAccountCode() != null ? acc.getAccountCode() : "",
            acc.getAccountName() != null ? acc.getAccountName() : "",
            acc.getOpeningDebit(),
            acc.getOpeningCredit(),
            acc.getPeriodDebit(),
            acc.getPeriodCredit(),
            acc.getClosingDebit(),
            acc.getClosingCredit()
        );
      }
    }

    return dataSource;
  }

  private String formatDate(LocalDate date) {
    return date != null ? DATE_FORMATTER.format(date) : "-";
  }

  private String formatNumber(BigDecimal number) {
    if (number == null || number.compareTo(BigDecimal.ZERO) == 0) {
      return "-";
    }
    return String.format("%,.0f", number);
  }
}
