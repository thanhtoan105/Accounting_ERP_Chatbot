import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router-dom'
import { z } from 'zod'
import { forgotPassword } from '@/features/auth/services/auth'
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
  const { t } = useTranslation()
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
      toast.success(t('auth.checkYourEmail'), {
        description: t('auth.resetEmailSent'),
      })
      if (onSuccess) {
        onSuccess()
      }
    } catch (err) {
      const errorData = err as { error?: { message?: string }; message?: string }
      const message =
        errorData?.error?.message || errorData?.message || 'Failed to send reset email'
      setErrors({ form: message })
      toast.error(t('auth.failedToSendResetEmail'), { description: message })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-[100dvh] w-full grid place-items-center px-4">
      <form onSubmit={onSubmit} className="w-full max-w-md">
        <Card>
          <CardHeader className="space-y-3">
            <CardTitle className="text-3xl">{t('auth.forgotPasswordTitle')}</CardTitle>
            <CardDescription>
              {t('auth.forgotPasswordDescription')}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="space-y-2">
              <Label htmlFor="email">{t('auth.emailAddress')}</Label>
              <Input
                id="email"
                type="email"
                value={values.email}
                onChange={(e) => onChange('email', e.target.value)}
                autoComplete="email"
                placeholder={t('auth.enterEmail')}
              />
              {errors.email && <p className="text-sm text-destructive">{errors.email}</p>}
            </div>

            {errors.form && <p className="text-sm text-destructive">{errors.form}</p>}

            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? t('auth.sending') : t('auth.sendResetLink')}
            </Button>
          </CardContent>
          <CardFooter className="justify-center">
            <RouterLink
              to="/login"
              className="text-sm underline underline-offset-4 inline-flex items-center gap-1"
            >
              <ArrowLeft className="h-4 w-4" /> {t('auth.backToLogin')}
            </RouterLink>
          </CardFooter>
        </Card>
      </form>
    </div>
  )
}
