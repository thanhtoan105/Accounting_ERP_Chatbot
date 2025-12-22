package com.accounting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accounting.dto.admin.EmbeddingStatusResponse;
import com.accounting.repository.VoucherRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceImplTest {

  @Mock private VoucherRepository voucherRepository;

  @Mock private ObjectMapper objectMapper;

  private EmbeddingServiceImpl embeddingService;

  @BeforeEach
  void setUp() {
    embeddingService =
        new EmbeddingServiceImpl(
            voucherRepository, objectMapper, "http://test-webhook-url", "test-secret");
  }

  @Test
  void getEmbeddingStatus_returnsCorrectCounts() {
    Long companyId = 1L;
    when(voucherRepository.countByCompanyId(companyId)).thenReturn(100L);
    when(voucherRepository.countByCompanyIdAndEmbeddedAtIsNotNull(companyId)).thenReturn(75L);

    EmbeddingStatusResponse result = embeddingService.getEmbeddingStatus(companyId);

    assertThat(result.total()).isEqualTo(100L);
    assertThat(result.embedded()).isEqualTo(75L);
    assertThat(result.pending()).isEqualTo(25L);
    assertThat(result.percentage()).isEqualByComparingTo(new BigDecimal("75.00"));
  }

  @Test
  void getEmbeddingStatus_withNoVouchers_returnsZeros() {
    Long companyId = 1L;
    when(voucherRepository.countByCompanyId(companyId)).thenReturn(0L);
    when(voucherRepository.countByCompanyIdAndEmbeddedAtIsNotNull(companyId)).thenReturn(0L);

    EmbeddingStatusResponse result = embeddingService.getEmbeddingStatus(companyId);

    assertThat(result.total()).isZero();
    assertThat(result.embedded()).isZero();
    assertThat(result.pending()).isZero();
    assertThat(result.percentage()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void getEmbeddingStatus_withAllEmbedded_returnsHundredPercent() {
    Long companyId = 1L;
    when(voucherRepository.countByCompanyId(companyId)).thenReturn(50L);
    when(voucherRepository.countByCompanyIdAndEmbeddedAtIsNotNull(companyId)).thenReturn(50L);

    EmbeddingStatusResponse result = embeddingService.getEmbeddingStatus(companyId);

    assertThat(result.total()).isEqualTo(50L);
    assertThat(result.embedded()).isEqualTo(50L);
    assertThat(result.pending()).isZero();
    assertThat(result.percentage()).isEqualByComparingTo(new BigDecimal("100.00"));
  }
}
