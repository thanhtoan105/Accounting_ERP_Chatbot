import { faker } from '@faker-js/faker';
import type { PurchaseBill } from './purchase-bill-factory';

/**
 * Approval Workflow Factory
 * 
 * Creates test approval workflow data with sensible defaults and explicit overrides.
 * Uses faker for dynamic values that prevent collisions in parallel execution.
 * 
 * Pattern: Pure function with overrides → API seeding → UI validation
 */
export type ApprovalWorkflow = {
  id?: number;
  purchaseBillId: number;
  companyId?: number;
  createdById: number;
  approvedById?: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'AUTO_APPROVED';
  thresholdAmount?: number;
  isSensitive?: boolean;
  approvalReason?: string;
  rejectionReason?: string;
  createdAt?: string;
  updatedAt?: string;
  approvedAt?: string;
  rejectedAt?: string;
};

export type ApprovalThreshold = {
  id?: number;
  companyId?: number;
  thresholdAmount: number; // in VND
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export class ApprovalWorkflowFactory {
  private createdWorkflows: number[] = [];
  private createdThresholds: number[] = [];

  /**
   * Create an approval workflow object with sensible defaults
   * @param overrides - Partial approval workflow data to override defaults
   * @returns Approval workflow object ready for API seeding
   */
  createApprovalWorkflow(overrides: Partial<ApprovalWorkflow> = {}): ApprovalWorkflow {
    return {
      id: faker.number.int({ min: 1, max: 999999 }),
      purchaseBillId: overrides.purchaseBillId ?? faker.number.int({ min: 1, max: 999999 }),
      companyId: overrides.companyId ?? faker.number.int({ min: 1, max: 100 }),
      createdById: overrides.createdById ?? faker.number.int({ min: 1, max: 1000 }),
      approvedById: overrides.approvedById,
      status: overrides.status ?? 'PENDING',
      thresholdAmount: overrides.thresholdAmount ?? 20_000_000, // 20M VND default
      isSensitive: overrides.isSensitive ?? false,
      approvalReason: overrides.approvalReason,
      rejectionReason: overrides.rejectionReason,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      approvedAt: overrides.approvedAt,
      rejectedAt: overrides.rejectedAt,
      ...overrides,
    };
  }

  /**
   * Create a pending approval workflow (convenience method)
   */
  createPendingWorkflow(overrides: Partial<ApprovalWorkflow> = {}): ApprovalWorkflow {
    return this.createApprovalWorkflow({ status: 'PENDING', ...overrides });
  }

  /**
   * Create an approved workflow (convenience method)
   */
  createApprovedWorkflow(overrides: Partial<ApprovalWorkflow> = {}): ApprovalWorkflow {
    return this.createApprovalWorkflow({
      status: 'APPROVED',
      approvedAt: new Date().toISOString(),
      ...overrides,
    });
  }

  /**
   * Create a rejected workflow (convenience method)
   */
  createRejectedWorkflow(overrides: Partial<ApprovalWorkflow> = {}): ApprovalWorkflow {
    return this.createApprovalWorkflow({
      status: 'REJECTED',
      rejectionReason: overrides.rejectionReason ?? faker.lorem.sentence(),
      rejectedAt: new Date().toISOString(),
      ...overrides,
    });
  }

  /**
   * Create an auto-approved workflow (convenience method)
   */
  createAutoApprovedWorkflow(overrides: Partial<ApprovalWorkflow> = {}): ApprovalWorkflow {
    return this.createApprovalWorkflow({
      status: 'AUTO_APPROVED',
      approvedAt: new Date().toISOString(),
      ...overrides,
    });
  }

  /**
   * Create an approval threshold configuration
   */
  createApprovalThreshold(overrides: Partial<ApprovalThreshold> = {}): ApprovalThreshold {
    return {
      id: faker.number.int({ min: 1, max: 999999 }),
      companyId: overrides.companyId ?? faker.number.int({ min: 1, max: 100 }),
      thresholdAmount: overrides.thresholdAmount ?? 20_000_000, // 20M VND default
      isActive: overrides.isActive ?? true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      ...overrides,
    };
  }

  /**
   * Check if a purchase bill requires approval based on threshold and sensitivity
   */
  requiresApproval(bill: PurchaseBill, threshold: number, isSensitive: boolean = false): boolean {
    return bill.totalAmount > threshold || isSensitive;
  }

  /**
   * Track a created workflow ID for cleanup
   */
  trackWorkflow(workflowId: number): void {
    this.createdWorkflows.push(workflowId);
  }

  /**
   * Track a created threshold ID for cleanup
   */
  trackThreshold(thresholdId: number): void {
    this.createdThresholds.push(thresholdId);
  }

  /**
   * Cleanup all tracked workflows and thresholds via API
   * Call this in fixture teardown for automatic cleanup
   */
  async cleanup(apiRequest?: (params: {
    method: 'DELETE';
    url: string;
  }) => Promise<void>): Promise<void> {
    if (!apiRequest) {
      console.warn('ApprovalWorkflowFactory.cleanup() called without apiRequest - skipping cleanup');
      return;
    }

    for (const workflowId of this.createdWorkflows) {
      try {
        await apiRequest({
          method: 'DELETE',
          url: `/api/v1/approval-workflows/${workflowId}`,
        });
      } catch (error) {
        console.warn(`Failed to cleanup workflow ${workflowId}:`, error);
      }
    }
    this.createdWorkflows = [];

    for (const thresholdId of this.createdThresholds) {
      try {
        await apiRequest({
          method: 'DELETE',
          url: `/api/v1/approval-thresholds/${thresholdId}`,
        });
      } catch (error) {
        console.warn(`Failed to cleanup threshold ${thresholdId}:`, error);
      }
    }
    this.createdThresholds = [];
  }

  /**
   * Get list of tracked workflow IDs
   */
  getTrackedWorkflows(): number[] {
    return [...this.createdWorkflows];
  }

  /**
   * Get list of tracked threshold IDs
   */
  getTrackedThresholds(): number[] {
    return [...this.createdThresholds];
  }
}

