/**
 * AR Aging Helper Functions
 * 
 * Pure functions for AR aging calculations and validations
 * These can be used in unit tests, integration tests, and E2E tests
 */

export interface DateRange {
  start: Date;
  end: Date;
}

/**
 * Calculate days overdue based on due date and as-of date
 * Returns positive number if overdue, 0 if current, negative if not yet due
 */
export function calculateDaysOverdue(dueDate: Date, asOfDate: Date): number {
  const diffTime = asOfDate.getTime() - dueDate.getTime();
  const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
  return Math.max(0, diffDays);
}

/**
 * Assign aging bucket based on days overdue
 * Returns bucket key: 'CURRENT' | 'DAYS_1_30' | 'DAYS_31_60' | 'DAYS_61_90' | 'DAYS_OVER_90'
 */
export function assignAgingBucket(daysOverdue: number): string {
  if (daysOverdue === 0) {
    return 'CURRENT';
  } else if (daysOverdue >= 1 && daysOverdue <= 30) {
    return 'DAYS_1_30';
  } else if (daysOverdue >= 31 && daysOverdue <= 60) {
    return 'DAYS_31_60';
  } else if (daysOverdue >= 61 && daysOverdue <= 90) {
    return 'DAYS_61_90';
  } else {
    return 'DAYS_OVER_90';
  }
}

/**
 * Calculate outstanding balance (totalAmount - amountPaid)
 */
export function calculateOutstandingBalance(totalAmount: number, amountPaid: number): number {
  return Math.max(0, totalAmount - amountPaid);
}

/**
 * Validate aging bucket structure
 * Ensures all required fields are present and amounts are non-negative
 */
export function validateAgingBuckets(buckets: {
  current: number;
  days1To30: number;
  days31To60: number;
  days61To90: number;
  daysOver90: number;
  total: number;
}): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  // Check all fields exist
  if (typeof buckets.current !== 'number') errors.push('current is missing or invalid');
  if (typeof buckets.days1To30 !== 'number') errors.push('days1To30 is missing or invalid');
  if (typeof buckets.days31To60 !== 'number') errors.push('days31To60 is missing or invalid');
  if (typeof buckets.days61To90 !== 'number') errors.push('days61To90 is missing or invalid');
  if (typeof buckets.daysOver90 !== 'number') errors.push('daysOver90 is missing or invalid');
  if (typeof buckets.total !== 'number') errors.push('total is missing or invalid');

  // Check non-negative amounts
  if (buckets.current < 0) errors.push('current must be >= 0');
  if (buckets.days1To30 < 0) errors.push('days1To30 must be >= 0');
  if (buckets.days31To60 < 0) errors.push('days31To60 must be >= 0');
  if (buckets.days61To90 < 0) errors.push('days61To90 must be >= 0');
  if (buckets.daysOver90 < 0) errors.push('daysOver90 must be >= 0');

  // Check total matches sum
  const calculatedTotal = buckets.current + buckets.days1To30 + buckets.days31To60 + buckets.days61To90 + buckets.daysOver90;
  if (Math.abs(buckets.total - calculatedTotal) > 0.01) {
    errors.push(`total (${buckets.total}) does not match sum of buckets (${calculatedTotal})`);
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Format aging bucket key for API requests
 */
export function formatAgingBucketKey(bucket: string): string {
  const keyMap: Record<string, string> = {
    CURRENT: 'CURRENT',
    DAYS_1_30: 'DAYS_1_30',
    DAYS_31_60: 'DAYS_31_60',
    DAYS_61_90: 'DAYS_61_90',
    DAYS_OVER_90: 'DAYS_OVER_90',
  };

  return keyMap[bucket] || bucket;
}

/**
 * Calculate total overdue amount from aging buckets
 */
export function calculateTotalOverdue(buckets: {
  days1To30: number;
  days31To60: number;
  days61To90: number;
  daysOver90: number;
}): number {
  return buckets.days1To30 + buckets.days31To60 + buckets.days61To90 + buckets.daysOver90;
}

/**
 * Check if customer has overdue invoices
 */
export function hasOverdue(buckets: {
  days1To30: number;
  days31To60: number;
  days61To90: number;
  daysOver90: number;
}): boolean {
  return calculateTotalOverdue(buckets) > 0;
}

