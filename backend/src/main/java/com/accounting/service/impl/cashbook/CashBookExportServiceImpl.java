package com.accounting.service.impl.cashbook;

import com.accounting.dto.cashbook.CashBookEntryDTO;
import com.accounting.dto.cashbook.CashBookFilterDTO;
import com.accounting.dto.cashbook.CashBookResponseDTO;
import com.accounting.dto.cashbook.CashBookSummaryDTO;
import com.accounting.dto.cashbook.CashBookSummaryDTO.AccountSummaryDTO;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.CashBookExportService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of CashBookExportService for Excel/PDF export.
 * Provides export with company branding, filter snapshot, and document hash.
 *
 * <p>
 * AC6.4-04: Export to Excel/PDF with company branding/logo, filter snapshot,
 * timestamp, generated-by; file footer includes document hash.
 */
@Service
public class CashBookExportServiceImpl implements CashBookExportService {

    private static final Logger logger = LoggerFactory.getLogger(CashBookExportServiceImpl.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String CASH_BOOK_SHEET_NAME = "Cash Book";
    private static final String SUMMARY_SHEET_NAME = "Cash Book Summary";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public CashBookExportServiceImpl(
            CompanyRepository companyRepository,
            UserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @Override
    public byte[] exportToExcel(CashBookResponseDTO cashBookData, CashBookFilterDTO filter) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(CASH_BOOK_SHEET_NAME);

            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);

            int rowNum = 0;

            // Company header
            Company company = getCompany();
            if (company != null) {
                Row companyRow = sheet.createRow(rowNum++);
                Cell companyCell = companyRow.createCell(0);
                companyCell.setCellValue(company.getName());
                companyCell.setCellStyle(titleStyle);

                if (company.getAddress() != null) {
                    Row addressRow = sheet.createRow(rowNum++);
                    addressRow.createCell(0).setCellValue(company.getAddress());
                }
                if (company.getTaxCode() != null) {
                    Row taxRow = sheet.createRow(rowNum++);
                    taxRow.createCell(0).setCellValue("Tax Code: " + company.getTaxCode());
                }
                rowNum++;
            }

            // Report title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("CASH/BANK BOOK REPORT");
            titleCell.setCellStyle(titleStyle);
            rowNum++;

            // Account info
            createInfoRow(sheet, rowNum++, "Account:",
                    cashBookData.getBankName() + " - " + cashBookData.getAccountNumber());
            createInfoRow(sheet, rowNum++, "Account Type:", cashBookData.getAccountType());
            createInfoRow(sheet, rowNum++, "GL Account Code:", cashBookData.getGlAccountCode());

            // Filter snapshot
            if (filter.getDateFrom() != null || filter.getDateTo() != null) {
                String dateRange = String.format("%s to %s",
                        filter.getDateFrom() != null ? DATE_FORMATTER.format(filter.getDateFrom()) : "Beginning",
                        filter.getDateTo() != null ? DATE_FORMATTER.format(filter.getDateTo()) : "End");
                createInfoRow(sheet, rowNum++, "Date Range:", dateRange);
            }
            if (filter.getTransactionType() != null && !"all".equals(filter.getTransactionType())) {
                createInfoRow(sheet, rowNum++, "Transaction Type:", filter.getTransactionType());
            }
            if (filter.getReference() != null && !filter.getReference().isBlank()) {
                createInfoRow(sheet, rowNum++, "Reference Filter:", filter.getReference());
            }

            // Summary totals
            rowNum++;
            createInfoRow(sheet, rowNum++, "Opening Balance:", formatCurrency(cashBookData.getOpeningBalance()));
            createInfoRow(sheet, rowNum++, "Total Inflow:", formatCurrency(cashBookData.getTotalInflow()));
            createInfoRow(sheet, rowNum++, "Total Outflow:", formatCurrency(cashBookData.getTotalOutflow()));
            createInfoRow(sheet, rowNum++, "Closing Balance:", formatCurrency(cashBookData.getClosingBalance()));
            rowNum++;

            // Transaction table header
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = { "Date", "Voucher #", "Description", "Debit (Inflow)", "Credit (Outflow)",
                    "Running Balance" };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Transaction data rows
            for (CashBookEntryDTO entry : cashBookData.getTransactions()) {
                Row dataRow = sheet.createRow(rowNum++);

                Cell dateCell = dataRow.createCell(0);
                dateCell.setCellValue(DATE_FORMATTER.format(entry.getTransactionDate()));

                dataRow.createCell(1).setCellValue(entry.getVoucherNumber() != null ? entry.getVoucherNumber() : "");
                dataRow.createCell(2).setCellValue(entry.getDescription() != null ? entry.getDescription() : "");

                Cell debitCell = dataRow.createCell(3);
                if (entry.getDebit() != null && entry.getDebit().compareTo(BigDecimal.ZERO) > 0) {
                    debitCell.setCellValue(entry.getDebit().doubleValue());
                    debitCell.setCellStyle(currencyStyle);
                }

                Cell creditCell = dataRow.createCell(4);
                if (entry.getCredit() != null && entry.getCredit().compareTo(BigDecimal.ZERO) > 0) {
                    creditCell.setCellValue(entry.getCredit().doubleValue());
                    creditCell.setCellStyle(currencyStyle);
                }

                Cell balanceCell = dataRow.createCell(5);
                if (entry.getRunningBalance() != null) {
                    balanceCell.setCellValue(entry.getRunningBalance().doubleValue());
                    balanceCell.setCellStyle(currencyStyle);
                }
            }

            // Footer with generation info and hash
            rowNum++;
            String generatedBy = getCurrentUserName();
            String timestamp = DATETIME_FORMATTER.format(LocalDateTime.now());
            String hash = generateDataHash(cashBookData);

            createInfoRow(sheet, rowNum++, "Generated by:", generatedBy);
            createInfoRow(sheet, rowNum++, "Generated at:", timestamp);
            createInfoRow(sheet, rowNum++, "Document Hash:", hash);

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            logger.error("Failed to export cash book to Excel", e);
            throw new RuntimeException("Failed to export cash book to Excel: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] exportToPdf(CashBookResponseDTO cashBookData, CashBookFilterDTO filter) {
        // Simple text-based PDF for MVP (can be enhanced with iText or PDFBox later)
        StringBuilder pdf = new StringBuilder();

        // Company header
        Company company = getCompany();
        if (company != null) {
            pdf.append(company.getName()).append("\n");
            if (company.getAddress() != null) {
                pdf.append(company.getAddress()).append("\n");
            }
            if (company.getTaxCode() != null) {
                pdf.append("Tax Code: ").append(company.getTaxCode()).append("\n");
            }
            pdf.append("\n");
        }

        // Report title
        pdf.append("CASH/BANK BOOK REPORT\n");
        pdf.append("=".repeat(80)).append("\n\n");

        // Account info
        pdf.append("Account: ").append(cashBookData.getBankName())
                .append(" - ").append(cashBookData.getAccountNumber()).append("\n");
        pdf.append("Account Type: ").append(cashBookData.getAccountType()).append("\n");
        pdf.append("GL Account Code: ").append(cashBookData.getGlAccountCode()).append("\n");

        // Filter snapshot
        if (filter.getDateFrom() != null || filter.getDateTo() != null) {
            String dateRange = String.format("%s to %s",
                    filter.getDateFrom() != null ? DATE_FORMATTER.format(filter.getDateFrom()) : "Beginning",
                    filter.getDateTo() != null ? DATE_FORMATTER.format(filter.getDateTo()) : "End");
            pdf.append("Date Range: ").append(dateRange).append("\n");
        }
        pdf.append("\n");

        // Summary
        pdf.append("Opening Balance: ").append(formatCurrency(cashBookData.getOpeningBalance())).append("\n");
        pdf.append("Total Inflow: ").append(formatCurrency(cashBookData.getTotalInflow())).append("\n");
        pdf.append("Total Outflow: ").append(formatCurrency(cashBookData.getTotalOutflow())).append("\n");
        pdf.append("Closing Balance: ").append(formatCurrency(cashBookData.getClosingBalance())).append("\n\n");

        // Transactions table
        pdf.append(String.format("%-12s %-15s %-30s %15s %15s %15s\n",
                "Date", "Voucher #", "Description", "Debit", "Credit", "Balance"));
        pdf.append("-".repeat(100)).append("\n");

        for (CashBookEntryDTO entry : cashBookData.getTransactions()) {
            pdf.append(String.format("%-12s %-15s %-30s %15s %15s %15s\n",
                    DATE_FORMATTER.format(entry.getTransactionDate()),
                    truncate(entry.getVoucherNumber(), 15),
                    truncate(entry.getDescription(), 30),
                    entry.getDebit() != null ? formatCurrency(entry.getDebit()) : "",
                    entry.getCredit() != null ? formatCurrency(entry.getCredit()) : "",
                    entry.getRunningBalance() != null ? formatCurrency(entry.getRunningBalance()) : ""));
        }

        // Footer
        pdf.append("\n");
        pdf.append("-".repeat(100)).append("\n");
        pdf.append("Generated by: ").append(getCurrentUserName()).append("\n");
        pdf.append("Generated at: ").append(DATETIME_FORMATTER.format(LocalDateTime.now())).append("\n");
        pdf.append("Document Hash: ").append(generateDataHash(cashBookData)).append("\n");

        return pdf.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] exportSummaryToExcel(CashBookSummaryDTO summaryData, CashBookFilterDTO filter) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SUMMARY_SHEET_NAME);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            int rowNum = 0;

