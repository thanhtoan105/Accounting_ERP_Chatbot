package com.accounting.entity.analytics;

/**
 * Types of analytics widgets available in the dashboard.
 * Maps to Vietnamese TT200 accounting report categories.
 */
public enum WidgetType {
    REVENUE_EXPENSE("revenue-vs-expenses"),
    AR_BALANCES("ar-ap-balances"),
    AP_BALANCES("ar-ap-balances"),
    CASH_POSITION("cash-position"),
    TOP_DEBTORS("top-5-debtors"),
    TOP_CREDITORS("top-5-creditors"),
    PERIOD_SUMMARY("period-summary");

    private final String widgetKey;

    WidgetType(String widgetKey) {
        this.widgetKey = widgetKey;
    }

    public String getWidgetKey() {
        return widgetKey;
    }

    public static WidgetType fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (WidgetType type : values()) {
            if (type.widgetKey.equals(key)) {
                return type;
            }
        }
        return null;
    }
}
