package com.accounting.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.ChangePasswordRequest;
import com.accounting.dto.CreateUserRequest;
import com.accounting.dto.UpdateProfileRequest;
import com.accounting.dto.UpdateUserRequest;
import com.accounting.entity.User;
import com.accounting.enums.Role;
import com.accounting.repository.ScopedSpecifications;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.PasswordEncoder;
import com.accounting.service.AuditService;
import com.accounting.service.EmailService;
import com.accounting.service.RoleService;
import com.accounting.service.UserService;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Implementation of UserService for user management operations.
 */
@Service
@Transactional
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final RoleService roleService;
  private final AuditService auditService;
  private final EmailService emailService;

  public UserServiceImpl(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      RoleService roleService,
      AuditService auditService,
      EmailService emailService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.roleService = roleService;
    this.auditService = auditService;
    this.emailService = emailService;
  }

  @Override
  public List<User> getAllUsers() {
    // Always filter by company - this is a company-scoped operation
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }
    return userRepository.findAll(ScopedSpecifications.companyScope());
  }

  @Override
  public Page<User> findAllWithFilters(String role, String status, String search, Pageable pageable) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Build specification with company scope and optional filters
    Specification<User> spec = (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Always filter by company
      predicates.add(criteriaBuilder.equal(root.get("companyId"), companyId));

      // Filter by role if provided
      if (role != null && !role.isBlank()) {
        predicates.add(criteriaBuilder.equal(root.get("role"), role.toLowerCase()));
      }

      // Filter by status if provided
      if (status != null && !status.isBlank()) {
        predicates.add(criteriaBuilder.equal(root.get("status"), status.toUpperCase()));
      }

      // Search by email or full name if provided
      if (search != null && !search.isBlank()) {
        String searchPattern = "%" + search.toLowerCase() + "%";
        Predicate emailPredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), searchPattern);
        Predicate namePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), searchPattern);
        predicates.add(criteriaBuilder.or(emailPredicate, namePredicate));
      }

      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };

    return userRepository.findAll(spec, pageable);
  }

  @Override
  public User getUserById(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "User not found"));
  }

  @Override
  public User createUser(CreateUserRequest request, HttpServletRequest httpRequest) {
    // Check if email already exists
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "User with this email already exists");
    }

    // Get current user role for permission check
    Long currentUserId = getCurrentUserIdFromContext();
    User currentUser = userRepository.findById(currentUserId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Current user not found"));
    Role requesterRole = Role.fromString(currentUser.getRole());

    // Validate role if provided, otherwise use default
    String roleValue = request.getRole();
    if (roleValue == null || roleValue.isBlank()) {
      roleValue = roleService.getDefaultRole().getValue();
    } else if (!roleService.isValidRole(roleValue)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid role. Must be one of: admin, accountant, chief_accountant, cfo");
    }
    
    Role newRole = Role.fromString(roleValue.toLowerCase());
    
    // Security check: Verify requester can create users with the specified role
    // Only ADMIN can create ADMIN users
    if (newRole == Role.ADMIN && requesterRole != Role.ADMIN) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only ADMIN can create ADMIN users");
    }
    
    // CHIEF_ACCOUNTANT cannot create ADMIN or CHIEF_ACCOUNTANT users
    if (requesterRole == Role.CHIEF_ACCOUNTANT && 
        (newRole == Role.ADMIN || newRole == Role.CHIEF_ACCOUNTANT)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "CHIEF_ACCOUNTANT cannot create ADMIN or CHIEF_ACCOUNTANT users");
    }
    
    // Verify requester can manage the role being assigned
    if (!requesterRole.canManageRole(newRole)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          String.format("%s cannot create users with role %s", 
              requesterRole.getValue(), newRole.getValue()));
    }

    // Create user
    User user = new User();
    user.setEmail(request.getEmail());
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setFullName(request.getFullName());
    user.setRole(roleValue);
    user.setStatus("ACTIVE");
    user.setCompanyId(CompanyContext.getCompanyId());
    user.setFailedLoginCount(0);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());

    User savedUser = userRepository.save(user);

    // Log user creation in audit trail
    auditService.logUserCreated(savedUser, currentUserId, httpRequest);

    return savedUser;
  }

  @Override
  public User updateUserRole(
      Long userId,
      String newRole,
      Long currentUserId,
      String currentUserRole,
      HttpServletRequest httpRequest) {

    // Validate requester has permission (ADMIN or CHIEF_ACCOUNTANT)
    Role requesterRole = Role.fromString(currentUserRole);
    if (requesterRole != Role.ADMIN && requesterRole != Role.CHIEF_ACCOUNTANT) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only ADMIN or CHIEF_ACCOUNTANT can change user roles");
    }

    // Prevent self-role-change
    if (userId.equals(currentUserId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You cannot change your own role");
    }

    // Validate new role value
    if (!roleService.isValidRole(newRole)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid role. Must be one of: admin, accountant, chief_accountant, cfo");
    }

    // Get target user
    User targetUser = userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found"));

    // Prevent changing role for inactive users - they must be activated first
    if ("INACTIVE".equals(targetUser.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Cannot change role for inactive users. Please activate them first.");
    }

    // Security checks for role change
    Role targetUserRole = Role.fromString(targetUser.getRole());
    Role newRoleEnum = Role.fromString(newRole.toLowerCase());
    
    // Verify requester can manage the target user's current role
    if (!requesterRole.canManageRole(targetUserRole)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          String.format("%s cannot change the role of users with role %s", 
              requesterRole.getValue(), targetUserRole.getValue()));
    }
    
    // Only ADMIN can assign ADMIN role
    if (newRoleEnum == Role.ADMIN && requesterRole != Role.ADMIN) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only ADMIN can assign ADMIN role");
    }
    
    // CHIEF_ACCOUNTANT cannot promote roles (cannot assign roles >= their own)
    if (requesterRole == Role.CHIEF_ACCOUNTANT && newRoleEnum.hasHigherOrEqualPrivilege(requesterRole)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "CHIEF_ACCOUNTANT cannot assign roles higher than or equal to their own");
    }
    
    // Verify requester can manage the new role being assigned
    if (!requesterRole.canManageRole(newRoleEnum)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          String.format("%s cannot assign role %s", 
              requesterRole.getValue(), newRoleEnum.getValue()));
    }

    // Store old role for audit logging
    String oldRole = targetUser.getRole();

    // Check if role is actually changing
    if (oldRole.equals(newRole.toLowerCase())) {
      return targetUser; // No change needed
    }

    // Log role change BEFORE applying the change (transactional: if logging fails,
    // rollback)
    auditService.logRoleChange(targetUser, oldRole, newRole.toLowerCase(), currentUserId, httpRequest);

    // Update role
    targetUser.setRole(newRole.toLowerCase());
    targetUser.setUpdatedAt(Instant.now());
    User updatedUser = userRepository.save(targetUser);

    return updatedUser;
  }

  @Override
  public User updateUser(
      Long userId,
      UpdateUserRequest request,
      Long currentUserId,
      String currentUserRole,
      HttpServletRequest httpRequest) {
    User targetUser = userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    // Prevent editing inactive users - they must be activated first
    if ("INACTIVE".equals(targetUser.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Cannot edit inactive users. Please activate them first.");
    }

    // Check if user is editing themselves
    boolean isSelfEdit = userId.equals(currentUserId);
    Role requesterRole = Role.fromString(currentUserRole);
    Role targetUserRole = Role.fromString(targetUser.getRole());

    // Security check: Verify requester can manage target user role
    // ADMIN can manage everyone (except themselves, checked separately)
    // CHIEF_ACCOUNTANT can only manage ACCOUNTANT and CFO
    // Others cannot manage anyone
    if (!isSelfEdit && !requesterRole.canManageRole(targetUserRole)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, 
          String.format("%s cannot manage users with role %s", 
              requesterRole.getValue(), targetUserRole.getValue()));
    }

    Map<String, String> oldValues = new HashMap<>();
    Map<String, String> newValues = new HashMap<>();

    // Update fullName if provided
    if (request.getFullName() != null && !request.getFullName().isBlank()) {
      oldValues.put("fullName", targetUser.getFullName());
      targetUser.setFullName(request.getFullName());
      newValues.put("fullName", request.getFullName());
    }

    // Update role if provided (only admin/chief, not self)
    if (request.getRole() != null && !request.getRole().isBlank()) {
      if (isSelfEdit) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "You cannot change your own role");
      }
      if (requesterRole != Role.ADMIN && requesterRole != Role.CHIEF_ACCOUNTANT) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Only ADMIN or CHIEF_ACCOUNTANT can change user roles");
      }
      // Additional checks for role assignment
      Role newRole = Role.fromString(request.getRole().toLowerCase());
      
      // Only ADMIN can assign ADMIN role
      if (newRole == Role.ADMIN && requesterRole != Role.ADMIN) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Only ADMIN can assign ADMIN role");
      }
      
      // CHIEF_ACCOUNTANT cannot promote roles (cannot assign roles >= their own)
      if (requesterRole == Role.CHIEF_ACCOUNTANT && newRole.hasHigherOrEqualPrivilege(requesterRole)) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "CHIEF_ACCOUNTANT cannot assign roles higher than or equal to their own");
      }
      
      // Verify requester can manage the new role being assigned
      if (!requesterRole.canManageRole(newRole)) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            String.format("%s cannot assign role %s", 
                requesterRole.getValue(), newRole.getValue()));
      }
      if (!roleService.isValidRole(request.getRole())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid role. Must be one of: admin, accountant, chief_accountant, cfo");
      }
      oldValues.put("role", targetUser.getRole());
      targetUser.setRole(request.getRole().toLowerCase());
      newValues.put("role", request.getRole().toLowerCase());
    }

    // Update status if provided (only admin/chief, not self)
    if (request.getStatus() != null && !request.getStatus().isBlank()) {
      if (isSelfEdit) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "You cannot change your own status");
      }
      if (requesterRole != Role.ADMIN && requesterRole != Role.CHIEF_ACCOUNTANT) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Only ADMIN or CHIEF_ACCOUNTANT can change user status");
      }
      // Status change is already covered by the canManageRole check at the beginning
      // This check is redundant but kept for clarity
      String status = request.getStatus().toUpperCase();
      if (!status.equals("ACTIVE") && !status.equals("INACTIVE") && !status.equals("LOCKED")) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Invalid status. Must be ACTIVE, INACTIVE, or LOCKED");
      }
      oldValues.put("status", targetUser.getStatus());
      targetUser.setStatus(status);
      newValues.put("status", status);
    }

    targetUser.setUpdatedAt(Instant.now());
    User updatedUser = userRepository.save(targetUser);

    // Log update if any changes were made
    if (!oldValues.isEmpty()) {
      auditService.logUserUpdated(updatedUser, currentUserId, oldValues, newValues, httpRequest);
    }

    return updatedUser;
  }

  @Override
  public User deactivateUser(Long userId, Long currentUserId, String currentUserRole, HttpServletRequest httpRequest) {
    // Prevent self-deactivation
    if (userId.equals(currentUserId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You cannot deactivate your own account");
    }

    User user = userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    // Security check: Verify requester can manage target user role
    Role requesterRole = Role.fromString(currentUserRole);
    Role targetUserRole = Role.fromString(user.getRole());
    if (!requesterRole.canManageRole(targetUserRole)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          String.format("%s cannot deactivate users with role %s", 
              requesterRole.getValue(), targetUserRole.getValue()));
    }
    
    user.setStatus("INACTIVE");
    user.setUpdatedAt(Instant.now());
    User deactivatedUser = userRepository.save(user);

    auditService.logUserDeactivated(deactivatedUser, currentUserId, httpRequest);

    return deactivatedUser;
  }

  @Override
  public User activateUser(Long userId, Long currentUserId, String currentUserRole, HttpServletRequest httpRequest) {
    User user = userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    // Security check: Verify requester can manage target user role
    Role requesterRole = Role.fromString(currentUserRole);
    Role targetUserRole = Role.fromString(user.getRole());
    if (!requesterRole.canManageRole(targetUserRole)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          String.format("%s cannot activate users with role %s", 
              requesterRole.getValue(), targetUserRole.getValue()));
    }

    user.setStatus("ACTIVE");
    user.setUpdatedAt(Instant.now());
    User activatedUser = userRepository.save(user);

    auditService.logUserActivated(activatedUser, currentUserId, httpRequest);

    return activatedUser;
  }

  @Override
  public void resetPasswordByAdmin(Long userId, Long adminUserId, String adminUserRole, HttpServletRequest httpRequest) {
    User user = userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    // Prevent resetting password for inactive users - they must be activated first
    if ("INACTIVE".equals(user.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Cannot reset password for inactive users. Please activate them first.");
    }

    // Security check: Verify requester can manage target user role
    Role requesterRole = Role.fromString(adminUserRole);
    Role targetUserRole = Role.fromString(user.getRole());
    if (!requesterRole.canManageRole(targetUserRole)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          String.format("%s cannot reset password for users with role %s", 
              requesterRole.getValue(), targetUserRole.getValue()));
    }

    // Generate reset token (same logic as AuthService.requestPasswordReset)
    String resetToken = UUID.randomUUID().toString();
    Instant expiry = Instant.now().plusSeconds(30 * 60); // 30 minutes

    user.setResetToken(resetToken);
    user.setResetTokenExpiry(expiry);
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);

    emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
    auditService.logPasswordResetByAdmin(user, adminUserId, httpRequest);
  }

  @Override
  public User getCurrentUserProfile(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }

  @Override
  public User updateProfile(Long userId, UpdateProfileRequest request, HttpServletRequest httpRequest) {
    User user = userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    Map<String, String> updatedFields = new HashMap<>();

    if (request.getFullName() != null && !request.getFullName().isBlank()) {
      updatedFields.put("fullName", request.getFullName());
      user.setFullName(request.getFullName());
      user.setUpdatedAt(Instant.now());
      userRepository.save(user);
    }

    if (!updatedFields.isEmpty()) {
      auditService.logProfileUpdated(user, updatedFields, httpRequest);
    }

    return user;
  }

  @Override
  public void changePassword(Long userId, ChangePasswordRequest request, HttpServletRequest httpRequest) {
    User user = userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    // Validate current password
    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Current password is incorrect");
    }

    // Validate new password matches confirm password
    if (!request.getNewPassword().equals(request.getConfirmPassword())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "New password and confirm password do not match");
    }

    // Update password
    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);

    auditService.logPasswordChanged(user, httpRequest);
  }

  /**
   * Get current user ID from security context.
   *
   * @return current user ID
   */
  private Long getCurrentUserIdFromContext() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      return null;
    }
    try {
      return Long.parseLong(authentication.getPrincipal().toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
