package com.accounting.service.impl;

import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.dto.UpdateBasicCompanySettingsRequest;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.CompanyService;
import com.accounting.service.AuditService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

@Service
@Transactional
public class CompanyServiceImpl implements CompanyService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9-]{3,16}$");
    private static final Pattern TAX_CODE_PATTERN = Pattern.compile("^\\d{10}$");

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final com.accounting.service.StorageService storageService;
    private final HttpServletRequest httpServletRequest;

    public CompanyServiceImpl(
            CompanyRepository companyRepository,
            UserRepository userRepository,
            AuditService auditService,
            HttpServletRequest httpServletRequest,
            com.accounting.service.StorageService storageService) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.httpServletRequest = httpServletRequest;
        this.storageService = storageService;
    }

    @Override
    public Company createCompany(Company input) {
        validate(input, true);
        Company created = companyRepository.save(input);

        // Associate current user with newly created company if user has no company
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() != null) {
                Long currentUserId = Long.parseLong(authentication.getPrincipal().toString());
                Optional<User> userOpt = userRepository.findById(currentUserId);

                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    // Only associate if user has no company (companyId is null)
                    if (user.getCompanyId() == null) {
                        // Set company context BEFORE updating user to avoid CompanyScopeEnforcer error
                        // When updating a User with companyId, the enforcer requires X-Company-Id
                        // header
                        // But since this is the first company, we set the context from the created
                        // company
                        CompanyContext.setCompanyId(created.getId());

                        user.setCompanyId(created.getId());
                        user.setUpdatedAt(Instant.now());
                        userRepository.save(user);
                    }
                }
            }
        } catch (Exception e) {
            // Log error but don't fail company creation if user association fails
            // This allows company creation to succeed even if user context is missing
            // (e.g., in tests or edge cases)
        }

        return created;
    }

    @Override
    public Company updateCompany(Long id, Company input) {
        Company existing = companyRepository
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Company not found"));

        if (input.getCode() != null && !input.getCode().equals(existing.getCode())) {
            if (companyRepository.existsByCode(input.getCode())) {
                throw new ValidationException("DUPLICATE: code exists");
            }
            existing.setCode(input.getCode());
        }
        if (input.getTaxCode() != null && !input.getTaxCode().equals(existing.getTaxCode())) {
            if (companyRepository.existsByTaxCode(input.getTaxCode())) {
                throw new ValidationException("DUPLICATE: tax_code exists");
            }
            existing.setTaxCode(input.getTaxCode());
        }
        if (input.getName() != null) {
            existing.setName(input.getName());
        }
        if (input.getAddress() != null) {
            existing.setAddress(input.getAddress());
        }
        if (input.getLogoUrl() != null) {
            existing.setLogoUrl(input.getLogoUrl());
        }

        validate(existing, false);
        return companyRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> getCompanyByCode(String code) {
        return companyRepository.findByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> listCompanies() {
        return companyRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Company getCurrentCompanySettings() {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ValidationException("Missing company context");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new EntityNotFoundException("Company not found"));
    }

    @Override
    public Company updateCurrentCompanySettings(UpdateBasicCompanySettingsRequest request, MultipartFile logoFile) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ValidationException("Missing company context");
        }

        Company existing = companyRepository.findById(companyId)
                .orElseThrow(() -> new EntityNotFoundException("Company not found"));

        // Capture old values for audit
        java.util.Map<String, String> oldValues = new java.util.HashMap<>();
        oldValues.put("name", existing.getName());
        oldValues.put("taxCode", existing.getTaxCode());
        oldValues.put("address", existing.getAddress());
        oldValues.put("contactEmail", existing.getContactEmail());
        oldValues.put("contactPhone", existing.getContactPhone());
        oldValues.put("logoUrl", existing.getLogoUrl());
        oldValues.put("fiscalYearStart",
                existing.getFiscalYearStart() == null ? null : existing.getFiscalYearStart().toString());

        // Validate optional logo file (AC: image/png or image/jpeg, ≤256KB)
        if (logoFile != null && !logoFile.isEmpty()) {
            String contentType = logoFile.getContentType();
            if (contentType == null || !(contentType.equals("image/png") || contentType.equals("image/jpeg"))) {
                throw new ValidationException("VALIDATION_ERROR: logo must be PNG or JPEG");
            }
            long maxSize = 256 * 1024; // 256KB
            if (logoFile.getSize() > maxSize) {
                throw new ValidationException("VALIDATION_ERROR: logo file too large (max 256KB)");
            }
            // Upload to Supabase Storage and set returned public URL
            String publicUrl = storageService.uploadCompanyLogo(companyId, logoFile);
            existing.setLogoUrl(publicUrl);
        }

        // Update fields
        if (request.getName() != null)
            existing.setName(request.getName());
        if (request.getAddress() != null)
            existing.setAddress(request.getAddress());
        if (request.getContactEmail() != null)
            existing.setContactEmail(request.getContactEmail());
        if (request.getContactPhone() != null)
            existing.setContactPhone(request.getContactPhone());
        if (request.getLogoUrl() != null)
            existing.setLogoUrl(request.getLogoUrl());
        LocalDate fys = request.getFiscalYearStart();
        if (fys != null)
            existing.setFiscalYearStart(fys);

        // Handle tax code separately due to uniqueness and checksum
        if (request.getTaxCode() != null && !request.getTaxCode().equals(existing.getTaxCode())) {
            if (companyRepository.existsByTaxCode(request.getTaxCode())) {
                throw new ValidationException("DUPLICATE: tax_code exists");
            }
            if (!isValidVietnamTaxCode(request.getTaxCode())) {
                throw new ValidationException("VALIDATION_ERROR: tax_code checksum invalid");
            }
            existing.setTaxCode(request.getTaxCode());
        }

        // Validate requireds
        validate(existing, false);

        Company saved = companyRepository.save(existing);

        // Build new values for audit
        java.util.Map<String, String> newValues = new java.util.HashMap<>();
        newValues.put("name", saved.getName());
        newValues.put("taxCode", saved.getTaxCode());
        newValues.put("address", saved.getAddress());
        newValues.put("contactEmail", saved.getContactEmail());
        newValues.put("contactPhone", saved.getContactPhone());
        newValues.put("logoUrl", saved.getLogoUrl());
        newValues.put("fiscalYearStart",
                saved.getFiscalYearStart() == null ? null : saved.getFiscalYearStart().toString());

        // Audit log company settings update (best-effort)
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long currentUserId = authentication != null && authentication.getPrincipal() != null
                    ? Long.parseLong(authentication.getPrincipal().toString())
                    : null;
            auditService.logCompanySettingsUpdated(
                    companyId,
                    currentUserId,
                    oldValues,
                    newValues,
                    httpServletRequest);
        } catch (Exception ignore) {
            // Do not block on audit failures
        }

        return saved;
    }

    private boolean isValidVietnamTaxCode(String taxCode) {
        if (taxCode == null || !TAX_CODE_PATTERN.matcher(taxCode).matches()) {
            return false;
        }
        // VN 10-digit tax code checksum algorithm (weights 31 29 23 19 17 13 7 5 3)
        int[] weights = { 31, 29, 23, 19, 17, 13, 7, 5, 3 };
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (taxCode.charAt(i) - '0') * weights[i];
        }
        int checksum = (10 - (sum % 10)) % 10;
        return checksum == (taxCode.charAt(9) - '0');
    }

    private void validate(Company company, boolean creating) {
        if (company.getCode() == null || !CODE_PATTERN.matcher(company.getCode()).matches()) {
            throw new ValidationException("VALIDATION_ERROR: code invalid");
        }
        if (company.getTaxCode() == null
                || !TAX_CODE_PATTERN.matcher(company.getTaxCode()).matches()) {
            throw new ValidationException("VALIDATION_ERROR: tax_code must be 10 digits");
        }
        if (company.getName() == null || company.getName().isBlank()) {
            throw new ValidationException("VALIDATION_ERROR: name required");
        }
        if (company.getAddress() == null || company.getAddress().isBlank()) {
            throw new ValidationException("VALIDATION_ERROR: address required");
        }
        // Minimal VN address formatting guard: allow letters/numbers and common
        // separators
        String address = company.getAddress();
        if (address != null) {
            String normalized = address.trim();
            if (normalized.length() < 5 || normalized.length() > 512) {
                throw new ValidationException("VALIDATION_ERROR: address length invalid");
            }
            if (!normalized.matches("[\\p{L}0-9 ,./-]+")) {
                throw new ValidationException("VALIDATION_ERROR: address format invalid");
            }
        }
        if (creating) {
            if (companyRepository.existsByCode(company.getCode())) {
                throw new ValidationException("DUPLICATE: code exists");
            }
            if (companyRepository.existsByTaxCode(company.getTaxCode())) {
                throw new ValidationException("DUPLICATE: tax_code exists");
            }
        }
    }
}
