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
import com.accounting.entity.Supplier;
import com.accounting.entity.User;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SupplierControllerIntegrationTest extends com.accounting.test.IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Company testCompany;
    private Company otherCompany;
    private User testUser;
    private User otherCompanyUser;
    private String testToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        supplierRepository.deleteAll();
        userRepository.deleteAll();
        // Delete chart of accounts first to avoid FK constraint violations
        jdbcTemplate.execute("UPDATE chart_of_accounts SET parent_id = NULL");
        jdbcTemplate.execute("DELETE FROM chart_of_accounts");
        // Delete supplier code sequences to avoid FK constraint violations
        jdbcTemplate.execute("DELETE FROM supplier_code_sequences");
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

        // Set company context BEFORE saving users (required for company scoping)
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
        // Temporarily set context to otherCompany for saving otherCompanyUser
        CompanyContext.setCompanyId(otherCompany.getId());
        otherCompanyUser = userRepository.save(otherCompanyUser);
        // Restore context to testCompany
        CompanyContext.setCompanyId(testCompany.getId());

        testToken = jwtTokenProvider.generateAccessToken(
                testUser.getId(), testUser.getEmail(), testUser.getRole());
        adminToken = jwtTokenProvider.generateAccessToken(
                adminUser.getId(), adminUser.getEmail(), adminUser.getRole());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void getSuppliers_returnsPaginatedList() throws Exception {
        // Create test suppliers
        Supplier supplier1 = createSupplier(testCompany.getId(), "SUP-2025-0001", "Supplier 1");
        Supplier supplier2 = createSupplier(testCompany.getId(), "SUP-2025-0002", "Supplier 2");
        supplierRepository.save(supplier1);
        supplierRepository.save(supplier2);

        mockMvc
                .perform(
                        get("/api/v1/suppliers")
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
    void getSuppliers_withStatusFilter_filtersByStatus() throws Exception {
        Supplier activeSupplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Active Supplier");
        activeSupplier.setActive(true);
        Supplier inactiveSupplier = createSupplier(testCompany.getId(), "SUP-2025-0002", "Inactive Supplier");
        inactiveSupplier.setActive(false);
        supplierRepository.save(activeSupplier);
        supplierRepository.save(inactiveSupplier);

        mockMvc
                .perform(
                        get("/api/v1/suppliers")
                                .param("status", "true")
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getSuppliers_withSearchTerm_searchesCodeOrName() throws Exception {
        Supplier matchingSupplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Matching Supplier");
        Supplier nonMatchingSupplier = createSupplier(testCompany.getId(), "SUP-2025-0002", "Other Supplier");
        supplierRepository.save(matchingSupplier);
        supplierRepository.save(nonMatchingSupplier);

        mockMvc
                .perform(
                        get("/api/v1/suppliers")
                                .param("search", "Matching")
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Matching Supplier"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getSuppliers_withSorting_sortsResults() throws Exception {
        Supplier supplier1 = createSupplier(testCompany.getId(), "SUP-2025-0001", "Supplier A");
        Supplier supplier2 = createSupplier(testCompany.getId(), "SUP-2025-0002", "Supplier B");
        supplierRepository.save(supplier1);
        supplierRepository.save(supplier2);

        mockMvc
                .perform(
                        get("/api/v1/suppliers")
                                .param("sort", "name,desc")
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Supplier B"));
    }

    @Test
    void getSuppliers_companyScoping_onlyShowsCurrentCompanySuppliers() throws Exception {
        Supplier company1Supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Company 1 Supplier");
        supplierRepository.save(company1Supplier);

        // Set context to otherCompany for saving company2Supplier
        CompanyContext.setCompanyId(otherCompany.getId());
        Supplier company2Supplier = createSupplier(otherCompany.getId(), "SUP-2025-0001", "Company 2 Supplier");
        supplierRepository.save(company2Supplier);
        // Restore context to testCompany
        CompanyContext.setCompanyId(testCompany.getId());

        mockMvc
                .perform(
                        get("/api/v1/suppliers")
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Company 1 Supplier"));
    }

    @Test
    void getSupplierById_returnsSupplier() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Test Supplier");
        supplier = supplierRepository.save(supplier);

        mockMvc
                .perform(
                        get("/api/v1/suppliers/" + supplier.getId())
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(supplier.getId()))
                .andExpect(jsonPath("$.data.name").value("Test Supplier"))
                .andExpect(jsonPath("$.data.code").value("SUP-2025-0001"));
    }

    @Test
    void getSupplierById_notFound_whenSupplierDoesNotExist() throws Exception {
        mockMvc
                .perform(
                        get("/api/v1/suppliers/99999")
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSupplierAPSummary_returnsSummary() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Test Supplier");
        supplier = supplierRepository.save(supplier);

        mockMvc
                .perform(
                        get("/api/v1/suppliers/" + supplier.getId() + "/ap-summary")
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.openBills").exists())
                .andExpect(jsonPath("$.data.totalOwed").exists())
                .andExpect(jsonPath("$.data.averagePaymentDays").exists());
    }

    @Test
    void createSupplier_createsSupplierWithAutoGeneratedCode() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "name", "New Supplier",
                        "taxCode", "1234567890",
                        "email", "new@example.com",
                        "phone", "+84123456789",
                        "address", "123 Test St",
                        "active", true));

        mockMvc
                .perform(
                        post("/api/v1/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("New Supplier"))
                .andExpect(jsonPath("$.data.code").exists())
                .andExpect(jsonPath("$.data.taxCode").value("1234567890"))
                .andExpect(jsonPath("$.data.email").value("new@example.com"));
    }

    @Test
    void createSupplier_createsSupplierWithProvidedCode() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "code", "SUPPLIER-001",
                        "name", "Supplier With Code",
                        "active", true));

        mockMvc
                .perform(
                        post("/api/v1/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("SUPPLIER-001"))
                .andExpect(jsonPath("$.data.name").value("Supplier With Code"));
    }

    @Test
    void createSupplier_forbidden_whenNotAuthorized() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of("name", "New Supplier", "active", true));

        mockMvc
                .perform(
                        post("/api/v1/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isForbidden()); // Spring Security returns 403 when @PreAuthorize fails without auth
    }

    @Test
    void updateSupplier_updatesSupplier() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Original Name");
        supplier = supplierRepository.save(supplier);

        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "name", "Updated Name",
                        "email", "updated@example.com"));

        mockMvc
                .perform(
                        put("/api/v1/suppliers/" + supplier.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.email").value("updated@example.com"))
                .andExpect(jsonPath("$.data.code").value("SUP-2025-0001")); // Code unchanged
    }

    @Test
    void updateSupplier_updatesCode() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Test Supplier");
        supplier = supplierRepository.save(supplier);

        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of("code", "NEW-CODE-001"));

        mockMvc
                .perform(
                        put("/api/v1/suppliers/" + supplier.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("NEW-CODE-001"));
    }

    @Test
    void deleteSupplier_blocksDeletionWithReferentialIntegrity() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "To Delete");
        supplier = supplierRepository.save(supplier);

        mockMvc
                .perform(
                        delete("/api/v1/suppliers/" + supplier.getId())
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());

        // Verify supplier is NOT deleted (deletion is blocked)
        assert supplierRepository.findById(supplier.getId()).isPresent();
    }

    @Test
    void activateSupplier_activatesSupplier() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Inactive Supplier");
        supplier.setActive(false);
        supplier = supplierRepository.save(supplier);

        mockMvc
                .perform(
                        patch("/api/v1/suppliers/" + supplier.getId() + "/activate")
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNoContent());

        Supplier updated = supplierRepository.findById(supplier.getId()).orElseThrow();
        assert updated.getActive() == true;
    }

    @Test
    void deactivateSupplier_deactivatesSupplier() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Active Supplier");
        supplier.setActive(true);
        supplier = supplierRepository.save(supplier);

        mockMvc
                .perform(
                        patch("/api/v1/suppliers/" + supplier.getId() + "/deactivate")
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNoContent());

        Supplier updated = supplierRepository.findById(supplier.getId()).orElseThrow();
        assert updated.getActive() == false;
    }

    @Test
    void createSupplier_conflict_whenDuplicateTaxCode() throws Exception {
        Supplier existingSupplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Existing Supplier");
        existingSupplier.setTaxCode("1234567890");
        supplierRepository.save(existingSupplier);

        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "name", "Duplicate Tax Code Supplier",
                        "taxCode", "1234567890",
                        "active", true));

        mockMvc
                .perform(
                        post("/api/v1/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void createSupplier_conflict_whenDuplicateEmail() throws Exception {
        Supplier existingSupplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Existing Supplier");
        existingSupplier.setEmail("duplicate@example.com");
        supplierRepository.save(existingSupplier);

        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "name", "Duplicate Email Supplier",
                        "email", "duplicate@example.com",
                        "active", true));

        mockMvc
                .perform(
                        post("/api/v1/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void createSupplier_conflict_whenDuplicatePhone() throws Exception {
        Supplier existingSupplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Existing Supplier");
        existingSupplier.setPhone("+84123456789");
        supplierRepository.save(existingSupplier);

        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "name", "Duplicate Phone Supplier",
                        "phone", "+84123456789",
                        "active", true));

        mockMvc
                .perform(
                        post("/api/v1/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void updateSupplier_conflict_whenDuplicateTaxCode() throws Exception {
        Supplier supplier1 = createSupplier(testCompany.getId(), "SUP-2025-0001", "Supplier 1");
        supplier1.setTaxCode("1111111111");
        supplier1 = supplierRepository.save(supplier1);

        Supplier supplier2 = createSupplier(testCompany.getId(), "SUP-2025-0002", "Supplier 2");
        supplier2.setTaxCode("2222222222");
        supplier2 = supplierRepository.save(supplier2);

        // Try to update supplier2 with supplier1's tax code
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of("taxCode", "1111111111"));

        mockMvc
                .perform(
                        put("/api/v1/suppliers/" + supplier2.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void createSupplier_allowsSameTaxCodeForDifferentCompanies() throws Exception {
        Supplier company1Supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Company 1 Supplier");
        company1Supplier.setTaxCode("1234567890");
        supplierRepository.save(company1Supplier);

        // Create supplier with same tax code but different company
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "name", "Company 2 Supplier",
                        "taxCode", "1234567890",
                        "active", true));

        mockMvc
                .perform(
                        post("/api/v1/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(otherCompany.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.taxCode").value("1234567890"));
    }

    @Test
    void exportSuppliers_returnsExcelFile() throws Exception {
        // Create test suppliers
        Supplier supplier1 = createSupplier(testCompany.getId(), "SUP-2025-0001", "Export Supplier 1");
        supplier1.setTaxCode("1111111111");
        supplier1.setEmail("export1@example.com");
        supplierRepository.save(supplier1);

        Supplier supplier2 = createSupplier(testCompany.getId(), "SUP-2025-0002", "Export Supplier 2");
        supplier2.setTaxCode("2222222222");
        supplier2.setEmail("export2@example.com");
        supplierRepository.save(supplier2);

        byte[] response = mockMvc
                .perform(
                        get("/api/v1/suppliers/export")
                                .param("format", "xlsx")
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .exists("Content-Disposition"))
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
    void exportSuppliers_returnsCSVFile() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "CSV Supplier");
        supplier.setTaxCode("1234567890");
        supplierRepository.save(supplier);

        String response = mockMvc
                .perform(
                        get("/api/v1/suppliers/export")
                                .param("format", "csv")
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .exists("Content-Disposition"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify CSV content
        assert response.contains("Code");
        assert response.contains("Name");
        assert response.contains("SUP-2025-0001");
        assert response.contains("CSV Supplier");
    }

    @Test
    void exportSuppliers_withFilters_appliesFilters() throws Exception {
        Supplier activeSupplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Active Supplier");
        activeSupplier.setActive(true);
        supplierRepository.save(activeSupplier);

        Supplier inactiveSupplier = createSupplier(testCompany.getId(), "SUP-2025-0002", "Inactive Supplier");
        inactiveSupplier.setActive(false);
        supplierRepository.save(inactiveSupplier);

        String response = mockMvc
                .perform(
                        get("/api/v1/suppliers/export")
                                .param("format", "csv")
                                .param("status", "true")
                                .header("Authorization", "Bearer " + testToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Should only contain active supplier
        assert response.contains("Active Supplier");
        assert !response.contains("Inactive Supplier");
    }

    @Test
    void importSuppliers_importsValidCSV() throws Exception {
        String csvContent = "Code,Name,Tax Code,Email,Phone,Address,Active\n"
                + "IMPORT-001,Imported Supplier 1,1111111111,import1@example.com,+84123456789,123 Import St,true\n"
                + "IMPORT-002,Imported Supplier 2,2222222222,import2@example.com,+84987654321,456 Import St,true\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "suppliers.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc
                .perform(
                        multipart("/api/v1/suppliers/import")
                                .file(file)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.errors").isEmpty());

        // Verify suppliers were created
        List<Supplier> allSuppliers = supplierRepository.findByCompanyId(testCompany.getId());
        assert allSuppliers.stream().anyMatch(s -> "IMPORT-001".equals(s.getCode()));
        assert allSuppliers.stream().anyMatch(s -> "IMPORT-002".equals(s.getCode()));
    }

    @Test
    void importSuppliers_importsValidExcel() throws Exception {
        // Create Excel file
        ByteArrayOutputStream excelOutput = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Suppliers");

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
            row1.createCell(1).setCellValue("Excel Supplier 1");
            row1.createCell(2).setCellValue("3333333333");
            row1.createCell(3).setCellValue("excel1@example.com");
            row1.createCell(4).setCellValue("+84111111111");
            row1.createCell(5).setCellValue("789 Excel St");
            row1.createCell(6).setCellValue("true");

            workbook.write(excelOutput);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "suppliers.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelOutput.toByteArray());

        mockMvc
                .perform(
                        multipart("/api/v1/suppliers/import")
                                .file(file)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.errorCount").value(0));

        // Verify supplier was created
        List<Supplier> allSuppliers = supplierRepository.findByCompanyId(testCompany.getId());
        assert allSuppliers.stream().anyMatch(s -> "EXCEL-001".equals(s.getCode()));
    }

    @Test
    void importSuppliers_returnsErrorsForInvalidData() throws Exception {
        // CSV with invalid data (invalid tax code, invalid email, missing name)
        String csvContent = "Code,Name,Tax Code,Email,Phone,Address,Active\n"
                + "INVALID-001,Valid Supplier,1234567890,valid@example.com,+84123456789,123 St,true\n"
                + "INVALID-002,Invalid Tax,123,invalid-email,+84123456789,456 St,true\n" // Invalid tax code and email
                + "INVALID-003,,,missing@example.com,+84987654321,789 St,true\n"; // Missing name (empty between commas)

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "suppliers.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc
                .perform(
                        multipart("/api/v1/suppliers/import")
                                .file(file)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1)) // Only first row is valid
                .andExpect(jsonPath("$.errorCount").value(3)) // Row 2 has 2 errors (tax code, email), row 3 has 1 error
                                                              // (name)
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(3));

        // Verify only valid supplier was created
        List<Supplier> allSuppliers = supplierRepository.findByCompanyId(testCompany.getId());
        assert allSuppliers.stream().anyMatch(s -> "INVALID-001".equals(s.getCode()));
        assert allSuppliers.stream().noneMatch(s -> "INVALID-002".equals(s.getCode()));
        assert allSuppliers.stream().noneMatch(s -> "INVALID-003".equals(s.getCode()));
    }

    @Test
    void importSuppliers_atomicTransaction_rollsBackOnError() throws Exception {
        // Create a supplier that will cause duplicate
        Supplier existing = createSupplier(testCompany.getId(), "DUPLICATE-001", "Existing Supplier");
        existing.setTaxCode("9999999999");
        supplierRepository.save(existing);

        // CSV with one valid row and one duplicate tax code
        String csvContent = "Code,Name,Tax Code,Email,Phone,Address,Active\n"
                + "NEW-001,New Supplier,1111111111,new@example.com,+84123456789,123 St,true\n"
                + "DUPLICATE-002,Duplicate Supplier,9999999999,duplicate@example.com,+84987654321,456 St,true\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "suppliers.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc
                .perform(
                        multipart("/api/v1/suppliers/import")
                                .file(file)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCount").value(1));

        // Verify atomic transaction: no new supplier should be created
        List<Supplier> allSuppliers = supplierRepository.findByCompanyId(testCompany.getId());
        assert allSuppliers.stream().noneMatch(s -> "NEW-001".equals(s.getCode()));
    }

    @Test
    void importSuppliers_forbidden_whenNotAuthorized() throws Exception {
        String csvContent = "Code,Name,Tax Code,Email,Phone,Address,Active\n"
                + "TEST-001,Test Supplier,1111111111,test@example.com,+84123456789,123 St,true\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "suppliers.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc
                .perform(
                        multipart("/api/v1/suppliers/import")
                                .file(file)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isForbidden()); // Spring Security returns 403 when @PreAuthorize fails without auth
    }

    @Test
    void createSupplier_logsAuditEntry() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "name", "Audit Test Supplier",
                        "taxCode", "1234567890",
                        "active", true));

        mockMvc
                .perform(
                        post("/api/v1/suppliers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isCreated());

        // Verify audit log was created
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assert auditLogs.stream()
                .anyMatch(log -> "SUPPLIER_CREATED".equals(log.getAction()));
    }

    @Test
    void updateSupplier_logsAuditEntry() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Original Name");
        supplier = supplierRepository.save(supplier);

        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of("name", "Updated Name"));

        mockMvc
                .perform(
                        put("/api/v1/suppliers/" + supplier.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isOk());

        // Verify audit log was created
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assert auditLogs.stream()
                .anyMatch(log -> "SUPPLIER_UPDATED".equals(log.getAction()));
    }

    @Test
    void deleteSupplier_logsAuditEntry() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "To Delete");
        supplier = supplierRepository.save(supplier);

        mockMvc
                .perform(
                        delete("/api/v1/suppliers/" + supplier.getId())
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isConflict()); // Deletion is blocked, but audit is still logged

        // Verify audit log was created (even though deletion was blocked)
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assert auditLogs.stream()
                .anyMatch(log -> "SUPPLIER_DELETED".equals(log.getAction()));
    }

    @Test
    void activateSupplier_logsAuditEntry() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Inactive Supplier");
        supplier.setActive(false);
        supplier = supplierRepository.save(supplier);

        mockMvc
                .perform(
                        patch("/api/v1/suppliers/" + supplier.getId() + "/activate")
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNoContent());

        // Verify audit log was created
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assert auditLogs.stream()
                .anyMatch(log -> "SUPPLIER_ACTIVATED".equals(log.getAction()));
    }

    @Test
    void deactivateSupplier_logsAuditEntry() throws Exception {
        Supplier supplier = createSupplier(testCompany.getId(), "SUP-2025-0001", "Active Supplier");
        supplier.setActive(true);
        supplier = supplierRepository.save(supplier);

        mockMvc
                .perform(
                        patch("/api/v1/suppliers/" + supplier.getId() + "/deactivate")
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Company-Id", String.valueOf(testCompany.getId())))
                .andExpect(status().isNoContent());

        // Verify audit log was created
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assert auditLogs.stream()
                .anyMatch(log -> "SUPPLIER_DEACTIVATED".equals(log.getAction()));
    }

    // Helper method to create a supplier
    private Supplier createSupplier(Long companyId, String code, String name) {
        Supplier supplier = new Supplier();
        supplier.setCompanyId(companyId);
        supplier.setCode(code);
        supplier.setName(name);
        supplier.setActive(true);
        supplier.setCreatedAt(Instant.now());
        supplier.setUpdatedAt(Instant.now());
        return supplier;
    }
}
