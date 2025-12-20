package com.accounting.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.accounting.dto.ARAgingBucketDTO;
import com.accounting.dto.ARAgingReportDTO;
import com.accounting.dto.ARAgingReportResponse;
import com.accounting.security.CompanyContext;
import com.accounting.service.ARAgingExportService;
import com.accounting.service.ARAgingService;
import com.accounting.service.ARDashboardMetricsService;
import com.accounting.service.ARReminderService;
import com.accounting.service.AuditService;

/**
 * Unit tests for ARAgingController.
 * Tests REST API endpoints for AR aging reports.
 */
@ExtendWith(MockitoExtension.class)
public class ARAgingControllerTest {

        @Mock
        private ARAgingService arAgingService;

        @Mock
        private ARAgingExportService exportService;

        @Mock
        private ARDashboardMetricsService dashboardMetricsService;

        @Mock
        private ARReminderService reminderService;

        @Mock
        private AuditService auditService;

        @InjectMocks
        private ARAgingController arAgingController;

        private static final Long COMPANY_ID = 1L;
        private LocalDate asOfDate;

        @BeforeEach
        void setUp() {
                CompanyContext.setCompanyId(COMPANY_ID);
                asOfDate = LocalDate.of(2025, 11, 22);
        }

        @AfterEach
        void tearDown() {
                CompanyContext.clear();
        }

        @Test
        void testGetAgingReport_Success() {
                // Arrange
                ARAgingReportDTO reportItem = createReportItem(100L, "Customer A", "CUST-100");
                List<ARAgingReportDTO> reportItems = Arrays.asList(reportItem);
                Page<ARAgingReportDTO> page = new PageImpl<>(reportItems, PageRequest.of(0, 20), 1);

                when(arAgingService.getAgingReport(
                                isNull(), eq(asOfDate), isNull(), isNull(), any(Pageable.class)))
                                .thenReturn(page);

                // Act
                ResponseEntity<ARAgingReportResponse> response = arAgingController.getAgingReport(null, asOfDate, null,
                                null,
                                0, 20, "customerName", "asc");

                // Assert
                assertThat(response.getStatusCodeValue()).isEqualTo(200);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getData()).isNotNull();
                assertThat(response.getBody().getData().getContent()).hasSize(1);
                assertThat(response.getBody().getData().getContent().get(0).getCustomerName()).isEqualTo("Customer A");
                assertThat(response.getBody().getMetadata()).isNotNull();
                assertThat(response.getBody().getMetadata().getSnapshotDate()).isEqualTo(asOfDate);
                assertThat(response.getBody().getMetadata().getCacheStatus()).isEqualTo("HIT");

