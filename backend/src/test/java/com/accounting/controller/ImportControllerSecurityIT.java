package com.accounting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
import org.springframework.test.web.servlet.MvcResult;

import com.accounting.dto.ImportResultDTO;
import com.accounting.imports.service.ImportErrorReportService;
import com.accounting.imports.service.ImportTemplateService;
import com.accounting.imports.service.MasterDataImportFacade;

@SpringBootTest
@AutoConfigureMockMvc
class ImportControllerSecurityIT extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private MasterDataImportFacade importFacade;

  @MockBean private ImportTemplateService templateService;

  @MockBean private ImportErrorReportService errorReportService;

  @MockBean private com.accounting.service.AuditService auditService;

  private MockMultipartFile validCsv;

  @BeforeEach
  void setupMocks() {
    validCsv =
        new MockMultipartFile(
            "file",
            "customers.csv",
            "text/csv",
            ("customer_code,name,tax_code,email,phone,address,active\n"
                    + "C001,Valid Customer,0101234567,valid@example.com,+84123456789,Address,TRUE\n")
                .getBytes(StandardCharsets.UTF_8));

    when(importFacade.process(any(), any(), any(), any()))
        .thenReturn(new ImportResultDTO(1, 0, 0, java.util.List.of(), null));
  }

  private UsernamePasswordAuthenticationToken auth(String userId, String role) {
    return new UsernamePasswordAuthenticationToken(
        userId, "password", java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  @Test
  void importData_asAdmin_shouldSucceed() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/import/customers")
                .file(validCsv)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .param("locale", "en")
                .header("X-Company-Id", "42")
                .with(SecurityMockMvcRequestPostProcessors.authentication(auth("1", "ADMIN"))))
        .andExpect(status().isOk());
  }

  @Test
  void importData_asChiefAccountant_shouldSucceed() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/import/customers")
                .file(validCsv)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header("X-Company-Id", "42")
                .with(SecurityMockMvcRequestPostProcessors.authentication(auth("2", "CHIEF_ACCOUNTANT"))))
        .andExpect(status().isOk());
  }

  @Test
  void importData_asAccountant_shouldBeForbidden() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/import/customers")
                .file(validCsv)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header("X-Company-Id", "42")
                .with(SecurityMockMvcRequestPostProcessors.authentication(auth("3", "ACCOUNTANT"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void importData_missingCompanyHeader_shouldReturnBadRequest() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/import/customers")
                .file(validCsv)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .with(SecurityMockMvcRequestPostProcessors.authentication(auth("4", "ADMIN"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void downloadErrorReport_scopesByCompanyContext() throws Exception {
    UUID reportId = UUID.randomUUID();
    when(errorReportService.getReport(reportId, 42L)).thenReturn("ok".getBytes(StandardCharsets.UTF_8));

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/import/error-reports/" + reportId)
                    .header("X-Company-Id", "42")
                    .with(SecurityMockMvcRequestPostProcessors.authentication(auth("5", "ADMIN"))))
            .andExpect(status().isOk())
            .andReturn();

    verify(errorReportService).getReport(reportId, 42L);
    byte[] body = result.getResponse().getContentAsByteArray();
    assertThat(body).containsExactly("ok".getBytes(StandardCharsets.UTF_8));

    reset(errorReportService);
    when(errorReportService.getReport(reportId, 99L)).thenReturn("other".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            get("/api/v1/import/error-reports/" + reportId)
                .header("X-Company-Id", "99")
                .with(SecurityMockMvcRequestPostProcessors.authentication(auth("5", "ADMIN"))))
        .andExpect(status().isOk());

    verify(errorReportService).getReport(reportId, 99L);
  }
}
