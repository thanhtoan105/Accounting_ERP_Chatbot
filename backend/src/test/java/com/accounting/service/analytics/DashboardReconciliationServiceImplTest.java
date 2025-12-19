package com.accounting.service.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.accounting.dto.analytics.MultiCurrencyReconciliationResult;
import com.accounting.dto.analytics.MultiCurrencyReconciliationResult.CurrencyVariance;
import com.accounting.dto.analytics.ReconciliationResult;

@ExtendWith(MockitoExtension.class)
class DashboardReconciliationServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DashboardReconciliationServiceImpl service;

    private static final Long COMPANY_ID = 1L;
    private static final UUID PERIOD_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DashboardReconciliationServiceImpl(jdbcTemplate);
    }

    @Test
    void checkARReconciliation_whenBalanced_shouldPass() {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(COMPANY_ID), eq(PERIOD_ID)))
                .thenReturn(new BigDecimal("1000000"));

        ReconciliationResult result = service.checkARReconciliation(COMPANY_ID, PERIOD_ID);

        assertTrue(result.passed());
        assertEquals(BigDecimal.ZERO, result.variance());
        assertEquals("AR", result.checkType());
    }

    @Test
    void checkARReconciliation_whenVarianceWithinTolerance_shouldPass() {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(COMPANY_ID), eq(PERIOD_ID)))
                .thenReturn(new BigDecimal("1000000"))
                .thenReturn(new BigDecimal("1000001"));

        ReconciliationResult result = service.checkARReconciliation(COMPANY_ID, PERIOD_ID);

        assertTrue(result.passed());
        assertEquals(BigDecimal.ONE, result.variance());
    }

    @Test
    void checkARReconciliation_whenVarianceExceedsTolerance_shouldFail() {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(COMPANY_ID), eq(PERIOD_ID)))
                .thenReturn(new BigDecimal("1000000"))
                .thenReturn(new BigDecimal("1000010"));

        ReconciliationResult result = service.checkARReconciliation(COMPANY_ID, PERIOD_ID);

        assertFalse(result.passed());
        assertEquals(new BigDecimal("10"), result.variance());
    }

    @Test
    void checkARReconciliationByCurrency_whenAllCurrenciesBalanced_shouldPass() throws SQLException {
        setupCurrencyQueryMock(
                new String[] {"VND"},
                new BigDecimal[] {new BigDecimal("1000000")},
                new String[] {"VND"},
                new BigDecimal[] {new BigDecimal("1000000")});

        MultiCurrencyReconciliationResult result = service.checkARReconciliationByCurrency(COMPANY_ID, PERIOD_ID);

        assertTrue(result.allPassed());
        assertEquals(1, result.currencyVariances().size());
        CurrencyVariance vnd = result.currencyVariances().get(0);
        assertEquals("VND", vnd.currencyCode());
        assertEquals(BigDecimal.ZERO, vnd.variance());
    }

    @Test
    void checkARReconciliationByCurrency_whenMultipleCurrencies_shouldValidateEach() throws SQLException {
        setupCurrencyQueryMock(
                new String[] {"VND", "USD"},
                new BigDecimal[] {new BigDecimal("1000000"), new BigDecimal("5000")},
                new String[] {"VND", "USD"},
                new BigDecimal[] {new BigDecimal("1000000"), new BigDecimal("5000")});

        MultiCurrencyReconciliationResult result = service.checkARReconciliationByCurrency(COMPANY_ID, PERIOD_ID);

        assertTrue(result.allPassed());
        assertEquals(2, result.currencyVariances().size());
    }

    @Test
    void checkARReconciliationByCurrency_whenOneCurrencyFails_shouldReportFailure() throws SQLException {
        setupCurrencyQueryMock(
                new String[] {"VND", "USD"},
                new BigDecimal[] {new BigDecimal("1000000"), new BigDecimal("5000")},
                new String[] {"VND", "USD"},
                new BigDecimal[] {new BigDecimal("1000000"), new BigDecimal("5100")});

        MultiCurrencyReconciliationResult result = service.checkARReconciliationByCurrency(COMPANY_ID, PERIOD_ID);

        assertFalse(result.allPassed());
        assertTrue(result.message().contains("1 of 2 currencies out of balance"));
    }

    @Test
    void checkRevenueReconciliationByCurrency_whenBalanced_shouldPass() throws SQLException {
        setupCurrencyQueryMock(
                new String[] {"VND"},
                new BigDecimal[] {new BigDecimal("500000")},
                new String[] {"VND"},
                new BigDecimal[] {new BigDecimal("500000")});

        MultiCurrencyReconciliationResult result =
                service.checkRevenueReconciliationByCurrency(COMPANY_ID, PERIOD_ID);

        assertTrue(result.allPassed());
        assertEquals("REVENUE", result.checkType());
    }

    @Test
    void checkARReconciliationByCurrency_whenCurrencyMissingInGL_shouldTreatAsZero() throws SQLException {
        setupCurrencyQueryMock(
                new String[] {"VND", "EUR"},
                new BigDecimal[] {new BigDecimal("1000000"), new BigDecimal("100")},
                new String[] {"VND"},
                new BigDecimal[] {new BigDecimal("1000000")});

        MultiCurrencyReconciliationResult result = service.checkARReconciliationByCurrency(COMPANY_ID, PERIOD_ID);

        assertFalse(result.allPassed());
        assertEquals(2, result.currencyVariances().size());

        CurrencyVariance eurVariance = result.currencyVariances().stream()
                .filter(v -> "EUR".equals(v.currencyCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(eurVariance);
        assertEquals(BigDecimal.ZERO, eurVariance.glTotal());
        assertEquals(new BigDecimal("100"), eurVariance.variance());
    }

    @Test
    void currencyVariance_of_shouldCalculateCorrectly() {
        CurrencyVariance variance =
                CurrencyVariance.of("USD", new BigDecimal("1000"), new BigDecimal("999"), BigDecimal.ONE);

        assertTrue(variance.passed());
        assertEquals(BigDecimal.ONE, variance.variance());
    }

    @Test
    void multiCurrencyReconciliationResult_emptyList_shouldPass() {
        MultiCurrencyReconciliationResult result =
                MultiCurrencyReconciliationResult.of("TEST", java.util.Collections.emptyList());

        assertTrue(result.allPassed());
        assertTrue(result.message().contains("0 currencies"));
    }

    private void setupCurrencyQueryMock(
            String[] mvCurrencies,
            BigDecimal[] mvTotals,
            String[] glCurrencies,
            BigDecimal[] glTotals)
            throws SQLException {
        doAnswer(invocation -> {
                    RowCallbackHandler handler = invocation.getArgument(1);
                    for (int i = 0; i < mvCurrencies.length; i++) {
                        ResultSet rs = Mockito.mock(ResultSet.class);
                        when(rs.getString("currency_code")).thenReturn(mvCurrencies[i]);
                        when(rs.getBigDecimal("total")).thenReturn(mvTotals[i]);
                        handler.processRow(rs);
                    }
                    return null;
                })
                .doAnswer(invocation -> {
                    RowCallbackHandler handler = invocation.getArgument(1);
                    for (int i = 0; i < glCurrencies.length; i++) {
                        ResultSet rs = Mockito.mock(ResultSet.class);
                        when(rs.getString("currency_code")).thenReturn(glCurrencies[i]);
                        when(rs.getBigDecimal("total")).thenReturn(glTotals[i]);
                        handler.processRow(rs);
                    }
                    return null;
                })
                .when(jdbcTemplate)
                .query(anyString(), any(RowCallbackHandler.class), eq(COMPANY_ID), eq(PERIOD_ID));
    }
}
