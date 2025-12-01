package com.accounting.controller.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accounting.dto.TrialBalanceResponseDTO;
import com.accounting.service.TrialBalanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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

@SpringBootTest
@AutoConfigureMockMvc
class TrialBalanceControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private TrialBalanceService trialBalanceService;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  @WithMockUser(roles = {"CHIEF_ACCOUNTANT"})
  void getTrialBalance_asChiefAccountant_ok() throws Exception {
    UUID periodId = UUID.randomUUID();
    TrialBalanceResponseDTO mockData = new TrialBalanceResponseDTO();
    Mockito.when(trialBalanceService.getTrialBalanceData(periodId)).thenReturn(mockData);

    MvcResult result = mockMvc
        .perform(get("/api/v1/reports/trial-balance")
            .param("periodId", periodId.toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    TrialBalanceResponseDTO response = objectMapper.readValue(responseBody, TrialBalanceResponseDTO.class);
    assertThat(response).isNotNull();
  }

  @Test
  @WithMockUser(roles = {"ACCOUNTANT"})
  void getTrialBalance_asNonPrivileged_forbidden() throws Exception {
    UUID periodId = UUID.randomUUID();
    mockMvc
        .perform(get("/api/v1/reports/trial-balance")
            .param("periodId", periodId.toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {"CHIEF_ACCOUNTANT"})
  void exportTrialBalance_asChiefAccountant_ok() throws Exception {
    UUID periodId = UUID.randomUUID();
    byte[] mockExcel = new byte[]{0x50, 0x4B, 0x03, 0x04}; // ZIP header (Excel file)
    Mockito.when(trialBalanceService.exportToExcel(periodId)).thenReturn(mockExcel);

    MvcResult result = mockMvc
        .perform(get("/api/v1/reports/trial-balance/export")
            .param("periodId", periodId.toString())
            .param("format", "xlsx"))
        .andExpect(status().isOk())
        .andReturn();

    byte[] bytes = result.getResponse().getContentAsByteArray();
    assertThat(bytes.length).isGreaterThan(0);
    assertThat(bytes[0]).isEqualTo((byte) 0x50); // ZIP header
  }
}

