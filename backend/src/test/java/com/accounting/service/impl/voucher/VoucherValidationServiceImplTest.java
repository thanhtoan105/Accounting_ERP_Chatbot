package com.accounting.service.impl.voucher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherLineDTO;
import com.accounting.dto.VoucherValidationResult;
import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.security.CompanyContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherValidationServiceImplTest {

  @Mock
  private ChartOfAccountsRepository chartOfAccountsRepository;

  private VoucherValidationServiceImpl validationService;

  @BeforeEach
  void setUp() {
    validationService = new VoucherValidationServiceImpl(chartOfAccountsRepository);
    CompanyContext.setCompanyId(1L);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void validate_balancedVoucherWithValidLines_returnsValid() {
    ChartOfAccount account1 = createPostableAccount(1L, "111", "Cash");
    ChartOfAccount account2 = createPostableAccount(2L, "411", "Revenue");

    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account1));
    when(chartOfAccountsRepository.findById(2L)).thenReturn(Optional.of(account2));

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    request.setLines(
        List.of(
            createLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "Cash receipt"),
            createLine(2L, BigDecimal.ZERO, BigDecimal.valueOf(1000), "Revenue")));

    VoucherValidationResult result = validationService.validate(request);

    assertTrue(result.isValid());
    assertTrue(result.getErrors().isEmpty());
  }

  @Test
  void validate_unbalancedVoucher_returnsErrors() {
    ChartOfAccount account1 = createPostableAccount(1L, "111", "Cash");

    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account1));

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    request.setLines(
        List.of(createLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "Cash receipt")));

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().containsKey(0));
    assertTrue(result.getErrors().get(0).containsKey("balance"));
  }

  @Test
  void validate_nonPostableAccount_returnsError() {
    ChartOfAccount account = createNonPostableAccount(1L, "111", "Cash");

    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    request.setLines(
        List.of(createLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "Cash receipt")));

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().containsKey(1)); // Line 1 (1-based)
    assertTrue(result.getErrors().get(1).containsKey("accountId"));
  }

  @Test
  void validate_accountNotFound_returnsError() {
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.empty());

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    request.setLines(List.of(createLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "Cash")));

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().containsKey(1));
    assertTrue(result.getErrors().get(1).containsKey("accountId"));
  }

  @Test
  void validate_accountFromDifferentCompany_returnsError() {
    ChartOfAccount account = createPostableAccount(1L, "111", "Cash");
    account.setCompanyId(999L); // Different company

    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    request.setLines(List.of(createLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "Cash")));

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().containsKey(1));
    assertTrue(result.getErrors().get(1).containsKey("accountId"));
    assertTrue(
        result.getErrors().get(1).get("accountId").contains("does not belong to your company"));
  }

  @Test
  void validate_bothDebitAndCreditNonZero_returnsError() {
    ChartOfAccount account = createPostableAccount(1L, "111", "Cash");

    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    VoucherLineDTO line = createLine(1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(500), "Error");
    request.setLines(List.of(line));

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().containsKey(1));
    assertTrue(result.getErrors().get(1).containsKey("debit"));
    assertTrue(result.getErrors().get(1).containsKey("credit"));
  }

  @Test
  void validate_bothDebitAndCreditZero_returnsError() {
    ChartOfAccount account = createPostableAccount(1L, "111", "Cash");

    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    VoucherLineDTO line = createLine(1L, BigDecimal.ZERO, BigDecimal.ZERO, "Error");
    request.setLines(List.of(line));

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().containsKey(1));
    assertTrue(result.getErrors().get(1).containsKey("debit"));
    assertTrue(result.getErrors().get(1).containsKey("credit"));
  }

  @Test
  void validate_account131MissingCustomer_returnsError() {
    ChartOfAccount account = createPostableAccount(1L, "131", "Accounts Receivable");

    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    VoucherLineDTO line = createLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "AR");
    line.setCustomerId(null); // Missing customer
    request.setLines(List.of(line));

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().containsKey(1));
    assertTrue(result.getErrors().get(1).containsKey("customerId"));
  }

  @Test
  void validate_account331MissingVendor_returnsError() {
    ChartOfAccount account = createPostableAccount(1L, "331", "Accounts Payable");

    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    VoucherLineDTO line = createLine(1L, BigDecimal.ZERO, BigDecimal.valueOf(1000), "AP");
    line.setVendorId(null); // Missing vendor
    request.setLines(List.of(line));

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().containsKey(1));
    assertTrue(result.getErrors().get(1).containsKey("vendorId"));
  }

  @Test
  void validate_account154MissingCostCenter_returnsError() {
    ChartOfAccount account = createPostableAccount(1L, "154", "Work in Progress");

    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    VoucherLineDTO line = createLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "WIP");
    line.setCostCenterId(null); // Missing cost center
    request.setLines(List.of(line));

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().containsKey(1));
    assertTrue(result.getErrors().get(1).containsKey("costCenterId"));
  }

  @Test
  void validate_emptyLines_returnsError() {
    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    request.setLines(List.of());

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().containsKey(0));
    assertTrue(result.getErrors().get(0).containsKey("general"));
  }

  @Test
  void validate_nullLines_returnsError() {
    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    request.setLines(null);

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().containsKey(0));
    assertTrue(result.getErrors().get(0).containsKey("general"));
  }

  @Test
  void validate_multipleErrors_returnedAtOnce() {
    ChartOfAccount account = createNonPostableAccount(1L, "111", "Cash");

    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    VoucherLineDTO line = createLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "Cash");
    line.setAccountId(null); // Missing account
    request.setLines(List.of(line));

    VoucherValidationResult result = validationService.validate(request);

    assertFalse(result.isValid());
    // Should have error for missing account
    assertTrue(result.getErrors().containsKey(1));
  }

  private ChartOfAccount createPostableAccount(Long id, String code, String name) {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(id);
    account.setCompanyId(1L);
    account.setCode(code);
    account.setName(name);
    account.setType("Asset");
    account.setNormalSide("Debit");
    account.setPostable(true);
    account.setOrderingPosition(1);
    return account;
  }

  private ChartOfAccount createNonPostableAccount(Long id, String code, String name) {
    ChartOfAccount account = createPostableAccount(id, code, name);
    account.setPostable(false);
    return account;
  }

  private VoucherLineDTO createLine(Long accountId, BigDecimal debit, BigDecimal credit, String description) {
    VoucherLineDTO line = new VoucherLineDTO();
    line.setAccountId(accountId);
    line.setDebit(debit);
    line.setCredit(credit);
    line.setDescription(description);
    return line;
  }
}
