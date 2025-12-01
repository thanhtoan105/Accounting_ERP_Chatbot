package com.accounting.service.impl.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.accounting.dto.VATValidationResultDTO;
import com.accounting.dto.VoucherEntryLineRequest;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.entity.VatRate;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ARVATServiceImplTest {

  @Mock
  private SalesInvoiceLineRepository salesInvoiceLineRepository;
  @Mock
  private ChartOfAccountsRepository chartOfAccountsRepository;
  @Mock
  private AuditService auditService;

  private ARVATServiceImpl arVatService;
  private static final Long COMPANY_ID = 1L;
  private static final UUID INVOICE_ID = UUID.randomUUID();
  private static final Long AR_ACCOUNT_ID = 100L;
  private static final Long VAT_ACCOUNT_ID = 200L;
  private static final Long REVENUE_ACCOUNT_ID = 300L;

  @BeforeEach
  void setUp() {
    arVatService = new ARVATServiceImpl(
        salesInvoiceLineRepository,
        chartOfAccountsRepository,
        auditService);
    CompanyContext.setCompanyId(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void validateVATRate_shouldReturnValidWhenRateProvided() {
    VATValidationResultDTO result = arVatService.validateVATRate(VatRate.TEN, COMPANY_ID);

    assertTrue(result.isValid());
    assertEquals(VatRate.TEN, result.getValidatedRate());
  }

  @Test
  void validateVATRate_shouldReturnInvalidWhenRateMissing() {
    VATValidationResultDTO result = arVatService.validateVATRate(null, COMPANY_ID);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().stream()
        .anyMatch(msg -> msg.contains("VAT rate is required")));
  }

  @Test
  void calculateVAT_shouldRoundToNearest100VND() {
    // 10% VAT on 12345.67 = 1234.567, should round to nearest 100 = 1200.00
    BigDecimal amount = new BigDecimal("12345.67");
    BigDecimal vat = arVatService.calculateVAT(amount, VatRate.TEN);

    assertEquals(new BigDecimal("1200.00"), vat);
  }

  @Test
  void calculateVAT_shouldReturnZeroForZeroRate() {
    BigDecimal amount = new BigDecimal("1000.00");
    BigDecimal vat = arVatService.calculateVAT(amount, VatRate.ZERO);

    assertEquals(BigDecimal.ZERO, vat);
  }

  @Test
  void validateVATSum_shouldReturnValidWhenSumMatches() {
    SalesInvoice invoice = createInvoice(new BigDecimal("1000.00"));
    SalesInvoiceLine line1 = createLine(invoice.getId(), new BigDecimal("500.00"), new BigDecimal("50.00"));
    SalesInvoiceLine line2 = createLine(invoice.getId(), new BigDecimal("500.00"), new BigDecimal("50.00"));

    when(salesInvoiceLineRepository.findBySalesInvoiceIdOrderByLineNumberAsc(invoice.getId()))
        .thenReturn(List.of(line1, line2));

    VATValidationResultDTO result = arVatService.validateVATSum(invoice);

    assertTrue(result.isValid());
  }

  @Test
  void validateVATSum_shouldReturnInvalidWhenDifferenceExceedsTolerance() {
    SalesInvoice invoice = createInvoice(new BigDecimal("1000.00"));
    SalesInvoiceLine line = createLine(invoice.getId(), new BigDecimal("1000.00"), new BigDecimal("2000.00"));

    when(salesInvoiceLineRepository.findBySalesInvoiceIdOrderByLineNumberAsc(invoice.getId()))
        .thenReturn(List.of(line));

    VATValidationResultDTO result = arVatService.validateVATSum(invoice);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().stream()
        .anyMatch(msg -> msg.contains("exceeding tolerance")));
  }

  @Test
  void generateGLSplit_shouldCreateCorrectEntries() {
    SalesInvoice invoice = createInvoice(new BigDecimal("1000.00"));
    SalesInvoiceLine line = createLine(invoice.getId(), new BigDecimal("900.00"), new BigDecimal("100.00"));
    line.setAccountId(REVENUE_ACCOUNT_ID);

    ChartOfAccount arAccount = createAccount(AR_ACCOUNT_ID, "131", true, false);
    ChartOfAccount vatAccount = createAccount(VAT_ACCOUNT_ID, "3331", true, false);
    ChartOfAccount revenueAccount = createAccount(REVENUE_ACCOUNT_ID, "511", true, false);

    when(salesInvoiceLineRepository.findBySalesInvoiceIdOrderByLineNumberAsc(invoice.getId()))
        .thenReturn(List.of(line));
    when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "131"))
        .thenReturn(Optional.of(arAccount));
    when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "3331"))
        .thenReturn(Optional.of(vatAccount));
    when(chartOfAccountsRepository.findByIdAndCompanyId(REVENUE_ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(revenueAccount));
    when(chartOfAccountsRepository.hasChildren(REVENUE_ACCOUNT_ID))
        .thenReturn(false);

    List<VoucherEntryLineRequest> entries = arVatService.generateGLSplit(invoice);

    assertNotNull(entries);
    assertEquals(2, entries.size()); // Revenue entry + VAT entry

    // Check revenue entry: Dr AR, Cr Revenue
    VoucherEntryLineRequest revenueEntry = entries.get(0);
    assertEquals(AR_ACCOUNT_ID, revenueEntry.getDebitAccountId());
    assertEquals(REVENUE_ACCOUNT_ID, revenueEntry.getCreditAccountId());
    assertEquals(new BigDecimal("900.00"), revenueEntry.getAmount());

    // Check VAT entry: Dr AR, Cr VAT
    VoucherEntryLineRequest vatEntry = entries.get(1);
    assertEquals(AR_ACCOUNT_ID, vatEntry.getDebitAccountId());
    assertEquals(VAT_ACCOUNT_ID, vatEntry.getCreditAccountId());
    assertEquals(new BigDecimal("100.00"), vatEntry.getAmount());
  }

  @Test
  void generateGLSplit_shouldValidateRevenueAccountIsIn5xxRange() {
    SalesInvoice invoice = createInvoice(new BigDecimal("1000.00"));
    SalesInvoiceLine line = createLine(invoice.getId(), new BigDecimal("900.00"), new BigDecimal("100.00"));
    line.setAccountId(REVENUE_ACCOUNT_ID);

    ChartOfAccount arAccount = createAccount(AR_ACCOUNT_ID, "131", true, false);
    ChartOfAccount vatAccount = createAccount(VAT_ACCOUNT_ID, "3331", true, false);
    ChartOfAccount invalidAccount = createAccount(REVENUE_ACCOUNT_ID, "411", true, false); // Not 5xx

    when(salesInvoiceLineRepository.findBySalesInvoiceIdOrderByLineNumberAsc(invoice.getId()))
        .thenReturn(List.of(line));
    when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "131"))
        .thenReturn(Optional.of(arAccount));
    when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "3331"))
        .thenReturn(Optional.of(vatAccount));
    when(chartOfAccountsRepository.findByIdAndCompanyId(REVENUE_ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(invalidAccount));

    try {
      arVatService.generateGLSplit(invoice);
      assertTrue(false, "Should throw exception for non-5xx revenue account");
    } catch (org.springframework.web.server.ResponseStatusException | IllegalStateException e) {
      assertTrue(e.getMessage().contains("5xx") || e.getMessage().contains("revenue") || e.getMessage().contains("Revenue"));
    }
  }

  @Test
  void generateGLSplit_shouldValidateRevenueAccountIsLeaf() {
    SalesInvoice invoice = createInvoice(new BigDecimal("1000.00"));
    SalesInvoiceLine line = createLine(invoice.getId(), new BigDecimal("900.00"), new BigDecimal("100.00"));
    line.setAccountId(REVENUE_ACCOUNT_ID);

    ChartOfAccount arAccount = createAccount(AR_ACCOUNT_ID, "131", true, false);
    ChartOfAccount vatAccount = createAccount(VAT_ACCOUNT_ID, "3331", true, false);
    ChartOfAccount accountWithChildren = createAccount(REVENUE_ACCOUNT_ID, "511", true, false);

    when(salesInvoiceLineRepository.findBySalesInvoiceIdOrderByLineNumberAsc(invoice.getId()))
        .thenReturn(List.of(line));
    when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "131"))
        .thenReturn(Optional.of(arAccount));
    when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "3331"))
        .thenReturn(Optional.of(vatAccount));
    when(chartOfAccountsRepository.findByIdAndCompanyId(REVENUE_ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(accountWithChildren));
    when(chartOfAccountsRepository.hasChildren(REVENUE_ACCOUNT_ID))
        .thenReturn(true); // Has children - not a leaf

    try {
      arVatService.generateGLSplit(invoice);
      assertTrue(false, "Should throw exception for non-leaf revenue account");
    } catch (org.springframework.web.server.ResponseStatusException | IllegalStateException e) {
      assertTrue(e.getMessage().contains("leaf") || e.getMessage().contains("postable") || e.getMessage().contains("has children"));
    }
  }

  @Test
  void generateCreditNoteGLSplit_shouldInvertGLSplit() {
    // Original invoice: Dr 131 1000, Cr 511 900, Cr 3331 100
    SalesInvoice originalInvoice = createInvoice(new BigDecimal("1000.00"));
    SalesInvoiceLine originalLine = createLine(originalInvoice.getId(), new BigDecimal("900.00"), new BigDecimal("100.00"));
    originalLine.setAccountId(REVENUE_ACCOUNT_ID);

    // Credit note: Use positive amounts (system will invert GL splits)
    // Should invert to Cr 131 1000, Dr 511 900, Dr 3331 100
    SalesInvoice creditNote = createInvoice(new BigDecimal("1000.00"));
    SalesInvoiceLine creditLine = createLine(creditNote.getId(), new BigDecimal("900.00"), new BigDecimal("100.00"));
    creditLine.setAccountId(REVENUE_ACCOUNT_ID);

    ChartOfAccount arAccount = createAccount(AR_ACCOUNT_ID, "131", true, false);
    ChartOfAccount vatAccount = createAccount(VAT_ACCOUNT_ID, "3331", true, false);
    ChartOfAccount revenueAccount = createAccount(REVENUE_ACCOUNT_ID, "511", true, false);

    when(salesInvoiceLineRepository.findBySalesInvoiceIdOrderByLineNumberAsc(creditNote.getId()))
        .thenReturn(List.of(creditLine));
    when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "131"))
        .thenReturn(Optional.of(arAccount));
    when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "3331"))
        .thenReturn(Optional.of(vatAccount));
    when(chartOfAccountsRepository.findByIdAndCompanyId(REVENUE_ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(revenueAccount));
    when(chartOfAccountsRepository.hasChildren(REVENUE_ACCOUNT_ID))
        .thenReturn(false);

    List<VoucherEntryLineRequest> entries = arVatService.generateCreditNoteGLSplit(creditNote, originalInvoice);

    assertNotNull(entries);
    assertEquals(2, entries.size());

    // Check revenue entry: Cr AR, Dr Revenue (inverted)
    VoucherEntryLineRequest revenueEntry = entries.get(0);
    assertEquals(REVENUE_ACCOUNT_ID, revenueEntry.getDebitAccountId());
    assertEquals(AR_ACCOUNT_ID, revenueEntry.getCreditAccountId());
    assertEquals(new BigDecimal("900.00"), revenueEntry.getAmount());

    // Check VAT entry: Cr AR, Dr VAT (inverted)
    VoucherEntryLineRequest vatEntry = entries.get(1);
    assertEquals(VAT_ACCOUNT_ID, vatEntry.getDebitAccountId());
    assertEquals(AR_ACCOUNT_ID, vatEntry.getCreditAccountId());
    assertEquals(new BigDecimal("100.00"), vatEntry.getAmount());
  }

  // Helper methods
  private SalesInvoice createInvoice(BigDecimal vatAmount) {
    SalesInvoice invoice = new SalesInvoice();
    invoice.setId(INVOICE_ID);
    invoice.setCompanyId(COMPANY_ID);
    invoice.setInvoiceNumber("INV-001");
    invoice.setInvoiceDate(java.time.LocalDate.now());
    invoice.setStatus(SalesInvoiceStatus.DRAFT);
    invoice.setVatAmount(vatAmount);
    return invoice;
  }

  private SalesInvoiceLine createLine(UUID invoiceId, BigDecimal amount, BigDecimal vatAmount) {
    SalesInvoiceLine line = new SalesInvoiceLine();
    line.setId(UUID.randomUUID());
    line.setSalesInvoiceId(invoiceId);
    line.setCompanyId(COMPANY_ID);
    line.setLineNumber(1);
    line.setAmount(amount);
    line.setVatAmount(vatAmount);
    line.setVatRate(VatRate.TEN);
    return line;
  }

  private ChartOfAccount createAccount(Long id, String code, boolean postable, boolean hasChildren) {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(id);
    account.setCompanyId(COMPANY_ID);
    account.setCode(code);
    account.setPostable(postable);
    return account;
  }
}

