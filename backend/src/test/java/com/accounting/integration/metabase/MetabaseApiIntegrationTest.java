package com.accounting.integration.metabase;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Requires Docker and takes ~2-5 minutes to start Metabase. Enable manually for integration testing.")
public class MetabaseApiIntegrationTest {

    @Container
    static GenericContainer<?> metabase = MetabaseTestContainer.createContainer();

    private static String sessionToken;
    private static String metabaseUrl;

    @Autowired
    private MetabaseApiClient metabaseApiClient;

    @DynamicPropertySource
    static void metabaseProperties(DynamicPropertyRegistry registry) {
        metabaseUrl = MetabaseTestContainer.getMetabaseUrl(metabase);
        registry.add("metabase.site-url", () -> metabaseUrl);
        registry.add("metabase.admin-email", () -> MetabaseTestContainer.ADMIN_EMAIL);
        registry.add("metabase.admin-password", () -> MetabaseTestContainer.ADMIN_PASSWORD);
        registry.add("metabase.api-key", () -> "");
    }

    @BeforeAll
    static void setupMetabase() throws IOException {
        metabaseUrl = MetabaseTestContainer.getMetabaseUrl(metabase);
        sessionToken = MetabaseTestContainer.setupMetabaseAndGetSessionToken(metabaseUrl);
    }

    @Test
    @Order(1)
    void testMetabaseContainerIsRunning() {
        assertThat(metabase.isRunning()).isTrue();
    }

    @Test
    @Order(2)
    void testMetabaseHealthCheck() {
        boolean isHealthy = MetabaseTestContainer.checkHealth(metabaseUrl);
        assertThat(isHealthy).isTrue();
    }

    @Test
    @Order(3)
    void testMetabaseSetupCompleted() {
        assertThat(sessionToken).isNotNull();
        assertThat(sessionToken).isNotEmpty();
    }

    @Test
    @Order(4)
    void testMetabaseApiClientAuthentication() {
        String token = metabaseApiClient.authenticate();
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
    }

    @Test
    @Order(5)
    void testUserProvisioningStub() {
        // TODO: Implement full user provisioning test
        // Steps:
        // 1. Create a new user via metabaseApiClient.createUser()
        // 2. Verify user exists via metabaseApiClient.findUserByEmail()
        // 3. Update user via metabaseApiClient.updateUser()
        // 4. Verify changes
        // 5. Deactivate user via metabaseApiClient.deactivateUser()
        assertThat(metabaseApiClient).isNotNull();
    }

    @Test
    @Order(6)
    void testGroupManagementStub() {
        // TODO: Implement group management test
        // Steps:
        // 1. Create a group via metabaseApiClient.createGroup()
        // 2. Verify group in list via metabaseApiClient.getGroups()
        // 3. Add user to group via metabaseApiClient.addUserToGroup()
        // 4. Remove user from group via metabaseApiClient.removeUserFromGroup()
        assertThat(metabaseApiClient).isNotNull();
    }

    @Test
    @Order(7)
    void testDatabaseConnectionStub() {
        // TODO: Implement database connection test
        // Steps:
        // 1. Create a database connection via metabaseApiClient.createDatabaseConnection()
        // 2. Verify connection in list via metabaseApiClient.getDatabases()
        // 3. Update connection via metabaseApiClient.updateDatabaseConnection()
        assertThat(metabaseApiClient).isNotNull();
    }

    @Test
    @Order(8)
    void testJwtSsoFlowStub() {
        // TODO: Implement JWT SSO flow test
        // This would require the MetabaseEmbedService to be configured
        // Steps:
        // 1. Generate embed token
        // 2. Verify token structure
        // 3. Verify embed URL generation
        assertThat(metabaseApiClient).isNotNull();
    }
}
