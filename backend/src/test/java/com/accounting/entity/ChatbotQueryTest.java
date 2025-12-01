package com.accounting.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.accounting.repository.CompanyScopedEntity;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ChatbotQuery entity.
 *
 * Tests cover:
 * - SHA-256 audit hash generation
 * - Hash verification for tamper detection
 * - PrePersist hook behavior
 */
class ChatbotQueryTest {

    private ChatbotQuery chatbotQuery;

    @BeforeEach
    void setUp() {
        chatbotQuery = new ChatbotQuery();
        chatbotQuery.setCompanyId(1L);
        chatbotQuery.setUserId(2L);
        chatbotQuery.setSessionId("test-session-123");
        chatbotQuery.setQueryText("What is the current AR balance?");
        chatbotQuery.setAnswerText("The current AR balance is 100,000,000 VND.");
        chatbotQuery.setCitations("[{\"entityType\":\"voucher\",\"voucherNumber\":\"PC-001\"}]");
        chatbotQuery.setConfidenceScore(0.85f);
        chatbotQuery.setResponseTimeMs(1500);
        chatbotQuery.setLanguage("vi");
    }

    @Nested
    @DisplayName("Audit Hash Generation Tests")
    class AuditHashGenerationTests {

        @Test
        @DisplayName("Should generate SHA-256 audit hash on prePersist")
        void prePersist_generatesAuditHash() {
            // When
            invokeOnCreate(chatbotQuery);

            // Then
            assertThat(chatbotQuery.getAuditHash()).isNotNull();
            assertThat(chatbotQuery.getAuditHash()).hasSize(64); // SHA-256 hex = 64 chars
            assertThat(chatbotQuery.getAuditHash()).matches("^[a-f0-9]{64}$");
        }

        @Test
        @DisplayName("Should set createdAt timestamp on prePersist")
        void prePersist_setsCreatedAt() {
            // When
            invokeOnCreate(chatbotQuery);

            // Then
            assertThat(chatbotQuery.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should generate different hashes for different content")
        void prePersist_differentContent_differentHashes() {
            // Given
            ChatbotQuery query1 = new ChatbotQuery();
            query1.setQueryText("Query 1");
            query1.setAnswerText("Answer 1");
            query1.setCitations("[]");

            ChatbotQuery query2 = new ChatbotQuery();
            query2.setQueryText("Query 2");
            query2.setAnswerText("Answer 2");
            query2.setCitations("[]");

            // When
            invokeOnCreate(query1);
            invokeOnCreate(query2);

            // Then
            assertThat(query1.getAuditHash()).isNotEqualTo(query2.getAuditHash());
        }

        @Test
        @DisplayName("Should generate same hash for identical content")
        void prePersist_sameContent_sameHash() {
            // Given
            ChatbotQuery query1 = new ChatbotQuery();
            query1.setQueryText("Same query");
            query1.setAnswerText("Same answer");
            query1.setCitations("[]");

            ChatbotQuery query2 = new ChatbotQuery();
            query2.setQueryText("Same query");
            query2.setAnswerText("Same answer");
            query2.setCitations("[]");

            // When
            invokeOnCreate(query1);
            invokeOnCreate(query2);

            // Then
            assertThat(query1.getAuditHash()).isEqualTo(query2.getAuditHash());
        }

        @Test
        @DisplayName("Should handle null citations in hash generation")
        void prePersist_nullCitations_generatesHash() {
            // Given
            chatbotQuery.setCitations(null);

            // When
            invokeOnCreate(chatbotQuery);

            // Then
            assertThat(chatbotQuery.getAuditHash()).isNotNull();
            assertThat(chatbotQuery.getAuditHash()).hasSize(64);
        }
    }

    @Nested
    @DisplayName("Hash Verification Tests")
    class HashVerificationTests {

        @Test
        @DisplayName("Should verify hash for unmodified content")
        void verifyAuditHash_unmodified_returnsTrue() {
            // Given
            invokeOnCreate(chatbotQuery);

            // When
            boolean isValid = chatbotQuery.verifyAuditHash();

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("Should detect tampered query text")
        void verifyAuditHash_tamperedQueryText_returnsFalse() {
            // Given
            invokeOnCreate(chatbotQuery);
            chatbotQuery.setQueryText("Modified query text");

            // When
            boolean isValid = chatbotQuery.verifyAuditHash();

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("Should detect tampered answer text")
        void verifyAuditHash_tamperedAnswerText_returnsFalse() {
            // Given
            invokeOnCreate(chatbotQuery);
            chatbotQuery.setAnswerText("Modified answer text");

            // When
            boolean isValid = chatbotQuery.verifyAuditHash();

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("Should detect tampered citations")
        void verifyAuditHash_tamperedCitations_returnsFalse() {
            // Given
            invokeOnCreate(chatbotQuery);
            chatbotQuery.setCitations("[{\"modified\":true}]");

            // When
            boolean isValid = chatbotQuery.verifyAuditHash();

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("Should return false when audit hash is null")
        void verifyAuditHash_nullHash_returnsFalse() {
            // Given - hash not generated yet
            assertThat(chatbotQuery.getAuditHash()).isNull();

            // When
            boolean isValid = chatbotQuery.verifyAuditHash();

            // Then
            assertThat(isValid).isFalse();
        }
    }

    @Nested
    @DisplayName("CompanyScopedEntity Tests")
    class CompanyScopedEntityTests {

        @Test
        @DisplayName("Should implement CompanyScopedEntity interface")
        void implementsCompanyScopedEntity() {
            assertThat(chatbotQuery).isInstanceOf(CompanyScopedEntity.class);
        }

        @Test
        @DisplayName("Should get and set company ID")
        void getSetCompanyId() {
            chatbotQuery.setCompanyId(999L);
            assertThat(chatbotQuery.getCompanyId()).isEqualTo(999L);
        }
    }

    @Nested
    @DisplayName("Field Validation Tests")
    class FieldValidationTests {

        @Test
        @DisplayName("Should store confidence score correctly")
        void confidenceScore_storesCorrectly() {
            chatbotQuery.setConfidenceScore(0.999f);
            assertThat(chatbotQuery.getConfidenceScore()).isEqualTo(0.999f);
        }

        @Test
        @DisplayName("Should store response time correctly")
        void responseTime_storesCorrectly() {
            chatbotQuery.setResponseTimeMs(5000);
            assertThat(chatbotQuery.getResponseTimeMs()).isEqualTo(5000);
        }

        @Test
        @DisplayName("Should handle long query text")
        void queryText_handlesLongText() {
            String longText = "a".repeat(5000);
            chatbotQuery.setQueryText(longText);
            assertThat(chatbotQuery.getQueryText()).hasSize(5000);
        }
    }

    /**
     * Helper method to invoke the protected onCreate() method via reflection.
     * This simulates the JPA @PrePersist callback.
     */
    private void invokeOnCreate(ChatbotQuery query) {
        try {
            Method onCreate = ChatbotQuery.class.getDeclaredMethod("onCreate");
            onCreate.setAccessible(true);
            onCreate.invoke(query);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke onCreate", e);
        }
    }
}
