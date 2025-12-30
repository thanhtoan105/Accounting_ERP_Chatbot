// AR Aging Buckets for pie/bar chart
export interface AgingBucket {
  bucketKey: 'CURRENT' | 'DAYS_1_30' | 'DAYS_31_60' | 'DAYS_61_90' | 'DAYS_OVER_90'
  amount: number
  percent: number
}

export interface AgingBucketsDTO {
  buckets: AgingBucket[]
  totalOutstanding: number
  asOfDate: string // ISO date
}

// Monthly Revenue for line chart
export interface MonthlyData {
  monthLabel: string // e.g., "2024-01" or "Jan 2024"
  revenue: number
}

export interface MonthlyRevenueDTO {
  data: MonthlyData[]
  totalRevenue: number
}

// Cash Flow for stacked bar chart
export interface CashFlowData {
  monthLabel: string
  inflow: number
  outflow: number
  net: number
}

export interface CashFlowDTO {
  data: CashFlowData[]
  totalInflow: number
  totalOutflow: number
  netCashFlow: number
}

// Expense Breakdown for pie chart
export interface ExpenseCategory {
  categoryId: number
  categoryName: string
  amount: number
  percent: number
}

export interface ExpenseBreakdownDTO {
  categories: ExpenseCategory[]
  totalExpenses: number
  startDate: string // ISO date
  endDate: string // ISO date
}
