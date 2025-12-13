package com.accounting.dto;

/** Response DTO for invitation validation (public endpoint). */
public record InvitationValidateResponse(
    boolean valid,
    String email,
    String companyName,
    String role,
    String expiresAt,
    String status,
    String errorMessage) {

  public static InvitationValidateResponse valid(
      String email, String companyName, String role, String expiresAt) {
    return new InvitationValidateResponse(true, email, companyName, role, expiresAt, "PENDING", null);
  }

  public static InvitationValidateResponse invalid(String errorMessage) {
    return new InvitationValidateResponse(false, null, null, null, null, null, errorMessage);
  }
}
