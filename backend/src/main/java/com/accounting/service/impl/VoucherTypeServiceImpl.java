package com.accounting.service.impl;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.VoucherTypeCreateRequest;
import com.accounting.dto.VoucherTypeDTO;
import com.accounting.dto.VoucherTypeUpdateRequest;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.VoucherType;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.VoucherTypeRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.VoucherTypeService;

/**
 * Implementation of VoucherTypeService.
 */
@Service
@Transactional
public class VoucherTypeServiceImpl implements VoucherTypeService {

  private final VoucherTypeRepository voucherTypeRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

  public VoucherTypeServiceImpl(
      VoucherTypeRepository voucherTypeRepository,
      ChartOfAccountsRepository chartOfAccountsRepository) {
    this.voucherTypeRepository = voucherTypeRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<VoucherTypeDTO> findAll(String search, String status) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    List<VoucherType> voucherTypes;
    if (search != null && !search.trim().isEmpty() && status != null && !status.trim().isEmpty()) {
      voucherTypes = voucherTypeRepository.findByCompanyIdAndStatusAndSearch(
          companyId, status.trim().toUpperCase(), search.trim());
    } else if (search != null && !search.trim().isEmpty()) {
      voucherTypes = voucherTypeRepository.findByCompanyIdAndSearch(companyId, search.trim());
    } else if (status != null && !status.trim().isEmpty()) {
      voucherTypes = voucherTypeRepository.findByCompanyIdAndStatus(companyId, status.trim().toUpperCase());
    } else {
      voucherTypes = voucherTypeRepository.findByCompanyId(companyId);
    }

    return voucherTypes.stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VoucherTypeDTO> findById(Long id) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    return voucherTypeRepository.findById(id)
        .filter(vt -> vt.getCompanyId().equals(companyId))
        .map(this::toDTO);
  }

  @Override
  public VoucherTypeDTO create(VoucherTypeCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    // Check for duplicate type code
    Optional<VoucherType> existing = voucherTypeRepository.findByCompanyIdAndTypeCode(
        companyId, request.getTypeCode());
    if (existing.isPresent()) {
      throw new IllegalArgumentException("Voucher type with code '" + request.getTypeCode() + "' already exists");
    }

    // Validate accounts exist and are level 3 (if provided)
    if (request.getDebitAccountId() != null) {
      validateAccount(request.getDebitAccountId(), "Debit account");
    }
    if (request.getCreditAccountId() != null) {
      validateAccount(request.getCreditAccountId(), "Credit account");
    }

    VoucherType voucherType = new VoucherType();
    voucherType.setCompanyId(companyId);
    voucherType.setTypeCode(request.getTypeCode().trim());
    voucherType.setTypeName(request.getTypeName().trim());
    voucherType.setDebitAccountId(request.getDebitAccountId());
    voucherType.setCreditAccountId(request.getCreditAccountId());
    voucherType.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
    voucherType.setStatus("ACTIVE");

    VoucherType saved = voucherTypeRepository.save(voucherType);
    return toDTO(saved);
  }

  @Override
  public VoucherTypeDTO update(Long id, VoucherTypeUpdateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    VoucherType voucherType = voucherTypeRepository.findById(id)
        .filter(vt -> vt.getCompanyId().equals(companyId))
        .orElseThrow(() -> new IllegalArgumentException("Voucher type not found"));

    // Check for duplicate type code (excluding current record)
    Optional<VoucherType> existing = voucherTypeRepository.findByCompanyIdAndTypeCode(
        companyId, request.getTypeCode());
    if (existing.isPresent() && !existing.get().getId().equals(id)) {
      throw new IllegalArgumentException("Voucher type with code '" + request.getTypeCode() + "' already exists");
    }

    // Validate accounts exist and are level 3 (if provided)
    if (request.getDebitAccountId() != null) {
      validateAccount(request.getDebitAccountId(), "Debit account");
    }
    if (request.getCreditAccountId() != null) {
      validateAccount(request.getCreditAccountId(), "Credit account");
    }

    voucherType.setTypeCode(request.getTypeCode().trim());
    voucherType.setTypeName(request.getTypeName().trim());
    voucherType.setDebitAccountId(request.getDebitAccountId());
    voucherType.setCreditAccountId(request.getCreditAccountId());
    voucherType.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

    VoucherType saved = voucherTypeRepository.save(voucherType);
    return toDTO(saved);
  }

