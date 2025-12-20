package com.accounting.controller.admin;

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

import com.accounting.report.ReportService;
import com.accounting.report.ReportService.Format;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportController {

    private final ReportService reportService;
    private final AuditService auditService;

    public ReportController(ReportService reportService, AuditService auditService) {
        this.reportService = reportService;
        this.auditService = auditService;
    }

    /**
     * Sample export to validate AC#5 footer rendering path (PDF/XLSX).
     */
    @PreAuthorize("hasAnyAuthority('admin','ADMIN','ROLE_ADMIN','chief_accountant','CHIEF_ACCOUNTANT','ROLE_CHIEF_ACCOUNTANT')")
    @GetMapping("/company/profile")
    public ResponseEntity<byte[]> exportCompanyProfile(@RequestParam(defaultValue = "pdf") String format) {
        Format fmt = "xlsx".equalsIgnoreCase(format) ? Format.XLSX : Format.PDF;
        byte[] bytes = reportService.generateCompanyProfileReport(fmt);

        // Audit export (best-effort)
        try {
            Long companyId = CompanyContext.getCompanyId();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long userId = (auth != null && auth.getPrincipal() != null)
                    ? Long.parseLong(auth.getPrincipal().toString())
                    : null;
            auditService.logReportExport(companyId, userId, fmt.name(), null);
        } catch (Exception ignored) {
        }

        HttpHeaders headers = new HttpHeaders();
        if (fmt == Format.PDF) {
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=company-profile.pdf");
        } else {
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=company-profile.xlsx");
        }
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
