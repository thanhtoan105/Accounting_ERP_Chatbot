package com.accounting.service.impl.ap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.Supplier;
import com.accounting.repository.APPaymentRepository;
import com.accounting.repository.PaymentAllocationRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.APAgingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Verifies caching behavior for APAgingServiceImpl without requiring a Redis instance.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
    classes = APAgingCachingTest.TestConfig.class,
    loader = AnnotationConfigContextLoader.class)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
@ActiveProfiles({"test", "ap-aging-cache-test"})
class APAgingCachingTest {

  @org.springframework.boot.test.context.TestConfiguration
  @org.springframework.context.annotation.Profile("ap-aging-cache-test")
  @EnableCaching
  static class TestConfig {

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager("ap-aging");
    }

    @Bean
    PurchaseBillRepository purchaseBillRepository() {
      return Mockito.mock(PurchaseBillRepository.class);
    }

    @Bean
    PaymentAllocationRepository paymentAllocationRepository() {
      return Mockito.mock(PaymentAllocationRepository.class);
    }

    @Bean
    APPaymentRepository apPaymentRepository() {
      return Mockito.mock(APPaymentRepository.class);
    }

    @Bean
    SupplierRepository supplierRepository() {
      return Mockito.mock(SupplierRepository.class);
    }

    @Bean
    APAgingService agingService(
        PurchaseBillRepository purchaseBillRepository,
        PaymentAllocationRepository paymentAllocationRepository,
        APPaymentRepository apPaymentRepository,
        SupplierRepository supplierRepository) {
      return new APAgingServiceImpl(
          purchaseBillRepository, paymentAllocationRepository, apPaymentRepository, supplierRepository);
    }
  }

  @Autowired private APAgingService agingService;

  @Autowired private PurchaseBillRepository purchaseBillRepository;

  @Autowired private PaymentAllocationRepository paymentAllocationRepository;

  @Autowired private SupplierRepository supplierRepository;

  @Autowired private CacheManager cacheManager;

  private final LocalDate asOfDate = LocalDate.of(2025, 1, 15);

  @BeforeEach
  void setUp() {
    CompanyContext.setCompanyId(1L);
    reset(purchaseBillRepository, paymentAllocationRepository, supplierRepository);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
    if (cacheManager.getCache("ap-aging") != null) {
      cacheManager.getCache("ap-aging").clear();
    }
  }

  @Test
  void calculateAgingBuckets_usesCache() {
    mockRepositories();

    agingService.calculateAgingBuckets(100L, asOfDate, null);
    agingService.calculateAgingBuckets(100L, asOfDate, null);

    verify(purchaseBillRepository, times(1))
        .findByCompanyIdAndStatus(1L, PurchaseBillStatus.POSTED);
  }

  @Test
  void invalidateCache_evicsEntries() {
    mockRepositories();

    agingService.calculateAgingBuckets(100L, asOfDate, null);
    agingService.invalidateAgingCache();
    agingService.calculateAgingBuckets(100L, asOfDate, null);

    verify(purchaseBillRepository, times(2))
        .findByCompanyIdAndStatus(1L, PurchaseBillStatus.POSTED);
  }

  private void mockRepositories() {
    Supplier supplier = new Supplier();
    supplier.setId(100L);
    supplier.setCompanyId(1L);
    supplier.setName("Supplier A");
    supplier.setCode("SUP-A");
    supplier.setActive(true);

    PurchaseBill bill = new PurchaseBill();
    bill.setId(UUID.randomUUID());
    bill.setCompanyId(1L);
    bill.setSupplierId(100L);
    bill.setBillNumber("BILL-100");
    bill.setBillDate(asOfDate.minusDays(45));
    bill.setDueDate(asOfDate.minusDays(15));
    bill.setTotalAmount(new BigDecimal("5000"));
    bill.setVatAmount(BigDecimal.ZERO);
    bill.setReference("CACHE-REF");
    bill.setDescription("Cache test bill");
    bill.setStatus(PurchaseBillStatus.POSTED);
    bill.setCreatedById(1L);
    bill.setIsSensitive(false);
    bill.setCreatedAt(Instant.now());
    bill.setUpdatedAt(Instant.now());

    when(purchaseBillRepository.findByCompanyIdAndStatus(1L, PurchaseBillStatus.POSTED))
        .thenReturn(Collections.singletonList(bill));
    when(purchaseBillRepository.findById(bill.getId())).thenReturn(Optional.of(bill));
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(bill.getId()))
        .thenReturn(BigDecimal.ZERO);
    when(supplierRepository.findByCompanyId(1L)).thenReturn(Collections.singletonList(supplier));
  }
}

