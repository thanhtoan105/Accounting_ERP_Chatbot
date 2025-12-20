package com.accounting.controller.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.accounting.dto.report.ComparisonSettingsDTO;
import com.accounting.dto.report.MultiPeriodLineDTO;
import com.accounting.dto.report.MultiPeriodReportDTO;
import com.accounting.dto.report.PeriodColumnDTO;
import com.accounting.dto.report.VarianceDTO;
import com.accounting.entity.CompanySettings;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.service.StatutoryReportService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MultiPeriodReportControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private StatutoryReportService statutoryReportService;

  @MockBean
  private CompanySettingsRepository companySettingsRepository;

  private static final Long COMPANY_ID = 1L;

  private MultiPeriodReportDTO createMockReport(String reportType, int periodCount) {
    UUID[] periodIds = new UUID[periodCount];
    for (int i = 0; i < periodCount; i++) {
      periodIds[i] = UUID.randomUUID();
    }

    List<PeriodColumnDTO> periods = new java.util.ArrayList<>();
    for (int i = 0; i < periodCount; i++) {
      periods.add(new PeriodColumnDTO(
          periodIds[i],
          "Period " + (i + 1),
          LocalDate.of(2024, i + 1, 1),
          LocalDate.of(2024, i + 1, 28),
          "2024",
          false));
    }

    Map<UUID, BigDecimal> periodValues = new java.util.HashMap<>();
    for (UUID periodId : periodIds) {
      periodValues.put(periodId, new BigDecimal("100000"));
    }

    List<VarianceDTO> variances = new java.util.ArrayList<>();
    for (int i = 1; i < periodCount; i++) {
      variances.add(new VarianceDTO(
          periodIds[i - 1],
          periodIds[i],
          new BigDecimal("10000"),
          10.0,
          "FAVORABLE"));
    }

    List<MultiPeriodLineDTO> lines = List.of(
        new MultiPeriodLineDTO(
            "110",
            "Cash",
            "Cash",
            1,
            false,
            periodValues,
            variances,
            List.of(0.0, 0.5, 1.0),
            false,
            true));

    ComparisonSettingsDTO settings = ComparisonSettingsDTO.defaults();

    return new MultiPeriodReportDTO(
        reportType,
        reportType.equals("B01") ? "Balance Sheet" : "Income Statement",
        COMPANY_ID,
        "Test Company",
        periods,
        lines,
        settings,
        Instant.now(),
        false);
  }

  @Nested
  @DisplayName("Multi-Period Report Endpoint RBAC Tests")
  class MultiPeriodReportRbacTests {

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("CFO role should access multi-period report")
    void cfoRoleShouldAccessMultiPeriodReport() throws Exception {
      UUID period1 = UUID.randomUUID();
      UUID period2 = UUID.randomUUID();
      String periodIds = period1.toString() + "," + period2.toString();

      when(statutoryReportService.generateMultiPeriodReport(eq("B01"), anyList()))
          .thenReturn(createMockReport("B01", 2));

      MvcResult result = mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", periodIds))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      MultiPeriodReportDTO response = objectMapper.readValue(responseBody, MultiPeriodReportDTO.class);
      assertThat(response).isNotNull();
      assertThat(response.reportType()).isEqualTo("B01");
    }

    @Test
    @WithMockUser(roles = { "CHIEF_ACCOUNTANT" })
    @DisplayName("CHIEF_ACCOUNTANT role should access multi-period report")
    void chiefAccountantRoleShouldAccessMultiPeriodReport() throws Exception {
      UUID period1 = UUID.randomUUID();
      UUID period2 = UUID.randomUUID();
      String periodIds = period1.toString() + "," + period2.toString();

      when(statutoryReportService.generateMultiPeriodReport(eq("B02"), anyList()))
          .thenReturn(createMockReport("B02", 2));

      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B02")
          .param("periodIds", periodIds))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(roles = { "AUDITOR" })
    @DisplayName("AUDITOR role should access multi-period report")
    void auditorRoleShouldAccessMultiPeriodReport() throws Exception {
      UUID period1 = UUID.randomUUID();
      UUID period2 = UUID.randomUUID();
      String periodIds = period1.toString() + "," + period2.toString();

      when(statutoryReportService.generateMultiPeriodReport(eq("B01"), anyList()))
          .thenReturn(createMockReport("B01", 2));

      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", periodIds))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    @DisplayName("ADMIN role should access multi-period report")
    void adminRoleShouldAccessMultiPeriodReport() throws Exception {
      UUID period1 = UUID.randomUUID();
      UUID period2 = UUID.randomUUID();
      String periodIds = period1.toString() + "," + period2.toString();

      when(statutoryReportService.generateMultiPeriodReport(eq("B01"), anyList()))
          .thenReturn(createMockReport("B01", 2));

      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", periodIds))
          .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = { "ACCOUNTANT" })
    @DisplayName("ACCOUNTANT role should be rejected for multi-period report")
    void accountantRoleShouldBeRejectedForMultiPeriodReport() throws Exception {
      UUID period1 = UUID.randomUUID();
      UUID period2 = UUID.randomUUID();
      String periodIds = period1.toString() + "," + period2.toString();

      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", periodIds))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated user should be rejected")
    void unauthenticatedUserShouldBeRejected() throws Exception {
      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", UUID.randomUUID().toString()))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("Period Validation Tests")
  class PeriodValidationTests {

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("Should accept valid 4 periods")
    void shouldAcceptValid4Periods() throws Exception {
      UUID period1 = UUID.randomUUID();
      UUID period2 = UUID.randomUUID();
      UUID period3 = UUID.randomUUID();
      UUID period4 = UUID.randomUUID();
      String periodIds = String.join(",", period1.toString(), period2.toString(),
          period3.toString(), period4.toString());

      when(statutoryReportService.generateMultiPeriodReport(eq("B01"), anyList()))
          .thenReturn(createMockReport("B01", 4));

      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", periodIds))
          .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("Should accept single period")
    void shouldAcceptSinglePeriod() throws Exception {
      UUID period1 = UUID.randomUUID();

      when(statutoryReportService.generateMultiPeriodReport(eq("B01"), anyList()))
          .thenReturn(createMockReport("B01", 1));

      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", period1.toString()))
          .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("Should return response with multiple periods and variances")
    void shouldReturnResponseWithMultiplePeriodsAndVariances() throws Exception {
      UUID period1 = UUID.randomUUID();
      UUID period2 = UUID.randomUUID();
      UUID period3 = UUID.randomUUID();
      String periodIds = String.join(",", period1.toString(), period2.toString(), period3.toString());

      when(statutoryReportService.generateMultiPeriodReport(eq("B01"), anyList()))
          .thenReturn(createMockReport("B01", 3));

      MvcResult result = mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", periodIds))
          .andExpect(status().isOk())
          .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      MultiPeriodReportDTO response = objectMapper.readValue(responseBody, MultiPeriodReportDTO.class);

      assertThat(response.periods()).hasSize(3);
      assertThat(response.lines()).isNotEmpty();
      assertThat(response.lines().get(0).variances()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("Comparison Settings Endpoint RBAC Tests")
  class ComparisonSettingsRbacTests {

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("CFO should GET comparison settings")
    void cfoShouldGetComparisonSettings() throws Exception {
      CompanySettings settings = new CompanySettings();
      settings.setCompanyId(COMPANY_ID);
      when(companySettingsRepository.findByCompanyId(anyLong()))
          .thenReturn(Optional.of(settings));

      mockMvc.perform(get("/api/settings/comparison"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(roles = { "CHIEF_ACCOUNTANT" })
    @DisplayName("CHIEF_ACCOUNTANT should GET comparison settings")
    void chiefAccountantShouldGetComparisonSettings() throws Exception {
      CompanySettings settings = new CompanySettings();
      settings.setCompanyId(COMPANY_ID);
      when(companySettingsRepository.findByCompanyId(anyLong()))
          .thenReturn(Optional.of(settings));

      mockMvc.perform(get("/api/settings/comparison"))
          .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = { "AUDITOR" })
    @DisplayName("AUDITOR should GET comparison settings")
    void auditorShouldGetComparisonSettings() throws Exception {
      CompanySettings settings = new CompanySettings();
      settings.setCompanyId(COMPANY_ID);
      when(companySettingsRepository.findByCompanyId(anyLong()))
          .thenReturn(Optional.of(settings));

      mockMvc.perform(get("/api/settings/comparison"))
          .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = { "ACCOUNTANT" })
    @DisplayName("ACCOUNTANT should GET comparison settings")
    void accountantShouldGetComparisonSettings() throws Exception {
      CompanySettings settings = new CompanySettings();
      settings.setCompanyId(COMPANY_ID);
      when(companySettingsRepository.findByCompanyId(anyLong()))
          .thenReturn(Optional.of(settings));

      mockMvc.perform(get("/api/settings/comparison"))
          .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    @DisplayName("ADMIN should PUT comparison settings")
    void adminShouldPutComparisonSettings() throws Exception {
      CompanySettings settings = new CompanySettings();
      settings.setCompanyId(COMPANY_ID);
      when(companySettingsRepository.findByCompanyId(anyLong()))
          .thenReturn(Optional.of(settings));
      when(companySettingsRepository.save(any(CompanySettings.class)))
          .thenReturn(settings);

      ComparisonSettingsDTO newSettings = new ComparisonSettingsDTO(
          15.0, new BigDecimal("2000000"), "MOM", true, true);

      mockMvc.perform(put("/api/settings/comparison")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(newSettings)))
          .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("CFO should be rejected for PUT comparison settings")
    void cfoShouldBeRejectedForPutComparisonSettings() throws Exception {
      ComparisonSettingsDTO newSettings = new ComparisonSettingsDTO(
          15.0, new BigDecimal("2000000"), "MOM", true, true);

      mockMvc.perform(put("/api/settings/comparison")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(newSettings)))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "CHIEF_ACCOUNTANT" })
    @DisplayName("CHIEF_ACCOUNTANT should be rejected for PUT comparison settings")
    void chiefAccountantShouldBeRejectedForPutComparisonSettings() throws Exception {
      ComparisonSettingsDTO newSettings = new ComparisonSettingsDTO(
          15.0, new BigDecimal("2000000"), "MOM", true, true);

      mockMvc.perform(put("/api/settings/comparison")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(newSettings)))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "AUDITOR" })
    @DisplayName("AUDITOR should be rejected for PUT comparison settings")
    void auditorShouldBeRejectedForPutComparisonSettings() throws Exception {
      ComparisonSettingsDTO newSettings = new ComparisonSettingsDTO(
          15.0, new BigDecimal("2000000"), "MOM", true, true);

      mockMvc.perform(put("/api/settings/comparison")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(newSettings)))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "ACCOUNTANT" })
    @DisplayName("ACCOUNTANT should be rejected for PUT comparison settings")
    void accountantShouldBeRejectedForPutComparisonSettings() throws Exception {
      ComparisonSettingsDTO newSettings = new ComparisonSettingsDTO(
          15.0, new BigDecimal("2000000"), "MOM", true, true);

      mockMvc.perform(put("/api/settings/comparison")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(newSettings)))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Response Format Tests")
  class ResponseFormatTests {

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("Should return correct report structure")
    void shouldReturnCorrectReportStructure() throws Exception {
      UUID period1 = UUID.randomUUID();
      UUID period2 = UUID.randomUUID();
      String periodIds = period1.toString() + "," + period2.toString();

      when(statutoryReportService.generateMultiPeriodReport(eq("B01"), anyList()))
          .thenReturn(createMockReport("B01", 2));

      MvcResult result = mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", periodIds))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.reportType").value("B01"))
          .andExpect(jsonPath("$.companyId").value(COMPANY_ID))
          .andExpect(jsonPath("$.periods").isArray())
          .andExpect(jsonPath("$.lines").isArray())
          .andExpect(jsonPath("$.settings").exists())
          .andExpect(jsonPath("$.hasDraftPeriod").value(false))
          .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      MultiPeriodReportDTO response = objectMapper.readValue(responseBody, MultiPeriodReportDTO.class);

      assertThat(response.reportName()).isEqualTo("Balance Sheet");
      assertThat(response.companyName()).isEqualTo("Test Company");
      assertThat(response.generatedAt()).isNotNull();
    }

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("Should return settings with correct defaults")
    void shouldReturnSettingsWithCorrectDefaults() throws Exception {
      UUID period1 = UUID.randomUUID();
      String periodIds = period1.toString();

      when(statutoryReportService.generateMultiPeriodReport(eq("B01"), anyList()))
          .thenReturn(createMockReport("B01", 1));

      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", periodIds))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.settings.varianceThresholdPercent").value(10.0))
          .andExpect(jsonPath("$.settings.varianceThresholdAbsolute").value(1000000))
          .andExpect(jsonPath("$.settings.defaultComparisonMode").value("YOY"))
          .andExpect(jsonPath("$.settings.showSparklines").value(true))
          .andExpect(jsonPath("$.settings.hideImmaterialDefault").value(false));
    }

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("Should return line with variance data")
    void shouldReturnLineWithVarianceData() throws Exception {
      UUID period1 = UUID.randomUUID();
      UUID period2 = UUID.randomUUID();
      String periodIds = period1.toString() + "," + period2.toString();

      when(statutoryReportService.generateMultiPeriodReport(eq("B01"), anyList()))
          .thenReturn(createMockReport("B01", 2));

      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", periodIds))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.lines[0].lineCode").value("110"))
          .andExpect(jsonPath("$.lines[0].lineName").value("Cash"))
          .andExpect(jsonPath("$.lines[0].periodValues").exists())
          .andExpect(jsonPath("$.lines[0].variances").isArray())
          .andExpect(jsonPath("$.lines[0].sparklineData").isArray())
          .andExpect(jsonPath("$.lines[0].isMaterial").value(false))
          .andExpect(jsonPath("$.lines[0].hasDrillDown").value(true));
    }
  }

  @Nested
  @DisplayName("Report Type Tests")
  class ReportTypeTests {

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("Should accept B01 report type")
    void shouldAcceptB01ReportType() throws Exception {
      UUID period1 = UUID.randomUUID();

      when(statutoryReportService.generateMultiPeriodReport(eq("B01"), anyList()))
          .thenReturn(createMockReport("B01", 1));

      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B01")
          .param("periodIds", period1.toString()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.reportType").value("B01"));
    }

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("Should accept B02 report type")
    void shouldAcceptB02ReportType() throws Exception {
      UUID period1 = UUID.randomUUID();

      when(statutoryReportService.generateMultiPeriodReport(eq("B02"), anyList()))
          .thenReturn(createMockReport("B02", 1));

      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B02")
          .param("periodIds", period1.toString()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.reportType").value("B02"));
    }

    @Test
    @WithMockUser(roles = { "CFO" })
    @DisplayName("Should accept B03 report type")
    void shouldAcceptB03ReportType() throws Exception {
      UUID period1 = UUID.randomUUID();

      when(statutoryReportService.generateMultiPeriodReport(eq("B03"), anyList()))
          .thenReturn(createMockReport("B03", 1));

      mockMvc.perform(get("/api/reports/statutory/multi-period")
          .param("reportType", "B03")
          .param("periodIds", period1.toString()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.reportType").value("B03"));
    }
  }
}
