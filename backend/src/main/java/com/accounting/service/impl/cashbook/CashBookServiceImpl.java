package com.accounting.service.impl.cashbook;

import com.accounting.dto.VoucherDTO;
import com.accounting.dto.cashbook.CashBookEntryDTO;
import com.accounting.dto.cashbook.CashBookFilterDTO;
import com.accounting.dto.cashbook.CashBookResponseDTO;
import com.accounting.dto.cashbook.CashBookSummaryDTO;
import com.accounting.dto.cashbook.CashBookSummaryDTO.AccountSummaryDTO;
import com.accounting.dto.cashbook.CashBookSummaryDTO.GrandTotalsDTO;
import com.accounting.entity.BankAccount;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Customer;
import com.accounting.entity.Supplier;
import com.accounting.entity.User;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.CashBookService;
import com.accounting.service.VoucherService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of CashBookService for Cash Book / Bank Book viewing.
 * Provides transaction ledger with running balances, multi-account summary, and
 * voucher drill-down.
 *
 * <p>
 * AC6.4-01: Per-account view with filters and running balance
 * <p>
 * AC6.4-03: Multi-account aggregated view
 * <p>
 * AC6.4-07: Performance target ≤2s for typical month (≤5k TX)
 */
@Service
@Transactional(readOnly = true)
public class CashBookServiceImpl implements CashBookService {

        private static final Logger logger = LoggerFactory.getLogger(CashBookServiceImpl.class);
        private static final long SLOW_QUERY_THRESHOLD_MS = 2000; // 2 seconds

        private final BankAccountRepository bankAccountRepository;
        private final ChartOfAccountsRepository chartOfAccountsRepository;
        private final VoucherRepository voucherRepository;
        private final VoucherLineRepository voucherLineRepository;
        private final VoucherService voucherService;
        private final UserRepository userRepository;
        private final CustomerRepository customerRepository;
        private final SupplierRepository supplierRepository;

        public CashBookServiceImpl(
                        BankAccountRepository bankAccountRepository,
                        ChartOfAccountsRepository chartOfAccountsRepository,
                        VoucherRepository voucherRepository,
                        VoucherLineRepository voucherLineRepository,
                        VoucherService voucherService,
                        UserRepository userRepository,
                        CustomerRepository customerRepository,
                        SupplierRepository supplierRepository) {
                this.bankAccountRepository = bankAccountRepository;
                this.chartOfAccountsRepository = chartOfAccountsRepository;
                this.voucherRepository = voucherRepository;
                this.voucherLineRepository = voucherLineRepository;
                this.voucherService = voucherService;
                this.userRepository = userRepository;
                this.customerRepository = customerRepository;
                this.supplierRepository = supplierRepository;
        }

        @Override
        public CashBookResponseDTO getCashBook(
                        Long bankAccountId,
                        LocalDate dateFrom,
                        LocalDate dateTo,
                        String transactionType,
                        String reference,
                        int page,
                        int size) {
                CashBookFilterDTO filter = new CashBookFilterDTO()
                                .withBankAccountId(bankAccountId)
                                .withDateRange(dateFrom, dateTo)
                                .withTransactionType(transactionType)
                                .withReference(reference)
                                .withPagination(page, size);
                return getCashBook(filter);
        }

