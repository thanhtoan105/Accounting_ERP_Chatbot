package com.accounting.controller;

import com.accounting.dto.BalanceTooltipDTO;
import com.accounting.dto.BankAccountCreateRequest;
import com.accounting.dto.BankAccountDTO;
import com.accounting.dto.BankAccountUpdateRequest;
import com.accounting.service.AuditService;
import com.accounting.service.BankAccountService;
import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportSummary;
import com.accounting.imports.handler.impl.BankAccountImportHandler;
import com.accounting.security.CompanyContext;

/**
 * REST controller for BankAccount operations.
 * All authenticated users can view bank accounts; edit requires
 * admin/accountant roles.
 */
@RestController
@RequestMapping("/api/v1/bank-accounts")
public class BankAccountController {

    private final BankAccountService bankAccountService;
    private final AuditService auditService;
    private final BankAccountImportHandler importHandler;

    public BankAccountController(
            BankAccountService bankAccountService,
            AuditService auditService,
            BankAccountImportHandler importHandler) {
        this.bankAccountService = bankAccountService;
        this.auditService = auditService;
        this.importHandler = importHandler;
    }

    /**
     * Get bank accounts with pagination, sorting, and filters.
     * Requires authenticated user (any role).
     *
     * @param page   page number (0-based, default: 0)
     * @param size   page size (default: 20)
     * @param sort   sort field and direction (e.g., "accountNumber,asc" or
     *               "bankName,desc", default: "bankName,asc")
     * @param type   filter by account type (optional: CASH or BANK)
     * @param status filter by active status (optional: true = active, false =
     *               inactive)
     * @param search search term for account number or bank name (optional, supports
     *               unaccented Vietnamese matching)
     * @return paginated list of bank accounts
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getBankAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "bankName,asc") String sort,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean status,
            @RequestParam(required = false) String search) {

        // Parse sort parameter
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        Sort.Direction direction = sortParts.length > 1 && "desc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        Page<BankAccountDTO> bankAccounts = bankAccountService.findAll(pageable, type, status, search);

        Map<String, Object> body = new HashMap<>();
        body.put("data", bankAccounts.getContent());
        body.put("total", bankAccounts.getTotalElements());
        body.put("page", bankAccounts.getNumber());
        body.put("size", bankAccounts.getSize());
        body.put("totalPages", bankAccounts.getTotalPages());
        return ResponseEntity.ok(body);
    }

    /**
     * Get single bank account by ID.
     * Requires authenticated user (any role).
     *
     * @param id bank account ID
     * @return bank account details
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getBankAccountById(@PathVariable Long id) {
        return bankAccountService
                .getBankAccountById(id)
                .map(
                        bankAccount -> {
                            Map<String, Object> body = new HashMap<>();
                            body.put("data", bankAccount);
                            return ResponseEntity.ok(body);
                        })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get balance tooltip data (current and prior period balances).
     * Requires authenticated user (any role).
     *
     * @param id bank account ID
     * @return balance tooltip data
     */
    @GetMapping("/{id}/balance-tooltip")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getBalanceTooltip(@PathVariable Long id) {
        BalanceTooltipDTO tooltip = bankAccountService.getBalanceTooltip(id);
        Map<String, Object> body = new HashMap<>();
        body.put("data", tooltip);
        return ResponseEntity.ok(body);
    }

