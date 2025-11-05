import type { ChartOfAccount, ChartOfAccountHierarchy } from '../types/chartOfAccount'

/**
 * Determine account level based on code length (TT200 format)
 * Level 1: 1 digit (e.g., "1")
 * Level 2: 2 digits (e.g., "11")
 * Level 3: 3 digits (e.g., "111")
 * Level 4: 4 digits (e.g., "1111")
 */
function getAccountLevel(code: string): number {
  return code.length
}

/**
 * Recursively filter accounts to get level 3 accounts and their sublevels (level 3+).
 * Flattens the hierarchy and returns a flat array of accounts with level 3 or higher.
 * This includes level 3 accounts (e.g., 111) and all their sublevels (e.g., 1111, 1112, etc.).
 */
export function filterLevel3Accounts(
  accounts: ChartOfAccountHierarchy[],
): ChartOfAccount[] {
  const result: ChartOfAccount[] = []

  const traverse = (accs: ChartOfAccountHierarchy[]) => {
    accs.forEach((account) => {
      const level = getAccountLevel(account.code)
      // Include level 3 and all sublevels (level >= 3)
      if (level >= 3) {
        // Extract only the ChartOfAccount fields (no children)
        const { children, ...accountData } = account
        result.push(accountData as ChartOfAccount)
      }
      // Continue traversing children to include sublevels
      if (account.children && account.children.length > 0) {
        traverse(account.children)
      }
    })
  }

  traverse(accounts)
  return result
}

