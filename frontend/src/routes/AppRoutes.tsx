import { Routes, Route, Navigate } from 'react-router-dom'
import { Dashboard } from '@/features/dashboard'
import { CompanySettings } from '@/features/company'
import { Login, ForgotPassword, ResetPassword } from '@/features/auth'
import { UserManagement } from '@/features/users'
import UserProfile from '@/pages/UserProfile'
import { ChartOfAccounts, VoucherList, VoucherForm, VoucherTypeList } from '@/features/accounting'
import AcceptInvitation from '@/pages/AcceptInvitation'
import ProtectedLayout from '@/layouts/ProtectedLayout'
import { RoleGuard, CompanyGuard } from '@/components'

export default function AppRoutes() {
  return (
    <Routes>
      {/* Public Auth routes */}
      <Route path="/login" element={<Login />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />

      {/* Public invitation route */}
      <Route path="/invite/:token" element={<AcceptInvitation />} />

      {/* Protected */}
      <Route
        path="/"
        element={
          <ProtectedLayout>
            <Dashboard />
          </ProtectedLayout>
        }
      />
      <Route
        path="/company"
        element={
          <ProtectedLayout>
            <CompanyGuard requiredRoles={['admin', 'chief_accountant']}>
              <CompanySettings />
            </CompanyGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/users"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant']}>
              <UserManagement />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/profile"
        element={
          <ProtectedLayout>
            <UserProfile />
          </ProtectedLayout>
        }
      />
      <Route
        path="/chart-of-accounts"
        element={
          <ProtectedLayout>
            <ChartOfAccounts />
          </ProtectedLayout>
        }
      />
      <Route
        path="/vouchers"
        element={
          <ProtectedLayout>
            <VoucherList />
          </ProtectedLayout>
        }
      />
      <Route
        path="/vouchers/new"
        element={
          <ProtectedLayout>
            <VoucherForm />
          </ProtectedLayout>
        }
      />
      <Route
        path="/vouchers/:id/edit"
        element={
          <ProtectedLayout>
            <VoucherForm />
          </ProtectedLayout>
        }
      />
      <Route
        path="/voucher-types"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant']}>
              <VoucherTypeList />
            </RoleGuard>
          </ProtectedLayout>
        }
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}


