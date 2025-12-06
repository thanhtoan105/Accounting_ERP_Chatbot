import { VoucherLineGrid, type VoucherEntryLine } from '@/components/voucher'
import type { AccountSummary } from '@/components/account/AccountPicker'
import { VoucherSkeleton } from '@/features/accounting/components/voucher-ui'

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
  return (
    <div className="voucher-card rounded-xl border bg-card shadow-sm overflow-hidden">
      <div className="px-5 py-4 border-b bg-voucher-surface-1">
        <h3 className="font-semibold text-base">Entry Lines</h3>
      </div>
      <div className="p-5">
        {loadingAccounts ? (
          <VoucherSkeleton variant="form" />
        ) : (
          <VoucherLineGrid
            accounts={accounts}
            lines={lines}
            onLinesChange={(updated) => {
              onLinesChange(updated)
            }}
            validationMap={validationMap}
            lockedAccountIds={lockedAccountIds}
            readOnly={readOnly}
            loading={loading}
            onUndo={onUndo}
            onRedo={onRedo}
            canUndo={canUndo}
            canRedo={canRedo}
          />
        )}
      </div>
    </div>
  )
}
