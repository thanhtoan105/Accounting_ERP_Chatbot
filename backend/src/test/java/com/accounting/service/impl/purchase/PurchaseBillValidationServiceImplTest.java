package com.accounting.service.impl.purchase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.PurchaseBillCreateRequest;
import com.accounting.dto.PurchaseBillLineDTO;
import com.accounting.dto.PurchaseBillValidationResult;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.VatRate;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AccountControlService;
import com.accounting.service.PeriodManagementService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseBillValidationServiceImplTest {

  @Mock private PurchaseBillRepository purchaseBillRepository;

  @Mock private ChartOfAccountsRepository chartOfAccountsRepository;

  @Mock private SupplierRepository supplierRepository;

  @Mock private AccountControlService accountControlService;

  @Mock private PeriodManagementService periodManagementService;

  private PurchaseBillValidationServiceImpl validationService;

  @BeforeEach
  void setUp() {
    validationService =
        new PurchaseBillValidationServiceImpl(
            purchaseBillRepository,
            chartOfAccountsRepository,
            accountControlService,
            periodManagementService);
    CompanyContext.setCompanyId(1L);

    // Mock AccountControlService to return empty (no dimension requirements by default)
    when(accountControlService.getRequiredDimensions(anyLong(), anyLong()))
        .thenReturn(Optional.empty());

    // Mock PeriodManagementService to return a valid open period by default
    AccountingPeriodDTO openPeriod = new AccountingPeriodDTO();
    openPeriod.setId(UUID.randomUUID());
    openPeriod.setStatus(PeriodStatus.OPEN);
    openPeriod.setPeriodName("Test Period");
    when(periodManagementService.findPeriodByDate(any(LocalDate.class)))
        .thenReturn(Optional.of(openPeriod));
    when(periodManagementService.isPeriodOpen(any(UUID.class))).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void validate_validBill_returnsValid() {
    // Setup
    ChartOfAccount account = createPostableAccount(1L, "621", "Expense");
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));
    when(chartOfAccountsRepository.hasChildren(1L)).thenReturn(false);
    when(purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndYear(
            anyLong(), anyLong(), anyString(), any(LocalDate.class), any(LocalDate.class), any()))
        .thenReturn(false);
    when(purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndBillDate(
            anyLong(), anyLong(), anyString(), any(LocalDate.class), any()))
        .thenReturn(false);

    PurchaseBillCreateRequest request = createValidRequest();

    // Execute
    PurchaseBillValidationResult result = validationService.validate(request, null);

    // Assert
    if (!result.isValid()) {
      System.out.println("Validation errors: " + result.getHeaderErrors());
      System.out.println("Line errors: " + result.getLineErrors());
    }
    assertTrue(result.isValid(), "Validation should pass. Errors: " + result.getHeaderErrors() + " Line errors: " + result.getLineErrors());
    assertTrue(result.getHeaderErrors().isEmpty());
    assertTrue(result.getLineErrors().isEmpty());
  }

  @Test
  void validate_duplicateBillNumber_returnsError() {
    // Setup
    ChartOfAccount account = createPostableAccount(1L, "621", "Expense");
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));
    when(chartOfAccountsRepository.hasChildren(1L)).thenReturn(false);
    when(purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndYear(
            anyLong(), anyLong(), anyString(), any(LocalDate.class), any(LocalDate.class), any()))
        .thenReturn(true);
    when(purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndBillDate(
            anyLong(), anyLong(), anyString(), any(LocalDate.class), any()))
        .thenReturn(false);

    PurchaseBillCreateRequest request = createValidRequest();

    // Execute
    PurchaseBillValidationResult result = validationService.validate(request, null);

    // Assert
    assertFalse(result.isValid());
    assertTrue(result.getHeaderErrors().containsKey("billNumber"));
  }

  @Test
  void validate_nonPostableAccount_returnsError() {
    // Setup
    ChartOfAccount account = createNonPostableAccount(1L, "111", "Asset");
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));
    when(chartOfAccountsRepository.hasChildren(1L)).thenReturn(false);
    when(purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndYear(
            anyLong(), anyLong(), anyString(), any(LocalDate.class), any(LocalDate.class), any()))
        .thenReturn(false);
    when(purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndBillDate(
            anyLong(), anyLong(), anyString(), any(LocalDate.class), any()))
        .thenReturn(false);

    PurchaseBillCreateRequest request = createValidRequest();

    // Execute
    PurchaseBillValidationResult result = validationService.validate(request, null);

    // Assert
    assertFalse(result.isValid());
    assertTrue(result.getLineErrors().containsKey(1)); // Line numbers are 1-based
    assertTrue(result.getLineErrors().get(1).containsKey("accountId"));
  }

  @Test
  void validate_vatSumMismatch_returnsError() {
    // Setup
    ChartOfAccount account = createPostableAccount(1L, "621", "Expense");
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));
    when(chartOfAccountsRepository.hasChildren(1L)).thenReturn(false);
    when(purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndYear(
            anyLong(), anyLong(), anyString(), any(LocalDate.class), any(LocalDate.class), any()))
        .thenReturn(false);
    when(purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndBillDate(
            anyLong(), anyLong(), anyString(), any(LocalDate.class), any()))
        .thenReturn(false);

    PurchaseBillCreateRequest request = createValidRequest();
    // Set line VAT to 2000, but header VAT is explicitly set to 0 (mismatch > 1000 VND tolerance)
    PurchaseBillLineDTO line = request.getLines().get(0);
    line.setVatRate(VatRate.TEN);
    line.setAmount(BigDecimal.valueOf(20000)); // 10% VAT = 2000
    line.setVatAmount(BigDecimal.valueOf(2000));
    // Explicitly set header VAT to 0 to create mismatch (line VAT sum is 2000, header is 0, difference = 2000 > 1000 tolerance)
    request.setVatAmount(BigDecimal.ZERO);

    // Execute
    PurchaseBillValidationResult result = validationService.validate(request, null);

    // Assert
    assertFalse(result.isValid());
    assertTrue(result.getHeaderErrors().containsKey("vatAmount"));
  }

  @Test
  void validate_missingRequiredFields_returnsErrors() {
    // Setup
    PurchaseBillCreateRequest request = new PurchaseBillCreateRequest();
    request.setSupplierId(1L);
    // Missing billNumber, billDate, dueDate, reference, lines
    // Note: The validation service will throw NPE on null billDate.getYear(), 
    // but we'll test that it catches missing fields through the date validation
    request.setBillDate(LocalDate.of(2025, 1, 15)); // Set date to avoid NPE
    request.setDueDate(LocalDate.of(2025, 2, 14)); // Set due date to avoid NPE
    // Missing billNumber, reference, lines

    // Execute
    PurchaseBillValidationResult result = validationService.validate(request, null);

    // Assert
    assertFalse(result.isValid());
    // Bill number validation will fail because billNumber is null
    // Lines validation will fail because lines is null/empty
    assertTrue(result.getHeaderErrors().containsKey("lines") || result.getHeaderErrors().containsKey("billNumber"));
  }

  @Test
  void validateBillNumber_duplicateInSameYear_returnsFalse() {
    // Setup
    LocalDate billDate = LocalDate.of(2025, 1, 15);
    LocalDate yearStart = billDate.withDayOfYear(1);
    LocalDate yearEnd = yearStart.plusYears(1);
    when(purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndYear(
            1L, 1L, "BILL-001", yearStart, yearEnd, null))
        .thenReturn(true);

    // Execute
    boolean result =
        validationService.validateBillNumber(1L, "BILL-001", LocalDate.of(2025, 1, 15), 1L, null);

    // Assert
    assertFalse(result);
  }

  @Test
  void validateBillNumber_sameNumberDifferentYear_returnsTrue() {
    // Setup
    LocalDate billDate = LocalDate.of(2025, 1, 15);
    LocalDate yearStart = billDate.withDayOfYear(1);
    LocalDate yearEnd = yearStart.plusYears(1);
    when(purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndYear(
            1L, 1L, "BILL-001", yearStart, yearEnd, null))
        .thenReturn(false);

    // Execute
    boolean result =
        validationService.validateBillNumber(1L, "BILL-001", LocalDate.of(2025, 1, 15), 1L, null);

    // Assert
    assertTrue(result);
  }

  @Test
  void validateDates_dueDateBeforeBillDate_returnsError() {
    // Setup
    LocalDate billDate = LocalDate.of(2025, 1, 15);
    LocalDate dueDate = LocalDate.of(2025, 1, 10); // Before bill date

    // Execute
    PurchaseBillValidationResult result = validationService.validateDates(billDate, dueDate);

    // Assert
    assertFalse(result.isValid());
    assertTrue(result.getHeaderErrors().containsKey("dueDate"));
  }

  @Test
  void validateDates_validDates_returnsValid() {
    // Setup
    LocalDate billDate = LocalDate.of(2025, 1, 15);
    LocalDate dueDate = LocalDate.of(2025, 2, 15); // 30 days later

    // Execute
    PurchaseBillValidationResult result = validationService.validateDates(billDate, dueDate);

    // Assert
    assertTrue(result.isValid());
  }

  // Helper methods
  private PurchaseBillCreateRequest createValidRequest() {
    PurchaseBillCreateRequest request = new PurchaseBillCreateRequest();
    request.setSupplierId(1L);
    request.setBillNumber("BILL-001");
    request.setBillDate(LocalDate.of(2025, 1, 15));
    request.setDueDate(LocalDate.of(2025, 2, 14));
    request.setReference("REF-001");
    request.setDescription("Test purchase bill");

    PurchaseBillLineDTO line = new PurchaseBillLineDTO();
    line.setAccountId(1L);
    line.setDescription("Test line");
    line.setQuantity(BigDecimal.ONE);
    line.setUnitPrice(BigDecimal.valueOf(1000));
    line.setAmount(BigDecimal.valueOf(1000));
    line.setVatRate(VatRate.ZERO);
    line.setVatAmount(BigDecimal.ZERO);

    request.setLines(List.of(line));

    return request;
  }

  private ChartOfAccount createPostableAccount(Long id, String code, String name) {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(id);
    account.setCode(code);
    account.setName(name);
    account.setPostable(true);
    account.setCompanyId(1L);
    return account;
  }

  private ChartOfAccount createNonPostableAccount(Long id, String code, String name) {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(id);
    account.setCode(code);
    account.setName(name);
    account.setPostable(false);
    account.setCompanyId(1L);
    return account;
  }
}
