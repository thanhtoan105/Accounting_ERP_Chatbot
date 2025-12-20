'use client'

import { useState, useEffect, useCallback, useMemo } from 'react'
import {
  Plus,
  Search,
  RefreshCw,
  Edit,
  Trash2,
  Building2,
  User,
  FolderTree,
  Package,
} from 'lucide-react'
import { toast } from 'sonner'
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
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import {
  getAccountControls,
  createAccountControl,
  updateAccountControl,
  deleteAccountControl,
} from '@/services/accountControl'
import { getPostableAccounts } from '@/services/chartOfAccounts'
import type { AccountControl, AccountControlCreateRequest } from '@/types/accountControl'
import type { ChartOfAccount } from '@/types/chartOfAccount'

export default function AccountControls() {
  const [accountControls, setAccountControls] = useState<AccountControl[]>([])
  const [accounts, setAccounts] = useState<ChartOfAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [debouncedSearch, setDebouncedSearch] = useState<string>('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingControl, setEditingControl] = useState<AccountControl | null>(null)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [controlToDelete, setControlToDelete] = useState<AccountControl | null>(null)

  // Form state
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null)
  const [requiresCustomer, setRequiresCustomer] = useState(false)
  const [requiresSupplier, setRequiresSupplier] = useState(false)
  const [requiresCostCenter, setRequiresCostCenter] = useState(false)
  const [requiresItem, setRequiresItem] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const loadAccountControls = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await getAccountControls()
      setAccountControls(data)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load account controls'
      setError(errorMessage)
      toast.error('Failed to load account controls', { description: errorMessage })
    } finally {
      setLoading(false)
    }
  }, [])

  const loadAccounts = useCallback(async () => {
    try {
      const data = await getPostableAccounts()
      setAccounts(data)
    } catch (err: any) {
      console.error('Failed to load accounts', err)
    }
  }, [])

  useEffect(() => {
    loadAccountControls()
    loadAccounts()
  }, [loadAccountControls, loadAccounts])

  const filteredControls = useMemo(() => {
    if (!debouncedSearch.trim()) return accountControls
    const search = debouncedSearch.toLowerCase()
    return accountControls.filter(
      (control) =>
        control.accountCode?.toLowerCase().includes(search) ||
        control.accountName?.toLowerCase().includes(search),
    )
  }, [accountControls, debouncedSearch])

  const paginatedControls = useMemo(() => {
    const start = (page - 1) * pageSize
    const end = start + pageSize
    return filteredControls.slice(start, end)
  }, [filteredControls, page, pageSize])

  const totalPages = Math.ceil(filteredControls.length / pageSize)

  const openCreateDialog = () => {
    setEditingControl(null)
    setSelectedAccountId(null)
    setRequiresCustomer(false)
    setRequiresSupplier(false)
    setRequiresCostCenter(false)
    setRequiresItem(false)
    setDialogOpen(true)
  }

  const openEditDialog = (control: AccountControl) => {
    setEditingControl(control)
    setSelectedAccountId(control.accountId)
    setRequiresCustomer(control.requiresCustomer)
    setRequiresSupplier(control.requiresSupplier)
    setRequiresCostCenter(control.requiresCostCenter)
    setRequiresItem(control.requiresItem)
    setDialogOpen(true)
  }

  const openDeleteDialog = (control: AccountControl) => {
    setControlToDelete(control)
    setDeleteDialogOpen(true)
  }

  const handleSave = async () => {
    if (!selectedAccountId) {
      toast.error('Vui lòng chọn tài khoản')
      return
    }

    try {
      setSaving(true)
      const request: AccountControlCreateRequest = {
        accountId: selectedAccountId,
        requiresCustomer,
        requiresSupplier,
        requiresCostCenter,
        requiresItem,
      }

      if (editingControl) {
        await updateAccountControl(editingControl.id, request)
        toast.success('Đã cập nhật cấu hình kiểm soát tài khoản')
      } else {
        await createAccountControl(request)
        toast.success('Đã tạo cấu hình kiểm soát tài khoản')
      }

      setDialogOpen(false)
      await loadAccountControls()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to save account control'
      toast.error('Failed to save account control', { description: errorMessage })
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!controlToDelete) return

    try {
      await deleteAccountControl(controlToDelete.id)
      toast.success('Đã xóa cấu hình kiểm soát tài khoản')
      setDeleteDialogOpen(false)
      setControlToDelete(null)
      await loadAccountControls()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to delete account control'
      toast.error('Failed to delete account control', { description: errorMessage })
    }
  }

  const selectedAccount = useMemo(() => {
    if (!selectedAccountId) return null
    return accounts.find((acc) => acc.id === selectedAccountId) || null
  }, [accounts, selectedAccountId])

  // Get accounts that don't have controls yet (for create dialog)
  const availableAccounts = useMemo(() => {
    const controlledAccountIds = new Set(accountControls.map((c) => c.accountId))
    return accounts.filter((acc) => {
      // If editing, include the current account
      if (editingControl && acc.id === editingControl.accountId) return true
      // Otherwise, only show accounts without controls
      return !controlledAccountIds.has(acc.id)
    })
  }, [accounts, accountControls, editingControl])

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">Cấu hình kiểm soát tài khoản</h1>
          <p className="text-muted-foreground mt-1">
            Quản lý các yêu cầu về dimensions (khách hàng, nhà cung cấp, trung tâm chi phí, hàng
            hóa) cho từng tài khoản
          </p>
        </div>
        <Button onClick={openCreateDialog}>
          <Plus className="mr-2 h-4 w-4" />
          Tạo cấu hình mới
        </Button>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-1 items-center gap-2 max-w-sm">
          <Search className="h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Tìm theo mã hoặc tên tài khoản..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="flex-1"
          />
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={loadAccountControls}>
            <RefreshCw className="mr-2 h-4 w-4" />
            Làm mới
          </Button>
        </div>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive bg-destructive/10 p-4 text-destructive">
          {error}
        </div>
      )}

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[120px]">Mã TK</TableHead>
              <TableHead>Tên tài khoản</TableHead>
              <TableHead className="w-[150px]">Tài khoản cha</TableHead>
              <TableHead className="w-[200px]">Yêu cầu Dimensions</TableHead>
              <TableHead className="w-[150px] text-center">Thao tác</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell>
                    <Skeleton className="h-4 w-20" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-48" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-32" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-32" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-24" />
                  </TableCell>
                </TableRow>
              ))
            ) : paginatedControls.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground py-8">
                  {debouncedSearch.trim()
                    ? 'Không tìm thấy cấu hình nào phù hợp'
                    : 'Chưa có cấu hình kiểm soát nào. Nhấn "Tạo cấu hình mới" để bắt đầu.'}
                </TableCell>
              </TableRow>
            ) : (
              paginatedControls.map((control) => {
                // Find the account and its parent account info from accounts list
                const account = control.accountId
                  ? accounts.find((acc) => acc.id === control.accountId)
                  : null
                const parentAccount = account?.parentId
                  ? accounts.find((acc) => acc.id === account.parentId)
                  : null

                return (
                  <TableRow key={control.id}>
                    <TableCell className="font-mono font-medium">
                      {control.accountCode || 'N/A'}
                    </TableCell>
                    <TableCell>
                      <div>
                        <div>{control.accountName || 'N/A'}</div>
                        {account && (
                          <div className="text-xs text-muted-foreground mt-1">
                            {account.postable ? (
                              <Badge variant="outline" className="text-xs">
                                Có thể ghi sổ
                              </Badge>
                            ) : (
                              <Badge variant="secondary" className="text-xs">
                                Tài khoản tổng hợp
                              </Badge>
                            )}
                          </div>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      {parentAccount ? (
                        <div className="text-sm">
                          <div className="font-mono text-muted-foreground">
                            {parentAccount.code}
                          </div>
                          <div className="text-xs text-muted-foreground truncate max-w-[140px]">
                            {parentAccount.name}
                          </div>
                        </div>
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {control.requiresCustomer && (
                          <Badge variant="outline" className="gap-1">
                            <User className="h-3 w-3" />
                            Khách hàng
                          </Badge>
                        )}
                        {control.requiresSupplier && (
                          <Badge variant="outline" className="gap-1">
                            <Building2 className="h-3 w-3" />
                            Nhà cung cấp
                          </Badge>
                        )}
                        {control.requiresCostCenter && (
                          <Badge variant="outline" className="gap-1">
                            <FolderTree className="h-3 w-3" />
                            Trung tâm chi phí
                          </Badge>
                        )}
                        {control.requiresItem && (
                          <Badge variant="outline" className="gap-1">
                            <Package className="h-3 w-3" />
                            Hàng hóa
                          </Badge>
                        )}
                        {!control.requiresCustomer &&
                          !control.requiresSupplier &&
                          !control.requiresCostCenter &&
                          !control.requiresItem && (
                            <span className="text-xs text-muted-foreground">Không có yêu cầu</span>
                          )}
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-center gap-2">
                        <Button variant="ghost" size="sm" onClick={() => openEditDialog(control)}>
                          <Edit className="h-4 w-4" />
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => openDeleteDialog(control)}>
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      {!loading && filteredControls.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="text-sm text-muted-foreground">
            Hiển thị {paginatedControls.length} / {filteredControls.length} cấu hình
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => setPage(1)} disabled={page === 1}>
              Đầu
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page === 1}
            >
              Trước
            </Button>
            <span className="text-sm">
              Trang {page} / {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page === totalPages}
            >
              Sau
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage(totalPages)}
              disabled={page === totalPages}
            >
              Cuối
            </Button>
            <Select
              value={String(pageSize)}
              onValueChange={(value) => {
                setPageSize(Number(value))
                setPage(1)
              }}
            >
              <SelectTrigger className="w-[100px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="10">10</SelectItem>
                <SelectItem value="20">20</SelectItem>
                <SelectItem value="30">30</SelectItem>
                <SelectItem value="50">50</SelectItem>
                <SelectItem value="100">100</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      )}

      {/* Create/Edit Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editingControl ? 'Chỉnh sửa cấu hình' : 'Tạo cấu hình mới'}</DialogTitle>
            <DialogDescription>
              Cấu hình các yêu cầu về dimensions cho tài khoản được chọn
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="account">Tài khoản *</Label>
              <Select
                value={selectedAccountId ? String(selectedAccountId) : ''}
                onValueChange={(value) => setSelectedAccountId(Number(value))}
                disabled={!!editingControl}
              >
                <SelectTrigger id="account">
                  <SelectValue placeholder="Chọn tài khoản..." />
                </SelectTrigger>
                <SelectContent>
                  {availableAccounts.map((account) => {
                    // Find parent account for context
                    const parentAccount = account.parentId
                      ? accounts.find((acc) => acc.id === account.parentId)
                      : null

                    return (
                      <SelectItem key={account.id} value={String(account.id)}>
                        <div className="flex flex-col">
                          <div className="flex items-center gap-2">
                            <span className="font-mono font-medium">{account.code}</span>
                            <span>•</span>
                            <span className="truncate">{account.name}</span>
                          </div>
                          {parentAccount && (
                            <span className="text-xs text-muted-foreground ml-6">
                              Thuộc: {parentAccount.code} • {parentAccount.name}
                            </span>
                          )}
                          {account.postable && (
                            <span className="text-xs text-muted-foreground ml-6">
                              Có thể ghi sổ
                            </span>
                          )}
                        </div>
                      </SelectItem>
                    )
                  })}
                </SelectContent>
              </Select>
              {selectedAccount && (
                <div className="space-y-1">
                  <p className="text-xs text-muted-foreground">
                    {selectedAccount.code} • {selectedAccount.name}
                  </p>
                  {selectedAccount.parentId && (
                    <p className="text-xs text-muted-foreground">
                      Tài khoản cha:{' '}
                      {accounts.find((acc) => acc.id === selectedAccount.parentId)?.code || 'N/A'}
                    </p>
                  )}
                  <div className="flex gap-2">
                    {selectedAccount.postable ? (
                      <Badge variant="outline" className="text-xs">
                        Có thể ghi sổ
                      </Badge>
                    ) : (
                      <Badge variant="secondary" className="text-xs">
                        Tài khoản tổng hợp
                      </Badge>
                    )}
                  </div>
                </div>
              )}
            </div>

            <div className="space-y-3">
              <Label>Yêu cầu Dimensions</Label>
              <div className="space-y-3 rounded-lg border p-4">
                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="requiresCustomer"
                    checked={requiresCustomer}
                    onCheckedChange={(checked) => setRequiresCustomer(checked === true)}
                  />
                  <Label
                    htmlFor="requiresCustomer"
                    className="flex items-center gap-2 cursor-pointer"
                  >
                    <User className="h-4 w-4" />
                    Yêu cầu chọn Khách hàng
                  </Label>
                </div>
                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="requiresSupplier"
                    checked={requiresSupplier}
                    onCheckedChange={(checked) => setRequiresSupplier(checked === true)}
                  />
                  <Label
                    htmlFor="requiresSupplier"
                    className="flex items-center gap-2 cursor-pointer"
                  >
                    <Building2 className="h-4 w-4" />
                    Yêu cầu chọn Nhà cung cấp
                  </Label>
                </div>
                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="requiresCostCenter"
                    checked={requiresCostCenter}
                    onCheckedChange={(checked) => setRequiresCostCenter(checked === true)}
                  />
                  <Label
                    htmlFor="requiresCostCenter"
                    className="flex items-center gap-2 cursor-pointer"
                  >
                    <FolderTree className="h-4 w-4" />
                    Yêu cầu chọn Trung tâm chi phí
                  </Label>
                </div>
                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="requiresItem"
                    checked={requiresItem}
                    onCheckedChange={(checked) => setRequiresItem(checked === true)}
                  />
                  <Label htmlFor="requiresItem" className="flex items-center gap-2 cursor-pointer">
                    <Package className="h-4 w-4" />
                    Yêu cầu chọn Hàng hóa
                  </Label>
                </div>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={saving}>
              Hủy
            </Button>
            <Button onClick={handleSave} disabled={saving || !selectedAccountId}>
              {saving ? 'Đang lưu...' : editingControl ? 'Cập nhật' : 'Tạo mới'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận xóa</AlertDialogTitle>
            <AlertDialogDescription>
              Bạn có chắc chắn muốn xóa cấu hình kiểm soát cho tài khoản{' '}
              <strong>
                {controlToDelete?.accountCode} • {controlToDelete?.accountName}
              </strong>
              ? Hành động này không thể hoàn tác.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Xóa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