            // Company header
            Company company = getCompany();
            if (company != null) {
                Row companyRow = sheet.createRow(rowNum++);
                Cell companyCell = companyRow.createCell(0);
                companyCell.setCellValue(company.getName());
                companyCell.setCellStyle(titleStyle);
                rowNum++;
            }

            // Report title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("CASH/BANK BOOK SUMMARY");
            titleCell.setCellStyle(titleStyle);
            rowNum++;

            // Filter snapshot
            if (filter.getDateFrom() != null || filter.getDateTo() != null) {
                String dateRange = String.format("%s to %s",
                        filter.getDateFrom() != null ? DATE_FORMATTER.format(filter.getDateFrom()) : "Beginning",
                        filter.getDateTo() != null ? DATE_FORMATTER.format(filter.getDateTo()) : "End");
                createInfoRow(sheet, rowNum++, "Date Range:", dateRange);
            }
            rowNum++;

            // Table header
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = { "Account Name", "Type", "Opening Balance", "Total Inflow", "Total Outflow",
                    "Closing Balance", "Transactions" };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Account rows
            for (AccountSummaryDTO account : summaryData.getAccounts()) {
                Row dataRow = sheet.createRow(rowNum++);
                dataRow.createCell(0).setCellValue(account.getBankName() + " - " + account.getAccountNumber());
                dataRow.createCell(1).setCellValue(account.getAccountType());

                Cell openingCell = dataRow.createCell(2);
                openingCell.setCellValue(account.getOpeningBalance().doubleValue());
                openingCell.setCellStyle(currencyStyle);

                Cell inflowCell = dataRow.createCell(3);
                inflowCell.setCellValue(account.getTotalInflow().doubleValue());
                inflowCell.setCellStyle(currencyStyle);

                Cell outflowCell = dataRow.createCell(4);
                outflowCell.setCellValue(account.getTotalOutflow().doubleValue());
                outflowCell.setCellStyle(currencyStyle);

                Cell closingCell = dataRow.createCell(5);
                closingCell.setCellValue(account.getClosingBalance().doubleValue());
                closingCell.setCellStyle(currencyStyle);

                dataRow.createCell(6).setCellValue(account.getTransactionCount());
            }

