'use client'

import { useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
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
import { CheckIcon, LockIcon, ChevronsUpDownIcon } from 'lucide-react'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'

export type AccountBalanceSide = 'debit' | 'credit' | 'both'

export interface AccountSummary {
  id: string
  code: string
  name: string
  balanceSide: AccountBalanceSide
  group?: string
  isLeaf: boolean
  disabledReason?: string
}

export interface AccountPickerProps {
  value?: AccountSummary | null
  options: AccountSummary[]
  onChange?: (account: AccountSummary | null) => void
  placeholder?: string
  allowOverride?: boolean
  disabled?: boolean
  lockReason?: string
  searchPlaceholder?: string
  className?: string
  triggerClassName?: string
}

const BALANCE_LABEL: Record<AccountBalanceSide, string> = {
  debit: 'Debit',
  credit: 'Credit',
  both: 'Balanced',
}

export function AccountPicker({
  value = null,
  options,
  onChange,
  placeholder = 'Select account...',
  allowOverride = true,
  disabled = false,
  lockReason,
  searchPlaceholder = 'Search by code or name...',
  className,
  triggerClassName,
}: AccountPickerProps) {
  const [open, setOpen] = useState(false)

  const selectedLabel = useMemo(() => {
    if (!value) return placeholder
    return `${value.code} • ${value.name}`
  }, [value, placeholder])

  const isLocked = !allowOverride && Boolean(value)

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild disabled={!isLocked || !lockReason}>
          <div className={cn('w-full', className)}>
            <Popover open={open} onOpenChange={setOpen}>
              <PopoverTrigger asChild>
                <Button
                  type="button"
                  variant="outline"
                  className={cn(
                    'w-full justify-between',
                    isLocked && 'cursor-not-allowed opacity-80',
                    triggerClassName
                  )}
                  disabled={disabled || isLocked}
                  role="combobox"
                  aria-expanded={open}
                  aria-label="Account combobox"
                >
                  <span className="truncate">{selectedLabel}</span>
                  {isLocked ? (
                    <LockIcon className="size-4 opacity-60" />
                  ) : (
                    <ChevronsUpDownIcon className="opacity-50" />
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent
                className="p-0 w-[var(--radix-popover-trigger-width)]"
                align="start"
                sideOffset={4}
                avoidCollisions
              >
                <Command>
                  <CommandInput placeholder={searchPlaceholder} />
                  <CommandList>
                    <CommandEmpty>No accounts found.</CommandEmpty>
                    <CommandGroup>
                      <CommandItem
                        value="__clear__"
                        onSelect={() => {
                          onChange?.(null)
                          setOpen(false)
                        }}
                      >
                        <span className="text-muted-foreground">Clear selection</span>
                        {!value ? <CheckIcon className="ml-auto size-4" /> : null}
                      </CommandItem>
                      {options.map((account) => {
                        const disabledOption = !account.isLeaf || Boolean(account.disabledReason)
                        const optionValue = `${account.code} ${account.name}`
                        const content = (
                          <>
                            <div className="flex flex-col items-start">
                              <span className="font-medium">
                                {account.code} • {account.name}
                              </span>
                              <div className="flex gap-2">
                                {account.group ? (
                                  <Badge variant="outline">{account.group}</Badge>
                                ) : null}
                                <Badge variant="secondary">
                                  {BALANCE_LABEL[account.balanceSide]}
                                </Badge>
                                {!account.isLeaf ? (
                                  <Badge variant="destructive">Not debited or credited</Badge>
                                ) : null}
                              </div>
                            </div>
                            <CheckIcon
                              className={cn(
                                'ml-auto size-4',
                                value?.id === account.id ? 'opacity-100' : 'opacity-0',
                              )}
                            />
                          </>
                        )

                        return (
                          <CommandItem
                            key={account.id}
                            value={optionValue}
                            disabled={disabledOption}
                            onSelect={() => {
                              onChange?.(account)
                              setOpen(false)
                            }}
                          >
                            {account.disabledReason ? (
                              <Tooltip>
                                <TooltipTrigger className="flex flex-1 items-center gap-2 text-left">
                                  {content}
                                </TooltipTrigger>
                                <TooltipContent>{account.disabledReason}</TooltipContent>
                              </Tooltip>
                            ) : (
                              content
                            )}
                          </CommandItem>
                        )
                      })}
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
          </div>
        </TooltipTrigger>
        {isLocked && lockReason ? <TooltipContent>{lockReason}</TooltipContent> : null}
      </Tooltip>
    </TooltipProvider>
  )
}

export default AccountPicker
