import { useEffect, useState } from 'react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
  FieldSeparator,
} from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, Link as RouterLink } from 'react-router-dom'
import { Checkbox } from '@/components/ui/checkbox'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { toast } from 'sonner'
import { login } from '../../services/auth'
import { setAccessToken, setCompanyId } from '../../utils/axios'

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
  const navigate = useNavigate()
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
  const [formError, setFormError] = useState<string | null>(null)
  const [lockoutMessage, setLockoutMessage] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    if (success) {
      toast.success('Login successful! Redirecting...')
    }
  }, [success])

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
          const hasNoCompany = userCompanyId == null || userCompanyId === 0
          navigate(hasNoCompany ? '/company' : '/')
        }, 1500)
      }
    } catch (err) {
      const errorData = err as { error?: { code?: string; message?: string }; message?: string }
      const message = errorData?.error?.message || errorData?.message || 'Login failed'
      const errorCode = errorData?.error?.code
      if (errorCode === 'ACCOUNT_LOCKED')
        setLockoutMessage('Account is locked. Please try again later.')
      else if (errorCode === 'ACCOUNT_DEACTIVATED' || message.toLowerCase().includes('deactivated'))
        setFormError('Account is deactivated')
      else if (errorCode === 'UNAUTHORIZED') setFormError('Invalid email or password')
      else setFormError(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className={cn('flex flex-col gap-6', className)} {...props}>
      <Card className="overflow-hidden p-0">
        <CardContent className="grid p-0 md:grid-cols-2">
          <form className="p-6 md:p-8" noValidate onSubmit={handleSubmit(onSubmit)}>
            <FieldGroup>
              <div className="flex flex-col items-center gap-2 text-center">
                <h1 className="text-2xl font-bold">Welcome back</h1>
                <p className="text-muted-foreground text-balance">Login to your account</p>
              </div>

              {accountCreated && (
                <Alert variant="default" className="mb-4">
                  <AlertTitle>Account Created</AlertTitle>
                  <AlertDescription>
                    Account created successfully! Please log in with your email and password.
                  </AlertDescription>
                </Alert>
              )}
              {lockoutMessage && (
                <Alert variant="destructive" className="mb-4">
                  <AlertTitle>Account Locked</AlertTitle>
                  <AlertDescription>{lockoutMessage}</AlertDescription>
                </Alert>
              )}
              {formError && (
                <Alert variant="destructive" className="mb-4">
                  <AlertTitle>Error</AlertTitle>
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}

              <Field>
                <FieldLabel htmlFor="email">Email</FieldLabel>
                <Input
                  id="email"
                  type="email"
                  placeholder="m@example.com"
                  {...register('email')}
                  className={errors.email ? 'border-destructive' : ''}
                />
                {errors.email?.message && (
                  <FieldDescription className="text-destructive text-sm">
                    {errors.email.message}
                  </FieldDescription>
                )}
              </Field>

              <Field>
                <div className="flex items-center">
                  <FieldLabel htmlFor="password">Password</FieldLabel>
                  <RouterLink
                    to="/forgot-password"
                    className="ml-auto text-sm underline-offset-2 hover:underline"
                  >
                    Forgot your password?
                  </RouterLink>
                </div>
                <Input
                  id="password"
                  type="password"
                  {...register('password')}
                  className={errors.password ? 'border-destructive' : ''}
                />
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
                    Remember me
                  </FieldLabel>
                </div>
              </Field>

              <Field>
                <Button type="submit" disabled={submitting || isSubmitting} className="w-full">
                  {submitting || isSubmitting ? 'Logging in…' : 'Log in'}
                </Button>
              </Field>

              <FieldSeparator className=":data-[slot=field-separator-content]:bg-card">
                Or continue with
              </FieldSeparator>
              <Field className="grid grid-cols-3 gap-4">
                <Button variant="outline" type="button">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                    <path
                      d="M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701"
                      fill="currentColor"
                    />
                  </svg>
                  <span className="sr-only">Login with Apple</span>
                </Button>
                <Button variant="outline" type="button">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                    <path
                      d="M12.48 10.92v3.28h7.84c-.24 1.84-.853 3.187-1.787 4.133-1.147 1.147-2.933 2.4-6.053 2.4-4.827 0-8.6-3.893-8.6-8.72s3.773-8.72 8.6-8.72c2.6 0 4.507 1.027 5.907 2.347l2.307-2.307C18.747 1.44 16.133 0 12.48 0 5.867 0 .307 5.387.307 12s5.56 12 12.173 12c3.573 0 6.267-1.173 8.373-3.36 2.16-2.16 2.84-5.213 2.84-7.667 0-.76-.053-1.467-.173-2.053H12.48z"
                      fill="currentColor"
                    />
                  </svg>
                  <span className="sr-only">Login with Google</span>
                </Button>
                <Button variant="outline" type="button">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                    <path
                      d="M6.915 4.03c-1.968 0-3.683 1.28-4.871 3.113C.704 9.208 0 11.883 0 14.449c0 .706.07 1.369.21 1.973a6.624 6.624 0 0 0 .265.86 5.297 5.297 0 0 0 .371.761c.696 1.159 1.818 1.927 3.593 1.927 1.497 0 2.633-.671 3.965-2.444.76-1.012 1.144-1.626 2.663-4.32l.756-1.339.186-.325c.061.1.121.196.183.3l2.152 3.595c.724 1.21 1.665 2.556 2.47 3.314 1.046.987 1.992 1.22 3.06 1.22 1.075 0 1.876-.355 2.455-.843a3.743 3.743 0 0 0 .81-.973c.542-.939.861-2.127.861-3.745 0-2.72-.681-5.357-2.084-7.45-1.282-1.912-2.957-2.93-4.716-2.93-1.047 0-2.088.467-3.053 1.308-.652.57-1.257 1.29-1.82 2.05-.69-.875-1.335-1.547-1.958-2.056-1.182-.966-2.315-1.303-3.454-1.303zm10.16 2.053c1.147 0 2.188.758 2.992 1.999 1.132 1.748 1.647 4.195 1.647 6.4 0 1.548-.368 2.9-1.839 2.9-.58 0-1.027-.23-1.664-1.004-.496-.601-1.343-1.878-2.832-4.358l-.617-1.028a44.908 44.908 0 0 0-1.255-1.98c.07-.109.141-.224.211-.327 1.12-1.667 2.118-2.602 3.358-2.602z"
                      fill="currentColor"
                    />
                  </svg>
                  <span className="sr-only">Login with Meta</span>
                </Button>
              </Field>
            </FieldGroup>
          </form>
          <div className="bg-muted relative hidden md:block">
            <img
              src="/placeholder.svg"
              alt="Image"
              className="absolute inset-0 h-full w-full object-cover dark:brightness-[0.2] dark:grayscale"
            />
          </div>
        </CardContent>
      </Card>
      {/* Success toast is handled via useEffect; no inline alert here */}
      <FieldDescription className="px-6 text-center">
        By clicking continue, you agree to our{' '}
        <a href="#" className="underline-offset-2 hover:underline">
          Terms of Service
        </a>{' '}
        and{' '}
        <a href="#" className="underline-offset-2 hover:underline">
          Privacy Policy
        </a>
        .
      </FieldDescription>
    </div>
  )
}

export default LoginForm
