import { List } from 'lucide-react'
import { VoucherLineGrid, type VoucherEntryLine } from '@/components/voucher'
import type { AccountSummary } from '@/components/account/AccountPicker'
import { VoucherLineGridSkeleton } from '@/features/accounting/components/voucher-ui'

interface VoucherFormLinesProps {
  accounts: AccountSummary[]
  lines: VoucherEntryLine[]
  onLinesChange: (lines: VoucherEntryLine[]) => void
  validationMap: Record<number, Record<string, string[]>>
  lockedAccountIds: string[]
  readOnly: boolean
  loading: boolean
  loadingAccounts: boolean
  onUndo: () => void
  onRedo: () => void
  canUndo: boolean
  canRedo: boolean
}

export function VoucherFormLines({
  accounts,
  lines,
  onLinesChange,
  validationMap,
  lockedAccountIds,
  readOnly,
  loading,
  loadingAccounts,
  onUndo,
  onRedo,
  canUndo,
  canRedo,
}: VoucherFormLinesProps) {
  if (loadingAccounts) {
    return <VoucherLineGridSkeleton />
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between px-1">
        <h3 className="font-semibold text-sm flex items-center gap-2">
          <List className="w-4 h-4 text-primary" />
          Entry Lines
        </h3>
        <div className="text-xs text-muted-foreground">
          Press{' '}
          <kbd className="pointer-events-none inline-flex h-5 select-none items-center gap-1 rounded border bg-muted px-1.5 font-mono text-[10px] font-medium text-muted-foreground opacity-100">
            Enter
          </kbd>{' '}
          to add new line
        </div>
      </div>

      <VoucherLineGrid
        accounts={accounts}
        lines={lines}
        onLinesChange={onLinesChange}
        validationMap={validationMap}
        lockedAccountIds={lockedAccountIds}
        readOnly={readOnly}
        loading={loading}
        onUndo={onUndo}
        onRedo={onRedo}
        canUndo={canUndo}
        canRedo={canRedo}
        variant="dense"
      />
    </div>
  )
}
