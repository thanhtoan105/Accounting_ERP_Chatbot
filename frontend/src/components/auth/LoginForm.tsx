import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, Link as RouterLink } from 'react-router-dom'
import { Checkbox } from '@/components/ui/checkbox'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { toast } from 'sonner'
import { login } from '../../services/auth'
import { Eye, EyeOff, Moon, Sun } from 'lucide-react'
import { setAccessToken, setCompanyId } from '../../utils/axios'
import { useTheme } from '@/hooks/useTheme'

const loginSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(1, 'Password is required'),
  rememberMe: z.boolean().optional(),
})

type LoginFormValues = z.infer<typeof loginSchema>

export type LoginFormProps = React.ComponentProps<'div'> & {
  accountCreated?: boolean
  onSuccess?: (user: { id: number; email: string; fullName: string; role: string }) => void
}

export function LoginForm({ className, accountCreated, onSuccess, ...props }: LoginFormProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resolvedTheme, toggleTheme } = useTheme()
  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '', rememberMe: false },
    mode: 'onSubmit',
    reValidateMode: 'onBlur',
  })
  const {
    handleSubmit,
    register,
    setValue,
    formState: { errors, isSubmitting },
    reset,
  } = form
  const [submitting, setSubmitting] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [lockoutMessage, setLockoutMessage] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [companyLogo, setCompanyLogo] = useState<string | null>(null)

  useEffect(() => {
    if (success) {
      toast.success(t('auth.loginSuccess'))
    }
  }, [success])

  // Try to load company logo from localStorage if available (from previous session)
  useEffect(() => {
    // For MVP, we'll show a default logo. In future, could fetch based on subdomain or email domain
    // For now, logo will be shown after login in the authenticated area
    setCompanyLogo(null)
  }, [])

  function onRememberMeChange(checked: boolean) {
    setValue('rememberMe', checked, { shouldDirty: true, shouldValidate: false })
    if (lockoutMessage) setLockoutMessage(null)
  }

  async function onSubmit(values: LoginFormValues) {
    setLockoutMessage(null)
    setFormError(null)
    try {
      setSubmitting(true)
      const response = await login(values)
      setSuccess(true)
      reset({ email: '', password: '', rememberMe: false })

      setAccessToken(response.accessToken)
      const userCompanyId = response.user.companyId ?? null
      setCompanyId(userCompanyId)
      if (userCompanyId !== null && userCompanyId !== undefined) {
        const stored = localStorage.getItem('activeCompanyId')
        if (!stored || Number(stored) !== userCompanyId) {
          localStorage.setItem('activeCompanyId', String(userCompanyId))
        }
      }

      if (onSuccess) {
        setTimeout(() => onSuccess(response.user), 1500)
      } else {
        setTimeout(() => {
          navigate('/')
        }, 1500)
      }
    } catch (err) {
      const errorData = err as { error?: { code?: string; message?: string }; message?: string }
      const message = errorData?.error?.message || errorData?.message || 'Login failed'
      const errorCode = errorData?.error?.code
      if (errorCode === 'ACCOUNT_LOCKED') setLockoutMessage(t('auth.accountLockedMessage'))
      else if (errorCode === 'ACCOUNT_DEACTIVATED' || message.toLowerCase().includes('deactivated'))
        setFormError(t('auth.accountDeactivated'))
      else if (errorCode === 'UNAUTHORIZED') setFormError(t('auth.invalidCredentials'))
      else setFormError(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className={cn('flex flex-col gap-6', className)} {...props}>
      <Card className="overflow-hidden p-0">
        <CardContent className="grid p-0 md:grid-cols-2">
          <form className="relative p-6 md:p-8" noValidate onSubmit={handleSubmit(onSubmit)}>
            <div className="absolute top-4 right-4">
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={toggleTheme}
                aria-label={
                  resolvedTheme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
                }
              >
                {resolvedTheme === 'dark' ? (
                  <Sun className="size-5" />
                ) : (
                  <Moon className="size-5" />
                )}
              </Button>
            </div>
            <FieldGroup>
              <div className="flex flex-col items-center gap-4 text-center">
                {companyLogo ? (
                  <img
                    src={companyLogo}
                    alt="Company logo"
                    className="h-16 w-auto object-contain"
                  />
                ) : (
                  <div className="bg-primary text-primary-foreground flex h-16 w-16 items-center justify-center rounded-lg">
                    <span className="text-2xl font-bold">A</span>
                  </div>
                )}
                <div>
                  <h1 className="text-2xl font-bold">{t('auth.welcomeBack')}</h1>
                  <p className="text-muted-foreground text-balance">{t('auth.loginToAccount')}</p>
                </div>
              </div>

              {accountCreated && (
                <Alert variant="default" className="mb-4">
                  <AlertTitle>{t('auth.accountCreated')}</AlertTitle>
                  <AlertDescription>{t('auth.accountCreatedMessage')}</AlertDescription>
                </Alert>
              )}
              {lockoutMessage && (
                <Alert variant="destructive" className="mb-4">
                  <AlertTitle>{t('auth.accountLocked')}</AlertTitle>
                  <AlertDescription>{lockoutMessage}</AlertDescription>
                </Alert>
              )}
              {formError && (
                <Alert variant="destructive" className="mb-4">
                  <AlertTitle>{t('auth.error')}</AlertTitle>
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}

              <Field>
                <FieldLabel htmlFor="email">{t('auth.email')}</FieldLabel>
                <Input
                  id="email"
                  data-testid="email-input"
                  type="email"
                  placeholder="m@example.com"
                  {...register('email')}
                  aria-invalid={!!errors.email}
                />
                {errors.email?.message && (
                  <FieldDescription className="text-destructive text-sm">
                    {errors.email.message}
                  </FieldDescription>
                )}
              </Field>

              <Field>
                <div className="flex items-center">
                  <FieldLabel htmlFor="password">{t('auth.password')}</FieldLabel>
                </div>
                <div className="relative">
                  <Input
                    id="password"
                    data-testid="password-input"
                    type={showPassword ? 'text' : 'password'}
                    {...register('password')}
                    aria-invalid={!!errors.password}
                  />
                  <button
                    type="button"
                    aria-label={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground/80 hover:text-foreground transition h-8 w-8 rounded-md inline-flex items-center justify-center"
                    onClick={() => setShowPassword((v) => !v)}
                  >
                    {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                  </button>
                </div>
                {errors.password?.message && (
                  <FieldDescription className="text-destructive text-sm">
                    {errors.password.message}
                  </FieldDescription>
                )}
              </Field>

              <Field>
                <div className="flex items-center gap-2">
                  <Checkbox
                    id="rememberMe"
                    onCheckedChange={(checked) => onRememberMeChange(checked === true)}
                  />
                  <FieldLabel htmlFor="rememberMe" className="cursor-pointer">
                    {t('auth.rememberMe')}
                  </FieldLabel>
                </div>
              </Field>

              <Field>
                <Button
                  type="submit"
                  data-testid="login-button"
                  disabled={submitting || isSubmitting}
                  className="w-full"
                >
                  {submitting || isSubmitting ? t('auth.loggingIn') : t('auth.login')}
                </Button>
              </Field>

              <div className="mt-2 text-right">
                <RouterLink
                  to="/forgot-password"
                  className="ml-auto text-sm underline-offset-2 hover:underline"
                >
                  {t('auth.forgotPassword')}
                </RouterLink>
              </div>
            </FieldGroup>
          </form>
          <div className="bg-muted relative hidden md:flex items-center justify-center p-6">
            <img
              src="/undraw_login_weas.svg"
              alt="Image"
              className="max-h-[260px] max-w-[260px] w-auto h-auto object-contain dark:brightness-[0.8]"
            />
          </div>
        </CardContent>
      </Card>
      {/* Success toast is handled via useEffect; no inline alert here */}
      <FieldDescription className="px-6 text-center">
        {t('auth.termsAgreement')}{' '}
        <a href="#" className="underline-offset-2 hover:underline">
          {t('auth.termsOfService')}
        </a>{' '}
        {t('auth.and')}{' '}
        <a href="#" className="underline-offset-2 hover:underline">
          {t('auth.privacyPolicy')}
        </a>
        .
      </FieldDescription>
    </div>
  )
}

export default LoginForm
