package com.accounting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.accounting.service.ChatbotService;
import com.accounting.service.N8nRAGQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for ChatbotServiceImpl.
 *
 * Tests cover:
 * - Input validation
 * - n8n RAG query execution
 * - Database persistence
 * - Service availability checks
 */
@ExtendWith(MockitoExtension.class)
class ChatbotServiceImplTest {

    @Mock
    private N8nRAGQueryService n8nRAGQueryService;

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
                n8nRAGQueryService,
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

            N8nRAGQueryService.Citation citation = new N8nRAGQueryService.Citation(
                    "VOUCHER",
                    "123e4567-e89b-12d3-a456-426614174000",
                    "PC-2024-001",
                    "Chi tiền mặt cho NCC ABC",
                    0.85,
                    "/vouchers/123");

            N8nRAGQueryService.RAGQueryResult ragResult = new N8nRAGQueryService.RAGQueryResult(
                    "Công nợ phải thu hiện tại là 100,000,000 VND.",
                    List.of(citation),
                    0.85,
                    "HIGH",
                    "q_123_456",
                    sessionId,
                    1500,
                    language,
                    List.of());

            when(n8nRAGQueryService.executeQuery(anyString(), anyLong(), anyLong(), anyString(), anyString()))
                    .thenReturn(ragResult);

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
            assertThat(response.getConfidenceScore()).isEqualTo(0.85);

            verify(n8nRAGQueryService).executeQuery(queryText, companyId, userId, sessionId, language);
            verify(chatbotQueryRepository).save(chatbotQueryCaptor.capture());

            ChatbotQuery capturedQuery = chatbotQueryCaptor.getValue();
            assertThat(capturedQuery.getQueryText()).isEqualTo(queryText);
            assertThat(capturedQuery.getCompanyId()).isEqualTo(companyId);
            assertThat(capturedQuery.getUserId()).isEqualTo(userId);
            assertThat(capturedQuery.getSessionId()).isEqualTo(sessionId);
            assertThat(capturedQuery.getLanguage()).isEqualTo(language);
        }

        @Test
        @DisplayName("Should handle low confidence response from n8n")
        void processQuery_lowConfidence_handlesResponse() {
            // Given
            String queryText = "Câu hỏi không liên quan";
            Long userId = 1L;
            Long companyId = 2L;
            String sessionId = UUID.randomUUID().toString();
            String language = "vi";
            Map<String, String> contextFilters = new HashMap<>();

            N8nRAGQueryService.RAGQueryResult ragResult = new N8nRAGQueryService.RAGQueryResult(
                    "Không tìm thấy dữ liệu phù hợp. Vui lòng thử lại với câu hỏi cụ thể hơn.",
                    List.of(),
                    0.3,
                    "LOW",
                    "q_456_789",
                    sessionId,
                    800,
                    language,
                    List.of("Thử hỏi cụ thể hơn về số phiếu hoặc ngày tháng"));

            when(n8nRAGQueryService.executeQuery(anyString(), anyLong(), anyLong(), anyString(), anyString()))
                    .thenReturn(ragResult);

            ChatbotQuery savedQuery = new ChatbotQuery();
            savedQuery.setId(456L);
            when(chatbotQueryRepository.save(any(ChatbotQuery.class))).thenReturn(savedQuery);

            // When
            ChatbotService.ChatbotQueryResponse response = chatbotService.processQuery(
                    queryText, userId, companyId, sessionId, language, contextFilters);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getAnswerText()).contains("Không tìm thấy dữ liệu");
            assertThat(response.getConfidenceScore()).isEqualTo(0.3);
            assertThat(response.getCitations()).isEmpty();
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
    @DisplayName("Service Availability Tests")
    class AvailabilityTests {

        @Test
        @DisplayName("Should return true when n8n service is available")
        void isAvailable_n8nServiceUp_returnsTrue() {
            when(n8nRAGQueryService.isAvailable()).thenReturn(true);

            boolean available = chatbotService.isAvailable();

            assertThat(available).isTrue();
        }

        @Test
        @DisplayName("Should return false when n8n service is unavailable")
        void isAvailable_n8nServiceDown_returnsFalse() {
            when(n8nRAGQueryService.isAvailable()).thenReturn(false);

            boolean available = chatbotService.isAvailable();

            assertThat(available).isFalse();
        }
    }
}
