'use client'

import { useState, useEffect, useMemo } from 'react'
import { CheckIcon, ChevronsUpDownIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { getChartOfAccounts } from '@/services/chartOfAccounts'
import type { ChartOfAccount } from '@/types/chartOfAccount'

interface AccountComboboxFilteredProps {
  value?: number | null
  onValueChange: (value: number | null) => void
  disabled?: boolean
  parentAccountIds?: number[] | null // Filter to show only children of these parent accounts
  placeholder?: string
}

export default function AccountComboboxFiltered({
  value,
  onValueChange,
  disabled = false,
  parentAccountIds,
  placeholder = 'Select account...',
}: AccountComboboxFilteredProps) {
  const [open, setOpen] = useState(false)
  const [allAccounts, setAllAccounts] = useState<ChartOfAccount[]>([])
  const [loading, setLoading] = useState(false)

  // Load all accounts when popover opens
  useEffect(() => {
    if (open) {
      loadAccounts()
    }
  }, [open])

  // Load accounts from API
  const loadAccounts = async () => {
    try {
      setLoading(true)
      const response = await getChartOfAccounts({ active: true })
      const accountList = Array.isArray(response.data) ? (response.data as ChartOfAccount[]) : []
      setAllAccounts(accountList)
    } catch (err) {
      console.error('Failed to load accounts:', err)
      setAllAccounts([])
    } finally {
      setLoading(false)
    }
  }

  // Filter accounts to show only children of selected parent accounts (including nested children)
  const filteredAccounts = useMemo(() => {
    if (!parentAccountIds || parentAccountIds.length === 0) {
      // If no parent filter, show all accounts
      return allAccounts
    }

    // Recursive function to get all descendants of a parent account
    const getDescendants = (parentId: number, visited = new Set<number>()): number[] => {
      if (visited.has(parentId)) return []
      visited.add(parentId)

      const directChildren = allAccounts
        .filter((acc) => acc.parentId === parentId)
        .map((acc) => acc.id)

      const nestedChildren = directChildren.flatMap((childId) => getDescendants(childId, visited))

      return [...directChildren, ...nestedChildren]
    }

    // Get all descendant IDs for all selected parent accounts
    const allDescendantIds = new Set<number>()
    parentAccountIds.forEach((parentId) => {
      const descendants = getDescendants(parentId)
      descendants.forEach((id) => allDescendantIds.add(id))
    })

    // Get all accounts that are descendants of the selected parents
    const children = allAccounts.filter((account) => allDescendantIds.has(account.id))

    // Also include the parent accounts themselves if they are in the filter
    const parents = allAccounts.filter((account) => parentAccountIds.includes(account.id))

    // Combine and remove duplicates
    const combined = [...children, ...parents]
    const unique = combined.filter(
      (account, index, self) => index === self.findIndex((a) => a.id === account.id),
    )

    // Sort by code for better UX
    return unique.sort((a, b) => a.code.localeCompare(b.code))
  }, [allAccounts, parentAccountIds])

  // Find selected account for display
  const selectedAccount = useMemo(
    () => allAccounts.find((acc) => acc.id === value) || null,
    [allAccounts, value],
  )

  // Display value for the trigger button
  const displayValue = selectedAccount
    ? `${selectedAccount.code} - ${selectedAccount.name}`
    : placeholder

  // Handle account selection
  const handleSelect = (accountId: number | null) => {
    onValueChange(accountId)
    setOpen(false)
  }

  return (
    <Popover open={open} onOpenChange={setOpen} modal={false}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="w-full justify-between"
          disabled={
            (disabled ?? undefined) ||
            (parentAccountIds && parentAccountIds.length === 0) ||
            undefined
          }
          aria-label="Account combobox"
        >
          {displayValue}
          <ChevronsUpDownIcon className="opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className="p-0 w-[var(--radix-popover-trigger-width)]"
        side="bottom"
        align="start"
        sideOffset={4}
        onOpenAutoFocus={(e) => e.preventDefault()}
      >
        <Command className="!overflow-visible">
          <CommandInput placeholder="Search accounts..." className="h-9" />
          <CommandList
            className="max-h-[300px] overflow-y-auto overscroll-contain"
            style={
              {
                overflowY: 'auto',
                maxHeight: '300px',
                touchAction: 'pan-y',
              } as React.CSSProperties
            }
          >
            <CommandEmpty>
              {loading
                ? 'Loading...'
                : parentAccountIds && parentAccountIds.length === 0
                  ? 'Please select account filters first'
                  : 'No account found.'}
            </CommandEmpty>
            <CommandGroup>
              <CommandItem value="none" onSelect={() => handleSelect(null)}>
                <span className="text-muted-foreground">Clear selection</span>
                <CheckIcon
                  className={cn('ml-auto', value === null ? 'opacity-100' : 'opacity-0')}
                />
              </CommandItem>
              {filteredAccounts.map((account) => (
                <CommandItem
                  key={account.id}
                  value={`${account.code} ${account.name}`}
                  onSelect={() => handleSelect(account.id)}
                >
                  <span>
                    {account.code} - {account.name}
                  </span>
                  <CheckIcon
                    className={cn('ml-auto', value === account.id ? 'opacity-100' : 'opacity-0')}
                  />
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
