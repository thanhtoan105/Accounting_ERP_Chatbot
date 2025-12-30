import type { ColumnDef } from '@tanstack/react-table'
import {
  ArrowUpDown,
  Edit,
  LockKeyhole,
  MoreVertical,
  ShieldCheck,
  Mail,
  User as UserIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { getRoleDisplayName } from '@/utils/roles'
import type { User } from '@/services/auth'
import { cn } from '@/lib/utils'

export type UserTableActions = {
  onEdit: (user: User) => void
  onActivate: (userId: number, email: string) => void
  onDeactivate: (userId: number, email: string) => void
  onResetPassword: (userId: number, email: string) => void
  isCurrentUser: (userId: number) => boolean
  canManageThisUser: (user: User) => boolean
  isInactive: (user: User) => boolean
  isDeactivating: (userId: number) => boolean
  isActivating: (userId: number) => boolean
  isResettingPassword: (userId: number) => boolean
}

export function createUserTableColumns(
  _currentUserRole: string | null | undefined,
  actions: UserTableActions,
): ColumnDef<User>[] {
  return [
    {
      accessorKey: 'fullName',
      header: ({ column }) => {
        return (
          <div
            className="flex cursor-pointer items-center gap-1 font-semibold text-muted-foreground hover:text-foreground transition-colors uppercase text-xs tracking-wider"
            onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
          >
            User
            <ArrowUpDown className="h-3 w-3" />
          </div>
        )
      },
      cell: ({ row }) => {
        const user = row.original
        return (
          <div className="flex items-center gap-3 py-1">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-slate-600 shadow-sm border border-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:border-slate-700">
              <UserIcon className="h-4 w-4" />
            </div>
            <div className="flex flex-col">
              <span className="font-medium text-foreground text-sm">{user.fullName}</span>
              <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                <Mail className="h-3 w-3" />
                {user.email}
              </span>
            </div>
          </div>
        )
      },
    },
    {
      accessorKey: 'role',
      header: 'Role',
      cell: ({ row }) => {
        const role = row.getValue('role') as string
        const isHighPrivilege = ['admin', 'cfo', 'chief_accountant'].includes(role)

        return (
          <div
            className={cn(
              'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium border transition-all voucher-badge-animate',
              isHighPrivilege
                ? 'bg-purple-50 text-purple-700 border-purple-200 dark:bg-purple-950/30 dark:text-purple-400 dark:border-purple-800'
                : 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/30 dark:text-blue-400 dark:border-blue-800',
            )}
          >
            {isHighPrivilege && <ShieldCheck className="h-3 w-3" />}
            {getRoleDisplayName(role)}
          </div>
        )
      },
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => {
        const user = row.original
        const isInactive = actions.isInactive(user)
        const isDeactivating = actions.isDeactivating(user.id)
        const isActivating = actions.isActivating(user.id)
        const isLoading = isDeactivating || isActivating
        const canManageThisUser = actions.canManageThisUser(user)
        const isCurrentUser = actions.isCurrentUser(user.id)

        const isActive = !isInactive

        return (
          <div className="flex items-center gap-3">
            <div
              className={cn(
                'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium border tabular-nums',
                isActive
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/30 dark:text-emerald-400 dark:border-emerald-800'
                  : 'bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:border-slate-700',
              )}
            >
              <div
                className={cn(
                  'h-1.5 w-1.5 rounded-full',
                  isActive ? 'bg-emerald-500' : 'bg-slate-400',
                )}
              />
              {isActive ? 'Active' : 'Inactive'}
            </div>

            {canManageThisUser && !isCurrentUser && (
              <Switch
                checked={isActive}
                onCheckedChange={(next) => {
                  if (next) {
                    actions.onActivate(user.id, user.email)
                  } else {
                    actions.onDeactivate(user.id, user.email)
                  }
                }}
                disabled={isLoading}
                className={cn(
                  'scale-75 origin-left data-[state=checked]:bg-emerald-500',
                  isLoading && 'opacity-50 cursor-not-allowed',
                )}
                aria-label={isActive ? 'Deactivate user' : 'Activate user'}
              />
            )}
          </div>
        )
      },
    },
    {
      id: 'actions',
      header: () => <div className="w-full text-right pr-4"></div>,
      enableHiding: false,
      cell: ({ row }) => {
        const user = row.original
        const isCurrentUser = actions.isCurrentUser(user.id)
        const isInactive = actions.isInactive(user)
        const canManageThisUser = actions.canManageThisUser(user)
        const isDeactivating = actions.isDeactivating(user.id)
        const isActivating = actions.isActivating(user.id)
        const isResettingPassword = actions.isResettingPassword(user.id)
        const isLoading = isDeactivating || isActivating || isResettingPassword

        if (!canManageThisUser) return null

        return (
          <div className="flex items-center justify-end">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 text-muted-foreground hover:text-foreground hover:bg-[var(--voucher-surface-2)]"
                >
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-48 shadow-lg border-[var(--border)]">
                <DropdownMenuLabel className="text-xs text-muted-foreground font-normal uppercase tracking-wider">
                  User Actions
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onClick={() => actions.onEdit(user)}
                  disabled={isLoading || isInactive}
                  className="cursor-pointer"
                >
                  <Edit className="mr-2 h-4 w-4 text-[var(--voucher-primary)]" />
                  Edit Details
                </DropdownMenuItem>

                <DropdownMenuItem
                  onClick={() => actions.onResetPassword(user.id, user.email)}
                  disabled={isLoading || isCurrentUser || isInactive}
                  className="cursor-pointer"
                >
                  {isResettingPassword ? (
                    <div className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                  ) : (
                    <LockKeyhole className="mr-2 h-4 w-4 text-[var(--voucher-teal)]" />
                  )}
                  Reset Password
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        )
      },
    },
  ]
}
