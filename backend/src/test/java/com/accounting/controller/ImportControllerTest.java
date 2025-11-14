package com.accounting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accounting.dto.ImportResultDTO;
import com.accounting.imports.ImportType;
import com.accounting.imports.service.ImportErrorReportService;
import com.accounting.imports.service.ImportTemplateService;
import com.accounting.imports.service.MasterDataImportFacade;
import com.accounting.security.CompanyContext;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ImportControllerTest extends com.accounting.test.IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.MockBean
    private MasterDataImportFacade importFacade;
    @org.springframework.boot.test.mock.mockito.MockBean
    private ImportTemplateService templateService;
    @org.springframework.boot.test.mock.mockito.MockBean
    private ImportErrorReportService errorReportService;

    @BeforeEach
    void setup() {
        CompanyContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private UsernamePasswordAuthenticationToken auth(String userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId, "password", java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void downloadTemplate_shouldReturnXlsx() throws Exception {
        byte[] payload = "xlsx-binary".getBytes(StandardCharsets.UTF_8);
        when(templateService.generateTemplate(ImportType.CUSTOMERS, "xlsx")).thenReturn(payload);

        var mvcResult = mockMvc
                .perform(get("/api/v1/import/templates/customers?format=xlsx")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth("1", "ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();
        String disposition = mvcResult.getResponse().getHeader("Content-Disposition");
        org.junit.jupiter.api.Assertions.assertNotNull(disposition);
        org.junit.jupiter.api.Assertions.assertTrue(disposition.contains("customers-template.xlsx"));
        String ctype = mvcResult.getResponse().getContentType();
        org.junit.jupiter.api.Assertions.assertNotNull(ctype);
        org.junit.jupiter.api.Assertions.assertTrue(
                ctype.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void downloadErrorReport_shouldReturn404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(errorReportService.getReport(id, 1L)).thenReturn(null);

        mockMvc
                .perform(get("/api/v1/import/error-reports/" + id)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth("1", "ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void importData_shouldReturn200() throws Exception {
        ImportResultDTO dto = new ImportResultDTO(1, 0, 0, java.util.List.of(), null);
        when(importFacade.process(any(), any(), anyString(), any())).thenReturn(dto);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "customers.csv",
                "text/csv",
                ("customer_code,name,tax_code,email,phone,address,active\n"
                        + "C001,Valid Customer,0101234567,valid@example.com,+84123456789,Address,TRUE\n")
                        .getBytes(StandardCharsets.UTF_8));

        mockMvc
                .perform(
                        multipart("/api/v1/import/customers")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                                .param("locale", "en")
                                .header("X-Company-Id", "1")
                                .with(SecurityMockMvcRequestPostProcessors.authentication(auth("1", "ADMIN"))))
                .andExpect(status().isOk());
    }
}
