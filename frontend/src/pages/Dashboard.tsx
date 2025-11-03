import { Box, Typography, Paper, Stack } from '@mui/material'
import { useAuth } from '../hooks/useAuth'
import { useRole } from '../hooks/useRole'

/**
 * Dashboard component - main landing page after login
 */
export default function Dashboard() {
  const { user } = useAuth()
  const { getRoleDisplayName } = useRole()

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Dashboard
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
        Welcome, {user?.fullName || 'User'}!
      </Typography>

      <Stack spacing={3}>
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            User Information
          </Typography>
          <Stack spacing={1}>
            <Typography variant="body2">
              <strong>Email:</strong> {user?.email || 'N/A'}
            </Typography>
            <Typography variant="body2">
              <strong>Role:</strong> {getRoleDisplayName()}
            </Typography>
            <Typography variant="body2">
              <strong>Company ID:</strong>{' '}
              {user?.companyId ? user.companyId : 'No company assigned'}
            </Typography>
          </Stack>
        </Paper>

        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            Quick Actions
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Dashboard content will be expanded with accounting features such as:
          </Typography>
          <Stack component="ul" spacing={1} sx={{ mt: 2, pl: 3 }}>
            <li>Financial overview and summaries</li>
            <li>Recent transactions</li>
            <li>Pending approvals</li>
            <li>Reports and analytics</li>
          </Stack>
        </Paper>
      </Stack>
    </Box>
  )
}
