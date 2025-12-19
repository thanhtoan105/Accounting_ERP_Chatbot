package com.accounting.dto.analytics;

import java.math.BigDecimal;

public record ReconciliationResult(
        String checkType,
        BigDecimal mvTotal,
        BigDecimal glTotal,
        BigDecimal variance,
        boolean passed,
        String message) {

    public static ReconciliationResult of(String checkType, BigDecimal mvTotal, BigDecimal glTotal,
            BigDecimal toleranceVnd) {
        BigDecimal variance = mvTotal.subtract(glTotal).abs();
        boolean passed = variance.compareTo(toleranceVnd) <= 0;
        String message = passed
                ? String.format("%s reconciliation passed (variance: %s VND)", checkType, variance)
                : String.format("%s reconciliation FAILED (variance: %s VND exceeds tolerance %s VND)",
                        checkType, variance, toleranceVnd);
        return new ReconciliationResult(checkType, mvTotal, glTotal, variance, passed, message);
    }
}
