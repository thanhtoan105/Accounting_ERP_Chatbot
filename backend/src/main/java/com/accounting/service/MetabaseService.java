package com.accounting.service;

import java.util.Map;

/**
 * Service for Metabase integration and JWT token generation.
 */
public interface MetabaseService {

    /**
     * Generate a JWT token for embedded Metabase dashboard.
     * The token will include the current company context to filter data.
     *
     * @param dashboardId The Metabase dashboard ID to embed
     * @return JWT token for signed embedding
     */
    String generateEmbeddingToken(Integer dashboardId);

    /**
     * Generate a JWT token with custom parameters for embedded Metabase dashboard.
     *
     * @param dashboardId The Metabase dashboard ID to embed
     * @param params Additional parameters to pass to the dashboard
     * @return JWT token for signed embedding
     */
    String generateEmbeddingToken(Integer dashboardId, Map<String, Object> params);

    /**
     * Get the full iframe URL for embedded Metabase dashboard.
     *
     * @param dashboardId The Metabase dashboard ID to embed
     * @return Complete iframe URL with signed token
     */
    String getEmbeddedDashboardUrl(Integer dashboardId);
}
