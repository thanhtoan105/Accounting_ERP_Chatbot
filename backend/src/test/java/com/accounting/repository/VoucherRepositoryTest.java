package com.accounting.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.entity.Voucher;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VoucherRepositoryTest extends com.accounting.test.IntegrationTest {

  @Autowired private VoucherRepository voucherRepository;

  @Autowired private CompanyRepository companyRepository;

  @Autowired private UserRepository userRepository;

  private Company testCompany;
  private Company otherCompany;
  private User testUser;

  @BeforeEach
  void setUp() {
    voucherRepository.deleteAll();
    userRepository.deleteAll();
    companyRepository.deleteAll();

    testCompany = new Company();
    testCompany.setCode("TEST");
    testCompany.setName("Test Company");
    testCompany.setTaxCode("1234567890");
    testCompany.setAddress("Test Address");
    testCompany = companyRepository.save(testCompany);

    otherCompany = new Company();
    otherCompany.setCode("OTHER");
    otherCompany.setName("Other Company");
    otherCompany.setTaxCode("0987654321");
    otherCompany.setAddress("Other Address");
    otherCompany = companyRepository.save(otherCompany);

    testUser = new User();
    testUser.setEmail("test@example.com");
    testUser.setPasswordHash("hash");
    testUser.setFullName("Test User");
    testUser.setRole("accountant");
    testUser.setStatus("ACTIVE");
    testUser.setCompanyId(testCompany.getId());
    testUser.setCreatedAt(Instant.now());
    testUser.setUpdatedAt(Instant.now());
    testUser = userRepository.save(testUser);
  }

  @Test
  void findByCompanyId_returnsOnlyCompanyVouchers() {
    Voucher company1Voucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    Voucher company2Voucher =
        createVoucher(otherCompany.getId(), testUser.getId(), "VC2025-002", "draft");
    voucherRepository.save(company1Voucher);
    voucherRepository.save(company2Voucher);

    List<Voucher> company1Vouchers = voucherRepository.findByCompanyId(testCompany.getId());

    assertEquals(1, company1Vouchers.size());
    assertEquals("VC2025-001", company1Vouchers.get(0).getVoucherNumber());
  }

  @Test
  void findByCompanyIdAndId_findsCorrectVoucher() {
    Voucher voucher = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    voucher = voucherRepository.save(voucher);

    var result = voucherRepository.findByCompanyIdAndId(testCompany.getId(), voucher.getId());

    assertTrue(result.isPresent());
    assertEquals("VC2025-001", result.get().getVoucherNumber());
  }

  @Test
  void findByCompanyIdAndId_wrongCompany_returnsEmpty() {
    Voucher voucher = createVoucher(otherCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    voucher = voucherRepository.save(voucher);

    var result = voucherRepository.findByCompanyIdAndId(testCompany.getId(), voucher.getId());

    assertTrue(result.isEmpty());
  }

  @Test
  void findByCompanyIdAndStatus_filtersByStatus() {
    Voucher draft1 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    Voucher draft2 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-002", "draft");
    Voucher posted = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-003", "posted");
    voucherRepository.save(draft1);
    voucherRepository.save(draft2);
    voucherRepository.save(posted);

    List<Voucher> draftVouchers =
        voucherRepository.findByCompanyIdAndStatus(testCompany.getId(), "draft");

    assertEquals(2, draftVouchers.size());
    assertTrue(draftVouchers.stream().allMatch(v -> "draft".equals(v.getStatus())));
  }

  @Test
  void findByCompanyIdAndVoucherDateBetween_filtersByDateRange() {
    LocalDate fromDate = LocalDate.of(2025, 1, 1);
    LocalDate toDate = LocalDate.of(2025, 1, 31);
    Voucher inRange =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", LocalDate.of(2025, 1, 15), "draft");
    Voucher beforeRange =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-002", LocalDate.of(2024, 12, 31), "draft");
    Voucher afterRange =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-003", LocalDate.of(2025, 2, 1), "draft");
    voucherRepository.save(inRange);
    voucherRepository.save(beforeRange);
    voucherRepository.save(afterRange);

    List<Voucher> vouchersInRange =
        voucherRepository.findByCompanyIdAndVoucherDateBetween(
            testCompany.getId(), fromDate, toDate);

    assertEquals(1, vouchersInRange.size());
    assertEquals("VC2025-001", vouchersInRange.get(0).getVoucherNumber());
  }

  @Test
  void countByCompanyIdAndStatus_countsCorrectly() {
    Voucher draft1 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    Voucher draft2 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-002", "draft");
    Voucher posted = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-003", "posted");
    voucherRepository.save(draft1);
    voucherRepository.save(draft2);
    voucherRepository.save(posted);

    long draftCount = voucherRepository.countByCompanyIdAndStatus(testCompany.getId(), "draft");
    long postedCount = voucherRepository.countByCompanyIdAndStatus(testCompany.getId(), "posted");

    assertEquals(2L, draftCount);
    assertEquals(1L, postedCount);
  }

  @Test
  void isReferenced_whenReferenced_returnsTrue() {
    Voucher originalVoucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "posted");
    originalVoucher = voucherRepository.save(originalVoucher);

    Voucher reversalVoucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-002", "draft");
    reversalVoucher.setReversalOf(originalVoucher.getId());
    reversalVoucher = voucherRepository.save(reversalVoucher);

    boolean isReferenced = voucherRepository.isReferenced(originalVoucher.getId());

    assertTrue(isReferenced);
  }

  @Test
  void isReferenced_whenNotReferenced_returnsFalse() {
    Voucher voucher = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    voucher = voucherRepository.save(voucher);

    boolean isReferenced = voucherRepository.isReferenced(voucher.getId());

    assertFalse(isReferenced);
  }

  @Test
  void existsByCompanyIdAndVoucherNumber_whenExists_returnsTrue() {
    Voucher voucher = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    voucherRepository.save(voucher);

    boolean exists =
        voucherRepository.existsByCompanyIdAndVoucherNumber(
            testCompany.getId(), "VC2025-001", null);

    assertTrue(exists);
  }

  @Test
  void existsByCompanyIdAndVoucherNumber_whenNotExists_returnsFalse() {
    boolean exists =
        voucherRepository.existsByCompanyIdAndVoucherNumber(
            testCompany.getId(), "VC2025-999", null);

    assertFalse(exists);
  }

  @Test
  void existsByCompanyIdAndVoucherNumber_withExcludeId_excludesVoucher() {
    Voucher voucher = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    voucher = voucherRepository.save(voucher);

    boolean exists =
        voucherRepository.existsByCompanyIdAndVoucherNumber(
            testCompany.getId(), "VC2025-001", voucher.getId());

    assertFalse(exists); // Should return false because we're excluding this voucher
  }

  private Voucher createVoucher(Long companyId, Long enteredBy, String voucherNumber, String status) {
    return createVoucher(companyId, enteredBy, voucherNumber, LocalDate.now(), status);
  }

  private Voucher createVoucher(
      Long companyId, Long enteredBy, String voucherNumber, LocalDate date, String status) {
    Voucher voucher = new Voucher();
    voucher.setId(UUID.randomUUID());
    voucher.setCompanyId(companyId);
    voucher.setVoucherNumber(voucherNumber);
    voucher.setVoucherDate(date);
    voucher.setDescription("Test voucher description");
    voucher.setStatus(status);
    voucher.setCurrency("VND");
    voucher.setTotalDebit(BigDecimal.valueOf(1000));
    voucher.setTotalCredit(BigDecimal.valueOf(1000));
    voucher.setEnteredBy(enteredBy);
    voucher.setCreatedAt(Instant.now());
    voucher.setUpdatedAt(Instant.now());
    return voucher;
  }
}

