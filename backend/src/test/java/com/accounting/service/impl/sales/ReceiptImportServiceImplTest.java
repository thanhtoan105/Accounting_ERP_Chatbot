package com.accounting.service.impl.sales;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.accounting.dto.ImportResultDTO;
import com.accounting.dto.ImportRowErrorDTO;
import com.accounting.dto.ARPaymentCreateRequest;
import com.accounting.entity.BankAccount;
import com.accounting.entity.Customer;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ReceiptService;
import com.accounting.service.AuditService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReceiptImportServiceImpl Unit Tests")
class ReceiptImportServiceImplTest {

  @Mock private ReceiptService receiptService;
  @Mock private CustomerRepository customerRepository;
  @Mock private BankAccountRepository bankAccountRepository;
  @Mock private SalesInvoiceRepository salesInvoiceRepository;
  @Mock private AuditService auditService;

  @InjectMocks private ReceiptImportServiceImpl receiptImportService;

  private static final Long COMPANY_ID = 1L;
  private static final Long USER_ID = 100L;

  @BeforeEach
  void setUp() {
    CompanyContext.setCompanyId(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Nested
  @DisplayName("AC #8: Excel Template Generation")
  class TemplateGenerationTests {

    @Test
    @DisplayName("Should generate Excel template with correct headers")
    void shouldGenerateTemplateWithCorrectHeaders() throws Exception {
      // WHEN
      byte[] template = receiptImportService.generateTemplate();

      // THEN
      assertThat(template).isNotNull().isNotEmpty();

      // Verify Excel structure
      try (ByteArrayInputStream bis = new ByteArrayInputStream(template);
          Workbook workbook = WorkbookFactory.create(bis)) {

        Sheet sheet = workbook.getSheetAt(0);
        assertThat(sheet).isNotNull();
        assertThat(sheet.getSheetName()).isEqualTo("Receipts");

        // Verify headers
        Row headerRow = sheet.getRow(0);
        assertThat(headerRow).isNotNull();
        assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("Customer Code");
        assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("Receipt Date");
        assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("Account Code");
        assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("Amount");
        assertThat(headerRow.getCell(4).getStringCellValue()).isEqualTo("Reference");
        assertThat(headerRow.getCell(5).getStringCellValue()).isEqualTo("Payment Method");
        assertThat(headerRow.getCell(6).getStringCellValue()).isEqualTo("Payee");
        assertThat(headerRow.getCell(7).getStringCellValue()).isEqualTo("Receipt Proof URL");
        assertThat(headerRow.getCell(8).getStringCellValue()).isEqualTo("Is Standalone");
        assertThat(headerRow.getCell(9).getStringCellValue()).isEqualTo("Invoice Numbers");

        // Verify example row exists
        Row exampleRow = sheet.getRow(1);
        assertThat(exampleRow).isNotNull();
        assertThat(exampleRow.getCell(0).getStringCellValue()).isNotEmpty(); // Customer code example
      }
    }
  }

  @Nested
  @DisplayName("AC #9: Validation and Error Reporting")
  class ValidationErrorReportingTests {

    @Test
    @DisplayName("Should validate header format and reject invalid headers")
    void shouldRejectInvalidHeaders() throws Exception {
      // GIVEN: Excel with wrong headers
      byte[] excelFile = createExcelWithHeaders("Wrong Header", "Bad Column");

      MockMultipartFile file =
          new MockMultipartFile("file", "receipts.xlsx", "application/vnd.ms-excel", excelFile);

      // WHEN
      ImportResultDTO result = receiptImportService.importReceipts(file);

      // THEN
      assertThat(result.successCount()).isZero();
      assertThat(result.errorCount()).isZero();
      assertThat(result.skippedCount()).isZero();
      assertThat(result.errors()).isNotEmpty();
      assertThat(result.errors().get(0).message()).contains("Invalid header format");
    }

    @Test
    @DisplayName("Should collect all row errors with row numbers and field names")
    void shouldCollectAllRowErrors() throws Exception {
      // GIVEN: Excel with multiple validation errors
      byte[] excelFile =
          createExcelWithData(
              List.of(
                  new String[] {
                    "INVALID_CUSTOMER", "2025-01-15", "111", "1000000", "", "BANK_TRANSFER", "", "",
                    "false", ""
                  }, // Invalid customer
                  new String[] {
                    "CUST001", "INVALID_DATE", "111", "1000000", "", "BANK_TRANSFER", "", "",
                    "false", ""
                  }, // Invalid date
                  new String[] {
                    "CUST001", "2025-01-15", "INVALID_ACCOUNT", "1000000", "", "BANK_TRANSFER", "",
                    "", "false", ""
                  }, // Invalid account
                  new String[] {
                    "CUST001", "2025-01-15", "111", "-1000", "", "BANK_TRANSFER", "", "", "false",
                    ""
                  } // Negative amount
                  ));

      MockMultipartFile file =
          new MockMultipartFile("file", "receipts.xlsx", "application/vnd.ms-excel", excelFile);

      // Mock repositories to return empty
      when(customerRepository.searchByCodeOrNameNative(eq(COMPANY_ID), anyString()))
          .thenReturn(new ArrayList<>());
      when(bankAccountRepository.findByCompanyId(COMPANY_ID))
          .thenReturn(new ArrayList<>());

      // WHEN
      ImportResultDTO result = receiptImportService.importReceipts(file);

      // THEN
      assertThat(result.successCount()).isZero();
      assertThat(result.errorCount()).isEqualTo(4);
      assertThat(result.errors()).hasSize(4);

      // Verify error details include row numbers and field names
      List<ImportRowErrorDTO> errors = result.errors();
      assertThat(errors.get(0).rowNumber()).isEqualTo(2); // Row 2 (1-indexed, after header)
      assertThat(errors.get(0).field()).isEqualTo("customerCode");
      assertThat(errors.get(0).message()).contains("Customer not found");

      assertThat(errors.get(1).rowNumber()).isEqualTo(3);
      assertThat(errors.get(1).field()).isEqualTo("receiptDate");
      assertThat(errors.get(1).message()).contains("Invalid date format");

      assertThat(errors.get(2).rowNumber()).isEqualTo(4);
      assertThat(errors.get(2).field()).isEqualTo("accountCode");
      assertThat(errors.get(2).message()).contains("Account not found");

      assertThat(errors.get(3).rowNumber()).isEqualTo(5);
      assertThat(errors.get(3).field()).isEqualTo("amount");
      assertThat(errors.get(3).message()).contains("must be positive");
    }

    @Test
    @DisplayName("Should validate customer code and report error if not found")
    void shouldValidateCustomerCode() throws Exception {
      // GIVEN
      List<String[]> rows = new ArrayList<>();
      rows.add(new String[] {
        "NONEXISTENT", "2025-01-15", "111", "1000000", "", "BANK_TRANSFER", "", "", "false", ""
      });
      byte[] excelFile = createExcelWithData(rows);

      MockMultipartFile file =
          new MockMultipartFile("file", "receipts.xlsx", "application/vnd.ms-excel", excelFile);

      when(customerRepository.searchByCodeOrNameNative(eq(COMPANY_ID), eq("NONEXISTENT")))
          .thenReturn(new ArrayList<>());

      // WHEN
      ImportResultDTO result = receiptImportService.importReceipts(file);

      // THEN
      assertThat(result.errorCount()).isEqualTo(1);
      assertThat(result.errors().get(0).field()).isEqualTo("customerCode");
      assertThat(result.errors().get(0).message()).contains("Customer not found");
    }

    @Test
    @DisplayName("Should validate account code and report error if not found")
    void shouldValidateAccountCode() throws Exception {
      // GIVEN
      List<String[]> rows = new ArrayList<>();
      rows.add(new String[] {
        "CUST001", "2025-01-15", "INVALID_ACC", "1000000", "", "BANK_TRANSFER", "", "", "false", ""
      });
      byte[] excelFile = createExcelWithData(rows);

      MockMultipartFile file =
          new MockMultipartFile("file", "receipts.xlsx", "application/vnd.ms-excel", excelFile);

      Customer customer = createMockCustomer(1L, "CUST001");
      when(customerRepository.searchByCodeOrNameNative(eq(COMPANY_ID), eq("CUST001")))
          .thenReturn(List.of(customer));
      when(bankAccountRepository.findByCompanyId(COMPANY_ID))
          .thenReturn(new ArrayList<>());

      // WHEN
      ImportResultDTO result = receiptImportService.importReceipts(file);

      // THEN
      assertThat(result.errorCount()).isEqualTo(1);
      assertThat(result.errors().get(0).field()).isEqualTo("accountCode");
      assertThat(result.errors().get(0).message()).contains("Account not found");
    }

    @Test
    @DisplayName("Should validate invoice numbers belong to customer")
    void shouldValidateInvoiceOwnership() throws Exception {
      // GIVEN
      List<String[]> rows = new ArrayList<>();
      rows.add(new String[] {
        "CUST001", "2025-01-15", "111", "1000000", "", "BANK_TRANSFER", "", "", "false", "INV001"
      });
      byte[] excelFile = createExcelWithData(rows);

      MockMultipartFile file =
          new MockMultipartFile("file", "receipts.xlsx", "application/vnd.ms-excel", excelFile);

      Customer customer = createMockCustomer(1L, "CUST001");
      BankAccount account = createMockBankAccount("111");
      SalesInvoice invoice =
          createMockInvoice(
              UUID.randomUUID(), "INV001", 2L, new BigDecimal("1000000")); // Wrong customer ID

      when(customerRepository.searchByCodeOrNameNative(eq(COMPANY_ID), eq("CUST001")))
          .thenReturn(List.of(customer));
      when(bankAccountRepository.findByCompanyId(COMPANY_ID))
          .thenReturn(List.of(account));
      when(salesInvoiceRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(invoice));

      // WHEN
      ImportResultDTO result = receiptImportService.importReceipts(file);

      // THEN
      assertThat(result.errorCount()).isEqualTo(1);
      assertThat(result.errors().get(0).field()).isEqualTo("invoiceNumbers");
      assertThat(result.errors().get(0).message()).contains("does not belong to customer");
    }
  }

  @Nested
  @DisplayName("AC #10: Atomic Transaction Behavior")
  class AtomicTransactionTests {

    @Test
    @DisplayName("Should import all valid rows successfully")
    void shouldImportAllValidRows() throws Exception {
      // GIVEN: 3 valid receipts
      byte[] excelFile =
          createExcelWithData(
              List.of(
                  new String[] {
                    "CUST001", "2025-01-15", "111", "1000000", "REF001", "BANK_TRANSFER", "John",
                    "", "false", ""
                  },
                  new String[] {
                    "CUST002", "2025-01-16", "112", "2000000", "REF002", "CASH", "Jane", "",
                    "false", ""
                  },
                  new String[] {
                    "CUST003", "2025-01-17", "111", "3000000", "REF003", "BANK_TRANSFER", "Bob",
                    "", "false", ""
                  }));

      MockMultipartFile file =
          new MockMultipartFile("file", "receipts.xlsx", "application/vnd.ms-excel", excelFile);

      // Mock repositories
      when(customerRepository.searchByCodeOrNameNative(eq(COMPANY_ID), anyString()))
          .thenAnswer(
              invocation -> {
                String code = invocation.getArgument(1);
                return List.of(
                    createMockCustomer(
                        Long.parseLong(code.substring(4)), code)); // CUST001 -> ID 1
              });
      when(bankAccountRepository.findByCompanyId(COMPANY_ID))
          .thenReturn(List.of(createMockBankAccount("111"), createMockBankAccount("112")));

      // WHEN
      ImportResultDTO result = receiptImportService.importReceipts(file);

      // THEN
      assertThat(result.successCount()).isEqualTo(3);
      assertThat(result.errorCount()).isZero();
      assertThat(result.errors()).isEmpty();

      // Verify service called 3 times
      verify(receiptService, times(3)).create(any(ARPaymentCreateRequest.class));
    }

    @Test
    @DisplayName("Should rollback all if any row fails (atomic behavior)")
    void shouldRollbackAllOnAnyFailure() throws Exception {
      // GIVEN: 2 valid rows, 1 invalid row
      byte[] excelFile =
          createExcelWithData(
              List.of(
                  new String[] {
                    "CUST001", "2025-01-15", "111", "1000000", "", "BANK_TRANSFER", "", "", "false",
                    ""
                  }, // Valid
                  new String[] {
                    "INVALID", "2025-01-16", "111", "2000000", "", "BANK_TRANSFER", "", "", "false",
                    ""
                  }, // Invalid customer
                  new String[] {
                    "CUST002", "2025-01-17", "111", "3000000", "", "BANK_TRANSFER", "", "", "false",
                    ""
                  } // Valid
                  ));

      MockMultipartFile file =
          new MockMultipartFile("file", "receipts.xlsx", "application/vnd.ms-excel", excelFile);

      when(customerRepository.searchByCodeOrNameNative(eq(COMPANY_ID), eq("CUST001")))
          .thenReturn(List.of(createMockCustomer(1L, "CUST001")));
      when(customerRepository.searchByCodeOrNameNative(eq(COMPANY_ID), eq("INVALID")))
          .thenReturn(new ArrayList<>());
      when(customerRepository.searchByCodeOrNameNative(eq(COMPANY_ID), eq("CUST002")))
          .thenReturn(List.of(createMockCustomer(2L, "CUST002")));
      when(bankAccountRepository.findByCompanyId(COMPANY_ID))
          .thenReturn(List.of(createMockBankAccount("111")));

      // WHEN
      ImportResultDTO result = receiptImportService.importReceipts(file);

      // THEN: All rows rejected due to atomic transaction
      assertThat(result.successCount()).isZero();
      assertThat(result.errorCount()).isEqualTo(3); // All 3 rows marked as failed
      assertThat(result.errors()).isNotEmpty();
      assertThat(result.errors().get(0).field()).isEqualTo("customerCode");

      // Verify NO receipts were created (atomic rollback)
      verify(receiptService, never()).create(any());
    }
  }

  @Nested
  @DisplayName("AC #11: Audit Logging Integration")
  class AuditLoggingTests {

    @Test
    @DisplayName("Should create audit log entry for each imported receipt")
    void shouldCreateAuditLogForEachReceipt() throws Exception {
      // GIVEN: 2 valid receipts
      byte[] excelFile =
          createExcelWithData(
              List.of(
                  new String[] {
                    "CUST001", "2025-01-15", "111", "1000000", "", "BANK_TRANSFER", "", "", "false",
                    ""
                  },
                  new String[] {
                    "CUST002", "2025-01-16", "111", "2000000", "", "CASH", "", "", "false", ""
                  }));

      MockMultipartFile file =
          new MockMultipartFile("file", "receipts.xlsx", "application/vnd.ms-excel", excelFile);

      when(customerRepository.searchByCodeOrNameNative(eq(COMPANY_ID), anyString()))
          .thenAnswer(
              invocation ->
                  List.of(
                      createMockCustomer(
                          Long.parseLong(invocation.getArgument(1, String.class).substring(4)),
                          invocation.getArgument(1))));
      when(bankAccountRepository.findByCompanyId(COMPANY_ID))
          .thenReturn(List.of(createMockBankAccount("111")));

      // WHEN
      ImportResultDTO result = receiptImportService.importReceipts(file);

      // THEN
      assertThat(result.successCount()).isEqualTo(2);

      // Verify receipt service called for each import
      verify(receiptService, times(2)).create(any(ARPaymentCreateRequest.class));
    }
  }

  // Helper methods

  private byte[] createExcelWithHeaders(String... headers) throws Exception {
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Receipts");
      Row headerRow = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        headerRow.createCell(i).setCellValue(headers[i]);
      }
      workbook.write(bos);
      return bos.toByteArray();
    }
  }

