import { useState, useEffect } from 'react'
import { useNavigate, Link as RouterLink } from 'react-router-dom'
import { z } from 'zod'
import {
  Box,
  Button,
  Stack,
  TextField,
  Typography,
  FormControlLabel,
  Checkbox,
  Alert,
} from '@mui/material'
import { resetPassword } from '../services/auth'

const resetPasswordSchema = z
  .object({
    password: z
      .string()
      .min(8, 'Password must be at least 8 characters')
      .regex(/[a-z]/, 'Password must contain at least one lowercase letter')
      .regex(/[A-Z]/, 'Password must contain at least one uppercase letter')
      .regex(/\d/, 'Password must contain at least one number')
      .regex(/[@$!%*?&]/, 'Password must contain at least one special character (@$!%*?&)'),
    confirmPassword: z.string(),
    token: z.string().min(1, 'Reset token is required'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords don't match",
    path: ['confirmPassword'],
  })

type ResetPasswordForm = z.infer<typeof resetPasswordSchema>

const passwordRequirements = [
  { label: 'At least 8 characters', check: (p: string) => p.length >= 8 },
  { label: 'One uppercase letter', check: (p: string) => /[A-Z]/.test(p) },
  { label: 'One lowercase letter', check: (p: string) => /[a-z]/.test(p) },
  { label: 'One number', check: (p: string) => /\d/.test(p) },
  { label: 'One special character (@$!%*?&)', check: (p: string) => /[@$!%*?&]/.test(p) },
]

export type ResetPasswordProps = {
  token?: string
  onSuccess?: () => void
  onBack?: () => void
}

export function ResetPassword({ token: propToken, onSuccess }: ResetPasswordProps = {}) {
  const navigate = useNavigate()
  const [token, setToken] = useState<string>(propToken || '')
  const [values, setValues] = useState<Omit<ResetPasswordForm, 'token'>>({
    password: '',
    confirmPassword: '',
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    if (propToken) {
      setToken(propToken)
    } else {
      const params = new URLSearchParams(window.location.search)
      const urlToken = params.get('token')
      if (urlToken) {
        setToken(urlToken)
      }
    }
  }, [propToken])

  function onChange<K extends keyof Omit<ResetPasswordForm, 'token'>>(
    key: K,
    value: ResetPasswordForm[K],
  ) {
    setValues((v) => ({ ...v, [key]: value }))
    if (errors[key]) {
      setErrors((e) => {
        const newErrors = { ...e }
        delete newErrors[key]
        return newErrors
      })
    }
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setErrors({})

    if (!token) {
      setErrors({ token: 'Reset token is required' })
      return
    }

    const parsed = resetPasswordSchema.safeParse({ ...values, token })
    if (!parsed.success) {
      const map: Record<string, string> = {}
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as string
        map[key] = issue.message
      }
      setErrors(map)
      return
    }

    try {
      setSubmitting(true)
      await resetPassword(token, values.password)
      setSuccess(true)
      // Clear form
      setValues({ password: '', confirmPassword: '' })
      // Navigate to login after showing success message
      if (onSuccess) {
        setTimeout(() => {
          onSuccess()
        }, 2000)
      } else {
        setTimeout(() => {
          navigate('/login')
        }, 2000)
      }
    } catch (err) {
      const errorData = err as { error?: { code?: string; message?: string }; message?: string }
      const errorCode = errorData?.error?.code
      const message = errorData?.error?.message || errorData?.message || 'Failed to reset password'

      // Map error codes to user-friendly messages
      if (errorCode === 'BAD_REQUEST' || message.includes('Invalid or expired')) {
        setErrors({
          form: 'Invalid or expired reset token. Please request a new password reset link.',
        })
      } else if (errorCode === 'UNAUTHORIZED' || errorCode === 'FORBIDDEN') {
        setErrors({
          form: 'You do not have permission to reset this password. Please contact support.',
        })
      } else {
        setErrors({ form: message })
      }

      // Clear success state if it was set
      setSuccess(false)
    } finally {
      setSubmitting(false)
    }
  }

  if (success) {
    return (
      <Box sx={{ maxWidth: 480, mx: 'auto', mt: 4 }}>
        <Alert severity="success" sx={{ mb: 2 }}>
          Password reset successful! Redirecting to login page...
        </Alert>
      </Box>
    )
  }

  return (
    <Box component="form" onSubmit={onSubmit} sx={{ maxWidth: 480, mx: 'auto', mt: 4 }}>
      <Typography variant="h5" gutterBottom>
        Reset Password
      </Typography>
      <Stack spacing={2}>
        <TextField
          label="New Password"
          type="password"
          value={values.password}
          onChange={(e) => onChange('password', e.target.value)}
          error={Boolean(errors.password)}
          helperText={errors.password}
          required
          fullWidth
        />
        {values.password && (
          <Box sx={{ pl: 2 }}>
            <Typography variant="caption" color="text.secondary" gutterBottom>
              Password Requirements:
            </Typography>
            <Stack spacing={0.5}>
              {passwordRequirements.map((req) => {
                const met = req.check(values.password)
                return (
                  <FormControlLabel
                    key={req.label}
                    control={<Checkbox checked={met} size="small" disabled />}
                    label={
                      <Typography variant="caption" color={met ? 'success.main' : 'text.secondary'}>
                        {req.label}
                      </Typography>
                    }
                  />
                )
              })}
            </Stack>
          </Box>
        )}
        <TextField
          label="Confirm Password"
          type="password"
          value={values.confirmPassword}
          onChange={(e) => onChange('confirmPassword', e.target.value)}
          error={Boolean(errors.confirmPassword)}
          helperText={errors.confirmPassword}
          required
          fullWidth
        />
        {errors.form && (
          <Typography color="error" variant="body2">
            {errors.form}
          </Typography>
        )}
        <Button type="submit" variant="contained" disabled={submitting || !token} fullWidth>
          {submitting ? 'Resetting…' : 'Reset Password'}
        </Button>
        <Button component={RouterLink} to="/login" variant="text" fullWidth>
          Back to Login
        </Button>
      </Stack>
    </Box>
  )
}

export default ResetPassword
