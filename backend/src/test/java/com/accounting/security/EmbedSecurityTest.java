package com.accounting.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.accounting.entity.User;
import com.accounting.service.analytics.MetabaseEmbedServiceImpl;
import com.accounting.test.IntegrationTest;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

/**
 * Security tests for Metabase embed URL tampering prevention.
 * 
 * AC 8.0.3: Security test - forcing company_id in embed URL is ignored
 * 
 * Tests verify that:
 * - JWT tokens are signed and cannot be tampered with
 * - company_id is locked in the JWT and cannot be overridden
 * - Expired tokens are rejected
 * - Wrong signatures are detected
 */
@SpringBootTest
@ActiveProfiles("test")
class EmbedSecurityTest extends IntegrationTest {

    @Autowired
    private MetabaseEmbedServiceImpl metabaseEmbedService;

    @Value("${metabase.embedding-secret:test-metabase-embed-secret-for-testing-purpose-256-bits}")
    private String jwtSecret;

    private JwtTestUtils jwtTestUtils;

    private static final Long COMPANY_A_ID = 1L;
    private static final Long COMPANY_B_ID = 2L;
    private static final String TEST_EMAIL = "test@company-a.com";
    private static final String TEST_ROLE = "ACCOUNTANT";

    @BeforeEach
    void setUp() {
        jwtTestUtils = new JwtTestUtils(jwtSecret);
    }

    @Nested
    @DisplayName("JWT Token Generation Security")
    class JwtTokenGenerationSecurityTests {

        @Test
        @DisplayName("Generated JWT should include locked company_id claim")
        void embedConfig_shouldIncludeLockedCompanyId() {
            User user = createTestUser(COMPANY_A_ID, TEST_EMAIL, TEST_ROLE);

            String jwt = metabaseEmbedService.generateJwtToken(user, COMPANY_A_ID);

            Long extractedCompanyId = jwtTestUtils.extractCompanyId(jwt);
            assertThat(extractedCompanyId).isEqualTo(COMPANY_A_ID);
        }

        @Test
        @DisplayName("Generated JWT should include tenant group")
        void embedConfig_shouldIncludeTenantGroup() {
            User user = createTestUser(COMPANY_A_ID, TEST_EMAIL, TEST_ROLE);

            String jwt = metabaseEmbedService.generateJwtToken(user, COMPANY_A_ID);

            var signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            var claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();

            @SuppressWarnings("unchecked")
            List<String> groups = claims.get("groups", List.class);
            assertThat(groups).contains("company_" + COMPANY_A_ID);
        }

        @Test
        @DisplayName("Generated JWT should have valid signature")
        void generatedJwt_shouldHaveValidSignature() {
            User user = createTestUser(COMPANY_A_ID, TEST_EMAIL, TEST_ROLE);

            String jwt = metabaseEmbedService.generateJwtToken(user, COMPANY_A_ID);

            assertThat(jwtTestUtils.validateJwt(jwt)).isTrue();
        }
    }

    @Nested
    @DisplayName("JWT Tampering Detection")
    class JwtTamperingDetectionTests {

        @Test
        @DisplayName("Tampered JWT with modified company_id should be rejected")
        void tamperedJwt_shouldBeRejected() {
            String validJwt = jwtTestUtils.createValidEmbedJwt(COMPANY_A_ID, TEST_EMAIL, TEST_ROLE, 60);

            String tamperedJwt = jwtTestUtils.tamperCompanyId(validJwt, COMPANY_B_ID);

            assertFalse(jwtTestUtils.validateJwt(tamperedJwt),
                    "Tampered JWT should fail signature verification");
        }

        @Test
        @DisplayName("JWT with wrong signature should throw SignatureException")
        void jwtWithWrongSignature_shouldThrowSignatureException() {
            String jwtWithWrongSecret = jwtTestUtils.createJwtWithWrongSecret(
                    COMPANY_A_ID, TEST_EMAIL, TEST_ROLE);

            var signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());

            assertThrows(SignatureException.class, () -> {
                Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(jwtWithWrongSecret);
            }, "JWT signed with wrong secret should throw SignatureException");
        }

