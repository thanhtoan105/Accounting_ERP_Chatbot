import type { ReactNode } from 'react'
import { useAuth } from '../hooks/useAuth'
import { useRole } from '../hooks/useRole'
import Forbidden403 from '../pages/Forbidden403'
import * as roles from '../utils/roles'

interface CompanyGuardProps {
  children: ReactNode
  requiredRoles?: roles.Role[]
  fallback?: ReactNode
}

/**
 * CompanyGuard component that allows access if:
 * 1. User has no company (companyId is null/undefined) - for first-time company creation
 * 2. User has one of the required roles (for managing existing company)
 *
 * This is useful for the company settings page where users without a company
 * need to create one, but existing company management should be role-restricted.
 */
export default function CompanyGuard({ children, requiredRoles, fallback }: CompanyGuardProps) {
  const { user } = useAuth()
  const { hasAnyRole } = useRole()
  const { role } = useRole()

  // Allow access if user has no company (null or undefined)
  // This allows new users to create their first company
  const hasNoCompany = user?.companyId == null || user?.companyId === undefined

  if (hasNoCompany) {
    // User has no company - allow access for company creation
    return <>{children}</>
  }

  // User has a company - check role requirements
  if (!role || !roles.isValidRole(role)) {
    return fallback || <Forbidden403 message="Your account does not have a valid role assigned." />
  }

  // Check role requirements if specified
  if (requiredRoles && requiredRoles.length > 0) {
    const hasAccess = hasAnyRole(requiredRoles)

    if (!hasAccess) {
      const roleNames = requiredRoles.map(roles.getRoleDisplayName).join(' or ')
      return fallback || <Forbidden403 message={`Access denied. Required role: ${roleNames}`} />
    }
  }

  return <>{children}</>
}
