package com.accounting.controller.audit;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.controller.audit.dto.AuditCheckpointResponse;
import com.accounting.controller.audit.dto.VerifyDailyChainResponse;
import com.accounting.entity.audit.AuditChainCheckpoint;
import com.accounting.repository.audit.AuditChainCheckpointRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.audit.AuditHashChainService;
import com.accounting.service.audit.dto.AuditChainVerificationResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/audit/integrity")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit Integrity", description = "Manual audit chain verification and checkpoint operations")
public class AuditIntegrityController {

    private final AuditHashChainService auditHashChainService;
    private final AuditChainCheckpointRepository checkpointRepository;

    @GetMapping("/verify/daily")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Verify daily chain", description = "Verify the audit hash chain for a specific date")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verification completed"),
        @ApiResponse(responseCode = "400", description = "Invalid date or missing company context"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<VerifyDailyChainResponse> verifyDaily(
        @Parameter(description = "Date to verify (ISO-8601)", required = true)
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long companyId = CompanyContext.getCompanyId();
        log.info("Verifying daily chain for company {} on date {}", companyId, date);

        AuditChainVerificationResult result = auditHashChainService.verifyDailyChain(companyId, date);
        return ResponseEntity.ok(mapToResponse(result));
    }

    @GetMapping("/checkpoint")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Get checkpoint", description = "Get the audit checkpoint for a specific date")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Checkpoint found"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Checkpoint not found")
    })
    public ResponseEntity<AuditCheckpointResponse> getCheckpoint(
        @Parameter(description = "Date of checkpoint (ISO-8601)", required = true)
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long companyId = CompanyContext.getCompanyId();
        log.debug("Getting checkpoint for company {} on date {}", companyId, date);

        AuditChainCheckpoint checkpoint = checkpointRepository
            .findByCompanyIdAndEventDateUtc(companyId, date)
            .orElseThrow(() -> new EntityNotFoundException(
                "Checkpoint not found for date: " + date));

        return ResponseEntity.ok(mapToResponse(checkpoint));
    }

    @PostMapping("/checkpoint/create")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create checkpoint", description = "Recompute and store a daily checkpoint (Admin only)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Checkpoint created"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role"),
        @ApiResponse(responseCode = "400", description = "Invalid date")
    })
    public ResponseEntity<AuditCheckpointResponse> createCheckpoint(
        @Parameter(description = "Date for checkpoint (ISO-8601)", required = true)
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long companyId = CompanyContext.getCompanyId();
        log.info("Creating checkpoint for company {} on date {}", companyId, date);

        auditHashChainService.recomputeAndStoreDailyCheckpoint(companyId, date);

        AuditChainCheckpoint checkpoint = checkpointRepository
            .findByCompanyIdAndEventDateUtc(companyId, date)
            .orElseThrow(() -> new EntityNotFoundException(
                "Checkpoint creation failed for date: " + date));

        return ResponseEntity.ok(mapToResponse(checkpoint));
    }

    private VerifyDailyChainResponse mapToResponse(AuditChainVerificationResult result) {
        return new VerifyDailyChainResponse(
            result.getCompanyId(),
            result.getEventDateUtc(),
            result.isVerified(),
            result.getStatus(),
            result.getMismatchReason(),
            result.isCheckpointCreated()
        );
    }

    private AuditCheckpointResponse mapToResponse(AuditChainCheckpoint checkpoint) {
        return new AuditCheckpointResponse(
            checkpoint.getCompanyId(),
            checkpoint.getEventDateUtc(),
            checkpoint.getFirstSequence(),
            checkpoint.getLastSequence(),
            checkpoint.getRecordCount(),
            checkpoint.getMerkleRoot(),
            checkpoint.getChainHeadHash(),
            checkpoint.getChainTailHash(),
            checkpoint.getStatus(),
            checkpoint.getLastVerifiedAt()
        );
    }
}
