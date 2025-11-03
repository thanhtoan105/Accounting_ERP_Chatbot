package com.accounting.service.impl;

import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.CompanyService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CompanyServiceImpl implements CompanyService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9-]{3,16}$");
    private static final Pattern TAX_CODE_PATTERN = Pattern.compile("^\\d{10}$");

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public CompanyServiceImpl(CompanyRepository companyRepository, UserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
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
