package com.accounting.service.analytics;

import org.springframework.stereotype.Component;

@Component
public class AnalyticsCacheKeyGenerator {

    public static final String PREFIX = "analytics";

    public String freshness(Long companyId) {
        return PREFIX + ":" + companyId + ":freshness";
    }

    public String embedConfig(Long companyId, String dashboardKey) {
        return PREFIX + ":" + companyId + ":embed_config:" + dashboardKey;
    }

    public String coaMapping(Long companyId) {
        return PREFIX + ":" + companyId + ":coa_mapping";
    }

    public String etlLock(Long companyId) {
        return PREFIX + ":" + companyId + ":etl_lock";
    }

    public String widgetCache(Long companyId, String widgetType) {
        return PREFIX + ":" + companyId + ":widget:" + widgetType;
    }

    public String cacheMetrics(String metricType, String widgetType) {
        return PREFIX + ":cache:metrics:" + metricType + ":" + widgetType;
    }

    public String companyWidgetPattern(Long companyId) {
        return PREFIX + ":" + companyId + ":widget:*";
    }
}
