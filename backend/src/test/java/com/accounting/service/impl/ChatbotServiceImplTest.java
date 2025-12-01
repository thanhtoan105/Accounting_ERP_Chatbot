package com.accounting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accounting.config.chatbot.ChatbotProperties;
import com.accounting.entity.ChatbotQuery;
import com.accounting.repository.ChatbotQueryRepository;
import com.accounting.service.AzureOpenAIService;
import com.accounting.service.ChatbotService;
import com.accounting.service.RAGQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for ChatbotServiceImpl.
 *
 * Tests cover:
 * - Input validation
 * - Confidence score calculation
 * - Fallback message handling
 * - Database persistence
 * - Service availability checks
 */
@ExtendWith(MockitoExtension.class)
class ChatbotServiceImplTest {

    @Mock
    private RAGQueryService ragQueryService;

    @Mock
    private AzureOpenAIService azureOpenAIService;

    @Mock
    private ChatbotQueryRepository chatbotQueryRepository;

    @Captor
    private ArgumentCaptor<ChatbotQuery> chatbotQueryCaptor;

    private ChatbotServiceImpl chatbotService;
    private ChatbotProperties chatbotProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Setup ChatbotProperties
        chatbotProperties = new ChatbotProperties();
        ChatbotProperties.Query queryProps = new ChatbotProperties.Query();
        queryProps.setMaxLength(5000);
        queryProps.setConfidenceThresholdLow(0.5);
        queryProps.setConfidenceThresholdHigh(0.8);
        chatbotProperties.setQuery(queryProps);

        objectMapper = new ObjectMapper();

