package com.accounting.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

public record MultiCurrencyReconciliationResult(
        String checkType,
        List<CurrencyVariance> currencyVariances,
        boolean allPassed,
        String message) {

    public record CurrencyVariance(
            String currencyCode,
            BigDecimal mvTotal,
            BigDecimal glTotal,
            BigDecimal variance,
            boolean passed) {

        public static CurrencyVariance of(String currencyCode, BigDecimal mvTotal,
                BigDecimal glTotal, BigDecimal tolerance) {
            BigDecimal variance = mvTotal.subtract(glTotal).abs();
            boolean passed = variance.compareTo(tolerance) <= 0;
            return new CurrencyVariance(currencyCode, mvTotal, glTotal, variance, passed);
        }
    }

    public static MultiCurrencyReconciliationResult of(String checkType,
            List<CurrencyVariance> variances) {
        boolean allPassed = variances.isEmpty() || variances.stream().allMatch(CurrencyVariance::passed);
        long failedCount = variances.stream().filter(v -> !v.passed()).count();
        String message = allPassed
                ? String.format("%s multi-currency reconciliation passed (%d currencies)", checkType, variances.size())
                : String.format("%s multi-currency reconciliation FAILED: %d of %d currencies out of balance",
                        checkType, failedCount, variances.size());
        return new MultiCurrencyReconciliationResult(checkType, variances, allPassed, message);
    }
}
