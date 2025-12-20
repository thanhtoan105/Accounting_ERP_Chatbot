package com.accounting.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.entity.Company;
import com.accounting.entity.Invitation;
import com.accounting.entity.User;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.InvitationRepository;
import com.accounting.repository.ScopedSpecifications;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.PasswordEncoder;
import com.accounting.service.AuditService;
import com.accounting.service.EmailService;
import com.accounting.service.InvitationService;
import com.accounting.service.RoleService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Implementation of InvitationService for invitation management.
 */
@Service
@Transactional
public class InvitationServiceImpl implements InvitationService {

  private static final int INVITATION_TOKEN_LENGTH = 32;
  private static final int INVITATION_VALIDITY_DAYS = 1; // 24 hours per proposal
  private static final String HASH_ALGORITHM = "SHA-256";

  private final InvitationRepository invitationRepository;
  private final UserRepository userRepository;
  private final CompanyRepository companyRepository;
  private final RoleService roleService;
  private final EmailService emailService;
  private final AuditService auditService;
  private final PasswordEncoder passwordEncoder;
  private final SecureRandom secureRandom;

  public InvitationServiceImpl(
      InvitationRepository invitationRepository,
      UserRepository userRepository,
      CompanyRepository companyRepository,
      RoleService roleService,
      EmailService emailService,
      AuditService auditService,
      PasswordEncoder passwordEncoder) {
    this.invitationRepository = invitationRepository;
    this.userRepository = userRepository;
    this.companyRepository = companyRepository;
    this.roleService = roleService;
    this.emailService = emailService;
    this.auditService = auditService;
    this.passwordEncoder = passwordEncoder;
    this.secureRandom = new SecureRandom();
  }

