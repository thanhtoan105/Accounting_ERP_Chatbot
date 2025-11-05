import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { LoginForm as LoginFormComponent } from '@/components/auth/LoginForm'

export type LoginProps = {
  onSuccess?: (user: { id: number; email: string; fullName: string; role: string }) => void
}

export default function Login({ onSuccess }: LoginProps = {}) {
  const [searchParams, setSearchParams] = useSearchParams()
  const [accountCreated, setAccountCreated] = useState(false)

  useEffect(() => {
    if (searchParams.get('accountCreated') === 'true') {
      setAccountCreated(true)
      setSearchParams({}, { replace: true })
    }
  }, [searchParams, setSearchParams])

  return (
    <div className="bg-muted flex min-h-svh flex-col items-center justify-center p-6 md:p-10">
      <div className="w-full max-w-sm md:max-w-4xl">
        <LoginFormComponent accountCreated={accountCreated} onSuccess={onSuccess} />
      </div>
    </div>
  )
}

