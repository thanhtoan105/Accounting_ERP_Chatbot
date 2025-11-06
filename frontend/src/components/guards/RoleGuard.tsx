import type { ReactNode } from 'react'
import { useRole } from '../../hooks/useRole'
import { useAuth } from '../../hooks/useAuth'
import Forbidden403 from '../../pages/Forbidden403'
import * as roles from '../../utils/roles'

interface RoleGuardProps {
  children: ReactNode
  requiredRole?: roles.Role
  requiredRoles?: roles.Role[]
  requireAll?: boolean
  fallback?: ReactNode
}

// <CHANGE> moved RoleGuard into components/guards and fixed relative imports
export default function RoleGuard({
  children,
  requiredRole,
  requiredRoles,
  requireAll = false,
  fallback,
}: RoleGuardProps) {
  const { loading, user } = useAuth()
  const { role, hasRole, hasAnyRole, hasAllRoles } = useRole()

  if (loading && !user) {
    return null
  }

  if (!role || !roles.isValidRole(role)) {
    return fallback || <Forbidden403 message="Your account does not have a valid role assigned." />
  }

  if (requiredRole) {
    if (!hasRole(requiredRole)) {
      return (
        fallback || (
          <Forbidden403
            message={`Access denied. Required role: ${roles.getRoleDisplayName(requiredRole)}`}
          />
        )
      )
    }
    return <>{children}</>
  }

  if (requiredRoles && requiredRoles.length > 0) {
    const hasAccess = requireAll ? hasAllRoles(requiredRoles) : hasAnyRole(requiredRoles)

    if (!hasAccess) {
      const roleNames = requiredRoles.map(roles.getRoleDisplayName).join(' or ')
      return fallback || <Forbidden403 message={`Access denied. Required role: ${roleNames}`} />
    }
    return <>{children}</>
  }

  return <>{children}</>
}