  @Override
  public Invitation createInvitation(
      String email, String role, Long createdByUserId, HttpServletRequest httpRequest) {

    // Validate email doesn't already exist
    if (userRepository.existsByEmail(email)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "User with this email already exists");
    }

    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Company context is required");
    }

    // Check for existing pending invitation
    invitationRepository
        .findByEmailAndCompanyId(email, companyId)
        .ifPresent(
            existing -> {
              if (existing.isPending() && !existing.isExpired()) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A pending invitation already exists for this email and company");
              }
            });

    // Validate role or use default
    String roleValue = role;
    if (roleValue == null || roleValue.isBlank()) {
      roleValue = roleService.getDefaultRole().getValue();
    } else if (!roleService.isValidRole(roleValue)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid role. Must be one of: admin, accountant, chief_accountant, cfo");
    }

    // Generate secure token (UUID v4 format, base64 encoded)
    String plainToken = generateSecureToken();
    String tokenHash = hashToken(plainToken);

    // Set expiration (24h from now per proposal)
    Instant expiresAt = Instant.now().plusSeconds(INVITATION_VALIDITY_DAYS * 24 * 60 * 60);

    // Create invitation
    Invitation invitation = new Invitation();
    invitation.setEmail(email);
    invitation.setToken(plainToken); // Store plain token temporarily for email
    invitation.setTokenHash(tokenHash); // Store hash for secure lookup
    invitation.setCompanyId(companyId);
    invitation.setCreatedBy(createdByUserId);
    invitation.setInvitedBy(createdByUserId); // New field for audit
    invitation.setRole(roleValue);
    invitation.setExpiresAt(expiresAt);
    invitation.setStatus("PENDING");
    invitation.setCreatedAt(Instant.now());
    invitation.setUpdatedAt(Instant.now());

    Invitation savedInvitation = invitationRepository.save(invitation);

    // Send invitation email with plain token
    try {
      emailService.sendInvitationEmail(
          email,
          plainToken,
          getUserName(createdByUserId),
          getCompanyName(companyId),
          roleValue,
          expiresAt,
          httpRequest);
    } catch (Exception e) {
      // Log error but don't fail invitation creation
      // In production, consider retry mechanism
    }

    // Clear plain token after email sent - only hash should remain
    // Note: We keep token for backwards compatibility, but tokenHash is the secure reference
    
    // Log invitation creation in audit trail
    // Logging must occur after invitation is saved (for transactional safety)
    if (httpRequest != null) {
      auditService.logInvitationCreated(
          createdByUserId, email, companyId, roleValue, httpRequest);
    }

    return savedInvitation;
  }

  @Override
  public Invitation validateInvitation(String token) {
    // Hash the incoming token for secure lookup
    String tokenHash = hashToken(token);
    
    // First try hash-based lookup (preferred), fallback to plain token for backwards compat
    Invitation invitation =
        invitationRepository
            .findByTokenHash(tokenHash)
            .or(() -> invitationRepository.findByToken(token))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Invalid invitation token"));

    // Check if revoked
    if (invitation.isRevoked()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation has been revoked");
    }

    // Check if already accepted
    if (invitation.isAccepted()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation has already been used");
    }

    // Check if expired
    if (invitation.isExpired() && invitation.isPending()) {
      invitation.setStatus("EXPIRED");
      invitation.setUpdatedAt(Instant.now());
      invitationRepository.save(invitation);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation has expired");
    }

    // Check if already accepted or cancelled
    if (!invitation.isPending()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invitation is no longer valid");
    }

    return invitation;
  }

  @Override
  public User acceptInvitation(
      String token, String password, String fullName, HttpServletRequest httpRequest) {

    // Validate invitation
    Invitation invitation = validateInvitation(token);

    // Check if user already exists
    if (userRepository.existsByEmail(invitation.getEmail())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "User with this email already exists");
    }

    // Set company context from invitation for CompanyScopeEnforcer
    // This is needed because invitation acceptance is a public endpoint
    // but we need to create a user scoped to the invitation's company
    CompanyContext.setCompanyId(invitation.getCompanyId());

    // Create user account
    User user = new User();
    user.setEmail(invitation.getEmail());
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setFullName(fullName);
    user.setRole(invitation.getRole());
    user.setCompanyId(invitation.getCompanyId());
    user.setFailedLoginCount(0);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());

    User savedUser = userRepository.save(user);

    // Mark invitation as accepted
    invitation.setStatus("ACCEPTED");
    invitation.setAcceptedAt(Instant.now());
    if (httpRequest != null) {
      invitation.setIpAddress(getClientIpAddress(httpRequest));
    }
    invitation.setUpdatedAt(Instant.now());
    invitationRepository.save(invitation);

    // Log invitation acceptance in audit trail
    // Logging must occur after invitation is marked as accepted (for transactional safety)
    if (httpRequest != null && invitation.getCreatedBy() != null) {
      auditService.logInvitationAccepted(
          invitation.getId(),
          invitation.getCreatedBy(),
          invitation.getEmail(),
          invitation.getCompanyId(),
          httpRequest);
    }

    return savedUser;
  }

  @Override
  public void cancelInvitation(Long invitationId, HttpServletRequest httpRequest) {
    Invitation invitation =
        invitationRepository
            .findById(invitationId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Invitation not found"));

    invitation.setStatus("CANCELLED");
    invitation.setUpdatedAt(Instant.now());
    invitationRepository.save(invitation);

    // Log invitation cancellation in audit trail
    if (httpRequest != null && invitation.getCreatedBy() != null) {
      auditService.logInvitationCancelled(
          invitation.getId(),
          invitation.getCreatedBy(),
          invitation.getEmail(),
          invitation.getCompanyId(),
          httpRequest);
    }
  }

  @Override
  public List<Invitation> listInvitations() {
    return invitationRepository.findAll(ScopedSpecifications.companyScope());
  }

  @Override
  public Invitation createInvitationForCompany(
      String email,
      String role,
      Long companyId,
      Long createdByUserId,
      HttpServletRequest httpRequest) {

    if (userRepository.existsByEmail(email)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "User with this email already exists");
    }

    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company ID is required");
    }

    invitationRepository
        .findByEmailAndCompanyId(email, companyId)
        .ifPresent(
            existing -> {
              if (existing.isPending() && !existing.isExpired()) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A pending invitation already exists for this email and company");
              }
            });

    String roleValue = role;
    if (roleValue == null || roleValue.isBlank()) {
      roleValue = roleService.getDefaultRole().getValue();
    } else if (!roleService.isValidRole(roleValue)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid role. Must be one of: admin, accountant, chief_accountant, cfo");
    }

    String token = generateSecureToken();
    Instant expiresAt = Instant.now().plusSeconds(INVITATION_VALIDITY_DAYS * 24 * 60 * 60);

    Invitation invitation = new Invitation();
    invitation.setEmail(email);
    invitation.setToken(token);
    invitation.setCompanyId(companyId);
    invitation.setCreatedBy(createdByUserId);
    invitation.setRole(roleValue);
    invitation.setExpiresAt(expiresAt);
    invitation.setStatus("PENDING");
    invitation.setCreatedAt(Instant.now());
    invitation.setUpdatedAt(Instant.now());

    Invitation savedInvitation = invitationRepository.save(invitation);

    try {
      emailService.sendInvitationEmail(
          email,
          token,
          getUserName(createdByUserId),
          getCompanyName(companyId),
          roleValue,
          expiresAt,
          httpRequest);
    } catch (Exception e) {
      // Log error but don't fail invitation creation
    }

    if (httpRequest != null) {
      auditService.logInvitationCreated(
          createdByUserId, email, companyId, roleValue, httpRequest);
    }

    return savedInvitation;
  }

  /**
   * Generate secure random token for invitation (UUID v4 equivalent).
   *
   * @return base64-encoded secure token
   */
  private String generateSecureToken() {
    byte[] tokenBytes = new byte[INVITATION_TOKEN_LENGTH];
    secureRandom.nextBytes(tokenBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
  }

  private String getUserName(Long userId) {
    return userRepository
        .findById(userId)
        .map(User::getFullName)
        .orElse("Administrator");
  }

  private String getCompanyName(Long companyId) {
    return companyRepository
        .findById(companyId)
        .map(Company::getName)
        .orElse("Company");
  }

  /**
   * Hash a token using SHA-256 for secure storage.
   *
   * @param token plain text token
   * @return hex-encoded SHA-256 hash
   */
  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }

  /**
   * Extract client IP address from HTTP request. Handles proxy headers.
   *
   * @param request HTTP request
   * @return client IP address
   */
  private String getClientIpAddress(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isBlank()) {
      return xRealIp;
    }
    return request.getRemoteAddr();
  }

  @Override
  public void revokeInvitation(Long invitationId, Long revokedByUserId, HttpServletRequest httpRequest) {
    Invitation invitation =
        invitationRepository
            .findById(invitationId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Invitation not found"));

    // Can only revoke pending invitations
    if (!invitation.isPending()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Can only revoke pending invitations");
    }

    if (invitation.isRevoked()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invitation is already revoked");
    }

    invitation.setRevokedAt(Instant.now());
    invitation.setStatus("REVOKED");
    invitation.setUpdatedAt(Instant.now());
    invitationRepository.save(invitation);

    // Log revocation in audit trail
    if (httpRequest != null) {
      auditService.logInvitationCancelled(
          invitation.getId(),
          revokedByUserId,
          invitation.getEmail(),
          invitation.getCompanyId(),
          httpRequest);
    }
  }

  @Override
  public Invitation resendInvitation(Long invitationId, Long resendByUserId, HttpServletRequest httpRequest) {
    Invitation oldInvitation =
        invitationRepository
            .findById(invitationId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Invitation not found"));

    // Revoke the old invitation first
    if (oldInvitation.isPending() && !oldInvitation.isRevoked()) {
      oldInvitation.setRevokedAt(Instant.now());
      oldInvitation.setStatus("REVOKED");
      oldInvitation.setUpdatedAt(Instant.now());
      invitationRepository.save(oldInvitation);
    }

    // Create a new invitation with the same parameters
    return createInvitationForCompany(
        oldInvitation.getEmail(),
        oldInvitation.getRole(),
        oldInvitation.getCompanyId(),
        resendByUserId,
        httpRequest);
  }
}
