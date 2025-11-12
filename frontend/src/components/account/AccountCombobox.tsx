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

interface AccountComboboxProps {
  value?: number | null
  onValueChange: (value: number | null) => void
  disabled?: boolean
  excludeAccountId?: number // Exclude this account from the list (to prevent circular references)
  placeholder?: string // Custom placeholder text
}

export default function AccountCombobox({
  value,
  onValueChange,
  disabled = false,
  excludeAccountId,
  placeholder = 'Select account...',
}: AccountComboboxProps) {
  const [open, setOpen] = useState(false)
  const [accounts, setAccounts] = useState<ChartOfAccount[]>([])
  const [loading, setLoading] = useState(false)

  // Load accounts when popover opens
  useEffect(() => {
    if (open) {
      loadAccounts()
    }
  }, [open])

  // Ensure CommandList can scroll when popover opens
  useEffect(() => {
    if (!open) return

    // Use setTimeout to ensure DOM is ready after popover renders
    const timer = setTimeout(() => {
      const popoverContent = document.querySelector('[data-slot="popover-content"]') as HTMLElement
      const commandList = popoverContent?.querySelector('[data-slot="command-list"]') as HTMLElement

      if (commandList) {
        // Force scrollable styles
        commandList.style.setProperty('overflow-y', 'auto', 'important')
        commandList.style.setProperty('max-height', '300px', 'important')
        commandList.style.setProperty('overscroll-behavior', 'contain', 'important')
        commandList.style.setProperty('touch-action', 'pan-y', 'important')

        // Add wheel event handler to ensure scrolling works
        const handleWheel = (e: WheelEvent) => {
          const element = commandList
          const { scrollTop, scrollHeight, clientHeight } = element
          const isAtTop = scrollTop <= 0
          const isAtBottom = scrollTop + clientHeight >= scrollHeight - 1

          // If we can scroll in the direction of the wheel, do it
          if ((e.deltaY > 0 && !isAtBottom) || (e.deltaY < 0 && !isAtTop)) {
            element.scrollTop += e.deltaY
            e.preventDefault()
            e.stopPropagation()
          }
        }

        commandList.addEventListener('wheel', handleWheel, { passive: false })

        // Store cleanup
        ;(commandList as any).__wheelHandler = handleWheel
      }
    }, 50)

    return () => {
      clearTimeout(timer)
      // Cleanup
      const commandList = document.querySelector(
        '[data-slot="popover-content"] [data-slot="command-list"]',
      ) as HTMLElement
      if (commandList && (commandList as any).__wheelHandler) {
        commandList.removeEventListener('wheel', (commandList as any).__wheelHandler)
        delete (commandList as any).__wheelHandler
      }
    }
  }, [open, accounts])

  // Load accounts from API
  const loadAccounts = async () => {
    try {
      setLoading(true)
      const response = await getChartOfAccounts({ active: true })
      // Filter out the account being edited to prevent circular references
      // Ensure we only use ChartOfAccount type (not ChartOfAccountHierarchy)
      const accountList = Array.isArray(response.data) ? (response.data as ChartOfAccount[]) : []
      const filtered = excludeAccountId
        ? accountList.filter((acc) => acc.id !== excludeAccountId)
        : accountList
      setAccounts(filtered)
    } catch (err) {
      console.error('Failed to load accounts:', err)
      setAccounts([])
    } finally {
      setLoading(false)
    }
  }

  // Find selected account for display
  const selectedAccount = useMemo(
    () => accounts.find((acc) => acc.id === value) || null,
    [accounts, value],
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

  // Layout at bottom following combobox-01 pattern from @ss-components
  return (
    <Popover open={open} onOpenChange={setOpen} modal={false}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="w-full justify-between"
          disabled={disabled}
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
        avoidCollisions={false}
        onOpenAutoFocus={(e) => e.preventDefault()}
        onWheel={(e) => {
          // Forward wheel events to CommandList if it exists
          const commandList = (e.currentTarget as HTMLElement).querySelector(
            '[data-slot="command-list"]',
          ) as HTMLElement
          if (commandList) {
            const { scrollTop, scrollHeight, clientHeight } = commandList
            const isAtTop = scrollTop <= 0
            const isAtBottom = scrollTop + clientHeight >= scrollHeight - 1

            if ((e.deltaY > 0 && !isAtBottom) || (e.deltaY < 0 && !isAtTop)) {
              commandList.scrollTop += e.deltaY
              e.preventDefault()
              e.stopPropagation()
            }
          }
        }}
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
            <CommandEmpty>{loading ? 'Loading...' : 'No account found.'}</CommandEmpty>
            <CommandGroup>
              <CommandItem value="none" onSelect={() => handleSelect(null)}>
                <span className="text-muted-foreground">Clear selection</span>
                <CheckIcon
                  className={cn('ml-auto', value === null ? 'opacity-100' : 'opacity-0')}
                />
              </CommandItem>
              {accounts.map((account) => (
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
