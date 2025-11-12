'use client'

import { Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import AccountFilterButton from '@/components/account/AccountFilterButton'
import AccountComboboxFiltered from '@/components/account/AccountComboboxFiltered'
import { getChartOfAccounts } from '@/services/chartOfAccounts'
import type { AccountDefault } from '@/types/defaultAccount'
import type { VoucherTypeOption } from '@/types/defaultAccount'

interface AccountDefaultsTableProps {
  rows: AccountDefault[]
  voucherType: VoucherTypeOption | null
  isAdmin: boolean
  onRowChange: (index: number, row: AccountDefault) => void
  onAddRow: () => void
  onRemoveRow: (index: number) => void
  disabled?: boolean
}

const MAX_ROWS = 5

export default function AccountDefaultsTable({
  rows,
  voucherType,
  isAdmin,
  onRowChange,
  onAddRow,
  onRemoveRow,
  disabled = false,
}: AccountDefaultsTableProps) {
  const handleAccountFilterChange = (index: number, accountFilterIds: number[] | null) => {
    const row = rows[index]
    // When account filter changes, clear the default account if it's not a child of the new filters
    onRowChange(index, {
      ...row,
      accountFilterIds: accountFilterIds || undefined,
      // Clear default account if filter changes (user needs to reselect)
      defaultAccountId: null,
      accountCode: null,
      accountName: null,
    })
  }

  const handleDefaultAccountSelect = async (index: number, accountId: number | null) => {
    const row = rows[index]
    let accountCode: string | null = null
    let accountName: string | null = null

    if (accountId) {
      try {
        const response = await getChartOfAccounts({ active: true })
        const accounts = Array.isArray(response.data) ? response.data : []
        const account = accounts.find((acc) => acc.id === accountId)
        if (account) {
          accountCode = account.code
          accountName = account.name
        }
      } catch (err) {
        console.error('Failed to load account details:', err)
      }
    }

    onRowChange(index, {
      ...row,
      defaultAccountId: accountId,
      accountCode,
      accountName,
    })
  }

  const handleColumnNameChange = (index: number, columnName: string) => {
    const row = rows[index]
    onRowChange(index, { ...row, columnName })
  }

  const canAddRow = isAdmin && rows.length < MAX_ROWS
  const isFirstTwoRows = (index: number) => index < 2

  return (
    <div className="space-y-2">
      <div className="overflow-hidden rounded-md border">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/50">
              <TableHead className="w-[200px]">Column Name</TableHead>
              <TableHead className="w-[200px]">Account Filter</TableHead>
              <TableHead>Default Account</TableHead>
              {isAdmin && <TableHead className="w-[80px]">Actions</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row, index) => {
              return (
                <TableRow key={index}>
                  <TableCell>
                    {isFirstTwoRows(index) ? (
                      <span className="text-sm">{row.columnName}</span>
                    ) : (
                      <Input
                        value={row.columnName}
                        onChange={(e) => handleColumnNameChange(index, e.target.value)}
                        placeholder="Column name"
                        disabled={disabled || !isAdmin}
                        className="h-8"
                      />
                    )}
                  </TableCell>
                  <TableCell>
                    <AccountFilterButton
                      value={row.accountFilterIds || null}
                      onValueChange={(accountFilterIds) => handleAccountFilterChange(index, accountFilterIds)}
                      disabled={disabled}
                      placeholder="Select account filters..."
                    />
                  </TableCell>
                  <TableCell>
                    <AccountComboboxFiltered
                      value={row.defaultAccountId ?? null}
                      onValueChange={(accountId) => handleDefaultAccountSelect(index, accountId)}
                      disabled={disabled}
                      parentAccountIds={row.accountFilterIds || null}
                      placeholder="Select default account..."
                    />
                  </TableCell>
                  {isAdmin && (
                    <TableCell>
                      {!isFirstTwoRows(index) && (
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7 text-destructive hover:text-destructive"
                              onClick={() => onRemoveRow(index)}
                              disabled={disabled}
                              aria-label="Remove row"
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>
                            <p>Remove Row</p>
                          </TooltipContent>
                        </Tooltip>
                      )}
                    </TableCell>
                  )}
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>
      {canAddRow && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onAddRow}
          disabled={disabled}
          className="w-full"
        >
          <Plus className="mr-2 h-4 w-4" />
          Add Row
        </Button>
      )}
    </div>
  )
}

