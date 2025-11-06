import type { ColumnDef } from '@tanstack/react-table'
import { ArrowUpDown, Edit, Ban, CheckCircle2, LockKeyhole, MoreVertical } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { getRoleDisplayName, canManageRole, type Role } from '@/utils/roles'
import type { User } from '@/services/auth'

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

const getStatusColor = (
  status?: string,
): 'default' | 'secondary' | 'destructive' | 'success' | 'warning' => {
  switch ((status || '').toUpperCase()) {
    case 'ACTIVE':
      return 'success'
    case 'INACTIVE':
      return 'destructive'
    case 'LOCKED':
      return 'warning'
    default:
      return 'default'
  }
}

const getStatusDisplayName = (status?: string): string => {
  return status?.toUpperCase() || 'UNKNOWN'
}

export function createUserTableColumns(
  currentUserRole: string | null | undefined,
  actions: UserTableActions,
): ColumnDef<User>[] {
  return [
    {
      accessorKey: 'email',
      header: ({ column }) => {
        return (
          <Button
            variant="ghost"
            onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
            className="h-8 px-2 lg:px-3"
          >
            Email
            <ArrowUpDown className="ml-2 h-4 w-4" />
          </Button>
        )
      },
      cell: ({ row }) => <div className="lowercase">{row.getValue('email')}</div>,
    },
    {
      accessorKey: 'fullName',
      header: ({ column }) => {
        return (
          <Button
            variant="ghost"
            onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
            className="h-8 px-2 lg:px-3"
          >
            Full Name
            <ArrowUpDown className="ml-2 h-4 w-4" />
          </Button>
        )
      },
      cell: ({ row }) => <div>{row.getValue('fullName')}</div>,
    },
    {
      accessorKey: 'role',
      header: 'Role',
      cell: ({ row }) => {
        const role = row.getValue('role') as string
        return (
          <Badge variant="secondary" className="font-medium">
            {getRoleDisplayName(role)}
          </Badge>
        )
      },
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => {
        const status = row.getValue('status') as string | undefined
        const user = row.original
        const isInactive = actions.isInactive(user)
        const isDeactivating = actions.isDeactivating(user.id)
        const isActivating = actions.isActivating(user.id)
        const isLoading = isDeactivating || isActivating
        const canManageThisUser = actions.canManageThisUser(user)
        const isCurrentUser = actions.isCurrentUser(user.id)

        const checked = !isInactive

        return (
          <div className="flex items-center gap-2">
            <Switch
              checked={checked}
              onCheckedChange={(next) => {
                if (next) {
                  actions.onActivate(user.id, user.email)
                } else {
                  actions.onDeactivate(user.id, user.email)
                }
              }}
              disabled={isLoading || !canManageThisUser || isCurrentUser}
              aria-label={checked ? 'Active' : 'Inactive'}
            />
            <span className="text-xs text-muted-foreground">{checked ? 'Active' : 'Inactive'}</span>
          </div>
        )
      },
    },
    {
      id: 'actions',
      header: () => <div className="flex w-full justify-end pr-2">Actions</div>,
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

        return (
          <div className="flex items-center justify-end h-10 w-full pr-2 min-w-[44px]">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8"
                  disabled={!canManageThisUser}
                >
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-48">
                <DropdownMenuLabel>Actions</DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onClick={() => actions.onEdit(user)}
                  disabled={isLoading || !canManageThisUser || isInactive}
                >
                  <Edit className="mr-2 h-4 w-4" /> Edit
                </DropdownMenuItem>

                <DropdownMenuItem
                  onClick={() => actions.onResetPassword(user.id, user.email)}
                  disabled={isLoading || isCurrentUser || !canManageThisUser || isInactive}
                >
                  {isResettingPassword ? (
                    <div className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                  ) : (
                    <LockKeyhole className="mr-2 h-4 w-4" />
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
