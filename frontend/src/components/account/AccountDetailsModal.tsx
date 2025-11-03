import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Chip,
  Grid,
  Divider,
} from '@mui/material'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import type { ChartOfAccountHierarchy } from '../../types/chartOfAccount'

interface AccountDetailsModalProps {
  open: boolean
  onClose: () => void
  account: ChartOfAccountHierarchy
}

/**
 * Modal displaying all account details including code, name, type, normal_side, postable flag, etc.
 */
export default function AccountDetailsModal({ open, onClose, account }: AccountDetailsModalProps) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="h6">Account Details</Typography>
          {account.postable && (
            <Chip icon={<CheckCircleIcon />} label="Postable" color="success" size="small" />
          )}
        </Box>
      </DialogTitle>
      <DialogContent>
        <Grid container spacing={2} sx={{ mt: 1 }}>
          <Grid item xs={12}>
            <Typography variant="caption" color="text.secondary">
              Account Code
            </Typography>
            <Typography variant="body1" sx={{ fontFamily: 'monospace', fontWeight: 'bold' }}>
              {account.code}
            </Typography>
          </Grid>

          <Grid item xs={12}>
            <Typography variant="caption" color="text.secondary">
              Account Name
            </Typography>
            <Typography variant="body1">{account.name}</Typography>
          </Grid>

          <Grid item xs={6}>
            <Typography variant="caption" color="text.secondary">
              Type
            </Typography>
            <Box sx={{ mt: 0.5 }}>
              <Chip
                label={account.type}
                size="small"
                color={account.type === 'Asset' ? 'primary' : 'default'}
              />
            </Box>
          </Grid>

          <Grid item xs={6}>
            <Typography variant="caption" color="text.secondary">
              Normal Side
            </Typography>
            <Box sx={{ mt: 0.5 }}>
              <Chip
                label={account.normalSide}
                size="small"
                color={account.normalSide === 'Debit' ? 'primary' : 'secondary'}
              />
            </Box>
          </Grid>

          <Grid item xs={6}>
            <Typography variant="caption" color="text.secondary">
              Postable
            </Typography>
            <Typography variant="body2" sx={{ mt: 0.5 }}>
              {account.postable ? 'Yes' : 'No'}
            </Typography>
          </Grid>

          <Grid item xs={6}>
            <Typography variant="caption" color="text.secondary">
              Ordering Position
            </Typography>
            <Typography variant="body2" sx={{ mt: 0.5 }}>
              {account.orderingPosition}
            </Typography>
          </Grid>

          {account.parentCode && (
            <Grid item xs={12}>
              <Typography variant="caption" color="text.secondary">
                Parent Account
              </Typography>
              <Typography variant="body2" sx={{ mt: 0.5, fontFamily: 'monospace' }}>
                {account.parentCode}
              </Typography>
            </Grid>
          )}

          {account.balance !== undefined && account.balance !== null && (
            <>
              <Divider sx={{ width: '100%', my: 1 }} />
              <Grid item xs={12}>
                <Typography variant="caption" color="text.secondary">
                  Current Balance
                </Typography>
                <Typography variant="h6" sx={{ mt: 0.5, fontWeight: 'bold' }}>
                  {account.balance.toLocaleString('vi-VN')} VND
                </Typography>
              </Grid>
            </>
          )}
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  )
}
