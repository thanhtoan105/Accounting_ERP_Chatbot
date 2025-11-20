'use client'

import { useEffect, useMemo, useState } from 'react'
import { Loader2, Search, Plus } from 'lucide-react'

import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { getSuppliers } from '@/features/suppliers/services/supplier'
import type { Supplier } from '@/types/supplier'

export interface SupplierPickerProps {
  value?: Supplier | null
  onChange?: (supplier: Supplier | null) => void
  disabled?: boolean
  error?: string | null
  placeholder?: string
  onAddNew?: () => void
}

export function SupplierPicker({
  value,
  onChange,
  disabled,
  error,
  placeholder = 'Select supplier...',
  onAddNew,
}: SupplierPickerProps) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(false)
  const [suppliers, setSuppliers] = useState<Supplier[]>([])

  useEffect(() => {
    if (!open) return
    async function fetchSuppliers() {
      setLoading(true)
      try {
        const response = await getSuppliers({
          page: 1,
          size: 20,
          search: search || undefined,
          status: true,
        })
        setSuppliers(response.data)
      } catch (err) {
        console.error('Failed to fetch suppliers', err)
      } finally {
        setLoading(false)
      }
    }
    fetchSuppliers()
  }, [open, search])

  const selectedLabel = useMemo(() => {
    if (!value) return placeholder
    return `${value.code || ''} • ${value.name}`.trim()
  }, [value, placeholder])

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          className={cn(
            'w-full justify-between',
            error && 'border-destructive',
            disabled && 'cursor-not-allowed opacity-50',
          )}
          disabled={disabled}
          role="combobox"
          aria-expanded={open}
        >
          <span className="truncate">{selectedLabel}</span>
          <span className="text-xs">⌄</span>
        </Button>
      </PopoverTrigger>
      <PopoverContent className="p-0 w-[var(--radix-popover-trigger-width)]" align="start">
        <Command>
          <CommandInput
            placeholder="Search suppliers..."
            value={search}
            onValueChange={setSearch}
          />
          <CommandList>
            {loading ? (
              <div className="flex items-center justify-center p-4">
                <Loader2 className="h-4 w-4 animate-spin" />
              </div>
            ) : (
              <>
                <CommandEmpty>
                  {search ? 'No suppliers found.' : 'Start typing to search...'}
                </CommandEmpty>
                {onAddNew && (
                  <CommandGroup>
                    <CommandItem
                      onSelect={() => {
                        onAddNew()
                        setOpen(false)
                      }}
                      className="text-primary"
                    >
                      <Plus className="mr-2 h-4 w-4" />
                      Add New Supplier
                    </CommandItem>
                  </CommandGroup>
                )}
                <CommandGroup>
                  {suppliers.map((supplier) => (
                    <CommandItem
                      key={supplier.id}
                      value={`${supplier.code} ${supplier.name}`}
                      onSelect={() => {
                        onChange?.(supplier)
                        setOpen(false)
                      }}
                    >
                      <div className="flex flex-col">
                        <span className="font-medium">
                          {supplier.code ? `${supplier.code} • ` : ''}
                          {supplier.name}
                        </span>
                        {supplier.taxCode && (
                          <span className="text-xs text-muted-foreground">
                            Tax: {supplier.taxCode}
                          </span>
                        )}
                      </div>
                    </CommandItem>
                  ))}
                </CommandGroup>
              </>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}

