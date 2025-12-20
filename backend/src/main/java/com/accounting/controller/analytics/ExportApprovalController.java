package com.accounting.controller.analytics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.analytics.ExportApprovalDTO;
import com.accounting.dto.analytics.ExportRejectRequest;
import com.accounting.dto.analytics.LargeExportRequest;
import com.accounting.entity.analytics.ExportApprovalRequest;
import com.accounting.service.analytics.ExportApprovalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/analytics/exports")
@Tag(name = "Export Approval", description = "Large export approval workflow APIs")
public class ExportApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ExportApprovalController.class);

    private final ExportApprovalService exportApprovalService;

    public ExportApprovalController(ExportApprovalService exportApprovalService) {
        this.exportApprovalService = exportApprovalService;
    }

    @PostMapping("/request")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT')")
    @Operation(summary = "Request large export", description = "Request approval for large data export (>10k rows)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Export request created"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<?> requestExport(@Valid @RequestBody LargeExportRequest request) {
        if (!exportApprovalService.requiresApproval(request.rowCount())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "Approval not required",
                            "message", "Exports with less than " + exportApprovalService.getLargeExportThreshold()
                                    + " rows do not require approval"));
        }

        ExportApprovalRequest created =
                exportApprovalService.requestLargeExport(request.exportType(), request.rowCount(), request.queryParameters());

        log.info("Large export request created: id={}, rowCount={}", created.getId(), request.rowCount());

        return ResponseEntity.ok(Map.of(
                "id", created.getId(),
                "status", created.getStatus().name(),
                "message", "Export request submitted for approval",
                "rowCount", request.rowCount(),
                "exportType", request.exportType()));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO')")
    @Operation(summary = "Get pending exports", description = "List all pending export requests for approval")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of pending exports"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or CFO required")
    })
    public ResponseEntity<List<ExportApprovalDTO>> getPendingExports() {
        List<ExportApprovalDTO> pending = exportApprovalService.getPendingExports();
        return ResponseEntity.ok(pending);
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT')")
    @Operation(summary = "Get my export requests", description = "List current user's export requests")
    public ResponseEntity<List<ExportApprovalDTO>> getMyExportRequests() {
        List<ExportApprovalDTO> requests = exportApprovalService.getMyExportRequests();
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT')")
    @Operation(summary = "Get export request by ID", description = "Get details of a specific export request")
    public ResponseEntity<ExportApprovalDTO> getExportRequest(@PathVariable Long id) {
        try {
            ExportApprovalDTO request = exportApprovalService.getById(id);
            return ResponseEntity.ok(request);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO')")
    @Operation(summary = "Approve export request", description = "Approve a pending export request")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Export approved"),
        @ApiResponse(responseCode = "400", description = "Request cannot be approved"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or CFO required"),
        @ApiResponse(responseCode = "404", description = "Export request not found")
    })
    public ResponseEntity<?> approve(@PathVariable Long id) {
        try {
            ExportApprovalDTO approved = exportApprovalService.approve(id);
            log.info("Export request approved: id={}", id);
            return ResponseEntity.ok(approved);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO')")
    @Operation(summary = "Reject export request", description = "Reject a pending export request with reason")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Export rejected"),
        @ApiResponse(responseCode = "400", description = "Request cannot be rejected or missing reason"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or CFO required"),
        @ApiResponse(responseCode = "404", description = "Export request not found")
    })
    public ResponseEntity<?> reject(@PathVariable Long id, @Valid @RequestBody ExportRejectRequest request) {
        try {
            ExportApprovalDTO rejected = exportApprovalService.reject(id, request.reason());
            log.info("Export request rejected: id={}, reason={}", id, request.reason());
            return ResponseEntity.ok(rejected);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/download/{token}")
    @Operation(summary = "Download approved export", description = "Download an approved export using the time-limited token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Export file downloaded"),
        @ApiResponse(responseCode = "400", description = "Export not approved or link expired"),
        @ApiResponse(responseCode = "404", description = "Invalid download token")
    })
    public ResponseEntity<?> download(@PathVariable String token) {
        try {
            ExportApprovalRequest request = exportApprovalService.getByDownloadToken(token);
            if (request == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] exportData = exportApprovalService.downloadExport(token);

            String filename = String.format(
                    "export_%s_%s.xlsx",
                    request.getExportType().toLowerCase(),
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(exportData.length);

            log.info("Export downloaded: token={}, size={}", token.substring(0, 8) + "...", exportData.length);

            return new ResponseEntity<>(exportData, headers, HttpStatus.OK);

        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/threshold")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT')")
    @Operation(summary = "Get large export threshold", description = "Get the row count threshold requiring approval")
    public ResponseEntity<Map<String, Object>> getThreshold() {
        return ResponseEntity.ok(Map.of(
                "threshold", exportApprovalService.getLargeExportThreshold(),
                "message", "Exports exceeding this row count require manager approval"));
    }
}
