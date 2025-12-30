package com.accounting.dto.tt200;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Statistics about parsed ThongTu200 content.
 *
 * @param totalChunks       Total number of chunks parsed
 * @param chapterCount      Number of distinct chapters
 * @param articleCount      Number of distinct articles
 * @param chunksPerChapter  Breakdown of chunks by chapter
 * @param warnings          Any warnings during parsing
 * @param pdfHash           Hash of the source PDF
 * @param parsedAt          When the parsing was done
 */
public record ParserStats(
        int totalChunks,
        int chapterCount,
        int articleCount,
        Map<String, Integer> chunksPerChapter,
        List<String> warnings,
        String pdfHash,
        Instant parsedAt) {
}
