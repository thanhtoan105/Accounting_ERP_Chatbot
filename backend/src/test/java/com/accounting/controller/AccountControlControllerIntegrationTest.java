package com.accounting.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accounting.entity.AccountControl;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.AccountControlRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControlControllerIntegrationTest extends com.accounting.test.IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountControlRepository accountControlRepository;

    @Autowired
    private ChartOfAccountsRepository chartOfAccountsRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private Company testCompany;
    private Company otherCompany;
    private User testUser;
    private User chiefAccountantUser;
    private String testToken;
    private String chiefAccountantToken;
    private ChartOfAccount testAccount;

    @BeforeEach
    void setUp() {
        accountControlRepository.deleteAll();
        chartOfAccountsRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

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

        // Create test account
        testAccount = new ChartOfAccount();
        testAccount.setCompanyId(testCompany.getId());
        testAccount.setCode("1111");
        testAccount.setName("Test Account");
        testAccount.setType("Asset");
        testAccount.setNormalSide("Debit");
        testAccount.setPostable(true);
        testAccount.setActive(true);
        testAccount.setOrderingPosition(1);
        testAccount = chartOfAccountsRepository.save(testAccount);

        // Create test users
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        testUser.setFullName("Test User");
        testUser.setRole("accountant");
        testUser.setStatus("ACTIVE");
        testUser.setCompanyId(testCompany.getId());
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        testUser = userRepository.save(testUser);

        chiefAccountantUser = new User();
        chiefAccountantUser.setEmail("chief@example.com");
        chiefAccountantUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        chiefAccountantUser.setFullName("Chief Accountant");
        chiefAccountantUser.setRole("chief_accountant");
        chiefAccountantUser.setStatus("ACTIVE");
        chiefAccountantUser.setCompanyId(testCompany.getId());
        chiefAccountantUser.setCreatedAt(Instant.now());
        chiefAccountantUser.setUpdatedAt(Instant.now());
        chiefAccountantUser = userRepository.save(chiefAccountantUser);

        testToken = jwtTokenProvider.generateAccessToken(
                testUser.getId(), testUser.getEmail(), testUser.getRole());
        chiefAccountantToken = jwtTokenProvider.generateAccessToken(
                chiefAccountantUser.getId(), chiefAccountantUser.getEmail(), chiefAccountantUser.getRole());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void getAllAccountControls_returnsAllForCurrentCompany() throws Exception {
        // Create account controls
        AccountControl control1 = createAccountControl(testAccount.getId(), testCompany.getId());
        control1.setRequiresCustomer(true);
        accountControlRepository.save(control1);

        AccountControl control2 = createAccountControl(testAccount.getId(), testCompany.getId());
        control2.setRequiresSupplier(true);
        accountControlRepository.save(control2);

        mockMvc
                .perform(
                        get("/api/v1/account-controls")
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void getAllAccountControls_companyScoping_onlyShowsCurrentCompany() throws Exception {
        AccountControl control1 = createAccountControl(testAccount.getId(), testCompany.getId());
        accountControlRepository.save(control1);

        // Create account for other company
        ChartOfAccount otherAccount = new ChartOfAccount();
        otherAccount.setCompanyId(otherCompany.getId());
        otherAccount.setCode("2222");
        otherAccount.setName("Other Account");
        otherAccount.setType("Asset");
        otherAccount.setNormalSide("Debit");
        otherAccount.setPostable(true);
        otherAccount.setActive(true);
        otherAccount.setOrderingPosition(1);
        otherAccount = chartOfAccountsRepository.save(otherAccount);

        CompanyContext.setCompanyId(otherCompany.getId());
        AccountControl control2 = createAccountControl(otherAccount.getId(), otherCompany.getId());
        accountControlRepository.save(control2);
        CompanyContext.setCompanyId(testCompany.getId());

        mockMvc
                .perform(
                        get("/api/v1/account-controls")
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getAccountControlById_whenExists_returnsAccountControl() throws Exception {
        AccountControl control = createAccountControl(testAccount.getId(), testCompany.getId());
        control.setRequiresCustomer(true);
        control = accountControlRepository.save(control);

        mockMvc
                .perform(
                        get("/api/v1/account-controls/" + control.getId())
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(control.getId().toString()))
                .andExpect(jsonPath("$.data.accountId").value(testAccount.getId()))
                .andExpect(jsonPath("$.data.requiresCustomer").value(true));
    }

    @Test
    void getAccountControlById_whenNotExists_returnsNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc
                .perform(
                        get("/api/v1/account-controls/" + nonExistentId)
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAccountControl_createsAccountControl() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "accountId", testAccount.getId(),
                        "requiresCustomer", true,
                        "requiresSupplier", false,
                        "requiresCostCenter", true,
                        "requiresItem", false));

        mockMvc
                .perform(
                        post("/api/v1/account-controls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").value(testAccount.getId()))
                .andExpect(jsonPath("$.data.requiresCustomer").value(true))
                .andExpect(jsonPath("$.data.requiresSupplier").value(false))
                .andExpect(jsonPath("$.data.requiresCostCenter").value(true))
                .andExpect(jsonPath("$.data.requiresItem").value(false));
    }

    @Test
    void createAccountControl_whenAccountNotExists_returnsNotFound() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "accountId", 99999L,
                        "requiresCustomer", true));

        mockMvc
                .perform(
                        post("/api/v1/account-controls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAccountControl_whenAlreadyExists_returnsConflict() throws Exception {
        AccountControl existing = createAccountControl(testAccount.getId(), testCompany.getId());
        accountControlRepository.save(existing);

        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "accountId", testAccount.getId(),
                        "requiresCustomer", true));

        mockMvc
                .perform(
                        post("/api/v1/account-controls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void createAccountControl_whenUnauthorized_returnsForbidden() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "accountId", testAccount.getId(),
                        "requiresCustomer", true));

        mockMvc
                .perform(
                        post("/api/v1/account-controls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAccountControl_updatesAccountControl() throws Exception {
        AccountControl control = createAccountControl(testAccount.getId(), testCompany.getId());
        control.setRequiresCustomer(false);
        control = accountControlRepository.save(control);

        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "accountId", testAccount.getId(),
                        "requiresCustomer", true,
                        "requiresSupplier", true,
                        "requiresCostCenter", false,
                        "requiresItem", false));

        mockMvc
                .perform(
                        put("/api/v1/account-controls/" + control.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresCustomer").value(true))
                .andExpect(jsonPath("$.data.requiresSupplier").value(true))
                .andExpect(jsonPath("$.data.requiresCostCenter").value(false));
    }

    @Test
    void updateAccountControl_whenNotExists_returnsNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "accountId", testAccount.getId(),
                        "requiresCustomer", true));

        mockMvc
                .perform(
                        put("/api/v1/account-controls/" + nonExistentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAccountControl_deletesAccountControl() throws Exception {
        AccountControl control = createAccountControl(testAccount.getId(), testCompany.getId());
        control = accountControlRepository.save(control);

        mockMvc
                .perform(
                        delete("/api/v1/account-controls/" + control.getId())
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNoContent());

        // Verify deletion
        assertTrue(accountControlRepository.findById(control.getId()).isEmpty());
    }

    @Test
    void deleteAccountControl_whenNotExists_returnsNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc
                .perform(
                        delete("/api/v1/account-controls/" + nonExistentId)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAccountControl_whenUnauthorized_returnsForbidden() throws Exception {
        AccountControl control = createAccountControl(testAccount.getId(), testCompany.getId());
        control = accountControlRepository.save(control);

        mockMvc
                .perform(
                        delete("/api/v1/account-controls/" + control.getId())
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isForbidden());
    }

    private AccountControl createAccountControl(Long accountId, Long companyId) {
        AccountControl control = new AccountControl();
        control.setAccountId(accountId);
        control.setCompanyId(companyId);
        control.setRequiresCustomer(false);
        control.setRequiresSupplier(false);
        control.setRequiresCostCenter(false);
        control.setRequiresItem(false);
        control.setCreatedAt(Instant.now());
        control.setUpdatedAt(Instant.now());
        return control;
    }
}
