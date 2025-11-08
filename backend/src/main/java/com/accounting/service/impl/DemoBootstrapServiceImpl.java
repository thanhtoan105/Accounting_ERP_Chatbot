package com.accounting.service.impl;

import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.service.DemoBootstrapService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.accounting.security.CompanyContext;

@Service
public class DemoBootstrapServiceImpl implements DemoBootstrapService {

    private static final String DEMO_CODE = "DEMO";
    private static final String DEMO_COMPANY_NAME = "Accounting Corp [DEMO]";
    private static final String DEFAULT_DEMO_PASSWORD = "Demo@12345"; // documented in README only

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public DemoBootstrapServiceImpl(
            CompanyRepository companyRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Map<String, Object> bootstrapDemoCompany() {
        Map<String, Object> result = new HashMap<>();
        Company existing = companyRepository.findByCode(DEMO_CODE).orElse(null);
        boolean createdNow = false;
        if (existing == null) {
            Company c = new Company();
            c.setCode(DEMO_CODE);
            c.setName(DEMO_COMPANY_NAME);
            c.setTaxCode("0123456789");
            c.setAddress("123 Demo Street, Hà Nội");
            c.setLogoUrl(null);
            existing = companyRepository.save(c);
            createdNow = true;
        }
        // Seed minimal TT200 subset (use existing function if available) for the demo company
        try {
            Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM chart_of_accounts WHERE company_id = ?",
                    Long.class,
                    existing.getId());
            if (cnt != null && cnt == 0L) {
                jdbcTemplate.update("SELECT seed_tt200_coa_for_company(?)", existing.getId());
            }
        } catch (Exception ignored) {
            // Function may not exist or table not yet created; ignore to keep idempotency
        }

        // Ensure demo users exist (one per core role)
        ensureDemoUser(existing.getId(), "admin@demo.local", "Admin Demo", "admin");
        ensureDemoUser(existing.getId(), "accountant@demo.local", "Accountant Demo", "accountant");
        ensureDemoUser(existing.getId(), "chief@demo.local", "Chief Accountant Demo", "chief_accountant");
        ensureDemoUser(existing.getId(), "cfo@demo.local", "CFO Demo", "cfo");
        result.put("created", createdNow);
        result.put("companyId", existing.getId());
        result.put("code", existing.getCode());
        return result;
    }

    private void ensureDemoUser(Long companyId, String email, String fullName, String role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        // Ensure company scoping context for repository save
        CompanyContext.setCompanyId(companyId);
        try {
            User u = new User();
            u.setEmail(email);
            u.setFullName(fullName);
            u.setCompanyId(companyId);
            u.setRole(role);
            u.setPasswordHash(passwordEncoder.encode(DEFAULT_DEMO_PASSWORD));
            java.time.Instant now = java.time.Instant.now();
            u.setCreatedAt(now);
            u.setUpdatedAt(now);
            userRepository.save(u);
        } finally {
            CompanyContext.clear();
        }
    }
}
