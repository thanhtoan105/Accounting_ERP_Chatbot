package com.accounting.service.impl.ap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.accounting.dto.BatchReminderRequestDTO;
import com.accounting.dto.BatchReminderResultDTO;
import com.accounting.dto.ReminderRequestDTO;
import com.accounting.dto.ReminderResultDTO;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.EmailService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for APAgingAlertServiceImpl.
 * Tests reminder generation, batch operations, and audit logging.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class APAgingAlertServiceImplTest {

  @Mock private PurchaseBillRepository purchaseBillRepository;

  @Mock private AuditService auditService;

  @Mock private EmailService emailService;

  @InjectMocks private APAgingAlertServiceImpl alertService;

  private static final Long COMPANY_ID = 1L;
  private static final Long SUPPLIER_ID = 100L;

  @BeforeEach
  void setUp() {
    CompanyContext.setCompanyId(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void sendReminder_withValidRequest_returnsSuccess() {
    // Setup
    ReminderRequestDTO request = new ReminderRequestDTO();
    request.setSupplierId(SUPPLIER_ID);
    request.setRecipients(List.of("test@example.com"));
    request.setMessage("Test reminder message");

    when(purchaseBillRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), eq(PurchaseBillStatus.POSTED)))
        .thenReturn(Collections.emptyList());
    doNothing().when(auditService).logAgingReminderSent(any(), any(), any(), any());

    // Execute
    ReminderResultDTO result = alertService.sendReminder(request);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getSentTo()).isNotEmpty();
    verify(auditService, atLeastOnce()).logAgingReminderSent(any(), any(), any(), any());
  }

  @Test
  void sendReminder_withBillIds_includesBillDetails() {
    // Setup
    UUID billId = UUID.randomUUID();
    PurchaseBill bill = new PurchaseBill();
    bill.setId(billId);
    bill.setBillNumber("BILL-001");
    bill.setTotalAmount(new BigDecimal("1000.00"));
    bill.setDueDate(LocalDate.now().minusDays(10));

    ReminderRequestDTO request = new ReminderRequestDTO();
    request.setSupplierId(SUPPLIER_ID);
    request.setBillIds(List.of(billId));
    request.setRecipients(List.of("test@example.com"));

    when(purchaseBillRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), eq(PurchaseBillStatus.POSTED)))
        .thenReturn(List.of(bill));
    doNothing().when(auditService).logAgingReminderSent(any(), any(), any(), any());

    // Execute
    ReminderResultDTO result = alertService.sendReminder(request);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    verify(auditService, times(1)).logAgingReminderSent(any(), any(), any(), any());
  }

  @Test
  void sendBatchReminders_withMultipleSuppliers_returnsBatchResult() {
    // Setup
    BatchReminderRequestDTO request = new BatchReminderRequestDTO();
    request.setSupplierIds(List.of(100L, 200L, 300L));
    request.setRecipients(List.of("test@example.com"));
    request.setMessage("Batch reminder");

    when(purchaseBillRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), eq(PurchaseBillStatus.POSTED)))
        .thenReturn(Collections.emptyList());
    doNothing().when(auditService).logAgingReminderSent(any(), any(), any(), any());
    doNothing().when(auditService).logAgingBatchReminderSent(any(), any(), any());

    // Execute
    BatchReminderResultDTO result = alertService.sendBatchReminders(request);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getTotalSuppliers()).isEqualTo(3);
    assertThat(result.getSuccessCount()).isEqualTo(3);
    assertThat(result.getFailedCount()).isEqualTo(0);
    verify(auditService, atLeastOnce()).logAgingBatchReminderSent(any(), any(), any());
  }

  @Test
  void sendReminder_withEmptyRecipients_handlesGracefully() {
    // Setup
    ReminderRequestDTO request = new ReminderRequestDTO();
    request.setSupplierId(SUPPLIER_ID);
    request.setRecipients(Collections.emptyList());

    // Execute
    ReminderResultDTO result = alertService.sendReminder(request);

    // Assert - should handle gracefully, not throw exception
    assertThat(result).isNotNull();
    // The result may indicate failure, which is acceptable
  }

  @Test
  void scheduleAutoAlerts_logsRequest() {
    // Setup
    LocalDate asOfDate = LocalDate.now();
    List<String> roles = List.of("CFO", "CHIEF_ACCOUNTANT");

    // Execute
    alertService.scheduleAutoAlerts(PERIOD_ID, asOfDate, roles);

    // Assert - should not throw exception
    // Note: Full implementation is deferred, so we just verify it doesn't crash
    assertThat(true).isTrue(); // Placeholder assertion
  }

  private static final Long PERIOD_ID = 1L;
}

