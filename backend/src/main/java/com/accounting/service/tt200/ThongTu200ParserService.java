package com.accounting.service.tt200;

import java.util.List;

import com.accounting.dto.tt200.ParserStats;
import com.accounting.dto.tt200.ThongTu200Chunk;

/**
 * Service for parsing ThongTu200 (Vietnam's Circular 200 accounting regulation) PDF.
 * Extracts structured chunks suitable for embedding and RAG retrieval.
 */
public interface ThongTu200ParserService {

    /**
     * Parse all chunks from the ThongTu200 PDF.
     *
     * @return List of all parsed chunks
     */
    List<ThongTu200Chunk> parseAllChunks();

    /**
     * Parse chunks related to a specific account code.
     *
     * @param accountCode The account code to filter by (e.g., "111", "131")
     * @return List of chunks mentioning this account code
     */
    List<ThongTu200Chunk> parseByAccountCode(String accountCode);

    /**
     * Parse chunks from a specific chapter.
     *
     * @param chapterId The chapter ID (e.g., "II" for accounting accounts chapter)
     * @return List of chunks from this chapter
     */
    List<ThongTu200Chunk> parseByChapter(String chapterId);

    /**
     * Get the SHA-256 hash of the source PDF for version tracking.
     *
     * @return Hash string of the PDF file
     */
    String getPdfHash();

    /**
     * Get parsing statistics.
     *
     * @return Statistics about the parsed content
     */
    ParserStats getStats();
}
