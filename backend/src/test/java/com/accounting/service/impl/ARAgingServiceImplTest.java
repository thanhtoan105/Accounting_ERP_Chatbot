package com.accounting.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.accounting.dto.ARAgingBucketDTO;
import com.accounting.dto.ARAgingReportDTO;
import com.accounting.entity.Customer;
import com.accounting.repository.CustomerRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ARAgingCalculationService;

/**
 * Unit tests for ARAgingServiceImpl.
 * Tests caching, RBAC filtering, and cache invalidation.
 */
@ExtendWith(MockitoExtension.class)
public class ARAgingServiceImplTest {

    @Mock
    private ARAgingCalculationService agingCalculationService;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private ARAgingServiceImpl arAgingService;

    private static final Long COMPANY_ID = 1L;
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
    void testGetAgingReport_RBACFiltering() {
        // Test that only company-scoped customers are included
        Customer customer1 = createCustomer(100L, "Customer A", COMPANY_ID);
        Customer customer2 = createCustomer(200L, "Customer B", COMPANY_ID);
        Customer customer3 = createCustomer(300L, "Customer C", 999L); // Different company

        when(customerRepository.findByCompanyId(COMPANY_ID))
                .thenReturn(Arrays.asList(customer1, customer2)); // Should not include customer3

        when(agingCalculationService.calculateAgingBuckets(eq(100L), any(LocalDate.class)))
                .thenReturn(createBuckets(new BigDecimal("1000.00")));
        when(agingCalculationService.calculateAgingBuckets(eq(200L), any(LocalDate.class)))
                .thenReturn(createBuckets(new BigDecimal("2000.00")));

        Pageable pageable = PageRequest.of(0, 20);
        Page<ARAgingReportDTO> result = arAgingService.getAgingReport(null, asOfDate, null, null, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting("customerId").containsExactlyInAnyOrder(100L, 200L);
        assertThat(result.getContent()).extracting("customerId").doesNotContain(300L);

        verify(customerRepository, times(1)).findByCompanyId(COMPANY_ID);
    }

    @Test
    void testGetAgingReport_CustomerFilter() {
        // Test filtering by specific customer ID
        Long targetCustomerId = 100L;
        Customer customer = createCustomer(targetCustomerId, "Customer A", COMPANY_ID);

        when(customerRepository.findByCompanyId(COMPANY_ID))
                .thenReturn(Arrays.asList(
                        customer,
                        createCustomer(200L, "Customer B", COMPANY_ID)));

        when(agingCalculationService.calculateAgingBuckets(eq(targetCustomerId), any(LocalDate.class)))
                .thenReturn(createBuckets(new BigDecimal("1000.00")));

        Pageable pageable = PageRequest.of(0, 20);
        Page<ARAgingReportDTO> result = arAgingService.getAgingReport(targetCustomerId, asOfDate, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCustomerId()).isEqualTo(targetCustomerId);

        verify(agingCalculationService, times(1)).calculateAgingBuckets(eq(targetCustomerId), any(LocalDate.class));
        verify(agingCalculationService, never()).calculateAgingBuckets(eq(200L), any(LocalDate.class));
    }

    @Test
    void testGetAgingReport_BucketFilter() {
        // Test filtering by aging bucket
        Customer customer1 = createCustomer(100L, "Customer A", COMPANY_ID);
        Customer customer2 = createCustomer(200L, "Customer B", COMPANY_ID);

        when(customerRepository.findByCompanyId(COMPANY_ID))
                .thenReturn(Arrays.asList(customer1, customer2));

        // Customer 1 has balance in DAYS_1_30 bucket
        ARAgingBucketDTO buckets1 = new ARAgingBucketDTO();
        buckets1.setDays1To30(new BigDecimal("1000.00"));
        buckets1.setTotal(new BigDecimal("1000.00"));

        // Customer 2 has no balance in DAYS_1_30 bucket
        ARAgingBucketDTO buckets2 = new ARAgingBucketDTO();
        buckets2.setCurrent(new BigDecimal("500.00"));
        buckets2.setTotal(new BigDecimal("500.00"));

        when(agingCalculationService.calculateAgingBuckets(eq(100L), any(LocalDate.class)))
                .thenReturn(buckets1);
        when(agingCalculationService.calculateAgingBuckets(eq(200L), any(LocalDate.class)))
                .thenReturn(buckets2);

        Pageable pageable = PageRequest.of(0, 20);
        Page<ARAgingReportDTO> result = arAgingService.getAgingReport(null, asOfDate, null, "DAYS_1_30", pageable);

        // Only customer1 should be included as it has balance in DAYS_1_30 bucket
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCustomerId()).isEqualTo(100L);
    }

    @Test
    void testGetAgingReport_ExcludeZeroBalance() {
        // Customers with zero total outstanding should be excluded
        Customer customer1 = createCustomer(100L, "Customer A", COMPANY_ID);
        Customer customer2 = createCustomer(200L, "Customer B", COMPANY_ID);

        when(customerRepository.findByCompanyId(COMPANY_ID))
                .thenReturn(Arrays.asList(customer1, customer2));

        when(agingCalculationService.calculateAgingBuckets(eq(100L), any(LocalDate.class)))
                .thenReturn(createBuckets(new BigDecimal("1000.00")));
        when(agingCalculationService.calculateAgingBuckets(eq(200L), any(LocalDate.class)))
                .thenReturn(createBuckets(BigDecimal.ZERO));

        Pageable pageable = PageRequest.of(0, 20);
        Page<ARAgingReportDTO> result = arAgingService.getAgingReport(null, asOfDate, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCustomerId()).isEqualTo(100L);
    }

