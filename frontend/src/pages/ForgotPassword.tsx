import { useState } from 'react'
import { useNavigate, Link as RouterLink } from 'react-router-dom'
import { z } from 'zod'
import { Box, Button, Stack, TextField, Typography } from '@mui/material'
import { forgotPassword } from '../services/auth'

const forgotPasswordSchema = z.object({
  email: z.string().email('Invalid email address'),
})

type ForgotPasswordForm = z.infer<typeof forgotPasswordSchema>

export type ForgotPasswordProps = {
  onSuccess?: () => void
  onBack?: () => void
}

export function ForgotPassword({ onSuccess }: ForgotPasswordProps = {}) {
  const navigate = useNavigate()
  const [values, setValues] = useState<ForgotPasswordForm>({ email: '' })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(false)

  function onChange<K extends keyof ForgotPasswordForm>(key: K, value: ForgotPasswordForm[K]) {
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
    setSuccess(false)

    const parsed = forgotPasswordSchema.safeParse(values)
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
      await forgotPassword(values.email)
      setSuccess(true)
      if (onSuccess) {
        setTimeout(() => {
          onSuccess()
        }, 2000)
      } else {
        // Auto navigate back to login after showing success message
        setTimeout(() => {
          navigate('/login')
        }, 3000)
      }
    } catch (err) {
      const errorData = err as { error?: { message?: string }; message?: string }
      const message =
        errorData?.error?.message || errorData?.message || 'Failed to send reset email'
      setErrors({ form: message })
    } finally {
      setSubmitting(false)
    }
  }

  if (success) {
    return (
      <Box sx={{ maxWidth: 480, mx: 'auto', mt: 4 }}>
        <Typography variant="h5" gutterBottom>
          Check Your Email
        </Typography>
        <Typography variant="body1" color="text.secondary" paragraph>
          If an account with that email exists, a reset link has been sent. Please check your email
          and click the reset link.
        </Typography>
        <Button component={RouterLink} to="/login" variant="text" fullWidth>
          Back to Login
        </Button>
      </Box>
    )
  }

  return (
    <Box component="form" onSubmit={onSubmit} sx={{ maxWidth: 480, mx: 'auto', mt: 4 }}>
      <Typography variant="h5" gutterBottom>
        Forgot Password
      </Typography>
      <Stack spacing={2}>
        <Typography variant="body2" color="text.secondary">
          Enter your email address and we'll send you a link to reset your password.
        </Typography>
        <TextField
          label="Email"
          type="email"
          value={values.email}
          onChange={(e) => onChange('email', e.target.value)}
          error={Boolean(errors.email)}
          helperText={errors.email}
          required
          fullWidth
        />
        {errors.form && (
          <Typography color="error" variant="body2">
            {errors.form}
          </Typography>
        )}
        <Button type="submit" variant="contained" disabled={submitting} fullWidth>
          {submitting ? 'Sending…' : 'Send Reset Link'}
        </Button>
        <Button component={RouterLink} to="/login" variant="text" fullWidth>
          Back to Login
        </Button>
      </Stack>
    </Box>
  )
}

export default ForgotPassword
