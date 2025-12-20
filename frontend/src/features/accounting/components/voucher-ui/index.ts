/**
 * Voucher UI Components
 *
 * A collection of premium, banking-inspired UI components
 * designed specifically for the voucher management feature.
 *
 * Design System Features:
 * - Trust-evoking blue/teal color palette
 * - Inter font for UI, JetBrains Mono for numbers
 * - Subtle shadows and depth
 * - Smooth micro-interactions
 * - Tabular numbers for financial data alignment
 */

// Status & Display
export {
  VoucherStatusBadge,
  VoucherStatusDot,
  type VoucherStatus,
  type VoucherStatusBadgeProps,
} from './VoucherStatusBadge'

// Amount & Currency
export {
  VoucherAmountCell,
  VoucherAmountInline,
  VoucherBalanceDisplay,
  type AmountVariant,
  type VoucherAmountCellProps,
} from './VoucherAmountCell'

// Loading States
export {
  VoucherSkeleton,
  VoucherTableRowSkeleton,
  VoucherTableSkeleton,
  VoucherCardSkeleton,
  VoucherCardsGridSkeleton,
  VoucherFormSectionSkeleton,
  VoucherLineGridSkeleton,
  VoucherPageHeaderSkeleton,
  VoucherFilterBarSkeleton,
  VoucherSummarySkeleton,
} from './VoucherSkeleton'

// Page Layout
export {
  VoucherPageHeader,
  VoucherSectionHeader,
  type VoucherQuickStat,
  type VoucherPageHeaderProps,
} from './VoucherPageHeader'

// Empty States
export {
  VoucherEmptyState,
  VoucherEmptyStateInline,
  type EmptyStateVariant,
  type VoucherEmptyStateProps,
} from './VoucherEmptyState'
