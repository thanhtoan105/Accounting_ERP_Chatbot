import type { ReactNode } from 'react'
import { useAuth } from '../../hooks/useAuth'
import { useRole } from '../../hooks/useRole'
import Forbidden403 from '../../pages/Forbidden403'
import * as roles from '../../utils/roles'

interface CompanyGuardProps {
  children: ReactNode
  requiredRoles?: roles.Role[]
  fallback?: ReactNode
}

// <CHANGE> moved CompanyGuard into components/guards and fixed relative imports
export default function CompanyGuard({ children, requiredRoles, fallback }: CompanyGuardProps) {
  const { user, loading } = useAuth()
  const { hasAnyRole } = useRole()
  const { role } = useRole()

  if (loading && !user) {
    return null
  }

  const hasNoCompany = user?.companyId == null || user?.companyId === undefined

  if (hasNoCompany) {
    return <>{children}</>
  }

  if (!role || !roles.isValidRole(role)) {
    return fallback || <Forbidden403 message="Your account does not have a valid role assigned." />
  }

  if (requiredRoles && requiredRoles.length > 0) {
    const hasAccess = hasAnyRole(requiredRoles)

    if (!hasAccess) {
      const roleNames = requiredRoles.map(roles.getRoleDisplayName).join(' or ')
      return fallback || <Forbidden403 message={`Access denied. Required role: ${roleNames}`} />
    }
  }

  return <>{children}</>
}


