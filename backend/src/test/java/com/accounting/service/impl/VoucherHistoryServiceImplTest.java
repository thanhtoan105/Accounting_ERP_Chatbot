package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accounting.dto.VoucherHistoryEntryDTO;
import com.accounting.entity.AuditLog;
import com.accounting.repository.AuditLogRepository;
import com.accounting.security.CompanyContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class VoucherHistoryServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private VoucherHistoryServiceImpl voucherHistoryService;

    private ObjectMapper objectMapper;
    private UUID voucherId;
    private Long companyId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        voucherId = UUID.randomUUID();
        companyId = 1L;
        CompanyContext.setCompanyId(companyId);
    }

    @Test
    void testGetVoucherHistory_ReturnsHistoryEntries() {
        // Given
        AuditLog auditLog = createTestAuditLog();
        List<AuditLog> auditLogs = List.of(auditLog);

        when(auditLogRepository.findByEntityTypeAndEntityIdAndCompanyIdOrderByCreatedAtDesc(
                "VOUCHER", voucherId.toString(), companyId))
                .thenReturn(auditLogs);

        // When
        List<VoucherHistoryEntryDTO> history = voucherHistoryService.getVoucherHistory(voucherId);

        // Then
        assertNotNull(history);
        assertEquals(1, history.size());
        VoucherHistoryEntryDTO entry = history.get(0);
        assertEquals("VOUCHER_CREATED", entry.getAction());
        assertNotNull(entry.getSummary());
        assertNotNull(entry.getTimestamp());
    }

    @Test
    void testGetVoucherHistory_EmptyHistory() {
        // Given
        when(auditLogRepository.findByEntityTypeAndEntityIdAndCompanyIdOrderByCreatedAtDesc(
                "VOUCHER", voucherId.toString(), companyId))
                .thenReturn(new ArrayList<>());

        // When
        List<VoucherHistoryEntryDTO> history = voucherHistoryService.getVoucherHistory(voucherId);

        // Then
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    void testGenerateFieldDiff_AddedField() {
        // Given
        JsonNode before = objectMapper.createObjectNode();
        JsonNode after = objectMapper.createObjectNode().put("newField", "newValue");

        // When
        Map<String, com.accounting.service.VoucherHistoryService.FieldDiff> diff = voucherHistoryService
                .generateFieldDiff(before, after);

        // Then
        assertNotNull(diff);
        assertTrue(diff.containsKey("newField"));
        assertEquals(com.accounting.service.VoucherHistoryService.ChangeType.ADDED,
                diff.get("newField").getChangeType());
        assertNull(diff.get("newField").getBeforeValue());
        assertEquals("newValue", ((JsonNode) diff.get("newField").getAfterValue()).asText());
    }

    @Test
    void testGenerateFieldDiff_RemovedField() {
        // Given
        JsonNode before = objectMapper.createObjectNode().put("oldField", "oldValue");
        JsonNode after = objectMapper.createObjectNode();

        // When
        Map<String, com.accounting.service.VoucherHistoryService.FieldDiff> diff = voucherHistoryService
                .generateFieldDiff(before, after);

        // Then
        assertNotNull(diff);
        assertTrue(diff.containsKey("oldField"));
        assertEquals(com.accounting.service.VoucherHistoryService.ChangeType.REMOVED,
                diff.get("oldField").getChangeType());
        assertEquals("oldValue", ((JsonNode) diff.get("oldField").getBeforeValue()).asText());
        assertNull(diff.get("oldField").getAfterValue());
    }

    @Test
    void testGenerateFieldDiff_ChangedField() {
        // Given
        JsonNode before = objectMapper.createObjectNode().put("field", "oldValue");
        JsonNode after = objectMapper.createObjectNode().put("field", "newValue");

        // When
        Map<String, com.accounting.service.VoucherHistoryService.FieldDiff> diff = voucherHistoryService
                .generateFieldDiff(before, after);

        // Then
        assertNotNull(diff);
        assertTrue(diff.containsKey("field"));
        assertEquals(com.accounting.service.VoucherHistoryService.ChangeType.CHANGED,
                diff.get("field").getChangeType());
        assertEquals("oldValue", ((JsonNode) diff.get("field").getBeforeValue()).asText());
        assertEquals("newValue", ((JsonNode) diff.get("field").getAfterValue()).asText());
    }

    @Test
    void testGenerateFieldDiff_CreateCase() {
        // Given
        JsonNode before = null;
        JsonNode after = objectMapper.createObjectNode()
                .put("field1", "value1")
                .put("field2", "value2");

        // When
        Map<String, com.accounting.service.VoucherHistoryService.FieldDiff> diff = voucherHistoryService
                .generateFieldDiff(before, after);

        // Then
        assertNotNull(diff);
        assertEquals(2, diff.size());
        assertTrue(diff.containsKey("field1"));
        assertTrue(diff.containsKey("field2"));
        assertEquals(com.accounting.service.VoucherHistoryService.ChangeType.ADDED,
                diff.get("field1").getChangeType());
    }

    @Test
    void testGeneratePlainEnglishSummary_Created() {
        // Given
        AuditLog auditLog = createTestAuditLog();
        auditLog.setAction("VOUCHER_CREATED");
        auditLog.setEmail("user@example.com");
        auditLog.setCreatedAt(Instant.now());

        // When
        String summary = voucherHistoryService.generatePlainEnglishSummary(auditLog, null);

        // Then
        assertNotNull(summary);
        assertTrue(summary.contains("Voucher created"));
        assertTrue(summary.contains("user@example.com"));
    }

    @Test
    void testGeneratePlainEnglishSummary_Updated() {
        // Given
        AuditLog auditLog = createTestAuditLog();
        auditLog.setAction("VOUCHER_UPDATED");
        auditLog.setEmail("user@example.com");
        auditLog.setCreatedAt(Instant.now());

        var diff = java.util.Map.of(
                "description",
                new com.accounting.service.VoucherHistoryService.FieldDiff(
                        "old", "new", com.accounting.service.VoucherHistoryService.ChangeType.CHANGED));

        // When
        String summary = voucherHistoryService.generatePlainEnglishSummary(auditLog, diff);

        // Then
        assertNotNull(summary);
        assertTrue(summary.contains("Voucher updated"));
        assertTrue(summary.contains("description"));
    }

    @Test
    void testGeneratePlainEnglishSummary_Posted() {
        // Given
        AuditLog auditLog = createTestAuditLog();
        auditLog.setAction("VOUCHER_POSTED");
        auditLog.setEmail("user@example.com");
        auditLog.setCreatedAt(Instant.now());

        // When
        String summary = voucherHistoryService.generatePlainEnglishSummary(auditLog, null);

        // Then
        assertNotNull(summary);
        assertTrue(summary.contains("Voucher posted"));
    }

    private AuditLog createTestAuditLog() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setEntityType("VOUCHER");
        log.setEntityId(voucherId.toString());
        log.setCompanyId(companyId);
        log.setAction("VOUCHER_CREATED");
        log.setUserId(1L);
        log.setEmail("user@example.com");
        log.setActorRole("ACCOUNTANT");
        log.setSuccess(true);
        log.setCreatedAt(Instant.now());

        // Create changes JSON
        try {
            com.fasterxml.jackson.databind.node.ObjectNode changes = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ObjectNode after = objectMapper.createObjectNode()
                    .put("id", voucherId.toString())
                    .put("status", "draft");
            changes.set("after", after);
            log.setChanges(changes);

            // Create metadata with diffHash
            com.fasterxml.jackson.databind.node.ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("diffHash", "test-hash-123");
            log.setMetadata(metadata);
        } catch (Exception e) {
            // Ignore
        }

        return log;
    }
}
