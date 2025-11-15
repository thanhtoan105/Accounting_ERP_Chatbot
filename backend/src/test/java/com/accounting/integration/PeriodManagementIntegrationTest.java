package com.accounting.integration;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.PeriodCloseRequest;
import com.accounting.dto.PeriodReopenRequest;
import com.accounting.dto.PeriodSummaryDTO;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.PeriodStatus;
import com.accounting.security.PasswordEncoder;
import com.accounting.entity.Voucher;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.PeriodManagementService;
import com.accounting.test.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
class PeriodManagementIntegrationTest extends IntegrationTest {

  @Autowired
  private PeriodManagementService periodManagementService;

  @Autowired
  private AccountingPeriodRepository periodRepository;

  @Autowired
  private VoucherRepository voucherRepository;

  @Autowired
  private CompanyRepository companyRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Long testCompanyId;
  private Long testUserId;
  private AccountingPeriod testPeriod;
  private AccountingPeriod testClosedPeriod;

  @BeforeEach
  void setUp() {
    // Create test company and user
    com.accounting.entity.Company company = new com.accounting.entity.Company();
    company.setName("Test Company");
    company.setTaxCode("1234567890");
    company = companyRepository.save(company);
    testCompanyId = company.getId();

    com.accounting.entity.User user = new com.accounting.entity.User();
    user.setEmail("test@example.com");
    user.setPasswordHash(passwordEncoder.encode("Password123!"));
    user.setFullName("Test User");
    user.setCompanyId(testCompanyId);
    user.setRole("CHIEF_ACCOUNTANT");
    user.setStatus("ACTIVE");
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    user = userRepository.save(user);
    testUserId = user.getId();

    // Set company context
    CompanyContext.setCompanyId(testCompanyId);

    // Create test periods
    testPeriod = new AccountingPeriod();
    testPeriod.setCompanyId(testCompanyId);
    testPeriod.setFiscalYear(2025);
    testPeriod.setPeriodNumber(1);
    testPeriod.setPeriodName("January 2025");
    testPeriod.setStartDate(LocalDate.of(2025, Month.JANUARY, 1));
    testPeriod.setEndDate(LocalDate.of(2025, Month.JANUARY, 31));
    testPeriod.setStatus(PeriodStatus.OPEN);
    testPeriod = periodRepository.save(testPeriod);

    testClosedPeriod = new AccountingPeriod();
    testClosedPeriod.setCompanyId(testCompanyId);
    testClosedPeriod.setFiscalYear(2024);
    testClosedPeriod.setPeriodNumber(12);
    testClosedPeriod.setPeriodName("December 2024");
    testClosedPeriod.setStartDate(LocalDate.of(2024, Month.DECEMBER, 1));
    testClosedPeriod.setEndDate(LocalDate.of(2024, Month.DECEMBER, 31));
    testClosedPeriod.setStatus(PeriodStatus.CLOSED);
    testClosedPeriod.setClosedBy(testUserId);
    testClosedPeriod.setClosedAt(Instant.now());
    testClosedPeriod.setCloseReason("Month end closing");
    testClosedPeriod = periodRepository.save(testClosedPeriod);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  @WithMockUser(username = "1", roles = {"ACCOUNTANT"})
  void getCurrentPeriod_WithExistingPeriod_ReturnsPeriod() {
    // Create a period that contains today's date
    LocalDate today = LocalDate.now();
    AccountingPeriod currentPeriod = new AccountingPeriod();
    currentPeriod.setCompanyId(testCompanyId);
    currentPeriod.setFiscalYear(today.getYear());
    currentPeriod.setPeriodNumber(today.getMonthValue());
    currentPeriod.setPeriodName(today.getMonth().name() + " " + today.getYear());
    currentPeriod.setStartDate(LocalDate.of(today.getYear(), today.getMonth(), 1));
    currentPeriod.setEndDate(LocalDate.of(today.getYear(), today.getMonth(), today.lengthOfMonth()));
    currentPeriod.setStatus(PeriodStatus.OPEN);
    currentPeriod = periodRepository.save(currentPeriod);

    // When
    Optional<AccountingPeriodDTO> result = periodManagementService.getCurrentPeriod();

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().getPeriodName()).contains(today.getMonth().name());
  }

