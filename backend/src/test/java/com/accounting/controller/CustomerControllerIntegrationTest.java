package com.accounting.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.entity.AuditLog;
import com.accounting.entity.Company;
import com.accounting.entity.Customer;
import com.accounting.entity.User;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private CompanyRepository companyRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private AuditLogRepository auditLogRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  @Autowired private ObjectMapper objectMapper;

  private Company testCompany;
  private Company otherCompany;
  private User testUser;
  private User otherCompanyUser;
  private String testToken;
  private String adminToken;

  @BeforeEach
  void setUp() {
    auditLogRepository.deleteAll();
    customerRepository.deleteAll();
    userRepository.deleteAll();
    companyRepository.deleteAll();

    // Create test companies
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

    CompanyContext.setCompanyId(testCompany.getId());

    // Create test users
    testUser = new User();
    testUser.setEmail("test@example.com");
    testUser.setPasswordHash(passwordEncoder.encode("Password123!"));
    testUser.setFullName("Test User");
    testUser.setRole("accountant");
    testUser.setStatus("ACTIVE");
    testUser.setCompanyId(testCompany.getId());
    testUser.setCreatedAt(Instant.now());
    testUser.setUpdatedAt(Instant.now());
    testUser = userRepository.save(testUser);

    User adminUser = new User();
    adminUser.setEmail("admin@example.com");
    adminUser.setPasswordHash(passwordEncoder.encode("Password123!"));
    adminUser.setFullName("Admin User");
    adminUser.setRole("admin");
    adminUser.setStatus("ACTIVE");
    adminUser.setCompanyId(testCompany.getId());
    adminUser.setCreatedAt(Instant.now());
    adminUser.setUpdatedAt(Instant.now());
    adminUser = userRepository.save(adminUser);

    otherCompanyUser = new User();
    otherCompanyUser.setEmail("other@example.com");
    otherCompanyUser.setPasswordHash(passwordEncoder.encode("Password123!"));
    otherCompanyUser.setFullName("Other User");
    otherCompanyUser.setRole("accountant");
    otherCompanyUser.setStatus("ACTIVE");
    otherCompanyUser.setCompanyId(otherCompany.getId());
    otherCompanyUser.setCreatedAt(Instant.now());
    otherCompanyUser.setUpdatedAt(Instant.now());
    otherCompanyUser = userRepository.save(otherCompanyUser);

    testToken =
        jwtTokenProvider.generateAccessToken(
            testUser.getId(), testUser.getEmail(), testUser.getRole());
    adminToken =
        jwtTokenProvider.generateAccessToken(
            adminUser.getId(), adminUser.getEmail(), adminUser.getRole());
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void getCustomers_returnsPaginatedList() throws Exception {
    // Create test customers
    Customer customer1 = createCustomer(testCompany.getId(), "CUST-2025-0001", "Customer 1");
    Customer customer2 = createCustomer(testCompany.getId(), "CUST-2025-0002", "Customer 2");
    customerRepository.save(customer1);
    customerRepository.save(customer2);

    mockMvc
        .perform(
            get("/api/v1/customers")
                .param("page", "0")
                .param("size", "20")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void getCustomers_withStatusFilter_filtersByStatus() throws Exception {
    Customer activeCustomer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Active Customer");
    activeCustomer.setActive(true);
    Customer inactiveCustomer = createCustomer(testCompany.getId(), "CUST-2025-0002", "Inactive Customer");
    inactiveCustomer.setActive(false);
    customerRepository.save(activeCustomer);
    customerRepository.save(inactiveCustomer);

    mockMvc
        .perform(
            get("/api/v1/customers")
                .param("status", "true")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].active").value(true))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void getCustomers_withSearchTerm_searchesCodeOrName() throws Exception {
    Customer matchingCustomer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Matching Customer");
    Customer nonMatchingCustomer = createCustomer(testCompany.getId(), "CUST-2025-0002", "Other Customer");
    customerRepository.save(matchingCustomer);
    customerRepository.save(nonMatchingCustomer);

    mockMvc
        .perform(
            get("/api/v1/customers")
                .param("search", "Matching")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].name").value("Matching Customer"))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void getCustomers_withSorting_sortsResults() throws Exception {
    Customer customer1 = createCustomer(testCompany.getId(), "CUST-2025-0001", "Customer A");
    Customer customer2 = createCustomer(testCompany.getId(), "CUST-2025-0002", "Customer B");
    customerRepository.save(customer1);
    customerRepository.save(customer2);

    mockMvc
        .perform(
            get("/api/v1/customers")
                .param("sort", "name,desc")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].name").value("Customer B"));
  }

  @Test
  void getCustomers_companyScoping_onlyShowsCurrentCompanyCustomers() throws Exception {
    Customer company1Customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Company 1 Customer");
    Customer company2Customer = createCustomer(otherCompany.getId(), "CUST-2025-0001", "Company 2 Customer");
    customerRepository.save(company1Customer);
    customerRepository.save(company2Customer);

    mockMvc
        .perform(
            get("/api/v1/customers")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.data[0].name").value("Company 1 Customer"));
  }

  @Test
  void getCustomerById_returnsCustomer() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Test Customer");
    customer = customerRepository.save(customer);

    mockMvc
        .perform(
            get("/api/v1/customers/" + customer.getId())
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(customer.getId()))
        .andExpect(jsonPath("$.data.name").value("Test Customer"))
        .andExpect(jsonPath("$.data.code").value("CUST-2025-0001"));
  }

  @Test
  void getCustomerById_notFound_whenCustomerDoesNotExist() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/customers/99999")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isNotFound());
  }

  @Test
  void getCustomerARSummary_returnsSummary() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Test Customer");
    customer = customerRepository.save(customer);

    mockMvc
        .perform(
            get("/api/v1/customers/" + customer.getId() + "/ar-summary")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").exists())
        .andExpect(jsonPath("$.data.openInvoices").exists())
        .andExpect(jsonPath("$.data.totalOwed").exists())
        .andExpect(jsonPath("$.data.averagePaymentDays").exists());
  }

  @Test
  void createCustomer_createsCustomerWithAutoGeneratedCode() throws Exception {
    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "name", "New Customer",
                "taxCode", "1234567890",
                "email", "new@example.com",
                "phone", "+84123456789",
                "address", "123 Test St",
                "active", true));

    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value("New Customer"))
        .andExpect(jsonPath("$.data.code").exists())
        .andExpect(jsonPath("$.data.taxCode").value("1234567890"))
        .andExpect(jsonPath("$.data.email").value("new@example.com"));
  }

  @Test
  void createCustomer_createsCustomerWithProvidedCode() throws Exception {
    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "code", "CUSTOM-001",
                "name", "Customer With Code",
                "active", true));

    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.code").value("CUSTOM-001"))
        .andExpect(jsonPath("$.data.name").value("Customer With Code"));
  }

  @Test
  void createCustomer_forbidden_whenNotAuthorized() throws Exception {
    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of("name", "New Customer", "active", true));

    // Create a user with no edit permissions (viewer role doesn't exist, but test with no role)
    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateCustomer_updatesCustomer() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Original Name");
    customer = customerRepository.save(customer);

    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "name", "Updated Name",
                "email", "updated@example.com"));

    mockMvc
        .perform(
            put("/api/v1/customers/" + customer.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Updated Name"))
        .andExpect(jsonPath("$.data.email").value("updated@example.com"))
        .andExpect(jsonPath("$.data.code").value("CUST-2025-0001")); // Code unchanged
  }

  @Test
  void updateCustomer_updatesCode() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Test Customer");
    customer = customerRepository.save(customer);

    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of("code", "NEW-CODE-001"));

    mockMvc
        .perform(
            put("/api/v1/customers/" + customer.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.code").value("NEW-CODE-001"));
  }

  @Test
  void deleteCustomer_deletesCustomer() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "To Delete");
    customer = customerRepository.save(customer);

    mockMvc
        .perform(
            delete("/api/v1/customers/" + customer.getId())
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isNoContent());

    // Verify customer is deleted
    assert customerRepository.findById(customer.getId()).isEmpty();
  }

  @Test
  void activateCustomer_activatesCustomer() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Inactive Customer");
    customer.setActive(false);
    customer = customerRepository.save(customer);

    mockMvc
        .perform(
            patch("/api/v1/customers/" + customer.getId() + "/activate")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isNoContent());

    Customer updated = customerRepository.findById(customer.getId()).orElseThrow();
    assert updated.getActive() == true;
  }

  @Test
  void deactivateCustomer_deactivatesCustomer() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Active Customer");
    customer.setActive(true);
    customer = customerRepository.save(customer);

    mockMvc
        .perform(
            patch("/api/v1/customers/" + customer.getId() + "/deactivate")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isNoContent());

    Customer updated = customerRepository.findById(customer.getId()).orElseThrow();
    assert updated.getActive() == false;
  }

  @Test
  void createCustomer_conflict_whenDuplicateTaxCode() throws Exception {
    Customer existingCustomer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Existing Customer");
    existingCustomer.setTaxCode("1234567890");
    customerRepository.save(existingCustomer);

    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "name", "Duplicate Tax Code Customer",
                "taxCode", "1234567890",
                "active", true));

    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void createCustomer_conflict_whenDuplicateEmail() throws Exception {
    Customer existingCustomer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Existing Customer");
    existingCustomer.setEmail("duplicate@example.com");
    customerRepository.save(existingCustomer);

    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "name", "Duplicate Email Customer",
                "email", "duplicate@example.com",
                "active", true));

    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void createCustomer_conflict_whenDuplicatePhone() throws Exception {
    Customer existingCustomer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Existing Customer");
    existingCustomer.setPhone("+84123456789");
    customerRepository.save(existingCustomer);

    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "name", "Duplicate Phone Customer",
                "phone", "+84123456789",
                "active", true));

    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void updateCustomer_conflict_whenDuplicateTaxCode() throws Exception {
    Customer customer1 = createCustomer(testCompany.getId(), "CUST-2025-0001", "Customer 1");
    customer1.setTaxCode("1111111111");
    customer1 = customerRepository.save(customer1);

    Customer customer2 = createCustomer(testCompany.getId(), "CUST-2025-0002", "Customer 2");
    customer2.setTaxCode("2222222222");
    customer2 = customerRepository.save(customer2);

    // Try to update customer2 with customer1's tax code
    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of("taxCode", "1111111111"));

    mockMvc
        .perform(
            put("/api/v1/customers/" + customer2.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void createCustomer_allowsSameTaxCodeForDifferentCompanies() throws Exception {
    Customer company1Customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Company 1 Customer");
    company1Customer.setTaxCode("1234567890");
    customerRepository.save(company1Customer);

    // Create customer with same tax code but different company
    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "name", "Company 2 Customer",
                "taxCode", "1234567890",
                "active", true));

    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(otherCompany.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.taxCode").value("1234567890"));
  }

  @Test
  void exportCustomers_returnsExcelFile() throws Exception {
    // Create test customers
    Customer customer1 = createCustomer(testCompany.getId(), "CUST-2025-0001", "Export Customer 1");
    customer1.setTaxCode("1111111111");
    customer1.setEmail("export1@example.com");
    customerRepository.save(customer1);

    Customer customer2 = createCustomer(testCompany.getId(), "CUST-2025-0002", "Export Customer 2");
    customer2.setTaxCode("2222222222");
    customer2.setEmail("export2@example.com");
    customerRepository.save(customer2);

    byte[] response = mockMvc
        .perform(
            get("/api/v1/customers/export")
                .param("format", "xlsx")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Content-Disposition"))
        .andReturn()
        .getResponse()
        .getContentAsByteArray();

    // Verify it's a valid Excel file
    assert response.length > 0;
    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response))) {
      Sheet sheet = workbook.getSheetAt(0);
      assert sheet != null;
      // Should have header row + 2 data rows
      assert sheet.getPhysicalNumberOfRows() >= 3;
    }
  }

  @Test
  void exportCustomers_returnsCSVFile() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "CSV Customer");
    customer.setTaxCode("1234567890");
    customerRepository.save(customer);

    String response = mockMvc
        .perform(
            get("/api/v1/customers/export")
                .param("format", "csv")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Content-Disposition"))
        .andReturn()
        .getResponse()
        .getContentAsString();

    // Verify CSV content
    assert response.contains("Code");
    assert response.contains("Name");
    assert response.contains("CUST-2025-0001");
    assert response.contains("CSV Customer");
  }

  @Test
  void exportCustomers_withFilters_appliesFilters() throws Exception {
    Customer activeCustomer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Active Customer");
    activeCustomer.setActive(true);
    customerRepository.save(activeCustomer);

    Customer inactiveCustomer = createCustomer(testCompany.getId(), "CUST-2025-0002", "Inactive Customer");
    inactiveCustomer.setActive(false);
    customerRepository.save(inactiveCustomer);

    String response = mockMvc
        .perform(
            get("/api/v1/customers/export")
                .param("format", "csv")
                .param("status", "true")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // Should only contain active customer
    assert response.contains("Active Customer");
    assert !response.contains("Inactive Customer");
  }

  @Test
  void importCustomers_importsValidCSV() throws Exception {
    String csvContent = "Code,Name,Tax Code,Email,Phone,Address,Active\n"
        + "IMPORT-001,Imported Customer 1,1111111111,import1@example.com,+84123456789,123 Import St,true\n"
        + "IMPORT-002,Imported Customer 2,2222222222,import2@example.com,+84987654321,456 Import St,true\n";

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "customers.csv",
        "text/csv",
        csvContent.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/customers/import")
                .file(file)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successCount").value(2))
        .andExpect(jsonPath("$.errorCount").value(0))
        .andExpect(jsonPath("$.errors").isEmpty());

    // Verify customers were created
    List<Customer> allCustomers = customerRepository.findByCompanyId(testCompany.getId());
    assert allCustomers.stream().anyMatch(c -> "IMPORT-001".equals(c.getCode()));
    assert allCustomers.stream().anyMatch(c -> "IMPORT-002".equals(c.getCode()));
  }

  @Test
  void importCustomers_importsValidExcel() throws Exception {
    // Create Excel file
    ByteArrayOutputStream excelOutput = new ByteArrayOutputStream();
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Customers");
      
      // Header row
      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("Code");
      headerRow.createCell(1).setCellValue("Name");
      headerRow.createCell(2).setCellValue("Tax Code");
      headerRow.createCell(3).setCellValue("Email");
      headerRow.createCell(4).setCellValue("Phone");
      headerRow.createCell(5).setCellValue("Address");
      headerRow.createCell(6).setCellValue("Active");
      
      // Data row 1
      Row row1 = sheet.createRow(1);
      row1.createCell(0).setCellValue("EXCEL-001");
      row1.createCell(1).setCellValue("Excel Customer 1");
      row1.createCell(2).setCellValue("3333333333");
      row1.createCell(3).setCellValue("excel1@example.com");
      row1.createCell(4).setCellValue("+84111111111");
      row1.createCell(5).setCellValue("789 Excel St");
      row1.createCell(6).setCellValue("true");
      
      workbook.write(excelOutput);
    }

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "customers.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        excelOutput.toByteArray());

    mockMvc
        .perform(
            multipart("/api/v1/customers/import")
                .file(file)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successCount").value(1))
        .andExpect(jsonPath("$.errorCount").value(0));

    // Verify customer was created
    List<Customer> allCustomers = customerRepository.findByCompanyId(testCompany.getId());
    assert allCustomers.stream().anyMatch(c -> "EXCEL-001".equals(c.getCode()));
  }

  @Test
  void importCustomers_returnsErrorsForInvalidData() throws Exception {
    // CSV with invalid data (invalid tax code, invalid email)
    String csvContent = "Code,Name,Tax Code,Email,Phone,Address,Active\n"
        + "INVALID-001,Valid Customer,1234567890,valid@example.com,+84123456789,123 St,true\n"
        + "INVALID-002,Invalid Tax,123,invalid-email,invalid-phone,456 St,true\n"
        + "INVALID-003,Missing Name,,missing@example.com,+84987654321,789 St,true\n";

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "customers.csv",
        "text/csv",
        csvContent.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/customers/import")
                .file(file)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successCount").value(1)) // Only first row is valid
        .andExpect(jsonPath("$.errorCount").value(2)) // Two invalid rows
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors.length()").value(2));

    // Verify only valid customer was created
    List<Customer> allCustomers = customerRepository.findByCompanyId(testCompany.getId());
    assert allCustomers.stream().anyMatch(c -> "INVALID-001".equals(c.getCode()));
    assert allCustomers.stream().noneMatch(c -> "INVALID-002".equals(c.getCode()));
    assert allCustomers.stream().noneMatch(c -> "INVALID-003".equals(c.getCode()));
  }

  @Test
  void importCustomers_atomicTransaction_rollsBackOnError() throws Exception {
    // Create a customer that will cause duplicate
    Customer existing = createCustomer(testCompany.getId(), "DUPLICATE-001", "Existing Customer");
    existing.setTaxCode("9999999999");
    customerRepository.save(existing);

    // CSV with one valid row and one duplicate tax code
    String csvContent = "Code,Name,Tax Code,Email,Phone,Address,Active\n"
        + "NEW-001,New Customer,1111111111,new@example.com,+84123456789,123 St,true\n"
        + "DUPLICATE-002,Duplicate Customer,9999999999,duplicate@example.com,+84987654321,456 St,true\n";

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "customers.csv",
        "text/csv",
        csvContent.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/customers/import")
                .file(file)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errorCount").value(1));

    // Verify atomic transaction: no new customer should be created
    List<Customer> allCustomers = customerRepository.findByCompanyId(testCompany.getId());
    assert allCustomers.stream().noneMatch(c -> "NEW-001".equals(c.getCode()));
  }

  @Test
  void importCustomers_forbidden_whenNotAuthorized() throws Exception {
    String csvContent = "Code,Name,Tax Code,Email,Phone,Address,Active\n"
        + "TEST-001,Test Customer,1111111111,test@example.com,+84123456789,123 St,true\n";

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "customers.csv",
        "text/csv",
        csvContent.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/customers/import")
                .file(file)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createCustomer_logsAuditEntry() throws Exception {
    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "name", "Audit Test Customer",
                "taxCode", "1234567890",
                "active", true));

    mockMvc
        .perform(
            post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isCreated());

    // Verify audit log was created
    List<AuditLog> auditLogs = auditLogRepository.findAll();
    assert auditLogs.stream()
        .anyMatch(log -> "CUSTOMER_CREATED".equals(log.getAction()));
  }

  @Test
  void updateCustomer_logsAuditEntry() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Original Name");
    customer = customerRepository.save(customer);

    String requestBody =
        objectMapper.writeValueAsString(
            java.util.Map.of("name", "Updated Name"));

    mockMvc
        .perform(
            put("/api/v1/customers/" + customer.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk());

    // Verify audit log was created
    List<AuditLog> auditLogs = auditLogRepository.findAll();
    assert auditLogs.stream()
        .anyMatch(log -> "CUSTOMER_UPDATED".equals(log.getAction()));
  }

  @Test
  void deleteCustomer_logsAuditEntry() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "To Delete");
    customer = customerRepository.save(customer);

    mockMvc
        .perform(
            delete("/api/v1/customers/" + customer.getId())
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isNoContent());

    // Verify audit log was created
    List<AuditLog> auditLogs = auditLogRepository.findAll();
    assert auditLogs.stream()
        .anyMatch(log -> "CUSTOMER_DELETED".equals(log.getAction()));
  }

  @Test
  void activateCustomer_logsAuditEntry() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Inactive Customer");
    customer.setActive(false);
    customer = customerRepository.save(customer);

    mockMvc
        .perform(
            patch("/api/v1/customers/" + customer.getId() + "/activate")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isNoContent());

    // Verify audit log was created
    List<AuditLog> auditLogs = auditLogRepository.findAll();
    assert auditLogs.stream()
        .anyMatch(log -> "CUSTOMER_ACTIVATED".equals(log.getAction()));
  }

  @Test
  void deactivateCustomer_logsAuditEntry() throws Exception {
    Customer customer = createCustomer(testCompany.getId(), "CUST-2025-0001", "Active Customer");
    customer.setActive(true);
    customer = customerRepository.save(customer);

    mockMvc
        .perform(
            patch("/api/v1/customers/" + customer.getId() + "/deactivate")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isNoContent());

    // Verify audit log was created
    List<AuditLog> auditLogs = auditLogRepository.findAll();
    assert auditLogs.stream()
        .anyMatch(log -> "CUSTOMER_DEACTIVATED".equals(log.getAction()));
  }

  // Helper method to create a customer
  private Customer createCustomer(Long companyId, String code, String name) {
    Customer customer = new Customer();
    customer.setCompanyId(companyId);
    customer.setCode(code);
    customer.setName(name);
    customer.setActive(true);
    customer.setCreatedAt(Instant.now());
    customer.setUpdatedAt(Instant.now());
    return customer;
  }
}
