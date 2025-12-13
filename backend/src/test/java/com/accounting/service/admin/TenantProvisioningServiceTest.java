package com.accounting.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.admin.TenantProvisionRequest;
import com.accounting.dto.admin.TenantProvisionResponse;
import com.accounting.entity.Company;
import com.accounting.entity.Invitation;
import com.accounting.entity.TenantProvision;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.TenantProvisionRepository;
import com.accounting.service.InvitationService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantProvisioningServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private TenantProvisionRepository tenantProvisionRepository;

    @Mock
    private InvitationService invitationService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query nativeQuery;

    @Mock
    private HttpServletRequest httpRequest;

    private TenantProvisioningServiceImpl tenantProvisioningService;

    private static final Long CREATED_BY_USER_ID = 1L;
    private static final Long COMPANY_ID = 100L;
    private static final Long INVITATION_ID = 200L;

    @BeforeEach
    void setUp() {
        tenantProvisioningService = new TenantProvisioningServiceImpl(
                companyRepository,
                tenantProvisionRepository,
                invitationService,
                entityManager);
    }

    @Test
    void provisionTenant_createsCompanyWithCorrectData() {
        TenantProvisionRequest request = createValidRequest();
        setupSuccessfulMocks(request);

        TenantProvisionResponse response = tenantProvisioningService.provisionTenant(
                request, CREATED_BY_USER_ID, httpRequest);

        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(companyCaptor.capture());

        Company savedCompany = companyCaptor.getValue();
        assertEquals("Test Company", savedCompany.getName());
        assertEquals("1234567890", savedCompany.getTaxCode());
        assertEquals("123 Test Street", savedCompany.getAddress());
        assertEquals("admin@test.com", savedCompany.getContactEmail());
        assertNotNull(savedCompany.getCode());
        assertTrue(savedCompany.getCode().startsWith("C"));
    }

    @Test
    void provisionTenant_seedsChartOfAccounts() {
        TenantProvisionRequest request = createValidRequest();
        setupSuccessfulMocks(request);

        tenantProvisioningService.provisionTenant(request, CREATED_BY_USER_ID, httpRequest);

        verify(entityManager).createNativeQuery("SELECT seed_tt200_coa_from_template(:companyId)");
        verify(nativeQuery).setParameter("companyId", COMPANY_ID);
        verify(nativeQuery).getSingleResult();
    }

    @Test
    void provisionTenant_createsInvitationForAdmin() {
        TenantProvisionRequest request = createValidRequest();
        setupSuccessfulMocks(request);

        tenantProvisioningService.provisionTenant(request, CREATED_BY_USER_ID, httpRequest);

        verify(invitationService).createInvitationForCompany(
                eq("admin@test.com"),
                eq("ADMIN"),
                eq(COMPANY_ID),
                eq(CREATED_BY_USER_ID),
                eq(httpRequest));
    }

    @Test
    void provisionTenant_createsAuditRecord() {
        TenantProvisionRequest request = createValidRequest();
        setupSuccessfulMocks(request);

        tenantProvisioningService.provisionTenant(request, CREATED_BY_USER_ID, httpRequest);

        ArgumentCaptor<TenantProvision> provisionCaptor = ArgumentCaptor.forClass(TenantProvision.class);
        verify(tenantProvisionRepository).save(provisionCaptor.capture());

        TenantProvision savedProvision = provisionCaptor.getValue();
        assertEquals(COMPANY_ID, savedProvision.getCompanyId());
        assertEquals(CREATED_BY_USER_ID, savedProvision.getCreatedBy());
        assertEquals("TT200", savedProvision.getCoaPreset());
        assertEquals("admin@test.com", savedProvision.getAdminEmail());
        assertEquals("Admin User", savedProvision.getAdminName());
        assertEquals(INVITATION_ID, savedProvision.getInvitationId());
    }

    @Test
    void provisionTenant_throwsConflictForDuplicateTaxCode() {
        TenantProvisionRequest request = createValidRequest();
        when(companyRepository.existsByTaxCode("1234567890")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantProvisioningService.provisionTenant(request, CREATED_BY_USER_ID, httpRequest));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertTrue(exception.getReason().contains("tax code already exists"));

        verify(companyRepository, never()).save(any());
        verify(invitationService, never()).createInvitationForCompany(
                anyString(), anyString(), anyLong(), anyLong(), any());
        verify(tenantProvisionRepository, never()).save(any());
    }

    @Test
    void provisionTenant_rollsBackOnFailure() {
        TenantProvisionRequest request = createValidRequest();
        when(companyRepository.existsByTaxCode("1234567890")).thenReturn(false);

        Company savedCompany = createCompany();
        when(companyRepository.save(any(Company.class))).thenReturn(savedCompany);
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);

        when(invitationService.createInvitationForCompany(
                anyString(), anyString(), anyLong(), anyLong(), any()))
                .thenThrow(new RuntimeException("Invitation creation failed"));

        assertThrows(RuntimeException.class,
                () -> tenantProvisioningService.provisionTenant(request, CREATED_BY_USER_ID, httpRequest));

        verify(tenantProvisionRepository, never()).save(any());
    }

    @Test
    void provisionTenant_returnsCorrectResponse() {
        TenantProvisionRequest request = createValidRequest();
        setupSuccessfulMocks(request);

        TenantProvisionResponse response = tenantProvisioningService.provisionTenant(
                request, CREATED_BY_USER_ID, httpRequest);

        assertNotNull(response);
        assertEquals(COMPANY_ID, response.companyId());
        assertEquals("Test Company", response.companyName());
        assertEquals(INVITATION_ID, response.invitationId());
        assertEquals("admin@test.com", response.adminEmail());
        assertTrue(response.message().contains("admin@test.com"));
    }

    @Test
    void provisionTenant_handlesNullAddress() {
        TenantProvisionRequest request = new TenantProvisionRequest(
                "Test Company",
                "1234567890",
                null,
                "Legal Rep",
                "01-01",
                "VND",
                "admin@test.com",
                "Admin User",
                "TT200");
        setupSuccessfulMocks(request);

        tenantProvisioningService.provisionTenant(request, CREATED_BY_USER_ID, httpRequest);

        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(companyCaptor.capture());

        Company savedCompany = companyCaptor.getValue();
        assertEquals("", savedCompany.getAddress());
    }

    @Test
    void provisionTenant_parsesFiscalYearStart() {
        TenantProvisionRequest request = new TenantProvisionRequest(
                "Test Company",
                "1234567890",
                "123 Street",
                "Legal Rep",
                "07-01",
                "VND",
                "admin@test.com",
                "Admin User",
                "TT200");
        setupSuccessfulMocks(request);

        tenantProvisioningService.provisionTenant(request, CREATED_BY_USER_ID, httpRequest);

        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(companyCaptor.capture());

        Company savedCompany = companyCaptor.getValue();
        assertNotNull(savedCompany.getFiscalYearStart());
        assertEquals(7, savedCompany.getFiscalYearStart().getMonthValue());
        assertEquals(1, savedCompany.getFiscalYearStart().getDayOfMonth());
    }

    private TenantProvisionRequest createValidRequest() {
        return new TenantProvisionRequest(
                "Test Company",
                "1234567890",
                "123 Test Street",
                "Legal Representative",
                "01-01",
                "VND",
                "admin@test.com",
                "Admin User",
                "TT200");
    }

    private Company createCompany() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setCode("C12345678");
        company.setName("Test Company");
        company.setTaxCode("1234567890");
        company.setAddress("123 Test Street");
        company.setContactEmail("admin@test.com");
        return company;
    }

    private Invitation createInvitation() {
        Invitation invitation = new Invitation();
        invitation.setId(INVITATION_ID);
        invitation.setEmail("admin@test.com");
        invitation.setRole("ADMIN");
        invitation.setCompanyId(COMPANY_ID);
        return invitation;
    }

    private void setupSuccessfulMocks(TenantProvisionRequest request) {
        when(companyRepository.existsByTaxCode(request.taxCode())).thenReturn(false);

        Company savedCompany = createCompany();
        when(companyRepository.save(any(Company.class))).thenReturn(savedCompany);

        when(entityManager.createNativeQuery("SELECT seed_tt200_coa_from_template(:companyId)"))
                .thenReturn(nativeQuery);
        when(nativeQuery.setParameter("companyId", COMPANY_ID)).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);

        Invitation invitation = createInvitation();
        when(invitationService.createInvitationForCompany(
                eq(request.adminEmail()),
                eq("ADMIN"),
                eq(COMPANY_ID),
                eq(CREATED_BY_USER_ID),
                eq(httpRequest)))
                .thenReturn(invitation);

        when(tenantProvisionRepository.save(any(TenantProvision.class)))
                .thenAnswer(invocation -> {
                    TenantProvision provision = invocation.getArgument(0);
                    provision.setId(1L);
                    return provision;
                });
    }
}
