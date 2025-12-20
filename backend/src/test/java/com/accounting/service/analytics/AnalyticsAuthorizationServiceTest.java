package com.accounting.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accounting.entity.User;
import com.accounting.service.analytics.AnalyticsAuthorizationService.AnalyticsPermission;
import com.accounting.service.analytics.AnalyticsAuthorizationService.WidgetScope;

@ExtendWith(MockitoExtension.class)
class AnalyticsAuthorizationServiceTest {

    private AnalyticsAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsAuthorizationService();
    }

    private User createUser(String role) {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setRole(role);
        return user;
    }

    @Nested
    @DisplayName("hasPermission tests")
    class HasPermissionTests {

        @Test
        void shouldReturnFalseForNullUser() {
            assertThat(service.hasPermission((User) null, AnalyticsPermission.VIEW_DASHBOARD)).isFalse();
        }

        @Test
        void shouldReturnFalseForNullRole() {
            User user = createUser(null);
            assertThat(service.hasPermission(user, AnalyticsPermission.VIEW_DASHBOARD)).isFalse();
        }

        @Test
        void shouldReturnFalseForNullRoleString() {
            assertThat(service.hasPermission((String) null, AnalyticsPermission.VIEW_DASHBOARD)).isFalse();
        }

        @Test
        void shouldReturnFalseForUnknownRole() {
            assertThat(service.hasPermission("UNKNOWN_ROLE", AnalyticsPermission.VIEW_DASHBOARD)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_GENERAL", "ACCOUNTANT_AR", "ACCOUNTANT_AP", "CASHIER", "ACCOUNTANT", "FINANCE"})
        void allRolesShouldHaveViewDashboardPermission(String role) {
            assertThat(service.hasPermission(role, AnalyticsPermission.VIEW_DASHBOARD)).isTrue();
        }

        @Test
        void adminShouldHaveAllPermissions() {
            User admin = createUser("ADMIN");
            assertThat(service.hasPermission(admin, AnalyticsPermission.VIEW_DASHBOARD)).isTrue();
            assertThat(service.hasPermission(admin, AnalyticsPermission.VIEW_ALL_WIDGETS)).isTrue();
            assertThat(service.hasPermission(admin, AnalyticsPermission.MANUAL_REFRESH)).isTrue();
            assertThat(service.hasPermission(admin, AnalyticsPermission.VIEW_ETL_STATUS)).isTrue();
            assertThat(service.hasPermission(admin, AnalyticsPermission.ANALYTICS_ADMIN)).isTrue();
        }

        @Test
        void cfoShouldHaveViewPermissionsButNotAdminOrRefresh() {
            User cfo = createUser("CFO");
            assertThat(service.hasPermission(cfo, AnalyticsPermission.VIEW_DASHBOARD)).isTrue();
            assertThat(service.hasPermission(cfo, AnalyticsPermission.VIEW_ALL_WIDGETS)).isTrue();
            assertThat(service.hasPermission(cfo, AnalyticsPermission.VIEW_ETL_STATUS)).isTrue();
            assertThat(service.hasPermission(cfo, AnalyticsPermission.MANUAL_REFRESH)).isFalse();
            assertThat(service.hasPermission(cfo, AnalyticsPermission.ANALYTICS_ADMIN)).isFalse();
        }

        @Test
        void chiefAccountantShouldHaveRefreshPermission() {
            assertThat(service.hasPermission("CHIEF_ACCOUNTANT", AnalyticsPermission.MANUAL_REFRESH)).isTrue();
        }

        @Test
        void accountantGeneralShouldOnlyHaveSummaryWidgets() {
            User accountant = createUser("ACCOUNTANT_GENERAL");
            assertThat(service.hasPermission(accountant, AnalyticsPermission.VIEW_DASHBOARD)).isTrue();
            assertThat(service.hasPermission(accountant, AnalyticsPermission.VIEW_SUMMARY_WIDGETS)).isTrue();
            assertThat(service.hasPermission(accountant, AnalyticsPermission.VIEW_ALL_WIDGETS)).isFalse();
        }

        @Test
        void roleNormalizationShouldHandleHyphensAndCase() {
            assertThat(service.hasPermission("chief-accountant", AnalyticsPermission.VIEW_ALL_WIDGETS)).isTrue();
            assertThat(service.hasPermission("Chief_Accountant", AnalyticsPermission.VIEW_ALL_WIDGETS)).isTrue();
        }
    }

    @Nested
    @DisplayName("getWidgetScope tests")
    class GetWidgetScopeTests {

        @Test
        void shouldReturnSummaryForNullUser() {
            assertThat(service.getWidgetScope((User) null)).isEqualTo(WidgetScope.SUMMARY);
        }

        @Test
        void shouldReturnSummaryForNullRole() {
            User user = createUser(null);
            assertThat(service.getWidgetScope(user)).isEqualTo(WidgetScope.SUMMARY);
        }

        @Test
        void shouldReturnSummaryForNullRoleString() {
            assertThat(service.getWidgetScope((String) null)).isEqualTo(WidgetScope.SUMMARY);
        }

        @Test
        void shouldReturnSummaryForUnknownRole() {
            assertThat(service.getWidgetScope("UNKNOWN")).isEqualTo(WidgetScope.SUMMARY);
        }

        @ParameterizedTest
        @CsvSource({
            "ADMIN, ALL",
            "CFO, ALL",
            "CHIEF_ACCOUNTANT, ALL",
            "ACCOUNTANT_GENERAL, SUMMARY",
            "ACCOUNTANT_AR, AR_ONLY",
            "ACCOUNTANT_AP, AP_ONLY",
            "CASHIER, CASH_ONLY",
            "ACCOUNTANT, SUMMARY",
            "FINANCE, SUMMARY"
        })
        void shouldReturnCorrectScopeForRole(String role, WidgetScope expectedScope) {
            assertThat(service.getWidgetScope(role)).isEqualTo(expectedScope);
        }

        @Test
        void shouldHandleRoleNormalization() {
            assertThat(service.getWidgetScope("accountant-ar")).isEqualTo(WidgetScope.AR_ONLY);
        }
    }

    @Nested
    @DisplayName("getAllowedWidgets tests")
    class GetAllowedWidgetsTests {

        @Test
        void adminShouldGetAllWidgets() {
            List<String> widgets = service.getAllowedWidgets("ADMIN");
            assertThat(widgets).containsExactlyInAnyOrder(
                "revenue-vs-expenses",
                "ar-ap-balances",
                "cash-position",
                "top-5-debtors",
                "top-5-creditors",
                "period-summary"
            );
        }

        @Test
        void accountantShouldGetOnlySummaryWidget() {
            List<String> widgets = service.getAllowedWidgets("ACCOUNTANT");
            assertThat(widgets).containsExactly("period-summary");
        }

        @Test
        void arAccountantShouldGetArWidgets() {
            List<String> widgets = service.getAllowedWidgets("ACCOUNTANT_AR");
            assertThat(widgets).containsExactlyInAnyOrder("ar-ap-balances", "top-5-debtors");
        }

        @Test
        void apAccountantShouldGetApWidgets() {
            List<String> widgets = service.getAllowedWidgets("ACCOUNTANT_AP");
            assertThat(widgets).containsExactlyInAnyOrder("ar-ap-balances", "top-5-creditors");
        }

        @Test
        void cashierShouldGetCashWidget() {
            List<String> widgets = service.getAllowedWidgets("CASHIER");
            assertThat(widgets).containsExactly("cash-position");
        }

        @Test
        void nullUserShouldGetSummaryWidgets() {
            List<String> widgets = service.getAllowedWidgets((User) null);
            assertThat(widgets).containsExactly("period-summary");
        }
    }

    @Nested
    @DisplayName("canViewWidget tests")
    class CanViewWidgetTests {

        @Test
        void adminCanViewAnyWidget() {
            User admin = createUser("ADMIN");
            assertThat(service.canViewWidget(admin, "revenue-vs-expenses")).isTrue();
            assertThat(service.canViewWidget(admin, "cash-position")).isTrue();
            assertThat(service.canViewWidget(admin, "top-5-debtors")).isTrue();
        }

        @Test
        void cashierCanOnlyViewCashPosition() {
            User cashier = createUser("CASHIER");
            assertThat(service.canViewWidget(cashier, "cash-position")).isTrue();
            assertThat(service.canViewWidget(cashier, "revenue-vs-expenses")).isFalse();
            assertThat(service.canViewWidget(cashier, "top-5-debtors")).isFalse();
        }

        @Test
        void canViewWidgetWithRoleString() {
            assertThat(service.canViewWidget("ADMIN", "cash-position")).isTrue();
            assertThat(service.canViewWidget("CASHIER", "revenue-vs-expenses")).isFalse();
        }
    }

    @Nested
    @DisplayName("convenience methods tests")
    class ConvenienceMethodsTests {

        @Test
        void canViewDashboardShouldDelegateToHasPermission() {
            User admin = createUser("ADMIN");
            assertThat(service.canViewDashboard(admin)).isTrue();

            User nullRoleUser = createUser(null);
            assertThat(service.canViewDashboard(nullRoleUser)).isFalse();
        }

        @Test
        void canManualRefreshShouldCheckAllowedRoles() {
            assertThat(service.canManualRefresh("ADMIN")).isTrue();
            assertThat(service.canManualRefresh("CHIEF_ACCOUNTANT")).isTrue();
            assertThat(service.canManualRefresh("CFO")).isFalse();
            assertThat(service.canManualRefresh("ACCOUNTANT")).isFalse();
        }

        @Test
        void canViewETLStatusShouldCheckAllowedRoles() {
            assertThat(service.canViewETLStatus("ADMIN")).isTrue();
            assertThat(service.canViewETLStatus("CFO")).isTrue();
            assertThat(service.canViewETLStatus("CHIEF_ACCOUNTANT")).isTrue();
            assertThat(service.canViewETLStatus("ACCOUNTANT")).isFalse();
        }

        @Test
        void isAnalyticsAdminShouldOnlyReturnTrueForAdmin() {
            assertThat(service.isAnalyticsAdmin("ADMIN")).isTrue();
            assertThat(service.isAnalyticsAdmin("CFO")).isFalse();
            assertThat(service.isAnalyticsAdmin("CHIEF_ACCOUNTANT")).isFalse();
        }
    }

    @Nested
    @DisplayName("getPermissions tests")
    class GetPermissionsTests {

        @Test
        void shouldReturnEmptySetForNullRole() {
            assertThat(service.getPermissions(null)).isEmpty();
        }

        @Test
        void shouldReturnEmptySetForUnknownRole() {
            assertThat(service.getPermissions("UNKNOWN")).isEmpty();
        }

        @Test
        void shouldReturnCorrectPermissionsForAdmin() {
            Set<AnalyticsPermission> permissions = service.getPermissions("ADMIN");
            assertThat(permissions).containsExactlyInAnyOrder(
                AnalyticsPermission.VIEW_DASHBOARD,
                AnalyticsPermission.VIEW_ALL_WIDGETS,
                AnalyticsPermission.MANUAL_REFRESH,
                AnalyticsPermission.VIEW_ETL_STATUS,
                AnalyticsPermission.ANALYTICS_ADMIN
            );
        }
    }
}
