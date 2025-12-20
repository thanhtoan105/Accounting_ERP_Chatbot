package com.accounting.report;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.accounting.entity.Company;
import com.accounting.service.CompanyService;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

@Service
public class ReportService {

    private final CompanyService companyService;
    private final ReportFooterProvider footerProvider = new ReportFooterProvider();

    public enum Format { PDF, XLSX }

    public ReportService(CompanyService companyService) {
        this.companyService = companyService;
    }

    /**
     * Generates a simple company profile report demonstrating footer injection (logo + legal info).
     */
    public byte[] generateCompanyProfileReport(Format format) {
        // Company is scoped by current CompanyContext in CompanyService implementation
        Company company = companyService.getCurrentCompanySettings();

        Map<String, Object> params = new HashMap<>();
        params.putAll(
                footerProvider.buildFooterParams(
                        company.getName(),
                        company.getTaxCode(),
                        company.getAddress(),
                        company.getLogoUrl()));

        // Header/body parameters
        params.put("COMPANY_CODE", company.getCode());
        params.put("CONTACT_EMAIL", company.getContactEmail());
        params.put("CONTACT_PHONE", company.getContactPhone());
        params.put("FISCAL_YEAR_START", company.getFiscalYearStart() != null ? company.getFiscalYearStart().toString() : "");

        try (InputStream jrxml = getClass().getResourceAsStream("/reports/sample_company_profile.jrxml")) {
            if (jrxml == null) {
                throw new IllegalStateException("JRXML template not found: /reports/sample_company_profile.jrxml");
            }
            JasperReport report = JasperCompileManager.compileReport(jrxml);
            JasperPrint print = JasperFillManager.fillReport(report, params, new JREmptyDataSource());

            if (format == Format.PDF) {
                return JasperExportManager.exportReportToPdf(print);
            } else {
                JRXlsxExporter exporter = new JRXlsxExporter();
                exporter.setExporterInput(new SimpleExporterInput(print));
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(bos));
                exporter.exportReport();
                return bos.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate report", e);
        }
    }
}
