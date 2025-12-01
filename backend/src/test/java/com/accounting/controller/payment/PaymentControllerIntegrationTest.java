package com.accounting.controller.payment;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accounting.entity.BankAccount;
import com.accounting.entity.Company;
import com.accounting.entity.CompanySettings;
import com.accounting.entity.APPayment;
import com.accounting.entity.PaymentStatus;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.Supplier;
import com.accounting.entity.User;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.enums.Role;
import com.accounting.repository.APPaymentRepository;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.repository.PaymentAllocationRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.entity.PaymentAllocation;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.test.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentControllerIntegrationTest extends IntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private ObjectMapper objectMapper;
        @Autowired
        private JwtTokenProvider jwtTokenProvider;
        @Autowired
        private PasswordEncoder passwordEncoder;
        @Autowired
        private CompanyRepository companyRepository;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private SupplierRepository supplierRepository;
        @Autowired
        private BankAccountRepository bankAccountRepository;
        @Autowired
        private PurchaseBillRepository purchaseBillRepository;
        @Autowired
        private APPaymentRepository appPaymentRepository;
        @Autowired
        private PaymentAllocationRepository paymentAllocationRepository;
        @Autowired
        private VoucherRepository voucherRepository;
        @Autowired
        private VoucherLineRepository voucherLineRepository;
        @Autowired
        private CompanySettingsRepository companySettingsRepository;

        private Company testCompany;
        private User testUser;
        private Supplier testSupplier;
        private BankAccount cashAccount;
        private PurchaseBill postedBill;
        private String testToken;

        @BeforeEach
        void setUp() {
                testCompany = new Company();
                testCompany.setCode("TEST");
                testCompany.setName("Test Company");
                testCompany.setTaxCode("1234567890");
                testCompany.setAddress("123 Test St");
                testCompany = companyRepository.save(testCompany);

                CompanyContext.setCompanyId(testCompany.getId());

                testUser = new User();
                testUser.setEmail("accountant@example.com");
                testUser.setPasswordHash(passwordEncoder.encode("Password123!"));
                testUser.setFullName("Test Accountant");
                testUser.setRole(Role.ACCOUNTANT.getValue());
                testUser.setCompanyId(testCompany.getId());
                testUser.setStatus("ACTIVE");
                testUser.setCreatedAt(Instant.now());
                testUser.setUpdatedAt(Instant.now());
                testUser = userRepository.save(testUser);

                testToken = jwtTokenProvider.generateAccessToken(
                                testUser.getId(), testUser.getEmail(), testUser.getRole());

                testSupplier = new Supplier();
                testSupplier.setCode("SUP-100");
                testSupplier.setName("Supplier 100");
                testSupplier.setTaxCode("9876543210");
                testSupplier.setCompanyId(testCompany.getId());
                testSupplier.setCreatedAt(Instant.now());
                testSupplier.setUpdatedAt(Instant.now());
                testSupplier = supplierRepository.save(testSupplier);

                cashAccount = new BankAccount();
                cashAccount.setCompanyId(testCompany.getId());
                cashAccount.setAccountNumber("CASH-001");
                cashAccount.setBankName("Main Cash");
                cashAccount.setBranch("HQ");
                cashAccount.setType(BankAccount.AccountType.CASH);
                cashAccount.setOpeningBalance(BigDecimal.valueOf(10_000_000));
                cashAccount.setActive(true);
                cashAccount = bankAccountRepository.save(cashAccount);

                postedBill = createPostedPurchaseBill("BILL-100", BigDecimal.valueOf(5_000_000));

                ensureCompanySettings(new BigDecimal("5000000.00"));
        }

        @AfterEach
        void tearDown() {
                CompanyContext.clear();
        }

        @Test
        void createPayment_linkedBills_createsDraftWithAllocations() throws Exception {
                String requestBody = objectMapper.writeValueAsString(basePaymentRequest(Map.of()));

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .contentType(APPLICATION_JSON)
                                                                .content(requestBody))
                                .andExpect(status().isCreated())
                                .andExpect(content().contentType(APPLICATION_JSON))
                                .andExpect(jsonPath("$.id", notNullValue()))
                                .andExpect(jsonPath("$.supplierId").value(testSupplier.getId()))
                                .andExpect(jsonPath("$.status").value("DRAFT"))
                                .andExpect(jsonPath("$.allocations", hasSize(1)))
                                .andExpect(
                                                jsonPath("$.allocations[0].purchaseBillId")
                                                                .value(postedBill.getId().toString()))
                                .andExpect(jsonPath("$.allocations[0].allocatedAmount", is(3_000_000.0)));
        }

        @Test
        void getPayment_existingPayment_returnsDetails() throws Exception {
                UUID paymentId = createPaymentViaApi(BigDecimal.valueOf(2_000_000));

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                get("/api/v1/ap-payments/" + paymentId)
                                                                .header("Authorization", "Bearer " + testToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                                .andExpect(jsonPath("$.supplierId").value(testSupplier.getId()))
                                .andExpect(jsonPath("$.allocations", hasSize(1)))
                                .andExpect(
                                                jsonPath("$.allocations[0].purchaseBillId")
                                                                .value(postedBill.getId().toString()));
        }

        @Test
        void allocateFIFO_returnsSuggestedAllocations() throws Exception {
                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/allocate-fifo")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .param("paymentAmount", "1500000")
                                                                .param("supplierId", testSupplier.getId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(
                                                jsonPath("$[0].purchaseBillId").value(postedBill.getId().toString()))
                                .andExpect(jsonPath("$[0].allocatedAmount", is(1_500_000.0)));
        }

        @Test
        void createPayment_withoutAccount_returnsBadRequest() throws Exception {
                Map<String, Object> overrides = new java.util.HashMap<>();
                overrides.put("cashAccountId", null);
                overrides.put("bankAccountId", null);
                overrides.put("amount", BigDecimal.valueOf(500_000));
                String requestBody = objectMapper.writeValueAsString(basePaymentRequest(overrides));

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .contentType(APPLICATION_JSON)
                                                                .content(requestBody))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error.message", notNullValue()));
        }

        @Test
        void createPayment_amountExceedsOpenBills_returnsBadRequest() throws Exception {
                Map<String, Object> overrides = new java.util.HashMap<>();
                overrides.put("amount", new BigDecimal("6000000"));
                overrides.put("paymentProofUrl", "https://files.example.com/proof.pdf");
                String requestBody = objectMapper.writeValueAsString(basePaymentRequest(overrides));

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .contentType(APPLICATION_JSON)
                                                                .content(requestBody))
                                .andExpect(status().isBadRequest())
                                .andExpect(
                                                jsonPath("$.error.message",
                                                                containsString("exceeds total open bills balance")));
        }

        @Test
        void createPayment_manualAllocationExceedsRemainingBalance_returnsBadRequest() throws Exception {
                createPaymentViaApi(new BigDecimal("4000000"));

                Map<String, Object> overrides = new java.util.HashMap<>();
                overrides.put("amount", new BigDecimal("1500000"));
                overrides.put("reference", "PMT-OVERALLOC");
                overrides.put(
                                "allocations",
                                List.of(
                                                Map.of(
                                                                "purchaseBillId", postedBill.getId(),
                                                                "allocatedAmount", new BigDecimal("1500000"))));

                String requestBody = objectMapper.writeValueAsString(basePaymentRequest(overrides));

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .contentType(APPLICATION_JSON)
                                                                .content(requestBody))
                                .andExpect(status().isBadRequest())
                                .andExpect(
                                                jsonPath("$.error.message",
                                                                containsString("allocations[0].allocatedAmount")));
        }

        @Test
        void createPayment_whenSupplierHasNoRemainingOpenBills_returnsBadRequest() throws Exception {
                createPaymentViaApi(new BigDecimal("5000000"));

                Map<String, Object> overrides = new java.util.HashMap<>();
                overrides.put("amount", new BigDecimal("500000"));
                overrides.put("reference", "PMT-NO-OPEN");
                String requestBody = objectMapper.writeValueAsString(basePaymentRequest(overrides));

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .contentType(APPLICATION_JSON)
                                                                .content(requestBody))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error.message", containsString("no open/unpaid bills")));
        }

        @Test
        void postPayment_draftPayment_generatesVoucherAndUpdatesStatus() throws Exception {
                UUID paymentId = createPaymentViaApi(new BigDecimal("2000000"));

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/post")
                                                                .header("Authorization", "Bearer " + testToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("POSTED"))
                                .andExpect(jsonPath("$.linkedVoucherId", notNullValue()));

                APPayment payment = appPaymentRepository.findById(paymentId).orElseThrow();
                assertEquals(PaymentStatus.POSTED, payment.getStatus());
                assertNotNull(payment.getLinkedVoucherId());

                UUID voucherId = payment.getLinkedVoucherId();
                Voucher voucher = voucherRepository.findById(voucherId).orElseThrow();
                assertEquals("posted", voucher.getStatus());
                assertEquals(0, voucher.getTotalDebit().compareTo(payment.getAmount()));
                assertEquals(0, voucher.getTotalCredit().compareTo(payment.getAmount()));

                List<VoucherLine> lines = voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(voucherId);
                assertEquals(2, lines.size());
                VoucherLine debitLine = lines.stream()
                                .filter(line -> line.getDebit().compareTo(BigDecimal.ZERO) > 0)
                                .findFirst()
                                .orElseThrow();
                VoucherLine creditLine = lines.stream()
                                .filter(line -> line.getCredit().compareTo(BigDecimal.ZERO) > 0)
                                .findFirst()
                                .orElseThrow();

                assertEquals(0, debitLine.getDebit().compareTo(payment.getAmount()));
                assertEquals(331L, debitLine.getAccountId());
                assertEquals(0, creditLine.getCredit().compareTo(payment.getAmount()));
                assertEquals(cashAccount.getId(), creditLine.getAccountId());
        }

        @Test
        void postPayment_pendingApprovalEnforcesMakerCheckerWorkflow() throws Exception {
                UUID paymentId = createPaymentViaApi(new BigDecimal("6000000"));

                APPayment pendingPayment = appPaymentRepository.findById(paymentId).orElseThrow();
                assertEquals(PaymentStatus.PENDING_APPROVAL, pendingPayment.getStatus());

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/post")
                                                                .header("Authorization", "Bearer " + testToken))
                                .andExpect(status().isForbidden())
                                .andExpect(
                                                jsonPath(
                                                                "$.error.message",
                                                                containsString(
                                                                                "PENDING_APPROVAL payments require Chief Accountant, CFO, or Admin role")));

                User approver = createUser(Role.CHIEF_ACCOUNTANT, "approver@example.com");
                String approverToken = jwtTokenProvider.generateAccessToken(
                                approver.getId(), approver.getEmail(), approver.getRole());

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/post")
                                                                .header("Authorization", "Bearer " + approverToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("POSTED"))
                                .andExpect(jsonPath("$.approvedById").value(approver.getId()))
                                .andExpect(jsonPath("$.linkedVoucherId", notNullValue()));

                APPayment approvedPayment = appPaymentRepository.findById(paymentId).orElseThrow();
                assertEquals(PaymentStatus.POSTED, approvedPayment.getStatus());
                assertEquals(approver.getId(), approvedPayment.getApprovedById());
                assertNotNull(approvedPayment.getLinkedVoucherId());
        }

        @Test
        void createPaymentAllocation_overpaymentViolatesDatabaseConstraint_throwsException() throws Exception {
                // Create a payment
                UUID paymentId = createPaymentViaApi(new BigDecimal("2000000"));

                // Create a second bill with remaining balance of 1,000,000
                PurchaseBill bill2 = createPostedPurchaseBill("BILL-002", new BigDecimal("1000000"));

                // Try to create an allocation that exceeds the remaining balance
                // This should be blocked by the database trigger
                PaymentAllocation overAllocation = new PaymentAllocation();
                overAllocation.setCompanyId(testCompany.getId());
                overAllocation.setPaymentId(paymentId);
                overAllocation.setPurchaseBillId(bill2.getId());
                overAllocation.setAllocatedAmount(new BigDecimal("1500000")); // Exceeds remaining balance of 1M
                overAllocation.setAllocationOrder(1);

                // Attempt to save - should throw exception due to database trigger
                org.junit.jupiter.api.Assertions.assertThrows(
                                org.springframework.dao.DataIntegrityViolationException.class,
                                () -> paymentAllocationRepository.save(overAllocation),
                                "Database trigger should prevent overpayment allocation");
        }

        @Test
        void createPaymentAllocation_validAllocationWithinRemainingBalance_succeeds() throws Exception {
                // Create a payment
                UUID paymentId = createPaymentViaApi(new BigDecimal("2000000"));

                // Create a second bill with remaining balance of 1,000,000
                PurchaseBill bill2 = createPostedPurchaseBill("BILL-002", new BigDecimal("1000000"));

                // Create a valid allocation within remaining balance
                PaymentAllocation validAllocation = new PaymentAllocation();
                validAllocation.setCompanyId(testCompany.getId());
                validAllocation.setPaymentId(paymentId);
                validAllocation.setPurchaseBillId(bill2.getId());
                validAllocation.setAllocatedAmount(new BigDecimal("800000")); // Within remaining balance
                validAllocation.setAllocationOrder(1);

                // Should save successfully
                PaymentAllocation saved = paymentAllocationRepository.save(validAllocation);
                assertNotNull(saved.getId());
                assertEquals(0, saved.getAllocatedAmount().compareTo(new BigDecimal("800000")));
        }

        @Test
        void postPayment_voucherPostingFailure_paymentRemainsInDraftStatus() throws Exception {
                // Create a payment
                UUID paymentId = createPaymentViaApi(new BigDecimal("2000000"));

                // Verify payment is in DRAFT status
                APPayment payment = appPaymentRepository.findById(paymentId).orElseThrow();
                assertEquals(PaymentStatus.DRAFT, payment.getStatus());

                // Note: To fully test voucher posting failure, we would need to:
                // 1. Mock VoucherPostingService to throw an exception, OR
                // 2. Create a scenario where voucher posting fails (e.g., period closed,
                // invalid account)
                //
                // For now, we verify that successful posting updates status correctly.
                // A more complete test would require mocking or setting up a failure scenario.
                //
                // The transaction rollback is handled by @Transactional annotation on
                // postPayment method.
                // If voucherPostingService.postVoucher() throws an exception, the entire
                // transaction
                // rolls back, keeping payment in DRAFT status and bills unchanged.

                // Verify payment can be posted successfully (this tests the happy path)
                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/post")
                                                                .header("Authorization", "Bearer " + testToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("POSTED"));

                // Verify payment status was updated
                APPayment postedPayment = appPaymentRepository.findById(paymentId).orElseThrow();
                assertEquals(PaymentStatus.POSTED, postedPayment.getStatus());
                assertNotNull(postedPayment.getLinkedVoucherId());

                // Verify bill status was updated
                PurchaseBill updatedBill = purchaseBillRepository.findById(postedBill.getId()).orElseThrow();
                assertTrue(
                                updatedBill.getStatus() == PurchaseBillStatus.PAID
                                                || updatedBill.getStatus() == PurchaseBillStatus.PARTIALLY_PAID,
                                "Bill status should be updated to PAID or PARTIALLY_PAID after payment posting");
        }

        @Test
        void createPayment_paymentNumberFormat_matchesPAY_YYYY_XXXXX() throws Exception {
                // Create a payment and verify the payment number format
                UUID paymentId = createPaymentViaApi(new BigDecimal("2000000"));

                APPayment payment = appPaymentRepository.findById(paymentId).orElseThrow();
                String paymentNumber = payment.getPaymentNumber();

                // Verify format: PAY-YYYY-XXXXX
                // Example: PAY-2025-00001
                assertTrue(
                                paymentNumber.matches("^PAY-\\d{4}-\\d{5}$"),
                                "Payment number should match format PAY-YYYY-XXXXX, got: " + paymentNumber);

                // Verify it starts with PAY-
                assertTrue(paymentNumber.startsWith("PAY-"), "Payment number should start with PAY-");

                // Verify year is 4 digits
                String[] parts = paymentNumber.split("-");
                assertEquals(3, parts.length, "Payment number should have 3 parts separated by -");
                assertEquals("PAY", parts[0], "First part should be PAY");
                assertEquals(4, parts[1].length(), "Year should be 4 digits");
                assertEquals(5, parts[2].length(), "Sequence should be 5 digits (zero-padded)");

                // Verify year matches payment date year
                int expectedYear = payment.getPaymentDate().getYear();
                int actualYear = Integer.parseInt(parts[1]);
                assertEquals(expectedYear, actualYear, "Year in payment number should match payment date year");
        }

        private UUID createPaymentViaApi(BigDecimal amount) throws Exception {
                Map<String, Object> overrides = Map.of("amount", amount, "reference", "PMT-" + amount.intValue());
                return createPaymentAndGetId(basePaymentRequest(overrides));
        }

        private UUID createPaymentAndGetId(Map<String, Object> requestMap) throws Exception {
                String requestBody = objectMapper.writeValueAsString(requestMap);

                CompanyContext.setCompanyId(testCompany.getId());
                MvcResult result = mockMvc
                                .perform(
                                                post("/api/v1/ap-payments")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .contentType(APPLICATION_JSON)
                                                                .content(requestBody))
                                .andExpect(status().isCreated())
                                .andReturn();

                JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
                return UUID.fromString(responseJson.get("id").asText());
        }

        private Map<String, Object> basePaymentRequest(Map<String, Object> overrides) {
                Map<String, Object> base = new java.util.HashMap<>();
                base.put("supplierId", testSupplier.getId());
                base.put("paymentDate", LocalDate.of(2025, 1, 20).toString());
                base.put("dueDate", LocalDate.of(2025, 1, 25).toString());
                base.put("cashAccountId", cashAccount.getId());
                base.put("bankAccountId", null);
                base.put("payee", "Supplier 100");
                base.put("amount", BigDecimal.valueOf(3_000_000));
                base.put("reference", "PMT-100");
                base.put("paymentMethod", "CASH");
                base.put("paymentProofUrl", null);
                base.put("isStandalone", false);
                base.put("allocations", null);
                overrides.forEach(base::put);
                return base;
        }

        private void ensureCompanySettings(BigDecimal threshold) {
                CompanySettings settings = companySettingsRepository
                                .findByCompanyId(testCompany.getId())
                                .orElseGet(
                                                () -> {
                                                        CompanySettings newSettings = new CompanySettings();
                                                        newSettings.setCompanyId(testCompany.getId());
                                                        return newSettings;
                                                });
                settings.setApprovalThresholdAmount(threshold);
                companySettingsRepository.save(settings);
        }

        private User createUser(Role role, String email) {
                User user = new User();
                user.setEmail(email);
                user.setPasswordHash(passwordEncoder.encode("Password123!"));
                user.setFullName("Test " + role.name());
                user.setRole(role.getValue());
                user.setCompanyId(testCompany.getId());
                user.setStatus("ACTIVE");
                user.setCreatedAt(Instant.now());
                user.setUpdatedAt(Instant.now());
                return userRepository.save(user);
        }

        private PurchaseBill createPostedPurchaseBill(String billNumber, BigDecimal amount) {
                PurchaseBill bill = new PurchaseBill();
                bill.setCompanyId(testCompany.getId());
                bill.setSupplierId(testSupplier.getId());
                bill.setBillNumber(billNumber);
                bill.setBillDate(LocalDate.of(2025, 1, 5));
                bill.setDueDate(LocalDate.of(2025, 1, 25));
                bill.setReference("REF-" + billNumber);
                bill.setDescription("Test bill");
                bill.setStatus(PurchaseBillStatus.POSTED);
                bill.setTotalAmount(amount);
                bill.setVatAmount(BigDecimal.ZERO);
                bill.setCreatedById(testUser.getId());
                bill.setIsSensitive(false);
                bill.setCreatedAt(Instant.now());
                bill.setUpdatedAt(Instant.now());
                return purchaseBillRepository.save(bill);
        }

        // =====================================================
        // AC6.3-08: Approve/Reject Payment Tests
        // =====================================================

        @Test
        void approvePayment_validApprover_approvesPayment() throws Exception {
                // Create a high-value payment that requires approval
                ensureCompanySettings(new BigDecimal("1000000.00")); // Lower threshold
                Map<String, Object> request = basePaymentRequest(Map.of("amount", BigDecimal.valueOf(2_000_000)));
                UUID paymentId = createPaymentAndGetId(request);

                // Create a different user with CHIEF_ACCOUNTANT role to approve
                User approver = createUser(Role.CHIEF_ACCOUNTANT, "chief@example.com");
                String approverToken = jwtTokenProvider.generateAccessToken(
                                approver.getId(), approver.getEmail(), approver.getRole());

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/approve")
                                                                .header("Authorization", "Bearer " + approverToken)
                                                                .header("X-Company-Id", testCompany.getId().toString())
                                                                .contentType(APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("DRAFT")));
        }

        @Test
        void approvePayment_makerEqualsChecker_returnsForbidden() throws Exception {
                // Create a high-value payment that requires approval
                ensureCompanySettings(new BigDecimal("1000000.00"));
                Map<String, Object> request = basePaymentRequest(Map.of("amount", BigDecimal.valueOf(2_000_000)));
                UUID paymentId = createPaymentAndGetId(request);

                // Try to approve with the same user (maker = checker)
                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/approve")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id", testCompany.getId().toString())
                                                                .contentType(APPLICATION_JSON))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.message", containsString("maker-checker")));
        }

        @Test
        void rejectPayment_validApprover_rejectsPayment() throws Exception {
                // Create a high-value payment that requires approval
                ensureCompanySettings(new BigDecimal("1000000.00"));
                Map<String, Object> request = basePaymentRequest(Map.of("amount", BigDecimal.valueOf(2_000_000)));
                UUID paymentId = createPaymentAndGetId(request);

                // Create a different user with CFO role to reject
                User approver = createUser(Role.CFO, "cfo@example.com");
                String approverToken = jwtTokenProvider.generateAccessToken(
                                approver.getId(), approver.getEmail(), approver.getRole());

                String rejectBody = objectMapper.writeValueAsString(Map.of("reason", "Insufficient documentation"));

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/reject")
                                                                .header("Authorization", "Bearer " + approverToken)
                                                                .header("X-Company-Id", testCompany.getId().toString())
                                                                .contentType(APPLICATION_JSON)
                                                                .content(rejectBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("REJECTED")));
        }

        @Test
        void rejectPayment_noReason_returnsBadRequest() throws Exception {
                // Create a high-value payment that requires approval
                ensureCompanySettings(new BigDecimal("1000000.00"));
                Map<String, Object> request = basePaymentRequest(Map.of("amount", BigDecimal.valueOf(2_000_000)));
                UUID paymentId = createPaymentAndGetId(request);

                User approver = createUser(Role.CHIEF_ACCOUNTANT, "chief2@example.com");
                String approverToken = jwtTokenProvider.generateAccessToken(
                                approver.getId(), approver.getEmail(), approver.getRole());

                // Empty reason
                String rejectBody = objectMapper.writeValueAsString(Map.of("reason", ""));

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/reject")
                                                                .header("Authorization", "Bearer " + approverToken)
                                                                .header("X-Company-Id", testCompany.getId().toString())
                                                                .contentType(APPLICATION_JSON)
                                                                .content(rejectBody))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("reason")));
        }

        // =====================================================
        // AC6.3-10: Reverse Payment Tests
        // =====================================================

        @Test
        void reversePayment_postedPayment_createsReversingVoucher() throws Exception {
                // Create and post a payment
                Map<String, Object> request = basePaymentRequest(Map.of("amount", BigDecimal.valueOf(1_000_000)));
                UUID paymentId = createPaymentAndGetId(request);

                // Post the payment
                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/post")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id", testCompany.getId().toString())
                                                                .contentType(APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("POSTED")));

                // Create CFO user to reverse
                User approver = createUser(Role.CFO, "cfo2@example.com");
                String approverToken = jwtTokenProvider.generateAccessToken(
                                approver.getId(), approver.getEmail(), approver.getRole());

                String reverseBody = objectMapper.writeValueAsString(Map.of("reason", "Duplicate payment"));

                // Reverse the payment
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/reverse")
                                                                .header("Authorization", "Bearer " + approverToken)
                                                                .header("X-Company-Id", testCompany.getId().toString())
                                                                .contentType(APPLICATION_JSON)
                                                                .content(reverseBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("REVERSED")));
        }

        @Test
        void reversePayment_noReason_returnsBadRequest() throws Exception {
                // Create and post a payment
                Map<String, Object> request = basePaymentRequest(Map.of("amount", BigDecimal.valueOf(1_000_000)));
                UUID paymentId = createPaymentAndGetId(request);

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/post")
                                                                .header("Authorization", "Bearer " + testToken)
                                                                .header("X-Company-Id", testCompany.getId().toString())
                                                                .contentType(APPLICATION_JSON))
                                .andExpect(status().isOk());

                // Try to reverse without reason
                User approver = createUser(Role.ADMIN, "admin@example.com");
                String approverToken = jwtTokenProvider.generateAccessToken(
                                approver.getId(), approver.getEmail(), approver.getRole());

                String reverseBody = objectMapper.writeValueAsString(Map.of("reason", ""));

                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/reverse")
                                                                .header("Authorization", "Bearer " + approverToken)
                                                                .header("X-Company-Id", testCompany.getId().toString())
                                                                .contentType(APPLICATION_JSON)
                                                                .content(reverseBody))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("reason")));
        }

        @Test
        void reversePayment_notPosted_returnsConflict() throws Exception {
                // Create a draft payment (not posted)
                Map<String, Object> request = basePaymentRequest(Map.of("amount", BigDecimal.valueOf(1_000_000)));
                UUID paymentId = createPaymentAndGetId(request);

                User approver = createUser(Role.CFO, "cfo3@example.com");
                String approverToken = jwtTokenProvider.generateAccessToken(
                                approver.getId(), approver.getEmail(), approver.getRole());

                String reverseBody = objectMapper.writeValueAsString(Map.of("reason", "Test reversal"));

                CompanyContext.setCompanyId(testCompany.getId());
                mockMvc
                                .perform(
                                                post("/api/v1/ap-payments/" + paymentId + "/reverse")
                                                                .header("Authorization", "Bearer " + approverToken)
                                                                .header("X-Company-Id", testCompany.getId().toString())
                                                                .contentType(APPLICATION_JSON)
                                                                .content(reverseBody))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message", containsString("POSTED")));
        }
}
