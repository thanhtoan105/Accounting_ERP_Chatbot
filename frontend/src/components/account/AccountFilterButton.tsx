'use client'

import { useState, useEffect, useMemo } from 'react'
import MultipleSelector, { type Option } from '@/components/ui/multi-select'
import { getChartOfAccounts } from '@/services/chartOfAccounts'
import type { ChartOfAccount } from '@/types/chartOfAccount'

interface AccountFilterButtonProps {
  value?: number[] | null // Array of selected account IDs
  onValueChange: (value: number[] | null) => void
  disabled?: boolean
  placeholder?: string
}

export default function AccountFilterButton({
  value,
  onValueChange,
  disabled = false,
  placeholder = 'Select accounts to filter...',
}: AccountFilterButtonProps) {
  const [accounts, setAccounts] = useState<ChartOfAccount[]>([])
  const [loading, setLoading] = useState(false)

  // Load accounts from API - show ALL accounts (no filtering)
  const loadAccounts = async (searchTerm: string = ''): Promise<Option[]> => {
    try {
      setLoading(true)
      const response = await getChartOfAccounts({ active: true, search: searchTerm || undefined })
      const accountList = Array.isArray(response.data)
        ? (response.data as ChartOfAccount[])
        : []
      // No filter - show all active accounts
      setAccounts(accountList)
      
      // Convert to options
      return accountList.map((account) => ({
        value: account.id.toString(),
        label: `${account.code} - ${account.name}`,
      }))
    } catch (err) {
      console.error('Failed to load accounts:', err)
      setAccounts([])
      return []
    } finally {
      setLoading(false)
    }
  }

  // State to store the selected account details (for display when not in loaded list)
  const [selectedAccountDetails, setSelectedAccountDetails] = useState<ChartOfAccount[]>([])

  // Load selected account details if not in loaded list
  useEffect(() => {
    if (value && value.length > 0) {
      const missingIds = value.filter(
        (id) => !accounts.find((acc) => acc.id === id)
      )
      
      if (missingIds.length > 0) {
        // Load missing account details
        getChartOfAccounts({ active: true })
          .then((response) => {
            const accountList = Array.isArray(response.data) ? response.data : []
            const found = accountList.filter((acc) => missingIds.includes(acc.id))
            setSelectedAccountDetails((prev) => {
              const existing = prev.filter((acc) => !missingIds.includes(acc.id))
              return [...existing, ...found]
            })
          })
          .catch(() => {
            // Keep existing details
          })
      } else {
        // All accounts are in the loaded list
        const found = accounts.filter((acc) => value.includes(acc.id))
        setSelectedAccountDetails(found)
      }
    } else {
      setSelectedAccountDetails([])
    }
  }, [value, accounts])

  // Convert selected value to Option format
  const selectedOptions = useMemo<Option[]>(() => {
    if (!value || value.length === 0) return []
    
    // Try to find in loaded accounts first
    const foundInAccounts = accounts
      .filter((acc) => value.includes(acc.id))
      .map((acc) => ({
        value: acc.id.toString(),
        label: `${acc.code} - ${acc.name}`,
      }))
    
    // Add any missing from selectedAccountDetails
    const foundInDetails = selectedAccountDetails
      .filter((acc) => value.includes(acc.id) && !accounts.find((a) => a.id === acc.id))
      .map((acc) => ({
        value: acc.id.toString(),
        label: `${acc.code} - ${acc.name}`,
      }))
    
    return [...foundInAccounts, ...foundInDetails]
  }, [value, accounts, selectedAccountDetails])

  // Handle option change
  const handleChange = (options: Option[]) => {
    if (options.length === 0) {
      onValueChange(null)
    } else {
      const accountIds = options.map((opt) => parseInt(opt.value, 10))
      onValueChange(accountIds)
    }
  }

  // Async search function
  const handleSearch = async (searchTerm: string): Promise<Option[]> => {
    return await loadAccounts(searchTerm)
  }

  // Load initial accounts when component mounts
  useEffect(() => {
    loadAccounts()
  }, [])

  return (
    <MultipleSelector
      value={selectedOptions}
      onChange={handleChange}
      placeholder={placeholder}
      onSearch={handleSearch}
      triggerSearchOnFocus={true}
      delay={300}
      disabled={disabled}
      hideClearAllButton={false}
      hidePlaceholderWhenSelected={true}
      loadingIndicator={
        <p className="text-center text-sm text-muted-foreground py-4">Loading accounts...</p>
      }
      emptyIndicator={
        <p className="text-center text-sm text-muted-foreground py-4">No account found.</p>
      }
      className="w-full min-w-[200px]"
      commandProps={{
        label: 'Search accounts',
      }}
    />
  )
}
