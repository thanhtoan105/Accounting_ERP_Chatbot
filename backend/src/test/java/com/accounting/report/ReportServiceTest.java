package com.accounting.report;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.accounting.entity.Company;
import com.accounting.report.ReportService.Format;
import com.accounting.service.CompanyService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReportServiceTest {

    private CompanyService companyService;
    private ReportService reportService;

    @BeforeEach
    void setup() {
        companyService = Mockito.mock(CompanyService.class);

        Company c = new Company();
        // Assuming setters exist on Company entity
        c.setCode("ACME");
        c.setName("ACME Corp");
        c.setTaxCode("0123456789");
        c.setAddress("123 Street, Hanoi");
        c.setLogoUrl(null); // optional
        c.setContactEmail("contact@acme.test");
        c.setContactPhone("+84 90 000 0000");
        c.setFiscalYearStart(LocalDate.of(2025, 1, 1));

        when(companyService.getCurrentCompanySettings()).thenReturn(c);

        reportService = new ReportService(companyService);
    }

    @Test
    void generatesPdf() {
        byte[] pdf = reportService.generateCompanyProfileReport(Format.PDF);
        assertNotNull(pdf);
        assertTrue(pdf.length > 100, "PDF should not be empty");
        // Simple PDF header check: %PDF
        String header = new String(pdf, 0, 4);
        assertTrue(header.startsWith("%PDF"));
    }

    @Test
    void generatesXlsx() {
        byte[] xlsx = reportService.generateCompanyProfileReport(Format.XLSX);
        assertNotNull(xlsx);
        assertTrue(xlsx.length > 100, "XLSX should not be empty");
        // XLSX is a zip file; check PK header
        assertTrue(xlsx[0] == 'P' && xlsx[1] == 'K');
    }
}


