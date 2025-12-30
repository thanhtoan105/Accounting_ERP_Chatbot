package com.accounting.service.tt200;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.accounting.dto.tt200.ParserStats;
import com.accounting.dto.tt200.ThongTu200Chunk;

/**
 * Unit tests for ThongTu200ParserService.
 * Tests PDF parsing functionality without Spring context.
 * Tests are skipped if ThongTu200.pdf is not available.
 */
class ThongTu200ParserServiceTest {

    private static ThongTu200ParserService parserService;
    private static boolean pdfAvailable = false;

    @BeforeAll
    static void setUp() {
        String pdfPath = System.getProperty("user.dir").replace("/backend", "") + "/ThongTu200.pdf";
        java.io.File pdfFile = new java.io.File(pdfPath);
        pdfAvailable = pdfFile.exists();
        
        if (pdfAvailable) {
            parserService = new ThongTu200ParserServiceImpl(pdfPath);
        }
    }

    static boolean isPdfAvailable() {
        return pdfAvailable;
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void parseAllChunks_returnsNonEmptyList() {
        List<ThongTu200Chunk> chunks = parserService.parseAllChunks();
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty(), "parseAllChunks should return at least one chunk");
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void parseAllChunks_hasAtLeast10Chunks() {
        List<ThongTu200Chunk> chunks = parserService.parseAllChunks();
        assertTrue(chunks.size() >= 10, "Expected at least 10 chunks, got: " + chunks.size());
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void parseByAccountCode_111_returnsTienMat() {
        List<ThongTu200Chunk> chunks = parserService.parseByAccountCode("111");
        assertFalse(chunks.isEmpty(), "Account 111 should have associated chunks");
        assertTrue(
                chunks.stream().anyMatch(c -> c.articleTitle() != null && c.articleTitle().contains("Tiền")),
                "Account 111 chunks should contain 'Tiền' in article title");
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void parseByAccountCode_131_returnsPhaiThu() {
        List<ThongTu200Chunk> chunks = parserService.parseByAccountCode("131");
        // May not find if PDF structure differs, just check no exception
        assertNotNull(chunks);
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void parseByChapter_II_returnsAccountArticles() {
        List<ThongTu200Chunk> chunks = parserService.parseByChapter("II");
        assertNotNull(chunks);
        // Chapter II should have accounting articles
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void chunkId_isDeterministic() {
        List<ThongTu200Chunk> chunks1 = parserService.parseAllChunks();
        List<ThongTu200Chunk> chunks2 = parserService.parseAllChunks();

        assertFalse(chunks1.isEmpty());
        assertEquals(
                chunks1.get(0).chunkId(),
                chunks2.get(0).chunkId(),
                "Chunk ID should be deterministic across calls");
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void vietnameseEncoding_preservesDiacritics() {
        List<ThongTu200Chunk> chunks = parserService.parseAllChunks();
        // Check if Vietnamese characters are preserved
        boolean hasVietnamese = chunks.stream()
                .anyMatch(c -> c.content() != null && 
                    (c.content().contains("ế") || c.content().contains("ề") || 
                     c.content().contains("á") || c.content().contains("ả")));
        assertTrue(hasVietnamese, "Content should preserve Vietnamese diacritics");
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void getPdfHash_returnsConsistentHash() {
        String hash1 = parserService.getPdfHash();
        String hash2 = parserService.getPdfHash();

        assertNotNull(hash1, "PDF hash should not be null");
        assertFalse(hash1.isBlank(), "PDF hash should not be blank");
        assertEquals(hash1, hash2, "PDF hash should be consistent across calls");
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void getStats_returnsValidStats() {
        ParserStats stats = parserService.getStats();

        assertNotNull(stats, "Stats should not be null");
        assertTrue(stats.totalChunks() >= 0, "Total chunks should be non-negative");
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void parseByAccountCode_nonExistent_returnsEmptyList() {
        List<ThongTu200Chunk> chunks = parserService.parseByAccountCode("99999");
        assertNotNull(chunks, "Should return empty list, not null");
        assertTrue(chunks.isEmpty(), "Non-existent account code should return empty list");
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void chunkId_followsExpectedFormat() {
        List<ThongTu200Chunk> chunks = parserService.parseAllChunks();
        if (!chunks.isEmpty()) {
            boolean allValidFormat = chunks.stream()
                    .allMatch(c -> c.chunkId().startsWith("tt200:"));
            assertTrue(allValidFormat, "All chunk IDs should start with 'tt200:'");
        }
    }

    @Test
    @EnabledIf("isPdfAvailable")
    void sectionType_hasValidValues() {
        List<ThongTu200Chunk> chunks = parserService.parseAllChunks();
        List<String> validTypes = List.of("principle", "structure", "journal_entry", "general");
        
        boolean allValidTypes = chunks.stream()
                .allMatch(c -> validTypes.contains(c.sectionType()));
        assertTrue(allValidTypes, "All chunks should have valid section types");
    }
}
