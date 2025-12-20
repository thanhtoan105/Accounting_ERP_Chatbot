package com.accounting.service.impl.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.reconciliation.BankReconciliationDTO;
import com.accounting.dto.reconciliation.BankReconciliationListDTO;
import com.accounting.dto.reconciliation.BankStatementLineDTO;
import com.accounting.dto.reconciliation.CreateAdjustmentRequestDTO;
import com.accounting.dto.reconciliation.CreateReconciliationRequestDTO;
import com.accounting.dto.reconciliation.MatchRequestDTO;
import com.accounting.dto.reconciliation.ReconciliationAdjustmentDTO;
import com.accounting.entity.BankAccount;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.entity.reconciliation.AdjustmentStatus;
import com.accounting.entity.reconciliation.BankReconciliation;
import com.accounting.entity.reconciliation.BankStatementLine;
import com.accounting.entity.reconciliation.MatchStatus;
import com.accounting.entity.reconciliation.ReconciliationAdjustment;
import com.accounting.entity.reconciliation.ReconciliationStatus;
import com.accounting.exception.BusinessException;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.repository.reconciliation.BankReconciliationRepository;
import com.accounting.repository.reconciliation.BankStatementLineRepository;
import com.accounting.repository.reconciliation.ReconciliationAdjustmentRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.BankReconciliationService;
import com.accounting.service.ReconciliationMatcherService;

/**
 * Implementation of BankReconciliationService.
 * Provides full bank reconciliation workflow including CRUD operations,
 * matching, adjustments, and completion.
 *
 * <p>AC6.5-01 through AC6.5-09: Full reconciliation workflow
 */
