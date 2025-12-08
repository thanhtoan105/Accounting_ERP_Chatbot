package com.accounting.service.impl.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.report.MappingVersionDTO;
import com.accounting.dto.report.ReportMappingDTO;
import com.accounting.entity.AuditLog;
import com.accounting.entity.User;
import com.accounting.entity.report.ReportMapping;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.report.ReportMappingRepository;
import com.accounting.repository.report.ReportSnapshotRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for ReportMappingServiceImpl.
 * Tests mapping CRUD, versioning, history, and rollback functionality.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportMappingServiceImplTest {

  @Mock
  private ReportMappingRepository reportMappingRepository;

  @Mock
  private ReportSnapshotRepository reportSnapshotRepository;

  @Mock
  private AuditLogRepository auditLogRepository;

  @Mock
  private UserRepository userRepository;

  private ReportMappingServiceImpl service;
  private MockedStatic<CompanyContext> companyContextMock;
  private MockedStatic<SecurityUtils> securityUtilsMock;

  private static final Long COMPANY_ID = 1L;
  private static final Long USER_ID = 10L;
  private static final String REPORT_TYPE = "B01";
  private static final String LINE_CODE = "110";

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();
    service = new ReportMappingServiceImpl(
        reportMappingRepository,
        reportSnapshotRepository,
        auditLogRepository,
        userRepository,
        objectMapper);

    companyContextMock = mockStatic(CompanyContext.class);
    companyContextMock.when(CompanyContext::getCompanyId).thenReturn(COMPANY_ID);

    securityUtilsMock = mockStatic(SecurityUtils.class);
    securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
  }

  @AfterEach
  void tearDown() {
    companyContextMock.close();
    securityUtilsMock.close();
  }

  // ==================== Helper Methods ====================

  private ReportMapping createMapping(String lineCode, String accountPattern, int version, boolean isCurrent) {
    ReportMapping mapping = new ReportMapping();
    mapping.setId(UUID.randomUUID());
    mapping.setCompanyId(COMPANY_ID);
    mapping.setReportType(REPORT_TYPE);
    mapping.setLineCode(lineCode);
    mapping.setLineName("Test Line " + lineCode);
    mapping.setLineNameEnglish("Test Line " + lineCode);
    mapping.setAccountPattern(accountPattern);
    mapping.setOperator("SUM");
    mapping.setSignModifier(1);
    mapping.setDisplayOrder(1);
    mapping.setLevel(1);
    mapping.setIsCalculated(false);
    mapping.setVersion(version);
    mapping.setIsCurrent(isCurrent);
    mapping.setCreatedBy(USER_ID);
    mapping.setCreatedAt(Instant.now());
    return mapping;
  }

  private User createUser() {
    User user = new User();
    user.setId(USER_ID);
    user.setFullName("Test User");
    user.setEmail("test@example.com");
    return user;
  }

  // ==================== Test Cases ====================

  @Nested
  @DisplayName("Get Mappings")
  class GetMappingsTests {

    @Test
    @DisplayName("Should return all current mappings for report type")
    void shouldReturnAllCurrentMappings() {
      // Given
      List<ReportMapping> mappings = List.of(
          createMapping("100", "SUM(110,120)", 1, true),
          createMapping("110", "111*", 1, true),
          createMapping("120", "131*", 1, true));

      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, REPORT_TYPE))
          .thenReturn(mappings);

      // When
      List<ReportMappingDTO> result = service.getMappings(REPORT_TYPE);

      // Then
      assertNotNull(result);
      assertEquals(3, result.size());
      assertEquals("100", result.get(0).getLineCode());
      verify(reportMappingRepository).findCurrentByCompanyAndReportType(COMPANY_ID, REPORT_TYPE);
    }

    @Test
    @DisplayName("Should return empty list when no mappings exist")
    void shouldReturnEmptyListWhenNoMappings() {
      // Given
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, REPORT_TYPE))
          .thenReturn(List.of());

      // When
      List<ReportMappingDTO> result = service.getMappings(REPORT_TYPE);

      // Then
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should throw exception when company context missing")
    void shouldThrowExceptionWhenCompanyContextMissing() {
      // Given
      companyContextMock.when(CompanyContext::getCompanyId).thenReturn(null);

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.getMappings(REPORT_TYPE));
    }
  }

  @Nested
  @DisplayName("Get Single Mapping")
  class GetMappingTests {

    @Test
    @DisplayName("Should return mapping by report type and line code")
    void shouldReturnMappingByReportTypeAndLineCode() {
      // Given
      ReportMapping mapping = createMapping(LINE_CODE, "111*", 1, true);

      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.of(mapping));

      // When
      ReportMappingDTO result = service.getMapping(REPORT_TYPE, LINE_CODE);

      // Then
      assertNotNull(result);
      assertEquals(LINE_CODE, result.getLineCode());
      assertEquals("111*", result.getAccountPattern());
    }

    @Test
    @DisplayName("Should throw exception when mapping not found")
    void shouldThrowExceptionWhenMappingNotFound() {
      // Given
      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.empty());

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.getMapping(REPORT_TYPE, LINE_CODE));
    }
  }

  @Nested
  @DisplayName("Update Mapping")
  class UpdateMappingTests {

    @Test
    @DisplayName("Should create new version when updating mapping")
    void shouldCreateNewVersionWhenUpdatingMapping() {
      // Given
      ReportMapping currentMapping = createMapping(LINE_CODE, "111*", 1, true);
      ReportMapping savedMapping = createMapping(LINE_CODE, "111*,112*", 2, true);
      User user = createUser();

      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.of(currentMapping));
      when(reportMappingRepository.save(any(ReportMapping.class))).thenReturn(savedMapping);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

      // When
      ReportMappingDTO result = service.updateMapping(
          REPORT_TYPE, LINE_CODE, "111*,112*", "SUM", "Added 112* accounts");

      // Then
      assertNotNull(result);
      assertEquals(2, result.getVersion());

      // Verify old version marked as not current
      verify(reportMappingRepository).markAllVersionsAsNotCurrent(COMPANY_ID, REPORT_TYPE, LINE_CODE);

      // Verify audit log created
      verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should throw exception when reason is blank")
    void shouldThrowExceptionWhenReasonBlank() {
      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.updateMapping(REPORT_TYPE, LINE_CODE, "111*", "SUM", ""));
    }

    @Test
    @DisplayName("Should throw exception when reason is null")
    void shouldThrowExceptionWhenReasonNull() {
      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.updateMapping(REPORT_TYPE, LINE_CODE, "111*", "SUM", null));
    }

    @Test
    @DisplayName("Should throw exception when mapping not found")
    void shouldThrowExceptionWhenMappingNotFoundForUpdate() {
      // Given
      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.empty());

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.updateMapping(REPORT_TYPE, LINE_CODE, "111*", "SUM", "Test reason"));
    }

    @Test
    @DisplayName("Should log mapping change with diff")
    void shouldLogMappingChangeWithDiff() {
      // Given
      ReportMapping currentMapping = createMapping(LINE_CODE, "111*", 1, true);
      currentMapping.setOperator("SUM");
      ReportMapping savedMapping = createMapping(LINE_CODE, "111*,112*", 2, true);
      savedMapping.setOperator("DIFF");

      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.of(currentMapping));
      when(reportMappingRepository.save(any(ReportMapping.class))).thenReturn(savedMapping);

      ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);

      // When
      service.updateMapping(REPORT_TYPE, LINE_CODE, "111*,112*", "DIFF", "Changed pattern and operator");

      // Then
      verify(auditLogRepository).save(auditCaptor.capture());
      AuditLog capturedLog = auditCaptor.getValue();

      assertEquals(COMPANY_ID, capturedLog.getCompanyId());
      assertEquals(USER_ID, capturedLog.getUserId());
      assertEquals("REPORT_MAPPING", capturedLog.getEntityType());
      assertEquals("B01:110", capturedLog.getEntityId());
      assertEquals("UPDATE", capturedLog.getAction());
      assertNotNull(capturedLog.getChanges());
    }
  }

  @Nested
  @DisplayName("Get Mapping History")
  class GetMappingHistoryTests {

    @Test
    @DisplayName("Should return version history for mapping")
    void shouldReturnVersionHistoryForMapping() {
      // Given
      List<ReportMapping> versions = List.of(
          createMapping(LINE_CODE, "111*,112*,113*", 3, true),
          createMapping(LINE_CODE, "111*,112*", 2, false),
          createMapping(LINE_CODE, "111*", 1, false));
      User user = createUser();

      when(reportMappingRepository.findVersionHistory(COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(versions);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

      // When
      List<MappingVersionDTO> result = service.getMappingHistory(REPORT_TYPE, LINE_CODE);

      // Then
      assertNotNull(result);
      assertEquals(3, result.size());

      // First (most recent) should be current
      assertTrue(result.get(0).getIsCurrent());
      assertEquals(3, result.get(0).getVersion());

      // Others should not be current
      assertFalse(result.get(1).getIsCurrent());
      assertFalse(result.get(2).getIsCurrent());
    }

    @Test
    @DisplayName("Should return empty list when no history exists")
    void shouldReturnEmptyListWhenNoHistory() {
      // Given
      when(reportMappingRepository.findVersionHistory(COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(List.of());

      // When
      List<MappingVersionDTO> result = service.getMappingHistory(REPORT_TYPE, LINE_CODE);

      // Then
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should calculate diff between versions")
    void shouldCalculateDiffBetweenVersions() {
      // Given
      ReportMapping v1 = createMapping(LINE_CODE, "111*", 1, false);
      ReportMapping v2 = createMapping(LINE_CODE, "111*,112*", 2, true);
      v2.setOperator("DIFF");

      List<ReportMapping> versions = List.of(v2, v1);

      when(reportMappingRepository.findVersionHistory(COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(versions);

      // When
      List<MappingVersionDTO> result = service.getMappingHistory(REPORT_TYPE, LINE_CODE);

      // Then
      assertNotNull(result);
      assertEquals(2, result.size());

      // Second version should have diff
      MappingVersionDTO secondVersion = result.get(1);
      assertNotNull(secondVersion.getDiff());
    }
  }

  @Nested
  @DisplayName("Rollback Mapping")
  class RollbackMappingTests {

    @Test
    @DisplayName("Should rollback to specified version")
    void shouldRollbackToSpecifiedVersion() {
      // Given
      ReportMapping currentMapping = createMapping(LINE_CODE, "111*,112*,113*", 3, true);
      ReportMapping historicalMapping = createMapping(LINE_CODE, "111*", 1, false);
      ReportMapping savedMapping = createMapping(LINE_CODE, "111*", 4, true);

      when(reportMappingRepository.findByCompanyIdAndReportTypeAndLineCodeAndVersion(
          COMPANY_ID, REPORT_TYPE, LINE_CODE, 1))
          .thenReturn(Optional.of(historicalMapping));
      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.of(currentMapping));
      when(reportMappingRepository.save(any(ReportMapping.class))).thenReturn(savedMapping);

      // When
      ReportMappingDTO result = service.rollbackMapping(REPORT_TYPE, LINE_CODE, 1);

      // Then
      assertNotNull(result);
      assertEquals(4, result.getVersion());
      assertEquals("111*", result.getAccountPattern());

      // Verify all versions marked as not current
      verify(reportMappingRepository).markAllVersionsAsNotCurrent(COMPANY_ID, REPORT_TYPE, LINE_CODE);

      // Verify audit log created
      verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should throw exception when historical version not found")
    void shouldThrowExceptionWhenHistoricalVersionNotFound() {
      // Given
      when(reportMappingRepository.findByCompanyIdAndReportTypeAndLineCodeAndVersion(
          COMPANY_ID, REPORT_TYPE, LINE_CODE, 99))
          .thenReturn(Optional.empty());

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.rollbackMapping(REPORT_TYPE, LINE_CODE, 99));
    }

    @Test
    @DisplayName("Should throw exception when current version not found")
    void shouldThrowExceptionWhenCurrentVersionNotFound() {
      // Given
      ReportMapping historicalMapping = createMapping(LINE_CODE, "111*", 1, false);

      when(reportMappingRepository.findByCompanyIdAndReportTypeAndLineCodeAndVersion(
          COMPANY_ID, REPORT_TYPE, LINE_CODE, 1))
          .thenReturn(Optional.of(historicalMapping));
      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.empty());

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.rollbackMapping(REPORT_TYPE, LINE_CODE, 1));
    }

    @Test
    @DisplayName("Should copy all fields from historical version")
    void shouldCopyAllFieldsFromHistoricalVersion() {
      // Given
      ReportMapping currentMapping = createMapping(LINE_CODE, "111*,112*", 2, true);
      ReportMapping historicalMapping = createMapping(LINE_CODE, "111*", 1, false);
      historicalMapping.setLineName("Historical Line Name");
      historicalMapping.setOperator("DIFF");
      historicalMapping.setSignModifier(-1);

      ArgumentCaptor<ReportMapping> mappingCaptor = ArgumentCaptor.forClass(ReportMapping.class);

      when(reportMappingRepository.findByCompanyIdAndReportTypeAndLineCodeAndVersion(
          COMPANY_ID, REPORT_TYPE, LINE_CODE, 1))
          .thenReturn(Optional.of(historicalMapping));
      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.of(currentMapping));
      when(reportMappingRepository.save(mappingCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

      // When
      service.rollbackMapping(REPORT_TYPE, LINE_CODE, 1);

      // Then
      ReportMapping savedMapping = mappingCaptor.getValue();
      assertEquals("111*", savedMapping.getAccountPattern());
      assertEquals("DIFF", savedMapping.getOperator());
      assertEquals(-1, savedMapping.getSignModifier());
      assertEquals("Historical Line Name", savedMapping.getLineName());
      assertEquals(3, savedMapping.getVersion()); // Should be current version + 1
      assertTrue(savedMapping.getIsCurrent());
      assertTrue(savedMapping.getChangeReason().contains("Rollback"));
    }
  }

  @Nested
  @DisplayName("Count Affected Snapshots")
  class CountAffectedSnapshotsTests {

    @Test
    @DisplayName("Should return count of snapshots using mapping version")
    void shouldReturnCountOfSnapshotsUsingMappingVersion() {
      // Given
      when(reportSnapshotRepository.findByMappingVersion(COMPANY_ID, REPORT_TYPE, 1))
          .thenReturn(List.of()); // Empty list = 0 snapshots

      // When
      long result = service.countAffectedSnapshots(REPORT_TYPE, 1);

      // Then
      assertEquals(0, result);
      verify(reportSnapshotRepository).findByMappingVersion(COMPANY_ID, REPORT_TYPE, 1);
    }
  }
}
