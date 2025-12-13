package com.accounting.service.admin;

import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import jakarta.servlet.http.HttpServletRequest;

@Service
public class TenantProvisioningServiceImpl implements TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningServiceImpl.class);

    private final CompanyRepository companyRepository;
    private final TenantProvisionRepository tenantProvisionRepository;
    private final InvitationService invitationService;
    private final EntityManager entityManager;

    public TenantProvisioningServiceImpl(
            CompanyRepository companyRepository,
            TenantProvisionRepository tenantProvisionRepository,
            InvitationService invitationService,
            EntityManager entityManager) {
        this.companyRepository = companyRepository;
        this.tenantProvisionRepository = tenantProvisionRepository;
        this.invitationService = invitationService;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public TenantProvisionResponse provisionTenant(
            TenantProvisionRequest request, Long createdByUserId, HttpServletRequest httpRequest) {
        log.info(
                "Provisioning tenant: {} for admin: {}",
                request.companyName(),
                request.adminEmail());

        if (companyRepository.existsByTaxCode(request.taxCode())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Company with this tax code already exists");
        }

        Company company = new Company();
        company.setCode(generateCompanyCode());
        company.setName(request.companyName());
        company.setTaxCode(request.taxCode());
        company.setAddress(request.address() != null ? request.address() : "");
        company.setContactEmail(request.adminEmail());

        if (request.fiscalYearStart() != null) {
            String[] parts = request.fiscalYearStart().split("-");
            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);
            company.setFiscalYearStart(LocalDate.of(LocalDate.now().getYear(), month, day));
        }

        company = companyRepository.save(company);

        entityManager
                .createNativeQuery("SELECT seed_tt200_coa_from_template(:companyId)")
                .setParameter("companyId", company.getId())
                .getSingleResult();

        Invitation invitation =
                invitationService.createInvitationForCompany(
                        request.adminEmail(),
                        "ADMIN",
                        company.getId(),
                        createdByUserId,
                        httpRequest);

        TenantProvision provision = new TenantProvision();
        provision.setCompanyId(company.getId());
        provision.setCreatedBy(createdByUserId);
        provision.setCoaPreset(request.coaPreset());
        provision.setAdminEmail(request.adminEmail());
        provision.setAdminName(request.adminName());
        provision.setInvitationId(invitation.getId());
        tenantProvisionRepository.save(provision);

        log.info("Tenant provisioned successfully: companyId={}", company.getId());

        return new TenantProvisionResponse(
                company.getId(),
                company.getName(),
                invitation.getId(),
                request.adminEmail(),
                "Tenant created and invitation sent to " + request.adminEmail());
    }

    private String generateCompanyCode() {
        return "C" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
