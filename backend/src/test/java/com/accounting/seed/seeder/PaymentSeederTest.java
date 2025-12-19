package com.accounting.seed.seeder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import com.accounting.entity.APPayment;
import com.accounting.entity.ARPayment;
import com.accounting.entity.PaymentMethod;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.seed.SeedProperties;
import com.accounting.seed.TenantContext;
import com.accounting.seed.UniqueGenerator;
import com.accounting.seed.VietnameseFaker;

@ExtendWith(MockitoExtension.class)
class PaymentSeederTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SeedProperties seedProperties;

    @Mock
    private VietnameseFaker faker;

    @Mock
    private UniqueGenerator generator;

    private PaymentSeeder paymentSeeder;
    private TenantContext tenantContext;
    private static final Long CASH_ACCOUNT_ID = 111L;
    private static final Long BANK_ACCOUNT_ID = 999L;

    @BeforeEach
    void setUp() {
        paymentSeeder = new PaymentSeeder(jdbcTemplate, seedProperties);

        Map<String, Long> accountsByCode = new HashMap<>();
        accountsByCode.put("111", CASH_ACCOUNT_ID);

        tenantContext = TenantContext.builder()
                .companyId(1L)
                .companyCode("TEST")
                .userIds(List.of(1L, 2L))
                .customerIds(List.of(1L, 2L))
                .supplierIds(List.of(1L, 2L))
                .bankAccountIds(List.of(BANK_ACCOUNT_ID))
                .periodIds(List.of(UUID.randomUUID()))
                .accountsByCode(accountsByCode)
                .accountsByPrefix(new HashMap<>())
                .build();

        when(seedProperties.getStartDate()).thenReturn(java.time.LocalDate.of(2024, 1, 1));
        when(seedProperties.getEndDate()).thenReturn(java.time.LocalDate.of(2024, 12, 31));
        when(faker.companyName()).thenReturn("Test Company");
        when(faker.personName()).thenReturn("Test Person");
        when(generator.nextPaymentNumber(any(), any(), anyString())).thenReturn("PC-001");
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1});
    }

    @Test
    void seedAPPayments_cashPayment_usesCashAccountId() {
        Random fixedRandom = new Random(42) {
            private int callCount = 0;

            @Override
            public int nextInt(int bound) {
                callCount++;
                if (callCount == 7) {
                    return 0;
                }
                return super.nextInt(bound);
            }
        };

        UUID billId = UUID.randomUUID();
        Map<UUID, PurchaseBillSeeder.DocumentInfo> billInfo = new HashMap<>();
        billInfo.put(billId, new PurchaseBillSeeder.DocumentInfo(
                billId, 1L, BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(10_000_000), PurchaseBillStatus.POSTED, LocalDate.of(2024, 1, 15)));

        List<APPayment> payments = paymentSeeder.seedAPPayments(
                tenantContext, billInfo, 10, faker, generator, fixedRandom);

        APPayment cashPayment = payments.stream()
                .filter(p -> p.getPaymentMethod() == PaymentMethod.CASH)
                .findFirst()
                .orElse(null);

        if (cashPayment != null) {
            assertEquals(CASH_ACCOUNT_ID, cashPayment.getCashAccountId(),
                    "Cash payment should use GL account 111");
            assertNull(cashPayment.getBankAccountId(),
                    "Cash payment should not have a bank account ID");
        }

        APPayment bankPayment = payments.stream()
                .filter(p -> p.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                        || p.getPaymentMethod() == PaymentMethod.CHECK)
                .findFirst()
                .orElse(null);

        if (bankPayment != null) {
            assertEquals(BANK_ACCOUNT_ID, bankPayment.getBankAccountId(),
                    "Bank payment should use bank account ID");
            assertNull(bankPayment.getCashAccountId(),
                    "Bank payment should not have a cash account ID");
        }
    }

    @Test
    void seedARPayments_cashPayment_usesCashAccountId() {
        Random fixedRandom = new Random(42) {
            private int callCount = 0;

            @Override
            public int nextInt(int bound) {
                callCount++;
                if (callCount == 7) {
                    return 0;
                }
                return super.nextInt(bound);
            }
        };

        UUID invoiceId = UUID.randomUUID();
        Map<UUID, SalesInvoiceSeeder.DocumentInfo> invoiceInfo = new HashMap<>();
        invoiceInfo.put(invoiceId, new SalesInvoiceSeeder.DocumentInfo(
                invoiceId, 1L, BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(10_000_000), SalesInvoiceStatus.POSTED, LocalDate.of(2024, 1, 15)));

        List<ARPayment> payments = paymentSeeder.seedARPayments(
                tenantContext, invoiceInfo, 10, faker, generator, fixedRandom);

        ARPayment cashPayment = payments.stream()
                .filter(p -> p.getPaymentMethod() == PaymentMethod.CASH)
                .findFirst()
                .orElse(null);

        if (cashPayment != null) {
            assertEquals(CASH_ACCOUNT_ID, cashPayment.getCashAccountId(),
                    "Cash receipt should use GL account 111");
            assertNull(cashPayment.getBankAccountId(),
                    "Cash receipt should not have a bank account ID");
        }

        ARPayment bankPayment = payments.stream()
                .filter(p -> p.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                        || p.getPaymentMethod() == PaymentMethod.CHECK)
                .findFirst()
                .orElse(null);

        if (bankPayment != null) {
            assertEquals(BANK_ACCOUNT_ID, bankPayment.getBankAccountId(),
                    "Bank receipt should use bank account ID");
            assertNull(bankPayment.getCashAccountId(),
                    "Bank receipt should not have a cash account ID");
        }
    }

    @Test
    void seedAPPayments_verifiesAccountMappingLogic() {
        UUID billId = UUID.randomUUID();
        Map<UUID, PurchaseBillSeeder.DocumentInfo> billInfo = new HashMap<>();
        billInfo.put(billId, new PurchaseBillSeeder.DocumentInfo(
                billId, 1L, BigDecimal.valueOf(50_000_000), BigDecimal.valueOf(50_000_000), PurchaseBillStatus.POSTED, LocalDate.of(2024, 1, 15)));

        List<APPayment> payments = paymentSeeder.seedAPPayments(
                tenantContext, billInfo, 1, faker, generator, new Random(123));

        for (APPayment payment : payments) {
            if (payment.getPaymentMethod() == PaymentMethod.CASH) {
                assertEquals(CASH_ACCOUNT_ID, payment.getCashAccountId(),
                        "All cash payments must use account 111");
                assertNull(payment.getBankAccountId(),
                        "Cash payments must not have bank account");
            } else {
                assertNotNull(payment.getBankAccountId(),
                        "Bank/check payments must have bank account");
                assertNull(payment.getCashAccountId(),
                        "Bank/check payments must not have cash account");
            }
        }
    }

    @Test
    void seedARPayments_verifiesAccountMappingLogic() {
        UUID invoiceId = UUID.randomUUID();
        Map<UUID, SalesInvoiceSeeder.DocumentInfo> invoiceInfo = new HashMap<>();
        invoiceInfo.put(invoiceId, new SalesInvoiceSeeder.DocumentInfo(
                invoiceId, 1L, BigDecimal.valueOf(50_000_000), BigDecimal.valueOf(50_000_000), SalesInvoiceStatus.POSTED, LocalDate.of(2024, 1, 15)));

        List<ARPayment> payments = paymentSeeder.seedARPayments(
                tenantContext, invoiceInfo, 1, faker, generator, new Random(123));

        for (ARPayment payment : payments) {
            if (payment.getPaymentMethod() == PaymentMethod.CASH) {
                assertEquals(CASH_ACCOUNT_ID, payment.getCashAccountId(),
                        "All cash receipts must use account 111");
                assertNull(payment.getBankAccountId(),
                        "Cash receipts must not have bank account");
            } else {
                assertNotNull(payment.getBankAccountId(),
                        "Bank/check receipts must have bank account");
                assertNull(payment.getCashAccountId(),
                        "Bank/check receipts must not have cash account");
            }
        }
    }
}
