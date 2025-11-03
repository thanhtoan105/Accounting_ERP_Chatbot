package com.accounting.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void createCustomer_ok_whenCompanyMatchesHeader() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("companyId", 10);
    body.put("code", "CUST-1");
    body.put("name", "Khach Hang 1");
    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Company-Id", "10")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());
  }

  @Test
  void createCustomer_forbidden_whenCompanyMismatch() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("companyId", 1);
    body.put("code", "CUST-2");
    body.put("name", "Khach Hang 2");
    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Company-Id", "2")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
  }
}


