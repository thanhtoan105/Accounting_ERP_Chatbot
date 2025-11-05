import { useAuth } from './useAuth'
import * as roles from '../utils/roles'

/**
 * Hook for role-based UI logic.
 * Provides convenient access to user role and role checking functions.
 */
export function useRole() {
  const { user } = useAuth()
  const userRole = user?.role

  return {
    role: userRole,
    hasRole: (requiredRole: roles.Role) => roles.hasRole(userRole, requiredRole),
    hasAnyRole: (requiredRoles: roles.Role[]) => roles.hasAnyRole(userRole, requiredRoles),
    hasAllRoles: (requiredRoles: roles.Role[]) => roles.hasAllRoles(userRole, requiredRoles),
    isAdmin: () => roles.isAdmin(userRole),
    isChiefAccountant: () => roles.isChiefAccountant(userRole),
    canManageUsers: () => roles.canManageUsers(userRole),
    canViewReports: () => roles.canViewReports(userRole),
    canCreateVouchers: () => roles.canCreateVouchers(userRole),
    canApproveVouchers: () => roles.canApproveVouchers(userRole),
    canChangeRoles: () => roles.canChangeRoles(userRole),
    getRoleDisplayName: () => roles.getRoleDisplayName(userRole),
    isValidRole: () => roles.isValidRole(userRole),
  }
}
