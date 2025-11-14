package com.accounting.imports.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accounting.imports.ImportType;
import com.accounting.imports.service.impl.ImportTemplateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportTemplateServiceImplTest {

  private ImportTemplateServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new ImportTemplateServiceImpl();
  }

  @Test
  void generateCustomerCsvTemplate_containsHeadersAndSamples() {
    byte[] template = service.generateTemplate(ImportType.CUSTOMERS, "csv");
    String csv = new String(template);
    assertTrue(csv.startsWith("customer_code,name,tax_code,email,phone,address,active"));
    assertTrue(csv.contains("Sunrise Trading Co."));
  }

  @Test
  void generateBankAccountsXlsxTemplate_hasBinaryContent() {
    byte[] template = service.generateTemplate(ImportType.BANK_ACCOUNTS, "xlsx");
    // check that the file has XLSX magic header (PK for zip)
    assertTrue(template.length > 4);
    assertTrue(template[0] == 'P' && template[1] == 'K');
  }
}

