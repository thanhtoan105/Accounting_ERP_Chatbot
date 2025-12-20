import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Building2, LogOut, Mail } from 'lucide-react'

export default function AwaitingCompanyPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { logout } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)

  const handleLogout = async () => {
    setLoggingOut(true)
    try {
      await logout()
      navigate('/login', { replace: true })
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <div className="min-h-[100dvh] w-full grid place-items-center px-4 bg-background">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center space-y-4">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-muted">
            <Building2 className="h-8 w-8 text-muted-foreground" />
          </div>
          <CardTitle className="text-2xl">
            {t('awaitingCompany.title', "You don't belong to any company yet")}
          </CardTitle>
          <CardDescription className="text-base">
            {t(
              'awaitingCompany.description',
              'Your account has been created, but you are not yet assigned to a company.',
            )}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="rounded-lg border bg-muted/50 p-4 space-y-3">
            <div className="flex items-start gap-3">
              <Mail className="h-5 w-5 text-muted-foreground mt-0.5" />
              <div className="space-y-1">
                <p className="text-sm font-medium">
                  {t('awaitingCompany.contactAdmin', 'Contact your administrator')}
                </p>
                <p className="text-sm text-muted-foreground">
                  {t(
                    'awaitingCompany.contactAdminDescription',
                    'Please reach out to your company administrator to request an invitation to join their organization.',
                  )}
                </p>
              </div>
            </div>
          </div>

          <Button variant="outline" className="w-full" onClick={handleLogout} disabled={loggingOut}>
            <LogOut className="mr-2 h-4 w-4" />
            {loggingOut ? t('auth.loggingOut', 'Logging out...') : t('auth.logout', 'Logout')}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