            // Grand totals row
            rowNum++;
            Row totalRow = sheet.createRow(rowNum++);
            totalRow.createCell(0).setCellValue("GRAND TOTAL");
            totalRow.getCell(0).setCellStyle(totalStyle);
            totalRow.createCell(1).setCellValue("");

            Cell totalOpeningCell = totalRow.createCell(2);
            totalOpeningCell.setCellValue(summaryData.getGrandTotals().getTotalOpeningBalance().doubleValue());
            totalOpeningCell.setCellStyle(totalStyle);

            Cell totalInflowCell = totalRow.createCell(3);
            totalInflowCell.setCellValue(summaryData.getGrandTotals().getTotalInflow().doubleValue());
            totalInflowCell.setCellStyle(totalStyle);

            Cell totalOutflowCell = totalRow.createCell(4);
            totalOutflowCell.setCellValue(summaryData.getGrandTotals().getTotalOutflow().doubleValue());
            totalOutflowCell.setCellStyle(totalStyle);

            Cell totalClosingCell = totalRow.createCell(5);
            totalClosingCell.setCellValue(summaryData.getGrandTotals().getTotalClosingBalance().doubleValue());
            totalClosingCell.setCellStyle(totalStyle);

            Cell totalTxCell = totalRow.createCell(6);
            totalTxCell.setCellValue(summaryData.getGrandTotals().getTotalTransactionCount());
            totalTxCell.setCellStyle(totalStyle);

            // Footer
            rowNum++;
            String generatedBy = getCurrentUserName();
            String timestamp = DATETIME_FORMATTER.format(LocalDateTime.now());
            String hash = generateSummaryHash(summaryData);

