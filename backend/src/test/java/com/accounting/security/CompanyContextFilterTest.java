package com.accounting.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CompanyContextFilterTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void setsCompanyIdWhenHeaderPresent() throws Exception {
    mockMvc
        .perform(get("/api/v1/_context/company").header("X-Company-Id", "123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyId").value(123));
  }

  @Test
  void returnsBadRequestForInvalidHeader() throws Exception {
    mockMvc
        .perform(get("/api/v1/_context/company").header("X-Company-Id", "abc"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void returnsNullWhenHeaderMissing() throws Exception {
    mockMvc
        .perform(get("/api/v1/_context/company"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyId").doesNotExist());
  }
}
