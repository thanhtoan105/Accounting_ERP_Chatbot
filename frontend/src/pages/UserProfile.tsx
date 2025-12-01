import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Alert,
  Divider,
  Chip,
  Card,
  CardContent,
  Grid,
} from '@mui/material'
import { Save as SaveIcon, Lock as LockIcon } from '@mui/icons-material'
import { useAuth } from '../hooks/useAuth'
import {
  getCurrentUserProfile,
  updateProfile,
  changePassword,
  type UpdateProfileRequest,
  type ChangePasswordRequest,
} from '../services/user'
import { getRoleDisplayName } from '../utils/roles'

/**
 * User Profile page for users to view and edit their own profile and change password.
 */
export default function UserProfile() {
  const { t } = useTranslation()
  const { user: currentUser } = useAuth()
  const [profile, setProfile] = useState(currentUser)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  // Profile edit state
  const [fullName, setFullName] = useState('')
  const [isEditingProfile, setIsEditingProfile] = useState(false)

  // Password change state
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [isChangingPassword, setIsChangingPassword] = useState(false)
  const [passwordErrors, setPasswordErrors] = useState<{
    current?: string
    new?: string
    confirm?: string
  }>({})

  useEffect(() => {
    loadProfile()
  }, [])

  useEffect(() => {
    if (profile) {
      setFullName(profile.fullName || '')
    }
  }, [profile])

  const loadProfile = async () => {
    try {
      setLoading(true)
      setError(null)
      const userProfile = await getCurrentUserProfile()
      setProfile(userProfile)
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
          (err as { message?: string })?.message ||
          t('users.failedToLoadProfile')
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!profile) return

    setError(null)
    setSuccess(null)

    if (!fullName || fullName.trim().length === 0) {
      setError(t('invitation.fullNameRequired'))
      return
    }

    try {
      setSaving(true)
      const request: UpdateProfileRequest = {
        fullName: fullName.trim(),
      }
      const updatedProfile = await updateProfile(request)
      setProfile(updatedProfile)
      setIsEditingProfile(false)
      setSuccess(t('users.profileUpdated'))
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
          (err as { message?: string })?.message ||
          t('users.failedToUpdateProfile')
      setError(errorMessage)
    } finally {
      setSaving(false)
    }
  }

  const validatePasswordChange = (): boolean => {
    const errors: typeof passwordErrors = {}

    if (!currentPassword) {
      errors.current = t('users.currentPasswordRequired')
    }

    if (!newPassword) {
      errors.new = t('users.newPasswordRequired')
    } else if (newPassword.length < 8) {
      errors.new = t('invitation.passwordMinLength')
    }

    if (!confirmPassword) {
      errors.confirm = t('users.confirmNewPassword')
    } else if (newPassword && confirmPassword !== newPassword) {
      errors.confirm = t('auth.passwordsDoNotMatch')
    }

    setPasswordErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault()

    setError(null)
    setSuccess(null)

    if (!validatePasswordChange()) {
      return
    }

    try {
      setSaving(true)
      const request: ChangePasswordRequest = {
        currentPassword,
        newPassword,
        confirmPassword,
      }
      await changePassword(request)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setIsChangingPassword(false)
      setPasswordErrors({})
      setSuccess(t('users.passwordChanged'))
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
          (err as { message?: string })?.message ||
          t('users.failedToChangePassword')
      setError(errorMessage)
    } finally {
      setSaving(false)
    }
  }

  const getPasswordStrength = (
    password: string,
  ): { label: string; color: 'success' | 'warning' | 'error' } => {
    if (password.length === 0) {
      return { label: '', color: 'error' }
    }
    if (password.length < 8) {
      return { label: t('users.weak'), color: 'error' }
    }
    if (password.length < 12) {
      return { label: t('users.medium'), color: 'warning' }
    }
    return { label: t('users.strong'), color: 'success' }
  }

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <Typography>{t('users.loadingProfile')}</Typography>
      </Box>
    )
  }

  if (!profile) {
    return (
      <Box>
        <Alert severity="error">{t('users.failedToLoadProfile')}</Alert>
      </Box>
    )
  }

  const passwordStrength = getPasswordStrength(newPassword)

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        {t('users.myProfile')}
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {success && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}

      <Grid container spacing={3}>
        {/* Profile Information */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="h6">{t('users.profileInfo')}</Typography>
                {!isEditingProfile && (
                  <Button variant="outlined" size="small" onClick={() => setIsEditingProfile(true)}>
                    {t('common.edit')}
                  </Button>
                )}
              </Box>

              {isEditingProfile ? (
                <form onSubmit={handleSaveProfile}>
                  <TextField
                    label={t('auth.email')}
                    fullWidth
                    disabled
                    value={profile.email || ''}
                    sx={{ mb: 2 }}
                  />
                  <TextField
                    label={t('invitation.fullName')}
                    fullWidth
                    required
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    disabled={saving}
                    sx={{ mb: 2 }}
                    autoFocus
                  />
                  <Box display="flex" gap={2}>
                    <Button
                      type="submit"
                      variant="contained"
                      startIcon={<SaveIcon />}
                      disabled={saving}
                    >
                      {saving ? t('common.saving') : t('common.save')}
                    </Button>
                    <Button
                      onClick={() => {
                        setIsEditingProfile(false)
                        setFullName(profile.fullName || '')
                      }}
                      disabled={saving}
                    >
                      Cancel
                    </Button>
                  </Box>
                </form>
              ) : (
                <Box>
                  <Box mb={2}>
                    <Typography variant="body2" color="text.secondary">
                      {t('auth.email')}
                    </Typography>
                    <Typography variant="body1">{profile.email}</Typography>
                  </Box>
                  <Box mb={2}>
                    <Typography variant="body2" color="text.secondary">
                      {t('invitation.fullName')}
                    </Typography>
                    <Typography variant="body1">{profile.fullName}</Typography>
                  </Box>
                  <Box mb={2}>
                    <Typography variant="body2" color="text.secondary">
                      {t('invitation.role')}
                    </Typography>
                    <Chip label={getRoleDisplayName(profile.role)} size="small" sx={{ mt: 0.5 }} />
                  </Box>
                  {profile.companyId && (
                    <Box>
                      <Typography variant="body2" color="text.secondary">
                        {t('invitation.company')} ID
                      </Typography>
                      <Typography variant="body1">{profile.companyId}</Typography>
                    </Box>
                  )}
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Change Password */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="h6">{t('users.changePassword')}</Typography>
                {!isChangingPassword && (
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={<LockIcon />}
                    onClick={() => setIsChangingPassword(true)}
                  >
                    {t('users.changePassword')}
                  </Button>
                )}
              </Box>

              {isChangingPassword ? (
                <form onSubmit={handleChangePassword}>
                  <TextField
                    label={t('users.currentPassword')}
                    type="password"
                    fullWidth
                    required
                    value={currentPassword}
                    onChange={(e) => {
                      setCurrentPassword(e.target.value)
                      setPasswordErrors({ ...passwordErrors, current: undefined })
                    }}
                    disabled={saving}
                    error={!!passwordErrors.current}
                    helperText={passwordErrors.current}
                    sx={{ mb: 2 }}
                    autoFocus
                  />
                  <TextField
                    label={t('auth.newPassword')}
                    type="password"
                    fullWidth
                    required
                    value={newPassword}
                    onChange={(e) => {
                      setNewPassword(e.target.value)
                      setPasswordErrors({ ...passwordErrors, new: undefined })
                    }}
                    disabled={saving}
                    error={!!passwordErrors.new}
                    helperText={
                      passwordErrors.new ||
                      (newPassword && (
                        <Typography
                          variant="caption"
                          color={passwordStrength.color}
                          component="span"
                        >
                          {t('users.passwordStrength')}: {passwordStrength.label}
                        </Typography>
                      ))
                    }
                    sx={{ mb: 2 }}
                  />
                  <TextField
                    label={t('auth.confirmPassword')}
                    type="password"
                    fullWidth
                    required
                    value={confirmPassword}
                    onChange={(e) => {
                      setConfirmPassword(e.target.value)
                      setPasswordErrors({ ...passwordErrors, confirm: undefined })
                    }}
                    disabled={saving}
                    error={!!passwordErrors.confirm}
                    helperText={passwordErrors.confirm}
                    sx={{ mb: 2 }}
                  />
                  <Box display="flex" gap={2}>
                    <Button type="submit" variant="contained" disabled={saving}>
                      {saving ? t('common.saving') : t('users.changePassword')}
                    </Button>
                    <Button
                      onClick={() => {
                        setIsChangingPassword(false)
                        setCurrentPassword('')
                        setNewPassword('')
                        setConfirmPassword('')
                        setPasswordErrors({})
                      }}
                      disabled={saving}
                    >
                      Cancel
                    </Button>
                  </Box>
                </form>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Click "Change Password" to update your password.
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}
