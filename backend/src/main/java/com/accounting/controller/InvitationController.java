package com.accounting.controller;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AcceptInvitationRequest;
import com.accounting.dto.AuthResponse;
import com.accounting.dto.CreateInvitationRequest;
import com.accounting.dto.InvitationResponse;
import com.accounting.entity.Invitation;
import com.accounting.entity.User;
import com.accounting.repository.CompanyRepository;
import com.accounting.security.JwtTokenProvider;
import com.accounting.service.InvitationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST controller for invitation operations.
 * POST /api/v1/invitations requires ADMIN or CHIEF_ACCOUNTANT role.
 * GET and POST /accept endpoints are public (no authentication required).
 */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

  private final InvitationService invitationService;
  private final CompanyRepository companyRepository;
  private final JwtTokenProvider jwtTokenProvider;

  public InvitationController(
      InvitationService invitationService,
      CompanyRepository companyRepository,
      JwtTokenProvider jwtTokenProvider) {
    this.invitationService = invitationService;
    this.companyRepository = companyRepository;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  /**
   * Create a new invitation.
   * Requires ADMIN or CHIEF_ACCOUNTANT role.
   */
  @PostMapping
  @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> createInvitation(
      @Valid @RequestBody CreateInvitationRequest request, HttpServletRequest httpRequest) {

    // Get current user ID from security context
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Long currentUserId = Long.parseLong(authentication.getPrincipal().toString());

    Invitation invitation =
        invitationService.createInvitation(
            request.getEmail(), request.getRole(), currentUserId, httpRequest);

    Map<String, Object> responseData = new HashMap<>();
    responseData.put("invitationToken", invitation.getToken());
    responseData.put("expiresAt", invitation.getExpiresAt());

    Map<String, Object> body = new HashMap<>();
    body.put("data", responseData);
    return ResponseEntity.created(URI.create("/api/v1/invitations/" + invitation.getToken()))
        .body(body);
  }

  /**
   * List all invitations for the current company.
   * Requires ADMIN or CHIEF_ACCOUNTANT role.
   */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> listInvitations() {
    List<Invitation> invitations = invitationService.listInvitations();

    List<Map<String, Object>> invitationData =
        invitations.stream()
            .map(
                invitation -> {
                  Map<String, Object> data = new HashMap<>();
                  data.put("id", invitation.getId());
                  data.put("email", invitation.getEmail());
                  data.put("role", invitation.getRole());
                  data.put("status", invitation.getStatus());
                  data.put("expiresAt", invitation.getExpiresAt());
                  data.put("createdAt", invitation.getCreatedAt());
                  return data;
                })
            .collect(Collectors.toList());

    Map<String, Object> body = new HashMap<>();
    body.put("data", invitationData);
    return ResponseEntity.ok(body);
  }

  /**
   * Validate and retrieve invitation details.
   * Public endpoint - no authentication required.
   */
  @GetMapping("/{token}")
  public ResponseEntity<Map<String, Object>> getInvitation(@PathVariable String token) {
    Invitation invitation = invitationService.validateInvitation(token);

    String companyName =
        companyRepository
            .findById(invitation.getCompanyId())
            .map(com.accounting.entity.Company::getName)
            .orElse("Company");

    InvitationResponse response =
        new InvitationResponse(
            invitation.getEmail(),
            companyName,
            invitation.getRole(),
            invitation.getExpiresAt());

    Map<String, Object> body = new HashMap<>();
    body.put("data", response);
    return ResponseEntity.ok(body);
  }

  /**
   * Accept invitation and create user account.
   * Public endpoint - no authentication required.
   */
  @PostMapping("/{token}/accept")
  public ResponseEntity<Map<String, Object>> acceptInvitation(
      @PathVariable String token,
      @Valid @RequestBody AcceptInvitationRequest request,
      HttpServletRequest httpRequest) {

    // Validate password confirmation
    if (!request.getPassword().equals(request.getConfirmPassword())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Password and confirmation do not match");
    }

    User user =
        invitationService.acceptInvitation(
            token, request.getPassword(), request.getFullName(), httpRequest);

    // Generate JWT token for automatic login
    String accessToken =
        jwtTokenProvider.generateAccessToken(
            user.getId(), user.getEmail(), user.getRole());

    AuthResponse.UserResponse userResponse =
        new AuthResponse.UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.getCompanyId());

    AuthResponse authResponse = new AuthResponse();
    authResponse.setAccessToken(accessToken);
    authResponse.setUser(userResponse);

    Map<String, Object> body = new HashMap<>();
    body.put("data", authResponse);
    return ResponseEntity.ok(body);
  }
}