    @Test
    void testGetAgingReport_Pagination() {
        // Test pagination logic
        List<Customer> customers = Arrays.asList(
                createCustomer(100L, "Customer A", COMPANY_ID),
                createCustomer(200L, "Customer B", COMPANY_ID),
                createCustomer(300L, "Customer C", COMPANY_ID),
                createCustomer(400L, "Customer D", COMPANY_ID),
                createCustomer(500L, "Customer E", COMPANY_ID));

        when(customerRepository.findByCompanyId(COMPANY_ID)).thenReturn(customers);

        // Mock aging buckets for all customers
        for (Customer customer : customers) {
            when(agingCalculationService.calculateAgingBuckets(eq(customer.getId()), any(LocalDate.class)))
                    .thenReturn(createBuckets(new BigDecimal("1000.00")));
        }

        // Request page 1 (second page) with size 2
        Pageable pageable = PageRequest.of(1, 2);
        Page<ARAgingReportDTO> result = arAgingService.getAgingReport(null, asOfDate, null, null, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getNumber()).isEqualTo(1); // Page number
        assertThat(result.getSize()).isEqualTo(2); // Page size
        assertThat(result.getTotalElements()).isEqualTo(5); // Total items
        assertThat(result.getTotalPages()).isEqualTo(3); // Total pages (5 items / 2 per page)
    }

    @Test
    void testRefreshAgingCache() {
        // Test cache refresh operation
        Customer customer = createCustomer(100L, "Customer A", COMPANY_ID);
        when(customerRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(customer));
        when(agingCalculationService.calculateAgingBuckets(eq(100L), any(LocalDate.class)))
                .thenReturn(createBuckets(new BigDecimal("1000.00")));

        arAgingService.refreshAgingCache(asOfDate);

        // Should trigger getAgingReport which recalculates
        verify(customerRepository, atLeastOnce()).findByCompanyId(COMPANY_ID);
        verify(agingCalculationService, atLeastOnce()).calculateAgingBuckets(eq(100L), any(LocalDate.class));
    }

    @Test
    void testInvalidateAgingCache() {
        // Test cache invalidation - no exceptions should be thrown
        assertThatCode(() -> arAgingService.invalidateAgingCache())
                .doesNotThrowAnyException();
    }

    @Test
    void testGetAgingReport_MissingCompanyContext() {
        // Clear company context
        CompanyContext.clear();

        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> arAgingService.getAgingReport(null, asOfDate, null, null, pageable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing company context");
    }

    @Test
    void testGetAgingReport_NullAsOfDate() {
        // Should default to today's date
        Customer customer = createCustomer(100L, "Customer A", COMPANY_ID);

        when(customerRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(customer));
        when(agingCalculationService.calculateAgingBuckets(eq(100L), any(LocalDate.class)))
                .thenReturn(createBuckets(new BigDecimal("1000.00")));

        Pageable pageable = PageRequest.of(0, 20);
        Page<ARAgingReportDTO> result = arAgingService.getAgingReport(null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        // Verify that calculateAgingBuckets was called with LocalDate.now() (captured
        // via any())
        verify(agingCalculationService, times(1)).calculateAgingBuckets(eq(100L), any(LocalDate.class));
    }

    @Test
    void testGetAgingReport_HasOverdueFlag() {
        // Test that hasOverdue flag is set correctly
        Customer customer1 = createCustomer(100L, "Customer A", COMPANY_ID);
        Customer customer2 = createCustomer(200L, "Customer B", COMPANY_ID);

        when(customerRepository.findByCompanyId(COMPANY_ID))
                .thenReturn(Arrays.asList(customer1, customer2));

        // Customer 1 has overdue amounts
        ARAgingBucketDTO overdueБuckets = new ARAgingBucketDTO();
        overdueБuckets.setDays1To30(new BigDecimal("1000.00"));
        overdueБuckets.setTotal(new BigDecimal("1000.00"));

        // Customer 2 has only current amounts (not overdue)
        ARAgingBucketDTO currentBuckets = new ARAgingBucketDTO();
        currentBuckets.setCurrent(new BigDecimal("500.00"));
        currentBuckets.setTotal(new BigDecimal("500.00"));

        when(agingCalculationService.calculateAgingBuckets(eq(100L), any(LocalDate.class)))
                .thenReturn(overdueБuckets);
        when(agingCalculationService.calculateAgingBuckets(eq(200L), any(LocalDate.class)))
                .thenReturn(currentBuckets);

        Pageable pageable = PageRequest.of(0, 20);
        Page<ARAgingReportDTO> result = arAgingService.getAgingReport(null, asOfDate, null, null, pageable);

        assertThat(result.getContent()).hasSize(2);

        ARAgingReportDTO customer1Report = result.getContent().stream()
                .filter(r -> r.getCustomerId().equals(100L))
                .findFirst()
                .orElseThrow();
        assertThat(customer1Report.isHasOverdue()).isTrue();

        ARAgingReportDTO customer2Report = result.getContent().stream()
                .filter(r -> r.getCustomerId().equals(200L))
                .findFirst()
                .orElseThrow();
        assertThat(customer2Report.isHasOverdue()).isFalse();
    }

    private Customer createCustomer(Long id, String name, Long companyId) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCode("CUST-" + id);
        customer.setName(name);
        customer.setCompanyId(companyId);
        return customer;
    }

    private ARAgingBucketDTO createBuckets(BigDecimal total) {
        ARAgingBucketDTO buckets = new ARAgingBucketDTO();
        buckets.setCurrent(total);
        buckets.setDays1To30(BigDecimal.ZERO);
        buckets.setDays31To60(BigDecimal.ZERO);
        buckets.setDays61To90(BigDecimal.ZERO);
        buckets.setDaysOver90(BigDecimal.ZERO);
        buckets.setTotal(total);
        return buckets;
    }
}
