package com.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.entity.Company;
import com.accounting.entity.Invitation;
import com.accounting.entity.User;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.InvitationRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.PasswordEncoder;
import com.accounting.service.impl.InvitationServiceImpl;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

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
  private PasswordEncoder passwordEncoder;

  private InvitationServiceImpl invitationService;

  private MockHttpServletRequest httpRequest;

  private static final Long COMPANY_ID = 1L;
  private static final Long USER_ID = 100L;
  private static final String TEST_EMAIL = "invitee@example.com";
  private static final String TEST_ROLE = "accountant";

  @BeforeEach
  void setUp() {
    invitationService = new InvitationServiceImpl(
        invitationRepository,
        userRepository,
        companyRepository,
        roleService,
        emailService,
        auditService,
        passwordEncoder);

    httpRequest = new MockHttpServletRequest();
    httpRequest.setRemoteAddr("192.168.1.1");
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void createInvitation_generatesSecureToken() {
    CompanyContext.setCompanyId(COMPANY_ID);
    when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
    when(invitationRepository.findByEmailAndCompanyId(TEST_EMAIL, COMPANY_ID)).thenReturn(Optional.empty());
    when(roleService.isValidRole(TEST_ROLE)).thenReturn(true);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(createTestUser()));
    when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(createTestCompany()));
    when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> {
      Invitation inv = invocation.getArgument(0);
      inv.setId(1L);
      return inv;
    });

    Invitation result = invitationService.createInvitation(TEST_EMAIL, TEST_ROLE, USER_ID, httpRequest);

    assertThat(result.getToken()).isNotNull();
    assertThat(result.getToken()).isNotBlank();
    assertThat(result.getTokenHash()).isNotNull();
    assertThat(result.getTokenHash()).isNotBlank();
    assertThat(result.getTokenHash()).hasSize(64);
  }

  @Test
  void createInvitation_setsExpiry24Hours() {
    CompanyContext.setCompanyId(COMPANY_ID);
    when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
    when(invitationRepository.findByEmailAndCompanyId(TEST_EMAIL, COMPANY_ID)).thenReturn(Optional.empty());
    when(roleService.isValidRole(TEST_ROLE)).thenReturn(true);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(createTestUser()));
    when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(createTestCompany()));
    when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> {
      Invitation inv = invocation.getArgument(0);
      inv.setId(1L);
      return inv;
    });

    Instant before = Instant.now().plus(23, ChronoUnit.HOURS);
    Invitation result = invitationService.createInvitation(TEST_EMAIL, TEST_ROLE, USER_ID, httpRequest);
    Instant after = Instant.now().plus(25, ChronoUnit.HOURS);

    assertThat(result.getExpiresAt()).isAfter(before);
    assertThat(result.getExpiresAt()).isBefore(after);
  }

  @Test
  void createInvitation_throwsConflictIfUserExists() {
    CompanyContext.setCompanyId(COMPANY_ID);
    when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> invitationService.createInvitation(TEST_EMAIL, TEST_ROLE, USER_ID, httpRequest))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("User with this email already exists");
  }

  @Test
  void createInvitation_throwsConflictIfPendingInvitationExists() {
    CompanyContext.setCompanyId(COMPANY_ID);
    when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);

    Invitation existingInvitation = new Invitation();
    existingInvitation.setStatus("PENDING");
    existingInvitation.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
    when(invitationRepository.findByEmailAndCompanyId(TEST_EMAIL, COMPANY_ID))
        .thenReturn(Optional.of(existingInvitation));

    assertThatThrownBy(() -> invitationService.createInvitation(TEST_EMAIL, TEST_ROLE, USER_ID, httpRequest))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("pending invitation already exists");
  }

  @Test
  void validateInvitation_returnsInvitationForValidToken() {
    String token = "valid-token-123";
    Invitation invitation = createPendingInvitation();
    when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));

    Invitation result = invitationService.validateInvitation(token);

    assertThat(result).isNotNull();
    assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
  }

  @Test
  void validateInvitation_throwsNotFoundForInvalidToken() {
    String token = "invalid-token";
    when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
    when(invitationRepository.findByToken(token)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> invitationService.validateInvitation(token))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid invitation token");
  }

  @Test
  void validateInvitation_throwsBadRequestForRevokedToken() {
    String token = "revoked-token";
    Invitation invitation = createPendingInvitation();
    invitation.setRevokedAt(Instant.now());
    when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));

    assertThatThrownBy(() -> invitationService.validateInvitation(token))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invitation has been revoked");
  }

  @Test
  void validateInvitation_throwsBadRequestForAcceptedToken() {
    String token = "accepted-token";
    Invitation invitation = createPendingInvitation();
    invitation.setAcceptedAt(Instant.now());
    when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));

    assertThatThrownBy(() -> invitationService.validateInvitation(token))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invitation has already been used");
  }

  @Test
  void validateInvitation_throwsNotFoundForExpiredToken() {
    String token = "expired-token";
    Invitation invitation = createPendingInvitation();
    invitation.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
    when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));
    when(invitationRepository.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));

    assertThatThrownBy(() -> invitationService.validateInvitation(token))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invitation has expired");

    ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
    verify(invitationRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("EXPIRED");
  }

  @Test
  void acceptInvitation_createsUserWithCorrectData() {
    String token = "valid-token";
    String password = "SecurePassword123!";
    String fullName = "John Doe";

    Invitation invitation = createPendingInvitation();
    invitation.setId(1L);
    when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));
    when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
    when(passwordEncoder.encode(password)).thenReturn("hashed-password");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      user.setId(10L);
      return user;
    });
    when(invitationRepository.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));

    User result = invitationService.acceptInvitation(token, password, fullName, httpRequest);

    assertThat(result).isNotNull();
    assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
    assertThat(result.getFullName()).isEqualTo(fullName);
    assertThat(result.getRole()).isEqualTo(TEST_ROLE);
    assertThat(result.getCompanyId()).isEqualTo(COMPANY_ID);
    assertThat(result.getPasswordHash()).isEqualTo("hashed-password");
  }

  @Test
  void acceptInvitation_marksInvitationAsAccepted() {
    String token = "valid-token";
    Invitation invitation = createPendingInvitation();
    invitation.setId(1L);
    when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));
    when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    when(userRepository.save(any(User.class))).thenAnswer(i -> {
      User u = i.getArgument(0);
      u.setId(10L);
      return u;
    });
    when(invitationRepository.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));

    invitationService.acceptInvitation(token, "password", "Name", httpRequest);

    ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
    verify(invitationRepository).save(captor.capture());
    Invitation saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo("ACCEPTED");
    assertThat(saved.getAcceptedAt()).isNotNull();
  }

  @Test
  void acceptInvitation_logsIpAddress() {
    String token = "valid-token";
    httpRequest.setRemoteAddr("10.0.0.1");

    Invitation invitation = createPendingInvitation();
    invitation.setId(1L);
    when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));
    when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    when(userRepository.save(any(User.class))).thenAnswer(i -> {
      User u = i.getArgument(0);
      u.setId(10L);
      return u;
    });
    when(invitationRepository.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));

    invitationService.acceptInvitation(token, "password", "Name", httpRequest);

    ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
    verify(invitationRepository).save(captor.capture());
    assertThat(captor.getValue().getIpAddress()).isEqualTo("10.0.0.1");
  }

  @Test
  void revokeInvitation_setsRevokedAt() {
    Long invitationId = 1L;
    Invitation invitation = createPendingInvitation();
    invitation.setId(invitationId);
    when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
    when(invitationRepository.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));

    invitationService.revokeInvitation(invitationId, USER_ID, httpRequest);

    ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
    verify(invitationRepository).save(captor.capture());
    Invitation saved = captor.getValue();
    assertThat(saved.getRevokedAt()).isNotNull();
    assertThat(saved.getStatus()).isEqualTo("REVOKED");
  }

  @Test
  void revokeInvitation_throwsBadRequestIfNotPending() {
    Long invitationId = 1L;
    Invitation invitation = createPendingInvitation();
    invitation.setId(invitationId);
    invitation.setStatus("ACCEPTED");
    invitation.setAcceptedAt(Instant.now());
    when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));

    assertThatThrownBy(() -> invitationService.revokeInvitation(invitationId, USER_ID, httpRequest))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Can only revoke pending invitations");

    verify(invitationRepository, never()).save(any(Invitation.class));
  }

  @Test
  void resendInvitation_revokesOldAndCreatesNew() {
    Long invitationId = 1L;
    Invitation oldInvitation = createPendingInvitation();
    oldInvitation.setId(invitationId);
    oldInvitation.setToken("old-token");

    when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(oldInvitation));
    when(invitationRepository.findByEmailAndCompanyId(TEST_EMAIL, COMPANY_ID)).thenReturn(Optional.empty());
    when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
    when(roleService.isValidRole(TEST_ROLE)).thenReturn(true);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(createTestUser()));
    when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(createTestCompany()));
    when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> {
      Invitation inv = invocation.getArgument(0);
      if (inv.getId() == null) {
        inv.setId(2L);
      }
      return inv;
    });

    Invitation result = invitationService.resendInvitation(invitationId, USER_ID, httpRequest);

    ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
    verify(invitationRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());

    Invitation revokedInvitation = captor.getAllValues().stream()
        .filter(inv -> "REVOKED".equals(inv.getStatus()))
        .findFirst()
        .orElseThrow();
    assertThat(revokedInvitation.getRevokedAt()).isNotNull();

    assertThat(result).isNotNull();
    assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
    assertThat(result.getStatus()).isEqualTo("PENDING");
  }

  private Invitation createPendingInvitation() {
    Invitation invitation = new Invitation();
    invitation.setEmail(TEST_EMAIL);
    invitation.setToken("test-token");
    invitation.setTokenHash("test-hash");
    invitation.setCompanyId(COMPANY_ID);
    invitation.setCreatedBy(USER_ID);
    invitation.setRole(TEST_ROLE);
    invitation.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
    invitation.setStatus("PENDING");
    invitation.setCreatedAt(Instant.now());
    invitation.setUpdatedAt(Instant.now());
    return invitation;
  }

  private User createTestUser() {
    User user = new User();
    user.setId(USER_ID);
    user.setEmail("admin@example.com");
    user.setFullName("Admin User");
    user.setRole("admin");
    user.setCompanyId(COMPANY_ID);
    return user;
  }

  private Company createTestCompany() {
    Company company = new Company();
    company.setId(COMPANY_ID);
    company.setName("Test Company");
    company.setCode("TEST");
    return company;
  }
}
