import { faker } from '@faker-js/faker';

/**
 * Period Factory - Creates accounting periods for testing
 */

export interface Period {
  id: string;
  year: number;
  month: number;
  status: 'Open' | 'Closed';
  startDate: string;
  endDate: string;
  closedAt?: string;
  closedBy?: string;
}

/**
 * Create an open period (current month by default)
 */
export const createOpenPeriod = (overrides: Partial<Period> = {}): Period => {
  const now = new Date();
  const year = overrides.year || now.getFullYear();
  const month = overrides.month || now.getMonth() + 1;

  const startDate = new Date(year, month - 1, 1);
  const endDate = new Date(year, month, 0); // Last day of month

  return {
    id: `period-${year}-${String(month).padStart(2, '0')}`,
    year,
    month,
    status: 'Open',
    startDate: startDate.toISOString().split('T')[0],
    endDate: endDate.toISOString().split('T')[0],
    ...overrides,
  };
};

/**
 * Create a closed period
 */
export const createClosedPeriod = (overrides: Partial<Period> = {}): Period => {
  const now = new Date();
  const year = overrides.year || now.getFullYear();
  const month = overrides.month || Math.max(1, now.getMonth());

  const startDate = new Date(year, month - 1, 1);
  const endDate = new Date(year, month, 0);

  return {
    id: `period-${year}-${String(month).padStart(2, '0')}`,
    year,
    month,
    status: 'Closed',
    startDate: startDate.toISOString().split('T')[0],
    endDate: endDate.toISOString().split('T')[0],
    closedAt: new Date().toISOString(),
    closedBy: faker.string.uuid(),
    ...overrides,
  };
};

/**
 * Create multiple periods (full fiscal year)
 */
export const createFiscalYear = (year: number, startMonth: number = 1): Period[] => {
  return Array.from({ length: 12 }, (_, i) => {
    const month = startMonth + i;
    const adjustedMonth = ((month - 1) % 12) + 1;
    const adjustedYear = year + Math.floor((month - 1) / 12);

    return createOpenPeriod({
      year: adjustedYear,
      month: adjustedMonth,
    });
  });
};

/**
 * Create periods with mixed open/closed status (e.g., current and previous months)
 */
export const createPeriodsWithClosedHistory = (): Period[] => {
  const now = new Date();
  const periods: Period[] = [];

  // Previous 3 months: closed
  for (let i = 3; i >= 1; i--) {
    const date = new Date(now.getFullYear(), now.getMonth() - i, 1);
    periods.push(
      createClosedPeriod({
        year: date.getFullYear(),
        month: date.getMonth() + 1,
      })
    );
  }

  // Current month: open
  periods.push(
    createOpenPeriod({
      year: now.getFullYear(),
      month: now.getMonth() + 1,
    })
  );

  // Next 2 months: open
  for (let i = 1; i <= 2; i++) {
    const date = new Date(now.getFullYear(), now.getMonth() + i, 1);
    periods.push(
      createOpenPeriod({
        year: date.getFullYear(),
        month: date.getMonth() + 1,
      })
    );
  }

  return periods;
};

/**
 * Get date range for a period
 */
export const getPeriodDateRange = (year: number, month: number): { start: Date; end: Date } => {
  const start = new Date(year, month - 1, 1);
  const end = new Date(year, month, 0);
  return { start, end };
};

/**
 * Check if date falls within period
 */
export const isDateInPeriod = (date: Date, period: Period): boolean => {
  const periodStart = new Date(period.startDate);
  const periodEnd = new Date(period.endDate);
  return date >= periodStart && date <= periodEnd;
};
