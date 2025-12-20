package com.accounting.service.impl.reconciliation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.reconciliation.AutoMatchConfigDTO;
import com.accounting.dto.reconciliation.AutoMatchResultDTO;
import com.accounting.dto.reconciliation.AutoMatchResultDTO.MatchSuggestionDTO;
import com.accounting.dto.reconciliation.BankStatementLineDTO;
import com.accounting.dto.reconciliation.LedgerTransactionDTO;
import com.accounting.entity.BankAccount;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.entity.reconciliation.BankReconciliation;
import com.accounting.entity.reconciliation.BankStatementLine;
import com.accounting.entity.reconciliation.MatchStatus;
import com.accounting.exception.BusinessException;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.repository.reconciliation.BankReconciliationRepository;
import com.accounting.repository.reconciliation.BankStatementLineRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ReconciliationMatcherService;

/**
 * Implementation of ReconciliationMatcherService.
 * Provides auto-matching algorithm for bank reconciliation.
 *
 * <p>Matching algorithm uses weighted scoring:
 * <ul>
 *   <li>Date: 30% weight - Exact match = 1.0, ±1 day = 0.8, ±2-3 days = 0.5, >3 days = 0</li>
 *   <li>Amount: 50% weight - Exact match = 1.0, tolerance-based matching available</li>
 *   <li>Reference: 20% weight - Levenshtein similarity (0-1)</li>
 * </ul>
 *
 * <p>AC6.5-03: Auto-Suggest Matches
 */
