import { useState } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Typography,
  Alert,
  Box,
} from '@mui/material'
import type { VoucherListDTO } from '../../types/voucher'

interface DeleteVoucherDialogProps {
  open: boolean
  voucher: VoucherListDTO | null
  onClose: () => void
  onConfirm: (reason: string) => Promise<void>
}

/**
 * Dialog for confirming voucher deletion with reason input.
 */
export default function DeleteVoucherDialog({
  open,
  voucher,
  onClose,
  onConfirm,
}: DeleteVoucherDialogProps) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const handleClose = () => {
    if (!loading) {
      setReason('')
      setError(null)
      onClose()
    }
  }

  const handleConfirm = async () => {
    if (!reason.trim()) {
      setError('Deletion reason is required')
      return
    }

    if (!voucher) {
      return
    }

    try {
      setLoading(true)
      setError(null)
      await onConfirm(reason.trim())
      handleClose()
    } catch (err: any) {
      setError(err?.message || 'Failed to delete voucher. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  if (!voucher) {
    return null
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Delete Voucher</DialogTitle>
      <DialogContent>
        <Typography variant="body1" sx={{ mb: 2 }}>
          Are you sure you want to delete this voucher?
        </Typography>
        <Box sx={{ mb: 2, p: 2, bgcolor: 'background.default', borderRadius: 1 }}>
          <Typography variant="body2">
            <strong>Voucher Number:</strong> {voucher.voucherNumber}
          </Typography>
          <Typography variant="body2">
            <strong>Date:</strong> {new Date(voucher.voucherDate).toLocaleDateString()}
          </Typography>
          <Typography variant="body2">
            <strong>Type/Description:</strong> {voucher.type}
          </Typography>
          <Typography variant="body2">
            <strong>Status:</strong> {voucher.status}
          </Typography>
        </Box>
        <Alert severity="warning" sx={{ mb: 2 }}>
          Only draft vouchers that are not referenced can be deleted. This action cannot be undone.
        </Alert>
        <TextField
          autoFocus
          fullWidth
          multiline
          rows={3}
          label="Deletion Reason *"
          placeholder="Please provide a reason for deleting this voucher..."
          value={reason}
          onChange={(e) => {
            setReason(e.target.value)
            setError(null)
          }}
          error={!!error}
          helperText={error || 'This reason will be logged in the audit trail'}
          disabled={loading}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={loading}>
          Cancel
        </Button>
        <Button onClick={handleConfirm} color="error" variant="contained" disabled={loading}>
          {loading ? 'Deleting...' : 'Delete'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