        chatbotService = new ChatbotServiceImpl(
                ragQueryService,
                azureOpenAIService,
                chatbotQueryRepository,
                chatbotProperties,
                objectMapper);
    }

    @Nested
    @DisplayName("processQuery Tests")
    class ProcessQueryTests {

        @Test
        @DisplayName("Should process query successfully with high confidence")
        void processQuery_highConfidence_success() {
            // Given
            String queryText = "Công nợ phải thu là bao nhiêu?";
            Long userId = 1L;
            Long companyId = 2L;
            String sessionId = UUID.randomUUID().toString();
            String language = "vi";
            Map<String, String> contextFilters = new HashMap<>();

            RAGQueryService.Citation citation = new RAGQueryService.Citation(
                    "voucher",
                    "123e4567-e89b-12d3-a456-426614174000",
                    "PC-2024-001",
                    "Chi tiền mặt cho NCC ABC",
                    0.85,
                    "/vouchers/123");

            RAGQueryService.RetrievalResult retrievalResult = new RAGQueryService.RetrievalResult(
                    "Context about voucher PC-2024-001...",
                    List.of(citation),
                    0.85,
                    1);

            when(ragQueryService.retrieveRelevantContext(anyString(), anyLong(), anyLong(), anyMap()))
                    .thenReturn(retrievalResult);

            when(azureOpenAIService.generateCompletion(anyString(), anyString(), anyString()))
                    .thenReturn("Công nợ phải thu hiện tại là 100,000,000 VND.");

            ChatbotQuery savedQuery = new ChatbotQuery();
            savedQuery.setId(123L);
            when(chatbotQueryRepository.save(any(ChatbotQuery.class))).thenReturn(savedQuery);

            // When
            ChatbotService.ChatbotQueryResponse response = chatbotService.processQuery(
                    queryText, userId, companyId, sessionId, language, contextFilters);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getQueryId()).isEqualTo(123L);
            assertThat(response.getAnswerText()).isEqualTo("Công nợ phải thu hiện tại là 100,000,000 VND.");
            assertThat(response.getCitations()).hasSize(1);
            assertThat(response.getConfidenceScore()).isGreaterThan(0.5);

            verify(ragQueryService).retrieveRelevantContext(queryText, companyId, userId, contextFilters);
            verify(azureOpenAIService).generateCompletion(queryText, "Context about voucher PC-2024-001...", language);
            verify(chatbotQueryRepository).save(chatbotQueryCaptor.capture());

            ChatbotQuery capturedQuery = chatbotQueryCaptor.getValue();
            assertThat(capturedQuery.getQueryText()).isEqualTo(queryText);
            assertThat(capturedQuery.getCompanyId()).isEqualTo(companyId);
            assertThat(capturedQuery.getUserId()).isEqualTo(userId);
            assertThat(capturedQuery.getSessionId()).isEqualTo(sessionId);
            assertThat(capturedQuery.getLanguage()).isEqualTo(language);
        }

        @Test
        @DisplayName("Should return fallback message for low confidence")
        void processQuery_lowConfidence_returnsFallback() {
            // Given
            String queryText = "Câu hỏi không liên quan";
            Long userId = 1L;
            Long companyId = 2L;
            String sessionId = UUID.randomUUID().toString();
            String language = "vi";
            Map<String, String> contextFilters = new HashMap<>();

            // Low relevance score = low confidence
            RAGQueryService.RetrievalResult retrievalResult = new RAGQueryService.RetrievalResult(
                    "",
                    List.of(), // Empty citations
                    0.0,
                    0);

            when(ragQueryService.retrieveRelevantContext(anyString(), anyLong(), anyLong(), anyMap()))
                    .thenReturn(retrievalResult);

            ChatbotQuery savedQuery = new ChatbotQuery();
            savedQuery.setId(456L);
            when(chatbotQueryRepository.save(any(ChatbotQuery.class))).thenReturn(savedQuery);

            // When
            ChatbotService.ChatbotQueryResponse response = chatbotService.processQuery(
                    queryText, userId, companyId, sessionId, language, contextFilters);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getAnswerText()).contains("Không đủ dữ liệu");
            assertThat(response.getConfidenceScore()).isEqualTo(0.0);

            // Should NOT call LLM for low confidence
            verify(azureOpenAIService, never()).generateCompletion(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should return English fallback message when language is 'en'")
        void processQuery_lowConfidenceEnglish_returnsEnglishFallback() {
            // Given
            String queryText = "Unrelated question";
            Long userId = 1L;
            Long companyId = 2L;
            String sessionId = UUID.randomUUID().toString();
            String language = "en";
            Map<String, String> contextFilters = new HashMap<>();

            RAGQueryService.RetrievalResult retrievalResult = new RAGQueryService.RetrievalResult(
                    "", List.of(), 0.0, 0);

            when(ragQueryService.retrieveRelevantContext(anyString(), anyLong(), anyLong(), anyMap()))
                    .thenReturn(retrievalResult);

            ChatbotQuery savedQuery = new ChatbotQuery();
            savedQuery.setId(789L);
            when(chatbotQueryRepository.save(any(ChatbotQuery.class))).thenReturn(savedQuery);

            // When
            ChatbotService.ChatbotQueryResponse response = chatbotService.processQuery(
                    queryText, userId, companyId, sessionId, language, contextFilters);

            // Then
            assertThat(response.getAnswerText()).contains("Insufficient data");
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Should throw exception for null query text")
        void processQuery_nullQueryText_throwsException() {
            assertThatThrownBy(() -> chatbotService.processQuery(
                    null, 1L, 2L, "session", "vi", new HashMap<>()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("Should throw exception for empty query text")
        void processQuery_emptyQueryText_throwsException() {
            assertThatThrownBy(() -> chatbotService.processQuery(
                    "", 1L, 2L, "session", "vi", new HashMap<>()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("Should throw exception for blank query text")
        void processQuery_blankQueryText_throwsException() {
            assertThatThrownBy(() -> chatbotService.processQuery(
                    "   ", 1L, 2L, "session", "vi", new HashMap<>()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("Should throw exception when query exceeds max length")
        void processQuery_exceedsMaxLength_throwsException() {
            String longQuery = "a".repeat(5001); // Exceeds 5000 limit

            assertThatThrownBy(() -> chatbotService.processQuery(
                    longQuery, 1L, 2L, "session", "vi", new HashMap<>()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maximum length");
        }
    }

    @Nested
    @DisplayName("Confidence Score Calculation Tests")
    class ConfidenceScoreTests {

        @Test
        @DisplayName("Should calculate correct confidence with 5 citations and high average score")
        void confidenceScore_5Citations_highScore() {
            // Given: 5 citations with 0.9 average score
            // Expected: (0.9 * 0.7) + (1.0 * 0.3) = 0.63 + 0.3 = 0.93
            String queryText = "Test query";
            Long userId = 1L;
            Long companyId = 2L;

            List<RAGQueryService.Citation> citations = List.of(
                    createCitation(0.9), createCitation(0.9), createCitation(0.9),
                    createCitation(0.9), createCitation(0.9));

            RAGQueryService.RetrievalResult retrievalResult = new RAGQueryService.RetrievalResult(
                    "Context", citations, 0.9, 5);

            when(ragQueryService.retrieveRelevantContext(anyString(), anyLong(), anyLong(), anyMap()))
                    .thenReturn(retrievalResult);
            when(azureOpenAIService.generateCompletion(anyString(), anyString(), anyString()))
                    .thenReturn("Answer");

            ChatbotQuery savedQuery = new ChatbotQuery();
            savedQuery.setId(1L);
            when(chatbotQueryRepository.save(any(ChatbotQuery.class))).thenReturn(savedQuery);

            // When
            ChatbotService.ChatbotQueryResponse response = chatbotService.processQuery(
                    queryText, userId, companyId, "session", "vi", new HashMap<>());

            // Then
            assertThat(response.getConfidenceScore()).isCloseTo(0.93, org.assertj.core.api.Assertions.within(0.01));
        }

        @Test
        @DisplayName("Should calculate correct confidence with 1 citation")
        void confidenceScore_1Citation_mediumScore() {
            // Given: 1 citation with 0.8 average score
            // Expected: (0.8 * 0.7) + (0.2 * 0.3) = 0.56 + 0.06 = 0.62
            String queryText = "Test query";

            List<RAGQueryService.Citation> citations = List.of(createCitation(0.8));

            RAGQueryService.RetrievalResult retrievalResult = new RAGQueryService.RetrievalResult(
                    "Context", citations, 0.8, 1);

            when(ragQueryService.retrieveRelevantContext(anyString(), anyLong(), anyLong(), anyMap()))
                    .thenReturn(retrievalResult);
            when(azureOpenAIService.generateCompletion(anyString(), anyString(), anyString()))
                    .thenReturn("Answer");

            ChatbotQuery savedQuery = new ChatbotQuery();
            savedQuery.setId(1L);
            when(chatbotQueryRepository.save(any(ChatbotQuery.class))).thenReturn(savedQuery);

            // When
            ChatbotService.ChatbotQueryResponse response = chatbotService.processQuery(
                    queryText, 1L, 2L, "session", "vi", new HashMap<>());

            // Then
            assertThat(response.getConfidenceScore()).isCloseTo(0.62, org.assertj.core.api.Assertions.within(0.01));
        }

        private RAGQueryService.Citation createCitation(double score) {
            return new RAGQueryService.Citation(
                    "voucher", "test-entity-id", "VC-001", "Excerpt", score, "/vouchers/1");
        }
    }

    @Nested
    @DisplayName("Service Availability Tests")
    class AvailabilityTests {

        @Test
        @DisplayName("Should return true when all services are available")
        void isAvailable_allServicesUp_returnsTrue() {
            when(azureOpenAIService.isAvailable()).thenReturn(true);

            boolean available = chatbotService.isAvailable();

            assertThat(available).isTrue();
        }

        @Test
        @DisplayName("Should return false when OpenAI is unavailable")
        void isAvailable_openAIDown_returnsFalse() {
            when(azureOpenAIService.isAvailable()).thenReturn(false);

            boolean available = chatbotService.isAvailable();

            assertThat(available).isFalse();
        }
    }
}
