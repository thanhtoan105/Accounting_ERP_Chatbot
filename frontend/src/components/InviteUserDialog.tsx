import { useState } from 'react'
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
import { createInvitation, type CreateInvitationRequest } from '../services/invitation'
import { getRoleDisplayName, type Role } from '../utils/roles'

const ROLES: Role[] = ['admin', 'accountant', 'chief_accountant', 'cfo']

interface InviteUserDialogProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}

/**
 * Dialog component for inviting new users via email.
 */
export default function InviteUserDialog({ open, onClose, onSuccess }: InviteUserDialogProps) {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<Role>('accountant')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setSuccess(false)

    if (!email || !email.includes('@')) {
      setError('Please enter a valid email address')
      return
    }

    try {
      setLoading(true)
      const request: CreateInvitationRequest = {
        email,
        role: role !== 'accountant' ? role : undefined, // Don't send if default
      }
      await createInvitation(request)
      setSuccess(true)
      setTimeout(() => {
        handleClose()
        onSuccess()
      }, 1500)
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
            (err as { message?: string })?.message ||
            'Failed to send invitation'
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }

  const handleClose = () => {
    setEmail('')
    setRole('accountant')
    setError(null)
    setSuccess(false)
    onClose()
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Invite User</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            {error && (
              <Alert severity="error" onClose={() => setError(null)}>
                {error}
              </Alert>
            )}
            {success && <Alert severity="success">Invitation sent successfully!</Alert>}
            <TextField
              label="Email Address"
              type="email"
              fullWidth
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading}
            />
            <TextField
              select
              label="Role"
              fullWidth
              value={role}
              onChange={(e) => setRole(e.target.value as Role)}
              disabled={loading}
            >
              {ROLES.map((r) => (
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
            {loading ? 'Sending...' : 'Send Invitation'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
