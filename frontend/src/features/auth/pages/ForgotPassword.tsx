import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { z } from 'zod'
import { forgotPassword } from '@/features/auth/services/auth'
import { Card, CardContent, CardFooter, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'
import { ArrowLeft } from 'lucide-react'

const forgotPasswordSchema = z.object({
  email: z.string().email('Invalid email address'),
})

type ForgotPasswordForm = z.infer<typeof forgotPasswordSchema>

export type ForgotPasswordProps = {
  onSuccess?: () => void
  onBack?: () => void
}

export default function ForgotPassword({ onSuccess }: ForgotPasswordProps = {}) {
  const [values, setValues] = useState<ForgotPasswordForm>({ email: '' })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

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
      toast.success('Check your email', {
        description:
          "If an account with that email exists, we've sent a password reset link."
      })
      if (onSuccess) {
        onSuccess()
      }
    } catch (err) {
      const errorData = err as { error?: { message?: string }; message?: string }
      const message =
        errorData?.error?.message || errorData?.message || 'Failed to send reset email'
      setErrors({ form: message })
      toast.error('Failed to send reset email', { description: message })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-[100dvh] w-full grid place-items-center px-4">
      <form onSubmit={onSubmit} className="w-full max-w-md">
        <Card>
          <CardHeader className="space-y-3">
            <CardTitle className="text-3xl">Forgot Password?</CardTitle>
            <CardDescription>
              Enter your email and we'll send you instructions to reset your password
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="space-y-2">
              <Label htmlFor="email">Email address*</Label>
              <Input
                id="email"
                type="email"
                value={values.email}
                onChange={(e) => onChange('email', e.target.value)}
                autoComplete="email"
                placeholder="Enter your email address"
              />
              {errors.email && <p className="text-sm text-destructive">{errors.email}</p>}
            </div>

            {errors.form && (
              <p className="text-sm text-destructive">{errors.form}</p>
            )}

            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? 'Sending…' : 'Send Reset Link'}
            </Button>
          </CardContent>
          <CardFooter className="justify-center">
            <RouterLink to="/login" className="text-sm underline underline-offset-4 inline-flex items-center gap-1">
              <ArrowLeft className="h-4 w-4" /> Back to login
            </RouterLink>
          </CardFooter>
        </Card>
      </form>
    </div>
  )
}

