package com.accounting.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.accounting.entity.User;
import com.accounting.integration.metabase.MetabaseApiClient;
import com.accounting.integration.metabase.dto.MetabaseDatabaseResponse;
import com.accounting.integration.metabase.dto.MetabaseGroupResponse;
import com.accounting.integration.metabase.dto.MetabaseUserResponse;
import com.accounting.service.analytics.MetabaseProvisioningService.ProvisioningResult;
import com.accounting.service.analytics.MetabaseProvisioningService.UserProvisioningResult;
import com.accounting.service.analytics.TenantAnalyticsProvisioningService.TenantCredentials;

@ExtendWith(MockitoExtension.class)
class MetabaseProvisioningServiceImplTest {

    private static final Long COMPANY_ID = 42L;
    private static final Long DATABASE_ID = 100L;
    private static final Long GROUP_ID = 200L;
    private static final Long USER_ID = 300L;

    @Mock
    private MetabaseApiClient metabaseApiClient;

    @Mock
    private TenantAnalyticsProvisioningService tenantDbService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MetabaseProvisioningServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MetabaseProvisioningServiceImpl(metabaseApiClient, tenantDbService, redisTemplate);
        ReflectionTestUtils.setField(service, "dbHost", "localhost");
        ReflectionTestUtils.setField(service, "dbPort", 5432);
        ReflectionTestUtils.setField(service, "dbName", "accounting_test");
    }

    private User createUser(Long id, String email, String fullName, String role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        return user;
    }

    @Nested
    @DisplayName("provisionTenant tests")
    class ProvisionTenantTests {

        @Test
        @DisplayName("provisionTenant_success_createsDbAndGroup")
        void provisionTenant_success_createsDbAndGroup() {
            var dbResult = TenantAnalyticsProvisioningService.ProvisioningResult.success("role_42", "tenant_42");
            var credentials = new TenantCredentials("role_42", "tenant_42", "password123", "jdbc:postgresql://localhost:5432/accounting");

            when(tenantDbService.provisionTenant(COMPANY_ID)).thenReturn(dbResult);
            when(tenantDbService.getTenantCredentials(COMPANY_ID)).thenReturn(Optional.of(credentials));
            when(metabaseApiClient.getDatabases()).thenReturn(List.of());
            when(metabaseApiClient.createDatabaseConnection(anyString(), anyString(), eq(5432), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(new MetabaseDatabaseResponse(DATABASE_ID, "Company 42", "postgres", null, false));
            when(metabaseApiClient.getGroups()).thenReturn(List.of());
            when(metabaseApiClient.createGroup("company_42"))
                    .thenReturn(new MetabaseGroupResponse(GROUP_ID, "company_42", 0));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            ProvisioningResult result = service.provisionTenant(COMPANY_ID);

            assertThat(result.success()).isTrue();
            assertThat(result.databaseId()).isEqualTo(DATABASE_ID);
            assertThat(result.groupId()).isEqualTo(GROUP_ID);
            verify(metabaseApiClient).authenticate();
            verify(metabaseApiClient).createDatabaseConnection(eq("Company 42"), eq("localhost"), eq(5432), eq("accounting_test"), eq("role_42"), eq("password123"), eq("tenant_42"));
            verify(metabaseApiClient).createGroup("company_42");
        }

        @Test
        @DisplayName("provisionTenant_idempotent_skipsIfExists")
        void provisionTenant_idempotent_skipsIfExists() {
            var dbResult = TenantAnalyticsProvisioningService.ProvisioningResult.success("role_42", "tenant_42");
            var credentials = new TenantCredentials("role_42", "tenant_42", "password123", "jdbc:postgresql://localhost:5432/accounting");

            when(tenantDbService.provisionTenant(COMPANY_ID)).thenReturn(dbResult);
            when(tenantDbService.getTenantCredentials(COMPANY_ID)).thenReturn(Optional.of(credentials));
            when(metabaseApiClient.getDatabases()).thenReturn(
                    List.of(new MetabaseDatabaseResponse(DATABASE_ID, "Company 42", "postgres", null, false)));
            when(metabaseApiClient.getGroups()).thenReturn(
                    List.of(new MetabaseGroupResponse(GROUP_ID, "company_42", 0)));
            when(metabaseApiClient.updateDatabaseConnection(eq(DATABASE_ID), any(), any(), any(), any(), eq("role_42"), eq("password123"), eq("tenant_42")))
                    .thenReturn(new MetabaseDatabaseResponse(DATABASE_ID, "Company 42", "postgres", null, false));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            ProvisioningResult result = service.provisionTenant(COMPANY_ID);

            assertThat(result.success()).isTrue();
            assertThat(result.databaseId()).isEqualTo(DATABASE_ID);
            assertThat(result.groupId()).isEqualTo(GROUP_ID);
            verify(metabaseApiClient, never()).createDatabaseConnection(anyString(), anyString(), eq(5432), anyString(), anyString(), anyString(), anyString());
            verify(metabaseApiClient, never()).createGroup(anyString());
        }

        @Test
        @DisplayName("provisionTenant_failsIfDbProvisioningFails")
        void provisionTenant_failsIfDbProvisioningFails() {
            var dbResult = TenantAnalyticsProvisioningService.ProvisioningResult.failure("DB error");
            when(tenantDbService.provisionTenant(COMPANY_ID)).thenReturn(dbResult);

            ProvisioningResult result = service.provisionTenant(COMPANY_ID);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("Database provisioning failed");
        }

        @Test
        @DisplayName("provisionTenant_failsIfCredentialsMissing")
        void provisionTenant_failsIfCredentialsMissing() {
            var dbResult = TenantAnalyticsProvisioningService.ProvisioningResult.success("role_42", "tenant_42");
            when(tenantDbService.provisionTenant(COMPANY_ID)).thenReturn(dbResult);
            when(tenantDbService.getTenantCredentials(COMPANY_ID)).thenReturn(Optional.empty());

            ProvisioningResult result = service.provisionTenant(COMPANY_ID);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("Failed to get tenant credentials");
        }
    }

    @Nested
    @DisplayName("provisionUser tests")
    class ProvisionUserTests {

        @Test
        @DisplayName("provisionUser_success_createsUserAndAssignsGroups")
        void provisionUser_success_createsUserAndAssignsGroups() {
            User user = createUser(1L, "test@example.com", "John Doe", "ADMIN");
            when(metabaseApiClient.findUserByEmail("test@example.com")).thenReturn(Optional.empty());
            when(metabaseApiClient.createUser("test@example.com", "John", "Doe"))
                    .thenReturn(new MetabaseUserResponse(USER_ID, "test@example.com", "John", "Doe", true, false, List.of()));
            when(metabaseApiClient.getGroups()).thenReturn(
                    List.of(new MetabaseGroupResponse(GROUP_ID, "company_42", 0),
                            new MetabaseGroupResponse(201L, "Analytics Admins", 0)));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("metabase:tenant:42:group_id")).thenReturn(GROUP_ID.toString());

            UserProvisioningResult result = service.provisionUser(user, COMPANY_ID);

            assertThat(result.success()).isTrue();
            assertThat(result.metabaseUserId()).isEqualTo(USER_ID);
            verify(metabaseApiClient).authenticate();
            verify(metabaseApiClient).createUser("test@example.com", "John", "Doe");
            verify(metabaseApiClient).addUserToGroup(USER_ID, GROUP_ID);
        }

        @Test
        @DisplayName("provisionUser_existingUser_updatesInsteadOfCreate")
        void provisionUser_existingUser_updatesInsteadOfCreate() {
            User user = createUser(1L, "existing@example.com", "Jane Smith", "CFO");
            when(metabaseApiClient.findUserByEmail("existing@example.com"))
                    .thenReturn(Optional.of(new MetabaseUserResponse(USER_ID, "existing@example.com", "Jane", "Smith", true, false, List.of())));
            when(metabaseApiClient.getGroups()).thenReturn(
                    List.of(new MetabaseGroupResponse(GROUP_ID, "company_42", 0),
                            new MetabaseGroupResponse(202L, "Analytics Viewers", 0)));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("metabase:tenant:42:group_id")).thenReturn(GROUP_ID.toString());

            UserProvisioningResult result = service.provisionUser(user, COMPANY_ID);

            assertThat(result.success()).isTrue();
            assertThat(result.metabaseUserId()).isEqualTo(USER_ID);
            verify(metabaseApiClient, never()).createUser(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("syncUser tests")
    class SyncUserTests {

        @Test
        @DisplayName("syncUser_updatesNameAndEmail")
        void syncUser_updatesNameAndEmail() {
            User user = createUser(1L, "updated@example.com", "Updated Name", "ACCOUNTANT");
            when(metabaseApiClient.findUserByEmail("updated@example.com"))
                    .thenReturn(Optional.of(new MetabaseUserResponse(USER_ID, "old@example.com", "Old", "Name", true, false, List.of())));
            when(metabaseApiClient.updateUser(USER_ID, "updated@example.com", "Updated", "Name"))
                    .thenReturn(new MetabaseUserResponse(USER_ID, "updated@example.com", "Updated", "Name", true, false, List.of()));
            when(metabaseApiClient.getGroups()).thenReturn(
                    List.of(new MetabaseGroupResponse(GROUP_ID, "company_42", 0),
                            new MetabaseGroupResponse(203L, "Analytics Basic", 0)));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("metabase:tenant:42:group_id")).thenReturn(GROUP_ID.toString());

            UserProvisioningResult result = service.syncUser(user, COMPANY_ID);

            assertThat(result.success()).isTrue();
            assertThat(result.message()).contains("updated");
            verify(metabaseApiClient).updateUser(USER_ID, "updated@example.com", "Updated", "Name");
        }

        @Test
        @DisplayName("syncUser_createsUserIfNotExists")
        void syncUser_createsUserIfNotExists() {
            User user = createUser(1L, "new@example.com", "New User", "ACCOUNTANT");
            when(metabaseApiClient.findUserByEmail("new@example.com")).thenReturn(Optional.empty());
            when(metabaseApiClient.createUser("new@example.com", "New", "User"))
                    .thenReturn(new MetabaseUserResponse(USER_ID, "new@example.com", "New", "User", true, false, List.of()));
            when(metabaseApiClient.getGroups()).thenReturn(
                    List.of(new MetabaseGroupResponse(GROUP_ID, "company_42", 0),
                            new MetabaseGroupResponse(203L, "Analytics Basic", 0)));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("metabase:tenant:42:group_id")).thenReturn(GROUP_ID.toString());

            UserProvisioningResult result = service.syncUser(user, COMPANY_ID);

            assertThat(result.success()).isTrue();
            verify(metabaseApiClient).createUser("new@example.com", "New", "User");
        }
    }

    @Nested
    @DisplayName("deactivateUser tests")
    class DeactivateUserTests {

        @Test
        @DisplayName("deactivateUser_marksUserInactive")
        void deactivateUser_marksUserInactive() {
            User user = createUser(1L, "deactivate@example.com", "To Deactivate", "ACCOUNTANT");
            when(metabaseApiClient.findUserByEmail("deactivate@example.com"))
                    .thenReturn(Optional.of(new MetabaseUserResponse(USER_ID, "deactivate@example.com", "To", "Deactivate", true, false, List.of())));

            service.deactivateUser(user);

            verify(metabaseApiClient).authenticate();
            verify(metabaseApiClient).deactivateUser(USER_ID);
        }

        @Test
        @DisplayName("deactivateUser_doesNothingIfUserNotFound")
        void deactivateUser_doesNothingIfUserNotFound() {
            User user = createUser(1L, "notfound@example.com", "Not Found", "ACCOUNTANT");
            when(metabaseApiClient.findUserByEmail("notfound@example.com")).thenReturn(Optional.empty());

            service.deactivateUser(user);

            verify(metabaseApiClient, never()).deactivateUser(anyLong());
        }
    }

    @Nested
    @DisplayName("assignUserGroups tests")
    class AssignUserGroupsTests {

        @ParameterizedTest
        @CsvSource({
            "ADMIN, Analytics Admins",
            "CFO, Analytics Viewers",
            "CHIEF_ACCOUNTANT, Analytics Power Users",
            "ACCOUNTANT, Analytics Basic"
        })
        @DisplayName("assignUserGroups_mapsRolesToMetabaseGroups")
        void assignUserGroups_mapsRolesToMetabaseGroups(String role, String expectedGroup) {
            User user = createUser(1L, "user@example.com", "Test User", role);
            when(metabaseApiClient.findUserByEmail("user@example.com"))
                    .thenReturn(Optional.of(new MetabaseUserResponse(USER_ID, "user@example.com", "Test", "User", true, false, List.of())));
            when(metabaseApiClient.getGroups()).thenReturn(
                    List.of(new MetabaseGroupResponse(GROUP_ID, "company_42", 0),
                            new MetabaseGroupResponse(201L, expectedGroup, 0)));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("metabase:tenant:42:group_id")).thenReturn(GROUP_ID.toString());

            service.assignUserGroups(user, COMPANY_ID, Set.of(role));

            verify(metabaseApiClient).authenticate();
            verify(metabaseApiClient).addUserToGroup(USER_ID, GROUP_ID);
            verify(metabaseApiClient).addUserToGroup(USER_ID, 201L);
        }

        @Test
        @DisplayName("assignUserGroups_doesNothingIfUserNotFound")
        void assignUserGroups_doesNothingIfUserNotFound() {
            User user = createUser(1L, "missing@example.com", "Missing User", "ADMIN");
            when(metabaseApiClient.findUserByEmail("missing@example.com")).thenReturn(Optional.empty());

            service.assignUserGroups(user, COMPANY_ID, Set.of("ADMIN"));

            verify(metabaseApiClient, never()).addUserToGroup(anyLong(), anyLong());
        }

        @Test
        @DisplayName("assignUserGroups_handlesUnknownRole")
        void assignUserGroups_handlesUnknownRole() {
            User user = createUser(1L, "user@example.com", "Test User", "UNKNOWN_ROLE");
            when(metabaseApiClient.findUserByEmail("user@example.com"))
                    .thenReturn(Optional.of(new MetabaseUserResponse(USER_ID, "user@example.com", "Test", "User", true, false, List.of())));
            when(metabaseApiClient.getGroups()).thenReturn(List.of());
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("metabase:tenant:42:group_id")).thenReturn(null);

            service.assignUserGroups(user, COMPANY_ID, Set.of("UNKNOWN_ROLE"));

            verify(metabaseApiClient, never()).addUserToGroup(anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("isTenantProvisioned tests")
    class IsTenantProvisionedTests {

        @Test
        @DisplayName("isTenantProvisioned_returnsTrueWhenKeyExists")
        void isTenantProvisioned_returnsTrueWhenKeyExists() {
            when(redisTemplate.hasKey("metabase:tenant:42:database_id")).thenReturn(true);

            boolean result = service.isTenantProvisioned(COMPANY_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isTenantProvisioned_returnsFalseWhenKeyMissing")
        void isTenantProvisioned_returnsFalseWhenKeyMissing() {
            when(redisTemplate.hasKey("metabase:tenant:42:database_id")).thenReturn(false);

            boolean result = service.isTenantProvisioned(COMPANY_ID);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("findMetabaseUser tests")
    class FindMetabaseUserTests {

        @Test
        @DisplayName("findMetabaseUser_returnsUserWhenFound")
        void findMetabaseUser_returnsUserWhenFound() {
            var expectedUser = new MetabaseUserResponse(USER_ID, "found@example.com", "Found", "User", true, false, List.of());
            when(metabaseApiClient.findUserByEmail("found@example.com")).thenReturn(Optional.of(expectedUser));

            Optional<MetabaseUserResponse> result = service.findMetabaseUser("found@example.com");

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("findMetabaseUser_returnsEmptyWhenNotFound")
        void findMetabaseUser_returnsEmptyWhenNotFound() {
            when(metabaseApiClient.findUserByEmail("notfound@example.com")).thenReturn(Optional.empty());

            Optional<MetabaseUserResponse> result = service.findMetabaseUser("notfound@example.com");

            assertThat(result).isEmpty();
        }
    }
}
