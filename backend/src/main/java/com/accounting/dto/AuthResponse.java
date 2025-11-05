package com.accounting.dto;

public class AuthResponse {

  private String accessToken;
  private UserResponse user;

  public AuthResponse() {}

  public AuthResponse(String accessToken, UserResponse user) {
    this.accessToken = accessToken;
    this.user = user;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  public UserResponse getUser() {
    return user;
  }

  public void setUser(UserResponse user) {
    this.user = user;
  }

  public static class UserResponse {
    private Long id;
    private String email;
    private String fullName;
    private String role;
    private Long companyId;

    public UserResponse() {}

    public UserResponse(Long id, String email, String fullName, String role, Long companyId) {
      this.id = id;
      this.email = email;
      this.fullName = fullName;
      this.role = role;
      this.companyId = companyId;
    }

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getFullName() {
      return fullName;
    }

    public void setFullName(String fullName) {
      this.fullName = fullName;
    }

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public Long getCompanyId() {
      return companyId;
    }

    public void setCompanyId(Long companyId) {
      this.companyId = companyId;
    }
  }
}

