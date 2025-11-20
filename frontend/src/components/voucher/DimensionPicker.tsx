import { useEffect, useMemo, useState } from 'react'
import { Loader2, Search, X } from 'lucide-react'

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
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { VoucherDimensionOption } from '@/types/voucher'
import { getCustomers } from '@/features/customers/services/customer'
import { getSuppliers } from '@/features/suppliers/services/supplier'

type DimensionType = 'customer' | 'supplier' | 'costCenter'

const costCenterOptions: VoucherDimensionOption[] = [
    { id: 'CC-OPS', code: 'OPS', name: 'Operations' },
  { id: 'CC-SALES', code: 'SAL', name: 'Sales' },
  { id: 'CC-MKT', code: 'MKT', name: 'Marketing' },
  { id: 'CC-RND', code: 'RND', name: 'Research & Development' },
  { id: 'CC-ADM', code: 'ADM', name: 'Administration' },
]

const typeLabels: Record<DimensionType, string> = {
  customer: 'Customer',
  supplier: 'Supplier',
  costCenter: 'Cost center',
}

const placeholderMap: Record<DimensionType, string> = {
  customer: 'Select customer...',
  supplier: 'Select supplier...',
  costCenter: 'Select cost center...',
}

async function fetchOptions(
  type: DimensionType,
  search: string,
): Promise<VoucherDimensionOption[]> {
  if (type === 'customer') {
    const response = await getCustomers({
      page: 1,
      size: 10,
      search: search || undefined,
      status: true,
    })
    return response.data.map((customer) => ({
      id: String(customer.id),
      code: customer.code,
      name: customer.name,
    }))
  }
  if (type === 'supplier') {
    const response = await getSuppliers({
      page: 1,
      size: 10,
      search: search || undefined,
      status: true,
    })
    return response.data.map((supplier) => ({
      id: String(supplier.id),
      code: supplier.code,
      name: supplier.name,
    }))
  }
  const normalized = search.trim().toLowerCase()
  if (!normalized) return costCenterOptions
  return costCenterOptions.filter(
    (option) =>
      option.name.toLowerCase().includes(normalized) ||
      option.code?.toLowerCase().includes(normalized),
  )
}

interface DimensionPickerProps {
  type: DimensionType
  value?: VoucherDimensionOption | null
  onChange?: (option: VoucherDimensionOption | null) => void
  disabled?: boolean
  required?: boolean
  error?: string | null
}

export function DimensionPicker({
  type,
  value,
  onChange,
  disabled,
  required,
  error,
}: DimensionPickerProps) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(false)
  const [options, setOptions] = useState<VoucherDimensionOption[]>([])

  useEffect(() => {
    if (!open) return
    let active = true
    setLoading(true)
    fetchOptions(type, search)
      .then((result) => {
        if (!active) return
        setOptions(result)
      })
      .catch(() => {
        if (!active) return
        setOptions([])
      })
      .finally(() => {
        if (!active) return
        setLoading(false)
      })
    return () => {
      active = false
    }
  }, [open, search, type])

  const selectedLabel = useMemo(() => {
    if (!value) return placeholderMap[type]
    if (value.code) return `${value.code} • ${value.name}`
    return value.name
  }, [type, value])

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>
          {typeLabels[type]} {required ? <span className="text-destructive">*</span> : null}
        </span>
        {value && !disabled ? (
          <button
            type="button"
            className="inline-flex items-center gap-1 text-muted-foreground hover:text-foreground"
            onClick={() => onChange?.(null)}
          >
            <X className="h-3 w-3" />
            Delete
          </button>
        ) : null}
      </div>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            type="button"
            variant="outline"
            className={cn(
              'w-full justify-between text-left font-normal',
              !value && 'text-muted-foreground',
            )}
            disabled={disabled}
          >
            <span className="truncate">{selectedLabel}</span>
            <Search className="ml-2 h-4 w-4 opacity-50" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-[320px] p-0" align="start">
          <Command>
            <CommandInput
              placeholder={`Search ${typeLabels[type].toLowerCase()}...`}
              value={search}
              onValueChange={setSearch}
            />
            <CommandList>
              {loading ? (
                <div className="flex items-center gap-2 px-3 py-2 text-sm text-muted-foreground">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  Loading...
                </div>
              ) : null}
              <CommandEmpty>No results found.</CommandEmpty>
              <CommandGroup>
                {options.map((option) => (
                  <CommandItem
                    key={`${type}-${option.id}`}
                    value={option.id}
                    onSelect={() => {
                      onChange?.(option)
                      setOpen(false)
                      setSearch('')
                    }}
                  >
                    <div className="flex flex-col">
                      <span className="font-medium">{option.name}</span>
                      {option.code ? (
                        <span className="text-xs text-muted-foreground">{option.code}</span>
                      ) : null}
                    </div>
                    {value?.id === option.id ? (
                      <Badge variant="secondary" className="ml-auto">
                          Selected
                      </Badge>
                    ) : null}
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  )
}

export type { DimensionType }