@Service
@Transactional(readOnly = true)
public class ReconciliationMatcherServiceImpl implements ReconciliationMatcherService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationMatcherServiceImpl.class);

    // Matching weight constants
    private static final double DATE_WEIGHT = 0.3;
    private static final double AMOUNT_WEIGHT = 0.5;
    private static final double REFERENCE_WEIGHT = 0.2;

    // Default configuration values
    private static final int DEFAULT_DATE_TOLERANCE_DAYS = 3;
    private static final BigDecimal DEFAULT_MIN_CONFIDENCE = new BigDecimal("0.7");

    private final BankReconciliationRepository reconciliationRepository;
    private final BankStatementLineRepository statementLineRepository;
    private final BankAccountRepository bankAccountRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final VoucherLineRepository voucherLineRepository;
    private final VoucherRepository voucherRepository;
    private final LevenshteinDistance levenshteinDistance;

    public ReconciliationMatcherServiceImpl(
            BankReconciliationRepository reconciliationRepository,
            BankStatementLineRepository statementLineRepository,
            BankAccountRepository bankAccountRepository,
            ChartOfAccountsRepository chartOfAccountsRepository,
            VoucherLineRepository voucherLineRepository,
            VoucherRepository voucherRepository) {
        this.reconciliationRepository = reconciliationRepository;
        this.statementLineRepository = statementLineRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.chartOfAccountsRepository = chartOfAccountsRepository;
        this.voucherLineRepository = voucherLineRepository;
        this.voucherRepository = voucherRepository;
        this.levenshteinDistance = LevenshteinDistance.getDefaultInstance();
    }

    @Override
    @Transactional
    public AutoMatchResultDTO runAutoMatch(UUID reconciliationId, AutoMatchConfigDTO config) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new BusinessException("Company context not set");
        }

        // Validate reconciliation exists and belongs to company
        reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new BusinessException("Reconciliation not found: " + reconciliationId));

        // Apply default config if null
        if (config == null) {
            config = new AutoMatchConfigDTO();
        }

        // Get unmatched statement lines
        List<BankStatementLine> unmatchedLines = statementLineRepository
                .findByReconciliationIdAndMatchStatus(reconciliationId, MatchStatus.UNMATCHED);

        // Get ledger transactions for this bank account
        List<LedgerTransactionDTO> ledgerTransactions = getLedgerTransactions(reconciliationId);

        // Track which vouchers have been matched in this run
        Set<UUID> matchedVoucherIds = new HashSet<>();

        AutoMatchResultDTO result = new AutoMatchResultDTO();
        result.setTotalLinesProcessed(unmatchedLines.size());

        int matchesFound = 0;
        int matchesApplied = 0;
        List<MatchSuggestionDTO> suggestions = new ArrayList<>();

        BigDecimal minConfidence = config.getMinimumConfidence() != null
                ? config.getMinimumConfidence()
                : DEFAULT_MIN_CONFIDENCE;
        Boolean autoApply = config.getAutoApply() != null ? config.getAutoApply() : false;

        for (BankStatementLine statementLine : unmatchedLines) {
            MatchSuggestionDTO bestMatch = findBestMatch(
                    statementLine, ledgerTransactions, matchedVoucherIds, reconciliationId, config);

            if (bestMatch != null) {
                matchesFound++;
                suggestions.add(bestMatch);

                // Auto-apply if enabled and confidence is above threshold
                if (autoApply && bestMatch.getConfidence() >= minConfidence.doubleValue()) {
                    applyMatch(statementLine, bestMatch);
                    matchedVoucherIds.add(bestMatch.getLedgerTransaction().getVoucherId());
                    matchesApplied++;
                }
            }
        }

        result.setMatchesFound(matchesFound);
        result.setMatchesApplied(matchesApplied);
        result.setNoMatchFound(unmatchedLines.size() - matchesFound);
        result.setSuggestions(suggestions);

        log.info("Auto-match completed for reconciliation {}: {} lines processed, {} matches found, {} applied",
                reconciliationId, unmatchedLines.size(), matchesFound, matchesApplied);

        return result;
    }

    @Override
    public List<LedgerTransactionDTO> getLedgerTransactions(UUID reconciliationId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new BusinessException("Company context not set");
        }

        BankReconciliation reconciliation = reconciliationRepository
                .findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new BusinessException("Reconciliation not found: " + reconciliationId));

        // Get bank account and its GL account
        BankAccount bankAccount = bankAccountRepository
                .findByCompanyIdAndId(companyId, reconciliation.getBankAccountId())
                .orElseThrow(() -> new BusinessException("Bank account not found: " + reconciliation.getBankAccountId()));

        String glAccountCode = bankAccount.getGlAccountCode();
        if (glAccountCode == null || glAccountCode.isBlank()) {
            log.warn("Bank account {} has no GL account code, returning empty ledger transactions",
                    bankAccount.getId());
            return List.of();
        }

        // Get GL account ID from code
        ChartOfAccount glAccount = chartOfAccountsRepository
                .findByCompanyIdAndCode(companyId, glAccountCode)
                .orElseThrow(() -> new BusinessException("GL account not found: " + glAccountCode));

        // Query voucher lines for this bank account
        List<VoucherLine> voucherLines = voucherLineRepository
                .findByCompanyIdAndAccountIdAndBankAccountId(companyId, glAccount.getId(),
                        bankAccount.getId());

        // Filter to posted vouchers within the reconciliation period
        LocalDate periodStart = reconciliation.getStatementPeriodStart();
        LocalDate periodEnd = reconciliation.getStatementPeriodEnd();

        List<LedgerTransactionDTO> transactions = new ArrayList<>();

        for (VoucherLine line : voucherLines) {
            Voucher voucher = voucherRepository.findById(line.getVoucherId()).orElse(null);
            if (voucher == null) {
                continue;
            }

            // Only include posted vouchers
            if (!"posted".equals(voucher.getStatus())) {
                continue;
            }

            // Filter by date range
            LocalDate voucherDate = voucher.getVoucherDate();
            if (voucherDate.isBefore(periodStart) || voucherDate.isAfter(periodEnd)) {
                continue;
            }

            LedgerTransactionDTO dto = new LedgerTransactionDTO();
            dto.setVoucherId(voucher.getId());
            dto.setVoucherNumber(voucher.getVoucherNumber());
            dto.setVoucherType(voucher.getStatus()); // Could be enhanced with voucher type if available
            dto.setTransactionDate(voucherDate);
            dto.setDescription(voucher.getDescription());
            dto.setReference(line.getDescription()); // Line description as reference
            dto.setDebitAmount(line.getDebit());
            dto.setCreditAmount(line.getCredit());
            dto.setStatus(voucher.getStatus());

            // Check if voucher is already matched
            dto.setAlreadyMatched(statementLineRepository.existsByMatchedVoucherId(voucher.getId()));

            transactions.add(dto);
        }

        log.debug("Found {} ledger transactions for reconciliation {}", transactions.size(), reconciliationId);
        return transactions;
    }

    @Override
    public double calculateMatchConfidence(UUID statementLineId, UUID voucherId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new BusinessException("Company context not set");
        }

        BankStatementLine statementLine = statementLineRepository.findById(statementLineId)
                .orElseThrow(() -> new BusinessException("Statement line not found: " + statementLineId));

        Voucher voucher = voucherRepository.findByCompanyIdAndId(companyId, voucherId)
                .orElseThrow(() -> new BusinessException("Voucher not found: " + voucherId));

        // Get voucher line for the specific bank account
        List<VoucherLine> voucherLines = voucherLineRepository
                .findByCompanyIdAndVoucherIdOrderByLineNumberAsc(companyId, voucherId);

        if (voucherLines.isEmpty()) {
            return 0.0;
        }

        // Sum the amounts for the voucher
        BigDecimal voucherDebit = voucherLines.stream()
                .map(VoucherLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal voucherCredit = voucherLines.stream()
                .map(VoucherLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String voucherReference = voucherLines.get(0).getDescription();

        return calculateConfidenceScore(
                statementLine.getTransactionDate(),
                voucher.getVoucherDate(),
                statementLine.getDebitAmount(),
                statementLine.getCreditAmount(),
                voucherDebit,
                voucherCredit,
                statementLine.getReference(),
                voucherReference,
                DEFAULT_DATE_TOLERANCE_DAYS,
                BigDecimal.ZERO);
    }

    @Override
    public String generateMatchReason(UUID statementLineId, UUID voucherId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new BusinessException("Company context not set");
        }

        BankStatementLine statementLine = statementLineRepository.findById(statementLineId)
                .orElseThrow(() -> new BusinessException("Statement line not found: " + statementLineId));

        Voucher voucher = voucherRepository.findByCompanyIdAndId(companyId, voucherId)
                .orElseThrow(() -> new BusinessException("Voucher not found: " + voucherId));

        // Get voucher line amounts
        List<VoucherLine> voucherLines = voucherLineRepository
                .findByCompanyIdAndVoucherIdOrderByLineNumberAsc(companyId, voucherId);

        BigDecimal voucherDebit = voucherLines.stream()
                .map(VoucherLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal voucherCredit = voucherLines.stream()
                .map(VoucherLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String voucherReference = voucherLines.isEmpty() ? "" : voucherLines.get(0).getDescription();

        return buildMatchReason(
                statementLine.getTransactionDate(),
                voucher.getVoucherDate(),
                statementLine.getDebitAmount(),
                statementLine.getCreditAmount(),
                voucherDebit,
                voucherCredit,
                statementLine.getReference(),
                voucherReference);
    }

    @Override
    public boolean canMatchVoucher(UUID voucherId, UUID reconciliationId) {
        // Check if voucher is already matched in a different reconciliation
        return !statementLineRepository.existsByMatchedVoucherIdAndReconciliationIdNot(voucherId, reconciliationId);
    }

    /**
     * Find the best matching ledger transaction for a statement line.
     */
    private MatchSuggestionDTO findBestMatch(
            BankStatementLine statementLine,
            List<LedgerTransactionDTO> ledgerTransactions,
            Set<UUID> matchedVoucherIds,
            UUID reconciliationId,
            AutoMatchConfigDTO config) {

        int dateTolerance = config.getDateTolerance() != null
                ? config.getDateTolerance()
                : DEFAULT_DATE_TOLERANCE_DAYS;
        BigDecimal amountTolerance = config.getAmountTolerance() != null
                ? config.getAmountTolerance()
                : BigDecimal.ZERO;

        MatchSuggestionDTO bestMatch = null;
        double bestConfidence = 0.0;

        for (LedgerTransactionDTO ledgerTx : ledgerTransactions) {
            // Skip if voucher is already matched
            if (ledgerTx.isAlreadyMatched() || matchedVoucherIds.contains(ledgerTx.getVoucherId())) {
                continue;
            }

            // Skip if voucher is matched in another reconciliation
            if (!canMatchVoucher(ledgerTx.getVoucherId(), reconciliationId)) {
                continue;
            }

            double confidence = calculateConfidenceScore(
                    statementLine.getTransactionDate(),
                    ledgerTx.getTransactionDate(),
                    statementLine.getDebitAmount(),
                    statementLine.getCreditAmount(),
                    ledgerTx.getDebitAmount(),
                    ledgerTx.getCreditAmount(),
                    statementLine.getReference(),
                    ledgerTx.getReference(),
                    dateTolerance,
                    amountTolerance);

            if (confidence > bestConfidence && confidence > 0) {
                bestConfidence = confidence;
                bestMatch = createMatchSuggestion(statementLine, ledgerTx, confidence,
                        statementLine.getTransactionDate(), ledgerTx.getTransactionDate(),
                        statementLine.getDebitAmount(), statementLine.getCreditAmount(),
                        ledgerTx.getDebitAmount(), ledgerTx.getCreditAmount(),
                        statementLine.getReference(), ledgerTx.getReference());
            }
        }

        return bestMatch;
    }

    /**
     * Calculate match confidence score using weighted factors.
     */
    private double calculateConfidenceScore(
            LocalDate statementDate,
            LocalDate voucherDate,
            BigDecimal statementDebit,
            BigDecimal statementCredit,
            BigDecimal voucherDebit,
            BigDecimal voucherCredit,
            String statementRef,
            String voucherRef,
            int dateTolerance,
            BigDecimal amountTolerance) {

        // Date score
        double dateScore = calculateDateScore(statementDate, voucherDate, dateTolerance);

        // Amount score
        double amountScore = calculateAmountScore(
                statementDebit, statementCredit, voucherDebit, voucherCredit, amountTolerance);

        // Reference score
        double referenceScore = calculateReferenceScore(statementRef, voucherRef);

        // Weighted confidence
        return (DATE_WEIGHT * dateScore) + (AMOUNT_WEIGHT * amountScore) + (REFERENCE_WEIGHT * referenceScore);
    }

    /**
     * Calculate date matching score.
     *
     * @param statementDate statement transaction date
     * @param voucherDate voucher date
     * @param toleranceDays maximum days tolerance
     * @return score 0.0 to 1.0
     */
    private double calculateDateScore(LocalDate statementDate, LocalDate voucherDate, int toleranceDays) {
        if (statementDate == null || voucherDate == null) {
            return 0.0;
        }

        long daysDiff = Math.abs(ChronoUnit.DAYS.between(statementDate, voucherDate));

        if (daysDiff == 0) {
            return 1.0; // Exact match
        } else if (daysDiff == 1) {
            return 0.8; // ±1 day
        } else if (daysDiff <= toleranceDays) {
            return 0.5; // Within tolerance
        } else {
            return 0.0; // Outside tolerance
        }
    }

    /**
     * Calculate amount matching score.
     * Bank statement: debit = withdrawal (money out), credit = deposit (money in)
     * Ledger: For bank account (1121), debit = increase, credit = decrease
     *
     * @param statementDebit statement debit (withdrawal)
     * @param statementCredit statement credit (deposit)
     * @param voucherDebit voucher line debit
     * @param voucherCredit voucher line credit
     * @param tolerancePercent tolerance percentage (0 = exact match required)
     * @return score 0.0 to 1.0
     */
    private double calculateAmountScore(
            BigDecimal statementDebit,
            BigDecimal statementCredit,
            BigDecimal voucherDebit,
            BigDecimal voucherCredit,
            BigDecimal tolerancePercent) {

        // Normalize amounts to zero if null
        BigDecimal stmtDebit = statementDebit != null ? statementDebit : BigDecimal.ZERO;
        BigDecimal stmtCredit = statementCredit != null ? statementCredit : BigDecimal.ZERO;
        BigDecimal vchDebit = voucherDebit != null ? voucherDebit : BigDecimal.ZERO;
        BigDecimal vchCredit = voucherCredit != null ? voucherCredit : BigDecimal.ZERO;

        // Bank statement debit (withdrawal) matches voucher credit (decrease in bank)
        // Bank statement credit (deposit) matches voucher debit (increase in bank)
        BigDecimal statementAmount = stmtCredit.subtract(stmtDebit); // Positive = deposit, negative = withdrawal
        BigDecimal voucherAmount = vchDebit.subtract(vchCredit); // Positive = increase, negative = decrease

        if (statementAmount.compareTo(BigDecimal.ZERO) == 0 && voucherAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 1.0; // Both zero
        }

        if (statementAmount.compareTo(voucherAmount) == 0) {
            return 1.0; // Exact match
        }

        // Check tolerance if enabled
        if (tolerancePercent != null && tolerancePercent.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal absStatement = statementAmount.abs();

            if (absStatement.compareTo(BigDecimal.ZERO) == 0) {
                return 0.0;
            }

            BigDecimal diff = statementAmount.subtract(voucherAmount).abs();
            BigDecimal percentDiff = diff.multiply(new BigDecimal("100"))
                    .divide(absStatement, 4, RoundingMode.HALF_UP);

            if (percentDiff.compareTo(tolerancePercent) <= 0) {
                // Score decreases linearly with difference
                return 1.0 - percentDiff.divide(tolerancePercent, 4, RoundingMode.HALF_UP).doubleValue();
            }
        }

        return 0.0; // No match
    }

    /**
     * Calculate reference similarity score using Levenshtein distance.
     *
     * @param statementRef statement reference
     * @param voucherRef voucher reference
     * @return score 0.0 to 1.0
     */
    private double calculateReferenceScore(String statementRef, String voucherRef) {
        if (statementRef == null || statementRef.isBlank()) {
            return voucherRef == null || voucherRef.isBlank() ? 0.5 : 0.0;
        }
        if (voucherRef == null || voucherRef.isBlank()) {
            return 0.0;
        }

        // Normalize strings
        String ref1 = statementRef.trim().toLowerCase();
        String ref2 = voucherRef.trim().toLowerCase();

        if (ref1.equals(ref2)) {
            return 1.0;
        }

        int maxLength = Math.max(ref1.length(), ref2.length());
        if (maxLength == 0) {
            return 1.0;
        }

        int distance = levenshteinDistance.apply(ref1, ref2);
        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Build human-readable match reason.
     */
    private String buildMatchReason(
            LocalDate statementDate,
            LocalDate voucherDate,
            BigDecimal statementDebit,
            BigDecimal statementCredit,
            BigDecimal voucherDebit,
            BigDecimal voucherCredit,
            String statementRef,
            String voucherRef) {

        List<String> reasons = new ArrayList<>();

        // Date reason
        if (statementDate != null && voucherDate != null) {
            long daysDiff = ChronoUnit.DAYS.between(statementDate, voucherDate);
            if (daysDiff == 0) {
                reasons.add("Exact date match");
            } else {
                reasons.add("Date difference: " + Math.abs(daysDiff) + " day(s)");
            }
        }

        // Amount reason
        BigDecimal stmtAmount = (statementCredit != null ? statementCredit : BigDecimal.ZERO)
                .subtract(statementDebit != null ? statementDebit : BigDecimal.ZERO);
        BigDecimal vchAmount = (voucherDebit != null ? voucherDebit : BigDecimal.ZERO)
                .subtract(voucherCredit != null ? voucherCredit : BigDecimal.ZERO);

        if (stmtAmount.compareTo(vchAmount) == 0) {
            reasons.add("Exact amount match");
        } else {
            BigDecimal diff = stmtAmount.subtract(vchAmount).abs();
            reasons.add("Amount difference: " + diff);
        }

        // Reference reason
        double refScore = calculateReferenceScore(statementRef, voucherRef);
        if (refScore >= 0.9) {
            reasons.add("Reference match: high similarity");
        } else if (refScore >= 0.5) {
            reasons.add("Reference match: partial similarity (" + String.format("%.0f%%", refScore * 100) + ")");
        } else if (refScore > 0) {
            reasons.add("Reference match: low similarity (" + String.format("%.0f%%", refScore * 100) + ")");
        }

        return String.join("; ", reasons);
    }

    /**
     * Create match suggestion DTO.
     */
    private MatchSuggestionDTO createMatchSuggestion(
            BankStatementLine statementLine,
            LedgerTransactionDTO ledgerTx,
            double confidence,
            LocalDate statementDate,
            LocalDate voucherDate,
            BigDecimal statementDebit,
            BigDecimal statementCredit,
            BigDecimal voucherDebit,
            BigDecimal voucherCredit,
            String statementRef,
            String voucherRef) {

        MatchSuggestionDTO suggestion = new MatchSuggestionDTO();

        // Convert statement line to DTO
        BankStatementLineDTO lineDTO = convertToDTO(statementLine);
        suggestion.setStatementLine(lineDTO);
        suggestion.setLedgerTransaction(ledgerTx);
        suggestion.setConfidence(confidence);
        suggestion.setMatchReason(buildMatchReason(
                statementDate, voucherDate,
                statementDebit, statementCredit,
                voucherDebit, voucherCredit,
                statementRef, voucherRef));

        return suggestion;
    }

    /**
     * Apply a match to a statement line.
     */
    private void applyMatch(BankStatementLine statementLine, MatchSuggestionDTO match) {
        statementLine.setMatchStatus(MatchStatus.MATCHED);
        statementLine.setMatchedVoucherId(match.getLedgerTransaction().getVoucherId());
        statementLine.setMatchedAt(Instant.now());
        statementLine.setMatchConfidence(BigDecimal.valueOf(match.getConfidence()));
        statementLine.setMatchReason(match.getMatchReason());
        // Note: matchedById should be set by the caller with current user ID
        statementLineRepository.save(statementLine);

        log.debug("Applied match: statement line {} -> voucher {}",
                statementLine.getId(), match.getLedgerTransaction().getVoucherId());
    }

    /**
     * Convert BankStatementLine entity to DTO.
     */
    private BankStatementLineDTO convertToDTO(BankStatementLine line) {
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
        return dto;
    }
}