        @Override
        public CashBookResponseDTO getCashBook(CashBookFilterDTO filter) {
                long startTime = System.currentTimeMillis();

                Long companyId = CompanyContext.getCompanyId();
                if (companyId == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
                }

                Long bankAccountId = filter.getBankAccountId();
                if (bankAccountId == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account ID is required");
                }

                // Get bank account and validate
                BankAccount bankAccount = bankAccountRepository
                                .findByCompanyIdAndId(companyId, bankAccountId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Bank account not found: " + bankAccountId));

                // Get GL account ID from bank account's GL account code
                if (bankAccount.getGlAccountCode() == null || bankAccount.getGlAccountCode().isBlank()) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Bank account has no GL account code configured: " + bankAccountId);
                }

                ChartOfAccount glAccount = chartOfAccountsRepository
                                .findByCompanyIdAndCode(companyId, bankAccount.getGlAccountCode())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "GL account not found for code: " + bankAccount.getGlAccountCode()));

                Long glAccountId = glAccount.getId();

                // Calculate opening balance
                BigDecimal openingBalance = calculateOpeningBalance(
                                companyId, bankAccount, glAccountId, filter.getDateFrom());

                // Query transactions
                List<CashBookEntryDTO> allTransactions = queryTransactions(
                                companyId, glAccountId, filter);

                // Count total for pagination
                int totalCount = allTransactions.size();

                // Apply pagination
                int fromIndex = filter.getPage() * filter.getSize();
                int toIndex = Math.min(fromIndex + filter.getSize(), totalCount);
                List<CashBookEntryDTO> pagedTransactions = fromIndex < totalCount
                                ? allTransactions.subList(fromIndex, toIndex)
                                : new ArrayList<>();

                // Calculate running balances for the page
                // Need to calculate cumulative balance up to the start of this page
                BigDecimal cumulativeBalance = openingBalance;
                for (int i = 0; i < fromIndex && i < allTransactions.size(); i++) {
                        CashBookEntryDTO tx = allTransactions.get(i);
                        cumulativeBalance = cumulativeBalance
                                        .add(tx.getDebit() != null ? tx.getDebit() : BigDecimal.ZERO)
                                        .subtract(tx.getCredit() != null ? tx.getCredit() : BigDecimal.ZERO);
                }

                // Set running balances for paged transactions
                for (CashBookEntryDTO tx : pagedTransactions) {
                        cumulativeBalance = cumulativeBalance
                                        .add(tx.getDebit() != null ? tx.getDebit() : BigDecimal.ZERO)
                                        .subtract(tx.getCredit() != null ? tx.getCredit() : BigDecimal.ZERO);
                        tx.setRunningBalance(cumulativeBalance);
                }

                // Calculate totals for the entire date range (not just the page)
                BigDecimal totalInflow = allTransactions.stream()
                                .map(tx -> tx.getDebit() != null ? tx.getDebit() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalOutflow = allTransactions.stream()
                                .map(tx -> tx.getCredit() != null ? tx.getCredit() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal closingBalance = openingBalance.add(totalInflow).subtract(totalOutflow);

                // Build response
                CashBookResponseDTO response = new CashBookResponseDTO();
                response.setBankAccountId(bankAccountId);
                response.setAccountNumber(bankAccount.getAccountNumber());
                response.setBankName(bankAccount.getBankName());
                response.setAccountType(bankAccount.getType().name());
                response.setGlAccountCode(bankAccount.getGlAccountCode());
                response.setOpeningBalance(openingBalance);
                response.setTotalInflow(totalInflow);
                response.setTotalOutflow(totalOutflow);
                response.setClosingBalance(closingBalance);
                response.setTransactions(pagedTransactions);
                response.setTotalCount(totalCount);
                response.setPage(filter.getPage());
                response.setSize(filter.getSize());

                // AC6.4-07: Log slow queries
                long duration = System.currentTimeMillis() - startTime;
                if (duration > SLOW_QUERY_THRESHOLD_MS) {
                        logger.warn(
                                        "Slow cash book query: {}ms for bankAccountId={}, filter={}",
                                        duration, bankAccountId, filter.toAuditString());
                } else {
                        logger.debug(
                                        "Cash book query completed: {}ms for bankAccountId={}",
                                        duration, bankAccountId);
                }

                return response;
        }

        @Override
        public CashBookSummaryDTO getCashBookSummary(
                        List<Long> accountIds,
                        LocalDate dateFrom,
                        LocalDate dateTo) {
                long startTime = System.currentTimeMillis();

                Long companyId = CompanyContext.getCompanyId();
                if (companyId == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
                }

                // Get bank accounts
                List<BankAccount> bankAccounts;
                if (accountIds != null && !accountIds.isEmpty()) {
                        bankAccounts = accountIds.stream()
                                        .map(id -> bankAccountRepository.findByCompanyIdAndId(companyId, id))
                                        .filter(Optional::isPresent)
                                        .map(Optional::get)
                                        .collect(Collectors.toList());
                } else {
                        // Get all active accounts
                        bankAccounts = bankAccountRepository.findByCompanyIdAndActive(companyId, true);
                }

                // Build summaries for each account
                List<AccountSummaryDTO> accountSummaries = new ArrayList<>();
                BigDecimal grandOpeningBalance = BigDecimal.ZERO;
                BigDecimal grandInflow = BigDecimal.ZERO;
                BigDecimal grandOutflow = BigDecimal.ZERO;
                int grandTransactionCount = 0;

                for (BankAccount bankAccount : bankAccounts) {
                        if (bankAccount.getGlAccountCode() == null || bankAccount.getGlAccountCode().isBlank()) {
                                logger.warn("Skipping bank account {} - no GL account code", bankAccount.getId());
                                continue;
                        }

                        Optional<ChartOfAccount> glAccountOpt = chartOfAccountsRepository
                                        .findByCompanyIdAndCode(companyId, bankAccount.getGlAccountCode());
                        if (glAccountOpt.isEmpty()) {
                                logger.warn("Skipping bank account {} - GL account not found: {}",
                                                bankAccount.getId(), bankAccount.getGlAccountCode());
                                continue;
                        }

                        Long glAccountId = glAccountOpt.get().getId();

                        // Get opening balance
                        BigDecimal openingBalance = calculateOpeningBalance(
                                        companyId, bankAccount, glAccountId, dateFrom);

                        // Query transactions for totals
                        CashBookFilterDTO filter = new CashBookFilterDTO()
                                        .withBankAccountId(bankAccount.getId())
                                        .withDateRange(dateFrom, dateTo);
                        List<CashBookEntryDTO> transactions = queryTransactions(companyId, glAccountId, filter);

                        BigDecimal totalInflow = transactions.stream()
                                        .map(tx -> tx.getDebit() != null ? tx.getDebit() : BigDecimal.ZERO)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal totalOutflow = transactions.stream()
                                        .map(tx -> tx.getCredit() != null ? tx.getCredit() : BigDecimal.ZERO)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal closingBalance = openingBalance.add(totalInflow).subtract(totalOutflow);

                        AccountSummaryDTO summary = new AccountSummaryDTO();
                        summary.setBankAccountId(bankAccount.getId());
                        summary.setAccountNumber(bankAccount.getAccountNumber());
                        summary.setBankName(bankAccount.getBankName());
                        summary.setAccountType(bankAccount.getType().name());
                        summary.setGlAccountCode(bankAccount.getGlAccountCode());
                        summary.setOpeningBalance(openingBalance);
                        summary.setTotalInflow(totalInflow);
                        summary.setTotalOutflow(totalOutflow);
                        summary.setClosingBalance(closingBalance);
                        summary.setTransactionCount(transactions.size());

                        accountSummaries.add(summary);

                        // Accumulate grand totals
                        grandOpeningBalance = grandOpeningBalance.add(openingBalance);
                        grandInflow = grandInflow.add(totalInflow);
                        grandOutflow = grandOutflow.add(totalOutflow);
                        grandTransactionCount += transactions.size();
                }

                // Sort summaries by account type, then by bank name
                accountSummaries.sort(Comparator
                                .comparing(AccountSummaryDTO::getAccountType)
                                .thenComparing(AccountSummaryDTO::getBankName));

                // Build grand totals
                GrandTotalsDTO grandTotals = new GrandTotalsDTO(
                                grandOpeningBalance,
                                grandInflow,
                                grandOutflow,
                                grandOpeningBalance.add(grandInflow).subtract(grandOutflow),
                                grandTransactionCount);

                // AC6.4-07: Log slow queries
                long duration = System.currentTimeMillis() - startTime;
                if (duration > SLOW_QUERY_THRESHOLD_MS) {
                        logger.warn(
                                        "Slow cash book summary query: {}ms for {} accounts, dateRange={} to {}",
                                        duration, accountSummaries.size(), dateFrom, dateTo);
                }

                return new CashBookSummaryDTO(accountSummaries, grandTotals);
        }

        @Override
        public VoucherDTO getVoucherDetail(Long bankAccountId, UUID voucherId) {
                Long companyId = CompanyContext.getCompanyId();
                if (companyId == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
                }

                // Validate bank account exists in company (for context)
                bankAccountRepository
                                .findByCompanyIdAndId(companyId, bankAccountId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Bank account not found: " + bankAccountId));

                // Get voucher detail using existing VoucherService
                return voucherService.getVoucherById(voucherId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));
        }

        @Override
        public long countTransactions(
                        Long bankAccountId,
                        LocalDate dateFrom,
                        LocalDate dateTo,
                        String transactionType) {
                Long companyId = CompanyContext.getCompanyId();
                if (companyId == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
                }

                BankAccount bankAccount = bankAccountRepository
                                .findByCompanyIdAndId(companyId, bankAccountId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Bank account not found: " + bankAccountId));

                if (bankAccount.getGlAccountCode() == null) {
                        return 0;
                }

                ChartOfAccount glAccount = chartOfAccountsRepository
                                .findByCompanyIdAndCode(companyId, bankAccount.getGlAccountCode())
                                .orElse(null);
                if (glAccount == null) {
                        return 0;
                }

                CashBookFilterDTO filter = new CashBookFilterDTO()
                                .withBankAccountId(bankAccountId)
                                .withDateRange(dateFrom, dateTo)
                                .withTransactionType(transactionType);

                return queryTransactions(companyId, glAccount.getId(), filter).size();
        }

        /**
         * Calculate opening balance for a bank account at a given date.
         * Opening balance = bank account's opening_balance + sum of (debit - credit)
         * for all posted transactions before the date.
         * 
         * <p>
         * Uses bank account ID filtering (MISA pattern for detailed tracking).
         */
        private BigDecimal calculateOpeningBalance(
                        Long companyId,
                        BankAccount bankAccount,
                        Long glAccountId,
                        LocalDate beforeDate) {
                BigDecimal accountOpeningBalance = bankAccount.getOpeningBalance() != null
                                ? bankAccount.getOpeningBalance()
                                : BigDecimal.ZERO;

                if (beforeDate == null) {
                        // No date filter - use only the account's opening balance
                        return accountOpeningBalance;
                }

                // Get historical voucher lines - filter by bank account ID (MISA pattern)
                List<VoucherLine> historicalLines = voucherLineRepository
                                .findByCompanyIdAndAccountIdAndBankAccountId(
                                                companyId, glAccountId, bankAccount.getId());

                BigDecimal historicalBalance = historicalLines.stream()
                                .filter(line -> {
                                        // Get voucher to check status and date
                                        Optional<Voucher> voucherOpt = voucherRepository.findById(line.getVoucherId());
                                        if (voucherOpt.isEmpty())
                                                return false;
                                        Voucher voucher = voucherOpt.get();
                                        return "posted".equals(voucher.getStatus())
                                                        && voucher.getVoucherDate().isBefore(beforeDate);
                                })
                                .map(line -> {
                                        BigDecimal debit = line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO;
                                        BigDecimal credit = line.getCredit() != null ? line.getCredit()
                                                        : BigDecimal.ZERO;
                                        return debit.subtract(credit);
                                })
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                return accountOpeningBalance.add(historicalBalance);
        }

        /**
         * Query transactions for a GL account with filters.
         * Returns all transactions sorted by date, then by voucher ID for same-date
         * ordering.
         * 
         * <p>
         * Uses bank account ID filtering when available (MISA pattern for detailed
         * tracking).
         */
        private List<CashBookEntryDTO> queryTransactions(
                        Long companyId,
                        Long glAccountId,
                        CashBookFilterDTO filter) {

                // Get voucher lines - filter by bank account if specified (MISA pattern)
                List<VoucherLine> lines;
                Long bankAccountId = filter.getBankAccountId();
                if (bankAccountId != null) {
                        // Use bank account filtering for detailed tracking
                        lines = voucherLineRepository
                                        .findByCompanyIdAndAccountIdAndBankAccountId(companyId, glAccountId,
                                                        bankAccountId);
                } else {
                        // Fallback to GL account only (for backward compatibility)
                        lines = voucherLineRepository
                                        .findByCompanyIdAndAccountId(companyId, glAccountId);
                }

                // Load all vouchers for efficient lookup
                List<UUID> voucherIds = lines.stream()
                                .map(VoucherLine::getVoucherId)
                                .distinct()
                                .collect(Collectors.toList());

                Map<UUID, Voucher> voucherMap = voucherIds.stream()
                                .map(id -> voucherRepository.findById(id))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(Collectors.toMap(Voucher::getId, v -> v));

                // Pre-load users for posted_by names
                Map<Long, String> userNameCache = new java.util.HashMap<>();

                // Pre-load customers and suppliers for AR/AP entity names
                Map<Long, String> customerNameCache = new java.util.HashMap<>();
                Map<Long, String> supplierNameCache = new java.util.HashMap<>();

                // Filter and transform to DTOs
                List<CashBookEntryDTO> entries = lines.stream()
                                .filter(line -> {
                                        Voucher voucher = voucherMap.get(line.getVoucherId());
                                        if (voucher == null)
                                                return false;

                                        // Only posted vouchers
                                        if (!"posted".equals(voucher.getStatus()))
                                                return false;

                                        // Date range filter
                                        LocalDate voucherDate = voucher.getVoucherDate();
                                        if (filter.getDateFrom() != null
                                                        && voucherDate.isBefore(filter.getDateFrom())) {
                                                return false;
                                        }
                                        if (filter.getDateTo() != null && voucherDate.isAfter(filter.getDateTo())) {
                                                return false;
                                        }

                                        // Transaction type filter
                                        String txType = filter.getTransactionType();
                                        if (txType != null && !"all".equals(txType)) {
                                                BigDecimal debit = line.getDebit() != null ? line.getDebit()
                                                                : BigDecimal.ZERO;
                                                BigDecimal credit = line.getCredit() != null ? line.getCredit()
                                                                : BigDecimal.ZERO;
                                                boolean isReceipt = debit.compareTo(BigDecimal.ZERO) > 0;
                                                boolean isPayment = credit.compareTo(BigDecimal.ZERO) > 0;

                                                if ("receipt".equals(txType) && !isReceipt)
                                                        return false;
                                                if ("payment".equals(txType) && !isPayment)
                                                        return false;
                                        }

                                        // Reference filter (search in voucher description or number)
                                        if (filter.getReference() != null && !filter.getReference().isBlank()) {
                                                String searchTerm = filter.getReference().toLowerCase();
                                                String voucherNumber = voucher.getVoucherNumber() != null
                                                                ? voucher.getVoucherNumber().toLowerCase()
                                                                : "";
                                                String description = voucher.getDescription() != null
                                                                ? voucher.getDescription().toLowerCase()
                                                                : "";
                                                if (!voucherNumber.contains(searchTerm)
                                                                && !description.contains(searchTerm)) {
                                                        return false;
                                                }
                                        }

                                        return true;
                                })
                                .map(line -> {
                                        Voucher voucher = voucherMap.get(line.getVoucherId());
                                        BigDecimal debit = line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO;
                                        BigDecimal credit = line.getCredit() != null ? line.getCredit()
                                                        : BigDecimal.ZERO;

                                        CashBookEntryDTO entry = new CashBookEntryDTO();
                                        entry.setVoucherId(voucher.getId());
                                        entry.setVoucherNumber(voucher.getVoucherNumber());
                                        entry.setTransactionDate(voucher.getVoucherDate());
                                        entry.setDescription(line.getDescription() != null
                                                        ? line.getDescription()
                                                        : voucher.getDescription());
                                        entry.setDebit(debit.compareTo(BigDecimal.ZERO) > 0 ? debit : null);
                                        entry.setCredit(credit.compareTo(BigDecimal.ZERO) > 0 ? credit : null);

                                        // Determine transaction type
                                        entry.setTransactionType(
                                                        debit.compareTo(BigDecimal.ZERO) > 0 ? "receipt" : "payment");

                                        // Get posted by user name
                                        if (voucher.getPostedBy() != null) {
                                                String userName = userNameCache.computeIfAbsent(voucher.getPostedBy(),
                                                                userId -> userRepository.findById(userId)
                                                                                .map(User::getFullName)
                                                                                .orElse(null));
                                                entry.setPostedByName(userName);
                                        }
                                        entry.setPostedAt(voucher.getPostedAt());

                                        // Get customer/supplier info
                                        if (line.getCustomerId() != null) {
                                                entry.setCustomerId(line.getCustomerId());
                                                String customerName = customerNameCache.computeIfAbsent(
                                                                line.getCustomerId(),
                                                                customerId -> customerRepository
                                                                                .findByCompanyIdAndId(companyId,
                                                                                                customerId)
                                                                                .map(Customer::getName)
                                                                                .orElse(null));
                                                entry.setCustomerName(customerName);
                                        }
                                        if (line.getVendorId() != null) {
                                                entry.setSupplierId(line.getVendorId());
                                                String supplierName = supplierNameCache.computeIfAbsent(
                                                                line.getVendorId(),
                                                                supplierId -> supplierRepository
                                                                                .findByCompanyIdAndId(companyId,
                                                                                                supplierId)
                                                                                .map(Supplier::getName)
                                                                                .orElse(null));
                                                entry.setSupplierName(supplierName);
                                        }

                                        return entry;
                                })
                                .collect(Collectors.toList());

                // Sort by date, then by voucher ID for consistent ordering of same-date
                // transactions
                entries.sort(Comparator
                                .comparing(CashBookEntryDTO::getTransactionDate)
                                .thenComparing(CashBookEntryDTO::getVoucherId));

                return entries;
        }
}
