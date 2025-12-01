import { useState, useEffect, useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useReactTable,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  getFilteredRowModel,
  flexRender,
  type SortingState,
} from '@tanstack/react-table'
import {
  Plus,
  Search,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  X,
  Loader2,
} from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { Label } from '@/components/ui/label'
import { useRole } from '@/hooks/useRole'
import { useAuth } from '@/hooks/useAuth'
import RoleGuard from '@/components/guards/RoleGuard'
import { getRoleDisplayName, canManageRole, type Role } from '@/utils/roles'
import InviteUserDialog from './InviteUserDialog'
import CreateUserDialog from './CreateUserDialog'
import EditUserDialog from './EditUserDialog'
import * as userService from '@/services/user'
import type { User } from '@/services/auth'
import { createUserTableColumns, type UserTableActions } from '../components/user-table-columns'

const ROLES: Role[] = ['admin', 'accountant', 'chief_accountant', 'cfo']
const STATUSES = ['ACTIVE', 'INACTIVE', 'LOCKED']

export default function UserManagement() {
  const { t } = useTranslation()
  const { canManageUsers } = useRole()
  const { user: currentUser } = useAuth()

  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [roleFilter, setRoleFilter] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)

  const [inviteDialogOpen, setInviteDialogOpen] = useState(false)
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [selectedUser, setSelectedUser] = useState<User | null>(null)

  const [actionLoading, setActionLoading] = useState<Record<string, number | null>>({
    deactivate: null,
    activate: null,
    resetPassword: null,
  })

  const [resetPasswordDialogOpen, setResetPasswordDialogOpen] = useState(false)
  const [resetPasswordUserId, setResetPasswordUserId] = useState<number | null>(null)
  const [resetPasswordEmail, setResetPasswordEmail] = useState<string>('')

  const [sorting, setSorting] = useState<SortingState>([])

  const loadUsers = useCallback(async () => {
    let requestSucceeded = false
    try {
      setLoading(true)
      setError(null)
      const params: userService.GetAllUsersParams = {
        page: page - 1,
        size: pageSize,
      }
      if (roleFilter) params.role = roleFilter
      if (statusFilter) params.status = statusFilter
      if (searchTerm.trim()) params.search = searchTerm.trim()

      const response = await userService.getAllUsers(params)
      requestSucceeded = true
      setUsers(response.data)
      setTotalPages(response.totalPages)
      setTotalElements(response.totalElements)
      setError(null)
    } catch (err: any) {
      if (!requestSucceeded) {
        if (
          err?.code === 'UNAUTHORIZED' ||
          err?.code === 'FORBIDDEN' ||
          err?.status === 401 ||
          err?.status === 403
        ) {
          const errorMessage =
            err instanceof Error
              ? err.message
              : err?.message || 'Authentication failed. Please try again.'
          setError(errorMessage)
        } else {
          const errorMessage =
            err instanceof Error
              ? err.message
              : (err as { error?: { message?: string }; message?: string })?.error?.message ||
              (err as { message?: string })?.message ||
              'Failed to load users'
          setError(errorMessage)
        }
      }
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, roleFilter, statusFilter, searchTerm])

  useEffect(() => {
    loadUsers()
  }, [loadUsers])

  const handleFilterChange = () => setPage(1)
  useEffect(() => {
    handleFilterChange()
  }, [roleFilter, statusFilter])
  const handleSearch = (value: string) => {
    setSearchTerm(value)
    setPage(1)
  }
  const handleCreateUser = () => setCreateDialogOpen(true)
  const handleEditUser = (user: User) => {
    setSelectedUser(user)
    setEditDialogOpen(true)
  }

  const handleDeactivateClick = async (userId: number, email: string) => {
    try {
      setActionLoading((prev) => ({ ...prev, deactivate: userId }))
      setError(null)
      await userService.deactivateUser(userId)
      toast.success('User deactivated successfully')
      await loadUsers()
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
          (err as { message?: string })?.message ||
          'Failed to deactivate user'
      setError(errorMessage)
      toast.error(errorMessage)
    } finally {
      setActionLoading((prev) => ({ ...prev, deactivate: null }))
    }
  }
  const handleActivateClick = async (userId: number, email: string) => {
    try {
      setActionLoading((prev) => ({ ...prev, activate: userId }))
      setError(null)
      await userService.activateUser(userId)
      toast.success('User activated successfully')
      await loadUsers()
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
          (err as { message?: string })?.message ||
          'Failed to activate user'
      setError(errorMessage)
      toast.error(errorMessage)
    } finally {
      setActionLoading((prev) => ({ ...prev, activate: null }))
    }
  }

  const handleResetPasswordClick = (userId: number, email: string) => {
    setResetPasswordUserId(userId)
    setResetPasswordEmail(email)
    setResetPasswordDialogOpen(true)
  }
  const handleResetPasswordConfirm = async () => {
    if (!resetPasswordUserId) return
    try {
      setActionLoading((prev) => ({ ...prev, resetPassword: resetPasswordUserId }))
      setError(null)
      await userService.resetPasswordByAdmin(resetPasswordUserId)
      toast.success('Password reset email sent successfully')
      setResetPasswordDialogOpen(false)
      setResetPasswordUserId(null)
      setResetPasswordEmail('')
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
          (err as { message?: string })?.message ||
          'Failed to reset password'
      setError(errorMessage)
      toast.error(errorMessage)
    } finally {
      setActionLoading((prev) => ({ ...prev, resetPassword: null }))
    }
  }
  const handleResetPasswordCancel = () => {
    setResetPasswordDialogOpen(false)
    setResetPasswordUserId(null)
    setResetPasswordEmail('')
  }

  const handleDialogSuccess = (message?: string) => {
    setCreateDialogOpen(false)
    setEditDialogOpen(false)
    setInviteDialogOpen(false)
    setSelectedUser(null)
    if (message) {
      toast.success(message)
    }
    loadUsers()
  }

  const tableActions: UserTableActions = useMemo(
    () => ({
      onEdit: handleEditUser,
      onActivate: handleActivateClick,
      onDeactivate: handleDeactivateClick,
      onResetPassword: handleResetPasswordClick,
      isCurrentUser: (userId: number) => userId === currentUser?.id,
      canManageThisUser: (user: User) =>
        currentUser?.role && user.role ? canManageRole(currentUser.role, user.role) : false,
      isInactive: (user: User) => (user.status || '').toUpperCase() === 'INACTIVE',
      isDeactivating: (userId: number) => actionLoading.deactivate === userId,
      isActivating: (userId: number) => actionLoading.activate === userId,
      isResettingPassword: (userId: number) => actionLoading.resetPassword === userId,
    }),
    [currentUser?.id, currentUser?.role, actionLoading],
  )

  const columns = useMemo(
    () => createUserTableColumns(currentUser?.role, tableActions),
    [currentUser?.role, tableActions],
  )

  const table = useReactTable({
    data: users,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    manualPagination: true,
    pageCount: totalPages,
    state: {
      sorting,
    },
    onSortingChange: setSorting,
  })

  if (!canManageUsers()) {
    return (
      <RoleGuard requiredRoles={['admin', 'chief_accountant']}>
        <div />
      </RoleGuard>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold">{t('users.title')}</h1>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setInviteDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            {t('users.inviteUser')}
          </Button>
          <Button onClick={handleCreateUser}>
            <Plus className="mr-2 h-4 w-4" />
            {t('users.createUser')}
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 items-center mb-4">
        <div className="relative flex-1 min-w-[250px]">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={t('users.searchUsers')}
            value={searchTerm}
            onChange={(e) => handleSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select
          value={roleFilter || 'all'}
          onValueChange={(value) => {
            setRoleFilter(value === 'all' ? '' : value)
            setPage(1)
          }}
        >
          <SelectTrigger className="w-[150px]" aria-label="Role">
            <SelectValue placeholder={t('users.allRoles')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('users.allRoles')}</SelectItem>
            {ROLES.map((role) => (
              <SelectItem key={role} value={role}>
                {getRoleDisplayName(role)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={statusFilter || 'all'}
          onValueChange={(value) => {
            setStatusFilter(value === 'all' ? '' : value)
            setPage(1)
          }}
        >
          <SelectTrigger className="w-[150px]" aria-label="Status">
            <SelectValue placeholder={t('users.allStatuses')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('users.allStatuses')}</SelectItem>
            {STATUSES.map((status) => (
              <SelectItem key={status} value={status}>
                {status}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between">
            <span>{error}</span>
            <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => setError(null)}>
              <X className="h-4 w-4" />
            </Button>
          </AlertDescription>
        </Alert>
      )}

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id} className="bg-muted/50">
                {headerGroup.headers.map((header) => (
                  <TableHead key={header.id} className="relative h-10 select-none">
                    {header.isPlaceholder
                      ? null
                      : flexRender(header.column.columnDef.header, header.getContext())}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {loading ? (
              Array.from({ length: pageSize }).map((_, index) => (
                <TableRow key={`skeleton-${index}`}>
                  <TableCell>
                    <Skeleton className="h-4 w-full" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-full" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-6 w-24" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-6 w-20" />
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center justify-end gap-1">
                      <Skeleton className="h-8 w-8" />
                      <Skeleton className="h-8 w-8" />
                      <Skeleton className="h-8 w-8" />
                    </div>
                  </TableCell>
                </TableRow>
              ))
            ) : table.getRowModel().rows?.length ? (
              table.getRowModel().rows.map((row) => {
                const user = row.original
                const isInactive = (user.status || '').toUpperCase() === 'INACTIVE'
                return (
                  <TableRow
                    key={row.id}
                    data-state={row.getIsSelected() && 'selected'}
                    className={isInactive ? 'opacity-60 bg-muted/50' : ''}
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </TableCell>
                    ))}
                  </TableRow>
                )
              })
            ) : (
              <TableRow>
                <TableCell colSpan={columns.length} className="h-24 text-center">
                  No users found
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <div className="flex items-center justify-between border-t px-4 py-4">
          <div className="text-sm text-muted-foreground">
            Total: <strong className="text-foreground">{totalElements}</strong> users
          </div>
          <div className="flex items-center gap-6">
            <div className="hidden items-center gap-2 lg:flex">
              <Label htmlFor="rows-per-page" className="text-sm font-medium">
                Number of records per page
              </Label>
              <Select
                value={`${pageSize}`}
                onValueChange={(value) => {
                  setPageSize(Number(value))
                  setPage(1)
                }}
                disabled={loading}
              >
                <SelectTrigger size="sm" className="w-20" id="rows-per-page">
                  <SelectValue placeholder={pageSize} />
                </SelectTrigger>
                <SelectContent side="top">
                  {[10, 20, 30, 50, 100].map((size) => (
                    <SelectItem key={size} value={`${size}`}>
                      {size}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-center justify-center text-sm font-medium">
              Page {page} / {totalPages || 1}
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                className="hidden h-8 w-8 p-0 lg:flex"
                onClick={() => setPage(1)}
                disabled={page === 1 || loading}
              >
                <span className="sr-only">First page</span>
                <ChevronsLeft className="size-4" />
              </Button>
              <Button
                variant="outline"
                className="h-8 w-8"
                size="icon"
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page === 1 || loading}
              >
                <span className="sr-only">Previous page</span>
                <ChevronLeft className="size-4" />
              </Button>
              <Button
                variant="outline"
                className="h-8 w-8"
                size="icon"
                onClick={() => setPage((p) => Math.min(totalPages || 1, p + 1))}
                disabled={page >= (totalPages || 1) || loading}
              >
                <span className="sr-only">Next page</span>
                <ChevronRight className="size-4" />
              </Button>
              <Button
                variant="outline"
                className="hidden h-8 w-8 lg:flex"
                size="icon"
                onClick={() => setPage(totalPages || 1)}
                disabled={page >= (totalPages || 1) || loading}
              >
                <span className="sr-only">Last page</span>
                <ChevronsRight className="size-4" />
              </Button>
            </div>
          </div>
        </div>
      </div>

      <InviteUserDialog
        open={inviteDialogOpen}
        onClose={() => setInviteDialogOpen(false)}
        onSuccess={() => handleDialogSuccess('Invitation email sent successfully')}
      />
      <CreateUserDialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
        onSuccess={() => handleDialogSuccess('User created successfully')}
      />
      <EditUserDialog
        open={editDialogOpen}
        user={selectedUser}
        onClose={() => {
          setEditDialogOpen(false)
          setSelectedUser(null)
        }}
        onSuccess={() => handleDialogSuccess('User updated successfully')}
      />

      <Dialog
        open={resetPasswordDialogOpen}
        onOpenChange={(open) => !open && handleResetPasswordCancel()}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reset Password</DialogTitle>
            <DialogDescription>
              Send password reset email to <strong>{resetPasswordEmail}</strong>?<br />
              <br />A password reset link will be sent to their email address. The link will expire
              after 30 minutes.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={handleResetPasswordCancel}
              disabled={actionLoading.resetPassword !== null}
            >
              Cancel
            </Button>
            <Button
              onClick={handleResetPasswordConfirm}
              disabled={actionLoading.resetPassword !== null}
            >
              {actionLoading.resetPassword !== null ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Sending...
                </>
              ) : (
                'Send Reset Email'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
