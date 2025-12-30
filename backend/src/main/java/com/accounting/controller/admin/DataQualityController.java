package com.accounting.controller.admin;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.bi.DataQualityIssue;
import com.accounting.dto.bi.DataQualitySummary;
import com.accounting.security.CompanyContext;
import com.accounting.service.bi.DataQualityService;

@RestController
@RequestMapping("/api/v1/admin/data-quality")
public class DataQualityController {

    private final DataQualityService dataQualityService;

    public DataQualityController(DataQualityService dataQualityService) {
        this.dataQualityService = dataQualityService;
    }

    @GetMapping("/check")
    @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
    public ResponseEntity<Map<String, Object>> runChecks() {
        Long companyId = requireCompanyContext();
        List<DataQualityIssue> issues = dataQualityService.runAllChecks(companyId);
        return ResponseEntity.ok(Map.of("data", issues, "count", issues.size()));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Long companyId = requireCompanyContext();
        DataQualitySummary summary = dataQualityService.getSummary(companyId);
        return ResponseEntity.ok(Map.of("data", summary));
    }

    private Long requireCompanyContext() {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
        }
        return companyId;
    }
}
