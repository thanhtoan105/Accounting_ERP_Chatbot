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
} from '@mui/material'
import { createUser, type CreateUserRequest } from '../services/user'
import { getRoleDisplayName, getAssignableRoles, type Role } from '../utils/roles'
import { useAuth } from '../hooks/useAuth'

const ALL_ROLES: Role[] = ['admin', 'accountant', 'chief_accountant', 'cfo']

interface CreateUserDialogProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}

/**
 * Dialog component for creating a new user directly (with password).
 */
export default function CreateUserDialog({ open, onClose, onSuccess }: CreateUserDialogProps) {
  const { user: currentUser } = useAuth()
  const assignableRoles = getAssignableRoles(currentUser?.role)
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>(assignableRoles[0] || 'accountant')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Update role when assignable roles change
  useEffect(() => {
    if (assignableRoles.length > 0 && !assignableRoles.includes(role)) {
      setRole(assignableRoles[0])
    }
  }, [assignableRoles, role])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    // Validation
    if (!email || !email.includes('@')) {
      setError('Please enter a valid email address')
      return
    }
    if (!fullName || fullName.trim().length === 0) {
      setError('Please enter a full name')
      return
    }
    if (!password || password.length < 8) {
      setError('Password must be at least 8 characters')
      return
    }

    try {
      setLoading(true)
      const request: CreateUserRequest = {
        email: email.trim().toLowerCase(),
        fullName: fullName.trim(),
        password,
        role: role !== 'accountant' ? role : undefined, // Don't send if default
      }
      await createUser(request)
      handleClose()
      onSuccess()
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
            (err as { message?: string })?.message ||
            'Failed to create user'
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }

  const handleClose = () => {
    setEmail('')
    setFullName('')
    setPassword('')
    setRole('accountant')
    setError(null)
    onClose()
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Create User</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            {error && (
              <Alert severity="error" onClose={() => setError(null)}>
                {error}
              </Alert>
            )}
            <TextField
              label="Email Address"
              type="email"
              fullWidth
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading}
              autoFocus
            />
            <TextField
              label="Full Name"
              fullWidth
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              disabled={loading}
            />
            <TextField
              label="Password"
              type="password"
              fullWidth
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
              helperText="Must be at least 8 characters"
            />
            <TextField
              select
              label="Role"
              fullWidth
              value={role}
              onChange={(e) => setRole(e.target.value as Role)}
              disabled={loading || assignableRoles.length === 0}
              helperText={
                assignableRoles.length === 0
                  ? 'You do not have permission to assign roles'
                  : undefined
              }
            >
              {assignableRoles.map((r) => (
                <MenuItem key={r} value={r}>
                  {getRoleDisplayName(r)}
                </MenuItem>
              ))}
            </TextField>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} disabled={loading}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={loading}>
            {loading ? 'Creating...' : 'Create User'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
