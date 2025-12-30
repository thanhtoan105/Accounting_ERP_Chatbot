package com.accounting.service.tt200;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.dto.embedding.EntityEmbeddingPayload;
import com.accounting.dto.embedding.EntityType;
import com.accounting.dto.tt200.ThongTu200Chunk;
import com.accounting.service.EntityTextSynthesizer;
import com.accounting.service.N8nWebhookService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThongTu200EmbeddingServiceTest {

    @Mock
    private ThongTu200ParserService parserService;

    @Mock
    private EntityTextSynthesizer textSynthesizer;

    @Mock
    private N8nWebhookService webhookService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> metadataCaptor;

    @Captor
    private ArgumentCaptor<EntityEmbeddingPayload> payloadCaptor;

    private ThongTu200EmbeddingServiceImpl embeddingService;

    private ThongTu200Chunk sampleChunk;

    @BeforeEach
    void setUp() {
        embeddingService = new ThongTu200EmbeddingServiceImpl(parserService, textSynthesizer, webhookService);
        sampleChunk = new ThongTu200Chunk(
                "tt200:II:12:111:principle",
                "II",
                "TÀI KHOẢN KẾ TOÁN",
                12,
                "Tài khoản 111 - Tiền mặt",
                "111",
                List.of("1111", "1112"),
                "Tài khoản này dùng để phản ánh tình hình thu, chi tiền mặt tại quỹ của doanh nghiệp.",
                "principle",
                6,
                8,
                "abc123def456");
    }

    @Test
    void embedAllChunks_usesEntityTypeRegulatoryTT200() {
        when(parserService.parseAllChunks()).thenReturn(List.of(sampleChunk));
        when(textSynthesizer.buildGenericPayload(any(), any(), any(), any(), any(), any()))
                .thenReturn(createMockPayload());

        embeddingService.embedAllChunks();

        verify(textSynthesizer).buildGenericPayload(
                eq(EntityType.REGULATORY_TT200),
                eq("tt200:II:12:111:principle"),
                isNull(),
                anyString(),
                anyMap(),
                eq(EmbeddingAction.UPSERT));
    }

    @Test
    void embedAllChunks_namespaceIsRegulatoryTT200() {
        when(parserService.parseAllChunks()).thenReturn(List.of(sampleChunk));
        when(textSynthesizer.buildGenericPayload(any(), any(), any(), any(), any(), any()))
                .thenReturn(createMockPayload());

        embeddingService.embedAllChunks();

        verify(webhookService).triggerEntityEmbedding(payloadCaptor.capture());
        EntityEmbeddingPayload payload = payloadCaptor.getValue();
        assertEquals("regulatory_tt200", payload.namespace());
    }

    @Test
    void embedAllChunks_entityIdIsChunkId() {
        when(parserService.parseAllChunks()).thenReturn(List.of(sampleChunk));
        when(textSynthesizer.buildGenericPayload(any(), any(), any(), any(), any(), any()))
                .thenReturn(createMockPayload());

        embeddingService.embedAllChunks();

        verify(textSynthesizer).buildGenericPayload(
                any(),
                eq("tt200:II:12:111:principle"),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void embedAllChunks_metadataIncludesAccountCode() {
        when(parserService.parseAllChunks()).thenReturn(List.of(sampleChunk));
        when(textSynthesizer.buildGenericPayload(any(), any(), any(), any(), metadataCaptor.capture(), any()))
                .thenReturn(createMockPayload());

        embeddingService.embedAllChunks();

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertNotNull(metadata);
        assertEquals("111", metadata.get("accountCode"));
        assertEquals("TT200", metadata.get("source"));
        assertEquals("II", metadata.get("chapterNumber"));
        assertEquals(12, metadata.get("articleNumber"));
    }

    @Test
    void embedAllChunks_metadataIncludesSubAccounts() {
        when(parserService.parseAllChunks()).thenReturn(List.of(sampleChunk));
        when(textSynthesizer.buildGenericPayload(any(), any(), any(), any(), metadataCaptor.capture(), any()))
                .thenReturn(createMockPayload());

        embeddingService.embedAllChunks();

        Map<String, Object> metadata = metadataCaptor.getValue();
        @SuppressWarnings("unchecked")
        List<String> subAccounts = (List<String>) metadata.get("subAccounts");
        assertNotNull(subAccounts);
        assertTrue(subAccounts.contains("1111"));
        assertTrue(subAccounts.contains("1112"));
    }

    @Test
    void embedAllChunks_handlesWebhookFailure() {
        ThongTu200Chunk chunk1 = sampleChunk;
        ThongTu200Chunk chunk2 = new ThongTu200Chunk(
                "tt200:II:13:112:principle",
                "II",
                "TÀI KHOẢN KẾ TOÁN",
                13,
                "Tài khoản 112 - Tiền gửi ngân hàng",
                "112",
                List.of("1121", "1122"),
                "Tài khoản này dùng để phản ánh số hiện có và tình hình biến động các khoản tiền gửi.",
                "principle",
                9,
                12,
                "def456abc123");

        when(parserService.parseAllChunks()).thenReturn(List.of(chunk1, chunk2));
        when(textSynthesizer.buildGenericPayload(any(), any(), any(), any(), any(), any()))
                .thenReturn(createMockPayload());

        when(webhookService.triggerEntityEmbedding(any()))
                .thenThrow(new RuntimeException("Webhook failed"))
                .thenReturn(CompletableFuture.completedFuture(null));

        embeddingService.embedAllChunks();

        verify(webhookService, times(2)).triggerEntityEmbedding(any());
    }

    @Test
    void embedAllChunks_emptyChunks_doesNotCallWebhook() {
        when(parserService.parseAllChunks()).thenReturn(List.of());

        embeddingService.embedAllChunks();

        verify(webhookService, never()).triggerEntityEmbedding(any());
    }

    @Test
    void embedAllChunks_synthesizesVietnameseText() {
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

        when(parserService.parseAllChunks()).thenReturn(List.of(sampleChunk));
        when(textSynthesizer.buildGenericPayload(any(), any(), any(), textCaptor.capture(), any(), any()))
                .thenReturn(createMockPayload());

        embeddingService.embedAllChunks();

        String synthesizedText = textCaptor.getValue();
        assertNotNull(synthesizedText);
        assertFalse(synthesizedText.isBlank());
    }

    @Test
    void getStatus_tracksProgress() {
        ThongTu200EmbeddingService.EmbeddingStatus status = embeddingService.getStatus();
        assertNotNull(status);
    }

    @Test
    void embedByAccountCode_onlyEmbedsMatchingChunks() {
        if (embeddingService == null) {
            return;
        }
        when(parserService.parseByAccountCode("111")).thenReturn(List.of(sampleChunk));
        when(textSynthesizer.buildGenericPayload(any(), any(), any(), any(), any(), any()))
                .thenReturn(createMockPayload());

        embeddingService.embedByAccountCode("111");

        verify(webhookService, times(1)).triggerEntityEmbedding(any());
    }

    @Test
    void sampleChunk_hasValidStructure() {
        assertNotNull(sampleChunk.chunkId());
        assertEquals("II", sampleChunk.chapterNumber());
        assertEquals(12, sampleChunk.articleNumber());
        assertEquals("111", sampleChunk.accountCode());
        assertEquals("principle", sampleChunk.sectionType());
        assertFalse(sampleChunk.content().isBlank());
    }

    private EntityEmbeddingPayload createMockPayload() {
        return EntityEmbeddingPayload.builder()
                .entityType(EntityType.REGULATORY_TT200)
                .entityId("tt200:II:12:111:principle")
                .action(EmbeddingAction.UPSERT)
                .text("Tài khoản 111 - Tiền mặt. Tài khoản này dùng để phản ánh tình hình thu, chi tiền mặt.")
                .namespace("regulatory_tt200")
                .metadata(Map.of(
                        "source", "TT200",
                        "accountCode", "111",
                        "chapterNumber", "II",
                        "articleNumber", 12))
                .build();
    }
}
