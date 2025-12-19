package com.accounting.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.accounting.test.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsControllerSecurityIT extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private ObjectMapper objectMapper;

  private Company companyA;
  private Company companyB;

  private User adminUser;
  private User cfoUser;
  private User chiefAccountantUser;
  private User accountantUser;
  private User cashierUser;
  private User companyBUser;

  private String adminToken;
  private String cfoToken;
  private String chiefAccountantToken;
  private String accountantToken;
  private String cashierToken;
  private String companyBToken;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    companyRepository.deleteAll();

    companyA = new Company();
    companyA.setCode("COMPANY_A");
    companyA.setName("Company A");
    companyA.setTaxCode("1234567890");
    companyA.setAddress("Address A");
    companyA = companyRepository.save(companyA);

    companyB = new Company();
    companyB.setCode("COMPANY_B");
    companyB.setName("Company B");
    companyB.setTaxCode("0987654321");
    companyB.setAddress("Address B");
    companyB = companyRepository.save(companyB);

    CompanyContext.setCompanyId(companyA.getId());

    adminUser = createUser("admin@companya.com", "Admin User", "ADMIN", companyA.getId());
    cfoUser = createUser("cfo@companya.com", "CFO User", "CFO", companyA.getId());
    chiefAccountantUser =
        createUser("chief@companya.com", "Chief Accountant", "CHIEF_ACCOUNTANT", companyA.getId());
    accountantUser =
        createUser("accountant@companya.com", "Accountant User", "ACCOUNTANT", companyA.getId());
    cashierUser = createUser("cashier@companya.com", "Cashier User", "CASHIER", companyA.getId());
    companyBUser = createUser("user@companyb.com", "Company B User", "ADMIN", companyB.getId());

    adminToken = generateToken(adminUser);
    cfoToken = generateToken(cfoUser);
    chiefAccountantToken = generateToken(chiefAccountantUser);
    accountantToken = generateToken(accountantUser);
    cashierToken = generateToken(cashierUser);
    companyBToken = generateToken(companyBUser);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  private User createUser(String email, String fullName, String role, Long companyId) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode("Password123!"));
    user.setFullName(fullName);
    user.setRole(role);
    user.setStatus("ACTIVE");
    user.setCompanyId(companyId);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    return userRepository.save(user);
  }

  private String generateToken(User user) {
    return jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
  }

  @Nested
  class RbacTests {

    @Test
    void getEmbeddingToken_allowsAdmin() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/token/1")
                  .header("Authorization", "Bearer " + adminToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.token").exists())
          .andExpect(jsonPath("$.dashboardId").value("1"));
    }

    @Test
    void getEmbeddingToken_allowsCFO() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/token/1")
                  .header("Authorization", "Bearer " + cfoToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void getEmbeddingToken_allowsChiefAccountant() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/token/1")
                  .header("Authorization", "Bearer " + chiefAccountantToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void getEmbeddingToken_deniesAccountant() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/token/1")
                  .header("Authorization", "Bearer " + accountantToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isForbidden());
    }

    @Test
    void getEmbeddingToken_deniesCashier() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/token/1")
                  .header("Authorization", "Bearer " + cashierToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isForbidden());
    }

    @Test
    void getEmbeddingToken_deniesUnauthenticated() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/token/1")
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void getSsoToken_allowsAllAccountingRoles() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/sso/token")
                  .header("Authorization", "Bearer " + adminToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.jwt").exists());

      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/sso/token")
                  .header("Authorization", "Bearer " + accountantToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.jwt").exists());

      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/sso/token")
                  .header("Authorization", "Bearer " + cashierToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.jwt").exists());
    }

    @Test
    void getSsoToken_deniesUnauthenticated() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/sso/token")
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void getEmbedConfig_allowsAccountant() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/embed/dashboard/financial-overview")
                  .header("Authorization", "Bearer " + accountantToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.metabaseInstanceUrl").exists())
          .andExpect(jsonPath("$.authEnabled").value(true));
    }

    @Test
    void listDashboards_allowsAllRoles() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/dashboards")
                  .header("Authorization", "Bearer " + cashierToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray());
    }

    @Test
    void logEvent_allowsAccountant() throws Exception {
      String eventBody =
          objectMapper.writeValueAsString(
              java.util.Map.of(
                  "eventType", "VIEW_LOADED",
                  "resourceType", "DASHBOARD",
                  "resourceId", "financial-overview"));

      mockMvc
          .perform(
              post("/api/v1/analytics/metabase/events")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(eventBody)
                  .header("Authorization", "Bearer " + accountantToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isOk());
    }
  }

  @Nested
  class TenantIsolationTests {

    @Test
    void companyBUser_cannotAccessCompanyAData() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/token/1")
                  .header("Authorization", "Bearer " + companyBToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isForbidden());
    }

    @Test
    void companyAUser_cannotAccessWithCompanyBHeader() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/sso/token")
                  .header("Authorization", "Bearer " + adminToken)
                  .header("X-Company-Id", String.valueOf(companyB.getId())))
          .andExpect(status().isForbidden());
    }

    @Test
    void missingCompanyIdHeader_returns400or403() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/sso/token")
                  .header("Authorization", "Bearer " + adminToken))
          .andExpect(
              result -> {
                int status = result.getResponse().getStatus();
                assert status == 400 || status == 403
                    : "Expected 400 or 403 but got " + status;
              });
    }

    @Test
    void companyBUser_getsOwnCompanySsoToken() throws Exception {
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/analytics/metabase/sso/token")
                      .header("Authorization", "Bearer " + companyBToken)
                      .header("X-Company-Id", String.valueOf(companyB.getId())))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.jwt").exists())
              .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      JsonNode json = objectMapper.readTree(responseBody);
      String jwtToken = json.get("jwt").asText();

      JsonNode claims = parseJwtPayload(jwtToken);
      assert claims.has("company_id")
          : "JWT should contain company_id claim";
      assert claims.get("company_id").asLong() == companyB.getId()
          : "company_id should match Company B";
    }
  }

  @Nested
  class JwtContentVerificationTests {

    @Test
    void ssoToken_containsCompanyIdClaim() throws Exception {
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/analytics/metabase/sso/token")
                      .header("Authorization", "Bearer " + adminToken)
                      .header("X-Company-Id", String.valueOf(companyA.getId())))
              .andExpect(status().isOk())
              .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      JsonNode json = objectMapper.readTree(responseBody);
      String jwtToken = json.get("jwt").asText();

      JsonNode claims = parseJwtPayload(jwtToken);
      assert claims.has("company_id") : "JWT must contain company_id claim";
      assert claims.get("company_id").asLong() == companyA.getId()
          : "company_id must match context company";
    }

    @Test
    void ssoToken_containsCompanyGroup() throws Exception {
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/analytics/metabase/sso/token")
                      .header("Authorization", "Bearer " + adminToken)
                      .header("X-Company-Id", String.valueOf(companyA.getId())))
              .andExpect(status().isOk())
              .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      JsonNode json = objectMapper.readTree(responseBody);
      String jwtToken = json.get("jwt").asText();

      JsonNode claims = parseJwtPayload(jwtToken);
      assert claims.has("groups") : "JWT must contain groups claim";

      JsonNode groups = claims.get("groups");
      assert groups.isArray() : "groups must be an array";

      boolean hasCompanyGroup = false;
      String expectedGroup = "company_" + companyA.getId();
      for (JsonNode group : groups) {
        if (expectedGroup.equals(group.asText())) {
          hasCompanyGroup = true;
          break;
        }
      }
      assert hasCompanyGroup : "groups must include company_" + companyA.getId();
    }

    @Test
    void ssoToken_containsRoleBasedGroup() throws Exception {
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/analytics/metabase/sso/token")
                      .header("Authorization", "Bearer " + adminToken)
                      .header("X-Company-Id", String.valueOf(companyA.getId())))
              .andExpect(status().isOk())
              .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      JsonNode json = objectMapper.readTree(responseBody);
      String jwtToken = json.get("jwt").asText();

      JsonNode claims = parseJwtPayload(jwtToken);
      JsonNode groups = claims.get("groups");

      boolean hasRoleGroup = false;
      for (JsonNode group : groups) {
        if (group.asText().startsWith("Analytics")) {
          hasRoleGroup = true;
          break;
        }
      }
      assert hasRoleGroup : "groups must include role-based Analytics group";
    }

    @Test
    void ssoToken_containsUserEmail() throws Exception {
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/analytics/metabase/sso/token")
                      .header("Authorization", "Bearer " + adminToken)
                      .header("X-Company-Id", String.valueOf(companyA.getId())))
              .andExpect(status().isOk())
              .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      JsonNode json = objectMapper.readTree(responseBody);
      String jwtToken = json.get("jwt").asText();

      JsonNode claims = parseJwtPayload(jwtToken);
      assert claims.has("email") : "JWT must contain email claim";
      assert adminUser.getEmail().equals(claims.get("email").asText())
          : "email must match user's email";
    }

    @Test
    void ssoToken_hasExpiration() throws Exception {
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/v1/analytics/metabase/sso/token")
                      .header("Authorization", "Bearer " + adminToken)
                      .header("X-Company-Id", String.valueOf(companyA.getId())))
              .andExpect(status().isOk())
              .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      JsonNode json = objectMapper.readTree(responseBody);
      String jwtToken = json.get("jwt").asText();

      JsonNode claims = parseJwtPayload(jwtToken);
      assert claims.has("exp") : "JWT must contain exp (expiration) claim";
      long exp = claims.get("exp").asLong();
      long now = Instant.now().getEpochSecond();
      assert exp > now : "Token expiration must be in the future";
    }
  }

  @Nested
  class WidgetAccessTests {

    @Test
    void checkWidgetAccess_returnsAllowedForAuthorizedUser() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/widgets/revenue-chart/access")
                  .header("Authorization", "Bearer " + adminToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.allowed").value(true))
          .andExpect(jsonPath("$.widgetKey").value("revenue-chart"));
    }

    @Test
    void getWidgetPermissions_returnsUserPermissions() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/analytics/metabase/widgets")
                  .header("Authorization", "Bearer " + accountantToken)
                  .header("X-Company-Id", String.valueOf(companyA.getId())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.scope").exists())
          .andExpect(jsonPath("$.allowedWidgets").isArray());
    }
  }

  private JsonNode parseJwtPayload(String jwtToken) throws Exception {
    String[] parts = jwtToken.split("\\.");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Invalid JWT format");
    }
    String payload = parts[1];
    byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
    String payloadJson = new String(decodedBytes);
    return objectMapper.readTree(payloadJson);
  }
}
