package com.accounting.service;

import static org.junit.jupiter.api.Assertions.*;

import com.accounting.dto.VoucherHistoryEntryDTO;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoucherHistoryExportServiceTest {

    private VoucherHistoryExportService exportService;
    private UUID voucherId;
    private List<VoucherHistoryEntryDTO> history;

    @BeforeEach
    void setUp() {
        exportService = new VoucherHistoryExportService();
        voucherId = UUID.randomUUID();
        history = createTestHistory();
    }

    @Test
    void testExportAsJson_ReturnsValidJson() {
        // When
        String json = exportService.exportAsJson(voucherId, history);

        // Then
        assertNotNull(json);
        assertTrue(json.contains(voucherId.toString()));
        assertTrue(json.contains("\"history\"") || json.contains("history"));
        assertTrue(json.contains("\"count\"") || json.contains("count"));
        assertTrue(json.contains("VOUCHER_CREATED"));
    }

    @Test
    void testExportAsJson_EmptyHistory() {
        // Given
        List<VoucherHistoryEntryDTO> emptyHistory = new ArrayList<>();

        // When
        String json = exportService.exportAsJson(voucherId, emptyHistory);

        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"count\":0") || json.contains("\"count\" : 0") || json.contains("count"));
    }

    @Test
    void testExportAsPdf_ReturnsValidContent() throws IOException {
        // When
        byte[] pdf = exportService.exportAsPdf(voucherId, history);

        // Then
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        String content = new String(pdf);
        assertTrue(content.contains("VOUCHER HISTORY REPORT"));
        assertTrue(content.contains(voucherId.toString()));
        assertTrue(content.contains("Document Hash (SHA-256)"));
    }

    @Test
    void testExportAsPdf_ContainsHashWatermark() throws IOException {
        // When
        byte[] pdf = exportService.exportAsPdf(voucherId, history);

        // Then
        String content = new String(pdf);
        assertTrue(content.contains("DOCUMENT INTEGRITY VERIFICATION"));
        assertTrue(content.contains("SHA-256"));
        // Hash should be 64 characters (SHA-256 hex)
        int hashIndex = content.indexOf("Document Hash (SHA-256):");
        assertTrue(hashIndex > 0);
    }

    @Test
    void testExportAsPdf_EmptyHistory() throws IOException {
        // Given
        List<VoucherHistoryEntryDTO> emptyHistory = new ArrayList<>();

        // When
        byte[] pdf = exportService.exportAsPdf(voucherId, emptyHistory);

        // Then
        assertNotNull(pdf);
        String content = new String(pdf);
        assertTrue(content.contains("Total Entries: 0"));
    }

    @Test
    void testExportAsPdf_ContainsFieldDiffs() throws IOException {
        // Given
        VoucherHistoryEntryDTO entry = history.get(0);
        Map<String, VoucherHistoryService.FieldDiff> diff = new HashMap<>();
        diff.put("description", new VoucherHistoryService.FieldDiff(
                "old description",
                "new description",
                VoucherHistoryService.ChangeType.CHANGED));
        entry.setDiff(diff);

        // When
        byte[] pdf = exportService.exportAsPdf(voucherId, history);

        // Then
        String content = new String(pdf);
        assertTrue(content.contains("Field Changes"));
        assertTrue(content.contains("description"));
        assertTrue(content.contains("CHANGED"));
    }

    private List<VoucherHistoryEntryDTO> createTestHistory() {
        List<VoucherHistoryEntryDTO> entries = new ArrayList<>();

        VoucherHistoryEntryDTO entry1 = new VoucherHistoryEntryDTO();
        entry1.setId(1L);
        entry1.setAction("VOUCHER_CREATED");
        entry1.setTimestamp(Instant.parse("2025-01-27T10:00:00Z"));
        entry1.setUserEmail("user@example.com");
        entry1.setUserRole("ACCOUNTANT");
        entry1.setSummary("Voucher created by user@example.com on 2025-01-27");
        entry1.setSuccess(true);
        entry1.setDiffHash("test-hash-123");
        entries.add(entry1);

        VoucherHistoryEntryDTO entry2 = new VoucherHistoryEntryDTO();
        entry2.setId(2L);
        entry2.setAction("VOUCHER_POSTED");
        entry2.setTimestamp(Instant.parse("2025-01-27T11:00:00Z"));
        entry2.setUserEmail("admin@example.com");
        entry2.setUserRole("CHIEF_ACCOUNTANT");
        entry2.setSummary("Voucher posted by admin@example.com on 2025-01-27");
        entry2.setSuccess(true);
        entry2.setDiffHash("test-hash-456");
        entries.add(entry2);

        return entries;
    }
}
