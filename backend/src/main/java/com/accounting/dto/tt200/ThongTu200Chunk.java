package com.accounting.dto.tt200;

import java.util.List;

/**
 * Represents a parsed chunk from ThongTu200 (Vietnam's Circular 200 accounting regulation).
 * Each chunk is a semantically meaningful section suitable for embedding.
 *
 * @param chunkId          Deterministic ID: tt200:{chapterNumber}:{articleNumber}:{accountCode}:{sectionType}
 * @param chapterNumber    Chapter number (e.g., "II" for accounting accounts chapter)
 * @param chapterTitle     Chapter title in Vietnamese
 * @param articleNumber    Article number within the chapter
 * @param articleTitle     Article title (e.g., "Tài khoản 111 - Tiền mặt")
 * @param accountCode      Account code this chunk relates to (e.g., "111")
 * @param subAccounts      Sub-account codes mentioned (e.g., ["1111", "1112"])
 * @param content          The actual text content of this chunk
 * @param sectionType      Type of content: "principle", "structure", "journal_entry", "general"
 * @param pageStart        Page number where this chunk starts in the PDF
 * @param pageEnd          Page number where this chunk ends in the PDF
 * @param pdfHash          Hash of the source PDF for version tracking
 */
public record ThongTu200Chunk(
        String chunkId,
        String chapterNumber,
        String chapterTitle,
        int articleNumber,
        String articleTitle,
        String accountCode,
        List<String> subAccounts,
        String content,
        String sectionType,
        int pageStart,
        int pageEnd,
        String pdfHash) {

    public ThongTu200Chunk {
        if (chunkId == null || chunkId.isBlank()) {
            throw new IllegalArgumentException("chunkId is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
    }
}
