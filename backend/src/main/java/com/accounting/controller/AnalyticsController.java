package com.accounting.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.accounting.service.MetabaseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for analytics and BI dashboard operations.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "BI Dashboard and Analytics APIs")
public class AnalyticsController {

    private final MetabaseService metabaseService;

    public AnalyticsController(MetabaseService metabaseService) {
        this.metabaseService = metabaseService;
    }

    /**
     * Generate a signed JWT token for embedded Metabase dashboard.
     * The token includes the current company context for data filtering.
     *
     * @param dashboardId The Metabase dashboard ID
     * @return JWT token for embedding
     */
    @GetMapping("/metabase/token/{dashboardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Generate Metabase embedding token",
               description = "Generate a signed JWT token for embedding Metabase dashboards with company-scoped data")
    public ResponseEntity<Map<String, String>> getEmbeddingToken(
        @PathVariable Integer dashboardId
    ) {
        String token = metabaseService.generateEmbeddingToken(dashboardId);
        return ResponseEntity.ok(Map.of(
            "token", token,
            "dashboardId", dashboardId.toString()
        ));
    }

    /**
     * Get the complete iframe URL for embedded Metabase dashboard.
     *
     * @param dashboardId The Metabase dashboard ID
     * @return Full iframe URL with signed token
     */
    @GetMapping("/metabase/url/{dashboardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Get embedded dashboard URL",
               description = "Get the complete iframe URL for embedding Metabase dashboards")
    public ResponseEntity<Map<String, String>> getEmbeddedDashboardUrl(
        @PathVariable Integer dashboardId
    ) {
        String url = metabaseService.getEmbeddedDashboardUrl(dashboardId);
        return ResponseEntity.ok(Map.of(
            "url", url,
            "dashboardId", dashboardId.toString()
        ));
    }
}
