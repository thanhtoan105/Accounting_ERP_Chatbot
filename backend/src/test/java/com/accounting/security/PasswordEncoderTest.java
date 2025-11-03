package com.accounting.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordEncoderTest {

  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    passwordEncoder = new PasswordEncoder();
  }

  @Test
  void encode_shouldHashPassword() {
    String rawPassword = "TestPassword123!";
    String hashed = passwordEncoder.encode(rawPassword);

    assertNotEquals(rawPassword, hashed);
    assertTrue(hashed.length() > 0);
  }

  @Test
  void encode_shouldGenerateDifferentHashForSamePassword() {
    String rawPassword = "TestPassword123!";
    String hashed1 = passwordEncoder.encode(rawPassword);
    String hashed2 = passwordEncoder.encode(rawPassword);

    assertNotEquals(hashed1, hashed2);
  }

  @Test
  void matches_shouldReturnTrueForCorrectPassword() {
    String rawPassword = "TestPassword123!";
    String hashed = passwordEncoder.encode(rawPassword);

    assertTrue(passwordEncoder.matches(rawPassword, hashed));
  }

  @Test
  void matches_shouldReturnFalseForIncorrectPassword() {
    String rawPassword = "TestPassword123!";
    String wrongPassword = "WrongPassword123!";
    String hashed = passwordEncoder.encode(rawPassword);

    assertFalse(passwordEncoder.matches(wrongPassword, hashed));
  }

  @Test
  void matches_shouldReturnFalseForDifferentPassword() {
    String password1 = "TestPassword123!";
    String password2 = "AnotherPassword456@";
    String hashed1 = passwordEncoder.encode(password1);

    assertFalse(passwordEncoder.matches(password2, hashed1));
  }
}

