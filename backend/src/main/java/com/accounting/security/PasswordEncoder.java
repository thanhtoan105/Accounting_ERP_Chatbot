package com.accounting.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordEncoder implements org.springframework.security.crypto.password.PasswordEncoder {

  private final BCryptPasswordEncoder bcryptEncoder;

  public PasswordEncoder() {
    this.bcryptEncoder = new BCryptPasswordEncoder(12);
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return bcryptEncoder.encode(rawPassword);
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    return bcryptEncoder.matches(rawPassword, encodedPassword);
  }
}
