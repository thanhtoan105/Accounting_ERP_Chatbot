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
  Search,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  X,
  Loader2,
  Check,
  ChevronsUpDown,
  Filter,
  Mail,
  UserPlus,
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
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
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
import { cn } from '@/lib/utils'

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

  // Combobox states
  const [roleOpen, setRoleOpen] = useState(false)
  const [statusOpen, setStatusOpen] = useState(false)

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

  const handleDeactivateClick = async (userId: number, _email: string) => {
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
  const handleActivateClick = async (userId: number, _email: string) => {
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
    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-500">
      <div className="voucher-page-header flex items-end justify-between pb-2">
        <div>
          <h1 className="voucher-font-heading text-3xl font-bold tracking-tight text-foreground">
            {t('users.title')}
          </h1>
          <p className="text-muted-foreground mt-1">Manage user access and permissions.</p>
        </div>
        <div className="flex gap-3">
          <Button
            variant="outline"
            className="voucher-button-secondary gap-2"
            onClick={() => setInviteDialogOpen(true)}
          >
            <Mail className="h-4 w-4" />
            {t('users.inviteUser')}
          </Button>
          <Button className="voucher-button-primary gap-2 shadow-sm" onClick={handleCreateUser}>
            <UserPlus className="h-4 w-4" />
            {t('users.createUser')}
          </Button>
        </div>
      </div>

      <div className="flex flex-col gap-4 md:flex-row md:items-center justify-between p-1 rounded-lg">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
          <Input
            placeholder={t('users.searchUsers')}
            value={searchTerm}
            onChange={(e) => handleSearch(e.target.value)}
            className="pl-9 h-9 border-[var(--border)] focus:ring-[var(--voucher-primary)] focus:border-[var(--voucher-primary)] bg-background rounded-md shadow-sm"
          />
        </div>

        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2 mr-2">
            <Filter className="h-4 w-4 text-muted-foreground" />
            <span className="text-sm font-medium text-muted-foreground">Filters:</span>
          </div>

          {/* Role Filter - Combobox Style */}
          <Popover open={roleOpen} onOpenChange={setRoleOpen}>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                role="combobox"
                aria-expanded={roleOpen}
                className="w-[180px] justify-between h-9 rounded-md border-dashed border-[var(--border)] hover:border-[var(--voucher-primary)] hover:text-[var(--voucher-primary)] transition-colors bg-background"
              >
                {roleFilter
                  ? getRoleDisplayName(ROLES.find((role) => role === roleFilter) || '')
                  : t('users.allRoles')}
                <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-[180px] p-0">
              <Command>
                <CommandInput placeholder="Search role..." />
                <CommandList>
                  <CommandEmpty>No role found.</CommandEmpty>
                  <CommandGroup>
                    <CommandItem
                      value="all"
                      onSelect={() => {
                        setRoleFilter('')
                        setPage(1)
                        setRoleOpen(false)
                      }}
                    >
                      <Check
                        className={cn('mr-2 h-4 w-4', !roleFilter ? 'opacity-100' : 'opacity-0')}
                      />
                      {t('users.allRoles')}
                    </CommandItem>
                    {ROLES.map((role) => (
                      <CommandItem
                        key={role}
                        value={role}
                        onSelect={(currentValue) => {
                          setRoleFilter(currentValue === roleFilter ? '' : currentValue)
                          setPage(1)
                          setRoleOpen(false)
                        }}
                      >
                        <Check
                          className={cn(
                            'mr-2 h-4 w-4',
                            roleFilter === role ? 'opacity-100' : 'opacity-0',
                          )}
                        />
                        {getRoleDisplayName(role)}
                      </CommandItem>
                    ))}
                  </CommandGroup>
                </CommandList>
              </Command>
            </PopoverContent>
          </Popover>

          {/* Status Filter - Combobox Style */}
          <Popover open={statusOpen} onOpenChange={setStatusOpen}>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                role="combobox"
                aria-expanded={statusOpen}
                className="w-[150px] justify-between h-9 rounded-md border-dashed border-[var(--border)] hover:border-[var(--voucher-primary)] hover:text-[var(--voucher-primary)] transition-colors bg-background"
              >
                {statusFilter
                  ? STATUSES.find((status) => status === statusFilter) || statusFilter
                  : t('users.allStatuses')}
                <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-[150px] p-0">
              <Command>
                <CommandInput placeholder="Search status..." />
                <CommandList>
                  <CommandEmpty>No status found.</CommandEmpty>
                  <CommandGroup>
                    <CommandItem
                      value="all"
                      onSelect={() => {
                        setStatusFilter('')
                        setPage(1)
                        setStatusOpen(false)
                      }}
                    >
                      <Check
                        className={cn('mr-2 h-4 w-4', !statusFilter ? 'opacity-100' : 'opacity-0')}
                      />
                      {t('users.allStatuses')}
                    </CommandItem>
                    {STATUSES.map((status) => (
                      <CommandItem
                        key={status}
                        value={status}
                        onSelect={(currentValue) => {
                          setStatusFilter(currentValue === statusFilter ? '' : currentValue)
                          setPage(1)
                          setStatusOpen(false)
                        }}
                      >
                        <Check
                          className={cn(
                            'mr-2 h-4 w-4',
                            statusFilter === status ? 'opacity-100' : 'opacity-0',
                          )}
                        />
                        {status}
                      </CommandItem>
                    ))}
                  </CommandGroup>
                </CommandList>
              </Command>
            </PopoverContent>
          </Popover>
        </div>
      </div>

      {error && (
        <Alert variant="destructive" className="animate-in fade-in slide-in-from-top-1">
          <AlertDescription className="flex items-center justify-between">
            <span>{error}</span>
            <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => setError(null)}>
              <X className="h-4 w-4" />
            </Button>
          </AlertDescription>
        </Alert>
      )}

      <div className="voucher-card relative flex flex-col rounded-xl border bg-card shadow-sm overflow-hidden">
        <div className="flex-1 overflow-auto relative flex flex-col">
          <Table className="flex-1">
            <TableHeader className="voucher-table-header-sticky">
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow
                  key={headerGroup.id}
                  className="hover:bg-transparent border-b-[var(--border)]"
                >
                  {headerGroup.headers.map((header) => (
                    <TableHead
                      key={header.id}
                      className="h-11 font-medium text-muted-foreground select-none"
                    >
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
                  <TableRow key={`skeleton-${index}`} className="border-b-[var(--border)]">
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <Skeleton className="h-9 w-9 rounded-full" />
                        <div className="space-y-1">
                          <Skeleton className="h-4 w-32" />
                          <Skeleton className="h-3 w-40" />
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-6 w-24 rounded-full" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-6 w-20 rounded-full" />
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end">
                        <Skeleton className="h-8 w-8 rounded-md" />
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
                      className={cn(
                        'voucher-row-interactive voucher-row-animate group border-b-[var(--border)] hover:bg-[var(--voucher-surface-1)] transition-colors',
                        isInactive && 'opacity-60 bg-muted/30',
                      )}
                      style={{
                        animationDelay: `${row.index * 50}ms`,
                        animationFillMode: 'backwards',
                      }}
                    >
                      {row.getVisibleCells().map((cell) => (
                        <TableCell key={cell.id} className="py-3">
                          {flexRender(cell.column.columnDef.cell, cell.getContext())}
                        </TableCell>
                      ))}
                    </TableRow>
                  )
                })
              ) : (
                <TableRow>
                  <TableCell colSpan={columns.length} className="h-32 text-center">
                    <div className="flex flex-col items-center justify-center text-muted-foreground">
                      <Search className="h-8 w-8 mb-2 opacity-50" />
                      <p>No users found matching your filters.</p>
                    </div>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>

          {/* Fixed Footer */}
          <div className="mt-auto border-t bg-background/80 px-4 py-3 text-sm backdrop-blur supports-[backdrop-filter]:bg-background/60 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2 text-muted-foreground">
              <span className="voucher-quick-stat voucher-quick-stat-draft bg-[var(--voucher-surface-2)] text-foreground">
                {totalElements}
              </span>
              users total
            </div>
            <div className="flex items-center gap-6">
              <div className="hidden items-center gap-2 lg:flex">
                <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                  Rows per page
                </span>
                <Select
                  value={`${pageSize}`}
                  onValueChange={(value) => {
                    setPageSize(Number(value))
                    setPage(1)
                  }}
                  disabled={loading}
                >
                  <SelectTrigger
                    size="sm"
                    className="h-8 w-[70px] bg-background border-[var(--border)]"
                  >
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

              <div className="flex items-center justify-center text-sm font-medium tabular-nums">
                Page {page} of {totalPages || 1}
              </div>

              <div className="flex items-center gap-1">
                <Button
                  variant="ghost"
                  className="hidden h-8 w-8 p-0 lg:flex hover:bg-[var(--voucher-surface-2)]"
                  onClick={() => setPage(1)}
                  disabled={page === 1 || loading}
                >
                  <span className="sr-only">First page</span>
                  <ChevronsLeft className="size-4" />
                </Button>
                <Button
                  variant="ghost"
                  className="h-8 w-8 p-0 hover:bg-[var(--voucher-surface-2)]"
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={page === 1 || loading}
                >
                  <span className="sr-only">Previous page</span>
                  <ChevronLeft className="size-4" />
                </Button>
                <Button
                  variant="ghost"
                  className="h-8 w-8 p-0 hover:bg-[var(--voucher-surface-2)]"
                  onClick={() => setPage((p) => Math.min(totalPages || 1, p + 1))}
                  disabled={page >= (totalPages || 1) || loading}
                >
                  <span className="sr-only">Next page</span>
                  <ChevronRight className="size-4" />
                </Button>
                <Button
                  variant="ghost"
                  className="hidden h-8 w-8 p-0 lg:flex hover:bg-[var(--voucher-surface-2)]"
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
