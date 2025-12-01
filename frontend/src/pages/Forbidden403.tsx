import { useTranslation } from 'react-i18next'
import { Box, Button, Stack, Typography } from '@mui/material'
import { useNavigate } from 'react-router-dom'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'

interface Forbidden403Props {
  message?: string
}

/**
 * 403 Forbidden error page component.
 * Displays when user tries to access a resource they don't have permission for.
 */
export default function Forbidden403({ message }: Forbidden403Props) {
  const { t } = useTranslation()
  const navigate = useNavigate()

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '60vh',
        p: 3,
      }}
    >
      <Stack spacing={3} alignItems="center" sx={{ maxWidth: 500, textAlign: 'center' }}>
        <LockOutlinedIcon sx={{ fontSize: 80, color: 'error.main' }} />
        <Typography variant="h4" component="h1">
          {t('errors.forbiddenTitle')}
        </Typography>
        <Typography variant="body1" color="text.secondary">
          {message || t('errors.forbiddenMessage')}
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button variant="outlined" onClick={() => navigate(-1)}>
            {t('errors.goBack')}
          </Button>
          <Button variant="contained" onClick={() => navigate('/')}>
            {t('errors.goHome')}
          </Button>
        </Stack>
      </Stack>
    </Box>
  )
}
