import type { ReactNode } from 'react'
import { useRole } from '../hooks/useRole'
import Forbidden403 from '../pages/Forbidden403'
import * as roles from '../utils/roles'

interface RoleGuardProps {
  children: ReactNode
  requiredRole?: roles.Role
  requiredRoles?: roles.Role[]
  requireAll?: boolean
  fallback?: ReactNode
}

/**
 * RoleGuard component that conditionally renders children based on user role.
 * Shows 403 Forbidden page if user doesn't have required role(s).
 */
export default function RoleGuard({
  children,
  requiredRole,
  requiredRoles,
  requireAll = false,
  fallback,
}: RoleGuardProps) {
  const { role, hasRole, hasAnyRole, hasAllRoles } = useRole()

  // Handle missing role
  if (!role || !roles.isValidRole(role)) {
    return fallback || <Forbidden403 message="Your account does not have a valid role assigned." />
  }

  // Check single required role
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

  // Check multiple required roles
  if (requiredRoles && requiredRoles.length > 0) {
    const hasAccess = requireAll ? hasAllRoles(requiredRoles) : hasAnyRole(requiredRoles)

    if (!hasAccess) {
      const roleNames = requiredRoles.map(roles.getRoleDisplayName).join(' or ')
      return fallback || <Forbidden403 message={`Access denied. Required role: ${roleNames}`} />
    }
    return <>{children}</>
  }

  // No role requirements specified, render children
  return <>{children}</>
}
