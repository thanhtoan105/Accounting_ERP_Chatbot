package com.accounting.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.GenerateStatementRequest;
import com.accounting.entity.*;
import com.accounting.repository.*;
import com.accounting.security.CompanyContext;
import com.accounting.security.PasswordEncoder;
import com.accounting.service.AuditService;
import com.accounting.test.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for Supplier Statement endpoints.
 * Tests the full flow from HTTP request to database and back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
class SupplierStatementIntegrationTest extends IntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private CompanyRepository companyRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private SupplierRepository supplierRepository;

        @Autowired
        private PurchaseBillRepository purchaseBillRepository;

        @Autowired
        private APPaymentRepository apPaymentRepository;

        @Autowired
        private PaymentAllocationRepository paymentAllocationRepository;

        @Autowired
        private SupplierStatementHistoryRepository historyRepository;

        @Autowired
        private SupplierStatementDisputeRepository disputeRepository;

        @SpyBean
        private AuditService auditService;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private ObjectMapper objectMapper;

        private Long testCompanyId;
        private Long testUserId;
        private Long testSupplierId;
        private PurchaseBill testBill;
        private LocalDate startDate;
        private LocalDate endDate;
        private User testUser;

        @BeforeEach
        void setUp() {
                CompanyContext.setCompanyId(null);

                // Create test company
                Company company = new Company();
                company.setCode("TEST001");
                company.setName("Test Company");
                company.setTaxCode("1234567890");
                company.setAddress("Test Address");
                company = companyRepository.save(company);
                testCompanyId = company.getId();
                CompanyContext.setCompanyId(testCompanyId);

                // Create test user
                testUser = new User();
                testUser.setEmail("test@example.com");
                testUser.setPasswordHash(passwordEncoder.encode("Password123!"));
                testUser.setFullName("Test User");
                testUser.setCompanyId(testCompanyId);
                testUser.setRole("chief_accountant");
                testUser.setStatus("ACTIVE");
                testUser.setCreatedAt(Instant.now());
                testUser.setUpdatedAt(Instant.now());
                testUser = userRepository.save(testUser);
                testUserId = testUser.getId();

                // Create test supplier
                Supplier supplier = new Supplier();
                supplier.setCompanyId(testCompanyId);
                supplier.setCode("SUP001");
                supplier.setName("Test Supplier");
                supplier.setTaxCode("9876543210");
                supplier = supplierRepository.save(supplier);
                testSupplierId = supplier.getId();

                // Create test bill
                startDate = LocalDate.now().minusMonths(1);
                endDate = LocalDate.now();
                testBill = new PurchaseBill();
                testBill.setCompanyId(testCompanyId);
                testBill.setSupplierId(testSupplierId);
                testBill.setBillNumber("BILL-001");
                testBill.setBillDate(startDate.plusDays(5));
                testBill.setDueDate(startDate.plusDays(35));
                testBill.setTotalAmount(new BigDecimal("1000.00"));
                testBill.setStatus(PurchaseBillStatus.POSTED);
                testBill.setReference("REF-001");
                testBill.setDescription("Test bill");
                testBill = purchaseBillRepository.save(testBill);
        }

        @AfterEach
        void tearDown() {
                CompanyContext.clear();
        }

        @Test
        void generateStatement_withValidRequest_returnsStatement() throws Exception {
                // Setup
                GenerateStatementRequest request = new GenerateStatementRequest();
                request.setSupplierId(testSupplierId);
                request.setStatementType(SupplierStatementHistory.StatementType.SUMMARY);
                request.setStartDate(startDate);
                request.setEndDate(endDate);
                request.setFormat(SupplierStatementHistory.ExportFormat.EXCEL);

                // Execute
                mockMvc
                                .perform(
                                                post("/api/v1/supplier-statements/generate")
                                                                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT"))
                                                                .header("X-Company-Id", testCompanyId.toString())
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.supplierId").value(testSupplierId))
                                .andExpect(jsonPath("$.statementType").value("SUMMARY"))
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.openingBalance").exists())
                                .andExpect(jsonPath("$.closingBalance").exists());

                // Verify history was saved
                List<SupplierStatementHistory> history = historyRepository
                                .findByCompanyId(testCompanyId, org.springframework.data.domain.PageRequest.of(0, 10))
                                .getContent();
                assertThat(history).hasSize(1);
                assertThat(history.get(0).getSupplierId()).isEqualTo(testSupplierId);
                assertThat(history.get(0).getStatementType())
                                .isEqualTo(SupplierStatementHistory.StatementType.SUMMARY);

                // Verify audit logging
                verify(auditService, times(1))
                                .logStatementGenerated(
                                                eq(testCompanyId),
                                                eq(testUserId),
                                                any(UUID.class),
                                                eq(testSupplierId),
                                                eq("SUMMARY"),
                                                any());
        }

        @Test
        void generateDetailedStatement_withValidRequest_returnsDetailedStatement() throws Exception {
                // Setup
                GenerateStatementRequest request = new GenerateStatementRequest();
                request.setSupplierId(testSupplierId);
                request.setStatementType(SupplierStatementHistory.StatementType.DETAILED);
                request.setStartDate(startDate);
                request.setEndDate(endDate);
                request.setFormat(SupplierStatementHistory.ExportFormat.EXCEL);

                // Execute
                mockMvc
                                .perform(
                                                post("/api/v1/supplier-statements/generate")
                                                                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT"))
                                                                .header("X-Company-Id", testCompanyId.toString())
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.supplierId").value(testSupplierId))
                                .andExpect(jsonPath("$.statementType").value("DETAILED"))
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.billDetails").exists())
                                .andExpect(jsonPath("$.billDetails").isArray());

                // Verify history was saved
                List<SupplierStatementHistory> history = historyRepository
                                .findByCompanyId(testCompanyId, org.springframework.data.domain.PageRequest.of(0, 10))
                                .getContent();
                assertThat(history).hasSize(1);
                assertThat(history.get(0).getStatementType())
                                .isEqualTo(SupplierStatementHistory.StatementType.DETAILED);
        }

        @Test
        void exportStatement_withValidId_returnsExcelFile() throws Exception {
                // Setup - Create statement history first
                SupplierStatementHistory history = new SupplierStatementHistory();
                history.setCompanyId(testCompanyId);
                history.setSupplierId(testSupplierId);
                history.setStatementTypeEnum(SupplierStatementHistory.StatementType.SUMMARY);
                history.setGenerationDate(Instant.now());
                history.setGeneratedById(testUserId);
                history.setExportFormatEnum(SupplierStatementHistory.ExportFormat.EXCEL);
                history.setHash("test-hash");
                history.setStartDate(startDate);
                history.setEndDate(endDate);
                history.setViewCount(0);
                history.setDownloadCount(0);
                history = historyRepository.save(history);

                // Execute
                mockMvc
                                .perform(
                                                get("/api/v1/supplier-statements/{id}/export", history.getId())
                                                                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT"))
                                                                .header("X-Company-Id", testCompanyId.toString())
                                                                .param("format", "EXCEL"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                .andExpect(header().exists("Content-Disposition"));

                // Verify download count was incremented
                SupplierStatementHistory updatedHistory = historyRepository.findById(history.getId()).orElseThrow();
                assertThat(updatedHistory.getDownloadCount()).isEqualTo(1);

                // Verify audit logging
                verify(auditService, times(1))
                                .logStatementExported(
                                                eq(testCompanyId), eq(testUserId), eq(history.getId()), eq("EXCEL"),
                                                any());
        }

        @Test
        void listStatements_withFilters_returnsFilteredResults() throws Exception {
                // Setup - Create multiple statement histories
                SupplierStatementHistory history1 = createStatementHistory(
                                SupplierStatementHistory.StatementType.SUMMARY);
                SupplierStatementHistory history2 = createStatementHistory(
                                SupplierStatementHistory.StatementType.DETAILED);
                historyRepository.saveAll(List.of(history1, history2));

                // Execute
                mockMvc
                                .perform(
                                                get("/api/v1/supplier-statements")
                                                                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT"))
                                                                .header("X-Company-Id", testCompanyId.toString())
                                                                .param("supplier", String.valueOf(testSupplierId))
                                                                .param("statementType", "SUMMARY")
                                                                .param("page", "0")
                                                                .param("size", "20"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.statements").isArray())
                                .andExpect(jsonPath("$.statements.length()").value(1))
                                .andExpect(jsonPath("$.statements[0].statementType").value("SUMMARY"));
        }

        @Test
        void sendStatementToSupplier_updatesHistory() throws Exception {
                // Setup
                SupplierStatementHistory history = createStatementHistory(
                                SupplierStatementHistory.StatementType.SUMMARY);
                history = historyRepository.save(history);
                List<String> recipientEmails = List.of("supplier@example.com");

                // Execute
                mockMvc
                                .perform(
                                                post("/api/v1/supplier-statements/{id}/send", history.getId())
                                                                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT"))
                                                                .header("X-Company-Id", testCompanyId.toString())
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(objectMapper
                                                                                .writeValueAsString(recipientEmails)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Statement sent successfully"));

                // Verify history was updated
                SupplierStatementHistory updatedHistory = historyRepository.findById(history.getId()).orElseThrow();
                assertThat(updatedHistory.getSentDate()).isNotNull();
                assertThat(updatedHistory.getSentTo()).isNotNull();

                // Verify audit logging
                verify(auditService, times(1))
                                .logStatementSent(
                                                eq(testCompanyId), eq(testUserId), eq(history.getId()), eq(1), any());
        }

        @Test
        void updateDispute_updatesStatusAndLogs() throws Exception {
                // Setup - Create dispute
                SupplierStatementDispute dispute = new SupplierStatementDispute();
                dispute.setCompanyId(testCompanyId);
                dispute.setSupplierId(testSupplierId);
                dispute.setBillId(testBill.getId());
                dispute.setBillNumber(testBill.getBillNumber());
                dispute.setDisputeReason("Amount mismatch");
                dispute.setStatusEnum(SupplierStatementDispute.DisputeStatus.OPEN);
                dispute.setCreatedBy(testUserId);
                dispute.setCreatedAt(Instant.now());
                dispute = disputeRepository.save(dispute);

                // Setup - Update request
                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("status", "RESOLVED");
                updateRequest.put("resolutionNotes", "Resolved by adjusting amount");

                // Execute
                mockMvc
                                .perform(
                                                put("/api/v1/supplier-statements/disputes/{id}", dispute.getId())
                                                                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT"))
                                                                .header("X-Company-Id", testCompanyId.toString())
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(objectMapper
                                                                                .writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Dispute updated successfully"))
                                .andExpect(jsonPath("$.status").value("RESOLVED"));

                // Verify dispute was updated
                SupplierStatementDispute updatedDispute = disputeRepository.findById(dispute.getId()).orElseThrow();
                assertThat(updatedDispute.getStatusEnum()).isEqualTo(SupplierStatementDispute.DisputeStatus.RESOLVED);
                assertThat(updatedDispute.getResolvedBy()).isEqualTo(testUserId);
                assertThat(updatedDispute.getResolvedAt()).isNotNull();

                // Verify audit logging
                verify(auditService, times(1))
                                .logDisputeUpdated(
                                                eq(testCompanyId),
                                                eq(testUserId),
                                                eq(dispute.getId()),
                                                eq("OPEN"),
                                                eq("RESOLVED"),
                                                any());
        }

        @Test
        void listDisputes_withFilters_returnsFilteredResults() throws Exception {
                // Setup - Create disputes
                SupplierStatementDispute dispute1 = new SupplierStatementDispute();
                dispute1.setCompanyId(testCompanyId);
                dispute1.setSupplierId(testSupplierId);
                dispute1.setDisputeReason("Mismatch 1");
                dispute1.setStatusEnum(SupplierStatementDispute.DisputeStatus.OPEN);
                dispute1.setCreatedBy(testUserId);
                dispute1.setCreatedAt(Instant.now());

                SupplierStatementDispute dispute2 = new SupplierStatementDispute();
                dispute2.setCompanyId(testCompanyId);
                dispute2.setSupplierId(testSupplierId);
                dispute2.setDisputeReason("Mismatch 2");
                dispute2.setStatusEnum(SupplierStatementDispute.DisputeStatus.RESOLVED);
                dispute2.setCreatedBy(testUserId);
                dispute2.setCreatedAt(Instant.now());
                disputeRepository.saveAll(List.of(dispute1, dispute2));

                // Execute
                mockMvc
                                .perform(
                                                get("/api/v1/supplier-statements/disputes")
                                                                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT"))
                                                                .header("X-Company-Id", testCompanyId.toString())
                                                                .param("status", "OPEN")
                                                                .param("page", "0")
                                                                .param("size", "20"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.disputes").isArray())
                                .andExpect(jsonPath("$.disputes.length()").value(1))
                                .andExpect(jsonPath("$.disputes[0].status").value("OPEN"));
        }

        // Helper methods
        private SupplierStatementHistory createStatementHistory(
                        SupplierStatementHistory.StatementType statementType) {
                SupplierStatementHistory history = new SupplierStatementHistory();
                history.setCompanyId(testCompanyId);
                history.setSupplierId(testSupplierId);
                history.setStatementTypeEnum(statementType);
                history.setGenerationDate(Instant.now());
                history.setGeneratedById(testUserId);
                history.setExportFormatEnum(SupplierStatementHistory.ExportFormat.EXCEL);
                history.setHash("test-hash-" + UUID.randomUUID());
                history.setStartDate(startDate);
                history.setEndDate(endDate);
                history.setViewCount(0);
                history.setDownloadCount(0);
                return history;
        }

        private RequestPostProcessor authenticatedUser(Long userId, String... roles) {
                List<GrantedAuthority> authorities = Arrays.stream(roles)
                                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                                .map(SimpleGrantedAuthority::new)
                                .collect(java.util.stream.Collectors.toList());
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                String.valueOf(userId), "password", authorities);
                return SecurityMockMvcRequestPostProcessors.authentication(authentication);
        }
}
