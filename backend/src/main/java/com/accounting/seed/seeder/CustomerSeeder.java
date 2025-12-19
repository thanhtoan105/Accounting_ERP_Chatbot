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

import com.accounting.entity.Customer;
import com.accounting.seed.UniqueGenerator;
import com.accounting.seed.VietnameseFaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerSeeder {

  private static final String INSERT_SQL =
      "INSERT INTO customers (company_id, code, name, tax_code, address, email, phone, active, "
          + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String SELECT_IDS_BY_CODES_SQL =
      "SELECT id, code FROM customers WHERE company_id = :companyId AND code IN (:codes)";

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  @Transactional
  public List<Customer> seedCustomers(
      Long companyId, int count, VietnameseFaker faker, UniqueGenerator generator, int year) {
    log.info("Seeding {} customers for company ID: {}", count, companyId);

    List<Customer> customers = new ArrayList<>(count);
    Set<String> usedTaxCodes = new HashSet<>();
    Instant now = Instant.now();

    for (int i = 0; i < count; i++) {
      Customer customer = new Customer();
      customer.setCompanyId(companyId);
      customer.setCode(generator.nextCustomerCode(companyId, year));
      customer.setName(faker.companyName());

      if (faker.getFaker().random().nextDouble() < 0.70) {
        customer.setTaxCode(generator.nextTaxCode(usedTaxCodes));
      }

      customer.setAddress(faker.address());
      customer.setEmail(faker.email(customer.getName()));
      customer.setPhone(faker.phoneNumber());
      customer.setActive(faker.getFaker().random().nextDouble() < 0.95);
      customer.setCreatedAt(now);
      customer.setUpdatedAt(now);
      customers.add(customer);
    }

    batchInsertCustomers(customers);

    log.info("Successfully seeded {} customers for company ID: {}", customers.size(), companyId);
    return customers;
  }

  private void batchInsertCustomers(List<Customer> customers) {
    jdbcTemplate.batchUpdate(
        INSERT_SQL,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            Customer customer = customers.get(i);
            ps.setLong(1, customer.getCompanyId());
            ps.setString(2, customer.getCode());
            ps.setString(3, customer.getName());
            if (customer.getTaxCode() != null) {
              ps.setString(4, customer.getTaxCode());
            } else {
              ps.setNull(4, Types.VARCHAR);
            }
            ps.setString(5, customer.getAddress());
            ps.setString(6, customer.getEmail());
            ps.setString(7, customer.getPhone());
            ps.setBoolean(8, customer.getActive());
            ps.setTimestamp(9, Timestamp.from(customer.getCreatedAt()));
            ps.setTimestamp(10, Timestamp.from(customer.getUpdatedAt()));
          }

          @Override
          public int getBatchSize() {
            return customers.size();
          }
        });

    List<String> codes = customers.stream().map(Customer::getCode).collect(Collectors.toList());
    Map<String, Object> params =
        Map.of("companyId", customers.get(0).getCompanyId(), "codes", codes);

    Map<String, Long> codeToIdMap =
        namedJdbcTemplate
            .queryForList(SELECT_IDS_BY_CODES_SQL, params)
            .stream()
            .collect(
                Collectors.toMap(
                    row -> (String) row.get("code"),
                    row -> ((Number) row.get("id")).longValue()));

    for (Customer customer : customers) {
      Long id = codeToIdMap.get(customer.getCode());
      if (id != null) {
        customer.setId(id);
      }
    }
  }
}
