import { useState, useEffect } from 'react'
import { useNavigate, Link as RouterLink } from 'react-router-dom'
import { z } from 'zod'
import { resetPassword } from '@/features/auth/services/auth'

import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
  CardDescription,
} from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'
import { Eye, EyeOff, Lock } from 'lucide-react'

const resetPasswordSchema = z
  .object({
    password: z
      .string()
      .min(8, 'At least 8 characters')
      .regex(/[a-z]/, 'Must include lowercase')
      .regex(/[A-Z]/, 'Must include uppercase')
      .regex(/\d/, 'Must include number'),
    confirmPassword: z.string(),
    token: z.string().min(1, 'Reset token is required'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords don't match",
    path: ['confirmPassword'],
  })

type ResetPasswordForm = z.infer<typeof resetPasswordSchema>

export type ResetPasswordProps = {
  token?: string
  onSuccess?: () => void
}

export default function ResetPassword({ token: propToken, onSuccess }: ResetPasswordProps = {}) {
  const navigate = useNavigate()
  const [token, setToken] = useState<string>(propToken || '')
  const [values, setValues] = useState<Omit<ResetPasswordForm, 'token'>>({
    password: '',
    confirmPassword: '',
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  const [show, setShow] = useState<{ pw: boolean; cpw: boolean }>({ pw: false, cpw: false })

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
        const next = { ...e }
        delete next[key]
        return next
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
      toast('Password reset successful', { description: 'Redirecting to login page...' })
      setValues({ password: '', confirmPassword: '' })
      if (onSuccess) {
        setTimeout(() => onSuccess(), 1500)
      } else {
        setTimeout(() => navigate('/login'), 1500)
      }
    } catch (err) {
      const errorData = err as { error?: { code?: string; message?: string }; message?: string }
      const errorCode = errorData?.error?.code
      const message = errorData?.error?.message || errorData?.message || 'Failed to reset password'

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
    } finally {
      setSubmitting(false)
    }
  }

  // Success toast is shown; keep rendering the form while redirect timer runs

  return (
    <div className="min-h-[100dvh] w-full grid place-items-center px-4">
      <form onSubmit={onSubmit} className="w-full max-w-md">
        <Card>
          <CardHeader className="text-center space-y-3">
            <div className="mx-auto h-16 w-16 rounded-full bg-muted grid place-items-center">
              <Lock className="h-8 w-8" />
            </div>
            <CardTitle className="text-3xl">Reset Password</CardTitle>
            <CardDescription>Create a new password for your account</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="space-y-2">
              <Label htmlFor="password">New Password</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={show.pw ? 'text' : 'password'}
                  value={values.password}
                  onChange={(e) => onChange('password', e.target.value)}
                  autoComplete="new-password"
                  placeholder="••••••••"
                />
                <button
                  type="button"
                  aria-label={show.pw ? 'Hide password' : 'Show password'}
                  onClick={() => setShow((s) => ({ ...s, pw: !s.pw }))}
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded-md text-muted-foreground hover:text-foreground"
                >
                  {show.pw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              <p className="text-sm text-muted-foreground">
                Password must be at least 8 characters with uppercase, lowercase, and number.
              </p>
              {errors.password && <p className="text-sm text-destructive">{errors.password}</p>}
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirmPassword">Confirm Password</Label>
              <div className="relative">
                <Input
                  id="confirmPassword"
                  type={show.cpw ? 'text' : 'password'}
                  value={values.confirmPassword}
                  onChange={(e) => onChange('confirmPassword', e.target.value)}
                  autoComplete="new-password"
                  placeholder="••••••••"
                />
                <button
                  type="button"
                  aria-label={show.cpw ? 'Hide password' : 'Show password'}
                  onClick={() => setShow((s) => ({ ...s, cpw: !s.cpw }))}
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded-md text-muted-foreground hover:text-foreground"
                >
                  {show.cpw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {errors.confirmPassword && (
                <p className="text-sm text-destructive">{errors.confirmPassword}</p>
              )}
            </div>

            {errors.form && <p className="text-sm text-destructive">{errors.form}</p>}
            {errors.token && <p className="text-sm text-destructive">{errors.token}</p>}

            <Button type="submit" className="w-full" disabled={submitting || !token}>
              {submitting ? 'Resetting…' : 'Reset Password'}
            </Button>
          </CardContent>
          <CardFooter className="justify-center">
            <p className="text-sm text-muted-foreground">
              Remember your password?{' '}
              <RouterLink to="/login" className="underline underline-offset-4">
                Sign in
              </RouterLink>
            </p>
          </CardFooter>
        </Card>
      </form>
    </div>
  )
}
