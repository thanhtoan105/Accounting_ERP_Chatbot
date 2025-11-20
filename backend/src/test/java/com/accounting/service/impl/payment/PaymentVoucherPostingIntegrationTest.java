package com.accounting.service.impl.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.accounting.dto.APPaymentCreateRequest;
import com.accounting.dto.PaymentAllocationRequest;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.APPayment;
import com.accounting.entity.BankAccount;
import com.accounting.entity.Company;
import com.accounting.entity.CompanySettings;
import com.accounting.entity.PaymentMethod;
import com.accounting.entity.PaymentStatus;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.Supplier;
import com.accounting.entity.User;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.enums.Role;
import com.accounting.exception.VoucherValidationException;
import com.accounting.repository.APPaymentRepository;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.PaymentService;
import com.accounting.test.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentVoucherPostingIntegrationTest extends IntegrationTest {

  @Autowired private PaymentService paymentService;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private SupplierRepository supplierRepository;
  @Autowired private BankAccountRepository bankAccountRepository;
  @Autowired private PurchaseBillRepository purchaseBillRepository;
  @Autowired private CompanySettingsRepository companySettingsRepository;
  @Autowired private APPaymentRepository appPaymentRepository;
  @Autowired private VoucherRepository voucherRepository;
  @Autowired private VoucherLineRepository voucherLineRepository;
  @Autowired private AccountingPeriodRepository accountingPeriodRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Company company;
  private User accountant;
  private Supplier supplier;
  private BankAccount cashAccount;
  private BankAccount bankAccount;
  private PurchaseBill firstBill;
  private PurchaseBill secondBill;

  @BeforeEach
  void setUp() {
    company = createCompany();
    CompanyContext.setCompanyId(company.getId());
    resetChartOfAccounts();
    createChartAccount(
        331L,
        "331",
        "Accounts Payable",
        "Liability",
        "Credit",
        true);
    ensureAccountingPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

    accountant = createUser(Role.ACCOUNTANT, "accountant@example.com");
    setAuthentication(accountant, Role.ACCOUNTANT);

    supplier = createSupplier("SUP-001");
    cashAccount =
        createBankAccount("CASH-001", BankAccount.AccountType.CASH, new BigDecimal("10000000"));
    bankAccount =
        createBankAccount("BANK-001", BankAccount.AccountType.BANK, new BigDecimal("12000000"));
    firstBill = createPurchaseBill("BILL-001", new BigDecimal("1000000"));
    secondBill = createPurchaseBill("BILL-002", new BigDecimal("500000"));
    ensureCompanySettings(new BigDecimal("5000000"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    CompanyContext.clear();
  }

  @Test
  void postPayment_withCashAccount_createsDr331CrCashVoucher() {
    APPaymentCreateRequest request =
        baseRequest(cashAccount.getId(), null, new BigDecimal("1000000"));
    request.setAllocations(
        List.of(new PaymentAllocationRequest(firstBill.getId(), new BigDecimal("1000000"))));

    APPayment created = savePayment(request);

    APPayment posted = postPayment(created.getId());

    assertEquals(PaymentStatus.POSTED, posted.getStatus());
    UUID voucherId = posted.getLinkedVoucherId();
    assertNotNull(voucherId);

    Voucher voucher = voucherRepository.findById(voucherId).orElseThrow();
    assertEquals(0, voucher.getTotalDebit().compareTo(request.getAmount()));
    assertEquals(0, voucher.getTotalCredit().compareTo(request.getAmount()));

    List<VoucherLine> lines = voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(voucherId);
    assertThat(lines).hasSize(2);

    VoucherLine debitLine =
        lines.stream()
            .filter(line -> line.getDebit().compareTo(BigDecimal.ZERO) > 0)
            .findFirst()
            .orElseThrow();
    VoucherLine creditLine =
        lines.stream()
            .filter(line -> line.getCredit().compareTo(BigDecimal.ZERO) > 0)
            .findFirst()
            .orElseThrow();

    assertEquals(331L, debitLine.getAccountId());
    assertEquals(0, debitLine.getDebit().compareTo(request.getAmount()));
    assertEquals(cashAccount.getId(), creditLine.getAccountId());
    assertEquals(0, creditLine.getCredit().compareTo(request.getAmount()));

    PurchaseBill paidBill = purchaseBillRepository.findById(firstBill.getId()).orElseThrow();
    assertEquals(PurchaseBillStatus.PAID, paidBill.getStatus());
  }

  @Test
  void postPayment_withMultipleAllocations_keepsDr331AndCrBankPerEntry() {
    PurchaseBill largeBill = createPurchaseBill("BILL-003", new BigDecimal("1500000"));
    PurchaseBill extraBill = createPurchaseBill("BILL-004", new BigDecimal("500000"));

    APPaymentCreateRequest request =
        baseRequest(null, bankAccount.getId(), new BigDecimal("2000000"));
    request.setAllocations(
        List.of(
            new PaymentAllocationRequest(largeBill.getId(), new BigDecimal("1500000")),
            new PaymentAllocationRequest(extraBill.getId(), new BigDecimal("500000"))));

    APPayment created = savePayment(request);

    APPayment posted = postPayment(created.getId());

    assertEquals(PaymentStatus.POSTED, posted.getStatus());
    UUID voucherId = posted.getLinkedVoucherId();
    assertNotNull(voucherId);

    List<VoucherLine> lines = voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(voucherId);
    assertThat(lines).hasSize(4);

    BigDecimal totalDebit =
        lines.stream().map(VoucherLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalCredit =
        lines.stream().map(VoucherLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);

    assertEquals(0, totalDebit.compareTo(request.getAmount()));
    assertEquals(0, totalCredit.compareTo(request.getAmount()));

    List<VoucherLine> debitLines =
        lines.stream().filter(line -> line.getDebit().compareTo(BigDecimal.ZERO) > 0).toList();
    List<VoucherLine> creditLines =
        lines.stream().filter(line -> line.getCredit().compareTo(BigDecimal.ZERO) > 0).toList();

    assertThat(debitLines).hasSize(2);
    assertThat(creditLines).hasSize(2);
    debitLines.forEach(line -> assertEquals(331L, line.getAccountId()));
    creditLines.forEach(line -> assertEquals(bankAccount.getId(), line.getAccountId()));

    PurchaseBill updatedFirst = purchaseBillRepository.findById(largeBill.getId()).orElseThrow();
    PurchaseBill updatedSecond = purchaseBillRepository.findById(extraBill.getId()).orElseThrow();
    assertEquals(PurchaseBillStatus.PAID, updatedFirst.getStatus());
    assertEquals(PurchaseBillStatus.PAID, updatedSecond.getStatus());
  }

  private APPaymentCreateRequest baseRequest(Long cashAccountId, Long bankAccountId, BigDecimal amount) {
    APPaymentCreateRequest request = new APPaymentCreateRequest();
    request.setSupplierId(supplier.getId());
    request.setPaymentDate(LocalDate.of(2025, 1, 20));
    request.setDueDate(LocalDate.of(2025, 1, 25));
    request.setCashAccountId(cashAccountId);
    request.setBankAccountId(bankAccountId);
    request.setPayee("Supplier 001");
    request.setAmount(amount);
    request.setReference("PMT-" + amount.toPlainString());
    request.setPaymentMethod(
        cashAccountId != null ? PaymentMethod.CASH : PaymentMethod.BANK_TRANSFER);
    request.setPaymentProofUrl(null);
    request.setIsStandalone(false);
    return request;
  }

  private APPayment savePayment(APPaymentCreateRequest request) {
    CompanyContext.setCompanyId(company.getId());
    setAuthentication(accountant, Role.ACCOUNTANT);
    return appPaymentRepository
        .findById(paymentService.create(request).getId())
        .orElseThrow();
  }

  private APPayment postPayment(UUID paymentId) {
    CompanyContext.setCompanyId(company.getId());
    setAuthentication(accountant, Role.ACCOUNTANT);
    try {
      return appPaymentRepository
          .findById(paymentService.postPayment(paymentId).getId())
          .orElseThrow();
    } catch (VoucherValidationException ex) {
      System.out.println("Voucher validation errors: " + ex.getValidationResult().getErrors());
      throw ex;
    }
  }

  private Company createCompany() {
    Company c = new Company();
    String code = "COMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    c.setCode(code);
    c.setName("Integration Test Co.");
    String taxCode =
        String.format(
            "%010d",
            Math.abs(
                java.util.concurrent.ThreadLocalRandom.current()
                    .nextInt(0, 1_000_000_000)));
    c.setTaxCode(taxCode);
    c.setAddress("123 Test St");
    c.setContactEmail("info@example.com");
    c.setContactPhone("0123456789");
    return companyRepository.save(c);
  }

  private User createUser(Role role, String email) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash("hashed");
    user.setFullName("Test " + role.name());
    user.setRole(role.getValue());
    user.setCompanyId(company.getId());
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    return userRepository.save(user);
  }

  private void setAuthentication(User user, Role role) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            user.getId().toString(),
            null,
            List.of(new SimpleGrantedAuthority(role.toAuthority())));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private Supplier createSupplier(String code) {
    Supplier supplierEntity = new Supplier();
    supplierEntity.setCompanyId(company.getId());
    supplierEntity.setCode(code);
    supplierEntity.setName("Supplier Name");
    supplierEntity.setTaxCode("1234567890");
    supplierEntity.setAddress("Supplier Address");
    supplierEntity.setEmail("supplier@example.com");
    supplierEntity.setPhone("+84889999");
    return supplierRepository.save(supplierEntity);
  }

  private BankAccount createBankAccount(
      String accountNumber, BankAccount.AccountType type, BigDecimal openingBalance) {
    BankAccount account = new BankAccount();
    account.setCompanyId(company.getId());
    account.setAccountNumber(accountNumber);
    account.setBankName(type == BankAccount.AccountType.CASH ? "Cash Safe" : "Main Bank");
    account.setBranch("HQ");
    account.setType(type);
    account.setOpeningBalance(openingBalance);
    account.setActive(true);
    BankAccount saved = bankAccountRepository.save(account);
    createChartAccount(
        saved.getId(),
        "ACC-" + saved.getId(),
        type == BankAccount.AccountType.CASH ? "Cash Account" : "Bank Account",
        "Asset",
        "Debit",
        true);
    return saved;
  }

  private PurchaseBill createPurchaseBill(String billNumber, BigDecimal totalAmount) {
    PurchaseBill bill = new PurchaseBill();
    bill.setCompanyId(company.getId());
    bill.setSupplierId(supplier.getId());
    bill.setBillNumber(billNumber);
    bill.setBillDate(LocalDate.of(2025, 1, 5));
    bill.setDueDate(LocalDate.of(2025, 1, 25));
    bill.setReference("REF-" + billNumber);
    bill.setDescription("Test bill " + billNumber);
    bill.setStatus(PurchaseBillStatus.POSTED);
    bill.setTotalAmount(totalAmount);
    bill.setVatAmount(BigDecimal.ZERO);
    bill.setCreatedById(accountant.getId());
    bill.setIsSensitive(false);
    return purchaseBillRepository.save(bill);
  }

  private void ensureAccountingPeriod(LocalDate startDate, LocalDate endDate) {
    AccountingPeriod period = new AccountingPeriod();
    period.setCompanyId(company.getId());
    period.setFiscalYear(startDate.getYear());
    period.setPeriodNumber(startDate.getMonthValue());
    period.setPeriodName(startDate.getMonth().name() + " " + startDate.getYear());
    period.setStartDate(startDate);
    period.setEndDate(endDate);
    period.setStatus(PeriodStatus.OPEN);
    accountingPeriodRepository.save(period);
  }

  private void createChartAccount(
      Long id,
      String code,
      String name,
      String type,
      String normalSide,
      boolean postable) {
    jdbcTemplate.update(
        """
            INSERT INTO chart_of_accounts
              (id, company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
            VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?)
            ON CONFLICT (id) DO NOTHING
            """,
        id,
        company.getId(),
        code,
        name,
        type,
        normalSide,
        postable,
        id.intValue());
  }

  private void ensureCompanySettings(BigDecimal approvalThreshold) {
    CompanySettings settings = new CompanySettings();
    settings.setCompanyId(company.getId());
    settings.setApprovalThresholdAmount(approvalThreshold);
    settings.setDefaultCurrency("VND");
    settings.setLegalName("Integration Test Co.");
    settings.setShortName("ITC");
    settings.setTimezone("Asia/Ho_Chi_Minh");
    companySettingsRepository.save(settings);
  }

  private void resetChartOfAccounts() {
    jdbcTemplate.execute("TRUNCATE TABLE chart_of_accounts RESTART IDENTITY CASCADE");
  }
}

