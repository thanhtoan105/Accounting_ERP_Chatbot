package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.VoucherCountDTO;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherLineDTO;
import com.accounting.dto.VoucherListDTO;
import com.accounting.dto.VoucherValidationResult;
import com.accounting.entity.User;
import com.accounting.entity.Voucher;
import com.accounting.exception.VoucherValidationException;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.service.AuditService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.VoucherValidationService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherServiceImplTest {

  @Mock
  private VoucherRepository voucherRepository;

  @Mock
  private VoucherLineRepository voucherLineRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private CustomerRepository customerRepository;

  @Mock
  private SupplierRepository supplierRepository;

  @Mock
  private AuditService auditService;

  @Mock
  private JwtTokenProvider jwtTokenProvider;

  @Mock
  private VoucherValidationService voucherValidationService;

  @Mock
  private com.accounting.service.util.VoucherAuditHelper voucherAuditHelper;

  @Mock
  private PeriodManagementService periodManagementService;

  @Mock
  private EntityManager entityManager;

  @Mock
  private HttpServletRequest request;

  private VoucherServiceImpl voucherService;

  @BeforeEach
  void setUp() {
    voucherService = new VoucherServiceImpl(
        voucherRepository,
        voucherLineRepository,
        userRepository,
        customerRepository,
        supplierRepository,
        auditService,
        jwtTokenProvider,
        voucherValidationService,
        voucherAuditHelper,
        periodManagementService);

    // Inject EntityManager via reflection (since it's @PersistenceContext)
    ReflectionTestUtils.setField(voucherService, "entityManager", entityManager);

    // Set up SecurityContext for getCurrentUserId()
    SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
    Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getPrincipal()).thenReturn("1"); // User ID as string
    SecurityContextHolder.setContext(securityContext);

    CompanyContext.setCompanyId(1L);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void findAll_withPagination_returnsPaginatedResults() {
    Voucher voucher1 = createVoucher(UUID.randomUUID(), "VC2025-001", LocalDate.now(), "draft");
    Voucher voucher2 = createVoucher(UUID.randomUUID(), "VC2025-002", LocalDate.now(), "posted");

    List<Voucher> vouchers = List.of(voucher1, voucher2);
    Page<Voucher> page = new PageImpl<>(vouchers, PageRequest.of(0, 20), 2);

    when(voucherRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);
    when(userRepository.findById(any())).thenReturn(Optional.of(createTestUser(1L, "User 1")));

    Page<VoucherListDTO> result = voucherService.findAll(PageRequest.of(0, 20), null, null, null, null, null);

    assertNotNull(result);
    assertEquals(2, result.getTotalElements());
    assertEquals(2, result.getContent().size());
  }

  @Test
  void findAll_withStatusFilter_filtersByStatus() {
    Voucher draftVoucher = createVoucher(UUID.randomUUID(), "VC2025-001", LocalDate.now(), "draft");

    List<Voucher> filteredVouchers = List.of(draftVoucher);
    Page<Voucher> page = new PageImpl<>(filteredVouchers, PageRequest.of(0, 20), 1);

    when(voucherRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);
    when(userRepository.findById(any())).thenReturn(Optional.of(createTestUser(1L, "User 1")));

    Page<VoucherListDTO> result = voucherService.findAll(PageRequest.of(0, 20), "draft", null, null, null, null);

    assertEquals(1, result.getContent().size());
    assertEquals("draft", result.getContent().get(0).getStatus());
  }

  @Test
  void findAll_withDateRange_filtersByDate() {
    LocalDate fromDate = LocalDate.of(2025, 1, 1);
    LocalDate toDate = LocalDate.of(2025, 1, 31);
    Voucher voucherInRange = createVoucher(UUID.randomUUID(), "VC2025-001", LocalDate.of(2025, 1, 15), "draft");

    List<Voucher> filteredVouchers = List.of(voucherInRange);
    Page<Voucher> page = new PageImpl<>(filteredVouchers, PageRequest.of(0, 20), 1);

    when(voucherRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);
    when(userRepository.findById(any())).thenReturn(Optional.of(createTestUser(1L, "User 1")));

    Page<VoucherListDTO> result = voucherService.findAll(PageRequest.of(0, 20), null, fromDate, toDate, null, null);

    assertEquals(1, result.getContent().size());
  }

  @Test
  void findAll_withSearchTerm_searchesVoucherNumberAndDescription() {
    Voucher matchingVoucher = createVoucher(UUID.randomUUID(), "VC2025-001", LocalDate.now(), "draft");
    matchingVoucher.setDescription("Payment for services");

    List<Voucher> matchingVouchers = List.of(matchingVoucher);
    Page<Voucher> page = new PageImpl<>(matchingVouchers, PageRequest.of(0, 20), 1);

    when(voucherRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);
    when(userRepository.findById(any())).thenReturn(Optional.of(createTestUser(1L, "User 1")));

    Page<VoucherListDTO> result = voucherService.findAll(PageRequest.of(0, 20), null, null, null, "VC2025", null);

    assertEquals(1, result.getContent().size());
  }

  @Test
  void findAll_missingCompanyContext_throwsException() {
    CompanyContext.clear();

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> voucherService.findAll(PageRequest.of(0, 20), null, null, null, null, null));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Missing company context"));
  }

  @Test
  void getCounts_returnsCountsByStatus() {
    when(voucherRepository.countByCompanyIdAndStatus(1L, "draft")).thenReturn(5L);
    when(voucherRepository.countByCompanyIdAndStatus(1L, "posted")).thenReturn(10L);
    when(voucherRepository.countByCompanyIdAndStatus(1L, "unposted")).thenReturn(2L);

    VoucherCountDTO result = voucherService.getCounts();

    assertNotNull(result);
    assertEquals(5L, result.getDraft());
    assertEquals(10L, result.getPosted());
    assertEquals(2L, result.getUnposted());
  }

  @Test
  void getCounts_missingCompanyContext_throwsException() {
    CompanyContext.clear();

    ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> voucherService.getCounts());

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Missing company context"));
  }

  @Test
  void getVoucherById_voucherFound_returnsDTO() {
    UUID voucherId = UUID.randomUUID();
    Voucher voucher = createVoucher(voucherId, "VC2025-001", LocalDate.now(), "draft");

    when(voucherRepository.findByCompanyIdAndId(1L, voucherId))
        .thenReturn(Optional.of(voucher));
    when(userRepository.findById(any())).thenReturn(Optional.of(createTestUser(1L, "User 1")));

    Optional<VoucherDTO> result = voucherService.getVoucherById(voucherId);

    assertTrue(result.isPresent());
    assertEquals(voucherId, result.get().getId());
    assertEquals("VC2025-001", result.get().getVoucherNumber());
  }

  @Test
  void getVoucherById_voucherNotFound_returnsEmpty() {
    UUID voucherId = UUID.randomUUID();

    when(voucherRepository.findByCompanyIdAndId(1L, voucherId)).thenReturn(Optional.empty());

    Optional<VoucherDTO> result = voucherService.getVoucherById(voucherId);

    assertFalse(result.isPresent());
  }

  @Test
  void delete_draftVoucher_notReferenced_deletesSuccessfully() {
    UUID voucherId = UUID.randomUUID();
    Voucher voucher = createVoucher(voucherId, "VC2025-001", LocalDate.now(), "draft");

    when(voucherRepository.findByCompanyIdAndId(1L, voucherId))
        .thenReturn(Optional.of(voucher));
    when(voucherRepository.isReferenced(voucherId)).thenReturn(false);
    when(request.getHeader("Authorization")).thenReturn("Bearer test-token");
    when(jwtTokenProvider.getUserIdFromToken("test-token")).thenReturn(1L);

    voucherService.delete(voucherId, "Test reason", request);

    // Verify repository delete was called (implicitly through no exception)
  }

  @Test
  void delete_postedVoucher_throwsConflictException() {
    UUID voucherId = UUID.randomUUID();
    Voucher voucher = createVoucher(voucherId, "VC2025-001", LocalDate.now(), "posted");

    when(voucherRepository.findByCompanyIdAndId(1L, voucherId))
        .thenReturn(Optional.of(voucher));

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> voucherService.delete(voucherId, "Test reason", request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    assertTrue(exception.getReason() != null &&
        (exception.getReason().contains("Cannot delete posted voucher") ||
            exception.getReason().contains("only draft vouchers can be deleted")));
  }

  @Test
  void delete_referencedVoucher_throwsConflictException() {
    UUID voucherId = UUID.randomUUID();
    Voucher voucher = createVoucher(voucherId, "VC2025-001", LocalDate.now(), "draft");

    when(voucherRepository.findByCompanyIdAndId(1L, voucherId))
        .thenReturn(Optional.of(voucher));
    when(voucherRepository.isReferenced(voucherId)).thenReturn(true);

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> voucherService.delete(voucherId, "Test reason", request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("referenced"));
  }

  @Test
  void delete_missingReason_throwsBadRequestException() {
    UUID voucherId = UUID.randomUUID();

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> voucherService.delete(voucherId, null, request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Deletion reason is required"));
  }

  @Test
  void delete_blankReason_throwsBadRequestException() {
    UUID voucherId = UUID.randomUUID();

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> voucherService.delete(voucherId, "   ", request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Deletion reason is required"));
  }

  @Test
  void delete_voucherNotFound_throwsNotFoundException() {
    UUID voucherId = UUID.randomUUID();

    when(voucherRepository.findByCompanyIdAndId(1L, voucherId)).thenReturn(Optional.empty());

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> voucherService.delete(voucherId, "Test reason", request));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Voucher not found"));
  }

  @Test
  void search_withSearchTerm_returnsMatchingVouchers() {
    Voucher matchingVoucher = createVoucher(UUID.randomUUID(), "VC2025-001", LocalDate.now(), "draft");
    matchingVoucher.setDescription("Payment invoice");

    List<Voucher> matchingVouchers = List.of(matchingVoucher);
    Page<Voucher> page = new PageImpl<>(matchingVouchers, PageRequest.of(0, 20), 1);

    when(voucherRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);
    when(userRepository.findById(any())).thenReturn(Optional.of(createTestUser(1L, "User 1")));
    when(voucherRepository.findByCompanyIdAndId(any(), any()))
        .thenReturn(Optional.of(matchingVoucher));

    List<VoucherDTO> result = voucherService.search("VC2025");

    assertEquals(1, result.size());
    assertEquals("VC2025-001", result.get(0).getVoucherNumber());
  }

  private Voucher createVoucher(UUID id, String voucherNumber, LocalDate date, String status) {
    Voucher voucher = new Voucher();
    voucher.setId(id);
    voucher.setCompanyId(1L);
    voucher.setVoucherNumber(voucherNumber);
    voucher.setVoucherDate(date);
    voucher.setDescription("Test voucher");
    voucher.setStatus(status);
    voucher.setCurrency("VND");
    voucher.setTotalDebit(BigDecimal.valueOf(1000));
    voucher.setTotalCredit(BigDecimal.valueOf(1000));
    voucher.setEnteredBy(1L);
    voucher.setCreatedAt(Instant.now());
    voucher.setUpdatedAt(Instant.now());
    return voucher;
  }

  private User createTestUser(Long id, String fullName) {
    User user = new User();
    user.setId(id);
    user.setFullName(fullName);
    user.setEmail("user" + id + "@example.com");
    return user;
  }

  @Test
  void create_validRequest_createsVoucher() {
    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    VoucherLineDTO line1 = new VoucherLineDTO();
    line1.setAccountId(1L);
    line1.setDebit(BigDecimal.valueOf(1000));
    line1.setCredit(BigDecimal.ZERO);
    VoucherLineDTO line2 = new VoucherLineDTO();
    line2.setAccountId(2L);
    line2.setDebit(BigDecimal.ZERO);
    line2.setCredit(BigDecimal.valueOf(1000));
    request.setLines(List.of(line1, line2));

    VoucherValidationResult validationResult = new VoucherValidationResult(true, null);
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    // Mock period management to allow voucher creation
    when(periodManagementService.isDateInOpenPeriod(any())).thenReturn(true);

    when(voucherRepository.save(any(Voucher.class)))
        .thenAnswer(
            invocation -> {
              Voucher voucher = invocation.getArgument(0);
              if (voucher.getId() == null) {
                voucher.setId(UUID.randomUUID());
              }
              return voucher;
            });

    // Mock voucher number generation
    Query query = org.mockito.Mockito.mock(Query.class);
    when(entityManager.createNativeQuery(any(String.class))).thenReturn(query);
    when(query.setParameter(any(String.class), any())).thenReturn(query);
    when(query.getSingleResult()).thenReturn("VC2025-000001");

    when(voucherLineRepository.saveAll(any(List.class))).thenReturn(List.of());

    VoucherDTO result = voucherService.create(request);

    assertNotNull(result);
    assertEquals("VC2025-000001", result.getVoucherNumber());
    assertEquals("draft", result.getStatus());
  }

  @Test
  void create_validationFails_throwsException() {
    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test voucher");
    request.setLines(List.of());

    VoucherValidationResult validationResult = new VoucherValidationResult(false, null);
    validationResult.addError(1, "accountId", "Account is required");
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    VoucherValidationException exception = assertThrows(VoucherValidationException.class,
        () -> voucherService.create(request));

    assertNotNull(exception.getValidationResult());
    assertFalse(exception.getValidationResult().isValid());
  }

  @Test
  void update_draftVoucher_updatesSuccessfully() {
    UUID voucherId = UUID.randomUUID();
    Voucher existingVoucher = createVoucher(voucherId, "VC2025-001", LocalDate.now(), "draft");

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now().plusDays(1));
    request.setDescription("Updated description");
    VoucherLineDTO line1 = new VoucherLineDTO();
    line1.setAccountId(1L);
    line1.setDebit(BigDecimal.valueOf(2000));
    line1.setCredit(BigDecimal.ZERO);
    VoucherLineDTO line2 = new VoucherLineDTO();
    line2.setAccountId(2L);
    line2.setDebit(BigDecimal.ZERO);
    line2.setCredit(BigDecimal.valueOf(2000));
    request.setLines(List.of(line1, line2));

    VoucherValidationResult validationResult = new VoucherValidationResult(true, null);
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    // Mock period management to allow voucher update
    when(periodManagementService.isDateInOpenPeriod(any())).thenReturn(true);

    when(voucherRepository.findByCompanyIdAndId(1L, voucherId))
        .thenReturn(Optional.of(existingVoucher));
    when(voucherRepository.save(any(Voucher.class))).thenReturn(existingVoucher);
    when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(voucherId))
        .thenReturn(List.of()); // Empty lines for new voucher

    VoucherDTO result = voucherService.update(voucherId, request);

    assertNotNull(result);
    assertEquals("Updated description", result.getDescription());
  }

  @Test
  void update_postedVoucher_throwsConflictException() {
    UUID voucherId = UUID.randomUUID();
    Voucher postedVoucher = createVoucher(voucherId, "VC2025-001", LocalDate.now(), "posted");

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test");
    request.setLines(List.of());

    when(voucherRepository.findByCompanyIdAndId(1L, voucherId))
        .thenReturn(Optional.of(postedVoucher));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> voucherService.update(voucherId, request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    assertTrue(exception.getReason().contains("only draft vouchers can be updated"));
  }

  @Test
  void update_voucherNotFound_throwsNotFoundException() {
    UUID voucherId = UUID.randomUUID();

    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(LocalDate.now());
    request.setDescription("Test");
    request.setLines(List.of());

    when(voucherRepository.findByCompanyIdAndId(1L, voucherId)).thenReturn(Optional.empty());

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> voucherService.update(voucherId, request));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    assertTrue(exception.getReason().contains("Voucher not found"));
  }

  // ========== PERIOD VALIDATION TESTS ==========

  @Test
  void create_voucherWithDateInOpenPeriod_succeeds() {
    LocalDate voucherDate = LocalDate.of(2025, 1, 15);
    VoucherCreateRequest request = createValidVoucherRequest(voucherDate);

    // Mock successful validation
    VoucherValidationResult validationResult = new VoucherValidationResult();
    validationResult.setValid(true);
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    // Mock period management - date is in open period
    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(true);
    when(voucherRepository.save(any(Voucher.class))).thenReturn(createVoucher(UUID.randomUUID(), "VC2025-001", voucherDate, "draft"));

    // Should not throw any exception
    assertDoesNotThrow(() -> voucherService.create(request));
  }

  @Test
  void create_voucherWithDateInClosedPeriod_throwsBadRequest() {
    LocalDate voucherDate = LocalDate.of(2024, 12, 15); // Closed period
    VoucherCreateRequest request = createValidVoucherRequest(voucherDate);

    // Mock successful validation
    VoucherValidationResult validationResult = new VoucherValidationResult();
    validationResult.setValid(true);
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    // Mock period management - date is NOT in open period
    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(false);

    // Mock period lookup for error message
    com.accounting.dto.AccountingPeriodDTO closedPeriod = new com.accounting.dto.AccountingPeriodDTO();
    closedPeriod.setId(UUID.randomUUID());
    closedPeriod.setPeriodName("Dec-2024");
    when(periodManagementService.findPeriodByDate(voucherDate)).thenReturn(Optional.of(closedPeriod));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> voucherService.create(request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason().contains("Cannot create voucher in closed or future period: Dec-2024"));

    // Verify audit logging was called
    verify(auditService).logPeriodValidationBlocked(
        closedPeriod.getId(),
        "VOUCHER_CREATION",
        "Cannot create voucher in closed or future period: Dec-2024"
    );
  }

  @Test
  void create_voucherWithDateInClosedPeriodNotFound_throwsBadRequestWithUnknownPeriod() {
    LocalDate voucherDate = LocalDate.of(2024, 12, 15); // Closed period
    VoucherCreateRequest request = createValidVoucherRequest(voucherDate);

    // Mock successful validation
    VoucherValidationResult validationResult = new VoucherValidationResult();
    validationResult.setValid(true);
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    // Mock period management - date is NOT in open period and period not found
    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(false);
    when(periodManagementService.findPeriodByDate(voucherDate)).thenReturn(Optional.empty());

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> voucherService.create(request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason().contains("Cannot create voucher in closed or future period: Unknown Period"));

    // Verify audit logging was called with null period ID
    verify(auditService).logPeriodValidationBlocked(
        null,
        "VOUCHER_CREATION",
        "Cannot create voucher in closed or future period: Unknown Period"
    );
  }

  @Test
  void create_voucherWithSpecificClosedPeriodId_throwsBadRequest() {
    LocalDate voucherDate = LocalDate.now(); // Current date (should be open)
    UUID closedPeriodUuid = UUID.randomUUID();
    VoucherCreateRequest request = createValidVoucherRequest(voucherDate);
    request.setPeriodId(closedPeriodUuid);

    // Mock successful validation
    VoucherValidationResult validationResult = new VoucherValidationResult();
    validationResult.setValid(true);
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    // Mock period management - date is in open period but specific period ID is closed
    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(true);
    when(periodManagementService.isPeriodOpen(closedPeriodUuid)).thenReturn(false);

    // Mock period lookup for error message
    com.accounting.dto.AccountingPeriodDTO closedPeriod = new com.accounting.dto.AccountingPeriodDTO();
    closedPeriod.setId(closedPeriodUuid);
    closedPeriod.setPeriodName("Jan-2025");
    when(periodManagementService.getPeriodById(closedPeriodUuid)).thenReturn(Optional.of(closedPeriod));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> voucherService.create(request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason().contains("Cannot create voucher in closed period: Jan-2025"));

    // Verify audit logging was called
    verify(auditService).logPeriodValidationBlocked(
        closedPeriodUuid,
        "VOUCHER_CREATION",
        "Cannot create voucher in closed period: Jan-2025"
    );
  }

  @Test
  void update_voucherWithOpenPeriod_succeeds() {
    UUID voucherId = UUID.randomUUID();
    LocalDate voucherDate = LocalDate.of(2025, 1, 15);
    VoucherCreateRequest request = createValidVoucherRequest(voucherDate);

    // Mock existing voucher
    Voucher existingVoucher = createVoucher(voucherId, "VC2025-001", voucherDate, "draft");
    when(voucherRepository.findByCompanyIdAndId(1L, voucherId)).thenReturn(Optional.of(existingVoucher));

    // Mock successful validation
    VoucherValidationResult validationResult = new VoucherValidationResult();
    validationResult.setValid(true);
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    // Mock period management - date is in open period
    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(true);
    when(voucherRepository.save(any(Voucher.class))).thenReturn(existingVoucher);

    // Should not throw any exception
    assertDoesNotThrow(() -> voucherService.update(voucherId, request));
  }

  @Test
  void update_voucherWithClosedPeriod_throwsBadRequest() {
    UUID voucherId = UUID.randomUUID();
    LocalDate voucherDate = LocalDate.of(2024, 12, 15); // Closed period
    VoucherCreateRequest request = createValidVoucherRequest(voucherDate);

    // Mock existing voucher
    Voucher existingVoucher = createVoucher(voucherId, "VC2025-001", voucherDate, "draft");
    when(voucherRepository.findByCompanyIdAndId(1L, voucherId)).thenReturn(Optional.of(existingVoucher));

    // Mock successful validation
    VoucherValidationResult validationResult = new VoucherValidationResult();
    validationResult.setValid(true);
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    // Mock period management - date is NOT in open period
    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(false);

    // Mock period lookup for error message
    com.accounting.dto.AccountingPeriodDTO closedPeriod = new com.accounting.dto.AccountingPeriodDTO();
    closedPeriod.setId(UUID.randomUUID());
    closedPeriod.setPeriodName("Dec-2024");
    when(periodManagementService.findPeriodByDate(voucherDate)).thenReturn(Optional.of(closedPeriod));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> voucherService.update(voucherId, request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason().contains("Cannot create voucher in closed or future period: Dec-2024"));
  }

  @Test
  void create_periodValidationServiceThrowsException_throwsInternalServerError() {
    LocalDate voucherDate = LocalDate.now();
    VoucherCreateRequest request = createValidVoucherRequest(voucherDate);

    // Mock successful validation
    VoucherValidationResult validationResult = new VoucherValidationResult();
    validationResult.setValid(true);
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    // Mock period management service throwing exception
    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenThrow(new RuntimeException("Database error"));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> voucherService.create(request));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
    assertTrue(exception.getReason().contains("Failed to validate period for voucher creation"));
  }

  @Test
  void update_periodValidationServiceThrowsException_throwsInternalServerError() {
    UUID voucherId = UUID.randomUUID();
    LocalDate voucherDate = LocalDate.now();
    VoucherCreateRequest request = createValidVoucherRequest(voucherDate);

    // Mock existing voucher
    Voucher existingVoucher = createVoucher(voucherId, "VC2025-001", voucherDate, "draft");
    when(voucherRepository.findByCompanyIdAndId(1L, voucherId)).thenReturn(Optional.of(existingVoucher));

    // Mock successful validation
    VoucherValidationResult validationResult = new VoucherValidationResult();
    validationResult.setValid(true);
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    // Mock period management service throwing exception
    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenThrow(new RuntimeException("Database error"));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> voucherService.update(voucherId, request));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
    assertTrue(exception.getReason().contains("Failed to validate period for voucher creation"));
  }

  @Test
  void create_auditLoggingFails_continuesWithValidation() {
    LocalDate voucherDate = LocalDate.of(2024, 12, 15);
    VoucherCreateRequest request = createValidVoucherRequest(voucherDate);

    // Mock successful validation
    VoucherValidationResult validationResult = new VoucherValidationResult();
    validationResult.setValid(true);
    when(voucherValidationService.validate(any())).thenReturn(validationResult);

    // Mock period management - date is NOT in open period
    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(false);

    // Mock period lookup for error message
    com.accounting.dto.AccountingPeriodDTO closedPeriod = new com.accounting.dto.AccountingPeriodDTO();
    closedPeriod.setId(UUID.randomUUID());
    closedPeriod.setPeriodName("Dec-2024");
    when(periodManagementService.findPeriodByDate(voucherDate)).thenReturn(Optional.of(closedPeriod));

    // Mock audit service throwing exception
    doThrow(new RuntimeException("Audit service error")).when(auditService).logPeriodValidationBlocked(any(), any(), any());

    // Should still throw the period validation exception even if audit logging fails
    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> voucherService.create(request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason().contains("Cannot create voucher in closed or future period: Dec-2024"));
  }

  // Helper method to create valid voucher request
  private VoucherCreateRequest createValidVoucherRequest(LocalDate date) {
    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(date);
    request.setDescription("Test voucher");
    request.setCurrency("VND");

    // Create valid voucher lines
    VoucherLineDTO debitLine = new VoucherLineDTO();
    debitLine.setAccountId(101L);
    debitLine.setDebit(new BigDecimal("1000"));
    debitLine.setCredit(BigDecimal.ZERO);

    VoucherLineDTO creditLine = new VoucherLineDTO();
    creditLine.setAccountId(201L);
    creditLine.setDebit(BigDecimal.ZERO);
    creditLine.setCredit(new BigDecimal("1000"));

    request.setLines(List.of(debitLine, creditLine));
    return request;
  }

  // Helper method to assert that no exception is thrown
  private void assertDoesNotThrow(Runnable runnable) {
    try {
      runnable.run();
    } catch (Exception e) {
      throw new AssertionError("Expected no exception, but got: " + e.getMessage(), e);
    }
  }
}
