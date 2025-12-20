package com.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request DTO for accepting an invitation. */
public record InvitationAcceptRequest(
    @NotBlank(message = "Token is required") String token,
    @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
            message = "Password must contain uppercase, lowercase, and digit")
        String password,
    @NotBlank(message = "Password confirmation is required") String passwordConfirm,
    @Size(max = 255, message = "Full name must not exceed 255 characters") String fullName) {

  public boolean passwordsMatch() {
    return password != null && password.equals(passwordConfirm);
  }
}
