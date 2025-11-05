import { describe, it, expect } from 'vitest'
import * as roles from '../roles'

describe('roles utilities', () => {
  describe('hasRole', () => {
    it('should return true when user has the required role', () => {
      expect(roles.hasRole('admin', 'admin')).toBe(true)
      expect(roles.hasRole('accountant', 'accountant')).toBe(true)
      expect(roles.hasRole('chief_accountant', 'chief_accountant')).toBe(true)
      expect(roles.hasRole('cfo', 'cfo')).toBe(true)
    })

    it('should return false when user does not have the required role', () => {
      expect(roles.hasRole('accountant', 'admin')).toBe(false)
      expect(roles.hasRole('admin', 'cfo')).toBe(false)
    })

    it('should return false for null or undefined role', () => {
      expect(roles.hasRole(null, 'admin')).toBe(false)
      expect(roles.hasRole(undefined, 'admin')).toBe(false)
    })

    it('should be case-insensitive', () => {
      expect(roles.hasRole('ADMIN', 'admin')).toBe(true)
      expect(roles.hasRole('Admin', 'admin')).toBe(true)
    })
  })

  describe('hasAnyRole', () => {
    it('should return true when user has one of the required roles', () => {
      expect(roles.hasAnyRole('admin', ['admin', 'accountant'])).toBe(true)
      expect(roles.hasAnyRole('accountant', ['admin', 'accountant'])).toBe(true)
      expect(roles.hasAnyRole('chief_accountant', ['admin', 'chief_accountant'])).toBe(true)
    })

    it('should return false when user has none of the required roles', () => {
      expect(roles.hasAnyRole('accountant', ['admin', 'cfo'])).toBe(false)
    })
  })

  describe('isAdmin', () => {
    it('should return true for admin role', () => {
      expect(roles.isAdmin('admin')).toBe(true)
    })

    it('should return false for non-admin roles', () => {
      expect(roles.isAdmin('accountant')).toBe(false)
      expect(roles.isAdmin('cfo')).toBe(false)
    })
  })

  describe('canManageUsers', () => {
    it('should return true for admin or chief_accountant', () => {
      expect(roles.canManageUsers('admin')).toBe(true)
      expect(roles.canManageUsers('chief_accountant')).toBe(true)
    })

    it('should return false for accountant or cfo', () => {
      expect(roles.canManageUsers('accountant')).toBe(false)
      expect(roles.canManageUsers('cfo')).toBe(false)
    })
  })

  describe('canChangeRoles', () => {
    it('should return true for admin or chief_accountant', () => {
      expect(roles.canChangeRoles('admin')).toBe(true)
      expect(roles.canChangeRoles('chief_accountant')).toBe(true)
    })

    it('should return false for accountant or cfo', () => {
      expect(roles.canChangeRoles('accountant')).toBe(false)
      expect(roles.canChangeRoles('cfo')).toBe(false)
    })
  })

  describe('getRoleDisplayName', () => {
    it('should return formatted role names', () => {
      expect(roles.getRoleDisplayName('admin')).toBe('Administrator')
      expect(roles.getRoleDisplayName('accountant')).toBe('Accountant')
      expect(roles.getRoleDisplayName('chief_accountant')).toBe('Chief Accountant')
      expect(roles.getRoleDisplayName('cfo')).toBe('CFO')
    })

    it('should handle null or undefined', () => {
      expect(roles.getRoleDisplayName(null)).toBe('Unknown')
      expect(roles.getRoleDisplayName(undefined)).toBe('Unknown')
    })
  })

  describe('isValidRole', () => {
    it('should return true for valid roles', () => {
      expect(roles.isValidRole('admin')).toBe(true)
      expect(roles.isValidRole('accountant')).toBe(true)
      expect(roles.isValidRole('chief_accountant')).toBe(true)
      expect(roles.isValidRole('cfo')).toBe(true)
    })

    it('should return false for invalid roles', () => {
      expect(roles.isValidRole('invalid')).toBe(false)
      expect(roles.isValidRole(null)).toBe(false)
      expect(roles.isValidRole(undefined)).toBe(false)
    })
  })
})
