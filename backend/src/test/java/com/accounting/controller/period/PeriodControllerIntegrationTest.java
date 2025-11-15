package com.accounting.controller.period;

import com.accounting.dto.PeriodCloseRequest;
import com.accounting.dto.PeriodReopenRequest;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.PeriodStatus;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.Instant;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
public class PeriodControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockBean
    private AccountingPeriodRepository periodRepository;

    @MockBean
    private VoucherRepository voucherRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private AccountingPeriod testPeriod;
    private AccountingPeriod testClosedPeriod;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Setup test data
        testPeriod = new AccountingPeriod();
        testPeriod.setId(UUID.randomUUID());
        testPeriod.setCompanyId(1L);
        testPeriod.setFiscalYear(2025);
        testPeriod.setPeriodNumber(1);
        testPeriod.setPeriodName("January 2025");
        testPeriod.setStartDate(LocalDate.of(2025, Month.JANUARY, 1));
        testPeriod.setEndDate(LocalDate.of(2025, Month.JANUARY, 31));
        testPeriod.setStatus(PeriodStatus.OPEN);
        testPeriod.setCreatedAt(Instant.now());
        testPeriod.setUpdatedAt(Instant.now());
        testPeriod.setVersion(0L);

        testClosedPeriod = new AccountingPeriod();
        testClosedPeriod.setId(UUID.randomUUID());
        testClosedPeriod.setCompanyId(1L);
        testClosedPeriod.setFiscalYear(2024);
        testClosedPeriod.setPeriodNumber(12);
        testClosedPeriod.setPeriodName("December 2024");
        testClosedPeriod.setStartDate(LocalDate.of(2024, Month.DECEMBER, 1));
        testClosedPeriod.setEndDate(LocalDate.of(2024, Month.DECEMBER, 31));
        testClosedPeriod.setStatus(PeriodStatus.CLOSED);
        testClosedPeriod.setClosedBy(1L);
        testClosedPeriod.setClosedAt(Instant.now());
        testClosedPeriod.setCloseReason("Month end closing");
        testClosedPeriod.setCreatedAt(Instant.now());
        testClosedPeriod.setUpdatedAt(Instant.now());
        testClosedPeriod.setVersion(1L);

        // Mock CompanyContext
        CompanyContext.setCompanyId(1L);
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT"})
    void getCurrentPeriod_WithExistingPeriod_ReturnsPeriod() throws Exception {
        // Given
        when(periodRepository.findCurrentPeriodByCompanyId(1L))
                .thenReturn(Optional.of(testPeriod));

        // When & Then
        mockMvc.perform(get("/api/v1/periods/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testPeriod.getId().toString()))
                .andExpect(jsonPath("$.data.periodName").value("January 2025"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT"})
    void getCurrentPeriod_NoPeriodFound_ReturnsNotFound() throws Exception {
        // Given
        when(periodRepository.findCurrentPeriodByCompanyId(1L))
                .thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/periods/current"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT"})
    void getOpenPeriods_WithOpenPeriods_ReturnsPeriods() throws Exception {
        // Given
        List<AccountingPeriod> openPeriods = List.of(testPeriod);
        LocalDate currentDate = LocalDate.now();
        when(periodRepository.findOpenPeriodsAroundDate(1L, PeriodStatus.OPEN, currentDate))
                .thenReturn(openPeriods);

        // When & Then
        mockMvc.perform(get("/api/v1/periods/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].periodName").value("January 2025"))
                .andExpect(jsonPath("$.meta.count").value(1));
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT"})
    void getPeriodById_WithValidPeriod_ReturnsPeriod() throws Exception {
        // Given
        when(periodRepository.findByCompanyIdAndId(1L, testPeriod.getId()))
                .thenReturn(Optional.of(testPeriod));

        // When & Then
        mockMvc.perform(get("/api/v1/periods/{periodId}", testPeriod.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testPeriod.getId().toString()))
                .andExpect(jsonPath("$.data.periodName").value("January 2025"));
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT"})
    void getPeriodById_PeriodNotFound_ReturnsNotFound() throws Exception {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(periodRepository.findByCompanyIdAndId(1L, nonExistentId))
                .thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/periods/{periodId}", nonExistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT"})
    void checkPeriodOpen_OpenPeriod_ReturnsTrue() throws Exception {
        // Given
        when(periodRepository.findByCompanyIdAndId(1L, testPeriod.getId()))
                .thenReturn(Optional.of(testPeriod));

        // When & Then
        mockMvc.perform(get("/api/v1/periods/{periodId}/check-open", testPeriod.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isOpen").value(true));
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT"})
    void checkPeriodOpen_ClosedPeriod_ReturnsFalse() throws Exception {
        // Given
        when(periodRepository.findByCompanyIdAndId(1L, testClosedPeriod.getId()))
                .thenReturn(Optional.of(testClosedPeriod));

        // When & Then
        mockMvc.perform(get("/api/v1/periods/{periodId}/check-open", testClosedPeriod.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isOpen").value(false));
    }

    @Test
    @WithMockUser(roles = {"CHIEF_ACCOUNTANT"})
    void closePeriod_ValidRequest_ReturnsClosedPeriod() throws Exception {
        // Given
        PeriodCloseRequest closeRequest = new PeriodCloseRequest();
        closeRequest.setReason("Month end closing");

        when(periodRepository.findByCompanyIdAndId(1L, testPeriod.getId()))
                .thenReturn(Optional.of(testPeriod));
        when(periodRepository.countDraftVouchersInPeriod(1L, testPeriod.getId()))
                .thenReturn(0L); // No draft vouchers
        when(voucherRepository.findByCompanyIdAndPeriodId(1L, testPeriod.getId()))
                .thenReturn(List.of()); // No vouchers

        // When & Then
        mockMvc.perform(post("/api/v1/periods/{periodId}/close", testPeriod.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(closeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.meta.message").value("Period closed successfully"));
    }

    @Test
    @WithMockUser(roles = {"CHIEF_ACCOUNTANT"})
    void closePeriod_PeriodAlreadyClosed_ReturnsConflict() throws Exception {
        // Given
        PeriodCloseRequest closeRequest = new PeriodCloseRequest();
        closeRequest.setReason("Already closed");

        when(periodRepository.findByCompanyIdAndId(1L, testClosedPeriod.getId()))
                .thenReturn(Optional.of(testClosedPeriod));

        // When & Then
        mockMvc.perform(post("/api/v1/periods/{periodId}/close", testClosedPeriod.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(closeRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = {"CHIEF_ACCOUNTANT"})
    void closePeriod_InsufficientRole_ReturnsForbidden() throws Exception {
        // Test with role that doesn't have permission
        mockMvc.perform(post("/api/v1/periods/{periodId}/close", testPeriod.getId())
                .with(csrf())
                .with(user("user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"CHIEF_ACCOUNTANT"})
    void reopenPeriod_ValidRequest_ReturnsReopenedPeriod() throws Exception {
        // Given
        PeriodReopenRequest reopenRequest = new PeriodReopenRequest();
        reopenRequest.setReason("Correction needed");
        reopenRequest.setApprovalMetadata("Approved by CFO");

        when(periodRepository.findByCompanyIdAndId(1L, testClosedPeriod.getId()))
                .thenReturn(Optional.of(testClosedPeriod));

        // When & Then
        mockMvc.perform(post("/api/v1/periods/{periodId}/reopen", testClosedPeriod.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reopenRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.meta.message").value("Period reopened successfully"));
    }

    @Test
    @WithMockUser(roles = {"CHIEF_ACCOUNTANT"})
    void reopenPeriod_OpenPeriod_ReturnsBadRequest() throws Exception {
        // Given
        PeriodReopenRequest reopenRequest = new PeriodReopenRequest();
        reopenRequest.setReason("Already open");

        when(periodRepository.findByCompanyIdAndId(1L, testPeriod.getId()))
                .thenReturn(Optional.of(testPeriod));

        // When & Then
        mockMvc.perform(post("/api/v1/periods/{periodId}/reopen", testPeriod.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reopenRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT"})
    void getPeriodSummary_ValidPeriod_ReturnsSummary() throws Exception {
        // Given
        when(periodRepository.findByCompanyIdAndId(1L, testPeriod.getId()))
                .thenReturn(Optional.of(testPeriod));
        when(periodRepository.countDraftVouchersInPeriod(1L, testPeriod.getId()))
                .thenReturn(5L);
        when(voucherRepository.findByCompanyIdAndPeriodId(1L, testPeriod.getId()))
                .thenReturn(List.of()); // No vouchers for simplicity
        when(periodRepository.findCurrentPeriodByCompanyId(1L))
                .thenReturn(Optional.of(testPeriod));

        // When & Then
        mockMvc.perform(get("/api/v1/periods/{periodId}/summary", testPeriod.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodName").value("January 2025"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.draftVouchersCount").value(5))
                .andExpect(jsonPath("$.data.postingFlowStatus").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT"})
    void findPeriodByDate_ValidDate_ReturnsPeriod() throws Exception {
        // Given
        LocalDate date = LocalDate.of(2025, Month.JANUARY, 15);
        when(periodRepository.findByCompanyIdAndDate(1L, date))
                .thenReturn(Optional.of(testPeriod));

        // When & Then
        mockMvc.perform(get("/api/v1/periods/find-by-date")
                .param("date", "2025-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodName").value("January 2025"));
    }

    @Test
    @WithMockUser(roles = {"ACCOUNTANT"})
    void checkDateInOpenPeriod_OpenDate_ReturnsTrue() throws Exception {
        // Given
        when(periodRepository.findByCompanyIdAndDate(1L, LocalDate.of(2025, Month.JANUARY, 15)))
                .thenReturn(Optional.of(testPeriod));

        // When & Then
        mockMvc.perform(get("/api/v1/periods/check-date-open")
                .param("date", "2025-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDateInOpenPeriod").value(true));
    }

    @Test
    @WithMockUser(roles = {"USER"}) // User without required role
    void getOpenPeriods_InsufficientRole_ReturnsForbidden() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/periods/open"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOpenPeriods_NoAuthentication_ReturnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/periods/open"))
                .andExpect(status().isUnauthorized());
    }
}