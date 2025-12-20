package com.accounting.seed.seeder;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.User;
import com.accounting.enums.Role;
import com.accounting.seed.VietnameseFaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserSeeder {

  // BCrypt hash for "password" with 12 rounds (matching PasswordEncoder config)
  private static final String DEFAULT_PASSWORD_HASH =
      "$2a$12$tpDRxVsa4Dxjgs0Axayp5.Ocq/KSAm4z2RTqaW7epAURkTExrQeem";

  private static final String INSERT_SQL =
      "INSERT INTO users (email, password_hash, full_name, role, company_id, status, "
          + "failed_login_count, is_super_admin, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public List<User> seedUsers(Long companyId, String companyCode, int count, VietnameseFaker faker) {
    log.info("Seeding {} users for company {} (ID: {})", count, companyCode, companyId);

    List<User> users = new ArrayList<>(count);
    Instant now = Instant.now();

    for (int i = 0; i < count; i++) {
      User user = new User();
      user.setEmail(String.format("user%d@%s.demo.local", i + 1, companyCode.toLowerCase()));
      user.setPasswordHash(DEFAULT_PASSWORD_HASH);
      user.setFullName(faker.personName());
      user.setRole(assignRole(i, count).getValue());
      user.setCompanyId(companyId);
      user.setStatus("ACTIVE");
      user.setFailedLoginCount(0);
      user.setIsSuperAdmin(false);
      user.setCreatedAt(now);
      user.setUpdatedAt(now);
      users.add(user);
    }

    batchInsertUsers(users);

    log.info("Successfully seeded {} users for company {}", users.size(), companyCode);
    return users;
  }

  private Role assignRole(int index, int total) {
    if (index == 0) {
      return Role.ADMIN;
    }

    double position = (double) index / total;

    if (position < 0.20) {
      return Role.CHIEF_ACCOUNTANT;
    } else if (position < 0.50) {
      return Role.ACCOUNTANT;
    } else if (position < 0.70) {
      return index % 2 == 0 ? Role.ACCOUNTANT_AR : Role.ACCOUNTANT_AP;
    } else if (position < 0.85) {
      return Role.CASHIER;
    } else {
      return index % 2 == 0 ? Role.FINANCE : Role.CFO;
    }
  }

  private void batchInsertUsers(List<User> users) {
    KeyHolder keyHolder = new GeneratedKeyHolder();

    jdbcTemplate.batchUpdate(
        INSERT_SQL,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            User user = users.get(i);
            ps.setString(1, user.getEmail());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getRole());
            ps.setLong(5, user.getCompanyId());
            ps.setString(6, user.getStatus());
            ps.setInt(7, user.getFailedLoginCount());
            ps.setBoolean(8, user.getIsSuperAdmin());
            ps.setTimestamp(9, Timestamp.from(user.getCreatedAt()));
            ps.setTimestamp(10, Timestamp.from(user.getUpdatedAt()));
          }

          @Override
          public int getBatchSize() {
            return users.size();
          }
        });

    List<Long> generatedIds = jdbcTemplate.queryForList(
        "SELECT id FROM users WHERE company_id = ? ORDER BY id DESC LIMIT ?",
        Long.class,
        users.get(0).getCompanyId(),
        users.size());

    for (int i = 0; i < users.size() && i < generatedIds.size(); i++) {
      users.get(users.size() - 1 - i).setId(generatedIds.get(i));
    }
  }
}
