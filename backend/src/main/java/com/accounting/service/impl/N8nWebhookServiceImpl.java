package com.accounting.service.impl;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.accounting.dto.VoucherEmbeddingPayload;
import com.accounting.service.N8nWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Implementation of N8nWebhookService with exponential backoff retry logic.
 * Handles webhook calls to n8n for voucher embedding automation.
 */
@Service
public class N8nWebhookServiceImpl implements N8nWebhookService {

    private static final Logger logger = LoggerFactory.getLogger(N8nWebhookServiceImpl.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS_FIRST = 1000;   // 1 second
    private static final long RETRY_DELAY_MS_SECOND = 5000;  // 5 seconds
    private static final long RETRY_DELAY_MS_THIRD = 15000;  // 15 seconds

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;
    private final String webhookSecret;
    private final boolean enabled;

    public N8nWebhookServiceImpl(
            @Value("${chatbot.n8n.webhook-url}") String webhookUrl,
            @Value("${chatbot.n8n.webhook-secret}") String webhookSecret,
            @Value("${chatbot.enabled:false}") boolean enabled,
            ObjectMapper objectMapper) {
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
        this.enabled = enabled;
        this.objectMapper = objectMapper;

        // Configure OkHttpClient with timeouts
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    @Async
    public CompletableFuture<Void> triggerEmbedding(VoucherEmbeddingPayload payload) {
        if (!enabled) {
            logger.debug("Chatbot feature disabled, skipping embedding trigger for voucher: {}",
                payload.voucherId());
            return CompletableFuture.completedFuture(null);
        }

        String requestId = UUID.randomUUID().toString();
        logger.info("Triggering n8n webhook for voucher embedding (requestId: {}, voucherId: {})",
            requestId, payload.voucherId());

        return CompletableFuture.runAsync(() -> {
            try {
                triggerWithRetry(payload, requestId, 1);
                logger.info("Successfully triggered n8n webhook (requestId: {}, voucherId: {})",
                    requestId, payload.voucherId());
            } catch (Exception e) {
                logger.error("Failed to trigger n8n webhook after {} attempts (requestId: {}, voucherId: {})",
                    MAX_RETRY_ATTEMPTS, requestId, payload.voucherId(), e);
                // Fire-and-forget: don't propagate exception
            }
        });
    }

    @Override
    public void triggerEmbeddingSync(VoucherEmbeddingPayload payload) {
        if (!enabled) {
            logger.warn("Chatbot feature disabled, cannot trigger embedding for voucher: {}",
                payload.voucherId());
            throw new IllegalStateException("Chatbot feature is disabled");
        }

        String requestId = UUID.randomUUID().toString();
        logger.info("Triggering n8n webhook synchronously (requestId: {}, voucherId: {})",
            requestId, payload.voucherId());

        triggerWithRetry(payload, requestId, 1);
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }

        try {
            // Simple HEAD or GET request to check webhook availability
            Request request = new Request.Builder()
                .url(webhookUrl)
                .head()
                .addHeader("X-Webhook-Secret", webhookSecret)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful() || response.code() == 404 || response.code() == 405;
                // 404/405 means endpoint exists but doesn't support HEAD - still available
            }
        } catch (IOException e) {
            logger.debug("n8n webhook health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Trigger webhook with exponential backoff retry logic.
     */
    private void triggerWithRetry(VoucherEmbeddingPayload payload, String requestId, int attempt) {
        try {
            executeWebhookCall(payload, requestId);
        } catch (IOException e) {
            if (attempt >= MAX_RETRY_ATTEMPTS) {
                throw new RuntimeException(
                    String.format("Failed to trigger n8n webhook after %d attempts", MAX_RETRY_ATTEMPTS),
                    e);
            }

            long delayMs = getRetryDelay(attempt);
            logger.warn("n8n webhook call failed (attempt {}/{}), retrying in {}ms (requestId: {}, error: {})",
                attempt, MAX_RETRY_ATTEMPTS, delayMs, requestId, e.getMessage());

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry interrupted", ie);
            }

            triggerWithRetry(payload, requestId, attempt + 1);
        }
    }

    /**
     * Execute the actual HTTP call to n8n webhook.
     */
    private void executeWebhookCall(VoucherEmbeddingPayload payload, String requestId) throws IOException {
        String jsonPayload = objectMapper.writeValueAsString(payload);

        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Webhook-Secret", webhookSecret)
            .addHeader("X-Request-ID", requestId)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "empty";
                throw new IOException(
                    String.format("Webhook call failed with status %d: %s",
                        response.code(), responseBody));
            }

            logger.debug("n8n webhook call successful (requestId: {}, status: {})",
                requestId, response.code());
        }
    }

    /**
     * Get retry delay based on attempt number (exponential backoff).
     */
    private long getRetryDelay(int attempt) {
        return switch (attempt) {
            case 1 -> RETRY_DELAY_MS_FIRST;
            case 2 -> RETRY_DELAY_MS_SECOND;
            default -> RETRY_DELAY_MS_THIRD;
        };
    }
}
