package com.accounting.controller.voucher;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class VoucherTemplateControllerIntegrationTest extends com.accounting.test.IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    private User chiefAccountantUser;
    private User accountantUser; // Should not have access
    private String chiefAccountantToken;
    private String accountantToken;
    private Long account1Id;
    private Long account2Id;

    @BeforeEach
    void setUp() {
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

        // Set CompanyContext BEFORE creating users
        CompanyContext.setCompanyId(testCompany.getId());

        // Create Chief Accountant user (has access)
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

        // Create Accountant user (should not have access to template management)
        accountantUser = new User();
        accountantUser.setEmail("accountant@example.com");
        accountantUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        accountantUser.setFullName("Accountant");
        accountantUser.setRole("accountant");
        accountantUser.setStatus("ACTIVE");
        accountantUser.setCompanyId(testCompany.getId());
        accountantUser.setCreatedAt(Instant.now());
        accountantUser.setUpdatedAt(Instant.now());
        accountantUser = userRepository.save(accountantUser);

        chiefAccountantToken = jwtTokenProvider.generateAccessToken(
                chiefAccountantUser.getId(),
                chiefAccountantUser.getEmail(),
                chiefAccountantUser.getRole()); // Role enum will handle the mapping

        accountantToken = jwtTokenProvider.generateAccessToken(
                accountantUser.getId(), accountantUser.getEmail(), accountantUser.getRole()); // Role enum will handle
                                                                                              // the mapping

        // Create test ChartOfAccounts
        ChartOfAccount account1 = createPostableAccount("1111", "Cash", testCompany.getId());
        ChartOfAccount account2 = createPostableAccount("4111", "Revenue", testCompany.getId());
        chartOfAccountsRepository.save(account1);
        chartOfAccountsRepository.save(account2);
        account1Id = account1.getId();
        account2Id = account2.getId();
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private ChartOfAccount createPostableAccount(String code, String name, Long companyId) {
        ChartOfAccount account = new ChartOfAccount();
        account.setCode(code);
        account.setName(name);
        account.setCompanyId(companyId);
        account.setPostable(true);
        account.setNormalSide("Debit");
        account.setType("Asset");
        account.setOrderingPosition(1);
        return account;
    }

    @Test
    void listTemplates_returnsTemplatesWithFirstLineAccounts() throws Exception {
        // Create a template first with unique name
        String uniqueName = "List Test Template " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> templateRequest = createTemplateRequest(uniqueName, true);
        String createJson = objectMapper.writeValueAsString(templateRequest);

        mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(createJson)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated());

        // Now test list endpoint
        mockMvc
                .perform(
                        get("/api/v1/voucher-templates")
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value(uniqueName))
                .andExpect(jsonPath("$.data[0].firstLineDebitAccount").exists())
                .andExpect(jsonPath("$.data[0].firstLineDebitAccount.code").exists())
                .andExpect(jsonPath("$.data[0].firstLineDebitAccount.name").exists())
                .andExpect(jsonPath("$.data[0].firstLineCreditAccount").exists())
                .andExpect(jsonPath("$.data[0].firstLineCreditAccount.code").exists())
                .andExpect(jsonPath("$.data[0].firstLineCreditAccount.name").exists());
    }

    @Test
    void listTemplates_withIsActiveFilter_filtersByStatus() throws Exception {
        // Create active template with unique name
        String activeName = "Active Template " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> activeTemplate = createTemplateRequest(activeName, true);
        mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(activeTemplate))
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated());

        // Create inactive template with unique name
        String inactiveName = "Inactive Template " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> inactiveTemplate = createTemplateRequest(inactiveName, false);
        mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inactiveTemplate))
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated());

        // Test filter by isActive=true
        mockMvc
                .perform(
                        get("/api/v1/voucher-templates")
                                .param("isActive", "true")
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.name == '" + activeName + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.name == '" + inactiveName + "')]").doesNotExist());
    }

    @Test
    void listTemplates_companyScoping_onlyShowsCurrentCompanyTemplates() throws Exception {
        // Create template for testCompany with unique name
        CompanyContext.setCompanyId(testCompany.getId());
        String template1Name = "Company 1 Template " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> template1 = createTemplateRequest(template1Name, true);
        mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(template1))
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated());

        // Create template for otherCompany
        CompanyContext.setCompanyId(otherCompany.getId());
        // Create accounts for otherCompany
        ChartOfAccount otherAccount1 = createPostableAccount("1111", "Cash", otherCompany.getId());
        ChartOfAccount otherAccount2 = createPostableAccount("4111", "Revenue", otherCompany.getId());
        chartOfAccountsRepository.save(otherAccount1);
        chartOfAccountsRepository.save(otherAccount2);

        String template2Name = "Company 2 Template " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> template2 = createTemplateRequestWithAccounts(
                template2Name, true, otherAccount1.getId(), otherAccount2.getId());
        mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(template2))
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(otherCompany.getId())))
                .andExpect(status().isCreated());

        // Restore CompanyContext to testCompany
        CompanyContext.setCompanyId(testCompany.getId());

        // List templates for testCompany - should only see testCompany's template
        mockMvc
                .perform(
                        get("/api/v1/voucher-templates")
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.name == '" + template1Name + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.name == '" + template2Name + "')]").doesNotExist());
    }

    @Test
    void createTemplate_validRequest_createsTemplate() throws Exception {
        String uniqueName = "New Template " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> templateRequest = createTemplateRequest(uniqueName, true);
        String requestJson = objectMapper.writeValueAsString(templateRequest);

        mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(requestJson)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(uniqueName))
                .andExpect(jsonPath("$.data.lines").isArray())
                .andExpect(jsonPath("$.data.lines.length()").value(1))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    void createTemplate_missingName_returnsBadRequest() throws Exception {
        Map<String, Object> templateRequest = new HashMap<>();
        templateRequest.put("description", "Template without name");
        templateRequest.put("isActive", true);
        templateRequest.put("lines", List.of(createTemplateLineRequest(account1Id, account2Id)));

        String requestJson = objectMapper.writeValueAsString(templateRequest);

        mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(requestJson)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTemplate_noLines_returnsBadRequest() throws Exception {
        Map<String, Object> templateRequest = new HashMap<>();
        templateRequest.put("name", "Template without lines");
        templateRequest.put("isActive", true);
        templateRequest.put("lines", List.of());

        String requestJson = objectMapper.writeValueAsString(templateRequest);

        mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(requestJson)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTemplate_accountantRole_returnsForbidden() throws Exception {
        String uniqueName = "Template " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> templateRequest = createTemplateRequest(uniqueName, true);
        String requestJson = objectMapper.writeValueAsString(templateRequest);

        mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(requestJson)
                                .header("Authorization", "Bearer " + accountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateTemplate_validRequest_updatesTemplate() throws Exception {
        // Create template first with unique name
        String uniqueName = "Update Test Template " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> createRequest = createTemplateRequest(uniqueName, true);
        String createJson = objectMapper.writeValueAsString(createRequest);

        String response = mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(createJson)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID templateId = UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());

        // Update template with different unique name
        String updatedName = "Updated " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> updateRequest = createTemplateRequest(updatedName, false);
        String updateJson = objectMapper.writeValueAsString(updateRequest);

        mockMvc
                .perform(
                        put("/api/v1/voucher-templates/" + templateId)
                                .contentType(APPLICATION_JSON)
                                .content(updateJson)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(updatedName))
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    void deleteTemplate_validTemplate_deletesTemplate() throws Exception {
        // Create template first with unique name
        String uniqueName = "To Delete " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> createRequest = createTemplateRequest(uniqueName, true);
        String createJson = objectMapper.writeValueAsString(createRequest);

        String response = mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(createJson)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID templateId = UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());

        // Delete template
        mockMvc
                .perform(
                        delete("/api/v1/voucher-templates/" + templateId)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNoContent());

        // Verify template is deleted
        mockMvc
                .perform(
                        get("/api/v1/voucher-templates/" + templateId)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void activateTemplate_activatesTemplate() throws Exception {
        // Create inactive template with unique name
        String uniqueName = "Inactive Template " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> createRequest = createTemplateRequest(uniqueName, false);
        String createJson = objectMapper.writeValueAsString(createRequest);

        String response = mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(createJson)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID templateId = UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());

        // Activate template
        mockMvc
                .perform(
                        patch("/api/v1/voucher-templates/" + templateId + "/activate")
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    void deactivateTemplate_deactivatesTemplate() throws Exception {
        // Create active template with unique name
        String uniqueName = "Active Template " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> createRequest = createTemplateRequest(uniqueName, true);
        String createJson = objectMapper.writeValueAsString(createRequest);

        String response = mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(createJson)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID templateId = UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());

        // Deactivate template
        mockMvc
                .perform(
                        patch("/api/v1/voucher-templates/" + templateId + "/deactivate")
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    void getTemplateById_returnsTemplateWithLines() throws Exception {
        // Create template first with unique name
        String uniqueName = "Template Detail " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> createRequest = createTemplateRequest(uniqueName, true);
        String createJson = objectMapper.writeValueAsString(createRequest);

        String response = mockMvc
                .perform(
                        post("/api/v1/voucher-templates")
                                .contentType(APPLICATION_JSON)
                                .content(createJson)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID templateId = UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());

        // Get template by ID
        mockMvc
                .perform(
                        get("/api/v1/voucher-templates/" + templateId)
                                .header("Authorization", "Bearer " + chiefAccountantToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(uniqueName))
                .andExpect(jsonPath("$.data.lines").isArray())
                .andExpect(jsonPath("$.data.lines.length()").value(1));
    }

    private Map<String, Object> createTemplateRequest(String name, Boolean isActive) {
        return createTemplateRequestWithAccounts(name, isActive, account1Id, account2Id);
    }

    private Map<String, Object> createTemplateRequestWithAccounts(
            String name, Boolean isActive, Long debitAccountId, Long creditAccountId) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("description", "Test template description");
        request.put("isActive", isActive);
        request.put("lines", List.of(createTemplateLineRequest(debitAccountId, creditAccountId)));
        return request;
    }

    private Map<String, Object> createTemplateLineRequest(Long debitAccountId, Long creditAccountId) {
        Map<String, Object> line = new HashMap<>();
        line.put("debitAccountId", debitAccountId);
        line.put("creditAccountId", creditAccountId);
        line.put("defaultDescription", "Test line description");
        line.put("requiresCustomer", false);
        line.put("requiresSupplier", false);
        line.put("requiresCostCenter", false);
        line.put("lockAccounts", false);
        return line;
    }
}
