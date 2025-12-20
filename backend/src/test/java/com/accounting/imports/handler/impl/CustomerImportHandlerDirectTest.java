package com.accounting.imports.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.accounting.dto.CustomerCreateRequest;
import com.accounting.dto.CustomerDTO;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportSummary;
import com.accounting.imports.service.ImportErrorReportService;
import com.accounting.service.CustomerService;

@ExtendWith(MockitoExtension.class)
class CustomerImportHandlerDirectTest {

  @Mock
  private CustomerService customerService;
  @Mock
  private ImportErrorReportService errorReportService;

  private CustomerImportHandler handler;

  @BeforeEach
  void setUp() {
    jakarta.validation.Validator validator = jakarta.validation.Validation.buildDefaultValidatorFactory()
        .getValidator();
    handler = new CustomerImportHandler(customerService, validator, errorReportService);
    // CustomerService.create() returns CustomerDTO
    CustomerDTO mockCustomer = new CustomerDTO();
    mockCustomer.setId(1L);
    when(customerService.create(any(CustomerCreateRequest.class))).thenReturn(mockCustomer);
  }

  @Test
  void parseCsv_shouldParseValidFile() throws Exception {
    // Read the actual template file
    Path moduleRoot = Path.of("").toAbsolutePath().normalize();
    Path projectRoot = moduleRoot.getParent() != null ? moduleRoot.getParent() : moduleRoot;
    Path templatePath = projectRoot.resolve(Path.of("docs", "assets", "import-templates", "customers-template.csv"))
        .normalize();

    if (!Files.exists(templatePath)) {
      throw new IllegalStateException("Template file not found: " + templatePath);
    }

    byte[] fileBytes = Files.readAllBytes(templatePath);
    System.err.println("Template file size: " + fileBytes.length + " bytes");

    // Create MockMultipartFile
    MockMultipartFile file = new MockMultipartFile("file", "customers-template.csv", "text/csv", fileBytes);
    System.err.println("MockMultipartFile: isEmpty=" + file.isEmpty() + ", size=" + file.getSize());

    // Verify file can be read
    String content = new String(file.getBytes(), StandardCharsets.UTF_8);
    System.err.println("File content (first 200 chars): " + content.substring(0, Math.min(200, content.length())));
    System.err.println("File lines: " + content.lines().count());

    // Create import context
    ImportContext context = new ImportContext(
        1L,
        1L,
        "customers-template.csv",
        Instant.now(),
        "en",
        java.util.UUID.randomUUID(),
        null,
        null);

    // Call handler directly
    ImportSummary result = handler.handle(file, context);

    System.err.println(
        "Import result: successCount="
            + result.getSuccessCount()
            + ", errorCount="
            + result.getErrorCount());

    // Assertions
    assertThat(result.getErrorCount()).isZero();
    assertThat(result.getSuccessCount()).isEqualTo(2);
  }
}
