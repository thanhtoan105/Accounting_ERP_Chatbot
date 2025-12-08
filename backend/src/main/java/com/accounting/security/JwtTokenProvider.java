package com.accounting.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

  private final SecretKey secretKey;
  private final long accessTokenValidityInMinutes;
  private final long refreshTokenValidityInDays;

  public JwtTokenProvider(
      @Value("${jwt.secret:your-secret-key-change-this-in-production-minimum-256-bits}")
          String secret,
      @Value("${jwt.access-token-validity-minutes:30}") long accessTokenValidityInMinutes,
      @Value("${jwt.refresh-token-validity-days:7}") long refreshTokenValidityInDays) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenValidityInMinutes = accessTokenValidityInMinutes;
    this.refreshTokenValidityInDays = refreshTokenValidityInDays;
  }

  public String generateAccessToken(Long userId, String email, String role) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(accessTokenValidityInMinutes * 60);

    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("email", email)
        .claim("role", role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(secretKey)
        .compact();
  }

  public String generateRefreshToken(Long userId, String email, boolean rememberMe) {
    Instant now = Instant.now();
    long validityDays = rememberMe ? 30 : refreshTokenValidityInDays;
    Instant expiry = now.plusSeconds(validityDays * 24 * 60 * 60);

    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("email", email)
        .claim("type", "refresh")
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(secretKey)
        .compact();
  }

  public boolean validateToken(String token) {
    try {
      Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public Claims getClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  public Long getUserIdFromToken(String token) {
    Claims claims = getClaims(token);
    return Long.parseLong(claims.getSubject());
  }

  public String getEmailFromToken(String token) {
    Claims claims = getClaims(token);
    return claims.get("email", String.class);
  }

  public String getRoleFromToken(String token) {
    Claims claims = getClaims(token);
    return claims.get("role", String.class);
  }
}
