package com.accounting.controller.ar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.accounting.dto.ARReconciliationImportDTO;
import com.accounting.dto.ARStatementDisputeDTO;
import com.accounting.dto.ARStatementHistoryDTO;
import com.accounting.dto.ARStatementSummaryDTO;
import com.accounting.entity.ARStatementHistory;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ARDisputeService;
import com.accounting.service.ARReconciliationImportService;
import com.accounting.service.ARStatementService;
import com.accounting.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ARStatementControllerTest {

  @Mock
  private ARStatementService statementService;
  @Mock
  private ARReconciliationImportService reconciliationService;
  @Mock
  private ARDisputeService disputeService;
  @Mock
  private AuditService auditService;
  @Mock
  private HttpServletRequest httpRequest;

  @InjectMocks
  private ARStatementController controller;

  private static final Long COMPANY_ID = 1L;
  private static final Long CUSTOMER_ID = 200L;

  @BeforeEach
  void setUp() {
    CompanyContext.setCompanyId(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void testGetStatement_Success() {
    // Arrange
    ARStatementSummaryDTO statement = new ARStatementSummaryDTO();
    statement.setCustomerId(CUSTOMER_ID);
    statement.setCustomerName("Test Customer");

    when(statementService.getStatement(
        anyLong(),
        any(ARStatementHistory.StatementFormat.class),
        any()))
        .thenReturn(statement);

    try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
      mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

      // Act
      ResponseEntity<?> response = controller.getStatement(CUSTOMER_ID, "SUMMARY", null, httpRequest);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
    }
  }

  @Test
  void testExportStatement_Success() {
    // Arrange
    byte[] exportData = "test export data".getBytes();
    UUID historyId = UUID.randomUUID();
    ARStatementService.ExportResult exportResult = new ARStatementService.ExportResult(exportData, historyId);
    when(statementService.exportStatement(
        anyLong(),
        anyString(),
        any(ARStatementHistory.StatementFormat.class),
        any()))
        .thenReturn(exportResult);

    try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
      mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

      // Act
      ResponseEntity<byte[]> response = controller.exportStatement(CUSTOMER_ID, "EXCEL", "SUMMARY", null, httpRequest);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      assertTrue(response.getHeaders().containsKey("Content-Type"));
    }
  }

  @Test
  void testSendStatement_Success() {
    // Arrange
    String email = "customer@test.com";
    doNothing()
        .when(statementService)
        .sendStatementToCustomer(
            anyLong(),
            anyString(),
            any(ARStatementHistory.StatementFormat.class),
            any());

    try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
      mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

      // Act
      ResponseEntity<Map<String, String>> response = controller.sendStatement(CUSTOMER_ID, email, "SUMMARY", null,
          httpRequest);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertEquals("Statement sent successfully", response.getBody().get("message"));
      assertEquals(email, response.getBody().get("recipient"));
    }
  }

  @Test
  void testImportReconciliation_Success() {
    // Arrange
    ARReconciliationImportDTO result = new ARReconciliationImportDTO();
    result.setCustomerId(CUSTOMER_ID);
    result.setMatchedCount(5);
    result.setMismatchCount(2);

    MultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "data".getBytes());
    when(reconciliationService.importReconciliation(anyLong(), any()))
        .thenReturn(result);

    try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
      mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

      // Act
      ResponseEntity<ARReconciliationImportDTO> response = controller.importReconciliation(CUSTOMER_ID, file,
          httpRequest);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertEquals(CUSTOMER_ID, response.getBody().getCustomerId());
      assertEquals(5, response.getBody().getMatchedCount());
      assertEquals(2, response.getBody().getMismatchCount());
    }
  }

  @Test
  void testGetStatementHistory_Success() {
    // Arrange
    ARStatementHistoryDTO history = new ARStatementHistoryDTO();
    history.setId(UUID.randomUUID());
    history.setCustomerId(CUSTOMER_ID);

    Page<ARStatementHistoryDTO> historyPage = new PageImpl<>(Arrays.asList(history));
    when(statementService.getStatementHistory(anyLong(), any()))
        .thenReturn(historyPage);

    // Act
    ResponseEntity<Map<String, Object>> response = controller.getStatementHistory(CUSTOMER_ID, 0, 20);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody().containsKey("history"));
    assertEquals(1, ((List<?>) response.getBody().get("history")).size());
  }

  @Test
  void testGetDisputes_Success() {
    // Arrange
    ARStatementDisputeDTO dispute = new ARStatementDisputeDTO();
    dispute.setId(UUID.randomUUID());
    dispute.setInvoiceNumber("INV-001");

    Page<ARStatementDisputeDTO> disputesPage = new PageImpl<>(Arrays.asList(dispute));
    when(disputeService.getDisputes(eq(null), eq(null), eq(null), eq(null), any()))
        .thenReturn(disputesPage);

    // Act
    ResponseEntity<Map<String, Object>> response = controller.getDisputes(0, 20, null, null, null, null);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody().containsKey("disputes"));
    assertEquals(1, ((List<?>) response.getBody().get("disputes")).size());
  }

  @Test
  void testResolveDispute_Success() {
    // Arrange
    UUID disputeId = UUID.randomUUID();
    Map<String, String> request = new HashMap<>();
    request.put("resolutionNotes", "Resolved after review");

    doNothing().when(disputeService).resolveDispute(eq(disputeId), anyString());

    try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
      mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

      // Act
      ResponseEntity<Map<String, String>> response = controller.resolveDispute(disputeId, request, httpRequest);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertEquals("Dispute resolved successfully", response.getBody().get("message"));
      assertEquals("RESOLVED", response.getBody().get("status"));
    }
  }
}
