import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams, useNavigate } from 'react-router-dom'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { validateInvitation, acceptInvitation } from '../services/invitation'
import type { InvitationDetails } from '../services/invitation'
import { getRoleDisplayName } from '../utils/roles'
import { setAccessToken, setCompanyId } from '../utils/axios'
import { AlertCircle, Eye, EyeOff } from 'lucide-react'
import { toast } from 'sonner'

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
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

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
      const response = await acceptInvitation(token, {
        password,
        confirmPassword,
        fullName: fullName.trim(),
      })

      // Auto-login with the returned access token
      setAccessToken(response.accessToken)
      if (response.user?.companyId) {
        setCompanyId(response.user.companyId)
      }

      toast.success(t('auth.loginSuccess'))
      navigate('/', { replace: true })
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
      <div className="flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-gray-900">
        <div className="flex flex-col items-center gap-2">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"></div>
          <p className="text-sm text-muted-foreground">{t('invitation.loading')}</p>
        </div>
      </div>
    )
  }

  if (error && !invitation) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-gray-900">
        <Card className="w-full max-w-[500px]">
          <CardHeader>
            <div className="flex items-center gap-2 text-destructive">
              <AlertCircle className="h-6 w-6" />
              <CardTitle className="text-xl">Error</CardTitle>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>Error</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
            <Button className="w-full" onClick={() => navigate('/login')}>
              {t('invitation.goToLogin')}
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (!invitation) {
    return null
  }

  const expirationDate = new Date(invitation.expiresAt)
  const isExpired = expirationDate < new Date()

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-gray-900">
      <Card className="w-full max-w-[500px]">
        <CardHeader>
          <CardTitle className="text-2xl">{t('invitation.youveBeenInvited')}</CardTitle>
          <CardDescription>
            {t(
              'invitation.completeRegistration',
              'Please complete your registration details below.',
            )}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isExpired && (
            <Alert variant="destructive" className="mb-6">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>{t('error')}</AlertTitle>
              <AlertDescription>{t('invitation.expired')}</AlertDescription>
            </Alert>
          )}

          {error && (
            <Alert variant="destructive" className="mb-6">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>{t('error')}</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <div className="mb-6 space-y-4 rounded-lg border bg-muted/50 p-4 text-sm">
            <div className="grid grid-cols-1 gap-1 sm:grid-cols-3">
              <span className="font-medium text-muted-foreground">{t('invitation.company')}</span>
              <span className="font-medium sm:col-span-2">{invitation.companyName}</span>
            </div>
            <div className="grid grid-cols-1 gap-1 sm:grid-cols-3">
              <span className="font-medium text-muted-foreground">{t('auth.email')}</span>
              <span className="font-medium sm:col-span-2">{invitation.email}</span>
            </div>
            <div className="grid grid-cols-1 gap-1 sm:grid-cols-3">
              <span className="font-medium text-muted-foreground">{t('invitation.role')}</span>
              <span className="font-medium sm:col-span-2">
                {getRoleDisplayName(invitation.role)}
              </span>
            </div>
            <div className="grid grid-cols-1 gap-1 sm:grid-cols-3">
              <span className="font-medium text-muted-foreground">{t('invitation.expires')}</span>
              <span className="font-medium sm:col-span-2">
                {expirationDate.toLocaleDateString()} {expirationDate.toLocaleTimeString()}
              </span>
            </div>
          </div>

          <Separator className="my-6" />

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">{t('auth.email')}</Label>
              <Input
                id="email"
                type="email"
                value={invitation.email}
                disabled
                className="bg-muted"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="fullName">{t('invitation.fullName')}</Label>
              <Input
                id="fullName"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
                disabled={submitting || isExpired}
                placeholder="John Doe"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">{t('auth.password')}</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  disabled={submitting || isExpired}
                />
                <button
                  type="button"
                  aria-label={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground/80 hover:text-foreground transition h-8 w-8 rounded-md inline-flex items-center justify-center"
                  onClick={() => setShowPassword((v) => !v)}
                  disabled={submitting || isExpired}
                >
                  {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
              <p className="text-xs text-muted-foreground">{t('invitation.passwordMinLength')}</p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirmPassword">{t('auth.confirmPassword')}</Label>
              <div className="relative">
                <Input
                  id="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                  disabled={submitting || isExpired}
                />
                <button
                  type="button"
                  aria-label={showConfirmPassword ? t('auth.hidePassword') : t('auth.showPassword')}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground/80 hover:text-foreground transition h-8 w-8 rounded-md inline-flex items-center justify-center"
                  onClick={() => setShowConfirmPassword((v) => !v)}
                  disabled={submitting || isExpired}
                >
                  {showConfirmPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </div>

            <Button type="submit" className="w-full" disabled={submitting || isExpired}>
              {submitting ? (
                <>
                  <div className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                  {t('invitation.creatingAccount')}
                </>
              ) : (
                t('invitation.acceptAndCreate')
              )}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