            createInfoRow(sheet, rowNum++, "Generated by:", generatedBy);
            createInfoRow(sheet, rowNum++, "Generated at:", timestamp);
            createInfoRow(sheet, rowNum++, "Document Hash:", hash);

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            logger.error("Failed to export cash book summary to Excel", e);
            throw new RuntimeException("Failed to export cash book summary to Excel: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] exportSummaryToPdf(CashBookSummaryDTO summaryData, CashBookFilterDTO filter) {
        StringBuilder pdf = new StringBuilder();

        // Company header
        Company company = getCompany();
        if (company != null) {
            pdf.append(company.getName()).append("\n\n");
        }

        // Report title
        pdf.append("CASH/BANK BOOK SUMMARY\n");
        pdf.append("=".repeat(80)).append("\n\n");

        // Filter snapshot
        if (filter.getDateFrom() != null || filter.getDateTo() != null) {
            String dateRange = String.format("%s to %s",
                    filter.getDateFrom() != null ? DATE_FORMATTER.format(filter.getDateFrom()) : "Beginning",
                    filter.getDateTo() != null ? DATE_FORMATTER.format(filter.getDateTo()) : "End");
            pdf.append("Date Range: ").append(dateRange).append("\n\n");
        }

        // Table
        pdf.append(String.format("%-30s %-8s %15s %15s %15s %15s %8s\n",
                "Account", "Type", "Opening", "Inflow", "Outflow", "Closing", "Count"));
        pdf.append("-".repeat(110)).append("\n");

        for (AccountSummaryDTO account : summaryData.getAccounts()) {
            pdf.append(String.format("%-30s %-8s %15s %15s %15s %15s %8d\n",
                    truncate(account.getBankName(), 30),
                    account.getAccountType(),
                    formatCurrency(account.getOpeningBalance()),
                    formatCurrency(account.getTotalInflow()),
                    formatCurrency(account.getTotalOutflow()),
                    formatCurrency(account.getClosingBalance()),
                    account.getTransactionCount()));
        }

        // Grand totals
        pdf.append("-".repeat(110)).append("\n");
        pdf.append(String.format("%-30s %-8s %15s %15s %15s %15s %8d\n",
                "GRAND TOTAL", "",
                formatCurrency(summaryData.getGrandTotals().getTotalOpeningBalance()),
                formatCurrency(summaryData.getGrandTotals().getTotalInflow()),
                formatCurrency(summaryData.getGrandTotals().getTotalOutflow()),
                formatCurrency(summaryData.getGrandTotals().getTotalClosingBalance()),
                summaryData.getGrandTotals().getTotalTransactionCount()));

        // Footer
        pdf.append("\n");
        pdf.append("Generated by: ").append(getCurrentUserName()).append("\n");
        pdf.append("Generated at: ").append(DATETIME_FORMATTER.format(LocalDateTime.now())).append("\n");
        pdf.append("Document Hash: ").append(generateSummaryHash(summaryData)).append("\n");

        return pdf.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String generateDataHash(CashBookResponseDTO cashBookData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder content = new StringBuilder();
            content.append(cashBookData.getBankAccountId());
            content.append(cashBookData.getOpeningBalance());
            content.append(cashBookData.getClosingBalance());
            content.append(cashBookData.getTotalCount());

            for (CashBookEntryDTO entry : cashBookData.getTransactions()) {
                content.append(entry.getVoucherId());
                content.append(entry.getDebit());
                content.append(entry.getCredit());
            }

            byte[] hash = digest.digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash).substring(0, 16).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to generate data hash", e);
            return "HASH_ERROR";
        }
    }

    private String generateSummaryHash(CashBookSummaryDTO summaryData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder content = new StringBuilder();
            content.append(summaryData.getGrandTotals().getTotalOpeningBalance());
            content.append(summaryData.getGrandTotals().getTotalClosingBalance());
            content.append(summaryData.getGrandTotals().getTotalTransactionCount());

            for (AccountSummaryDTO account : summaryData.getAccounts()) {
                content.append(account.getBankAccountId());
                content.append(account.getClosingBalance());
            }

            byte[] hash = digest.digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash).substring(0, 16).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to generate summary hash", e);
            return "HASH_ERROR";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private void createInfoRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value != null ? value : "");
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0₫";
        }
        return String.format("%,.0f₫", amount);
    }

    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0"));
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("dd/mm/yyyy"));
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0"));
        return style;
    }

    private Company getCompany() {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            return null;
        }
        return companyRepository.findById(companyId).orElse(null);
    }

    private String getCurrentUserName() {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            if (userId != null) {
                return userRepository.findById(userId)
                        .map(User::getFullName)
                        .orElse("Unknown User");
            }
        } catch (Exception e) {
            logger.debug("Could not get current user name", e);
        }
        return "System";
    }
}
