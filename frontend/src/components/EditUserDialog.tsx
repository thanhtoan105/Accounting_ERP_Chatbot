import { useState, useEffect } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  MenuItem,
  Alert,
  Box,
  Typography,
} from '@mui/material'
import { updateUser, type UpdateUserRequest, getUserById } from '../services/user'
import { getRoleDisplayName, getAssignableRoles, canManageRole, type Role } from '../utils/roles'
import { useAuth } from '../hooks/useAuth'
import { useRole } from '../hooks/useRole'
import type { User } from '../services/auth'

const ALL_ROLES: Role[] = ['admin', 'accountant', 'chief_accountant', 'cfo']
const STATUSES = ['ACTIVE', 'INACTIVE', 'LOCKED']

interface EditUserDialogProps {
  open: boolean
  user: User | null
  onClose: () => void
  onSuccess: () => void
}

/**
 * Dialog component for editing a user (fullName, role, status).
 */
export default function EditUserDialog({ open, onClose, onSuccess, user }: EditUserDialogProps) {
  const { user: currentUser } = useAuth()
  const { canChangeRoles } = useRole()
  const [fullName, setFullName] = useState('')
  const [role, setRole] = useState<Role>('accountant')
  const [status, setStatus] = useState<string>('ACTIVE')
  const [loading, setLoading] = useState(false)
  const [fetching, setFetching] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isCurrentUser = user?.id === currentUser?.id
  const canEditRole = canChangeRoles() && !isCurrentUser
  const canEditStatus = canChangeRoles() && !isCurrentUser

  // Check if current user can manage the target user's role
  const canManageTargetUser =
    user && currentUser?.role ? canManageRole(currentUser.role, user.role) : false

  // Get assignable roles based on current user role
  const assignableRoles = getAssignableRoles(currentUser?.role)

  useEffect(() => {
    if (open && user) {
      setFullName(user.fullName || '')
      setRole((user.role?.toLowerCase() as Role) || 'accountant')
      setStatus(user.status?.toUpperCase() || 'ACTIVE')
      setError(null)
    }
  }, [open, user])

  // Fetch fresh user data if only ID is available
  useEffect(() => {
    if (open && user && !user.fullName) {
      const fetchUser = async () => {
        try {
          setFetching(true)
          const freshUser = await getUserById(user.id)
          setFullName(freshUser.fullName || '')
          setRole((freshUser.role?.toLowerCase() as Role) || 'accountant')
          setStatus(freshUser.status?.toUpperCase() || 'ACTIVE')
        } catch (err) {
          setError('Failed to load user data')
        } finally {
          setFetching(false)
        }
      }
      fetchUser()
    }
  }, [open, user])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!user) return

    setError(null)

    // Validation
    if (!fullName || fullName.trim().length === 0) {
      setError('Please enter a full name')
      return
    }

    try {
      setLoading(true)
      const request: UpdateUserRequest = {
        fullName: fullName.trim(),
      }

      // Only include role if it's different and user can edit it
      if (canEditRole && role !== user.role?.toLowerCase()) {
        request.role = role
      }

      // Only include status if it's different and user can edit it
      if (canEditStatus && status !== user.status?.toUpperCase()) {
        request.status = status
      }

      await updateUser(user.id, request)
      handleClose()
      onSuccess()
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
            (err as { message?: string })?.message ||
            'Failed to update user'
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }

  const handleClose = () => {
    setFullName('')
    setRole('accountant')
    setStatus('ACTIVE')
    setError(null)
    onClose()
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Edit User</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            {error && (
              <Alert severity="error" onClose={() => setError(null)}>
                {error}
              </Alert>
            )}

            {fetching ? (
              <Typography>Loading user data...</Typography>
            ) : (
              <>
                {user && (
                  <Typography variant="body2" color="text.secondary">
                    {user.email}
                  </Typography>
                )}

                {isCurrentUser && (
                  <Alert severity="info">You cannot change your own role or status.</Alert>
                )}

                {!isCurrentUser && !canManageTargetUser && (
                  <Alert severity="warning">
                    You do not have permission to manage users with this role.
                  </Alert>
                )}

                <TextField
                  label="Full Name"
                  fullWidth
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  disabled={loading || fetching}
                  autoFocus
                />

                <TextField
                  select
                  label="Role"
                  fullWidth
                  value={role}
                  onChange={(e) => setRole(e.target.value as Role)}
                  disabled={loading || fetching || !canEditRole || !canManageTargetUser}
                  helperText={
                    !canEditRole
                      ? 'You cannot change your own role'
                      : !canManageTargetUser
                        ? 'You cannot manage users with this role'
                        : undefined
                  }
                >
                  {assignableRoles.map((r) => (
                    <MenuItem key={r} value={r}>
                      {getRoleDisplayName(r)}
                    </MenuItem>
                  ))}
                </TextField>

                <TextField
                  select
                  label="Status"
                  fullWidth
                  value={status}
                  onChange={(e) => setStatus(e.target.value)}
                  disabled={loading || fetching || !canEditStatus || !canManageTargetUser}
                  helperText={
                    !canEditStatus
                      ? 'You cannot change your own status'
                      : !canManageTargetUser
                        ? 'You cannot manage users with this role'
                        : undefined
                  }
                >
                  {STATUSES.map((s) => (
                    <MenuItem key={s} value={s}>
                      {s}
                    </MenuItem>
                  ))}
                </TextField>
              </>
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} disabled={loading || fetching}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={loading || fetching}>
            {loading ? 'Saving...' : 'Save'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
