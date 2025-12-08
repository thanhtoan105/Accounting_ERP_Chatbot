package com.accounting.service.impl.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.ARVATCorrectionCreateRequest;
import com.accounting.dto.ARVATCorrectionDTO;
import com.accounting.entity.ARVATCorrection;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.repository.ARVATCorrectionRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ARVATCorrectionServiceImplTest {

  @Mock
  private ARVATCorrectionRepository arVatCorrectionRepository;
  @Mock
  private SalesInvoiceRepository salesInvoiceRepository;
  @Mock
  private SalesInvoiceLineRepository salesInvoiceLineRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private AuditService auditService;
  @Mock
  private SecurityContext securityContext;
  @Mock
  private Authentication authentication;

  private ARVATCorrectionServiceImpl arVatCorrectionService;
  private static final Long COMPANY_ID = 1L;
  private static final Long USER_ID = 100L;
  private static final UUID INVOICE_ID = UUID.randomUUID();
  private static final UUID LINE_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    arVatCorrectionService = new ARVATCorrectionServiceImpl(
        arVatCorrectionRepository,
        salesInvoiceRepository,
        salesInvoiceLineRepository,
        userRepository,
        auditService);
    CompanyContext.setCompanyId(COMPANY_ID);

    // Setup security context
    SecurityContextHolder.setContext(securityContext);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getAuthorities()).thenAnswer(invocation -> 
        List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_CHIEF_ACCOUNTANT")));
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void createCorrection_shouldCreatePendingCorrection() {
    try (MockedStatic<SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      SalesInvoice invoice = createInvoice(SalesInvoiceStatus.POSTED, new BigDecimal("1000.00"));
      ARVATCorrectionCreateRequest request = new ARVATCorrectionCreateRequest();
      request.setInvoiceId(INVOICE_ID);
      request.setNewVatAmount(new BigDecimal("1200.00"));
      request.setReason("VAT rate correction needed");

      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, INVOICE_ID))
          .thenReturn(Optional.of(invoice));
      when(arVatCorrectionRepository.save(any(ARVATCorrection.class)))
          .thenAnswer(invocation -> {
            ARVATCorrection correction = invocation.getArgument(0);
            correction.setId(UUID.randomUUID());
            return correction;
          });

      ARVATCorrectionDTO result = arVatCorrectionService.createCorrection(request);

      assertNotNull(result);
      assertEquals(ARVATCorrection.Status.PENDING, result.getStatus());
      assertEquals(new BigDecimal("1000.00"), result.getOldVatAmount());
      assertEquals(new BigDecimal("1200.00"), result.getNewVatAmount());
      assertEquals("VAT rate correction needed", result.getReason());

      verify(arVatCorrectionRepository).save(any(ARVATCorrection.class));
      verify(auditService).logVatCorrectionCreated(
          eq(COMPANY_ID),
          eq(USER_ID),
          any(UUID.class),
          eq(INVOICE_ID),
          eq(new BigDecimal("1000.00")),
          eq(new BigDecimal("1200.00")),
          eq("VAT rate correction needed"));
    }
  }

  @Test
  void createCorrection_shouldRejectWhenInvoiceNotPosted() {
    try (MockedStatic<SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      SalesInvoice invoice = createInvoice(SalesInvoiceStatus.DRAFT, new BigDecimal("1000.00"));
      ARVATCorrectionCreateRequest request = new ARVATCorrectionCreateRequest();
      request.setInvoiceId(INVOICE_ID);
      request.setNewVatAmount(new BigDecimal("1200.00"));
      request.setReason("VAT rate correction needed");

      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, INVOICE_ID))
          .thenReturn(Optional.of(invoice));

      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> arVatCorrectionService.createCorrection(request));

      assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
      assertTrue(exception.getMessage().contains("POSTED"));

      verify(arVatCorrectionRepository, never()).save(any());
    }
  }

  @Test
  void createCorrection_shouldRejectWhenNewAmountEqualsOld() {
    try (MockedStatic<SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      SalesInvoice invoice = createInvoice(SalesInvoiceStatus.POSTED, new BigDecimal("1000.00"));
      ARVATCorrectionCreateRequest request = new ARVATCorrectionCreateRequest();
      request.setInvoiceId(INVOICE_ID);
      request.setNewVatAmount(new BigDecimal("1000.00")); // Same as old
      request.setReason("No change");

      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, INVOICE_ID))
          .thenReturn(Optional.of(invoice));

      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> arVatCorrectionService.createCorrection(request));

      assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
      assertTrue(exception.getMessage().contains("differ"));

      verify(arVatCorrectionRepository, never()).save(any());
    }
  }

  @Test
  void approveCorrection_shouldUpdateInvoiceVATAmount() {
    try (MockedStatic<SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      SalesInvoice invoice = createInvoice(SalesInvoiceStatus.POSTED, new BigDecimal("1000.00"));
      ARVATCorrection correction = createCorrection(ARVATCorrection.Status.PENDING,
          new BigDecimal("1000.00"), new BigDecimal("1200.00"));

      when(arVatCorrectionRepository.findByCompanyIdAndId(COMPANY_ID, correction.getId()))
          .thenReturn(Optional.of(correction));
      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, INVOICE_ID))
          .thenReturn(Optional.of(invoice));
      when(arVatCorrectionRepository.save(any(ARVATCorrection.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      ARVATCorrectionDTO result = arVatCorrectionService.approveCorrection(correction.getId(), null);

      assertNotNull(result);
      assertEquals(ARVATCorrection.Status.APPROVED, result.getStatus());
      assertEquals(new BigDecimal("1200.00"), invoice.getVatAmount());

      verify(salesInvoiceRepository).save(invoice);
      verify(auditService).logVatCorrectionApproved(
          eq(COMPANY_ID),
          eq(USER_ID),
          eq(correction.getId()),
          eq(INVOICE_ID),
          eq(new BigDecimal("1000.00")),
          eq(new BigDecimal("1200.00")));
    }
  }

  @Test
  void approveCorrection_shouldRejectWhenNotPending() {
    try (MockedStatic<SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      ARVATCorrection correction = createCorrection(ARVATCorrection.Status.APPROVED,
          new BigDecimal("1000.00"), new BigDecimal("1200.00"));

      when(arVatCorrectionRepository.findByCompanyIdAndId(COMPANY_ID, correction.getId()))
          .thenReturn(Optional.of(correction));

      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> arVatCorrectionService.approveCorrection(correction.getId(), null));

      assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
      assertTrue(exception.getMessage().contains("pending"));

      verify(salesInvoiceRepository, never()).save(any());
    }
  }

  @Test
  void rejectCorrection_shouldUpdateStatusToRejected() {
    try (MockedStatic<SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      ARVATCorrection correction = createCorrection(ARVATCorrection.Status.PENDING,
          new BigDecimal("1000.00"), new BigDecimal("1200.00"));

      when(arVatCorrectionRepository.findByCompanyIdAndId(COMPANY_ID, correction.getId()))
          .thenReturn(Optional.of(correction));
      when(arVatCorrectionRepository.save(any(ARVATCorrection.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      ARVATCorrectionDTO result = arVatCorrectionService.rejectCorrection(
          correction.getId(), "Invalid correction request");

      assertNotNull(result);
      assertEquals(ARVATCorrection.Status.REJECTED, result.getStatus());

      verify(arVatCorrectionRepository).save(any(ARVATCorrection.class));
    }
  }

  @Test
  void getCorrections_shouldFilterByStatus() {
    ARVATCorrection correction1 = createCorrection(ARVATCorrection.Status.PENDING,
        new BigDecimal("1000.00"), new BigDecimal("1200.00"));
    ARVATCorrection correction2 = createCorrection(ARVATCorrection.Status.APPROVED,
        new BigDecimal("1000.00"), new BigDecimal("1100.00"));

    when(arVatCorrectionRepository.findByCompanyIdAndInvoiceIdAndStatusOrderByCorrectedAtDesc(
        COMPANY_ID, INVOICE_ID, ARVATCorrection.Status.PENDING))
        .thenReturn(List.of(correction1));

    List<ARVATCorrectionDTO> results = arVatCorrectionService.getCorrections(
        INVOICE_ID, java.util.Map.of("status", ARVATCorrection.Status.PENDING));

    assertThat(results).hasSize(1);
    assertEquals(ARVATCorrection.Status.PENDING, results.get(0).getStatus());
  }

  // Helper methods
  private SalesInvoice createInvoice(SalesInvoiceStatus status, BigDecimal vatAmount) {
    SalesInvoice invoice = new SalesInvoice();
    invoice.setId(INVOICE_ID);
    invoice.setCompanyId(COMPANY_ID);
    invoice.setInvoiceNumber("INV-001");
    invoice.setInvoiceDate(java.time.LocalDate.now());
    invoice.setStatus(status);
    invoice.setVatAmount(vatAmount);
    return invoice;
  }

  private ARVATCorrection createCorrection(
      ARVATCorrection.Status status, BigDecimal oldAmount, BigDecimal newAmount) {
    ARVATCorrection correction = new ARVATCorrection();
    correction.setId(UUID.randomUUID());
    correction.setCompanyId(COMPANY_ID);
    correction.setInvoiceId(INVOICE_ID);
    correction.setOldVatAmount(oldAmount);
    correction.setNewVatAmount(newAmount);
    correction.setReason("Test correction");
    correction.setStatus(status);
    correction.setCorrectedById(USER_ID);
    correction.setCorrectedAt(Instant.now());
    return correction;
  }
}
