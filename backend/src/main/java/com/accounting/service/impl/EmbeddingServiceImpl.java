package com.accounting.service.impl;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accounting.dto.admin.EmbeddingStatusResponse;
import com.accounting.entity.Voucher;
import com.accounting.repository.VoucherRepository;
import com.accounting.service.EmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

  private static final Logger logger = LoggerFactory.getLogger(EmbeddingServiceImpl.class);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final VoucherRepository voucherRepository;
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String batchWebhookUrl;
  private final String webhookSecret;

  public EmbeddingServiceImpl(
      VoucherRepository voucherRepository,
      ObjectMapper objectMapper,
      @Value("${chatbot.n8n.batch-webhook-url}") String batchWebhookUrl,
      @Value("${chatbot.n8n.webhook-secret}") String webhookSecret) {
    this.voucherRepository = voucherRepository;
    this.objectMapper = objectMapper;
    this.batchWebhookUrl = batchWebhookUrl;
    this.webhookSecret = webhookSecret;
    this.httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
  }

  @Override
  public EmbeddingStatusResponse getEmbeddingStatus(Long companyId) {
    long total = voucherRepository.countByCompanyId(companyId);
    long embedded = voucherRepository.countByCompanyIdAndEmbeddedAtIsNotNull(companyId);
    return EmbeddingStatusResponse.of(total, embedded);
  }

  @Override
  public void triggerBatchEmbedding(Long companyId) {
    logger.info("Triggering batch embedding for company: {}", companyId);
    try {
      String jsonPayload = objectMapper.writeValueAsString(Map.of("companyId", companyId));
      RequestBody body = RequestBody.create(jsonPayload, JSON);
      Request request =
          new Request.Builder()
              .url(batchWebhookUrl)
              .post(body)
              .addHeader("Content-Type", "application/json")
              .addHeader("X-Webhook-Secret", webhookSecret)
              .build();

      try (Response response = httpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          String responseBody = response.body() != null ? response.body().string() : "empty";
          throw new RuntimeException(
              String.format(
                  "Batch webhook call failed with status %d: %s", response.code(), responseBody));
        }
        logger.info("Batch embedding triggered successfully for company: {}", companyId);
      }
    } catch (IOException e) {
      logger.error("Failed to trigger batch embedding for company: {}", companyId, e);
      throw new RuntimeException("Failed to trigger batch embedding", e);
    }
  }

  @Override
  public void embedVoucher(Voucher voucher) {
    throw new UnsupportedOperationException("Direct embedding not implemented - use n8n webhook");
  }

  @Override
  public void deleteEmbedding(String voucherId, Long companyId) {
    throw new UnsupportedOperationException("Direct embedding not implemented - use n8n webhook");
  }

  @Override
  public void embedVouchersBatch(Iterable<Voucher> vouchers) {
    throw new UnsupportedOperationException("Direct embedding not implemented - use n8n webhook");
  }
}
