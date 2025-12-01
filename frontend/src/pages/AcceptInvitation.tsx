import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  Card,
  CardContent,
  TextField,
  Typography,
  Alert,
  Stack,
  Divider,
} from '@mui/material'
import { validateInvitation, acceptInvitation } from '../services/invitation'
import type { InvitationDetails } from '../services/invitation'
import { getRoleDisplayName } from '../utils/roles'

/**
 * Public page for accepting user invitations.
 * Users can register via invitation link.
 */
export default function AcceptInvitation() {
  const { t } = useTranslation()
  const { token } = useParams<{ token: string }>()
  const navigate = useNavigate()

  const [invitation, setInvitation] = useState<InvitationDetails | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fullName, setFullName] = useState('')

  useEffect(() => {
    if (token) {
      loadInvitation()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  const loadInvitation = async () => {
    if (!token) return

    try {
      setLoading(true)
      setError(null)
      const data = await validateInvitation(token)
      setInvitation(data)
      // Pre-fill email (read-only)
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
            (err as { message?: string })?.message ||
            t('invitation.invalidOrExpired')
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (password.length < 8) {
      setError(t('invitation.passwordMinLength'))
      return
    }

    if (password !== confirmPassword) {
      setError(t('auth.passwordsDoNotMatch'))
      return
    }

    if (!fullName.trim()) {
      setError(t('invitation.fullNameRequired'))
      return
    }

    if (!token) {
      setError(t('invitation.invalidToken'))
      return
    }

    try {
      setSubmitting(true)
      await acceptInvitation(token, {
        password,
        confirmPassword,
        fullName: fullName.trim(),
      })

      // Don't set access token - redirect to login page so user can log in manually
      // This ensures user explicitly logs in with their new credentials
      navigate('/login?accountCreated=true', { replace: true })
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
            (err as { message?: string })?.message ||
            t('invitation.failedToAccept')
      setError(errorMessage)
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <Box
        sx={{
          display: 'flex',
          minHeight: '100vh',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Typography>{t('invitation.loading')}</Typography>
      </Box>
    )
  }

  if (error && !invitation) {
    return (
      <Box
        sx={{
          display: 'flex',
          minHeight: '100vh',
          alignItems: 'center',
          justifyContent: 'center',
          p: 3,
        }}
      >
        <Card sx={{ maxWidth: 500, width: '100%' }}>
          <CardContent>
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
            <Button fullWidth variant="contained" onClick={() => navigate('/login')}>
              {t('invitation.goToLogin')}
            </Button>
          </CardContent>
        </Card>
      </Box>
    )
  }

  if (!invitation) {
    return null
  }

  const expirationDate = new Date(invitation.expiresAt)
  const isExpired = expirationDate < new Date()

  return (
    <Box
      sx={{
        display: 'flex',
        minHeight: '100vh',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
        p: 3,
      }}
    >
      <Card sx={{ maxWidth: 500, width: '100%' }}>
        <CardContent>
          <Typography variant="h5" component="h1" gutterBottom>
            {t('invitation.youveBeenInvited')}
          </Typography>

          {isExpired && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {t('invitation.expired')}
            </Alert>
          )}

          {error && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
              {error}
            </Alert>
          )}

          <Stack spacing={2} sx={{ mb: 3 }}>
            <Box>
              <Typography variant="body2" color="text.secondary">
                {t('invitation.company')}
              </Typography>
              <Typography variant="body1">{invitation.companyName}</Typography>
            </Box>
            <Box>
              <Typography variant="body2" color="text.secondary">
                {t('auth.email')}
              </Typography>
              <Typography variant="body1">{invitation.email}</Typography>
            </Box>
            <Box>
              <Typography variant="body2" color="text.secondary">
                {t('invitation.role')}
              </Typography>
              <Typography variant="body1">{getRoleDisplayName(invitation.role)}</Typography>
            </Box>
            <Box>
              <Typography variant="body2" color="text.secondary">
                {t('invitation.expires')}
              </Typography>
              <Typography variant="body1">
                {expirationDate.toLocaleDateString()} at {expirationDate.toLocaleTimeString()}
              </Typography>
            </Box>
          </Stack>

          <Divider sx={{ my: 3 }} />

          <form onSubmit={handleSubmit}>
            <Stack spacing={2}>
              <TextField
                label={t('auth.email')}
                type="email"
                value={invitation.email}
                disabled
                fullWidth
              />
              <TextField
                label={t('invitation.fullName')}
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
                fullWidth
                disabled={submitting || isExpired}
              />
              <TextField
                label={t('auth.password')}
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                fullWidth
                disabled={submitting || isExpired}
                helperText={t('invitation.passwordMinLength')}
              />
              <TextField
                label={t('auth.confirmPassword')}
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                fullWidth
                disabled={submitting || isExpired}
              />
              <Button
                type="submit"
                variant="contained"
                fullWidth
                disabled={submitting || isExpired}
                sx={{ mt: 2 }}
              >
                {submitting ? t('invitation.creatingAccount') : t('invitation.acceptAndCreate')}
              </Button>
            </Stack>
          </form>
        </CardContent>
      </Card>
    </Box>
  )
}
