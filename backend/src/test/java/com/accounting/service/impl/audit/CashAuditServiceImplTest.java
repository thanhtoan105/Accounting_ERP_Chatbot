package com.accounting.service.impl.audit;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.accounting.dto.audit.CashAuditLogDTO;
import com.accounting.dto.audit.CashAuditPageDTO;
import com.accounting.dto.audit.CashAuditQueryDTO;
import com.accounting.dto.audit.PurgeRequestDTO;
import com.accounting.dto.audit.PurgeResponseDTO;
import com.accounting.entity.AuditLog;
import com.accounting.entity.audit.AuditPurgeRequest;
import com.accounting.entity.audit.AuditPurgeRequest.PurgeStatus;
import com.accounting.exception.BusinessException;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.audit.AuditPurgeRequestRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.CashAuditService.ExportFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for CashAuditServiceImpl.
 *
 * Test coverage:
 * - Audit log querying with filters
 * - Export functionality (JSON, CSV)
 * - Purge workflow (request, approve, reject)
 * - Dual approval validation
 * - Company context validation
 * - Date range validation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CashAuditServiceImpl")
class CashAuditServiceImplTest {

  @Mock
  private AuditLogRepository auditLogRepository;

  @Mock
  private AuditPurgeRequestRepository purgeRequestRepository;

  @InjectMocks
  private CashAuditServiceImpl cashAuditService;

  private MockedStatic<SecurityUtils> securityUtilsMock;

  private static final Long COMPANY_ID = 1L;
  private static final Long USER_ID = 100L;
  private static final Long DIFFERENT_USER_ID = 200L;

  @BeforeEach
  void setUp() {
    // Use properly configured ObjectMapper for testing
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    cashAuditService = new CashAuditServiceImpl(
        auditLogRepository,
        purgeRequestRepository,
        objectMapper);

    CompanyContext.setCompanyId(COMPANY_ID);
    securityUtilsMock = mockStatic(SecurityUtils.class);
    securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
    if (securityUtilsMock != null) {
      securityUtilsMock.close();
    }
  }

  // === Helper Methods ===

  private CashAuditQueryDTO createValidFilter() {
    CashAuditQueryDTO filter = new CashAuditQueryDTO();
    filter.setDateFrom(LocalDate.now().minusMonths(1));
    filter.setDateTo(LocalDate.now());
    filter.setPage(0);
    filter.setSize(20);
    return filter;
  }

  private AuditLog createMockAuditLog() {
    AuditLog log = new AuditLog();
    log.setId(1L);
    log.setAction("TEST_ACTION");
    log.setEntityType("TestEntity");
    log.setEntityId("test-123");
    log.setCompanyId(COMPANY_ID);
    log.setUserId(USER_ID);
    log.setCreatedAt(Instant.now());
    log.setSuccess(true);
    return log;
  }

  private AuditPurgeRequest createMockPurgeRequest() {
    AuditPurgeRequest request = new AuditPurgeRequest();
    request.setId(UUID.randomUUID());
    request.setCompanyId(COMPANY_ID);
    request.setRequesterId(USER_ID);
    request.setDateFrom(LocalDate.now().minusMonths(6));
    request.setDateTo(LocalDate.now().minusMonths(1));
    request.setReason("GDPR compliance");
    request.setEstimatedRecords(100);
    return request;
  }

  // === Query Tests ===

  @Nested
  @DisplayName("Query Audit Logs")
  class QueryAuditLogsTests {

    @Test
    @DisplayName("should return paginated audit logs for valid filter")
    void shouldReturnPaginatedAuditLogs() {
      // Given
      CashAuditQueryDTO filter = createValidFilter();
      List<AuditLog> mockLogs = List.of(createMockAuditLog(), createMockAuditLog());
      Page<AuditLog> mockPage = new PageImpl<>(mockLogs);

      when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
          .thenReturn(mockPage);
      when(auditLogRepository.save(any(AuditLog.class))).thenReturn(createMockAuditLog());

      // When
      CashAuditPageDTO result = cashAuditService.queryAuditLogs(filter);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getData()).hasSize(2);
      assertThat(result.getMeta().getTotal()).isEqualTo(2);
    }

    @Test
    @DisplayName("should throw exception when company context is missing")
    void shouldThrowExceptionWhenCompanyContextMissing() {
      // Given
      CompanyContext.clear();
      CashAuditQueryDTO filter = createValidFilter();

      // When/Then
      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> cashAuditService.queryAuditLogs(filter));

      assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(exception.getReason()).contains("X-Company-Id");
    }

    @Test
    @DisplayName("should throw exception when date range exceeds 12 months")
    void shouldThrowExceptionWhenDateRangeExceeds12Months() {
      // Given
      CashAuditQueryDTO filter = createValidFilter();
      filter.setDateFrom(LocalDate.now().minusMonths(15));

      // When/Then
      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> cashAuditService.queryAuditLogs(filter));

      assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(exception.getReason()).contains("12 months");
    }

    @Test
    @DisplayName("should throw exception when dateFrom is after dateTo")
    void shouldThrowExceptionWhenDateFromAfterDateTo() {
      // Given
      CashAuditQueryDTO filter = createValidFilter();
      filter.setDateFrom(LocalDate.now());
      filter.setDateTo(LocalDate.now().minusDays(10));

      // When/Then
      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> cashAuditService.queryAuditLogs(filter));

      assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("should throw exception when page size is invalid")
    void shouldThrowExceptionWhenPageSizeInvalid() {
      // Given
      CashAuditQueryDTO filter = createValidFilter();
      filter.setSize(25); // Invalid - must be 10, 20, or 50

      // When/Then
      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> cashAuditService.queryAuditLogs(filter));

      assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(exception.getReason()).contains("size");
    }
  }

  // === Export Tests ===

  @Nested
  @DisplayName("Export Audit Logs")
  class ExportAuditLogsTests {

    @Test
    @DisplayName("should export to JSON format")
    void shouldExportToJson() {
      // Given
      CashAuditQueryDTO filter = createValidFilter();
      List<AuditLog> mockLogs = List.of(createMockAuditLog());
      Page<AuditLog> mockPage = new PageImpl<>(mockLogs);

      when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
          .thenReturn(mockPage);
      when(auditLogRepository.save(any(AuditLog.class))).thenReturn(createMockAuditLog());

      // When
      byte[] result = cashAuditService.exportAuditLogs(filter, ExportFormat.JSON);

      // Then
      assertThat(result).isNotNull();
      assertThat(new String(result)).contains("["); // JSON array
    }

    @Test
    @DisplayName("should export to CSV format")
    void shouldExportToCsv() {
      // Given
      CashAuditQueryDTO filter = createValidFilter();
      List<AuditLog> mockLogs = List.of(createMockAuditLog());
      Page<AuditLog> mockPage = new PageImpl<>(mockLogs);

      when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
          .thenReturn(mockPage);
      when(auditLogRepository.save(any(AuditLog.class))).thenReturn(createMockAuditLog());

      // When
      byte[] result = cashAuditService.exportAuditLogs(filter, ExportFormat.CSV);

      // Then
      assertThat(result).isNotNull();
      String csv = new String(result);
      assertThat(csv).contains("id,timestamp,action"); // Header
    }

    @Test
    @DisplayName("should calculate SHA-256 hash for export")
    void shouldCalculateHashForExport() {
      // Given
      CashAuditQueryDTO filter = createValidFilter();
      List<AuditLog> mockLogs = List.of(createMockAuditLog());
      Page<AuditLog> mockPage = new PageImpl<>(mockLogs);

      when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
          .thenReturn(mockPage);
      when(auditLogRepository.save(any(AuditLog.class))).thenReturn(createMockAuditLog());

      // When
      cashAuditService.exportAuditLogs(filter, ExportFormat.JSON);
      String hash = cashAuditService.getLastExportHash();

      // Then
      assertThat(hash).isNotNull();
      assertThat(hash).matches("[a-f0-9]{64}"); // SHA-256 hex
    }
  }

  // === Purge Workflow Tests ===

  @Nested
  @DisplayName("Purge Request")
  class PurgeRequestTests {

    @Test
    @DisplayName("should create purge request successfully")
    void shouldCreatePurgeRequest() {
      // Given
      PurgeRequestDTO request = new PurgeRequestDTO();
      request.setDateFrom(LocalDate.now().minusMonths(6));
      request.setDateTo(LocalDate.now().minusMonths(1));
      request.setReason("GDPR compliance");

      when(auditLogRepository.count(any(Specification.class))).thenReturn(100L);
      when(purgeRequestRepository.save(any(AuditPurgeRequest.class)))
          .thenAnswer(inv -> {
            AuditPurgeRequest saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
          });
      when(auditLogRepository.save(any(AuditLog.class))).thenReturn(createMockAuditLog());

      // When
      PurgeResponseDTO result = cashAuditService.requestPurge(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
      assertThat(result.getEstimatedRecords()).isEqualTo(100);
      assertThat(result.getRequestedBy()).isEqualTo(USER_ID);
    }
  }

  @Nested
  @DisplayName("Purge Approval")
  class PurgeApprovalTests {

    @Test
    @DisplayName("should approve purge when approver is different from requester")
    void shouldApprovePurgeWhenDifferentUser() {
      // Given
      UUID requestId = UUID.randomUUID();
      AuditPurgeRequest mockRequest = createMockPurgeRequest();
      mockRequest.setId(requestId);

      // Set approver to different user
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(DIFFERENT_USER_ID);

      when(purgeRequestRepository.findByIdAndCompanyId(requestId, COMPANY_ID))
          .thenReturn(Optional.of(mockRequest));
      when(auditLogRepository.findAll(any(Specification.class)))
          .thenReturn(List.of(createMockAuditLog()));
      when(purgeRequestRepository.save(any(AuditPurgeRequest.class)))
          .thenReturn(mockRequest);
      when(auditLogRepository.save(any(AuditLog.class))).thenReturn(createMockAuditLog());

      // When
      PurgeResponseDTO result = cashAuditService.approvePurge(requestId);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo("APPROVED");
      verify(auditLogRepository).deleteAll(anyList());
    }

    @Test
    @DisplayName("should reject self-approval (dual approval enforcement)")
    void shouldRejectSelfApproval() {
      // Given
      UUID requestId = UUID.randomUUID();
      AuditPurgeRequest mockRequest = createMockPurgeRequest();
      mockRequest.setId(requestId);
      mockRequest.setRequesterId(USER_ID); // Same as current user

      when(purgeRequestRepository.findByIdAndCompanyId(requestId, COMPANY_ID))
          .thenReturn(Optional.of(mockRequest));

      // When/Then
      BusinessException exception = assertThrows(
          BusinessException.class,
          () -> cashAuditService.approvePurge(requestId));

      assertThat(exception.getErrorCode()).isEqualTo("SELF_APPROVAL_FORBIDDEN");
      verify(auditLogRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("should throw exception when purge request not found")
    void shouldThrowExceptionWhenPurgeRequestNotFound() {
      // Given
      UUID requestId = UUID.randomUUID();

      when(purgeRequestRepository.findByIdAndCompanyId(requestId, COMPANY_ID))
          .thenReturn(Optional.empty());

      // When/Then
      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> cashAuditService.approvePurge(requestId));

      assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("should throw exception when purge request already processed")
    void shouldThrowExceptionWhenAlreadyProcessed() {
      // Given
      UUID requestId = UUID.randomUUID();
      AuditPurgeRequest mockRequest = createMockPurgeRequest();
      mockRequest.setId(requestId);
      mockRequest.approve(DIFFERENT_USER_ID, 50); // Already approved

      // Set approver to different user to pass self-approval check
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(DIFFERENT_USER_ID);

      when(purgeRequestRepository.findByIdAndCompanyId(requestId, COMPANY_ID))
          .thenReturn(Optional.of(mockRequest));

      // When/Then
      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> cashAuditService.approvePurge(requestId));

      assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
  }

  @Nested
  @DisplayName("Purge Rejection")
  class PurgeRejectionTests {

    @Test
    @DisplayName("should reject purge request successfully")
    void shouldRejectPurgeRequest() {
      // Given
      UUID requestId = UUID.randomUUID();
      AuditPurgeRequest mockRequest = createMockPurgeRequest();
      mockRequest.setId(requestId);
      String rejectionReason = "Insufficient justification";

      when(purgeRequestRepository.findByIdAndCompanyId(requestId, COMPANY_ID))
          .thenReturn(Optional.of(mockRequest));
      when(purgeRequestRepository.save(any(AuditPurgeRequest.class)))
          .thenReturn(mockRequest);
      when(auditLogRepository.save(any(AuditLog.class))).thenReturn(createMockAuditLog());

      // When
      PurgeResponseDTO result = cashAuditService.rejectPurge(requestId, rejectionReason);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo("REJECTED");
      verify(auditLogRepository, never()).deleteAll(anyList());
    }
  }

  // === Company Context Tests ===

  @Nested
  @DisplayName("Company Context Validation")
  class CompanyContextTests {

    @Test
    @DisplayName("queryAuditLogs should require company context")
    void queryAuditLogsShouldRequireCompanyContext() {
      CompanyContext.clear();
      CashAuditQueryDTO filter = createValidFilter();

      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> cashAuditService.queryAuditLogs(filter));

      assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("exportAuditLogs should require company context")
    void exportAuditLogsShouldRequireCompanyContext() {
      CompanyContext.clear();
      CashAuditQueryDTO filter = createValidFilter();

      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> cashAuditService.exportAuditLogs(filter, ExportFormat.JSON));

      assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("requestPurge should require company context")
    void requestPurgeShouldRequireCompanyContext() {
      CompanyContext.clear();
      PurgeRequestDTO request = new PurgeRequestDTO();
      request.setDateFrom(LocalDate.now().minusMonths(6));
      request.setDateTo(LocalDate.now());
      request.setReason("test");

      ResponseStatusException exception = assertThrows(
          ResponseStatusException.class,
          () -> cashAuditService.requestPurge(request));

      assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }
}
