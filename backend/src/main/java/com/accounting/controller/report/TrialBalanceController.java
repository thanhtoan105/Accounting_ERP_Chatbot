package com.accounting.controller.report;

import com.accounting.dto.TrialBalanceResponseDTO;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.TrialBalanceService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for Trial Balance report (S06-DN) endpoints.
 */
@RestController
@RequestMapping("/api/v1/reports/trial-balance")
public class TrialBalanceController {

  private final TrialBalanceService trialBalanceService;
  private final AuditService auditService;

  public TrialBalanceController(
      TrialBalanceService trialBalanceService,
      AuditService auditService) {
    this.trialBalanceService = trialBalanceService;
    this.auditService = auditService;
  }

  /**
   * Get trial balance data for a specific period.
   *
   * @param periodId period ID (UUID)
   * @return trial balance response with account balances
   */
  @PreAuthorize("hasAnyAuthority('chief_accountant','CHIEF_ACCOUNTANT','ROLE_CHIEF_ACCOUNTANT','admin','ADMIN','ROLE_ADMIN')")
  @GetMapping
  public ResponseEntity<TrialBalanceResponseDTO> getTrialBalance(
      @RequestParam UUID periodId) {
    TrialBalanceResponseDTO data = trialBalanceService.getTrialBalanceData(periodId);
    return ResponseEntity.ok(data);
  }

  /**
   * Export trial balance report to Excel.
   *
   * @param periodId period ID (UUID)
   * @param format export format (default: xlsx)
   * @return Excel file as byte array
   */
  @PreAuthorize("hasAnyAuthority('chief_accountant','CHIEF_ACCOUNTANT','ROLE_CHIEF_ACCOUNTANT','admin','ADMIN','ROLE_ADMIN')")
  @GetMapping("/export")
  public ResponseEntity<byte[]> exportTrialBalance(
      @RequestParam UUID periodId,
      @RequestParam(defaultValue = "xlsx") String format,
      HttpServletRequest request) {
    
    byte[] bytes = trialBalanceService.exportToExcel(periodId);

    // Audit export (best-effort)
    try {
      Long companyId = CompanyContext.getCompanyId();
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      Long userId = (auth != null && auth.getPrincipal() != null)
          ? Long.parseLong(auth.getPrincipal().toString())
          : null;
      auditService.logReportExport(companyId, userId, "XLSX", request);
    } catch (Exception ignored) {
      // Best-effort audit logging
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    headers.set(HttpHeaders.CONTENT_DISPOSITION, 
        "attachment; filename=trial-balance-" + periodId.toString() + ".xlsx");
    return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
  }
}

