import type { ChartOfAccount } from '@/types/chartOfAccount'
import type { VoucherTypeOption } from '@/types/defaultAccount'

/**
 * Get account filter function for a given voucher type
 * Returns a function that filters accounts based on voucher type rules
 */
export function getAccountFilterForVoucherType(
  voucherType: VoucherTypeOption | null | undefined,
): (account: ChartOfAccount) => boolean {
  if (!voucherType || voucherType === 'Other Business Voucher') {
    // No filter - show all active accounts
    return (account: ChartOfAccount) => account.active === true
  }

  switch (voucherType) {
    case 'Cash Payment':
      // Filter by account codes starting with 111 (cash accounts)
      return (account: ChartOfAccount) =>
        account.active === true && account.code.startsWith('111')
    case 'Bank Payment':
      // Filter by account codes starting with 112 (bank accounts)
      return (account: ChartOfAccount) =>
        account.active === true && account.code.startsWith('112')
    case 'Cash Receipt':
    case 'Bank Receipt':
      // Filter by account codes starting with 131 (customer receivables)
      return (account: ChartOfAccount) =>
        account.active === true && account.code.startsWith('131')
    default:
      return (account: ChartOfAccount) => account.active === true
  }
}

