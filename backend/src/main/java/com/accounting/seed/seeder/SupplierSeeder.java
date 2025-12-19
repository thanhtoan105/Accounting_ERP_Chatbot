package com.accounting.seed.seeder;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.Supplier;
import com.accounting.seed.UniqueGenerator;
import com.accounting.seed.VietnameseFaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class SupplierSeeder {

  private static final String INSERT_SQL =
      "INSERT INTO suppliers (company_id, code, name, tax_code, address, email, phone, active, "
          + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String SELECT_IDS_BY_CODES_SQL =
      "SELECT id, code FROM suppliers WHERE company_id = :companyId AND code IN (:codes)";

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  @Transactional
  public List<Supplier> seedSuppliers(
      Long companyId, int count, VietnameseFaker faker, UniqueGenerator generator, int year) {
    log.info("Seeding {} suppliers for company ID: {}", count, companyId);

    List<Supplier> suppliers = new ArrayList<>(count);
    Set<String> usedTaxCodes = new HashSet<>();
    Instant now = Instant.now();

    for (int i = 0; i < count; i++) {
      Supplier supplier = new Supplier();
      supplier.setCompanyId(companyId);
      supplier.setCode(generator.nextSupplierCode(companyId, year));
      supplier.setName(faker.companyName());

      if (faker.getFaker().random().nextDouble() < 0.80) {
        supplier.setTaxCode(generator.nextTaxCode(usedTaxCodes));
      }

      supplier.setAddress(faker.address());
      supplier.setEmail(faker.email(supplier.getName()));
      supplier.setPhone(faker.phoneNumber());
      supplier.setActive(faker.getFaker().random().nextDouble() < 0.95);
      supplier.setCreatedAt(now);
      supplier.setUpdatedAt(now);
      suppliers.add(supplier);
    }

    batchInsertSuppliers(suppliers);

    log.info("Successfully seeded {} suppliers for company ID: {}", suppliers.size(), companyId);
    return suppliers;
  }

  private void batchInsertSuppliers(List<Supplier> suppliers) {
    jdbcTemplate.batchUpdate(
        INSERT_SQL,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            Supplier supplier = suppliers.get(i);
            ps.setLong(1, supplier.getCompanyId());
            ps.setString(2, supplier.getCode());
            ps.setString(3, supplier.getName());
            if (supplier.getTaxCode() != null) {
              ps.setString(4, supplier.getTaxCode());
            } else {
              ps.setNull(4, Types.VARCHAR);
            }
            ps.setString(5, supplier.getAddress());
            ps.setString(6, supplier.getEmail());
            ps.setString(7, supplier.getPhone());
            ps.setBoolean(8, supplier.getActive());
            ps.setTimestamp(9, Timestamp.from(supplier.getCreatedAt()));
            ps.setTimestamp(10, Timestamp.from(supplier.getUpdatedAt()));
          }

          @Override
          public int getBatchSize() {
            return suppliers.size();
          }
        });

    List<String> codes = suppliers.stream().map(Supplier::getCode).collect(Collectors.toList());
    Map<String, Object> params =
        Map.of("companyId", suppliers.get(0).getCompanyId(), "codes", codes);

    Map<String, Long> codeToIdMap =
        namedJdbcTemplate
            .queryForList(SELECT_IDS_BY_CODES_SQL, params)
            .stream()
            .collect(
                Collectors.toMap(
                    row -> (String) row.get("code"),
                    row -> ((Number) row.get("id")).longValue()));

    for (Supplier supplier : suppliers) {
      Long id = codeToIdMap.get(supplier.getCode());
      if (id != null) {
        supplier.setId(id);
      }
    }
  }
}
