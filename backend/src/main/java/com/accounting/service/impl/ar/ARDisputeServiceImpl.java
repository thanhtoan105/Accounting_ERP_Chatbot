package com.accounting.service.impl.ar;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.ARStatementDisputeDTO;
import com.accounting.entity.ARStatementDispute;
import com.accounting.repository.ARStatementDisputeRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ARDisputeService;
import com.accounting.service.AuditService;

/**
 * Implementation of ARDisputeService for dispute management.
 */
@Service
@Transactional(readOnly = true)
public class ARDisputeServiceImpl implements ARDisputeService {

  private static final Logger logger = LoggerFactory.getLogger(ARDisputeServiceImpl.class);

  private final ARStatementDisputeRepository disputeRepository;
  private final AuditService auditService;

  public ARDisputeServiceImpl(
      ARStatementDisputeRepository disputeRepository, AuditService auditService) {
    this.disputeRepository = disputeRepository;
    this.auditService = auditService;
  }

  @Override
  public Page<ARStatementDisputeDTO> getDisputes(
      Long customerId,
      ARStatementDispute.DisputeStatus status,
      Instant dateFrom,
      Instant dateTo,
      Pageable pageable) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    var disputePage = disputeRepository.findByCustomerIdAndFilters(
        customerId, companyId, status, dateFrom, dateTo, pageable);

    List<ARStatementDisputeDTO> dtos = disputePage.getContent().stream()
        .map(this::toDisputeDTO)
        .collect(Collectors.toList());

    return new PageImpl<>(dtos, pageable, disputePage.getTotalElements());
  }

  @Override
  public ARStatementDisputeDTO getDisputeById(UUID disputeId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    var disputeOpt = disputeRepository.findByIdAndCompanyId(disputeId, companyId);
    if (disputeOpt.isEmpty()) {
      throw new IllegalArgumentException("Dispute not found: " + disputeId);
    }

    return toDisputeDTO(disputeOpt.get());
  }

  @Override
  @Transactional
  public void resolveDispute(UUID disputeId, String resolutionNotes) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    var disputeOpt = disputeRepository.findByIdAndCompanyId(disputeId, companyId);
    if (disputeOpt.isEmpty()) {
      throw new IllegalArgumentException("Dispute not found: " + disputeId);
    }

    ARStatementDispute dispute = disputeOpt.get();
    dispute.resolve(userId, resolutionNotes);
    disputeRepository.save(dispute);

    // Log audit
    try {
      // TODO: Add logDisputeResolved method to AuditService or use existing method
      logger.info(
          "Dispute {} resolved by user {} for invoice {}",
          disputeId,
          userId,
          dispute.getInvoiceId());
    } catch (Exception e) {
      logger.warn("Failed to log dispute resolution: {}", e.getMessage());
    }
  }

  @Override
  public List<ARStatementDisputeDTO> getDisputeHistory(UUID invoiceId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    List<ARStatementDispute> disputes = disputeRepository.findByInvoiceIdAndCompanyId(invoiceId, companyId);

    return disputes.stream().map(this::toDisputeDTO).collect(Collectors.toList());
  }

  @Override
  public List<ARStatementDisputeDTO> getReconciliationNotes(Long customerId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    List<ARStatementDispute> disputes = disputeRepository.findOpenDisputesByCustomerId(customerId, companyId);

    return disputes.stream()
        .filter(d -> d.getStatusEnum() == ARStatementDispute.DisputeStatus.RESOLVED)
        .map(this::toDisputeDTO)
        .collect(Collectors.toList());
  }

  private ARStatementDisputeDTO toDisputeDTO(ARStatementDispute dispute) {
    ARStatementDisputeDTO dto = new ARStatementDisputeDTO();
    dto.setId(dispute.getId());
    dto.setReconciliationId(dispute.getReconciliationId());
    dto.setInvoiceId(dispute.getInvoiceId());
    dto.setInvoiceNumber(dispute.getInvoiceNumber());
    dto.setSystemAmount(dispute.getSystemAmount());
    dto.setCustomerAmount(dispute.getCustomerAmount());
    dto.setVariance(dispute.getVariance());
    dto.setVarianceType(dispute.getVarianceTypeEnum());
    dto.setNotes(dispute.getNotes());
    dto.setStatus(dispute.getStatusEnum());
    dto.setResolvedAt(dispute.getResolvedAt());
    dto.setResolvedById(dispute.getResolvedById());
    if (dispute.getResolvedBy() != null) {
      dto.setResolvedByName(dispute.getResolvedBy().getFullName());
    }
    dto.setResolutionNotes(dispute.getResolutionNotes());
    dto.setCreatedAt(dispute.getCreatedAt());
    dto.setUpdatedAt(dispute.getUpdatedAt());
    return dto;
  }
}
