package com.accounting.controller.purchase;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.accounting.dto.ApprovalWorkflowDTO;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ApprovalWorkflowService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * REST controller for approval workflow operations.
 * Implements maker-checker pattern for purchase bills.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Approval Workflow", description = "Purchase bill approval workflow endpoints")
public class ApprovalWorkflowController {

  private final ApprovalWorkflowService approvalWorkflowService;

  public ApprovalWorkflowController(ApprovalWorkflowService approvalWorkflowService) {
    this.approvalWorkflowService = approvalWorkflowService;
  }

  @PostMapping("/purchase-bills/{id}/submit-for-approval")
  @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  @Operation(summary = "Submit purchase bill for approval")
  public ResponseEntity<ApprovalWorkflowDTO> submitForApproval(@PathVariable UUID id) {
    Long userId = SecurityUtils.getCurrentUserId();
    ApprovalWorkflowDTO workflow = approvalWorkflowService.submitForApproval(id, userId);
    return ResponseEntity.ok(workflow);
  }

  @PostMapping("/purchase-bills/{id}/approve")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO')")
  @Operation(summary = "Approve purchase bill")
  public ResponseEntity<ApprovalWorkflowDTO> approve(
      @PathVariable UUID id, @RequestBody(required = false) ApprovalRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();

    // Find workflow by bill ID
    List<ApprovalWorkflowDTO> workflows = approvalWorkflowService.getApprovalHistory(id);
    if (workflows.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    // Get the latest workflow
    ApprovalWorkflowDTO latestWorkflow = workflows.get(workflows.size() - 1);

    String reason = request != null ? request.getReason() : null;
    ApprovalWorkflowDTO approved =
        approvalWorkflowService.approve(latestWorkflow.getId(), userId, reason);

    return ResponseEntity.ok(approved);
  }

  @PostMapping("/purchase-bills/{id}/reject")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO')")
  @Operation(summary = "Reject purchase bill")
  public ResponseEntity<ApprovalWorkflowDTO> reject(
      @PathVariable UUID id, @Valid @RequestBody RejectionRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();

    // Find workflow by bill ID
    List<ApprovalWorkflowDTO> workflows = approvalWorkflowService.getApprovalHistory(id);
    if (workflows.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    // Get the latest workflow
    ApprovalWorkflowDTO latestWorkflow = workflows.get(workflows.size() - 1);

    ApprovalWorkflowDTO rejected =
        approvalWorkflowService.reject(latestWorkflow.getId(), userId, request.getReason());

    return ResponseEntity.ok(rejected);
  }

  @GetMapping("/approval-workflows/pending")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO')")
  @Operation(summary = "Get pending approval workflows")
  public ResponseEntity<List<ApprovalWorkflowDTO>> getPendingApprovals() {
    List<ApprovalWorkflowDTO> pending = approvalWorkflowService.getPendingApprovals();
    return ResponseEntity.ok(pending);
  }

  @GetMapping("/purchase-bills/{id}/approval-history")
  @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  @Operation(summary = "Get approval workflow history for a purchase bill")
  public ResponseEntity<List<ApprovalWorkflowDTO>> getApprovalHistory(@PathVariable UUID id) {
    List<ApprovalWorkflowDTO> history = approvalWorkflowService.getApprovalHistory(id);
    return ResponseEntity.ok(history);
  }

  @GetMapping("/approval-workflows/pending/count")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO')")
  @Operation(summary = "Get count of pending approvals")
  public ResponseEntity<Long> getPendingApprovalsCount() {
    long count = approvalWorkflowService.getPendingApprovalsCount();
    return ResponseEntity.ok(count);
  }

  /** Request DTO for approval with optional reason. */
  public static class ApprovalRequest {
    @Size(max = 1000)
    private String reason;

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }

  /** Request DTO for rejection with mandatory reason. */
  public static class RejectionRequest {
    @NotBlank(message = "Rejection reason is required")
    @Size(max = 1000, message = "Rejection reason must not exceed 1000 characters")
    private String reason;

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }
}
