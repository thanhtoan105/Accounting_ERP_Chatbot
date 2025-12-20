package com.accounting.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.VoucherTemplateDTO;
import com.accounting.dto.VoucherTemplateLineDTO;
import com.accounting.dto.VoucherTemplateLineRequest;
import com.accounting.dto.VoucherTemplateRequest;
import com.accounting.dto.VoucherTemplateSummaryDTO;
import com.accounting.dto.VoucherTemplateSummaryDTO.AccountPreview;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.User;
import com.accounting.entity.VoucherTemplate;
import com.accounting.entity.VoucherTemplateLine;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherTemplateRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.VoucherTemplateService;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class VoucherTemplateServiceImpl implements VoucherTemplateService {

  private final VoucherTemplateRepository templateRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final UserRepository userRepository;
  private final EntityManager entityManager;

  public VoucherTemplateServiceImpl(
      VoucherTemplateRepository templateRepository,
      ChartOfAccountsRepository chartOfAccountsRepository,
      UserRepository userRepository,
      EntityManager entityManager) {
    this.templateRepository = templateRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.userRepository = userRepository;
    this.entityManager = entityManager;
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public List<VoucherTemplateSummaryDTO> list(Boolean isActive) {
    Long companyId = requireCompanyId();
    List<VoucherTemplate> templates = isActive == null
        ? templateRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
        : templateRepository.findByCompanyIdAndActiveOrderByCreatedAtDesc(companyId, isActive);
    return templates.stream().map(this::toSummaryDTO).collect(Collectors.toList());
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public Optional<VoucherTemplateDTO> getById(UUID id) {
    Long companyId = requireCompanyId();
    return templateRepository.findByCompanyIdAndId(companyId, id).map(this::toDTO);
  }

  @Override
  public VoucherTemplateDTO create(VoucherTemplateRequest request) {
    Long companyId = requireCompanyId();
    ensureUniqueName(request.getName(), companyId, null);

    VoucherTemplate template = new VoucherTemplate();
    template.setCompanyId(companyId);
    template.setName(request.getName().trim());
    template.setDescription(normalize(request.getDescription()));
    template.setActive(Boolean.TRUE.equals(request.getActive()));

    Long userId = getCurrentUserId();
    template.setCreatedBy(userId);
    template.setUpdatedBy(userId);
    template.setCreatedAt(Instant.now());
    template.setUpdatedAt(Instant.now());

    List<VoucherTemplateLine> lines = buildLines(template, request.getLines(), companyId);
    template.setLines(lines);

    VoucherTemplate saved = templateRepository.save(template);
    return toDTO(saved);
  }

  @Override
  public VoucherTemplateDTO update(UUID id, VoucherTemplateRequest request) {
    Long companyId = requireCompanyId();
    VoucherTemplate template = templateRepository
        .findByCompanyIdAndId(companyId, id)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Voucher template not found: " + id));

    ensureUniqueName(request.getName(), companyId, id);
    template.setName(request.getName().trim());
    template.setDescription(normalize(request.getDescription()));
    template.setActive(Boolean.TRUE.equals(request.getActive()));
    template.setUpdatedAt(Instant.now());
    template.setUpdatedBy(getCurrentUserId());

    // Remove old lines explicitly and flush to ensure deletions are processed
    // before inserting new lines with potentially same line numbers
    List<VoucherTemplateLine> oldLines = new ArrayList<>(template.getLines());
    template.getLines().clear();
    // Explicitly delete old lines to ensure they're removed from database
    for (VoucherTemplateLine oldLine : oldLines) {
      entityManager.remove(oldLine);
    }
    // Flush to ensure deletions are executed before inserts
    entityManager.flush();

    // Now add the new lines
    List<VoucherTemplateLine> newLines = buildLines(template, request.getLines(), companyId);
    template.getLines().addAll(newLines);

    VoucherTemplate saved = templateRepository.save(template);
    return toDTO(saved);
  }

  @Override
  public void delete(UUID id) {
    Long companyId = requireCompanyId();
    VoucherTemplate template = templateRepository
        .findByCompanyIdAndId(companyId, id)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Voucher template not found: " + id));
    templateRepository.deleteById(Objects.requireNonNull(template.getId()));
  }

  @Override
  public VoucherTemplateDTO activate(UUID id) {
    VoucherTemplate template = toggleStatus(id, true);
    return toDTO(template);
  }

  @Override
  public VoucherTemplateDTO deactivate(UUID id) {
    VoucherTemplate template = toggleStatus(id, false);
    return toDTO(template);
  }

  private VoucherTemplate toggleStatus(UUID id, boolean active) {
    Long companyId = requireCompanyId();
    VoucherTemplate template = templateRepository
        .findByCompanyIdAndId(companyId, id)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Voucher template not found: " + id));
    template.setActive(active);
    template.setUpdatedAt(Instant.now());
    template.setUpdatedBy(getCurrentUserId());
    return templateRepository.save(template);
  }

  private List<VoucherTemplateLine> buildLines(
      VoucherTemplate template, List<VoucherTemplateLineRequest> lineRequests, Long companyId) {
    if (lineRequests == null || lineRequests.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "At least one template line is required");
    }
    List<VoucherTemplateLine> lines = new ArrayList<>();
    int lineNumber = 1;
    for (VoucherTemplateLineRequest lineRequest : lineRequests) {
      // Validate at least one account is provided
      if (lineRequest.getDebitAccountId() == null && lineRequest.getCreditAccountId() == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Line " + lineNumber + ": At least one account (debit or credit) is required");
      }

      Long debitAccountId = null;
      Long creditAccountId = null;

      // Validate debit account if provided
      if (lineRequest.getDebitAccountId() != null) {
        ChartOfAccount debit = chartOfAccountsRepository
            .findByIdAndCompanyId(lineRequest.getDebitAccountId(), companyId)
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debit account not found: " + lineRequest.getDebitAccountId()));
        if (!Boolean.TRUE.equals(debit.getPostable())) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Debit account must be a leaf/postable account: " + debit.getCode());
        }
        debitAccountId = debit.getId();
      }

      // Validate credit account if provided
      if (lineRequest.getCreditAccountId() != null) {
        ChartOfAccount credit = chartOfAccountsRepository
            .findByIdAndCompanyId(lineRequest.getCreditAccountId(), companyId)
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Credit account not found: " + lineRequest.getCreditAccountId()));
        if (!Boolean.TRUE.equals(credit.getPostable())) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Credit account must be a leaf/postable account: " + credit.getCode());
        }
        creditAccountId = credit.getId();
      }

      VoucherTemplateLine line = new VoucherTemplateLine();
      line.setCompanyId(companyId);
      line.setTemplate(template);
      line.setLineNumber(lineNumber++);
      line.setDebitAccountId(debitAccountId);
      line.setCreditAccountId(creditAccountId);
      line.setDefaultDescription(normalize(lineRequest.getDefaultDescription()));
      line.setRequiresCustomer(Boolean.TRUE.equals(lineRequest.getRequiresCustomer()));
      line.setRequiresSupplier(Boolean.TRUE.equals(lineRequest.getRequiresSupplier()));
      line.setRequiresCostCenter(Boolean.TRUE.equals(lineRequest.getRequiresCostCenter()));
      line.setLockAccounts(Boolean.TRUE.equals(lineRequest.getLockAccounts()));
      lines.add(line);
    }
    return lines;
  }

  private VoucherTemplateSummaryDTO toSummaryDTO(VoucherTemplate template) {
    VoucherTemplateSummaryDTO dto = new VoucherTemplateSummaryDTO();
    dto.setId(template.getId());
    dto.setName(template.getName());
    dto.setDescription(template.getDescription());
    dto.setActive(template.isActive());
    dto.setCreatedAt(template.getCreatedAt());
    dto.setCreatedBy(resolveUserName(template.getCreatedBy()));

    VoucherTemplateLine firstLine = template.getLines().stream()
        .min(Comparator.comparingInt(VoucherTemplateLine::getLineNumber))
        .orElse(null);
    if (firstLine != null) {
      dto.setFirstLineDebitAccount(
          new AccountPreview(
              firstLine.getDebitAccount() != null ? firstLine.getDebitAccount().getCode() : null,
              firstLine.getDebitAccount() != null ? firstLine.getDebitAccount().getName() : null));
      dto.setFirstLineCreditAccount(
          new AccountPreview(
              firstLine.getCreditAccount() != null ? firstLine.getCreditAccount().getCode() : null,
              firstLine.getCreditAccount() != null
                  ? firstLine.getCreditAccount().getName()
                  : null));
    }
    return dto;
  }

  private VoucherTemplateDTO toDTO(VoucherTemplate template) {
    VoucherTemplateDTO dto = new VoucherTemplateDTO();
    dto.setId(template.getId());
    dto.setName(template.getName());
    dto.setDescription(template.getDescription());
    dto.setActive(template.isActive());
    dto.setCreatedAt(template.getCreatedAt());
    dto.setCreatedBy(resolveUserName(template.getCreatedBy()));
    dto.setFirstLineDebitAccount(null);
    dto.setFirstLineCreditAccount(null);
    if (!template.getLines().isEmpty()) {
      VoucherTemplateLine firstLine = template.getLines().stream()
          .min(Comparator.comparingInt(VoucherTemplateLine::getLineNumber))
          .orElse(null);
      if (firstLine != null) {
        dto.setFirstLineDebitAccount(
            new AccountPreview(
                firstLine.getDebitAccount() != null ? firstLine.getDebitAccount().getCode() : null,
                firstLine.getDebitAccount() != null
                    ? firstLine.getDebitAccount().getName()
                    : null));
        dto.setFirstLineCreditAccount(
            new AccountPreview(
                firstLine.getCreditAccount() != null
                    ? firstLine.getCreditAccount().getCode()
                    : null,
                firstLine.getCreditAccount() != null
                    ? firstLine.getCreditAccount().getName()
                    : null));
      }
    }

    List<VoucherTemplateLineDTO> lines = template.getLines().stream()
        .sorted(Comparator.comparingInt(VoucherTemplateLine::getLineNumber))
        .map(this::toLineDTO)
        .collect(Collectors.toList());
    dto.setLines(lines);
    return dto;
  }

  private VoucherTemplateLineDTO toLineDTO(VoucherTemplateLine line) {
    VoucherTemplateLineDTO dto = new VoucherTemplateLineDTO();
    dto.setId(line.getId());
    dto.setLineNumber(line.getLineNumber());
    dto.setDebitAccountId(line.getDebitAccountId());
    dto.setDebitAccountCode(
        line.getDebitAccount() != null ? line.getDebitAccount().getCode() : null);
    dto.setDebitAccountName(
        line.getDebitAccount() != null ? line.getDebitAccount().getName() : null);
    dto.setCreditAccountId(line.getCreditAccountId());
    dto.setCreditAccountCode(
        line.getCreditAccount() != null ? line.getCreditAccount().getCode() : null);
    dto.setCreditAccountName(
        line.getCreditAccount() != null ? line.getCreditAccount().getName() : null);
    dto.setDefaultDescription(line.getDefaultDescription());
    dto.setRequiresCustomer(line.isRequiresCustomer());
    dto.setRequiresSupplier(line.isRequiresSupplier());
    dto.setRequiresCostCenter(line.isRequiresCostCenter());
    dto.setLockAccounts(line.isLockAccounts());
    return dto;
  }

  private void ensureUniqueName(String name, Long companyId, UUID excludeId) {
    if (name == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template name is required");
    }
    boolean exists = excludeId == null
        ? templateRepository.existsByCompanyIdAndNameIgnoreCase(companyId, name.trim())
        : templateRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(
            companyId, name.trim(), excludeId);
    if (exists) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A voucher template with the same name already exists");
    }
  }

  private String resolveUserName(Long userId) {
    if (userId == null) {
      return "System";
    }
    Optional<User> user = userRepository.findById(userId);
    return user.map(User::getFullName).orElse("User #" + userId);
  }

  private Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Unable to determine current user for voucher template action");
    }
    Object principal = authentication.getPrincipal();

    // Handle Long principal (from JWT authentication filter)
    if (principal instanceof Long userId) {
      return userId;
    }

    if (principal instanceof org.springframework.security.core.userdetails.User springUser) {
      return userRepository
          .findByEmail(springUser.getUsername())
          .map(User::getId)
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.UNAUTHORIZED, "Authenticated user record not found"));
    }
    if (principal instanceof User user) {
      return user.getId();
    }
    throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED, "Unsupported authentication principal for voucher templates");
  }

  private Long requireCompanyId() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header is required)");
    }
    return companyId;
  }

  private String normalize(String value) {
    return value == null ? null : value.trim();
  }
}
