package com.accounting.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.entity.User;
import com.accounting.repository.UserRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Integration tests for JWT authentication filter role handling.
 * Tests that missing role in JWT returns 401 Unauthorized.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationFilterTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  @Test
  void shouldExtractRoleFromToken() {
    // Test that JWT token provider includes role in token
    String token = jwtTokenProvider.generateAccessToken(1L, "test@example.com", "admin");
    assertNotNull(token);

    String role = jwtTokenProvider.getRoleFromToken(token);
    assertEquals("admin", role);
  }

  @Test
  void shouldHandleMissingRoleInToken() throws Exception {
    User user = new User();
    user.setEmail("test@example.com");
    user.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
    user.setFullName("Test User");
    user.setRole("accountant");
    user.setFailedLoginCount(0);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    user = userRepository.save(user);

    // Create token with role (normal case)
    String tokenWithRole = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());

    // Test that valid token with role works (returns 403 because accountant can't access users endpoint)
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", "Bearer " + tokenWithRole))
        .andExpect(status().isForbidden()); // Forbidden because accountant can't access, confirms role was extracted

    // Create a token without role claim to test 401 response
    // Note: Using a manually constructed JWT without role claim
    // Using the same default secret as the application (from application.yml default)
    String secret = "your-secret-key-change-this-in-production-minimum-256-bits";
    Key key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", user.getId().toString());
    claims.put("email", user.getEmail());
    // Intentionally omitting "role" claim

    String tokenWithoutRole =
        Jwts.builder()
            .setClaims(claims)
            .setSubject(user.getId().toString())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 24 hours
            .signWith(key)
            .compact();

    // Test that token without role returns 401 Unauthorized
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", "Bearer " + tokenWithoutRole))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(
            jsonPath("$.error.message")
                .value(org.hamcrest.Matchers.containsStringIgnoringCase("role")));
  }
}
