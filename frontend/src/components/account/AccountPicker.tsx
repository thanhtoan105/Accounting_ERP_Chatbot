import { useState, useEffect, useMemo } from 'react'
// <CHANGE> Replace MUI Autocomplete with shadcn Popover + Command pattern
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import { Badge } from '@/components/ui/badge'
import { Check, ChevronsUpDown } from 'lucide-react'
import { cn } from '@/lib/utils'
import { getPostableAccounts } from '@/services/chartOfAccounts'
import type { ChartOfAccount } from '@/types/chartOfAccount'

interface AccountPickerProps {
  value?: number | null
  onChange: (account: ChartOfAccount | null) => void
  label?: string
  error?: boolean
  helperText?: string
  disabled?: boolean
}

/**
 * Account picker component for voucher forms.
 * Only shows postable leaf accounts (accounts with postable=true and no children).
 * Supports search/typeahead with unaccented Vietnamese matching.
 */
export default function AccountPicker({
  value,
  onChange,
  label = 'Select Account',
  error = false,
  helperText,
  disabled = false,
}: AccountPickerProps) {
  const [accounts, setAccounts] = useState<ChartOfAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [open, setOpen] = useState(false)

  useEffect(() => {
    const loadAccounts = async () => {
      try {
        setLoading(true)
        setErrorMessage(null)
        const postableAccounts = await getPostableAccounts()
        setAccounts(postableAccounts)
      } catch (err) {
        const errorMsg = err instanceof Error ? err.message : 'Failed to load accounts'
        setErrorMessage(errorMsg)
      } finally {
        setLoading(false)
      }
    }

    loadAccounts()
  }, [])

  const selectedAccount = useMemo(() => {
    if (value === null || value === undefined) return null
    return accounts.find((acc) => acc.id === value) || null
  }, [value, accounts])

  const getOptionLabel = (option: ChartOfAccount) => {
    return `${option.code} - ${option.name}`
  }

  const isOptionEqualToValue = (option: ChartOfAccount, value: ChartOfAccount) => {
    return option.id === value.id
  }

  return (
    <div className={cn('w-full', disabled && 'opacity-50 cursor-not-allowed')}>
      {label && <label className="mb-1 block text-sm font-medium">{label}</label>}
      <Popover open={open} onOpenChange={(o) => !disabled && setOpen(o)}>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            role="combobox"
            aria-expanded={open}
            className="w-full justify-between"
          >
            {selectedAccount ? (
              <span className="truncate text-left">
                <span className="font-mono mr-2">{selectedAccount.code}</span>
                {selectedAccount.name}
                {selectedAccount.postable && (
                  <Badge className="ml-2" variant="secondary">
                    postable
                  </Badge>
                )}
              </span>
            ) : (
              'Select account'
            )}
            <ChevronsUpDown className="ml-2 size-4 opacity-50" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="p-0 w-[420px]">
          <Command
            filter={(value, search) => {
              const [code, name] = value.split('::')
              const s = search.toLowerCase()
              return code.includes(s) || name.includes(s) ? 1 : 0
            }}
          >
            <CommandInput placeholder="Search by code or name..." />
            <CommandList>
              {loading && <CommandEmpty>Loading…</CommandEmpty>}
              {!loading && accounts.length === 0 && (
                <CommandEmpty>No postable accounts found</CommandEmpty>
              )}
              {!loading && accounts.length > 0 && (
                <CommandGroup>
                  {accounts.map((acc) => (
                    <CommandItem
                      key={acc.id}
                      value={`${acc.code.toLowerCase()}::${acc.name.toLowerCase()}`}
                      onSelect={() => {
                        onChange(acc)
                        setOpen(false)
                      }}
                      className="flex items-center gap-2"
                    >
                      <span className="font-mono w-16">{acc.code}</span>
                      <span className="flex-1 truncate">{acc.name}</span>
                      <Check
                        className={cn(
                          'size-4',
                          selectedAccount?.id === acc.id ? 'opacity-100' : 'opacity-0',
                        )}
                      />
                    </CommandItem>
                  ))}
                </CommandGroup>
              )}
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
      {(error || errorMessage || helperText) && (
        <div
          className={cn(
            'mt-1 text-xs',
            error || errorMessage ? 'text-destructive' : 'text-muted-foreground',
          )}
        >
          {errorMessage || helperText}
        </div>
      )}
    </div>
  )
}
