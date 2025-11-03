/**
 * Role utility functions for role-based access control.
 * Role values match backend: 'admin', 'accountant', 'chief_accountant', 'cfo'
 */

export type Role = 'admin' | 'accountant' | 'chief_accountant' | 'cfo'

/**
 * Check if user has a specific role.
 */
export function hasRole(userRole: string | null | undefined, requiredRole: Role): boolean {
  if (!userRole) return false
  return userRole.toLowerCase() === requiredRole.toLowerCase()
}

/**
 * Check if user has any of the required roles.
 */
export function hasAnyRole(userRole: string | null | undefined, requiredRoles: Role[]): boolean {
  if (!userRole) return false
  return requiredRoles.some((role) => userRole.toLowerCase() === role.toLowerCase())
}

/**
 * Check if user has all of the required roles.
 */
export function hasAllRoles(userRole: string | null | undefined, requiredRoles: Role[]): boolean {
  if (!userRole) return false
  return requiredRoles.every((role) => userRole.toLowerCase() === role.toLowerCase())
}

/**
 * Check if user is admin.
 */
export function isAdmin(userRole: string | null | undefined): boolean {
  return hasRole(userRole, 'admin')
}

/**
 * Check if user is chief accountant.
 */
export function isChiefAccountant(userRole: string | null | undefined): boolean {
  return hasRole(userRole, 'chief_accountant')
}

/**
 * Check if user is admin or chief accountant (can manage users).
 */
export function canManageUsers(userRole: string | null | undefined): boolean {
  return hasAnyRole(userRole, ['admin', 'chief_accountant'])
}

/**
 * Check if user can view reports.
 */
export function canViewReports(userRole: string | null | undefined): boolean {
  return hasAnyRole(userRole, ['admin', 'accountant', 'chief_accountant', 'cfo'])
}

/**
 * Check if user can create vouchers.
 */
export function canCreateVouchers(userRole: string | null | undefined): boolean {
  return hasAnyRole(userRole, ['admin', 'accountant', 'chief_accountant'])
}

/**
 * Check if user can approve vouchers.
 */
export function canApproveVouchers(userRole: string | null | undefined): boolean {
  return hasAnyRole(userRole, ['admin', 'chief_accountant'])
}

/**
 * Check if user can change roles.
 */
export function canChangeRoles(userRole: string | null | undefined): boolean {
  return hasAnyRole(userRole, ['admin', 'chief_accountant'])
}

/**
 * Get display name for role.
 */
export function getRoleDisplayName(role: string | null | undefined): string {
  if (!role) return 'Unknown'
  switch (role.toLowerCase()) {
    case 'admin':
      return 'Administrator'
    case 'accountant':
      return 'Accountant'
    case 'chief_accountant':
      return 'Chief Accountant'
    case 'cfo':
      return 'CFO'
    default:
      return role
  }
}

/**
 * Check if role value is valid.
 */
export function isValidRole(role: string | null | undefined): role is Role {
  if (!role) return false
  const validRoles: Role[] = ['admin', 'accountant', 'chief_accountant', 'cfo']
  return validRoles.includes(role.toLowerCase() as Role)
}

/**
 * Get hierarchy level for role comparison.
 * Higher number = higher privilege.
 */
function getRoleLevel(role: Role): number {
  switch (role) {
    case 'admin':
      return 4
    case 'chief_accountant':
      return 3
    case 'cfo':
      return 2
    case 'accountant':
      return 1
    default:
      return 0
  }
}

/**
 * Check if a role can manage another role.
 * Rules:
 * - ADMIN can manage everyone (except themselves, handled separately)
 * - CHIEF_ACCOUNTANT can only manage lower roles (ACCOUNTANT, CFO)
 * - Others cannot manage anyone
 */
export function canManageRole(
  requesterRole: string | null | undefined,
  targetRole: string | null | undefined,
): boolean {
  if (!requesterRole || !targetRole) return false

  const requester = requesterRole.toLowerCase() as Role
  const target = targetRole.toLowerCase() as Role

  if (requester === 'admin') {
    return true // ADMIN can manage everyone
  }

  if (requester === 'chief_accountant') {
    // CHIEF_ACCOUNTANT can only manage lower roles (ACCOUNTANT, CFO)
    return target === 'accountant' || target === 'cfo'
  }

  return false // ACCOUNTANT and CFO cannot manage anyone
}

/**
 * Check if a role can assign another role.
 * Rules:
 * - Only ADMIN can assign ADMIN role
 * - CHIEF_ACCOUNTANT cannot promote (cannot assign roles >= their own)
 */
export function canAssignRole(
  requesterRole: string | null | undefined,
  newRole: string | null | undefined,
): boolean {
  if (!requesterRole || !newRole) return false

  const requester = requesterRole.toLowerCase() as Role
  const assigned = newRole.toLowerCase() as Role

  // Only ADMIN can assign ADMIN role
  if (assigned === 'admin' && requester !== 'admin') {
    return false
  }

  // CHIEF_ACCOUNTANT cannot promote (cannot assign roles >= their own)
  if (requester === 'chief_accountant') {
    const requesterLevel = getRoleLevel(requester)
    const assignedLevel = getRoleLevel(assigned)
    if (assignedLevel >= requesterLevel) {
      return false
    }
  }

  // Use canManageRole to verify permission
  return canManageRole(requesterRole, newRole)
}

/**
 * Get available roles that a user can assign.
 * - ADMIN can assign all roles
 * - CHIEF_ACCOUNTANT can only assign ACCOUNTANT and CFO
 */
export function getAssignableRoles(requesterRole: string | null | undefined): Role[] {
  if (!requesterRole) return []

  const requester = requesterRole.toLowerCase() as Role

  if (requester === 'admin') {
    return ['admin', 'accountant', 'chief_accountant', 'cfo']
  }

  if (requester === 'chief_accountant') {
    return ['accountant', 'cfo']
  }

  return []
}