    /**
     * Create a new bank account.
     * Requires admin or accountant role.
     *
     * @param request create request with bank account details
     * @return created bank account
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    public ResponseEntity<Map<String, Object>> createBankAccount(
            @Valid @RequestBody BankAccountCreateRequest request) {
        try {
            BankAccountDTO bankAccount = bankAccountService.create(request);
            Map<String, Object> body = new HashMap<>();
            body.put("data", bankAccount);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                Map<String, Object> body = new HashMap<>();
                body.put("error", e.getReason());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }
            throw e;
        }
    }

    /**
     * Update an existing bank account.
     * Requires admin or accountant role.
     *
     * @param id      bank account ID
     * @param request update request with bank account details
     * @return updated bank account
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    public ResponseEntity<Map<String, Object>> updateBankAccount(
            @PathVariable Long id, @Valid @RequestBody BankAccountUpdateRequest request) {
        try {
            BankAccountDTO bankAccount = bankAccountService.update(id, request);
            Map<String, Object> body = new HashMap<>();
            body.put("data", bankAccount);
            return ResponseEntity.ok(body);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                Map<String, Object> body = new HashMap<>();
                body.put("error", e.getReason());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }
            throw e;
        }
    }

    /**
     * Delete a bank account (hard delete).
     * Blocks deletion if bank account is referenced by vouchers, periods, or
     * reconciliations.
     * Requires admin or accountant role.
     *
     * @param id bank account ID
     * @return no content or conflict if has references
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    public ResponseEntity<Map<String, Object>> deleteBankAccount(@PathVariable Long id) {
        try {
            bankAccountService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (org.springframework.web.server.ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                Map<String, Object> body = new HashMap<>();
                body.put("error", e.getReason());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }
            throw e;
        }
    }

    /**
     * Activate a bank account (set active=true).
     * Requires admin or accountant role.
     *
     * @param id bank account ID
     * @return no content
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    public ResponseEntity<Void> activateBankAccount(@PathVariable Long id) {
        bankAccountService.activate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivate a bank account (set active=false).
     * Requires admin or accountant role.
     *
     * @param id bank account ID
     * @return no content
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    public ResponseEntity<Void> deactivateBankAccount(@PathVariable Long id) {
        bankAccountService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Export bank accounts to Excel or CSV format.
     * Requires authenticated user (any role).
     *
     * @param format  export format ("xlsx" or "csv", default: "xlsx")
     * @param type    filter by account type (optional)
     * @param status  filter by active status (optional)
     * @param request HTTP request for audit logging
     * @return file download
     */
    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> exportBankAccounts(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean status,
            HttpServletRequest request) {
        try {
            // Get all bank accounts matching filters (no pagination for export)
            Page<BankAccountDTO> bankAccounts = bankAccountService.findAll(
                    PageRequest.of(0, Integer.MAX_VALUE), type, status, null);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            if ("csv".equalsIgnoreCase(format)) {
                // Export as CSV
                try (PrintWriter writer = new PrintWriter(outputStream)) {
                    // Write header with filter info
                    writer.println("# Filters: type=" + (type != null ? type : "ALL") + ", status="
                            + (status != null ? status : "ALL"));
                    writer.println(
                            "Account Number,Bank Name,Branch,Type,Opening Balance,GL Account Code,Opening Balance Locked,Last Reconciled Date,Last Reconciled Balance,Active,Created At,Updated At");

                    // Write data
                    for (BankAccountDTO account : bankAccounts.getContent()) {
                        writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                                escapeCsv(account.getAccountNumber()),
                                escapeCsv(account.getBankName()),
                                escapeCsv(account.getBranch() != null ? account.getBranch() : ""),
                                account.getType().name(),
                                account.getOpeningBalance().toString(),
                                escapeCsv(account.getGlAccountCode() != null ? account.getGlAccountCode() : ""),
                                account.getOpeningBalanceLocked() != null ? account.getOpeningBalanceLocked().toString()
                                        : "false",
                                account.getLastReconciledDate() != null ? account.getLastReconciledDate().toString()
                                        : "",
                                account.getLastReconciledBalance() != null
                                        ? account.getLastReconciledBalance().toString()
                                        : "",
                                account.getActive() ? "Yes" : "No",
                                account.getCreatedAt() != null ? account.getCreatedAt().toString() : "",
                                account.getUpdatedAt() != null ? account.getUpdatedAt().toString() : "");
                    }
                }
            } else {
                // Export as Excel (XLSX)
                try (Workbook workbook = new XSSFWorkbook()) {
                    Sheet sheet = workbook.createSheet("Bank Accounts");

                    // Create header row
                    Row headerRow = sheet.createRow(0);
                    String[] headers = { "Account Number", "Bank Name", "Branch", "Type", "Opening Balance",
                            "GL Account Code", "Opening Balance Locked", "Last Reconciled Date",
                            "Last Reconciled Balance",
                            "Active", "Created At", "Updated At" };
                    for (int i = 0; i < headers.length; i++) {
                        Cell cell = headerRow.createCell(i);
                        cell.setCellValue(headers[i]);
                    }

                    // Create data rows
                    int rowNum = 1;
                    for (BankAccountDTO account : bankAccounts.getContent()) {
                        Row row = sheet.createRow(rowNum++);
                        row.createCell(0).setCellValue(account.getAccountNumber());
                        row.createCell(1).setCellValue(account.getBankName());
                        row.createCell(2).setCellValue(account.getBranch() != null ? account.getBranch() : "");
                        row.createCell(3).setCellValue(account.getType().name());
                        row.createCell(4).setCellValue(account.getOpeningBalance().doubleValue());
                        row.createCell(5)
                                .setCellValue(account.getGlAccountCode() != null ? account.getGlAccountCode() : "");
                        row.createCell(6)
                                .setCellValue(account.getOpeningBalanceLocked() != null
                                        ? account.getOpeningBalanceLocked().toString()
                                        : "false");
                        row.createCell(7)
                                .setCellValue(account.getLastReconciledDate() != null
                                        ? account.getLastReconciledDate().toString()
                                        : "");
                        row.createCell(8)
                                .setCellValue(account.getLastReconciledBalance() != null
                                        ? account.getLastReconciledBalance().doubleValue()
                                        : 0);
                        row.createCell(9).setCellValue(account.getActive() ? "Yes" : "No");
                        row.createCell(10)
                                .setCellValue(account.getCreatedAt() != null ? account.getCreatedAt().toString() : "");
                        row.createCell(11)
                                .setCellValue(account.getUpdatedAt() != null ? account.getUpdatedAt().toString() : "");
                    }

                    // Auto-size columns
                    for (int i = 0; i < headers.length; i++) {
                        sheet.autoSizeColumn(i);
                    }

                    workbook.write(outputStream);
                }
            }

            String contentType = "xlsx".equalsIgnoreCase(format)
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    : "text/csv";
            String extension = "csv".equalsIgnoreCase(format) ? "csv" : "xlsx";
            String filename = "bank_accounts_export_" + System.currentTimeMillis() + "." + extension;

            // Get current user ID for audit logging
            Long currentUserId = getCurrentUserId();

            // Log export action
            auditService.logBankAccountExport(
                    bankAccounts.getContent().size(),
                    format.toUpperCase(),
                    currentUserId,
                    request);

            ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to export bank accounts: " + e.getMessage());
        }
    }

    /**
     * Import bank accounts from CSV/Excel file.
     * Atomic batch processing - all or nothing.
     * Requires admin or chief accountant role.
     *
     * @param file uploaded file (CSV or XLSX)
     * @return import summary with success/error counts
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
    public ResponseEntity<Map<String, Object>> importBankAccounts(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = getCurrentUserId();
        String filename = file.getOriginalFilename();

        ImportContext context = new ImportContext(
                companyId != null ? companyId : 0L,
                userId != null ? userId : 0L,
                filename,
                java.time.Instant.now(),
                "en",
                java.util.UUID.randomUUID(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        ImportSummary summary = importHandler.handle(file, context);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("successCount", summary.getSuccessCount());
        body.put("message", "Successfully imported " + summary.getSuccessCount() + " bank account(s)");
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * Download import template for bank accounts.
     * Requires authenticated user.
     *
     * @return CSV template file
     */
    @GetMapping("/import/template")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadImportTemplate() {
        String template = "account_number,bank_name,branch,account_type,opening_balance,gl_account_code,active\n"
                + "ACC-001,Vietcombank,Hanoi Branch,BANK,1000000,1121,true\n"
                + "ACC-002,Cash Drawer,Main Office,CASH,500000,1111,true";

        ByteArrayResource resource = new ByteArrayResource(template.getBytes());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bank_accounts_import_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(resource);
    }

    /**
     * Escape CSV field value (wrap in quotes if contains comma, quote, or newline).
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Get current user ID from security context.
     * Returns null if authentication is not available.
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        try {
            return Long.parseLong(authentication.getPrincipal().toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
