/**
 * AR Aging Calculations Unit Tests
 * Priority: P2 (Pure business logic validation)
 * 
 * Tests pure functions for aging calculations without framework dependencies
 */

import { describe, it, expect } from 'vitest';
import {
  calculateDaysOverdue,
  assignAgingBucket,
  calculateOutstandingBalance,
  validateAgingBuckets,
  calculateTotalOverdue,
  hasOverdue,
} from '../support/helpers/ar-aging-helpers';

describe('AR Aging Calculations', () => {
  describe('calculateDaysOverdue', () => {
    it('[P2] should return 0 for current invoices', () => {
      const dueDate = new Date('2025-01-27');
      const asOfDate = new Date('2025-01-27');
      expect(calculateDaysOverdue(dueDate, asOfDate)).toBe(0);
    });

    it('[P2] should return positive number for overdue invoices', () => {
      const dueDate = new Date('2025-01-20');
      const asOfDate = new Date('2025-01-27');
      expect(calculateDaysOverdue(dueDate, asOfDate)).toBe(7);
    });

    it('[P2] should return 0 for future due dates', () => {
      const dueDate = new Date('2025-02-01');
      const asOfDate = new Date('2025-01-27');
      expect(calculateDaysOverdue(dueDate, asOfDate)).toBe(0);
    });

    it('[P2] should handle exactly 30 days overdue', () => {
      const dueDate = new Date('2024-12-28');
      const asOfDate = new Date('2025-01-27');
      expect(calculateDaysOverdue(dueDate, asOfDate)).toBe(30);
    });

    it('[P2] should handle exactly 60 days overdue', () => {
      const dueDate = new Date('2024-11-28');
      const asOfDate = new Date('2025-01-27');
      expect(calculateDaysOverdue(dueDate, asOfDate)).toBe(60);
    });

    it('[P2] should handle exactly 90 days overdue', () => {
      const dueDate = new Date('2024-10-29');
      const asOfDate = new Date('2025-01-27');
      expect(calculateDaysOverdue(dueDate, asOfDate)).toBe(90);
    });
  });

  describe('assignAgingBucket', () => {
    it('[P2] should assign CURRENT for 0 days overdue', () => {
      expect(assignAgingBucket(0)).toBe('CURRENT');
    });

    it('[P2] should assign DAYS_1_30 for 1-30 days overdue', () => {
      expect(assignAgingBucket(1)).toBe('DAYS_1_30');
      expect(assignAgingBucket(15)).toBe('DAYS_1_30');
      expect(assignAgingBucket(30)).toBe('DAYS_1_30');
    });

    it('[P2] should assign DAYS_31_60 for 31-60 days overdue', () => {
      expect(assignAgingBucket(31)).toBe('DAYS_31_60');
      expect(assignAgingBucket(45)).toBe('DAYS_31_60');
      expect(assignAgingBucket(60)).toBe('DAYS_31_60');
    });

    it('[P2] should assign DAYS_61_90 for 61-90 days overdue', () => {
      expect(assignAgingBucket(61)).toBe('DAYS_61_90');
      expect(assignAgingBucket(75)).toBe('DAYS_61_90');
      expect(assignAgingBucket(90)).toBe('DAYS_61_90');
    });

    it('[P2] should assign DAYS_OVER_90 for 91+ days overdue', () => {
      expect(assignAgingBucket(91)).toBe('DAYS_OVER_90');
      expect(assignAgingBucket(120)).toBe('DAYS_OVER_90');
      expect(assignAgingBucket(365)).toBe('DAYS_OVER_90');
    });
  });

  describe('calculateOutstandingBalance', () => {
    it('[P2] should calculate outstanding balance correctly', () => {
      expect(calculateOutstandingBalance(1000000, 300000)).toBe(700000);
      expect(calculateOutstandingBalance(1000000, 0)).toBe(1000000);
      expect(calculateOutstandingBalance(1000000, 1000000)).toBe(0);
    });

    it('[P2] should return 0 if amount paid exceeds total', () => {
      expect(calculateOutstandingBalance(1000000, 1500000)).toBe(0);
    });

    it('[P2] should handle zero amounts', () => {
      expect(calculateOutstandingBalance(0, 0)).toBe(0);
    });
  });

  describe('validateAgingBuckets', () => {
    it('[P2] should validate correct bucket structure', () => {
      const buckets = {
        current: 100000,
        days1To30: 200000,
        days31To60: 150000,
        days61To90: 100000,
        daysOver90: 50000,
        total: 600000,
      };

      const result = validateAgingBuckets(buckets);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('[P2] should detect missing fields', () => {
      const buckets = {
        current: 100000,
        days1To30: 200000,
        // Missing other fields
      } as any;

      const result = validateAgingBuckets(buckets);
      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    it('[P2] should detect negative amounts', () => {
      const buckets = {
        current: -100000,
        days1To30: 200000,
        days31To60: 150000,
        days61To90: 100000,
        daysOver90: 50000,
        total: 400000,
      };

      const result = validateAgingBuckets(buckets);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('current'))).toBe(true);
    });

    it('[P2] should detect total mismatch', () => {
      const buckets = {
        current: 100000,
        days1To30: 200000,
        days31To60: 150000,
        days61To90: 100000,
        daysOver90: 50000,
        total: 1000000, // Incorrect total
      };

      const result = validateAgingBuckets(buckets);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('total'))).toBe(true);
    });
  });

  describe('calculateTotalOverdue', () => {
    it('[P2] should sum all overdue buckets', () => {
      const buckets = {
        days1To30: 200000,
        days31To60: 150000,
        days61To90: 100000,
        daysOver90: 50000,
      };

      expect(calculateTotalOverdue(buckets)).toBe(500000);
    });

    it('[P2] should return 0 when no overdue', () => {
      const buckets = {
        days1To30: 0,
        days31To60: 0,
        days61To90: 0,
        daysOver90: 0,
      };

      expect(calculateTotalOverdue(buckets)).toBe(0);
    });
  });

  describe('hasOverdue', () => {
    it('[P2] should return true when overdue exists', () => {
      const buckets = {
        days1To30: 100000,
        days31To60: 0,
        days61To90: 0,
        daysOver90: 0,
      };

      expect(hasOverdue(buckets)).toBe(true);
    });

    it('[P2] should return false when no overdue', () => {
      const buckets = {
        days1To30: 0,
        days31To60: 0,
        days61To90: 0,
        daysOver90: 0,
      };

      expect(hasOverdue(buckets)).toBe(false);
    });
  });
});