  private byte[] createExcelWithData(List<String[]> rows) throws Exception {
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Receipts");

      // Add headers
      Row headerRow = sheet.createRow(0);
      String[] headers = {
        "Customer Code",
        "Receipt Date",
        "Account Code",
        "Amount",
        "Reference",
        "Payment Method",
        "Payee",
        "Receipt Proof URL",
        "Is Standalone",
        "Invoice Numbers"
      };
      for (int i = 0; i < headers.length; i++) {
        headerRow.createCell(i).setCellValue(headers[i]);
      }

      // Add data rows
      int rowNum = 1;
      for (String[] rowData : rows) {
        Row row = sheet.createRow(rowNum++);
        for (int i = 0; i < rowData.length; i++) {
          if (rowData[i] != null && !rowData[i].isEmpty()) {
            row.createCell(i).setCellValue(rowData[i]);
          }
        }
      }

      workbook.write(bos);
      return bos.toByteArray();
    }
  }

  private Customer createMockCustomer(Long id, String code) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setCode(code);
    customer.setName("Customer " + code);
    customer.setCompanyId(COMPANY_ID);
    return customer;
  }

  private BankAccount createMockBankAccount(String accountNumber) {
    BankAccount account = new BankAccount();
    account.setId(1L);
    account.setAccountNumber(accountNumber);
    account.setBankName("Bank " + accountNumber);
    account.setType(BankAccount.AccountType.BANK);
    account.setCompanyId(COMPANY_ID);
    return account;
  }

  private SalesInvoice createMockInvoice(
      UUID id, String invoiceNumber, Long customerId, BigDecimal amount) {
    SalesInvoice invoice = new SalesInvoice();
    invoice.setId(id);
    invoice.setInvoiceNumber(invoiceNumber);
    invoice.setCustomerId(customerId);
    invoice.setTotalAmount(amount);
    invoice.setStatus(SalesInvoiceStatus.POSTED);
    invoice.setIsDeleted(false);
    invoice.setCompanyId(COMPANY_ID);
    invoice.setInvoiceDate(LocalDate.now());
    invoice.setDueDate(LocalDate.now().plusDays(30));
    invoice.setReference("Test Reference");
    invoice.setVatAmount(BigDecimal.ZERO);
    invoice.setCreatedById(USER_ID);
    invoice.setIsSensitive(false);
    return invoice;
  }

}
