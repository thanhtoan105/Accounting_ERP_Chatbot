package com.accounting.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.accounting.entity.Company;
import com.accounting.service.CompanyService;

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerIT {

    @Autowired private MockMvc mockMvc;

    @MockBean private CompanyService companyService;

    @BeforeEach
    void seedCompany() {
        Company c = new Company();
        c.setCode("ACME");
        c.setName("ACME Corp");
        c.setTaxCode("0123456789");
        c.setAddress("123 Street, Hanoi");
        c.setLogoUrl(null);
        c.setContactEmail("contact@acme.test");
        c.setContactPhone("+84 90 000 0000");
        c.setFiscalYearStart(LocalDate.of(2025, 1, 1));
        Mockito.when(companyService.getCurrentCompanySettings()).thenReturn(c);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void exportPdf_asAdmin_ok() throws Exception {
        MvcResult res = mockMvc
                .perform(get("/api/v1/admin/reports/company/profile").param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] bytes = res.getResponse().getContentAsByteArray();
        assertThat(bytes.length).isGreaterThan(100);
        String header = new String(bytes, 0, 4);
        assertThat(header).startsWith("%PDF");
    }

    @Test
    @WithMockUser(roles = {"CHIEF_ACCOUNTANT"})
    void exportXlsx_asChief_ok() throws Exception {
        MvcResult res = mockMvc
                .perform(get("/api/v1/admin/reports/company/profile").param("format", "xlsx"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = res.getResponse().getContentAsByteArray();
        assertThat(bytes.length).isGreaterThan(100);
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT"})
    void export_asNonPrivileged_forbidden() throws Exception {
        mockMvc
                .perform(get("/api/v1/admin/reports/company/profile").param("format", "pdf"))
                .andExpect(status().isForbidden());
    }
}
