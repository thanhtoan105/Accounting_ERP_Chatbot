import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { CheckCircle2 } from 'lucide-react'
import type { ChartOfAccountHierarchy } from '../../types/chartOfAccount'

interface AccountDetailsModalProps {
  open: boolean
  onClose: () => void
  account: ChartOfAccountHierarchy
}

const getAccountTypeLabel = (type: string) => {
  switch (type) {
    case 'Asset':
      return 'Tài sản'
    case 'Liability':
      return 'Nợ phải trả'
    case 'Equity':
      return 'Vốn chủ sở hữu'
    case 'Revenue':
      return 'Doanh thu'
    case 'Expense':
      return 'Chi phí'
    default:
      return type
  }
}

/**
 * Modal displaying all account details including code, name, type, normal_side, postable flag, etc.
 */
export default function AccountDetailsModal({ open, onClose, account }: AccountDetailsModalProps) {
  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <DialogTitle>Chi tiết tài khoản</DialogTitle>
            {account.postable && (
              <Badge variant="success" className="gap-1">
                <CheckCircle2 className="size-3" />
                Có thể hạch toán
              </Badge>
            )}
          </div>
          <DialogDescription>
            Thông tin chi tiết về tài khoản {account.code}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {/* Account Code */}
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-muted-foreground">Số tài khoản</label>
            <p className="font-mono font-bold text-base">{account.code}</p>
          </div>

          {/* Account Name */}
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-muted-foreground">Tên tài khoản</label>
            <p className="text-base">{account.name}</p>
          </div>

          {/* Type and Normal Side */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">Tính chất</label>
              <div>
                <Badge
                  variant={account.type === 'Asset' ? 'default' : 'secondary'}
                  className="text-xs"
                >
                  {getAccountTypeLabel(account.type)}
                </Badge>
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">Số dư bình thường</label>
              <div>
                <Badge
                  variant={account.normalSide === 'Debit' ? 'default' : 'secondary'}
                  className="text-xs"
                >
                  {account.normalSide === 'Debit' ? 'Dư Nợ' : 'Dư Có'}
                </Badge>
              </div>
            </div>
          </div>

          {/* Postable and Ordering Position */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">Có thể hạch toán</label>
              <p className="text-sm">{account.postable ? 'Có' : 'Không'}</p>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">Vị trí sắp xếp</label>
              <p className="text-sm">{account.orderingPosition}</p>
            </div>
          </div>

          {/* Parent Account */}
          {account.parentCode && (
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">Tài khoản cha</label>
              <p className="font-mono text-sm">{account.parentCode}</p>
            </div>
          )}

          {/* Balance */}
          {account.balance !== undefined && account.balance !== null && (
            <>
              <Separator />
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">Số dư hiện tại</label>
                <p className="text-2xl font-bold">
                  {account.balance.toLocaleString('vi-VN')} VND
                </p>
              </div>
            </>
          )}
        </div>

        <DialogFooter>
          <Button onClick={onClose}>Đóng</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
