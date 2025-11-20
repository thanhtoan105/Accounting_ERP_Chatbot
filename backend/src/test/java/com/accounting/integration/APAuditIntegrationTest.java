package com.accounting.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accounting.entity.APAuditBackup;
import com.accounting.entity.AuditLog;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.APAuditBackupRepository;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.PasswordEncoder;
import com.accounting.test.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
class APAuditIntegrationTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private APAuditBackupRepository backupRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private Long testCompanyId;
    private Long testUserId;
    private Long adminUserId;

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

        // Create standard user (Accountant)
        User user = new User();
        user.setEmail("accountant@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setFullName("Test Accountant");
        user.setCompanyId(testCompanyId);
        user.setRole("accountant");
        user.setStatus("ACTIVE");
        user = userRepository.save(user);
        testUserId = user.getId();

        // Create admin user (Chief Accountant)
        User admin = new User();
        admin.setEmail("chief@example.com");
        admin.setPasswordHash(passwordEncoder.encode("Password123!"));
        admin.setFullName("Chief Accountant");
        admin.setCompanyId(testCompanyId);
        admin.setRole("chief_accountant");
        admin.setStatus("ACTIVE");
        admin = userRepository.save(admin);
        adminUserId = admin.getId();

        // Seed some audit logs
        createAuditLog("PURCHASE_BILL_CREATED", "PURCHASE_BILL", "BILL-001", true);
        createAuditLog("PURCHASE_BILL_APPROVED", "PURCHASE_BILL", "BILL-001", true);
        createAuditLog("PURCHASE_BILL_DELETE_FAILED", "PURCHASE_BILL", "BILL-002", false);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private void createAuditLog(String action, String entityType, String entityId, boolean success) {
        AuditLog log = new AuditLog();
        log.setCompanyId(testCompanyId);
        log.setUserId(testUserId);
        log.setAction(action);
        log.setEventType(entityType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setSuccess(success);
        if (!success) {
            log.setFailureReason("Simulated failure");
        }
        auditLogRepository.save(log);
    }

    @Test
    void getTimeline_returnsLogs() throws Exception {
        mockMvc.perform(get("/api/v1/ap-audit/timeline")
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "ACCOUNTANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void getAbuseDetections_requiresPrivilege() throws Exception {
        // Accountant cannot access abuse view
        mockMvc.perform(get("/api/v1/ap-audit/abuse")
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "ACCOUNTANT")))
                .andExpect(status().isForbidden());

        // Chief Accountant can access
        mockMvc.perform(get("/api/v1/ap-audit/abuse")
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(adminUserId, "CHIEF_ACCOUNTANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createBackup_createsBackupEntry() throws Exception {
        mockMvc.perform(post("/api/v1/ap-audit/backups")
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(adminUserId, "CHIEF_ACCOUNTANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(backupRepository.count()).isEqualTo(1);
    }

    @Test
    void purgeAuditLogs_requiresAdmin() throws Exception {
        // Chief Accountant cannot purge (requires ADMIN role per implementation check usually, or verify controller)
        // Controller @PreAuthorize("hasRole('ADMIN')")
        
        Map<String, Object> criteria = Map.of(
            "beforeDate", java.time.LocalDate.now().plusDays(1).toString()
        );

        mockMvc.perform(post("/api/v1/ap-audit/purge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(criteria))
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(adminUserId, "CHIEF_ACCOUNTANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void purgeAuditLogs_adminCanPurge() throws Exception {
        // Create global admin
        User globalAdmin = new User();
        globalAdmin.setEmail("admin@example.com");
        globalAdmin.setPasswordHash(passwordEncoder.encode("Password123!"));
        globalAdmin.setFullName("Global Admin");
        globalAdmin.setCompanyId(testCompanyId);
        globalAdmin.setRole("admin");
        globalAdmin.setStatus("ACTIVE");
        userRepository.save(globalAdmin);

        Map<String, Object> criteria = Map.of(
            "beforeDate", java.time.LocalDate.now().plusDays(1).toString()
        );

        mockMvc.perform(post("/api/v1/ap-audit/purge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(criteria))
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(globalAdmin.getId(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purgedCount").value(3)); // All 3 seeded logs
        
        assertThat(auditLogRepository.count()).isEqualTo(1); // 3 purged + 1 purge audit log = 1
        AuditLog purgeLog = auditLogRepository.findAll().get(0);
        assertThat(purgeLog.getAction()).isEqualTo("AUDIT_LOG_PURGE");
    }

    private RequestPostProcessor authenticatedUser(Long userId, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), "password", authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
