package com.accounting.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Utility class for creating and manipulating JWT tokens in security tests.
 * Used to test JWT tampering, expiration, and signature verification scenarios.
 */
public class JwtTestUtils {

    private static final String TENANT_GROUP_PREFIX = "company_";

    private final SecretKey signingKey;

    public JwtTestUtils(String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a valid JWT token for Metabase embed with locked company_id.
     */
    public String createValidEmbedJwt(Long companyId, String email, String role, int expiryMinutes) {
        List<String> groups = List.of(
                TENANT_GROUP_PREFIX + companyId,
                getMetabaseGroupForRole(role)
        );

        Instant expiration = Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .claim("email", email)
                .claim("first_name", "Test")
                .claim("last_name", "User")
                .claim("groups", groups)
                .claim("company_id", companyId)
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Creates an expired JWT token (expired 1 hour ago).
     */
    public String createExpiredJwt(Long companyId, String email, String role) {
        List<String> groups = List.of(
                TENANT_GROUP_PREFIX + companyId,
                getMetabaseGroupForRole(role)
        );

        Instant expiration = Instant.now().minus(1, ChronoUnit.HOURS);

        return Jwts.builder()
                .claim("email", email)
                .claim("first_name", "Test")
                .claim("last_name", "User")
                .claim("groups", groups)
                .claim("company_id", companyId)
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Tampers with the JWT payload by modifying the company_id.
     * This creates an invalid token because the signature won't match.
     * 
     * @param validJwt A valid JWT token
     * @param newCompanyId The new company_id to inject
     * @return A tampered JWT with modified payload but original signature
     */
    public String tamperCompanyId(String validJwt, Long newCompanyId) {
        String[] parts = validJwt.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        String header = parts[0];
        String payload = parts[1];
        String signature = parts[2];

        // Decode payload
        String decodedPayload = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);

        // Replace company_id in the payload
        String tamperedPayload = decodedPayload.replaceAll(
                "\"company_id\":\\d+",
                "\"company_id\":" + newCompanyId
        );

        // Also replace the group
        tamperedPayload = tamperedPayload.replaceAll(
                "\"company_\\d+\"",
                "\"company_" + newCompanyId + "\""
        );

        // Re-encode the tampered payload (no padding)
        String encodedTamperedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8));

        // Return tampered JWT with original signature (this will fail verification)
        return header + "." + encodedTamperedPayload + "." + signature;
    }

    /**
     * Creates a JWT signed with a different (wrong) secret.
     */
    public String createJwtWithWrongSecret(Long companyId, String email, String role) {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-key-for-testing-purposes-minimum-256-bits".getBytes(StandardCharsets.UTF_8));

        List<String> groups = List.of(
                TENANT_GROUP_PREFIX + companyId,
                getMetabaseGroupForRole(role)
        );

        Instant expiration = Instant.now().plus(60, ChronoUnit.MINUTES);

        return Jwts.builder()
                .claim("email", email)
                .claim("first_name", "Test")
                .claim("last_name", "User")
                .claim("groups", groups)
                .claim("company_id", companyId)
                .expiration(Date.from(expiration))
                .signWith(wrongKey)
                .compact();
    }

    /**
     * Validates JWT signature and expiration.
     */
    public boolean validateJwt(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts the company_id claim from a valid JWT.
     */
    public Long extractCompanyId(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("company_id", Long.class);
    }

    private String getMetabaseGroupForRole(String role) {
        return switch (role.toUpperCase()) {
            case "ADMIN" -> "Analytics Admins";
            case "CHIEF_ACCOUNTANT" -> "Analytics Power Users";
            case "CFO" -> "Analytics Viewers";
            case "ACCOUNTANT", "ACCOUNTANT_GENERAL" -> "Analytics Basic";
            case "ACCOUNTANT_AR" -> "Analytics AR";
            case "ACCOUNTANT_AP" -> "Analytics AP";
            case "CASHIER" -> "Analytics Cashier";
            default -> "Analytics Basic";
        };
    }
}