  @Override
  public void delete(Long id) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    VoucherType voucherType = voucherTypeRepository.findById(id)
        .filter(vt -> vt.getCompanyId().equals(companyId))
        .orElseThrow(() -> new IllegalArgumentException("Voucher type not found"));

    // Use deleteById when available to satisfy null-safety analysis
    if (voucherType.getId() != null) {
      voucherTypeRepository.deleteById(Objects.requireNonNull(voucherType.getId()));
    } else {
      voucherTypeRepository.delete(voucherType);
    }
  }

  @Override
  public VoucherTypeDTO deactivate(Long id) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    VoucherType voucherType = voucherTypeRepository.findById(id)
        .filter(vt -> vt.getCompanyId().equals(companyId))
        .orElseThrow(() -> new IllegalArgumentException("Voucher type not found"));

    voucherType.setStatus("INACTIVE");
    VoucherType saved = voucherTypeRepository.save(voucherType);
    return toDTO(saved);
  }

  @Override
  public VoucherTypeDTO activate(Long id) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    VoucherType voucherType = voucherTypeRepository.findById(id)
        .filter(vt -> vt.getCompanyId().equals(companyId))
        .orElseThrow(() -> new IllegalArgumentException("Voucher type not found"));

    voucherType.setStatus("ACTIVE");
    VoucherType saved = voucherTypeRepository.save(voucherType);
    return toDTO(saved);
  }

  /**
   * Validate that account exists, belongs to company, and is level 3 or higher
   * (code length >= 3).
   * This allows level 3 accounts (e.g., 111) and all their sublevels (e.g., 1111,
   * 1112, etc.).
   * Note: This method should only be called when accountId is not null.
   */
  private void validateAccount(Long accountId, String accountLabel) {
    if (accountId == null) {
      return; // Skip validation if null (optional field)
    }

    Long companyId = CompanyContext.getCompanyId();
    Optional<ChartOfAccount> account = chartOfAccountsRepository.findById(accountId);

    if (account.isEmpty()) {
      throw new IllegalArgumentException(accountLabel + " not found");
    }

    ChartOfAccount acc = account.get();
    if (!acc.getCompanyId().equals(companyId)) {
      throw new IllegalArgumentException(accountLabel + " does not belong to current company");
    }

    // Validate level 3 or higher: code must be at least 3 digits (level 3, 4, etc.)
    if (acc.getCode() == null || acc.getCode().length() < 3) {
      throw new IllegalArgumentException(
          accountLabel + " must be a level 3 account or higher (code length must be at least 3)");
    }
  }

  /**
   * Convert entity to DTO.
   */
  private VoucherTypeDTO toDTO(VoucherType voucherType) {
    VoucherTypeDTO dto = new VoucherTypeDTO();
    dto.setId(voucherType.getId());
    dto.setCompanyId(voucherType.getCompanyId());
    dto.setTypeCode(voucherType.getTypeCode());
    dto.setTypeName(voucherType.getTypeName());
    dto.setDebitAccountId(voucherType.getDebitAccountId());
    dto.setCreditAccountId(voucherType.getCreditAccountId());
    dto.setDescription(voucherType.getDescription());
    dto.setStatus(voucherType.getStatus());
    dto.setCreatedAt(voucherType.getCreatedAt() != null
        ? DATE_TIME_FORMATTER.format(voucherType.getCreatedAt())
        : null);
    dto.setUpdatedAt(voucherType.getUpdatedAt() != null
        ? DATE_TIME_FORMATTER.format(voucherType.getUpdatedAt())
        : null);

    // Load account details for display (only if account IDs are provided)
    if (voucherType.getDebitAccountId() != null && voucherType.getDebitAccountId() > 0) {
      chartOfAccountsRepository.findById(voucherType.getDebitAccountId())
          .ifPresent(account -> {
            dto.setDebitAccountCode(account.getCode());
            dto.setDebitAccountName(account.getName());
          });
    }

    if (voucherType.getCreditAccountId() != null && voucherType.getCreditAccountId() > 0) {
      chartOfAccountsRepository.findById(voucherType.getCreditAccountId())
          .ifPresent(account -> {
            dto.setCreditAccountCode(account.getCode());
            dto.setCreditAccountName(account.getName());
          });
    }

    return dto;
  }
}
