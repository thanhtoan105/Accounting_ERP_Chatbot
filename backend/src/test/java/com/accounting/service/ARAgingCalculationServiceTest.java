package com.accounting.service;

import com.accounting.dto.ARAgingBucketDTO;
import com.accounting.entity.Customer;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.impl.ARAgingCalculationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ARAgingCalculationService.
 * Tests aging bucket calculation logic with various date scenarios.
 */
@ExtendWith(MockitoExtension.class)
public class ARAgingCalculationServiceTest {

    @Mock
    private SalesInvoiceRepository salesInvoiceRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private ARAgingCalculationServiceImpl agingCalculationService;

    private static final Long COMPANY_ID = 1L;
    private static final Long CUSTOMER_ID = 100L;
    private LocalDate asOfDate;

    @BeforeEach
    void setUp() {
        CompanyContext.setCompanyId(COMPANY_ID);
        asOfDate = LocalDate.of(2025, 11, 22);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void testCalculateAgingBuckets_CurrentBucket() {
        // Invoice due today (0 days overdue) → Current bucket
        SalesInvoice invoice = createInvoice(asOfDate, new BigDecimal("1000.00"));

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, CUSTOMER_ID))
                .thenReturn(List.of(invoice));

        ARAgingBucketDTO result = agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, asOfDate);

        assertThat(result.getCurrent()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getDays1To30()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDays31To60()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDays61To90()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDaysOver90()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void testCalculateAgingBuckets_FutureDated() {
        // Invoice due in future → Current bucket
        SalesInvoice invoice = createInvoice(asOfDate.plusDays(10), new BigDecimal("2000.00"));

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, CUSTOMER_ID))
                .thenReturn(List.of(invoice));

        ARAgingBucketDTO result = agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, asOfDate);

        assertThat(result.getCurrent()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    void testCalculateAgingBuckets_1To30DaysOverdue() {
        // Invoice due 15 days ago → 1-30d bucket
        SalesInvoice invoice = createInvoice(asOfDate.minusDays(15), new BigDecimal("3000.00"));

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, CUSTOMER_ID))
                .thenReturn(List.of(invoice));

        ARAgingBucketDTO result = agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, asOfDate);

        assertThat(result.getCurrent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDays1To30()).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("3000.00"));
    }

    @Test
    void testCalculateAgingBuckets_31To60DaysOverdue() {
        // Invoice due 45 days ago → 31-60d bucket
        SalesInvoice invoice = createInvoice(asOfDate.minusDays(45), new BigDecimal("4000.00"));

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, CUSTOMER_ID))
                .thenReturn(List.of(invoice));

        ARAgingBucketDTO result = agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, asOfDate);

        assertThat(result.getDays31To60()).isEqualByComparingTo(new BigDecimal("4000.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("4000.00"));
    }

    @Test
    void testCalculateAgingBuckets_61To90DaysOverdue() {
        // Invoice due 75 days ago → 61-90d bucket
        SalesInvoice invoice = createInvoice(asOfDate.minusDays(75), new BigDecimal("5000.00"));

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, CUSTOMER_ID))
                .thenReturn(List.of(invoice));

        ARAgingBucketDTO result = agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, asOfDate);

        assertThat(result.getDays61To90()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    void testCalculateAgingBuckets_Over90DaysOverdue() {
        // Invoice due 120 days ago → 90+d bucket
        SalesInvoice invoice = createInvoice(asOfDate.minusDays(120), new BigDecimal("6000.00"));

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, CUSTOMER_ID))
                .thenReturn(List.of(invoice));

        ARAgingBucketDTO result = agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, asOfDate);

        assertThat(result.getDaysOver90()).isEqualByComparingTo(new BigDecimal("6000.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("6000.00"));
    }

    @Test
    void testCalculateAgingBuckets_MultipleBuckets() {
        // Multiple invoices across different buckets
        List<SalesInvoice> invoices = Arrays.asList(
                createInvoice(asOfDate, new BigDecimal("1000.00")), // Current
                createInvoice(asOfDate.minusDays(15), new BigDecimal("2000.00")), // 1-30d
                createInvoice(asOfDate.minusDays(45), new BigDecimal("3000.00")), // 31-60d
                createInvoice(asOfDate.minusDays(75), new BigDecimal("4000.00")), // 61-90d
                createInvoice(asOfDate.minusDays(120), new BigDecimal("5000.00")) // 90+d
        );

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, CUSTOMER_ID))
                .thenReturn(invoices);

        ARAgingBucketDTO result = agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, asOfDate);

        assertThat(result.getCurrent()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getDays1To30()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(result.getDays31To60()).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(result.getDays61To90()).isEqualByComparingTo(new BigDecimal("4000.00"));
        assertThat(result.getDaysOver90()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("15000.00"));
    }

    @Test
    void testCalculateAgingBuckets_BoundaryConditions() {
        // Test exact boundary dates
        List<SalesInvoice> invoices = Arrays.asList(
                createInvoice(asOfDate.minusDays(30), new BigDecimal("1000.00")), // Exactly 30 days
                createInvoice(asOfDate.minusDays(60), new BigDecimal("2000.00")), // Exactly 60 days
                createInvoice(asOfDate.minusDays(90), new BigDecimal("3000.00")) // Exactly 90 days
        );

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, CUSTOMER_ID))
                .thenReturn(invoices);

        ARAgingBucketDTO result = agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, asOfDate);

        assertThat(result.getDays1To30()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getDays31To60()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(result.getDays61To90()).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("6000.00"));
    }

    @Test
    void testCalculateAgingBuckets_ExcludeZeroBalance() {
        // Invoice with zero remaining balance should be excluded
        SalesInvoice invoiceWithBalance = createInvoice(asOfDate.minusDays(15), new BigDecimal("1000.00"));
        SalesInvoice invoiceZeroBalance = createInvoice(asOfDate.minusDays(20), BigDecimal.ZERO);

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, CUSTOMER_ID))
                .thenReturn(Arrays.asList(invoiceWithBalance, invoiceZeroBalance));

        ARAgingBucketDTO result = agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, asOfDate);

        assertThat(result.getDays1To30()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void testCalculateAgingBuckets_NoInvoices() {
        // No open invoices
        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, CUSTOMER_ID))
                .thenReturn(List.of());

        ARAgingBucketDTO result = agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, asOfDate);

        assertThat(result.getCurrent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDays1To30()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDays31To60()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDays61To90()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDaysOver90()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void testCalculateAgingBuckets_NullAsOfDate() {
        // Should default to today
        SalesInvoice invoice = createInvoice(LocalDate.now().minusDays(15), new BigDecimal("1000.00"));

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(eq(COMPANY_ID), eq(CUSTOMER_ID)))
                .thenReturn(List.of(invoice));

        ARAgingBucketDTO result = agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, null);

        assertThat(result.getDays1To30()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void testCalculateAgingForAllCustomers() {
        // Setup customers
        Customer customer1 = createCustomer(100L, "Customer A");
        Customer customer2 = createCustomer(200L, "Customer B");

        when(customerRepository.findByCompanyId(COMPANY_ID))
                .thenReturn(Arrays.asList(customer1, customer2));

        // Setup invoices for customer 1
        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, 100L))
                .thenReturn(List.of(createInvoice(asOfDate.minusDays(15), new BigDecimal("1000.00"))));

        // Setup invoices for customer 2
        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, 200L))
                .thenReturn(List.of(createInvoice(asOfDate.minusDays(45), new BigDecimal("2000.00"))));

        Map<Long, ARAgingBucketDTO> result = agingCalculationService.calculateAgingForAllCustomers(asOfDate);

        assertThat(result).hasSize(2);
        assertThat(result.get(100L).getDays1To30()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.get(200L).getDays31To60()).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    void testCalculateAgingForAllCustomers_ExcludeZeroBalance() {
        // Customer with zero balance should not be included
        Customer customer1 = createCustomer(100L, "Customer A");
        Customer customer2 = createCustomer(200L, "Customer B");

        when(customerRepository.findByCompanyId(COMPANY_ID))
                .thenReturn(Arrays.asList(customer1, customer2));

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, 100L))
                .thenReturn(List.of(createInvoice(asOfDate.minusDays(15), new BigDecimal("1000.00"))));

        when(salesInvoiceRepository.findOpenInvoicesByCustomerId(COMPANY_ID, 200L))
                .thenReturn(List.of()); // No invoices

        Map<Long, ARAgingBucketDTO> result = agingCalculationService.calculateAgingForAllCustomers(asOfDate);

        assertThat(result).hasSize(1);
        assertThat(result).containsKey(100L);
        assertThat(result).doesNotContainKey(200L);
    }

    @Test
    void testCalculateAgingBuckets_MissingCompanyContext() {
        CompanyContext.clear();

        assertThatThrownBy(() -> agingCalculationService.calculateAgingBuckets(CUSTOMER_ID, asOfDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing company context");
    }

    private SalesInvoice createInvoice(LocalDate dueDate, BigDecimal remainingBalance) {
        SalesInvoice invoice = new SalesInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setCompanyId(COMPANY_ID);
        invoice.setCustomerId(CUSTOMER_ID);
        invoice.setInvoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8));
        invoice.setInvoiceDate(dueDate.minusDays(30));
        invoice.setDueDate(dueDate);
        invoice.setStatus(SalesInvoiceStatus.POSTED);
        invoice.setTotalAmount(remainingBalance);
        invoice.setRemainingBalance(remainingBalance);
        return invoice;
    }

    private Customer createCustomer(Long id, String name) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCompanyId(COMPANY_ID);
        customer.setCode("CUST-" + id);
        customer.setName(name);
        return customer;
    }
}
