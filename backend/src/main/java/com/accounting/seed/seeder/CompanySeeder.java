package com.accounting.seed.seeder;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.Company;
import com.accounting.seed.SeedProperties;
import com.accounting.seed.UniqueGenerator;
import com.accounting.seed.VietnameseFaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanySeeder {

    private final JdbcTemplate jdbcTemplate;
    private final SeedProperties seedProperties;

    private static final String INSERT_SQL =
            "INSERT INTO companies (code, name, tax_code, address, contact_email, contact_phone, fiscal_year_start, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

    @Transactional
    public List<Company> seedCompanies(int count, VietnameseFaker faker, UniqueGenerator generator) {
        log.info("Seeding {} companies", count);

        List<Company> existingCompanies = loadExistingDemoCompanies();
        if (!existingCompanies.isEmpty()) {
            log.info("Found {} existing DEMO companies, reusing them", existingCompanies.size());
            return existingCompanies;
        }

        Set<String> existingTaxCodes = new HashSet<>();
        List<Company> companies = new ArrayList<>(count);
        LocalDate fiscalYearStart = LocalDate.of(LocalDate.now().getYear(), 1, 1);

        for (int i = 0; i < count; i++) {
            Company company = new Company();
            company.setCode(generator.nextCompanyCode());
            company.setName(faker.companyName());
            company.setTaxCode(generator.nextTaxCode(existingTaxCodes));
            company.setAddress(faker.address());
            String name = company.getName();
            company.setContactEmail(faker.email(name));
            company.setContactPhone(faker.phoneNumber());
            company.setFiscalYearStart(fiscalYearStart);
            companies.add(company);
        }

        batchInsert(companies);
        fetchAndSetIds(companies);

        log.info("Successfully seeded {} companies", companies.size());
        return companies;
    }

    private List<Company> loadExistingDemoCompanies() {
        String sql = "SELECT id, code, name, tax_code, address, contact_email, contact_phone, fiscal_year_start "
                + "FROM companies WHERE code LIKE 'DEMO-%' ORDER BY code";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Company c = new Company();
            c.setId(rs.getLong("id"));
            c.setCode(rs.getString("code"));
            c.setName(rs.getString("name"));
            c.setTaxCode(rs.getString("tax_code"));
            c.setAddress(rs.getString("address"));
            c.setContactEmail(rs.getString("contact_email"));
            c.setContactPhone(rs.getString("contact_phone"));
            java.sql.Date fys = rs.getDate("fiscal_year_start");
            if (fys != null) {
                c.setFiscalYearStart(fys.toLocalDate());
            }
            return c;
        });
    }

    private void batchInsert(List<Company> companies) {
        jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Company company = companies.get(i);
                ps.setString(1, company.getCode());
                ps.setString(2, company.getName());
                ps.setString(3, company.getTaxCode());
                ps.setString(4, company.getAddress());
                if (company.getContactEmail() != null) {
                    ps.setString(5, company.getContactEmail());
                } else {
                    ps.setNull(5, Types.VARCHAR);
                }
                if (company.getContactPhone() != null) {
                    ps.setString(6, company.getContactPhone());
                } else {
                    ps.setNull(6, Types.VARCHAR);
                }
                if (company.getFiscalYearStart() != null) {
                    ps.setDate(7, Date.valueOf(company.getFiscalYearStart()));
                } else {
                    ps.setNull(7, Types.DATE);
                }
            }

            @Override
            public int getBatchSize() {
                return companies.size();
            }
        });
    }

    private void fetchAndSetIds(List<Company> companies) {
        List<String> codes = companies.stream().map(Company::getCode).toList();
        String placeholders = String.join(",", codes.stream().map(c -> "?").toList());
        String selectSql = "SELECT id, code FROM companies WHERE code IN (" + placeholders + ")";

        Map<String, Long> codeToIdMap =
                jdbcTemplate.query(
                        selectSql,
                        codes.toArray(),
                        rs -> {
                            Map<String, Long> map = new HashMap<>();
                            while (rs.next()) {
                                map.put(rs.getString("code"), rs.getLong("id"));
                            }
                            return map;
                        });

        for (Company company : companies) {
            company.setId(codeToIdMap.get(company.getCode()));
        }
    }
}
