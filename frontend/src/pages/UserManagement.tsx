import { useState, useEffect, useCallback } from 'react'
import {
  Box,
  Button,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Chip,
  IconButton,
  Alert,
  TextField,
  MenuItem,
  InputAdornment,
  Pagination,
  CircularProgress,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import EditIcon from '@mui/icons-material/Edit'
import SearchIcon from '@mui/icons-material/Search'
import BlockIcon from '@mui/icons-material/Block'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import LockResetIcon from '@mui/icons-material/LockReset'
import { useRole } from '../hooks/useRole'
import { useAuth } from '../hooks/useAuth'
import RoleGuard from '../components/RoleGuard'
import { getRoleDisplayName, canManageRole, type Role } from '../utils/roles'
import InviteUserDialog from '../components/InviteUserDialog'
import CreateUserDialog from '../components/CreateUserDialog'
import EditUserDialog from '../components/EditUserDialog'
import * as userService from '../services/user'
import type { User } from '../services/auth'

const ROLES: Role[] = ['admin', 'accountant', 'chief_accountant', 'cfo']
const STATUSES = ['ACTIVE', 'INACTIVE', 'LOCKED']
const PAGE_SIZE = 20

/**
 * User Management page for admins and chief accountants.
 * Allows viewing users with filters, creating/editing users, deactivating/activating,
 * and resetting passwords.
 */
export default function UserManagement() {
  const { canChangeRoles, canManageUsers } = useRole()
  const { user: currentUser } = useAuth()

  // State
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  // Filters and pagination
  const [roleFilter, setRoleFilter] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [page, setPage] = useState(1)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)

  // Dialogs
  const [inviteDialogOpen, setInviteDialogOpen] = useState(false)
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [selectedUser, setSelectedUser] = useState<User | null>(null)

  // Actions - track loading per action type and user ID
  const [actionLoading, setActionLoading] = useState<Record<string, number | null>>({
    deactivate: null,
    activate: null,
    resetPassword: null,
  })

  // Reset password dialog state
  const [resetPasswordDialogOpen, setResetPasswordDialogOpen] = useState(false)
  const [resetPasswordUserId, setResetPasswordUserId] = useState<number | null>(null)
  const [resetPasswordEmail, setResetPasswordEmail] = useState<string>('')

  // Activate/Deactivate dialog state
  const [activateDeactivateDialogOpen, setActivateDeactivateDialogOpen] = useState(false)
  const [activateDeactivateUserId, setActivateDeactivateUserId] = useState<number | null>(null)
  const [activateDeactivateEmail, setActivateDeactivateEmail] = useState<string>('')
  const [activateDeactivateAction, setActivateDeactivateAction] = useState<
    'activate' | 'deactivate' | null
  >(null)

  const loadUsers = useCallback(async () => {
    let requestSucceeded = false
    try {
      setLoading(true)
      setError(null)
      const params: userService.GetAllUsersParams = {
        page: page - 1, // Backend uses 0-based pagination
        size: PAGE_SIZE,
      }
      if (roleFilter) params.role = roleFilter
      if (statusFilter) params.status = statusFilter
      if (searchTerm.trim()) params.search = searchTerm.trim()

      const response = await userService.getAllUsers(params)
      requestSucceeded = true
      setUsers(response.data)
      setTotalPages(response.totalPages)
      setTotalElements(response.totalElements)
      // Clear any transient errors if request succeeded
      setError(null)
    } catch (err: any) {
      // Only show error if request didn't succeed (retry failed)
      if (!requestSucceeded) {
        // Check if this is an auth error - if retry failed, show error
        if (
          err?.code === 'UNAUTHORIZED' ||
          err?.code === 'FORBIDDEN' ||
          err?.status === 401 ||
          err?.status === 403
        ) {
          // This means the retry also failed or refresh token is invalid - show error
          const errorMessage =
            err instanceof Error
              ? err.message
              : err?.message || 'Authentication failed. Please try again.'
          setError(errorMessage)
        } else {
          // Other errors - show normally
          const errorMessage =
            err instanceof Error
              ? err.message
              : (err as { error?: { message?: string }; message?: string })?.error?.message ||
                (err as { message?: string })?.message ||
                'Failed to load users'
          setError(errorMessage)
        }
      }
      // If requestSucceeded is true, don't set error (retry worked)
    } finally {
      setLoading(false)
    }
  }, [page, roleFilter, statusFilter, searchTerm])

  useEffect(() => {
    loadUsers()
  }, [loadUsers])

  const handleFilterChange = () => {
    setPage(1) // Reset to first page when filters change
  }

  useEffect(() => {
    handleFilterChange()
  }, [roleFilter, statusFilter])

  const handleSearch = (value: string) => {
    setSearchTerm(value)
    setPage(1)
  }

  const handleCreateUser = () => {
    setCreateDialogOpen(true)
  }

  const handleEditUser = (user: User) => {
    setSelectedUser(user)
    setEditDialogOpen(true)
  }

  const handleDeactivateClick = (userId: number, email: string) => {
    setActivateDeactivateUserId(userId)
    setActivateDeactivateEmail(email)
    setActivateDeactivateAction('deactivate')
    setActivateDeactivateDialogOpen(true)
  }

  const handleActivateClick = (userId: number, email: string) => {
    setActivateDeactivateUserId(userId)
    setActivateDeactivateEmail(email)
    setActivateDeactivateAction('activate')
    setActivateDeactivateDialogOpen(true)
  }

  const handleActivateDeactivateConfirm = async () => {
    if (!activateDeactivateUserId || !activateDeactivateAction) return

    try {
      const userId = activateDeactivateUserId
      const isActivating = activateDeactivateAction === 'activate'

      setActionLoading((prev) => ({ ...prev, [activateDeactivateAction]: userId }))
      setError(null)

      if (isActivating) {
        await userService.activateUser(userId)
        setSuccess('User activated successfully')
      } else {
        await userService.deactivateUser(userId)
        setSuccess('User deactivated successfully')
      }

      await loadUsers()
      setActivateDeactivateDialogOpen(false)
      setActivateDeactivateUserId(null)
      setActivateDeactivateEmail('')
      setActivateDeactivateAction(null)
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
            (err as { message?: string })?.message ||
            `Failed to ${activateDeactivateAction} user`
      setError(errorMessage)
    } finally {
      setActionLoading((prev) => ({ ...prev, [activateDeactivateAction!]: null }))
    }
  }

  const handleActivateDeactivateCancel = () => {
    setActivateDeactivateDialogOpen(false)
    setActivateDeactivateUserId(null)
    setActivateDeactivateEmail('')
    setActivateDeactivateAction(null)
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
      setSuccess('Password reset email sent successfully')
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
    } finally {
      setActionLoading((prev) => ({ ...prev, resetPassword: null }))
    }
  }

  const handleResetPasswordCancel = () => {
    setResetPasswordDialogOpen(false)
    setResetPasswordUserId(null)
    setResetPasswordEmail('')
  }

  const handleDialogSuccess = () => {
    setCreateDialogOpen(false)
    setEditDialogOpen(false)
    setInviteDialogOpen(false)
    setSelectedUser(null)
    loadUsers()
  }

  const getStatusColor = (
    status?: string,
  ): 'default' | 'primary' | 'success' | 'warning' | 'error' => {
    switch (status?.toUpperCase()) {
      case 'ACTIVE':
        return 'success'
      case 'INACTIVE':
        return 'error'
      case 'LOCKED':
        return 'warning'
      default:
        return 'default'
    }
  }

  const getStatusDisplayName = (status?: string): string => {
    return status?.toUpperCase() || 'UNKNOWN'
  }

  // Only show if user can manage users
  if (!canManageUsers()) {
    return (
      <RoleGuard requiredRoles={['admin', 'chief_accountant']}>
        <div />
      </RoleGuard>
    )
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">User Management</Typography>
        <Box display="flex" gap={2}>
          <Button
            variant="outlined"
            startIcon={<AddIcon />}
            onClick={() => setInviteDialogOpen(true)}
          >
            Invite User
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleCreateUser}>
            Create User
          </Button>
        </Box>
      </Box>

      {/* Filters */}
      <Paper sx={{ p: 2, mb: 2 }}>
        <Box display="flex" gap={2} flexWrap="wrap" alignItems="center">
          <TextField
            size="small"
            placeholder="Search by email or name..."
            value={searchTerm}
            onChange={(e) => handleSearch(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
            }}
            sx={{ minWidth: 250 }}
          />
          <TextField
            size="small"
            select
            label="Role"
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            sx={{ minWidth: 150 }}
          >
            <MenuItem value="">All Roles</MenuItem>
            {ROLES.map((role) => (
              <MenuItem key={role} value={role}>
                {getRoleDisplayName(role)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            size="small"
            select
            label="Status"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            sx={{ minWidth: 150 }}
          >
            <MenuItem value="">All Statuses</MenuItem>
            {STATUSES.map((status) => (
              <MenuItem key={status} value={status}>
                {status}
              </MenuItem>
            ))}
          </TextField>
        </Box>
      </Paper>

      {/* Error Alert */}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Success Alert */}
      {success && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Email</TableCell>
              <TableCell>Full Name</TableCell>
              <TableCell>Role Badge</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  <CircularProgress />
                </TableCell>
              </TableRow>
            ) : users.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  No users found
                </TableCell>
              </TableRow>
            ) : (
              users.map((user) => {
                const isCurrentUser = user.id === currentUser?.id
                const isInactive = user.status?.toUpperCase() === 'INACTIVE'
                const isDeactivating = actionLoading.deactivate === user.id
                const isActivating = actionLoading.activate === user.id
                const isResettingPassword = actionLoading.resetPassword === user.id
                const isLoading = isDeactivating || isActivating || isResettingPassword

                // Check if current user can manage this user's role
                const canManageThisUser =
                  currentUser?.role && user.role
                    ? canManageRole(currentUser.role, user.role)
                    : false

                return (
                  <TableRow
                    key={user.id}
                    sx={{
                      opacity: isInactive ? 0.6 : 1,
                      backgroundColor: isInactive ? 'action.hover' : 'inherit',
                    }}
                  >
                    <TableCell>{user.email}</TableCell>
                    <TableCell>{user.fullName}</TableCell>
                    <TableCell>
                      <Chip label={getRoleDisplayName(user.role)} size="small" />
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={getStatusDisplayName(user.status)}
                        size="small"
                        color={getStatusColor(user.status)}
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Box display="flex" gap={1} justifyContent="flex-end">
                        <Tooltip
                          title={
                            isInactive
                              ? 'Cannot edit inactive users. Activate them first.'
                              : canManageThisUser
                                ? 'Edit User'
                                : 'You cannot manage this user'
                          }
                        >
                          <span>
                            <IconButton
                              size="small"
                              onClick={() => handleEditUser(user)}
                              disabled={isLoading || !canManageThisUser || isInactive}
                            >
                              <EditIcon />
                            </IconButton>
                          </span>
                        </Tooltip>
                        {isInactive ? (
                          <Tooltip
                            title={
                              canManageThisUser ? 'Activate User' : 'You cannot manage this user'
                            }
                          >
                            <span>
                              <IconButton
                                size="small"
                                onClick={() => handleActivateClick(user.id, user.email)}
                                disabled={isLoading || isCurrentUser || !canManageThisUser}
                                color="success"
                              >
                                {isActivating ? (
                                  <CircularProgress size={20} />
                                ) : (
                                  <CheckCircleIcon />
                                )}
                              </IconButton>
                            </span>
                          </Tooltip>
                        ) : (
                          <Tooltip
                            title={
                              canManageThisUser ? 'Deactivate User' : 'You cannot manage this user'
                            }
                          >
                            <span>
                              <IconButton
                                size="small"
                                onClick={() => handleDeactivateClick(user.id, user.email)}
                                disabled={isLoading || isCurrentUser || !canManageThisUser}
                                color="error"
                              >
                                {isDeactivating ? <CircularProgress size={20} /> : <BlockIcon />}
                              </IconButton>
                            </span>
                          </Tooltip>
                        )}
                        <Tooltip
                          title={
                            isInactive
                              ? 'Cannot reset password for inactive users. Activate them first.'
                              : canManageThisUser
                                ? 'Reset Password'
                                : 'You cannot manage this user'
                          }
                        >
                          <span>
                            <IconButton
                              size="small"
                              onClick={() => handleResetPasswordClick(user.id, user.email)}
                              disabled={
                                isLoading || isCurrentUser || !canManageThisUser || isInactive
                              }
                              color="primary"
                            >
                              {isResettingPassword ? (
                                <CircularProgress size={20} />
                              ) : (
                                <LockResetIcon />
                              )}
                            </IconButton>
                          </span>
                        </Tooltip>
                      </Box>
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Pagination */}
      {totalPages > 1 && (
        <Box display="flex" justifyContent="center" mt={3}>
          <Pagination
            count={totalPages}
            page={page}
            onChange={(_, newPage) => setPage(newPage)}
            color="primary"
          />
        </Box>
      )}

      {/* Summary */}
      <Typography variant="body2" color="text.secondary" mt={2} textAlign="center">
        Showing {users.length} of {totalElements} users
      </Typography>

      {/* Dialogs */}
      <InviteUserDialog
        open={inviteDialogOpen}
        onClose={() => setInviteDialogOpen(false)}
        onSuccess={handleDialogSuccess}
      />

      <CreateUserDialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
        onSuccess={handleDialogSuccess}
      />

      <EditUserDialog
        open={editDialogOpen}
        user={selectedUser}
        onClose={() => {
          setEditDialogOpen(false)
          setSelectedUser(null)
        }}
        onSuccess={handleDialogSuccess}
      />

      {/* Activate/Deactivate Confirmation Dialog */}
      <Dialog
        open={activateDeactivateDialogOpen}
        onClose={handleActivateDeactivateCancel}
        aria-labelledby="activate-deactivate-dialog-title"
        aria-describedby="activate-deactivate-dialog-description"
      >
        <DialogTitle id="activate-deactivate-dialog-title">
          {activateDeactivateAction === 'activate' ? 'Activate User' : 'Deactivate User'}
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="activate-deactivate-dialog-description">
            {activateDeactivateAction === 'activate' ? (
              <>
                Activate user <strong>{activateDeactivateEmail}</strong>?
                <br />
                <br />
                This will allow them to log in and access the system again.
              </>
            ) : (
              <>
                Deactivate user <strong>{activateDeactivateEmail}</strong>?
                <br />
                <br />
                They will not be able to log in until their account is reactivated.
              </>
            )}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={handleActivateDeactivateCancel}
            disabled={
              (activateDeactivateAction === 'activate' && actionLoading.activate !== null) ||
              (activateDeactivateAction === 'deactivate' && actionLoading.deactivate !== null)
            }
          >
            Cancel
          </Button>
          <Button
            onClick={handleActivateDeactivateConfirm}
            variant="contained"
            color={activateDeactivateAction === 'activate' ? 'success' : 'error'}
            disabled={
              (activateDeactivateAction === 'activate' && actionLoading.activate !== null) ||
              (activateDeactivateAction === 'deactivate' && actionLoading.deactivate !== null)
            }
            autoFocus
            startIcon={
              (activateDeactivateAction === 'activate' && actionLoading.activate !== null) ||
              (activateDeactivateAction === 'deactivate' && actionLoading.deactivate !== null) ? (
                <CircularProgress size={16} />
              ) : undefined
            }
          >
            {activateDeactivateAction === 'activate'
              ? actionLoading.activate !== null
                ? 'Activating...'
                : 'Activate'
              : actionLoading.deactivate !== null
                ? 'Deactivating...'
                : 'Deactivate'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reset Password Confirmation Dialog */}
      <Dialog
        open={resetPasswordDialogOpen}
        onClose={handleResetPasswordCancel}
        aria-labelledby="reset-password-dialog-title"
        aria-describedby="reset-password-dialog-description"
      >
        <DialogTitle id="reset-password-dialog-title">Reset Password</DialogTitle>
        <DialogContent>
          <DialogContentText id="reset-password-dialog-description">
            Send password reset email to <strong>{resetPasswordEmail}</strong>?
            <br />
            <br />A password reset link will be sent to their email address. The link will expire
            after 30 minutes.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={handleResetPasswordCancel}
            disabled={actionLoading.resetPassword !== null}
          >
            Cancel
          </Button>
          <Button
            onClick={handleResetPasswordConfirm}
            variant="contained"
            disabled={actionLoading.resetPassword !== null}
            autoFocus
            startIcon={
              actionLoading.resetPassword !== null ? <CircularProgress size={16} /> : undefined
            }
          >
            {actionLoading.resetPassword !== null ? 'Sending...' : 'Send Reset Email'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