@Service
@Transactional(readOnly = true)
public class BankReconciliationServiceImpl implements BankReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(BankReconciliationServiceImpl.class);

    private final BankReconciliationRepository reconciliationRepository;
    private final BankStatementLineRepository statementLineRepository;
    private final ReconciliationAdjustmentRepository adjustmentRepository;
    private final BankAccountRepository bankAccountRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherLineRepository voucherLineRepository;
    private final UserRepository userRepository;
    private final ReconciliationMatcherService matcherService;

    public BankReconciliationServiceImpl(
            BankReconciliationRepository reconciliationRepository,
            BankStatementLineRepository statementLineRepository,
            ReconciliationAdjustmentRepository adjustmentRepository,
            BankAccountRepository bankAccountRepository,
            ChartOfAccountsRepository chartOfAccountsRepository,
            VoucherRepository voucherRepository,
            VoucherLineRepository voucherLineRepository,
            UserRepository userRepository,
            ReconciliationMatcherService matcherService) {
        this.reconciliationRepository = reconciliationRepository;
        this.statementLineRepository = statementLineRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.chartOfAccountsRepository = chartOfAccountsRepository;
        this.voucherRepository = voucherRepository;
        this.voucherLineRepository = voucherLineRepository;
        this.userRepository = userRepository;
        this.matcherService = matcherService;
    }

    // ========== Reconciliation CRUD ==========

    @Override
    @Transactional
    public BankReconciliationDTO createReconciliation(CreateReconciliationRequestDTO request) {
        Long companyId = getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate bank account exists
        BankAccount bankAccount = bankAccountRepository
                .findByCompanyIdAndId(companyId, request.getBankAccountId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Bank account not found: " + request.getBankAccountId()));

        // Validate period dates
        if (request.getStatementPeriodStart().isAfter(request.getStatementPeriodEnd())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Period start date must be before or equal to end date");
        }

        // Check for overlapping periods
        boolean hasOverlap = reconciliationRepository.existsOverlappingPeriod(
                companyId,
                request.getBankAccountId(),
                request.getStatementPeriodStart(),
                request.getStatementPeriodEnd());
        if (hasOverlap) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "An overlapping reconciliation period already exists for this account");
        }

        // Calculate ledger balance at period end
        BigDecimal ledgerBalance = calculateLedgerBalance(
                companyId, bankAccount, request.getStatementPeriodEnd());

        // Create reconciliation
        BankReconciliation reconciliation = new BankReconciliation();
        reconciliation.setCompanyId(companyId);
        reconciliation.setBankAccountId(request.getBankAccountId());
        reconciliation.setStatementPeriodStart(request.getStatementPeriodStart());
        reconciliation.setStatementPeriodEnd(request.getStatementPeriodEnd());
        reconciliation.setStatementBalance(request.getStatementBalance());
        reconciliation.setLedgerBalance(ledgerBalance);
        reconciliation.setStatus(ReconciliationStatus.NOT_STARTED);
        reconciliation.setNotes(request.getNotes());

        BankReconciliation saved = reconciliationRepository.save(reconciliation);

        log.info("Created reconciliation {} for bank account {} by user {}",
                saved.getId(), request.getBankAccountId(), userId);

        return convertToDTO(saved, bankAccount, true);
    }

    @Override
    public BankReconciliationDTO getReconciliation(UUID reconciliationId) {
        Long companyId = getCompanyId();

        BankReconciliation reconciliation = reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        BankAccount bankAccount = bankAccountRepository
                .findByCompanyIdAndId(companyId, reconciliation.getBankAccountId())
                .orElse(null);

        return convertToDTO(reconciliation, bankAccount, true);
    }

    @Override
    public Page<BankReconciliationListDTO> listReconciliations(
            Long bankAccountId, String status, Pageable pageable) {
        Long companyId = getCompanyId();

        Page<BankReconciliation> page;

        if (bankAccountId != null && status != null) {
            ReconciliationStatus statusEnum = parseStatus(status);
            page = reconciliationRepository.findByCompanyIdAndBankAccountId(
                    companyId, bankAccountId, pageable);
            // Filter by status in memory (could optimize with custom query)
            List<BankReconciliation> filtered = page.getContent().stream()
                    .filter(r -> r.getStatus() == statusEnum)
                    .collect(Collectors.toList());
            page = new PageImpl<>(filtered, pageable, filtered.size());
        } else if (bankAccountId != null) {
            page = reconciliationRepository.findByCompanyIdAndBankAccountId(
                    companyId, bankAccountId, pageable);
        } else if (status != null) {
            ReconciliationStatus statusEnum = parseStatus(status);
            page = reconciliationRepository.findByCompanyIdAndStatus(companyId, statusEnum, pageable);
        } else {
            page = reconciliationRepository.findByCompanyId(companyId, pageable);
        }

        return page.map(this::convertToListDTO);
    }

    @Override
    @Transactional
    public void deleteReconciliation(UUID reconciliationId) {
        Long companyId = getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        BankReconciliation reconciliation = reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        if (reconciliation.getStatus() == ReconciliationStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot delete a completed reconciliation");
        }

        // Delete adjustments first (cascade)
        adjustmentRepository.deleteByReconciliationId(reconciliationId);

        // Delete statement lines (cascade)
        statementLineRepository.deleteByReconciliationId(reconciliationId);

        // Delete reconciliation
        reconciliationRepository.delete(reconciliation);

        log.info("Deleted reconciliation {} by user {}", reconciliationId, userId);
    }

    // ========== Statement Lines ==========

    @Override
    public Page<BankStatementLineDTO> getStatementLines(
            UUID reconciliationId, MatchStatus matchStatus, Pageable pageable) {
        Long companyId = getCompanyId();

        // Validate reconciliation exists
        reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        Page<BankStatementLine> page;
        if (matchStatus != null) {
            page = statementLineRepository.findByReconciliationIdAndMatchStatus(
                    reconciliationId, matchStatus, pageable);
        } else {
            page = statementLineRepository.findByReconciliationId(reconciliationId, pageable);
        }

        return page.map(this::convertToLineDTO);
    }

    @Override
    public BankStatementLineDTO getStatementLine(UUID reconciliationId, UUID lineId) {
        Long companyId = getCompanyId();

        // Validate reconciliation exists
        reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        BankStatementLine line = statementLineRepository.findById(lineId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Statement line not found: " + lineId));

        if (!line.getReconciliation().getId().equals(reconciliationId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Statement line does not belong to this reconciliation");
        }

        return convertToLineDTO(line);
    }

    // ========== Matching Operations ==========

    @Override
    @Transactional
    public BankStatementLineDTO manualMatch(UUID reconciliationId, MatchRequestDTO matchRequest) {
        Long companyId = getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate reconciliation exists and is not completed
        BankReconciliation reconciliation = reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        if (reconciliation.getStatus() == ReconciliationStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot modify a completed reconciliation");
        }

        // Get statement line
        BankStatementLine line = statementLineRepository.findById(matchRequest.getStatementLineId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Statement line not found: " + matchRequest.getStatementLineId()));

        if (!line.getReconciliation().getId().equals(reconciliationId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Statement line does not belong to this reconciliation");
        }

        // Validate voucher exists and belongs to company
        Voucher voucher = voucherRepository.findByCompanyIdAndId(companyId, matchRequest.getVoucherId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Voucher not found: " + matchRequest.getVoucherId()));

        // Check voucher is posted
        if (!"posted".equals(voucher.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot match to an unposted voucher");
        }

        // Check voucher is not already matched elsewhere
        if (!matcherService.canMatchVoucher(matchRequest.getVoucherId(), reconciliationId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Voucher is already matched in another reconciliation");
        }

        // Calculate confidence and reason
        double confidence = matcherService.calculateMatchConfidence(
                matchRequest.getStatementLineId(), matchRequest.getVoucherId());
        String matchReason = matcherService.generateMatchReason(
                matchRequest.getStatementLineId(), matchRequest.getVoucherId());

        // Apply match
        line.setMatchStatus(MatchStatus.MATCHED);
        line.setMatchedVoucherId(matchRequest.getVoucherId());
        line.setMatchedAt(Instant.now());
        line.setMatchedById(userId);
        line.setMatchConfidence(BigDecimal.valueOf(confidence));
        line.setMatchReason("Manual: " + matchReason);
        if (matchRequest.getNotes() != null) {
            line.setNotes(matchRequest.getNotes());
        }

        BankStatementLine saved = statementLineRepository.save(line);

        // Update reconciliation status if first match
        if (reconciliation.getStatus() == ReconciliationStatus.NOT_STARTED) {
            reconciliation.setStatus(ReconciliationStatus.IN_PROGRESS);
            reconciliationRepository.save(reconciliation);
        }

        log.info("Manual match: statement line {} -> voucher {} by user {}",
                line.getId(), matchRequest.getVoucherId(), userId);

        return convertToLineDTO(saved);
    }

    @Override
    @Transactional
    public BankStatementLineDTO unmatch(UUID reconciliationId, UUID lineId) {
        Long companyId = getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate reconciliation exists and is not completed
        BankReconciliation reconciliation = reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        if (reconciliation.getStatus() == ReconciliationStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot modify a completed reconciliation");
        }

        // Get statement line
        BankStatementLine line = statementLineRepository.findById(lineId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Statement line not found: " + lineId));

        if (!line.getReconciliation().getId().equals(reconciliationId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Statement line does not belong to this reconciliation");
        }

        // Clear match
        line.setMatchStatus(MatchStatus.UNMATCHED);
        line.setMatchedVoucherId(null);
        line.setMatchedAt(null);
        line.setMatchedById(null);
        line.setMatchConfidence(null);
        line.setMatchReason(null);

        BankStatementLine saved = statementLineRepository.save(line);

        log.info("Unmatched statement line {} by user {}", lineId, userId);

        return convertToLineDTO(saved);
    }

    @Override
    @Transactional
    public BankStatementLineDTO updateLineNotes(UUID reconciliationId, UUID lineId, String notes) {
        Long companyId = getCompanyId();

        // Validate reconciliation exists
        reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        // Get statement line
        BankStatementLine line = statementLineRepository.findById(lineId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Statement line not found: " + lineId));

        if (!line.getReconciliation().getId().equals(reconciliationId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Statement line does not belong to this reconciliation");
        }

        line.setNotes(notes);
        BankStatementLine saved = statementLineRepository.save(line);

        return convertToLineDTO(saved);
    }

    // ========== Adjustments ==========

    @Override
    @Transactional
    public ReconciliationAdjustmentDTO createAdjustment(
            UUID reconciliationId, CreateAdjustmentRequestDTO request) {
        Long companyId = getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate reconciliation exists and is not completed
        BankReconciliation reconciliation = reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        if (reconciliation.getStatus() == ReconciliationStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot modify a completed reconciliation");
        }

        // Validate statement line if provided
        BankStatementLine statementLine = null;
        if (request.getStatementLineId() != null) {
            statementLine = statementLineRepository.findById(request.getStatementLineId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Statement line not found: " + request.getStatementLineId()));

            if (!statementLine.getReconciliation().getId().equals(reconciliationId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Statement line does not belong to this reconciliation");
            }

            // Mark line as requiring adjustment
            statementLine.setMatchStatus(MatchStatus.ADJUSTMENT_REQUIRED);
            statementLineRepository.save(statementLine);
        }

        // Create adjustment
        ReconciliationAdjustment adjustment = new ReconciliationAdjustment();
        adjustment.setReconciliation(reconciliation);
        adjustment.setStatementLine(statementLine);
        adjustment.setAdjustmentType(request.getAdjustmentType());
        adjustment.setAmount(request.getAmount());
        adjustment.setDescription(request.getDescription());
        adjustment.setAccountCode(request.getAccountCode());
        adjustment.setStatus(AdjustmentStatus.PENDING);
        adjustment.setCreatedById(userId);

        ReconciliationAdjustment saved = adjustmentRepository.save(adjustment);

        // Update reconciliation status
        if (reconciliation.getStatus() == ReconciliationStatus.NOT_STARTED) {
            reconciliation.setStatus(ReconciliationStatus.IN_PROGRESS);
            reconciliationRepository.save(reconciliation);
        }

        log.info("Created adjustment {} for reconciliation {} by user {}",
                saved.getId(), reconciliationId, userId);

        return convertToAdjustmentDTO(saved);
    }

    @Override
    @Transactional
    public ReconciliationAdjustmentDTO approveAdjustment(UUID reconciliationId, UUID adjustmentId) {
        Long companyId = getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate reconciliation exists
        reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        // Get adjustment
        ReconciliationAdjustment adjustment = adjustmentRepository
                .findByReconciliationIdAndId(reconciliationId, adjustmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Adjustment not found: " + adjustmentId));

        if (!adjustment.isPending()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Adjustment is not in pending status");
        }

        // Approve
        adjustment.setStatus(AdjustmentStatus.APPROVED);
        adjustment.setApprovedById(userId);
        adjustment.setApprovedAt(Instant.now());

        ReconciliationAdjustment saved = adjustmentRepository.save(adjustment);

        log.info("Approved adjustment {} by user {}", adjustmentId, userId);

        return convertToAdjustmentDTO(saved);
    }

    @Override
    @Transactional
    public ReconciliationAdjustmentDTO rejectAdjustment(
            UUID reconciliationId, UUID adjustmentId, String reason) {
        Long companyId = getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate reconciliation exists
        reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        // Get adjustment
        ReconciliationAdjustment adjustment = adjustmentRepository
                .findByReconciliationIdAndId(reconciliationId, adjustmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Adjustment not found: " + adjustmentId));

        if (!adjustment.isPending()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Adjustment is not in pending status");
        }

        // Reject
        adjustment.setStatus(AdjustmentStatus.REJECTED);
        adjustment.setApprovedById(userId);
        adjustment.setApprovedAt(Instant.now());
        // Append reason to description
        if (reason != null && !reason.isBlank()) {
            adjustment.setDescription(adjustment.getDescription() + " [REJECTED: " + reason + "]");
        }

        // Revert statement line status if linked
        if (adjustment.getStatementLine() != null) {
            BankStatementLine line = adjustment.getStatementLine();
            line.setMatchStatus(MatchStatus.UNMATCHED);
            statementLineRepository.save(line);
        }

        ReconciliationAdjustment saved = adjustmentRepository.save(adjustment);

        log.info("Rejected adjustment {} by user {}: {}", adjustmentId, userId, reason);

        return convertToAdjustmentDTO(saved);
    }

    @Override
    @Transactional
    public ReconciliationAdjustmentDTO postAdjustment(UUID reconciliationId, UUID adjustmentId) {
        Long companyId = getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate reconciliation exists
        BankReconciliation reconciliation = reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        // Get adjustment
        ReconciliationAdjustment adjustment = adjustmentRepository
                .findByReconciliationIdAndId(reconciliationId, adjustmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Adjustment not found: " + adjustmentId));

        if (!adjustment.isApproved()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Adjustment must be approved before posting");
        }

        // TODO: Create voucher using VoucherService
        // For now, just mark as posted
        // UUID voucherId = createAdjustmentVoucher(reconciliation, adjustment, userId);
        // adjustment.setVoucherId(voucherId);

        adjustment.setStatus(AdjustmentStatus.POSTED);

        // Update statement line status if linked
        if (adjustment.getStatementLine() != null) {
            BankStatementLine line = adjustment.getStatementLine();
            line.setMatchStatus(MatchStatus.MATCHED);
            line.setMatchReason("Adjustment posted");
            statementLineRepository.save(line);
        }

        ReconciliationAdjustment saved = adjustmentRepository.save(adjustment);

        log.info("Posted adjustment {} by user {}", adjustmentId, userId);

        return convertToAdjustmentDTO(saved);
    }

    @Override
    @Transactional
    public void deleteAdjustment(UUID reconciliationId, UUID adjustmentId) {
        Long companyId = getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate reconciliation exists
        reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        // Get adjustment
        ReconciliationAdjustment adjustment = adjustmentRepository
                .findByReconciliationIdAndId(reconciliationId, adjustmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Adjustment not found: " + adjustmentId));

        if (!adjustment.isPending()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only pending adjustments can be deleted");
        }

        // Revert statement line status if linked
        if (adjustment.getStatementLine() != null) {
            BankStatementLine line = adjustment.getStatementLine();
            line.setMatchStatus(MatchStatus.UNMATCHED);
            statementLineRepository.save(line);
        }

        adjustmentRepository.delete(adjustment);

        log.info("Deleted adjustment {} by user {}", adjustmentId, userId);
    }

    // ========== Completion ==========

    @Override
    @Transactional
    public BankReconciliationDTO completeReconciliation(UUID reconciliationId, String notes) {
        Long companyId = getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        BankReconciliation reconciliation = reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        if (reconciliation.getStatus() == ReconciliationStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Reconciliation is already completed");
        }

        // Check for unmatched lines
        long unmatchedCount = statementLineRepository.countByReconciliationIdAndMatchStatus(
                reconciliationId, MatchStatus.UNMATCHED);
        if (unmatchedCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot complete: " + unmatchedCount + " unmatched statement lines");
        }

        // Check for unposted adjustments
        if (adjustmentRepository.hasUnpostedAdjustments(reconciliationId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot complete: there are pending or approved adjustments that need to be posted");
        }

        // Calculate reconciled balance
        BigDecimal matchedAmount = calculateMatchedAmount(reconciliationId);
        BigDecimal reconciledBalance = reconciliation.getLedgerBalance().add(matchedAmount);

        // Validate balance reconciles
        BigDecimal difference = reconciliation.getStatementBalance().subtract(reconciledBalance).abs();
        if (difference.compareTo(BigDecimal.valueOf(0.01)) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot complete: balance difference of " + difference + " exceeds tolerance");
        }

        // Complete reconciliation
        reconciliation.setStatus(ReconciliationStatus.COMPLETED);
        reconciliation.setReconciledBalance(reconciledBalance);
        reconciliation.setCompletedAt(Instant.now());
        reconciliation.setCompletedById(userId);
        if (notes != null) {
            reconciliation.setNotes(
                    (reconciliation.getNotes() != null ? reconciliation.getNotes() + "\n" : "") + notes);
        }

        // Update bank account
        BankAccount bankAccount = bankAccountRepository
                .findByCompanyIdAndId(companyId, reconciliation.getBankAccountId())
                .orElse(null);
        if (bankAccount != null) {
            bankAccount.setLastReconciledDate(reconciliation.getStatementPeriodEnd());
            bankAccount.setLastReconciledBalance(reconciliation.getStatementBalance());
            bankAccountRepository.save(bankAccount);
        }

        BankReconciliation saved = reconciliationRepository.save(reconciliation);

        log.info("Completed reconciliation {} by user {}", reconciliationId, userId);

        return convertToDTO(saved, bankAccount, true);
    }

    @Override
    @Transactional
    public BankReconciliationDTO reopenReconciliation(UUID reconciliationId) {
        Long companyId = getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        BankReconciliation reconciliation = reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        if (reconciliation.getStatus() != ReconciliationStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Reconciliation is not completed");
        }

        // Reopen
        reconciliation.setStatus(ReconciliationStatus.IN_PROGRESS);
        reconciliation.setCompletedAt(null);
        reconciliation.setCompletedById(null);

        BankReconciliation saved = reconciliationRepository.save(reconciliation);

        BankAccount bankAccount = bankAccountRepository
                .findByCompanyIdAndId(companyId, reconciliation.getBankAccountId())
                .orElse(null);

        log.info("Reopened reconciliation {} by user {}", reconciliationId, userId);

        return convertToDTO(saved, bankAccount, true);
    }

    // ========== Export ==========

    @Override
    public byte[] exportToExcel(UUID reconciliationId) {
        Long companyId = getCompanyId();

        BankReconciliation reconciliation = reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reconciliation not found: " + reconciliationId));

        BankAccount bankAccount = bankAccountRepository
                .findByCompanyIdAndId(companyId, reconciliation.getBankAccountId())
                .orElse(null);

        List<BankStatementLine> lines = statementLineRepository
                .findByReconciliationIdOrderByLineNumber(reconciliationId);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bank Reconciliation");

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Summary section
            int rowNum = 0;
            Row summaryRow = sheet.createRow(rowNum++);
            summaryRow.createCell(0).setCellValue("Bank Account:");
            summaryRow.createCell(1).setCellValue(
                    bankAccount != null ? bankAccount.getBankName() + " - " + bankAccount.getAccountNumber() : "");

            summaryRow = sheet.createRow(rowNum++);
            summaryRow.createCell(0).setCellValue("Period:");
            summaryRow.createCell(1).setCellValue(
                    reconciliation.getStatementPeriodStart() + " to " + reconciliation.getStatementPeriodEnd());

            summaryRow = sheet.createRow(rowNum++);
            summaryRow.createCell(0).setCellValue("Statement Balance:");
            summaryRow.createCell(1).setCellValue(
                    reconciliation.getStatementBalance() != null
                            ? reconciliation.getStatementBalance().doubleValue() : 0);

            summaryRow = sheet.createRow(rowNum++);
            summaryRow.createCell(0).setCellValue("Ledger Balance:");
            summaryRow.createCell(1).setCellValue(
                    reconciliation.getLedgerBalance() != null
                            ? reconciliation.getLedgerBalance().doubleValue() : 0);

            summaryRow = sheet.createRow(rowNum++);
            summaryRow.createCell(0).setCellValue("Status:");
            summaryRow.createCell(1).setCellValue(reconciliation.getStatus().name());

            rowNum++; // Empty row

            // Header row for statement lines
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Line #", "Date", "Description", "Reference", "Debit", "Credit",
                    "Balance", "Status", "Matched Voucher", "Confidence"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            for (BankStatementLine line : lines) {
                Row dataRow = sheet.createRow(rowNum++);
                dataRow.createCell(0).setCellValue(line.getLineNumber());
                dataRow.createCell(1).setCellValue(
                        line.getTransactionDate() != null ? line.getTransactionDate().toString() : "");
                dataRow.createCell(2).setCellValue(line.getDescription() != null ? line.getDescription() : "");
                dataRow.createCell(3).setCellValue(line.getReference() != null ? line.getReference() : "");
                dataRow.createCell(4).setCellValue(
                        line.getDebitAmount() != null ? line.getDebitAmount().doubleValue() : 0);
                dataRow.createCell(5).setCellValue(
                        line.getCreditAmount() != null ? line.getCreditAmount().doubleValue() : 0);
                dataRow.createCell(6).setCellValue(
                        line.getBalance() != null ? line.getBalance().doubleValue() : 0);
                dataRow.createCell(7).setCellValue(line.getMatchStatus().name());
                dataRow.createCell(8).setCellValue(
                        line.getMatchedVoucherId() != null ? line.getMatchedVoucherId().toString() : "");
                dataRow.createCell(9).setCellValue(
                        line.getMatchConfidence() != null
                                ? String.format("%.0f%%", line.getMatchConfidence().doubleValue() * 100) : "");
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to byte array
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to export reconciliation {} to Excel", reconciliationId, e);
            throw new BusinessException("Failed to export to Excel: " + e.getMessage());
        }
    }

    @Override
    public byte[] exportToPdf(UUID reconciliationId) {
        // TODO: Implement PDF export using a PDF library (e.g., iText, OpenPDF)
        throw new UnsupportedOperationException("PDF export not yet implemented");
    }

    // ========== Helper Methods ==========

    private Long getCompanyId() {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
        }
        return companyId;
    }

    private ReconciliationStatus parseStatus(String status) {
        try {
            return ReconciliationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid status: " + status);
        }
    }

    /**
     * Calculate ledger balance for a bank account at a given date.
     * Balance = opening_balance + sum(debit - credit) for all posted transactions up to date.
     */
    private BigDecimal calculateLedgerBalance(Long companyId, BankAccount bankAccount, LocalDate asOfDate) {
        BigDecimal openingBalance = bankAccount.getOpeningBalance() != null
                ? bankAccount.getOpeningBalance() : BigDecimal.ZERO;

        if (bankAccount.getGlAccountCode() == null) {
            return openingBalance;
        }

        ChartOfAccount glAccount = chartOfAccountsRepository
                .findByCompanyIdAndCode(companyId, bankAccount.getGlAccountCode())
                .orElse(null);
        if (glAccount == null) {
            return openingBalance;
        }

        // Get voucher lines for this bank account
        List<VoucherLine> lines = voucherLineRepository
                .findByCompanyIdAndAccountIdAndBankAccountId(companyId, glAccount.getId(), bankAccount.getId());

        BigDecimal transactionBalance = lines.stream()
                .filter(line -> {
                    Optional<Voucher> voucherOpt = voucherRepository.findById(line.getVoucherId());
                    if (voucherOpt.isEmpty()) return false;
                    Voucher voucher = voucherOpt.get();
                    return "posted".equals(voucher.getStatus())
                            && !voucher.getVoucherDate().isAfter(asOfDate);
                })
                .map(line -> {
                    BigDecimal debit = line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO;
                    BigDecimal credit = line.getCredit() != null ? line.getCredit() : BigDecimal.ZERO;
                    return debit.subtract(credit);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return openingBalance.add(transactionBalance);
    }

    /**
     * Calculate total matched amount for reconciliation.
     */
    private BigDecimal calculateMatchedAmount(UUID reconciliationId) {
        List<BankStatementLine> matchedLines = statementLineRepository
                .findByReconciliationIdAndMatchStatus(reconciliationId, MatchStatus.MATCHED);

        return matchedLines.stream()
                .map(line -> {
                    BigDecimal credit = line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO;
                    BigDecimal debit = line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO;
                    return credit.subtract(debit);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Convert BankReconciliation entity to full DTO.
     */
    private BankReconciliationDTO convertToDTO(
            BankReconciliation entity, BankAccount bankAccount, boolean includeDetails) {
        BankReconciliationDTO dto = new BankReconciliationDTO();
        dto.setId(entity.getId());
        dto.setCompanyId(entity.getCompanyId());
        dto.setBankAccountId(entity.getBankAccountId());
        dto.setStatementPeriodStart(entity.getStatementPeriodStart());
        dto.setStatementPeriodEnd(entity.getStatementPeriodEnd());
        dto.setStatementBalance(entity.getStatementBalance());
        dto.setLedgerBalance(entity.getLedgerBalance());
        dto.setReconciledBalance(entity.getReconciledBalance());
        dto.setStatus(entity.getStatus());
        dto.setStatementFileUrl(entity.getStatementFileUrl());
        dto.setStatementFileHash(entity.getStatementFileHash());
        dto.setNotes(entity.getNotes());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCompletedById(entity.getCompletedById());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (bankAccount != null) {
            dto.setBankAccountNumber(bankAccount.getAccountNumber());
            dto.setBankName(bankAccount.getBankName());
        }

        // Load user names
        if (entity.getCompletedById() != null) {
            userRepository.findById(entity.getCompletedById())
                    .ifPresent(user -> dto.setCompletedByName(user.getFullName()));
        }

        // Calculate summary counts
        long totalLines = statementLineRepository.countByReconciliationId(entity.getId());
        long matchedLines = statementLineRepository.countByReconciliationIdAndMatchStatus(
                entity.getId(), MatchStatus.MATCHED);
        long unmatchedLines = statementLineRepository.countByReconciliationIdAndMatchStatus(
                entity.getId(), MatchStatus.UNMATCHED);
        long adjustmentRequired = statementLineRepository.countByReconciliationIdAndMatchStatus(
                entity.getId(), MatchStatus.ADJUSTMENT_REQUIRED);

        dto.setTotalLines((int) totalLines);
        dto.setMatchedLines((int) matchedLines);
        dto.setUnmatchedLines((int) unmatchedLines);
        dto.setAdjustmentRequiredLines((int) adjustmentRequired);

        if (includeDetails) {
            // Load statement lines
            List<BankStatementLine> lines = statementLineRepository
                    .findByReconciliationIdOrderByLineNumber(entity.getId());
            dto.setStatementLines(lines.stream()
                    .map(this::convertToLineDTO)
                    .collect(Collectors.toList()));

            // Load adjustments
            List<ReconciliationAdjustment> adjustments = adjustmentRepository
                    .findByReconciliationId(entity.getId());
            dto.setAdjustments(adjustments.stream()
                    .map(this::convertToAdjustmentDTO)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    /**
     * Convert BankReconciliation entity to list DTO.
     */
    private BankReconciliationListDTO convertToListDTO(BankReconciliation entity) {
        BankReconciliationListDTO dto = new BankReconciliationListDTO();
        dto.setId(entity.getId());
        dto.setBankAccountId(entity.getBankAccountId());
        dto.setStatementPeriodStart(entity.getStatementPeriodStart());
        dto.setStatementPeriodEnd(entity.getStatementPeriodEnd());
        dto.setStatementBalance(entity.getStatementBalance());
        dto.setLedgerBalance(entity.getLedgerBalance());
        dto.setStatus(entity.getStatus());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // Load bank account info
        Long companyId = entity.getCompanyId();
        bankAccountRepository.findByCompanyIdAndId(companyId, entity.getBankAccountId())
                .ifPresent(bankAccount -> {
                    dto.setBankAccountNumber(bankAccount.getAccountNumber());
                    dto.setBankName(bankAccount.getBankName());
                });

        // Summary counts
        long totalLines = statementLineRepository.countByReconciliationId(entity.getId());
        long matchedLines = statementLineRepository.countByReconciliationIdAndMatchStatus(
                entity.getId(), MatchStatus.MATCHED);
        long unmatchedLines = statementLineRepository.countByReconciliationIdAndMatchStatus(
                entity.getId(), MatchStatus.UNMATCHED);

        dto.setTotalLines((int) totalLines);
        dto.setMatchedLines((int) matchedLines);
        dto.setUnmatchedLines((int) unmatchedLines);

        return dto;
    }

    /**
     * Convert BankStatementLine entity to DTO.
     */
    private BankStatementLineDTO convertToLineDTO(BankStatementLine line) {
        BankStatementLineDTO dto = new BankStatementLineDTO();
        dto.setId(line.getId());
        dto.setReconciliationId(line.getReconciliation().getId());
        dto.setLineNumber(line.getLineNumber());
        dto.setTransactionDate(line.getTransactionDate());
        dto.setDescription(line.getDescription());
        dto.setReference(line.getReference());
        dto.setDebitAmount(line.getDebitAmount());
        dto.setCreditAmount(line.getCreditAmount());
        dto.setBalance(line.getBalance());
        dto.setMatchStatus(line.getMatchStatus());
        dto.setMatchedVoucherId(line.getMatchedVoucherId());
        dto.setMatchedAt(line.getMatchedAt());
        dto.setMatchedById(line.getMatchedById());
        dto.setMatchConfidence(line.getMatchConfidence());
        dto.setMatchReason(line.getMatchReason());
        dto.setNotes(line.getNotes());
        dto.setCreatedAt(line.getCreatedAt());
        dto.setUpdatedAt(line.getUpdatedAt());

        // Load voucher number if matched
        if (line.getMatchedVoucherId() != null) {
            voucherRepository.findById(line.getMatchedVoucherId())
                    .ifPresent(voucher -> dto.setMatchedVoucherNumber(voucher.getVoucherNumber()));
        }

        // Load user name if matched
        if (line.getMatchedById() != null) {
            userRepository.findById(line.getMatchedById())
                    .ifPresent(user -> dto.setMatchedByName(user.getFullName()));
        }

        return dto;
    }

    /**
     * Convert ReconciliationAdjustment entity to DTO.
     */
    private ReconciliationAdjustmentDTO convertToAdjustmentDTO(ReconciliationAdjustment adjustment) {
        ReconciliationAdjustmentDTO dto = new ReconciliationAdjustmentDTO();
        dto.setId(adjustment.getId());
        dto.setReconciliationId(adjustment.getReconciliation().getId());
        dto.setStatementLineId(
                adjustment.getStatementLine() != null ? adjustment.getStatementLine().getId() : null);
        dto.setAdjustmentType(adjustment.getAdjustmentType());
        dto.setAmount(adjustment.getAmount());
        dto.setDescription(adjustment.getDescription());
        dto.setAccountCode(adjustment.getAccountCode());
        dto.setVoucherId(adjustment.getVoucherId());
        dto.setStatus(adjustment.getStatus());
        dto.setCreatedById(adjustment.getCreatedById());
        dto.setApprovedById(adjustment.getApprovedById());
        dto.setCreatedAt(adjustment.getCreatedAt());
        dto.setApprovedAt(adjustment.getApprovedAt());

        // Load user names
        if (adjustment.getCreatedById() != null) {
            userRepository.findById(adjustment.getCreatedById())
                    .ifPresent(user -> dto.setCreatedByName(user.getFullName()));
        }
        if (adjustment.getApprovedById() != null) {
            userRepository.findById(adjustment.getApprovedById())
                    .ifPresent(user -> dto.setApprovedByName(user.getFullName()));
        }

        return dto;
    }
}
