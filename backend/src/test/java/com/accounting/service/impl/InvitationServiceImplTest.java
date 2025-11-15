package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accounting.entity.Invitation;
import com.accounting.entity.User;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.InvitationRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.PasswordEncoder;
import com.accounting.service.AuditService;
import com.accounting.service.EmailService;
import com.accounting.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvitationServiceImplTest {

  @Mock
  private InvitationRepository invitationRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private CompanyRepository companyRepository;

  @Mock
  private RoleService roleService;

  @Mock
  private EmailService emailService;

  @Mock
  private AuditService auditService;

  @Mock
  private HttpServletRequest httpRequest;

  private PasswordEncoder passwordEncoder;
  private InvitationServiceImpl invitationService;

  @BeforeEach
  void setUp() {
    passwordEncoder = new PasswordEncoder();
    invitationService = new InvitationServiceImpl(
        invitationRepository,
        userRepository,
        companyRepository,
        roleService,
        emailService,
        auditService,
        passwordEncoder);
    CompanyContext.setCompanyId(1L);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void createInvitation_shouldGenerateSecureToken() {
    String email = "newuser@example.com";
    String role = "accountant";

    com.accounting.entity.Company company = new com.accounting.entity.Company();
    company.setId(1L);
    company.setName("Test Company");

    org.mockito.Mockito.when(userRepository.existsByEmail(email)).thenReturn(false);
    org.mockito.Mockito.when(invitationRepository.findByEmailAndCompanyId(email, 1L))
        .thenReturn(Optional.empty());
    org.mockito.Mockito.when(roleService.isValidRole(role)).thenReturn(true);
    org.mockito.Mockito.when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
    org.mockito.Mockito.when(userRepository.findById(1L))
        .thenReturn(Optional.of(createMockUser(1L, "Admin User")));
    org.mockito.Mockito.when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    org.mockito.Mockito.when(invitationRepository.save(org.mockito.ArgumentMatchers.any(Invitation.class)))
        .thenAnswer(
            invocation -> {
              Invitation inv = invocation.getArgument(0);
              inv.setId(1L);
              return inv;
            });

    Invitation invitation = invitationService.createInvitation(email, role, 1L, httpRequest);

    assertNotNull(invitation);
    assertNotNull(invitation.getToken());
    assertTrue(invitation.getToken().length() >= 32); // At least 32 characters
    assertEquals("PENDING", invitation.getStatus());
    assertTrue(invitation.getExpiresAt().isAfter(Instant.now()));
  }

  @Test
  void createInvitation_shouldRejectExistingUser() {
    String email = "existing@example.com";
    org.mockito.Mockito.when(userRepository.existsByEmail(email)).thenReturn(true);

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> invitationService.createInvitation(email, "accountant", 1L, httpRequest));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    assertEquals("User with this email already exists", exception.getReason());
  }

  @Test
  void validateInvitation_shouldReturnInvitationForValidToken() {
    String token = "valid-token-12345";
    Invitation invitation = new Invitation();
    invitation.setToken(token);
    invitation.setStatus("PENDING");
    invitation.setExpiresAt(Instant.now().plusSeconds(86400)); // Not expired

    org.mockito.Mockito.when(invitationRepository.findByToken(token))
        .thenReturn(Optional.of(invitation));

    Invitation result = invitationService.validateInvitation(token);

    assertNotNull(result);
    assertEquals(token, result.getToken());
  }

  @Test
  void validateInvitation_shouldRejectExpiredToken() {
    String token = "expired-token";
    Invitation invitation = new Invitation();
    invitation.setToken(token);
    invitation.setStatus("PENDING");
    invitation.setExpiresAt(Instant.now().minusSeconds(86400)); // Expired

    org.mockito.Mockito.when(invitationRepository.findByToken(token))
        .thenReturn(Optional.of(invitation));
    org.mockito.Mockito.when(invitationRepository.save(org.mockito.ArgumentMatchers.any(Invitation.class)))
        .thenReturn(invitation);

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class, () -> invitationService.validateInvitation(token));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    assertEquals("Invitation has expired", exception.getReason());
  }

  @Test
  void validateInvitation_shouldRejectAlreadyAcceptedToken() {
    String token = "accepted-token";
    Invitation invitation = new Invitation();
    invitation.setToken(token);
    invitation.setStatus("ACCEPTED");

    org.mockito.Mockito.when(invitationRepository.findByToken(token))
        .thenReturn(Optional.of(invitation));

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class, () -> invitationService.validateInvitation(token));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("Invitation is no longer valid", exception.getReason());
  }

  private User createMockUser(Long id, String fullName) {
    User user = new User();
    user.setId(id);
    user.setEmail("admin@example.com");
    user.setFullName(fullName);
    user.setRole("admin");
    return user;
  }
}
