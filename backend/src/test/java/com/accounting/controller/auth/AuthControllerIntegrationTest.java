package com.accounting.controller.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.entity.User;
import com.accounting.repository.UserRepository;
import com.accounting.security.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  private User createUser(String email, String password, String fullName) {
    User user = new User();
    user.setEmail(email.toLowerCase());
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setFullName(fullName);
    user.setRole("USER");
    user.setCompanyId(null);
    user.setFailedLoginCount(0);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    return userRepository.save(user);
  }

  @Test
  void login_shouldReturnTokensOnSuccess() throws Exception {
    createUser("login@example.com", "TestPassword123!", "Login User");

    Map<String, Object> loginBody = new HashMap<>();
    loginBody.put("email", "login@example.com");
    loginBody.put("password", "TestPassword123!");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").exists())
        .andExpect(jsonPath("$.data.user.email").value("login@example.com"))
        .andExpect(cookie().exists("refreshToken"));
  }

  @Test
  void login_shouldReturn401ForInvalidCredentials() throws Exception {
    Map<String, Object> loginBody = new HashMap<>();
    loginBody.put("email", "nonexistent@example.com");
    loginBody.put("password", "WrongPassword123!");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void login_shouldLockAccountAfter5FailedAttempts() throws Exception {
    createUser("lockout@example.com", "TestPassword123!", "Lockout User");

    Map<String, Object> loginBody = new HashMap<>();
    loginBody.put("email", "lockout@example.com");
    loginBody.put("password", "WrongPassword123!");

    for (int i = 0; i < 5; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(loginBody)))
          .andExpect(status().isUnauthorized());
    }

    loginBody.put("password", "TestPassword123!");
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
        .andExpect(status().isLocked())
        .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
  }

  @Test
  void forgotPassword_shouldAcceptValidEmail() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("email", "forgot@example.com");

    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.message").exists());
  }
}
