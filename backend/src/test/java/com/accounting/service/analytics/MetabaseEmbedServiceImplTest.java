package com.accounting.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.accounting.entity.User;
import com.accounting.entity.dashboard.DashboardAuditLog;
import com.accounting.repository.dashboard.DashboardAuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for MetabaseEmbedServiceImpl JWT generation.
 * Verifies that company_id is always set server-side and cannot be overridden by clients (AC 8.0.3).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetabaseEmbedServiceImplTest {

    private static final Long COMPANY_A = 100L;
    private static final Long COMPANY_B = 200L;
    private static final String JWT_SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256";

    @Mock
    private DashboardAuditLogRepository auditLogRepository;

    private MetabaseEmbedServiceImpl embedService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        embedService = new MetabaseEmbedServiceImpl(auditLogRepository);
        ReflectionTestUtils.setField(embedService, "metabaseSiteUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(embedService, "jwtSecret", JWT_SECRET);
        objectMapper = new ObjectMapper();

        when(auditLogRepository.save(any(DashboardAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User createUser(Long id, String email, String fullName, String role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        return user;
    }

    private JsonNode decodeJwtPayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        return objectMapper.readTree(payload);
    }

    @Nested
    @DisplayName("JWT company_id Locking Tests (AC 8.0.3)")
    class JwtCompanyIdLockingTests {

        @Test
        @DisplayName("JWT token includes company_id from server context")
        void generateJwtToken_includesCompanyIdFromServer() throws Exception {
            User user = createUser(1L, "test@example.com", "Test User", "ADMIN");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertThat(payload.has("company_id")).isTrue();
            assertThat(payload.get("company_id").asLong()).isEqualTo(COMPANY_A);
        }

        @Test
        @DisplayName("Different users get JWT with their respective company_id")
        void generateJwtToken_respectsCompanyContext() throws Exception {
            User userA = createUser(1L, "userA@example.com", "User A", "ADMIN");
            User userB = createUser(2L, "userB@example.com", "User B", "ADMIN");

            String tokenA = embedService.generateJwtToken(userA, COMPANY_A);
            String tokenB = embedService.generateJwtToken(userB, COMPANY_B);

            JsonNode payloadA = decodeJwtPayload(tokenA);
            JsonNode payloadB = decodeJwtPayload(tokenB);

            assertThat(payloadA.get("company_id").asLong()).isEqualTo(COMPANY_A);
            assertThat(payloadB.get("company_id").asLong()).isEqualTo(COMPANY_B);
        }

        @Test
        @DisplayName("JWT includes tenant group based on company_id")
        void generateJwtToken_includesTenantGroup() throws Exception {
            User user = createUser(1L, "test@example.com", "Test User", "ADMIN");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertThat(payload.has("groups")).isTrue();
            assertThat(payload.get("groups").isArray()).isTrue();
            
            boolean hasTenantGroup = false;
            for (JsonNode group : payload.get("groups")) {
                if (group.asText().equals("company_100")) {
                    hasTenantGroup = true;
                    break;
                }
            }
            assertThat(hasTenantGroup)
                    .as("JWT should include tenant group 'company_100'")
                    .isTrue();
        }

        @Test
        @DisplayName("JWT includes expiration claim")
        void generateJwtToken_hasExpiration() throws Exception {
            User user = createUser(1L, "test@example.com", "Test User", "ADMIN");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertThat(payload.has("exp")).isTrue();
            
            long expiration = payload.get("exp").asLong();
            long nowEpochSeconds = System.currentTimeMillis() / 1000;
            
            assertThat(expiration).isGreaterThan(nowEpochSeconds);
            assertThat(expiration - nowEpochSeconds)
                    .as("Token should expire in approximately 60 minutes")
                    .isBetween(3500L, 3700L);
        }
    }

    @Nested
    @DisplayName("JWT Role-to-Group Mapping Tests")
    class JwtRoleGroupMappingTests {

        @Test
        @DisplayName("ADMIN role maps to Analytics Admins group")
        void generateJwtToken_adminMapsToAdminsGroup() throws Exception {
            User user = createUser(1L, "admin@example.com", "Admin User", "ADMIN");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertGroupContains(payload.get("groups"), "Analytics Admins");
        }

        @Test
        @DisplayName("CHIEF_ACCOUNTANT role maps to Analytics Power Users group")
        void generateJwtToken_chiefAccountantMapsToPowerUsersGroup() throws Exception {
            User user = createUser(1L, "chief@example.com", "Chief Accountant", "CHIEF_ACCOUNTANT");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertGroupContains(payload.get("groups"), "Analytics Power Users");
        }

        @Test
        @DisplayName("CFO role maps to Analytics Viewers group")
        void generateJwtToken_cfoMapsToViewersGroup() throws Exception {
            User user = createUser(1L, "cfo@example.com", "CFO User", "CFO");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertGroupContains(payload.get("groups"), "Analytics Viewers");
        }

        @Test
        @DisplayName("ACCOUNTANT_AR role maps to Analytics AR group")
        void generateJwtToken_accountantArMapsToArGroup() throws Exception {
            User user = createUser(1L, "ar@example.com", "AR Accountant", "ACCOUNTANT_AR");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertGroupContains(payload.get("groups"), "Analytics AR");
        }

        @Test
        @DisplayName("CASHIER role maps to Analytics Cashier group")
        void generateJwtToken_cashierMapsToCashierGroup() throws Exception {
            User user = createUser(1L, "cashier@example.com", "Cashier User", "CASHIER");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertGroupContains(payload.get("groups"), "Analytics Cashier");
        }

        private void assertGroupContains(JsonNode groupsNode, String expectedGroup) {
            boolean found = false;
            for (JsonNode group : groupsNode) {
                if (group.asText().equals(expectedGroup)) {
                    found = true;
                    break;
                }
            }
            assertThat(found)
                    .as("Groups should contain '%s'", expectedGroup)
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Embed Config Tests")
    class EmbedConfigTests {

        @Test
        @DisplayName("generateEmbedConfig returns valid config structure")
        void generateEmbedConfig_returnsValidConfig() {
            User user = createUser(1L, "test@example.com", "Test User", "ADMIN");

            var config = embedService.generateEmbedConfig("financial-overview", user, COMPANY_A);

            assertThat(config.metabaseInstanceUrl()).isEqualTo("http://localhost:3000");
            assertThat(config.authConfig().enabled()).isTrue();
            assertThat(config.authConfig().authProviderUri().uri())
                    .isEqualTo("/api/v1/analytics/metabase/sso/token");
            assertThat(config.authConfig().authType())
                    .isEqualTo(MetabaseEmbedService.AuthType.JWT);
        }

        @Test
        @DisplayName("getTokenExpiryMinutes returns expected value")
        void getTokenExpiryMinutes_returns60() {
            assertThat(embedService.getTokenExpiryMinutes()).isEqualTo(60);
        }

        @Test
        @DisplayName("getRefreshBeforeExpiryMinutes returns expected value")
        void getRefreshBeforeExpiryMinutes_returns15() {
            assertThat(embedService.getRefreshBeforeExpiryMinutes()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("User Name Parsing Tests")
    class UserNameParsingTests {

        @Test
        @DisplayName("JWT includes correct first and last name")
        void generateJwtToken_parsesFullNameCorrectly() throws Exception {
            User user = createUser(1L, "test@example.com", "John Doe", "ADMIN");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertThat(payload.get("first_name").asText()).isEqualTo("John");
            assertThat(payload.get("last_name").asText()).isEqualTo("Doe");
        }

        @Test
        @DisplayName("JWT handles single name correctly")
        void generateJwtToken_handlesSingleName() throws Exception {
            User user = createUser(1L, "test@example.com", "Mononym", "ADMIN");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertThat(payload.get("first_name").asText()).isEqualTo("Mononym");
            assertThat(payload.get("last_name").asText()).isEmpty();
        }

        @Test
        @DisplayName("JWT handles null name gracefully")
        void generateJwtToken_handlesNullName() throws Exception {
            User user = createUser(1L, "test@example.com", null, "ADMIN");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertThat(payload.get("first_name").asText()).isEqualTo("User");
            assertThat(payload.get("last_name").asText()).isEmpty();
        }

        @Test
        @DisplayName("JWT handles multi-part name correctly")
        void generateJwtToken_handlesMultiPartName() throws Exception {
            User user = createUser(1L, "test@example.com", "Jean Claude Van Damme", "ADMIN");

            String token = embedService.generateJwtToken(user, COMPANY_A);

            JsonNode payload = decodeJwtPayload(token);
            assertThat(payload.get("first_name").asText()).isEqualTo("Jean");
            assertThat(payload.get("last_name").asText()).isEqualTo("Claude Van Damme");
        }
    }
}
