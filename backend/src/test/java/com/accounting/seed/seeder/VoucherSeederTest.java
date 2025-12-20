package com.accounting.seed.seeder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.accounting.seed.TenantContext;
import com.accounting.seed.UniqueGenerator;
import com.accounting.seed.VietnameseFaker;

@ExtendWith(MockitoExtension.class)
class VoucherSeederTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  private VoucherSeeder voucherSeeder;

  @BeforeEach
  void setUp() {
    voucherSeeder = new VoucherSeeder(jdbcTemplate);
  }

  @Test
  void seedVouchers_withMissingRequiredAccounts_throwsIllegalStateException() {
    Map<String, Long> incompleteAccounts = new HashMap<>();
    incompleteAccounts.put("111", 1L);
    incompleteAccounts.put("112", 2L);

    TenantContext ctx = TenantContext.builder()
        .companyId(1L)
        .companyCode("TEST")
        .accountsByCode(incompleteAccounts)
        .userIds(List.of(1L))
        .customerIds(List.of(1L))
        .supplierIds(List.of(1L))
        .bankAccountIds(List.of(1L))
        .periodIds(List.of(UUID.randomUUID()))
        .build();

    VietnameseFaker faker = new VietnameseFaker(12345L);
    UniqueGenerator generator = new UniqueGenerator(12345L);
    List<UUID> periodIds = List.of(UUID.randomUUID());
    Random random = new Random();

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> voucherSeeder.seedVouchers(ctx, 1, faker, generator, periodIds, random));

    assertTrue(exception.getMessage().contains("Missing required TT200 accounts"));
    assertTrue(exception.getMessage().contains("companyId=1"));
    assertTrue(exception.getMessage().contains("131"));
  }

  @Test
  void seedVouchers_withEmptyAccounts_throwsIllegalStateException() {
    TenantContext ctx = TenantContext.builder()
        .companyId(99L)
        .companyCode("EMPTY")
        .accountsByCode(Collections.emptyMap())
        .userIds(List.of(1L))
        .customerIds(List.of(1L))
        .supplierIds(List.of(1L))
        .bankAccountIds(List.of(1L))
        .periodIds(List.of(UUID.randomUUID()))
        .build();

    VietnameseFaker faker = new VietnameseFaker(12345L);
    UniqueGenerator generator = new UniqueGenerator(12345L);
    List<UUID> periodIds = List.of(UUID.randomUUID());
    Random random = new Random();

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> voucherSeeder.seedVouchers(ctx, 1, faker, generator, periodIds, random));

    assertTrue(exception.getMessage().contains("Missing required TT200 accounts"));
    assertTrue(exception.getMessage().contains("companyId=99"));
  }

  @Test
  void seedVouchers_errorMessageIncludesAllMissingAccounts() {
    Map<String, Long> partialAccounts = new HashMap<>();
    partialAccounts.put("111", 1L);
    partialAccounts.put("112", 2L);
    partialAccounts.put("131", 3L);

    TenantContext ctx = TenantContext.builder()
        .companyId(5L)
        .companyCode("PARTIAL")
        .accountsByCode(partialAccounts)
        .userIds(List.of(1L))
        .customerIds(List.of(1L))
        .supplierIds(List.of(1L))
        .bankAccountIds(List.of(1L))
        .periodIds(List.of(UUID.randomUUID()))
        .build();

    VietnameseFaker faker = new VietnameseFaker(12345L);
    UniqueGenerator generator = new UniqueGenerator(12345L);
    List<UUID> periodIds = List.of(UUID.randomUUID());
    Random random = new Random();

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> voucherSeeder.seedVouchers(ctx, 1, faker, generator, periodIds, random));

    String message = exception.getMessage();
    assertTrue(message.contains("331"));
    assertTrue(message.contains("511"));
    assertTrue(message.contains("1331"));
    assertTrue(message.contains("3331"));
    assertTrue(message.contains("156"));
    assertTrue(message.contains("152"));
    assertTrue(message.contains("642"));
  }

  @Test
  void seedVouchers_withPrefixMatchAccounts_passesValidation() {
    Map<String, Long> prefixAccounts = new HashMap<>();
    prefixAccounts.put("1111", 1L);
    prefixAccounts.put("1121", 2L);
    prefixAccounts.put("1311", 3L);
    prefixAccounts.put("3311", 4L);
    prefixAccounts.put("5111", 5L);
    prefixAccounts.put("13311", 6L);
    prefixAccounts.put("33311", 7L);
    prefixAccounts.put("1561", 8L);
    prefixAccounts.put("1521", 9L);
    prefixAccounts.put("6421", 10L);
    prefixAccounts.put("6411", 11L);
    prefixAccounts.put("6271", 12L);
    prefixAccounts.put("1541", 13L);
    prefixAccounts.put("3341", 14L);
    prefixAccounts.put("3381", 15L);
    prefixAccounts.put("2141", 16L);
    prefixAccounts.put("3351", 17L);

    TenantContext ctx = TenantContext.builder()
        .companyId(1L)
        .companyCode("PREFIX")
        .accountsByCode(prefixAccounts)
        .userIds(List.of(1L))
        .customerIds(List.of(1L))
        .supplierIds(List.of(1L))
        .bankAccountIds(List.of(1L))
        .periodIds(List.of(UUID.randomUUID()))
        .build();

    VietnameseFaker faker = new VietnameseFaker(12345L);
    UniqueGenerator generator = new UniqueGenerator(12345L);
    List<UUID> periodIds = List.of(UUID.randomUUID());
    Random random = new Random();

    Exception exception = assertThrows(
        Exception.class,
        () -> voucherSeeder.seedVouchers(ctx, 1, faker, generator, periodIds, random));

    assertFalse(exception.getMessage().contains("Missing required TT200 accounts"),
        "Validation should pass with prefix-matched accounts");
  }
}