  @Test
  @WithMockUser(username = "1", roles = {"ACCOUNTANT"})
  void getOpenPeriods_WithOpenPeriods_ReturnsPeriods() {
    // When
    List<AccountingPeriodDTO> result = periodManagementService.getOpenPeriods();

    // Then
    assertThat(result).isNotEmpty();
    assertThat(result).extracting(AccountingPeriodDTO::getStatus)
        .containsOnly(PeriodStatus.OPEN);
  }

  @Test
  @WithMockUser(username = "1", roles = {"ACCOUNTANT"})
  void getPeriodById_WithValidPeriod_ReturnsPeriod() {
    // When
    Optional<AccountingPeriodDTO> result = periodManagementService.getPeriodById(testPeriod.getId());

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(testPeriod.getId());
    assertThat(result.get().getPeriodName()).isEqualTo("January 2025");
  }

  @Test
  @WithMockUser(username = "1", roles = {"ACCOUNTANT"})
  void findPeriodByDate_WithValidDate_ReturnsPeriod() {
    // Given
    LocalDate date = LocalDate.of(2025, Month.JANUARY, 15);

    // When
    Optional<AccountingPeriodDTO> result = periodManagementService.findPeriodByDate(date);

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().getPeriodName()).isEqualTo("January 2025");
  }

  @Test
  @WithMockUser(username = "1", roles = {"ACCOUNTANT"})
  void isPeriodOpen_OpenPeriod_ReturnsTrue() {
    // When
    boolean result = periodManagementService.isPeriodOpen(testPeriod.getId());

    // Then
    assertThat(result).isTrue();
  }

  @Test
  @WithMockUser(username = "1", roles = {"ACCOUNTANT"})
  void isPeriodOpen_ClosedPeriod_ReturnsFalse() {
    // When
    boolean result = periodManagementService.isPeriodOpen(testClosedPeriod.getId());

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @WithMockUser(username = "1", roles = {"ACCOUNTANT"})
  void isDateInOpenPeriod_OpenDate_ReturnsTrue() {
    // Given
    LocalDate date = LocalDate.of(2025, Month.JANUARY, 15);

    // When
    boolean result = periodManagementService.isDateInOpenPeriod(date);

    // Then
    assertThat(result).isTrue();
  }

  @Test
  @WithMockUser(username = "1", roles = {"ACCOUNTANT"})
  void isDateInOpenPeriod_ClosedDate_ReturnsFalse() {
    // Given
    LocalDate date = LocalDate.of(2024, Month.DECEMBER, 15);

    // When
    boolean result = periodManagementService.isDateInOpenPeriod(date);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @WithMockUser(username = "1", roles = {"CHIEF_ACCOUNTANT"})
  void closePeriod_ValidPeriod_ClosesPeriod() {
    // Given
    PeriodCloseRequest request = new PeriodCloseRequest();
    request.setReason("Month end closing");

    // When
    AccountingPeriodDTO result = periodManagementService.closePeriod(testPeriod.getId(), request);

    // Then
    assertThat(result.getStatus()).isEqualTo(PeriodStatus.CLOSED);
    assertThat(result.getCloseReason()).isEqualTo("Month end closing");
    assertThat(result.getClosedBy()).isNotNull();
    assertThat(result.getClosedAt()).isNotNull();

    // Verify in database
    Optional<AccountingPeriod> saved = periodRepository.findById(testPeriod.getId());
    assertThat(saved).isPresent();
    assertThat(saved.get().getStatus()).isEqualTo(PeriodStatus.CLOSED);
  }

  @Test
  @WithMockUser(username = "1", roles = {"CHIEF_ACCOUNTANT"})
  void closePeriod_WithDraftVouchers_ThrowsException() {
    // Given
    Voucher draftVoucher = new Voucher();
    draftVoucher.setCompanyId(testCompanyId);
    draftVoucher.setVoucherNumber("V001");
    draftVoucher.setVoucherDate(LocalDate.of(2025, Month.JANUARY, 15));
    draftVoucher.setPeriodId(testPeriod.getId());
    draftVoucher.setDescription("Test voucher");
    draftVoucher.setStatus("draft");
    draftVoucher.setTotalDebit(BigDecimal.ZERO);
    draftVoucher.setTotalCredit(BigDecimal.ZERO);
    draftVoucher.setEnteredBy(testUserId);
    draftVoucher.setCreatedAt(Instant.now());
    draftVoucher.setUpdatedAt(Instant.now());
    voucherRepository.save(draftVoucher);

    PeriodCloseRequest request = new PeriodCloseRequest();
    request.setReason("Month end closing");

    // When & Then
    org.springframework.web.server.ResponseStatusException exception = assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> periodManagementService.closePeriod(testPeriod.getId(), request)
    );
    assertThat(exception.getStatusCode().value()).isEqualTo(400);
    assertThat(exception.getMessage()).contains("draft voucher");
  }

  @Test
  @WithMockUser(username = "1", roles = {"CHIEF_ACCOUNTANT"})
  void closePeriod_PeriodAlreadyClosed_ThrowsException() {
    // Given
    PeriodCloseRequest request = new PeriodCloseRequest();
    request.setReason("Already closed");

    // When & Then
    org.springframework.web.server.ResponseStatusException exception = assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> periodManagementService.closePeriod(testClosedPeriod.getId(), request)
    );
    assertThat(exception.getStatusCode().value()).isEqualTo(409);
  }

  @Test
  @WithMockUser(username = "1", roles = {"CHIEF_ACCOUNTANT"})
  void reopenPeriod_ValidClosedPeriod_ReopensPeriod() {
    // Given
    PeriodReopenRequest request = new PeriodReopenRequest();
    request.setReason("Correction needed");
    request.setApprovalMetadata("Approved by CFO");

    // When
    AccountingPeriodDTO result = periodManagementService.reopenPeriod(testClosedPeriod.getId(), request);

    // Then
    assertThat(result.getStatus()).isEqualTo(PeriodStatus.OPEN);
    assertThat(result.getCloseReason()).isNull();
    assertThat(result.getClosedBy()).isNull();
    assertThat(result.getClosedAt()).isNull();

    // Verify in database
    Optional<AccountingPeriod> saved = periodRepository.findById(testClosedPeriod.getId());
    assertThat(saved).isPresent();
    assertThat(saved.get().getStatus()).isEqualTo(PeriodStatus.OPEN);
  }

  @Test
  @WithMockUser(username = "1", roles = {"CHIEF_ACCOUNTANT"})
  void reopenPeriod_OpenPeriod_ThrowsException() {
    // Given
    PeriodReopenRequest request = new PeriodReopenRequest();
    request.setReason("Already open");
    request.setApprovalMetadata("Test approval");

    // When & Then
    org.springframework.web.server.ResponseStatusException exception = assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> periodManagementService.reopenPeriod(testPeriod.getId(), request)
    );
    assertThat(exception.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  @WithMockUser(username = "1", roles = {"ACCOUNTANT"})
  void getPeriodSummary_WithValidPeriod_ReturnsSummary() {
    // When
    Optional<PeriodSummaryDTO> result = periodManagementService.getPeriodSummary(testPeriod.getId());

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().getPeriodName()).isEqualTo("January 2025");
    assertThat(result.get().getStatus()).isEqualTo(PeriodStatus.OPEN);
    assertThat(result.get().getPostingFlowStatus()).isEqualTo("ACTIVE");
    assertThat(result.get().getDraftVouchersCount()).isNotNull();
  }

  @Test
  @WithMockUser(username = "1", roles = {"ACCOUNTANT"})
  void validatePeriodForVoucherOperation_ClosedPeriod_ThrowsException() {
    // When & Then
    org.springframework.web.server.ResponseStatusException exception = assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> periodManagementService.validatePeriodForVoucherOperation(
            testClosedPeriod.getId(), "VOUCHER_CREATE")
    );
    assertThat(exception.getStatusCode().value()).isEqualTo(400);
    assertThat(exception.getMessage()).contains("closed period");
  }

  @Test
  @WithMockUser(username = "1", roles = {"ACCOUNTANT"})
  void validatePeriodForVoucherOperation_OpenPeriod_Succeeds() {
    // When & Then - should not throw exception
    assertDoesNotThrow(() -> {
      periodManagementService.validatePeriodForVoucherOperation(
          testPeriod.getId(), "VOUCHER_CREATE");
    });
  }
}

