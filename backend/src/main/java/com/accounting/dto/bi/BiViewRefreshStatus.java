package com.accounting.dto.bi;

import java.time.Instant;
import java.util.Map;

public record BiViewRefreshStatus(
    Instant lastRefresh,
    Map<String, ViewStatus> viewStatuses
) {
    public record ViewStatus(
        String viewName,
        Instant lastRefresh,
        Long rowCount,
        Long refreshDurationMs,
        String error
    ) {
        public boolean isSuccess() {
            return error == null;
        }
    }
}
