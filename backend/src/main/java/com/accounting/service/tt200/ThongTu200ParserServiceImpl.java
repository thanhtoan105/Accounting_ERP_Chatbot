package com.accounting.service.tt200;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accounting.dto.tt200.ParserStats;
import com.accounting.dto.tt200.ThongTu200Chunk;

@Service
public class ThongTu200ParserServiceImpl implements ThongTu200ParserService {

    private static final Logger log = LoggerFactory.getLogger(ThongTu200ParserServiceImpl.class);

    private static final int MAX_CHUNK_SIZE = 1500;

    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
        "Chương\\s+([IVX]+)\\s*\\n\\s*([A-ZĐƯÀÁẢÃẠÈÉẺẼẸÌÍỈĨỊÒÓỎÕỌÙÚỦŨỤỲÝỶỸỴÂĂÊÔƠƯ\\s]+)",
        Pattern.UNICODE_CASE
    );

    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
        "Điều\\s+(\\d+)\\.\\s*(.+?)(?=\\n)",
        Pattern.UNICODE_CASE
    );

    private static final Pattern ACCOUNT_IN_TITLE_PATTERN = Pattern.compile(
        "Tài khoản\\s+(\\d{3})\\s*-\\s*(.+)",
        Pattern.UNICODE_CASE
    );

    private static final Pattern SUB_ACCOUNT_PATTERN = Pattern.compile(
        "Tài khoản\\s+(\\d{4})\\s*-",
        Pattern.UNICODE_CASE
    );

    private static final Pattern PRINCIPLE_SECTION = Pattern.compile(
        "(?:1\\.\\s*)?Nguyên tắc (?:kế toán|hạch toán)(.+?)(?=(?:2\\.\\s*)?Kết cấu|Điều\\s+\\d+|$)",
        Pattern.DOTALL | Pattern.UNICODE_CASE
    );

    private static final Pattern STRUCTURE_SECTION = Pattern.compile(
        "(?:2\\.\\s*)?Kết cấu và nội dung(.+?)(?=(?:3\\.\\s*)?Phương pháp|Điều\\s+\\d+|$)",
        Pattern.DOTALL | Pattern.UNICODE_CASE
    );

    private static final Pattern JOURNAL_SECTION = Pattern.compile(
        "(?:3\\.\\s*)?Phương pháp (?:kế toán|hạch toán)(.+?)(?=Điều\\s+\\d+|Chương\\s+[IVX]+|$)",
        Pattern.DOTALL | Pattern.UNICODE_CASE
    );

    private final String pdfPath;
    private final ConcurrentHashMap<String, List<ThongTu200Chunk>> cache = new ConcurrentHashMap<>();

    /**
     * Constructor for Spring DI with configurable PDF path.
     */
    public ThongTu200ParserServiceImpl(
            @Value("${app.tt200.pdf-path:ThongTu200.pdf}") String pdfPath) {
        this.pdfPath = pdfPath;
    }
    private String cachedPdfHash;
    private ParserStats cachedStats;

    @Override
    public List<ThongTu200Chunk> parseAllChunks() {
        String hash = getPdfHash();
        return cache.computeIfAbsent(hash, k -> doParse());
    }

    @Override
    public List<ThongTu200Chunk> parseByChapter(String chapterNumber) {
        return parseAllChunks().stream()
            .filter(c -> c.chapterNumber().equals(chapterNumber))
            .toList();
    }

    @Override
    public List<ThongTu200Chunk> parseByAccountCode(String accountCodePrefix) {
        return parseAllChunks().stream()
            .filter(c -> c.accountCode() != null && c.accountCode().startsWith(accountCodePrefix))
            .toList();
    }

    @Override
    public String getPdfHash() {
        if (cachedPdfHash != null) {
            return cachedPdfHash;
        }
        try {
            Path path = Path.of(pdfPath);
            if (!Files.exists(path)) {
                path = Path.of(System.getProperty("user.dir"), pdfPath);
            }
            byte[] bytes = Files.readAllBytes(path);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            cachedPdfHash = HexFormat.of().formatHex(digest);
            return cachedPdfHash;
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Failed to calculate PDF hash", e);
            return "unknown";
        }
    }

    @Override
    public ParserStats getStats() {
        if (cachedStats == null) {
            parseAllChunks();
        }
        return cachedStats;
    }

    private List<ThongTu200Chunk> doParse() {
        List<ThongTu200Chunk> chunks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> chunksPerChapter = new HashMap<>();
        int articleCount = 0;

        String pdfHash = getPdfHash();

        try {
            Path path = Path.of(pdfPath);
            if (!Files.exists(path)) {
                path = Path.of(System.getProperty("user.dir"), pdfPath);
            }

            try (InputStream is = Files.newInputStream(path);
                 PDDocument document = Loader.loadPDF(is.readAllBytes())) {

                PDFTextStripper stripper = new PDFTextStripper();
                String fullText = stripper.getText(document);
                int totalPages = document.getNumberOfPages();

                String currentChapter = "";
                String currentChapterTitle = "";

                Matcher chapterMatcher = CHAPTER_PATTERN.matcher(fullText);
                List<int[]> chapterPositions = new ArrayList<>();
                while (chapterMatcher.find()) {
                    chapterPositions.add(new int[]{chapterMatcher.start(), chapterMatcher.end()});
                }

                Matcher articleMatcher = ARTICLE_PATTERN.matcher(fullText);
                while (articleMatcher.find()) {
                    int articlePos = articleMatcher.start();
                    int articleNum = Integer.parseInt(articleMatcher.group(1));
                    String articleTitle = articleMatcher.group(2).trim();
                    articleCount++;

                    for (int[] chPos : chapterPositions) {
                        if (chPos[0] < articlePos) {
                            Matcher cm = CHAPTER_PATTERN.matcher(fullText.substring(chPos[0], chPos[1]));
                            if (cm.find()) {
                                currentChapter = cm.group(1);
                                currentChapterTitle = cm.group(2).trim();
                            }
                        }
                    }

                    String accountCode = null;
                    Matcher accountMatcher = ACCOUNT_IN_TITLE_PATTERN.matcher(articleTitle);
                    if (accountMatcher.find()) {
                        accountCode = accountMatcher.group(1);
                    }

                    int articleEnd = findArticleEnd(fullText, articleMatcher.end());
                    String articleContent = fullText.substring(articleMatcher.end(), articleEnd);

                    List<String> subAccounts = extractSubAccounts(articleContent);

                    int pageStart = estimatePage(articlePos, fullText.length(), totalPages);
                    int pageEnd = estimatePage(articleEnd, fullText.length(), totalPages);

                    chunks.addAll(extractSections(
                        currentChapter, currentChapterTitle,
                        articleNum, articleTitle, accountCode, subAccounts,
                        articleContent, pageStart, pageEnd, pdfHash
                    ));

                    chunksPerChapter.merge(currentChapter, 1, Integer::sum);
                }
            }

            if (articleCount < 80) {
                warnings.add("Expected ~96 articles, found " + articleCount);
            }

            for (ThongTu200Chunk chunk : chunks) {
                if (chunk.content() == null || chunk.content().isBlank()) {
                    warnings.add("Empty content for chunk: " + chunk.chunkId());
                }
            }

            cachedStats = new ParserStats(
                chunks.size(),
                chunksPerChapter.size(),
                articleCount,
                chunksPerChapter,
                warnings,
                pdfHash,
                Instant.now()
            );

            log.info("Parsed ThongTu200: {} chunks, {} articles, {} chapters",
                chunks.size(), articleCount, chunksPerChapter.size());

        } catch (IOException e) {
            log.error("Failed to parse ThongTu200 PDF", e);
            warnings.add("Parse error: " + e.getMessage());
            cachedStats = new ParserStats(0, 0, 0, Map.of(), warnings, pdfHash, Instant.now());
        }

        return chunks;
    }

    private int findArticleEnd(String text, int start) {
        Matcher nextArticle = ARTICLE_PATTERN.matcher(text);
        if (nextArticle.find(start)) {
            return nextArticle.start();
        }
        Matcher nextChapter = CHAPTER_PATTERN.matcher(text);
        if (nextChapter.find(start)) {
            return nextChapter.start();
        }
        return text.length();
    }

    private List<String> extractSubAccounts(String content) {
        List<String> subAccounts = new ArrayList<>();
        Matcher matcher = SUB_ACCOUNT_PATTERN.matcher(content);
        while (matcher.find()) {
            String code = matcher.group(1);
            if (!subAccounts.contains(code)) {
                subAccounts.add(code);
            }
        }
        return subAccounts;
    }

    private int estimatePage(int charPos, int totalChars, int totalPages) {
        if (totalChars == 0) return 1;
        return Math.max(1, Math.min(totalPages, (int) ((double) charPos / totalChars * totalPages) + 1));
    }

    private List<ThongTu200Chunk> extractSections(
        String chapterNumber, String chapterTitle,
        int articleNumber, String articleTitle,
        String accountCode, List<String> subAccounts,
        String content, int pageStart, int pageEnd, String pdfHash
    ) {
        List<ThongTu200Chunk> chunks = new ArrayList<>();

        String principleContent = extractSection(content, PRINCIPLE_SECTION);
        String structureContent = extractSection(content, STRUCTURE_SECTION);
        String journalContent = extractSection(content, JOURNAL_SECTION);

        if (principleContent != null && !principleContent.isBlank()) {
            chunks.addAll(splitIntoChunks(
                chapterNumber, chapterTitle, articleNumber, articleTitle,
                accountCode, subAccounts, principleContent, "principle",
                pageStart, pageEnd, pdfHash
            ));
        }

        if (structureContent != null && !structureContent.isBlank()) {
            chunks.addAll(splitIntoChunks(
                chapterNumber, chapterTitle, articleNumber, articleTitle,
                accountCode, subAccounts, structureContent, "structure",
                pageStart, pageEnd, pdfHash
            ));
        }

        if (journalContent != null && !journalContent.isBlank()) {
            chunks.addAll(splitIntoChunks(
                chapterNumber, chapterTitle, articleNumber, articleTitle,
                accountCode, subAccounts, journalContent, "journal_entry",
                pageStart, pageEnd, pdfHash
            ));
        }

        if (chunks.isEmpty() && !content.isBlank()) {
            chunks.addAll(splitIntoChunks(
                chapterNumber, chapterTitle, articleNumber, articleTitle,
                accountCode, subAccounts, content.trim(), "general",
                pageStart, pageEnd, pdfHash
            ));
        }

        return chunks;
    }

    private String extractSection(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private List<ThongTu200Chunk> splitIntoChunks(
        String chapterNumber, String chapterTitle,
        int articleNumber, String articleTitle,
        String accountCode, List<String> subAccounts,
        String content, String sectionType,
        int pageStart, int pageEnd, String pdfHash
    ) {
        List<ThongTu200Chunk> chunks = new ArrayList<>();
        String accountPart = accountCode != null ? accountCode : "general";
        String baseId = String.format("tt200:%s:%d:%s:%s",
            chapterNumber, articleNumber, accountPart, sectionType);

        if (content.length() <= MAX_CHUNK_SIZE) {
            chunks.add(new ThongTu200Chunk(
                baseId, chapterNumber, chapterTitle,
                articleNumber, articleTitle, accountCode, subAccounts,
                content, sectionType, pageStart, pageEnd, pdfHash
            ));
        } else {
            String[] paragraphs = content.split("\\n\\n+");
            StringBuilder currentChunk = new StringBuilder();
            int partNum = 1;

            for (String para : paragraphs) {
                if (currentChunk.length() + para.length() > MAX_CHUNK_SIZE && currentChunk.length() > 0) {
                    chunks.add(new ThongTu200Chunk(
                        baseId + ":" + partNum, chapterNumber, chapterTitle,
                        articleNumber, articleTitle, accountCode, subAccounts,
                        currentChunk.toString().trim(), sectionType,
                        pageStart, pageEnd, pdfHash
                    ));
                    currentChunk = new StringBuilder();
                    partNum++;
                }
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(para);
            }

            if (currentChunk.length() > 0) {
                chunks.add(new ThongTu200Chunk(
                    baseId + (partNum > 1 ? ":" + partNum : ""),
                    chapterNumber, chapterTitle,
                    articleNumber, articleTitle, accountCode, subAccounts,
                    currentChunk.toString().trim(), sectionType,
                    pageStart, pageEnd, pdfHash
                ));
            }
        }

        return chunks;
    }
}
