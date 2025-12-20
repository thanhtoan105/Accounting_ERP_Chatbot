package com.accounting.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.security.CompanyContext;
import com.accounting.service.MetabaseService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Implementation of MetabaseService for JWT token generation and embedding.
 */
@Service
public class MetabaseServiceImpl implements MetabaseService {

    @Value("${metabase.embedding-secret}")
    private String embeddingSecret;

    @Value("${metabase.site-url}")
    private String metabaseSiteUrl;

    @Override
    public String generateEmbeddingToken(Integer dashboardId) {
        return generateEmbeddingToken(dashboardId, new HashMap<>());
    }

    @Override
    public String generateEmbeddingToken(Integer dashboardId, Map<String, Object> params) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Company context is required for Metabase embedding"
            );
        }

        // Build the resource map
        Map<String, Object> resource = new HashMap<>();
        resource.put("dashboard", dashboardId);

        // Start with any additional params (e.g., date range) but enforce company_id
        Map<String, Object> embeddingParams = new HashMap<>(params != null ? params : Map.of());

        // Enforce tenant isolation: company_id is always set from CompanyContext
        // IMPORTANT: The dashboard must have a filter parameter named "company_id"
        // set to "Locked" in Metabase's embedding settings
        embeddingParams.put("company_id", companyId);

        // Build the JWT payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("resource", resource);
        payload.put("params", embeddingParams);

        // Set expiration to 10 minutes from now
        Instant expiration = Instant.now().plus(10, ChronoUnit.MINUTES);

        // Create signing key from the embedding secret
        SecretKey signingKey = Keys.hmacShaKeyFor(embeddingSecret.getBytes());

        // Generate the JWT token
        return Jwts.builder()
            .claims(payload)
            .expiration(java.util.Date.from(expiration))
            .signWith(signingKey)
            .compact();
    }

    @Override
    public String getEmbeddedDashboardUrl(Integer dashboardId) {
        String token = generateEmbeddingToken(dashboardId);
        return String.format(
            "%s/embed/dashboard/%s#bordered=true&titled=true",
            metabaseSiteUrl,
            token
        );
    }
}
