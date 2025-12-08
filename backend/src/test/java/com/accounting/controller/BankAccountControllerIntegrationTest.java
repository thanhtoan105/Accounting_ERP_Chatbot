package com.accounting.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.entity.BankAccount;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class BankAccountControllerIntegrationTest extends com.accounting.test.IntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private BankAccountRepository bankAccountRepository;

        @Autowired
        private CompanyRepository companyRepository;

        @Autowired
        private CustomerRepository customerRepository;

        @Autowired
        private SupplierRepository supplierRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        @Autowired
        private ObjectMapper objectMapper;

        private Company testCompany;
        private Company otherCompany;
        private User testUser;
        private User adminUser;
        private String testToken;
        private String adminToken;

        @BeforeEach
        void setUp() {
                bankAccountRepository.deleteAll();
                customerRepository.deleteAll();
                supplierRepository.deleteAll();
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

                adminUser = new User();
                adminUser.setEmail("admin@example.com");
                adminUser.setPasswordHash(passwordEncoder.encode("Password123!"));
                adminUser.setFullName("Admin User");
                adminUser.setRole("admin");
                adminUser.setStatus("ACTIVE");
                adminUser.setCompanyId(testCompany.getId());
                adminUser.setCreatedAt(Instant.now());
                adminUser.setUpdatedAt(Instant.now());
                adminUser = userRepository.save(adminUser);

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
        void getBankAccounts_returnsPaginatedList() throws Exception {
                // Create test bank accounts
                BankAccount account1 = createBankAccount(testCompany.getId(), "ACC-001", "Bank One");
                BankAccount account2 = createBankAccount(testCompany.getId(), "ACC-002", "Bank Two");
                bankAccountRepository.save(account1);
                bankAccountRepository.save(account2);

                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts")
                                                                .param("page", "0")
                                                                .param("size", "20")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.total").value(2))
                                .andExpect(jsonPath("$.page").value(0))
                                .andExpect(jsonPath("$.size").value(20))
                                .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        void getBankAccounts_withTypeFilter_filtersByType() throws Exception {
                BankAccount cashAccount = createBankAccount(testCompany.getId(), "CASH-001", "Cash Account");
                cashAccount.setType(BankAccount.AccountType.CASH);
                BankAccount bankAccount = createBankAccount(testCompany.getId(), "BANK-001", "Bank Account");
                bankAccount.setType(BankAccount.AccountType.BANK);
                bankAccountRepository.save(cashAccount);
                bankAccountRepository.save(bankAccount);

                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts")
                                                                .param("type", "CASH")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data[0].type").value("CASH"))
                                .andExpect(jsonPath("$.total").value(1));
        }

        @Test
        void getBankAccounts_withStatusFilter_filtersByStatus() throws Exception {
                BankAccount activeAccount = createBankAccount(testCompany.getId(), "ACC-001", "Active Bank");
                activeAccount.setActive(true);
                BankAccount inactiveAccount = createBankAccount(testCompany.getId(), "ACC-002", "Inactive Bank");
                inactiveAccount.setActive(false);
                bankAccountRepository.save(activeAccount);
                bankAccountRepository.save(inactiveAccount);

                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts")
                                                                .param("status", "true")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data[0].active").value(true))
                                .andExpect(jsonPath("$.total").value(1));
        }

        @Test
        void getBankAccounts_companyScoping_onlyShowsCurrentCompanyAccounts() throws Exception {
                BankAccount company1Account = createBankAccount(testCompany.getId(), "ACC-001", "Company 1 Bank");
                bankAccountRepository.save(company1Account);

                // Save account for other company with correct context
                CompanyContext.setCompanyId(otherCompany.getId());
                BankAccount company2Account = createBankAccount(otherCompany.getId(), "ACC-001", "Company 2 Bank");
                bankAccountRepository.save(company2Account);
                CompanyContext.setCompanyId(testCompany.getId());

                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.data[0].bankName").value("Company 1 Bank"));
        }

        @Test
        void getBankAccountById_returnsAccount() throws Exception {
                BankAccount bankAccount = createBankAccount(testCompany.getId(), "ACC-001", "Test Bank");
                bankAccount = bankAccountRepository.save(bankAccount);

                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts/" + bankAccount.getId())
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.id").value(bankAccount.getId()))
                                .andExpect(jsonPath("$.data.bankName").value("Test Bank"))
                                .andExpect(jsonPath("$.data.accountNumber").value("ACC-001"));
        }

        @Test
        void getBankAccountById_notFound_whenAccountDoesNotExist() throws Exception {
                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts/99999")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isNotFound());
        }

        @Test
        void getBalanceTooltip_returnsTooltipData() throws Exception {
                BankAccount bankAccount = createBankAccount(testCompany.getId(), "ACC-001", "Test Bank");
                bankAccount = bankAccountRepository.save(bankAccount);

                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts/" + bankAccount.getId() + "/balance-tooltip")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").exists())
                                .andExpect(jsonPath("$.data.currentBalance").exists())
                                .andExpect(jsonPath("$.data.priorBalance").exists())
                                .andExpect(jsonPath("$.data.currentPeriod").exists())
                                .andExpect(jsonPath("$.data.priorPeriod").exists());
        }

        @Test
        void createBankAccount_createsAccount() throws Exception {
                String requestBody = objectMapper.writeValueAsString(
                                java.util.Map.of(
                                                "accountNumber", "ACC-001",
                                                "bankName", "New Bank",
                                                "type", "BANK",
                                                "openingBalance", 1000.00,
                                                "active", true));

                mockMvc
                                .perform(
                                                post("/api/v1/bank-accounts")
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(requestBody)
                                                                .header("Authorization", "Bearer " + adminToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.bankName").value("New Bank"))
                                .andExpect(jsonPath("$.data.accountNumber").value("ACC-001"))
                                .andExpect(jsonPath("$.data.type").value("BANK"))
                                .andExpect(jsonPath("$.data.openingBalance").value(1000.00));
        }

        @Test
        void createBankAccount_conflict_whenDuplicateAccountNumber() throws Exception {
                BankAccount existingAccount = createBankAccount(testCompany.getId(), "ACC-001", "Existing Bank");
                bankAccountRepository.save(existingAccount);

                String requestBody = objectMapper.writeValueAsString(
                                java.util.Map.of(
                                                "accountNumber", "ACC-001",
                                                "bankName", "New Bank",
                                                "type", "BANK",
                                                "openingBalance", 1000.00));

                mockMvc
                                .perform(
                                                post("/api/v1/bank-accounts")
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(requestBody)
                                                                .header("Authorization", "Bearer " + adminToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void createBankAccount_allowsSameAccountNumberForDifferentCompanies() throws Exception {
                BankAccount company1Account = createBankAccount(testCompany.getId(), "ACC-001", "Company 1 Bank");
                bankAccountRepository.save(company1Account);

                String requestBody = objectMapper.writeValueAsString(
                                java.util.Map.of(
                                                "accountNumber", "ACC-001",
                                                "bankName", "Company 2 Bank",
                                                "type", "BANK",
                                                "openingBalance", 1000.00));

                // Create account for other company
                CompanyContext.setCompanyId(otherCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/bank-accounts")
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(requestBody)
                                                                .header("Authorization", "Bearer " + adminToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(otherCompany.getId())))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.accountNumber").value("ACC-001"));
        }

        @Test
        void updateBankAccount_updatesAccount() throws Exception {
                BankAccount bankAccount = createBankAccount(testCompany.getId(), "ACC-001", "Original Name");
                bankAccount = bankAccountRepository.save(bankAccount);

                String requestBody = objectMapper.writeValueAsString(
                                java.util.Map.of(
                                                "bankName", "Updated Name",
                                                "branch", "Updated Branch"));

                mockMvc
                                .perform(
                                                put("/api/v1/bank-accounts/" + bankAccount.getId())
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(requestBody)
                                                                .header("Authorization", "Bearer " + adminToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.bankName").value("Updated Name"))
                                .andExpect(jsonPath("$.data.branch").value("Updated Branch"))
                                .andExpect(jsonPath("$.data.accountNumber").value("ACC-001")); // Account number
                                                                                               // unchanged
        }

        @Test
        void deleteBankAccount_deletesAccount() throws Exception {
                BankAccount bankAccount = createBankAccount(testCompany.getId(), "ACC-001", "To Delete");
                bankAccount = bankAccountRepository.save(bankAccount);

                mockMvc
                                .perform(
                                                delete("/api/v1/bank-accounts/" + bankAccount.getId())
                                                                .header("Authorization", "Bearer " + adminToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isNoContent());

                // Verify account is deleted
                assertTrue(bankAccountRepository.findByCompanyIdAndId(testCompany.getId(), bankAccount.getId())
                                .isEmpty());
        }

        @Test
        void activateBankAccount_setsActiveToTrue() throws Exception {
                BankAccount bankAccount = createBankAccount(testCompany.getId(), "ACC-001", "Inactive Bank");
                bankAccount.setActive(false);
                bankAccount = bankAccountRepository.save(bankAccount);

                mockMvc
                                .perform(
                                                patch("/api/v1/bank-accounts/" + bankAccount.getId() + "/activate")
                                                                .header("Authorization", "Bearer " + adminToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isNoContent());

                BankAccount updated = bankAccountRepository.findById(bankAccount.getId()).orElseThrow();
                assertTrue(updated.getActive());
        }

        @Test
        void deactivateBankAccount_setsActiveToFalse() throws Exception {
                BankAccount bankAccount = createBankAccount(testCompany.getId(), "ACC-001", "Active Bank");
                bankAccount.setActive(true);
                bankAccount = bankAccountRepository.save(bankAccount);

                mockMvc
                                .perform(
                                                patch("/api/v1/bank-accounts/" + bankAccount.getId() + "/deactivate")
                                                                .header("Authorization", "Bearer " + adminToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isNoContent());

                BankAccount updated = bankAccountRepository.findById(bankAccount.getId()).orElseThrow();
                assertFalse(updated.getActive());
        }

        @Test
        void exportBankAccounts_returnsExcelFile() throws Exception {
                BankAccount account1 = createBankAccount(testCompany.getId(), "ACC-001", "Export Bank 1");
                BankAccount account2 = createBankAccount(testCompany.getId(), "ACC-002", "Export Bank 2");
                bankAccountRepository.save(account1);
                bankAccountRepository.save(account2);

                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts/export")
                                                                .param("format", "xlsx")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(
                                                result -> {
                                                        String contentType = result.getResponse().getContentType();
                                                        assertTrue(contentType != null
                                                                        && contentType.contains("spreadsheetml"));
                                                });
        }

        @Test
        void exportBankAccounts_withFilters_appliesFilters() throws Exception {
                BankAccount cashAccount = createBankAccount(testCompany.getId(), "CASH-001", "Cash Account");
                cashAccount.setType(BankAccount.AccountType.CASH);
                BankAccount bankAccount = createBankAccount(testCompany.getId(), "BANK-001", "Bank Account");
                bankAccount.setType(BankAccount.AccountType.BANK);
                bankAccountRepository.save(cashAccount);
                bankAccountRepository.save(bankAccount);

                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts/export")
                                                                .param("format", "xlsx")
                                                                .param("type", "CASH")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk());
        }

        @Test
        void createBankAccount_forbidden_whenNotAuthorized() throws Exception {
                String requestBody = objectMapper.writeValueAsString(
                                java.util.Map.of(
                                                "accountNumber", "ACC-001",
                                                "bankName", "New Bank",
                                                "type", "BANK",
                                                "openingBalance", 1000.00));

                mockMvc
                                .perform(
                                                post("/api/v1/bank-accounts")
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(requestBody)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isForbidden());
        }

        // ============ Epic 6 New Tests ============

        @Test
        void createBankAccount_withGlAccountCode_createsAccount() throws Exception {
                String requestBody = objectMapper.writeValueAsString(
                                java.util.Map.of(
                                                "accountNumber", "ACC-001",
                                                "bankName", "New Bank",
                                                "type", "BANK",
                                                "openingBalance", 1000.00,
                                                "glAccountCode", "1121",
                                                "active", true));

                mockMvc
                                .perform(
                                                post("/api/v1/bank-accounts")
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(requestBody)
                                                                .header("Authorization", "Bearer " + adminToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.glAccountCode").value("1121"));
        }

        @Test
        void updateBankAccount_withGlAccountCode_updatesGlCode() throws Exception {
                BankAccount bankAccount = createBankAccount(testCompany.getId(), "ACC-001", "Original Name");
                bankAccount = bankAccountRepository.save(bankAccount);

                String requestBody = objectMapper.writeValueAsString(
                                java.util.Map.of(
                                                "bankName", "Updated Name",
                                                "glAccountCode", "1122"));

                mockMvc
                                .perform(
                                                put("/api/v1/bank-accounts/" + bankAccount.getId())
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(requestBody)
                                                                .header("Authorization", "Bearer " + adminToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.glAccountCode").value("1122"));
        }

        @Test
        void getImportTemplate_returnsCSVTemplate() throws Exception {
                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts/import/template")
                                                                .header("Authorization", "Bearer " + adminToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Content-Disposition",
                                                "attachment; filename=\"bank_accounts_import_template.csv\""))
                                .andExpect(result -> {
                                        String content = result.getResponse().getContentAsString();
                                        assertTrue(content.contains("account_number"));
                                        assertTrue(content.contains("bank_name"));
                                        assertTrue(content.contains("gl_account_code"));
                                });
        }

        @Test
        void importBankAccounts_importsFromCSV() throws Exception {
                String csvContent = "account_number,bank_name,branch,account_type,opening_balance,gl_account_code,active\n"
                                + "IMP-001,Import Bank,Main Branch,BANK,5000,1121,true";

                MockMultipartFile file = new MockMultipartFile(
                                "file", "import.csv", "text/csv", csvContent.getBytes());

                mockMvc
                                .perform(
                                                multipart("/api/v1/bank-accounts/import")
                                                                .file(file)
                                                                .header("Authorization", "Bearer " + adminToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.successCount").value(1));

                // Verify account was created
                assertTrue(bankAccountRepository.existsByCompanyIdAndAccountNumber(testCompany.getId(), "IMP-001",
                                null));
        }

        @Test
        void importBankAccounts_forbidden_whenNotAdminOrChiefAccountant() throws Exception {
                String csvContent = "account_number,bank_name,branch,account_type,opening_balance,gl_account_code,active\n"
                                + "IMP-001,Import Bank,Main Branch,BANK,5000,1121,true";

                MockMultipartFile file = new MockMultipartFile(
                                "file", "import.csv", "text/csv", csvContent.getBytes());

                mockMvc
                                .perform(
                                                multipart("/api/v1/bank-accounts/import")
                                                                .file(file)
                                                                .header("Authorization", "Bearer " + testToken) // accountant
                                                                                                                // token
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isForbidden());
        }

        @Test
        void exportBankAccounts_asCSV_returnsCSVFile() throws Exception {
                BankAccount account = createBankAccount(testCompany.getId(), "ACC-001", "Export Bank");
                account.setGlAccountCode("1121");
                bankAccountRepository.save(account);

                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts/export")
                                                                .param("format", "csv")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(result -> {
                                        String contentType = result.getResponse().getContentType();
                                        assertTrue(contentType != null && contentType.contains("csv"));
                                        String content = result.getResponse().getContentAsString();
                                        assertTrue(content.contains("GL Account Code"));
                                        assertTrue(content.contains("1121"));
                                });
        }

        @Test
        void getBalanceTooltip_returnsNewFields() throws Exception {
                BankAccount bankAccount = createBankAccount(testCompany.getId(), "ACC-001", "Test Bank");
                bankAccount.setGlAccountCode("1121");
                bankAccount.setLastReconciledDate(java.time.LocalDate.of(2025, 1, 15));
                bankAccount.setLastReconciledBalance(BigDecimal.valueOf(5000));
                bankAccount = bankAccountRepository.save(bankAccount);

                mockMvc
                                .perform(
                                                get("/api/v1/bank-accounts/" + bankAccount.getId() + "/balance-tooltip")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id",
                                                                                String.valueOf(testCompany.getId())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.currentBalance").exists())
                                .andExpect(jsonPath("$.data.lastReconciledDate").value("2025-01-15"));
        }

        private BankAccount createBankAccount(Long companyId, String accountNumber, String bankName) {
                BankAccount bankAccount = new BankAccount();
                bankAccount.setCompanyId(companyId);
                bankAccount.setAccountNumber(accountNumber);
                bankAccount.setBankName(bankName);
                bankAccount.setType(BankAccount.AccountType.BANK);
                bankAccount.setOpeningBalance(BigDecimal.valueOf(1000.00));
                bankAccount.setActive(true);
                bankAccount.setCreatedAt(Instant.now());
                bankAccount.setUpdatedAt(Instant.now());
                return bankAccount;
        }
}
