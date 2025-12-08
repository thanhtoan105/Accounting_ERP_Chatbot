package com.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;

@SpringBootTest
class DemoBootstrapServiceTest extends com.accounting.test.IntegrationTest {

    @Autowired
    private DemoBootstrapService demoBootstrapService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChartOfAccountsRepository chartOfAccountsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void bootstrapIsIdempotent() {
        Map<String, Object> first = demoBootstrapService.bootstrapDemoCompany();
        Map<String, Object> second = demoBootstrapService.bootstrapDemoCompany();

        assertThat(first.get("code")).isEqualTo("DEMO");
        assertThat(first.get("companyId")).isNotNull();
        assertThat(first.get("created")).isEqualTo(true);

        assertThat(second.get("code")).isEqualTo("DEMO");
        assertThat(second.get("companyId")).isEqualTo(first.get("companyId"));
        assertThat(second.get("created")).isEqualTo(false);

        Long companyId = (Long) first.get("companyId");
        assertThat(companyId).isNotNull();
        Company demo = companyRepository.findById(companyId).orElseThrow();
        assertThat(demo.getName()).endsWith("[DEMO]");

        // Users seeded for each role
        assertThat(userRepository.existsByEmail("admin@demo.local")).isTrue();
        assertThat(userRepository.existsByEmail("accountant@demo.local")).isTrue();
        assertThat(userRepository.existsByEmail("chief@demo.local")).isTrue();
        assertThat(userRepository.existsByEmail("cfo@demo.local")).isTrue();
    }

    @Test
    @Transactional
    void bootstrapCreatesDemoCompanyWithCorrectSuffix() {
        // AC #4: Demo company name includes "[DEMO]" suffix
        Map<String, Object> result = demoBootstrapService.bootstrapDemoCompany();
        Long companyId = (Long) result.get("companyId");
        assertThat(companyId).isNotNull();
        Company demo = companyRepository.findById(companyId).orElseThrow();

        assertThat(demo.getName()).endsWith("[DEMO]");
        assertThat(demo.getCode()).isEqualTo("DEMO");
    }

    @Test
    @Transactional
    void bootstrapCreatesUsersForAllCoreRoles() {
        // AC #2: One user for each core role: admin, accountant, chief_accountant, cfo
        demoBootstrapService.bootstrapDemoCompany();

        User admin = userRepository.findByEmail("admin@demo.local").orElseThrow();
        User accountant = userRepository.findByEmail("accountant@demo.local").orElseThrow();
        User chief = userRepository.findByEmail("chief@demo.local").orElseThrow();
        User cfo = userRepository.findByEmail("cfo@demo.local").orElseThrow();

        assertThat(admin.getRole()).isEqualTo("admin");
        assertThat(accountant.getRole()).isEqualTo("accountant");
        assertThat(chief.getRole()).isEqualTo("chief_accountant");
        assertThat(cfo.getRole()).isEqualTo("cfo");

        // AC #2: Verify users can authenticate (password is hashed)
        assertThat(admin.getPasswordHash()).isNotNull();
        assertThat(passwordEncoder.matches("Demo@12345", admin.getPasswordHash())).isTrue();
    }

    @Test
    @Transactional
    void bootstrapSeedsValidTT200ChartOfAccounts() {
        // AC #3: Seeded COA is valid TT200 subset with postable leaf accounts
        Map<String, Object> result = demoBootstrapService.bootstrapDemoCompany();
        Long companyId = (Long) result.get("companyId");

        List<ChartOfAccount> allAccounts = chartOfAccountsRepository.findByCompanyId(companyId);
        assertThat(allAccounts).isNotEmpty();

        // Verify TT200 structure: should have root account "1" (Tài sản)
        ChartOfAccount rootAsset = allAccounts.stream()
                .filter(a -> "1".equals(a.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Root asset account '1' not found"));
        assertThat(rootAsset.getName()).contains("Tài sản");
        assertThat(rootAsset.getPostable()).isFalse(); // Root accounts are not postable

        // Verify postable leaf accounts exist (AC #3 requirement)
        List<ChartOfAccount> postableAccounts = chartOfAccountsRepository.findByCompanyIdAndPostableTrue(companyId);
        assertThat(postableAccounts).isNotEmpty();

        // Verify all postable accounts are leaf accounts (no children)
        for (ChartOfAccount account : postableAccounts) {
            assertThat(chartOfAccountsRepository.hasChildren(account.getId())).isFalse();
        }

        // Verify common transaction accounts exist (cash, bank, etc.)
        boolean hasCashAccount = allAccounts.stream()
                .anyMatch(a -> a.getCode().startsWith("111") && a.getPostable());
        boolean hasBankAccount = allAccounts.stream()
                .anyMatch(a -> a.getCode().startsWith("112") && a.getPostable());

        assertThat(hasCashAccount).isTrue();
        assertThat(hasBankAccount).isTrue();
    }

    @Test
    @Transactional
    void rollbackScriptRemovesOnlyDemoData() {
        // AC #5: Rollback script removes demo data without affecting other tenants
        // First, create demo data
        Map<String, Object> result = demoBootstrapService.bootstrapDemoCompany();
        Long demoCompanyId = (Long) result.get("companyId");
        assertThat(demoCompanyId).isNotNull();

        // Create another company to ensure rollback doesn't affect it
        Company otherCompany = new Company();
        otherCompany.setCode("OTHER");
        otherCompany.setName("Other Company");
        otherCompany.setTaxCode("9876543210");
        otherCompany.setAddress("Other Address");
        Company savedOther = companyRepository.save(otherCompany);

        // Verify demo data exists
        assertThat(companyRepository.findById(demoCompanyId)).isPresent();
        assertThat(userRepository.existsByEmail("admin@demo.local")).isTrue();
        assertThat(chartOfAccountsRepository.findByCompanyId(demoCompanyId)).isNotEmpty();

        // Execute rollback script
        jdbcTemplate.execute(
                "DO $$ " +
                        "DECLARE v_company_id BIGINT; " +
                        "BEGIN " +
                        "  SELECT id INTO v_company_id FROM companies WHERE code = 'DEMO' OR name LIKE '%[DEMO]'; " +
                        "  IF v_company_id IS NOT NULL THEN " +
                        "    DELETE FROM users WHERE company_id = v_company_id; " +
                        "    DELETE FROM chart_of_accounts WHERE company_id = v_company_id; " +
                        "    DELETE FROM companies WHERE id = v_company_id; " +
                        "  END IF; " +
                        "END $$;");

        // Verify demo data is removed using SQL count (bypasses JPA cache)
        Long demoCompanyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM companies WHERE id = ?", Long.class, demoCompanyId);
        assertThat(demoCompanyCount).isEqualTo(0L);

        Long demoUserCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Long.class, "admin@demo.local");
        assertThat(demoUserCount).isEqualTo(0L);

        Long demoCoaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chart_of_accounts WHERE company_id = ?", Long.class, demoCompanyId);
        assertThat(demoCoaCount).isEqualTo(0L);

        // Verify other company data is intact
        Long otherCompanyId = savedOther.getId();
        assertThat(otherCompanyId).isNotNull();
        Long otherCompanyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM companies WHERE id = ?", Long.class, otherCompanyId);
        assertThat(otherCompanyCount).isEqualTo(1L);
    }
}