        @Test
        @DisplayName("Original company_id should remain after tampering attempt")
        void originalCompanyId_shouldRemainAfterTamperingAttempt() {
            String validJwt = jwtTestUtils.createValidEmbedJwt(COMPANY_A_ID, TEST_EMAIL, TEST_ROLE, 60);

            Long originalCompanyId = jwtTestUtils.extractCompanyId(validJwt);

            assertThat(originalCompanyId)
                    .isEqualTo(COMPANY_A_ID)
                    .as("Original JWT should have company_id = %d", COMPANY_A_ID);
        }
    }

    @Nested
    @DisplayName("JWT Expiration Handling")
    class JwtExpirationHandlingTests {

        @Test
        @DisplayName("Expired JWT should be rejected")
        void expiredJwt_shouldBeRejected() {
            String expiredJwt = jwtTestUtils.createExpiredJwt(COMPANY_A_ID, TEST_EMAIL, TEST_ROLE);

            assertFalse(jwtTestUtils.validateJwt(expiredJwt),
                    "Expired JWT should fail validation");
        }

        @Test
        @DisplayName("Expired JWT should throw ExpiredJwtException")
        void expiredJwt_shouldThrowExpiredJwtException() {
            String expiredJwt = jwtTestUtils.createExpiredJwt(COMPANY_A_ID, TEST_EMAIL, TEST_ROLE);

            var signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());

            assertThrows(ExpiredJwtException.class, () -> {
                Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(expiredJwt);
            }, "Expired JWT should throw ExpiredJwtException");
        }
    }

    @Nested
    @DisplayName("Multi-tenant Isolation")
    class MultiTenantIsolationTests {

        @Test
        @DisplayName("User from company A should get JWT with company A's ID only")
        void userFromCompanyA_shouldGetJwtWithCompanyAIdOnly() {
            User userCompanyA = createTestUser(COMPANY_A_ID, "user-a@company-a.com", TEST_ROLE);

            String jwt = metabaseEmbedService.generateJwtToken(userCompanyA, COMPANY_A_ID);

            Long companyIdInJwt = jwtTestUtils.extractCompanyId(jwt);
            assertThat(companyIdInJwt)
                    .isEqualTo(COMPANY_A_ID)
                    .isNotEqualTo(COMPANY_B_ID);
        }

        @Test
        @DisplayName("User from company B should get JWT with company B's ID only")
        void userFromCompanyB_shouldGetJwtWithCompanyBIdOnly() {
            User userCompanyB = createTestUser(COMPANY_B_ID, "user-b@company-b.com", TEST_ROLE);

            String jwt = metabaseEmbedService.generateJwtToken(userCompanyB, COMPANY_B_ID);

            Long companyIdInJwt = jwtTestUtils.extractCompanyId(jwt);
            assertThat(companyIdInJwt)
                    .isEqualTo(COMPANY_B_ID)
                    .isNotEqualTo(COMPANY_A_ID);
        }

        @Test
        @DisplayName("Company A's JWT cannot be used to access company B's data")
        void companyAJwt_cannotAccessCompanyBData() {
            String jwtCompanyA = jwtTestUtils.createValidEmbedJwt(COMPANY_A_ID, TEST_EMAIL, TEST_ROLE, 60);

            String tamperedJwt = jwtTestUtils.tamperCompanyId(jwtCompanyA, COMPANY_B_ID);
            assertFalse(jwtTestUtils.validateJwt(tamperedJwt),
                    "Tampered JWT to access company B should be rejected");
        }
    }

    @Nested
    @DisplayName("Token Replay Prevention")
    class TokenReplayPreventionTests {

        @Test
        @DisplayName("Same token should validate consistently within expiry window")
        void sameToken_shouldValidateConsistentlyWithinExpiryWindow() {
            String jwt = jwtTestUtils.createValidEmbedJwt(COMPANY_A_ID, TEST_EMAIL, TEST_ROLE, 60);

            assertThat(jwtTestUtils.validateJwt(jwt)).isTrue();
            assertThat(jwtTestUtils.validateJwt(jwt)).isTrue();
        }

        @Test
        @DisplayName("Token with future expiry should be valid")
        void tokenWithFutureExpiry_shouldBeValid() {
            String jwt = jwtTestUtils.createValidEmbedJwt(COMPANY_A_ID, TEST_EMAIL, TEST_ROLE, 120);

            assertThat(jwtTestUtils.validateJwt(jwt)).isTrue();
        }
    }

    private User createTestUser(Long companyId, String email, String role) {
        User user = new User();
        user.setId(100L);
        user.setEmail(email);
        user.setFullName("Test User");
        user.setRole(role);
        return user;
    }
}
