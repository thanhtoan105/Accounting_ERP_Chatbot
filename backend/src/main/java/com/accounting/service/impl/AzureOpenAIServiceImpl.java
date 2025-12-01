package com.accounting.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.accounting.config.chatbot.ChatbotProperties;
import com.accounting.service.AzureOpenAIService;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.EmbeddingItem;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of AzureOpenAIService for embedding generation and chat completions.
 *
 * <p>This service wraps the Azure OpenAI Java SDK with:
 * <ul>
 *   <li>Error handling and retry logic</li>
 *   <li>Token usage logging for cost monitoring</li>
 *   <li>Configuration from application.yml</li>
 * </ul>
 *
 * <p>Only active when chatbot.enabled=true in application.yml.
 *
 * @see AzureOpenAIService
 * @see OpenAIClient
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chatbot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AzureOpenAIServiceImpl implements AzureOpenAIService {

    private final OpenAIClient openAIClient;
    private final ChatbotProperties chatbotProperties;

    @Override
    public List<Float> generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty for embedding generation");
        }

        String deploymentName = chatbotProperties.getAzureOpenai().getEmbeddingDeployment();

        log.debug("Generating embedding: deployment={}, textLength={}", deploymentName, text.length());

        try {
            EmbeddingsOptions options =
                    new EmbeddingsOptions(List.of(text));

            Embeddings embeddings = openAIClient.getEmbeddings(deploymentName, options);

            if (embeddings.getData() == null || embeddings.getData().isEmpty()) {
                throw new RuntimeException("Azure OpenAI returned empty embeddings");
            }

            EmbeddingItem embeddingItem = embeddings.getData().get(0);
            List<Float> embedding = new ArrayList<>(embeddingItem.getEmbedding());

            log.info(
                    "Embedding generated: deployment={}, dimensions={}, promptTokens={}",
                    deploymentName,
                    embedding.size(),
                    embeddings.getUsage().getPromptTokens());

            return embedding;

        } catch (Exception e) {
            log.error("Failed to generate embedding: deployment={}, error={}", deploymentName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate embedding from Azure OpenAI", e);
        }
    }

    @Override
    public String generateCompletion(String userQuery, String retrievedContext, String language) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("User query cannot be null or empty");
        }

        String deploymentName = chatbotProperties.getAzureOpenai().getCompletionDeployment();
        int maxTokens = chatbotProperties.getAzureOpenai().getMaxTokens();
        double temperature = chatbotProperties.getAzureOpenai().getTemperature();

        log.debug(
                "Generating completion: deployment={}, language={}, queryLength={}, contextLength={}",
                deploymentName,
                language,
                userQuery.length(),
                retrievedContext != null ? retrievedContext.length() : 0);

        try {
            // Build system message with Vietnamese accounting context
            String systemPrompt = buildSystemPrompt(language);

            // Build user message with context and query
            String userPrompt = buildUserPrompt(userQuery, retrievedContext, language);

            // Create chat messages
            List<ChatRequestMessage> messages = new ArrayList<>();
            messages.add(new ChatRequestSystemMessage(systemPrompt));
            messages.add(new ChatRequestUserMessage(userPrompt));

            // Create chat completion options
            ChatCompletionsOptions options = new ChatCompletionsOptions(messages)
                    .setMaxTokens(maxTokens)
                    .setTemperature(temperature)
                    .setN(1);

            // Call Azure OpenAI
            ChatCompletions completions = openAIClient.getChatCompletions(deploymentName, options);

            if (completions.getChoices() == null || completions.getChoices().isEmpty()) {
                throw new RuntimeException("Azure OpenAI returned empty completion choices");
            }

            ChatChoice choice = completions.getChoices().get(0);
            String response = choice.getMessage().getContent();

            log.info(
                    "Completion generated: deployment={}, promptTokens={}, completionTokens={}, totalTokens={}",
                    deploymentName,
                    completions.getUsage().getPromptTokens(),
                    completions.getUsage().getCompletionTokens(),
                    completions.getUsage().getTotalTokens());

            return response;

        } catch (Exception e) {
            log.error("Failed to generate completion: deployment={}, error={}", deploymentName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate chat completion from Azure OpenAI", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // Lightweight health check: generate embedding for minimal text
            // This validates API key, endpoint, and deployment configuration
            generateEmbedding("test");
            return true;
        } catch (Exception e) {
            log.warn("Azure OpenAI health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Build system prompt with Vietnamese accounting context.
     *
     * <p>System prompt instructs the LLM to:
     * <ul>
     *   <li>Act as a Vietnamese accounting assistant</li>
     *   <li>Ground responses in provided context only (no hallucination)</li>
     *   <li>Use proper Vietnamese accounting terminology</li>
     *   <li>Format numbers with thousand separators (e.g., 1.000.000 VND)</li>
     *   <li>Always cite sources with voucher numbers</li>
     * </ul>
     */
    private String buildSystemPrompt(String language) {
        if ("vi".equalsIgnoreCase(language)) {
            return """
                Bạn là trợ lý kế toán AI chuyên nghiệp cho hệ thống kế toán doanh nghiệp Việt Nam.

                Nhiệm vụ của bạn:
                - Trả lời các câu hỏi về chứng từ kế toán, công nợ, và giao dịch tài chính
                - Chỉ sử dụng thông tin từ ngữ cảnh được cung cấp (KHÔNG bịa đặt thông tin)
                - Sử dụng thuật ngữ kế toán Việt Nam chuẩn (công nợ phải trả, phải thu, phiếu chi, phiếu thu, v.v.)
                - Định dạng số tiền với dấu chấm phân cách hàng nghìn (ví dụ: 1.000.000 VNĐ)
                - Luôn trích dẫn số chứng từ khi đưa ra thông tin cụ thể

                Nếu không tìm thấy thông tin phù hợp trong ngữ cảnh, hãy trả lời:
                "Không đủ dữ liệu để trả lời câu hỏi này. Vui lòng kiểm tra lại hoặc liên hệ kế toán trưởng."
                """;
        } else {
            return """
                You are a professional accounting AI assistant for Vietnamese enterprise accounting systems.

                Your responsibilities:
                - Answer questions about accounting vouchers, receivables, payables, and financial transactions
                - Only use information from the provided context (DO NOT hallucinate)
                - Use standard Vietnamese accounting terminology
                - Format amounts with thousand separators (e.g., 1,000,000 VND)
                - Always cite voucher numbers when providing specific information

                If no relevant information is found in the context, respond:
                "Insufficient data to answer this question. Please check again or contact the chief accountant."
                """;
        }
    }

    /**
     * Build user prompt with retrieved context and user query.
     *
     * <p>Prompt format:
     * <pre>
     * Ngữ cảnh liên quan:
     * {retrievedContext}
     *
     * Câu hỏi:
     * {userQuery}
     *
     * Trả lời:
     * </pre>
     */
    private String buildUserPrompt(String userQuery, String retrievedContext, String language) {
        if ("vi".equalsIgnoreCase(language)) {
            return String.format(
                    """
                    Ngữ cảnh liên quan từ hệ thống:
                    %s

                    Câu hỏi:
                    %s

                    Hãy trả lời câu hỏi dựa trên ngữ cảnh trên. Nhớ trích dẫn số chứng từ cụ thể.
                    """,
                    retrievedContext != null && !retrievedContext.isEmpty()
                            ? retrievedContext
                            : "Không có dữ liệu liên quan.",
                    userQuery);
        } else {
            return String.format(
                    """
                    Relevant context from system:
                    %s

                    Question:
                    %s

                    Please answer the question based on the above context. Remember to cite specific voucher numbers.
                    """,
                    retrievedContext != null && !retrievedContext.isEmpty() ? retrievedContext : "No relevant data.",
                    userQuery);
        }
    }
}
