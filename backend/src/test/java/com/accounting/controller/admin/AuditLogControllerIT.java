package com.accounting.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accounting.entity.AuditLog;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogControllerIT extends com.accounting.test.IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Company testCompany;
    private Company otherCompany;
    private User adminUser;
    private User chiefAccountantUser;
    private User accountantUser;
    private String adminToken;
    private String chiefAccountantToken;
    private String accountantToken;

    @BeforeEach
    void setUp() {
        // IntegrationTest base class resets the database, so we just need to clean up
        // audit logs
        auditLogRepository.deleteAll();

        // Create test companies
        testCompany = new Company();
        testCompany.setCode("TEST");
        testCompany.setName("Test Company");
        testCompany.setTaxCode("1234567890");
        testCompany.setAddress("Test Address");
        testCompany = companyRepository.save(testCompany);

        otherCompany = new Company();
        otherCompany.setCode("OTHER");
        otherCompany.setName("Other Company");
        otherCompany.setTaxCode("0987654321");
        otherCompany.setAddress("Other Address");
        otherCompany = companyRepository.save(otherCompany);

        CompanyContext.setCompanyId(testCompany.getId());

        // Create admin user
        adminUser = new User();
        adminUser.setEmail("admin@test.com");
        adminUser.setFullName("Admin User");
        adminUser.setPasswordHash(passwordEncoder.encode("password"));
        adminUser.setRole("admin");
        adminUser.setCompanyId(testCompany.getId());
        adminUser.setCreatedAt(Instant.now());
        adminUser.setUpdatedAt(Instant.now());
        adminUser = userRepository.save(adminUser);
        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getEmail(), "admin");

        // Create chief accountant user
        chiefAccountantUser = new User();
        chiefAccountantUser.setEmail("chief@test.com");
        chiefAccountantUser.setFullName("Chief Accountant");
        chiefAccountantUser.setPasswordHash(passwordEncoder.encode("password"));
        chiefAccountantUser.setRole("chief_accountant");
        chiefAccountantUser.setCompanyId(testCompany.getId());
        chiefAccountantUser.setCreatedAt(Instant.now());
        chiefAccountantUser.setUpdatedAt(Instant.now());
        chiefAccountantUser = userRepository.save(chiefAccountantUser);
        chiefAccountantToken = jwtTokenProvider.generateAccessToken(chiefAccountantUser.getId(),
                chiefAccountantUser.getEmail(), "chief_accountant");

        // Create accountant user (should not have access)
        accountantUser = new User();
        accountantUser.setEmail("accountant@test.com");
        accountantUser.setFullName("Accountant");
        accountantUser.setPasswordHash(passwordEncoder.encode("password"));
        accountantUser.setRole("accountant");
        accountantUser.setCompanyId(testCompany.getId());
        accountantUser.setCreatedAt(Instant.now());
        accountantUser.setUpdatedAt(Instant.now());
        accountantUser = userRepository.save(accountantUser);
        accountantToken = jwtTokenProvider.generateAccessToken(accountantUser.getId(), accountantUser.getEmail(),
                "accountant");

        // Create test audit logs
        createTestAuditLogs();
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    // AC2: API integration tests confirm company scoping, RBAC enforcement, and
    // export payload metadata

    @Test
    void listAuditLogs_asAdmin_returnsOk() throws Exception {
        mockMvc
                .perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Company-Id", testCompany.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta").exists())
                .andExpect(jsonPath("$.meta.totalElements").value(3));
    }

    @Test
    void listAuditLogs_asChiefAccountant_returnsOk() throws Exception {
        mockMvc
                .perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + chiefAccountantToken)
                        .header("X-Company-Id", testCompany.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta").exists());
    }

    @Test
    void listAuditLogs_asAccountant_returnsForbidden() throws Exception {
        mockMvc
                .perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + accountantToken)
                        .header("X-Company-Id", testCompany.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAuditLogs_withoutAuth_returnsForbidden() throws Exception {
        // Spring Security may return 403 Forbidden instead of 401 Unauthorized
        // when authentication is missing but the endpoint requires it
        mockMvc
                .perform(get("/api/v1/admin/audit-logs")
                        .header("X-Company-Id", testCompany.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAuditLogs_companyScoping_onlyReturnsCurrentCompanyLogs() throws Exception {
        // Create audit log for other company
        AuditLog otherCompanyLog = new AuditLog();
        otherCompanyLog.setCompanyId(otherCompany.getId());
        otherCompanyLog.setAction("CREATE");
        otherCompanyLog.setEntityType("CUSTOMER");
        otherCompanyLog.setUserId(adminUser.getId());
        otherCompanyLog.setEmail(adminUser.getEmail());
        otherCompanyLog.setActorRole("admin");
        otherCompanyLog.setSuccess(true);
        otherCompanyLog.setCreatedAt(Instant.now());
        auditLogRepository.save(otherCompanyLog);

        mockMvc
                .perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Company-Id", testCompany.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(3)); // Only test company logs
    }

    @Test
    void listAuditLogs_withEntityTypeFilter_returnsFilteredResults() throws Exception {
        mockMvc
                .perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Company-Id", testCompany.getId().toString())
                        .param("entityType", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].entityType").value("CUSTOMER"));
    }

    @Test
    void listAuditLogs_withActionFilter_returnsFilteredResults() throws Exception {
        mockMvc
                .perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Company-Id", testCompany.getId().toString())
                        .param("action", "UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].action").value("UPDATE"));
    }

    @Test
    void listAuditLogs_withSuccessFilter_returnsFilteredResults() throws Exception {
        mockMvc
                .perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Company-Id", testCompany.getId().toString())
                        .param("success", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2)); // 2 successful logs
    }

    @Test
    void listAuditLogs_withPagination_returnsPaginatedResults() throws Exception {
        mockMvc
                .perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Company-Id", testCompany.getId().toString())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.totalElements").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void exportAuditLogs_asAdmin_returnsCsv() throws Exception {
        MvcResult result = mockMvc
                .perform(get("/api/v1/admin/audit-logs/export")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Company-Id", testCompany.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(header().exists("X-Content-SHA256"))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        // Check CSV header exists
        assertThat(content).contains("occurredAt");
        assertThat(content).contains("action");
        assertThat(content).contains("entityType");
        assertThat(content).contains("actorEmail");
        // Check data rows exist
        assertThat(content).contains("CREATE");
        assertThat(content).contains("UPDATE");
    }

    @Test
    void exportAuditLogs_withFilters_appliesFilters() throws Exception {
        MvcResult result = mockMvc
                .perform(get("/api/v1/admin/audit-logs/export")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Company-Id", testCompany.getId().toString())
                        .param("entityType", "CUSTOMER"))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).contains("CUSTOMER");
        // Should not contain SUPPLIER
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.contains("SUPPLIER")) {
                throw new AssertionError("Export should not contain SUPPLIER logs when filtered by CUSTOMER");
            }
        }
    }

    @Test
    void exportAuditLogs_asAccountant_returnsForbidden() throws Exception {
        mockMvc
                .perform(get("/api/v1/admin/audit-logs/export")
                        .header("Authorization", "Bearer " + accountantToken)
                        .header("X-Company-Id", testCompany.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAuditLogs_withoutCompanyContext_returnsBadRequest() throws Exception {
        // Clear company context to test missing header
        CompanyContext.clear();
        try {
            mockMvc
                    .perform(get("/api/v1/admin/audit-logs")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest());
        } finally {
            // Restore context for other tests
            CompanyContext.setCompanyId(testCompany.getId());
        }
    }

    private void createTestAuditLogs() {
        Instant now = Instant.now();

        // Successful CREATE log
        AuditLog log1 = new AuditLog();
        log1.setCompanyId(testCompany.getId());
        log1.setAction("CREATE");
        log1.setEntityType("CUSTOMER");
        log1.setEntityId("1");
        log1.setEntityDisplay("Customer One");
        log1.setEventType("MASTER_DATA_CREATED");
        log1.setUserId(adminUser.getId());
        log1.setEmail(adminUser.getEmail());
        log1.setActorRole("admin");
        log1.setSuccess(true);
        log1.setIpAddress("127.0.0.1");
        log1.setUserAgent("Test Agent");
        log1.setCreatedAt(now);
        auditLogRepository.save(log1);

        // Successful UPDATE log
        AuditLog log2 = new AuditLog();
        log2.setCompanyId(testCompany.getId());
        log2.setAction("UPDATE");
        log2.setEntityType("SUPPLIER");
        log2.setEntityId("2");
        log2.setEntityDisplay("Supplier Two");
        log2.setEventType("MASTER_DATA_UPDATED");
        log2.setUserId(adminUser.getId());
        log2.setEmail(adminUser.getEmail());
        log2.setActorRole("admin");
        log2.setSuccess(true);
        log2.setIpAddress("127.0.0.1");
        log2.setUserAgent("Test Agent");
        log2.setCreatedAt(now.plusSeconds(1));
        auditLogRepository.save(log2);

        // Failed DELETE log
        AuditLog log3 = new AuditLog();
        log3.setCompanyId(testCompany.getId());
        log3.setAction("DELETE");
        log3.setEntityType("BANK_ACCOUNT");
        log3.setEntityId("3");
        log3.setEntityDisplay("Bank Account Three");
        log3.setEventType("MASTER_DATA_DELETE_BLOCKED");
        log3.setUserId(adminUser.getId());
        log3.setEmail(adminUser.getEmail());
        log3.setActorRole("admin");
        log3.setSuccess(false);
        log3.setFailureReason("Record is referenced by vouchers");
        log3.setIpAddress("127.0.0.1");
        log3.setUserAgent("Test Agent");
        log3.setCreatedAt(now.plusSeconds(2));
        auditLogRepository.save(log3);
    }
}