                verify(arAgingService, times(1))
                                .getAgingReport(isNull(), eq(asOfDate), isNull(), isNull(), any(Pageable.class));
        }

        @Test
        void testGetAgingReport_WithCustomerFilter() {
                // Arrange
                Long customerId = 100L;
                ARAgingReportDTO reportItem = createReportItem(customerId, "Customer A", "CUST-100");
                List<ARAgingReportDTO> reportItems = Arrays.asList(reportItem);
                Page<ARAgingReportDTO> page = new PageImpl<>(reportItems, PageRequest.of(0, 20), 1);

                when(arAgingService.getAgingReport(
                                eq(customerId), eq(asOfDate), isNull(), isNull(), any(Pageable.class)))
                                .thenReturn(page);

                // Act
                ResponseEntity<ARAgingReportResponse> response = arAgingController.getAgingReport(
                                customerId, asOfDate, null, null, 0, 20, "customerName", "asc");

                // Assert
                assertThat(response.getStatusCodeValue()).isEqualTo(200);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getData()).isNotNull();
                assertThat(response.getBody().getData().getContent()).hasSize(1);

                verify(arAgingService, times(1))
                                .getAgingReport(eq(customerId), eq(asOfDate), isNull(), isNull(), any(Pageable.class));
        }

        @Test
        void testGetAgingReport_WithBucketFilter() {
                // Arrange
                String bucket = "DAYS_1_30";
                ARAgingReportDTO reportItem = createReportItem(100L, "Customer A", "CUST-100");
                List<ARAgingReportDTO> reportItems = Arrays.asList(reportItem);
                Page<ARAgingReportDTO> page = new PageImpl<>(reportItems, PageRequest.of(0, 20), 1);

                when(arAgingService.getAgingReport(
                                isNull(), eq(asOfDate), isNull(), eq(bucket), any(Pageable.class)))
                                .thenReturn(page);

                // Act
                ResponseEntity<ARAgingReportResponse> response = arAgingController.getAgingReport(null, asOfDate, null,
                                bucket,
                                0, 20, "customerName", "asc");

                // Assert
                assertThat(response.getStatusCodeValue()).isEqualTo(200);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getMetadata()).isNotNull();
                verify(arAgingService, times(1))
                                .getAgingReport(isNull(), eq(asOfDate), isNull(), eq(bucket), any(Pageable.class));
        }

        @Test
        void testGetAgingReport_WithPagination() {
                // Arrange
                List<ARAgingReportDTO> reportItems = Arrays.asList(
                                createReportItem(100L, "Customer A", "CUST-100"),
                                createReportItem(200L, "Customer B", "CUST-200"));
                Page<ARAgingReportDTO> page = new PageImpl<>(reportItems, PageRequest.of(1, 10), 25);

                when(arAgingService.getAgingReport(
                                isNull(), eq(asOfDate), isNull(), isNull(), any(Pageable.class)))
                                .thenReturn(page);

                // Act
                ResponseEntity<ARAgingReportResponse> response = arAgingController.getAgingReport(null, asOfDate, null,
                                null,
                                1, 10, "customerName", "asc");

                // Assert
                assertThat(response.getStatusCodeValue()).isEqualTo(200);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getData()).isNotNull();
                assertThat(response.getBody().getData().getNumber()).isEqualTo(1);
                assertThat(response.getBody().getData().getSize()).isEqualTo(10);
                assertThat(response.getBody().getData().getTotalElements()).isEqualTo(25);
        }

        @Test
        void testGetAgingReport_DefaultAsOfDate() {
                // Arrange
                Page<ARAgingReportDTO> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

                when(arAgingService.getAgingReport(
                                isNull(), any(LocalDate.class), isNull(), isNull(), any(Pageable.class)))
                                .thenReturn(page);

                // Act
                ResponseEntity<ARAgingReportResponse> response = arAgingController.getAgingReport(null, null, null,
                                null, 0,
                                20, "customerName", "asc");

                // Assert
                assertThat(response.getStatusCodeValue()).isEqualTo(200);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getMetadata()).isNotNull();
                assertThat(response.getBody().getMetadata().getSnapshotDate()).isNotNull();
                verify(arAgingService, times(1))
                                .getAgingReport(isNull(), any(LocalDate.class), isNull(), isNull(),
                                                any(Pageable.class));
        }

        @Test
        void testRefreshCache_Success() {
                // Arrange
                doNothing().when(arAgingService).refreshAgingCache(eq(asOfDate));

                // Act
                ResponseEntity<String> response = arAgingController.refreshCache(asOfDate);

                // Assert
                assertThat(response.getStatusCodeValue()).isEqualTo(200);
                assertThat(response.getBody()).contains("refreshed successfully");

                verify(arAgingService, times(1)).refreshAgingCache(eq(asOfDate));
        }

        @Test
        void testRefreshCache_DefaultAsOfDate() {
                // Arrange
                doNothing().when(arAgingService).refreshAgingCache(isNull());

                // Act
                ResponseEntity<String> response = arAgingController.refreshCache(null);

                // Assert
                assertThat(response.getStatusCodeValue()).isEqualTo(200);
                verify(arAgingService, times(1)).refreshAgingCache(isNull());
        }

        private ARAgingReportDTO createReportItem(Long customerId, String customerName, String customerCode) {
                ARAgingReportDTO dto = new ARAgingReportDTO();
                dto.setCustomerId(customerId);
                dto.setCustomerName(customerName);
                dto.setCustomerCode(customerCode);

                ARAgingBucketDTO buckets = new ARAgingBucketDTO();
                buckets.setCurrent(new BigDecimal("1000.00"));
                buckets.setDays1To30(new BigDecimal("2000.00"));
                buckets.setDays31To60(new BigDecimal("3000.00"));
                buckets.setDays61To90(new BigDecimal("4000.00"));
                buckets.setDaysOver90(new BigDecimal("5000.00"));
                buckets.setTotal(new BigDecimal("15000.00"));

                dto.setBuckets(buckets);
                dto.setTotalOutstanding(buckets.getTotal());
                dto.setHasOverdue(true);
                dto.setInvoiceCount(5);

                return dto;
        }
}
