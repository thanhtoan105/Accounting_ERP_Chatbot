import { Routes, Route, Navigate } from 'react-router-dom'
import { Dashboard } from '@/features/dashboard'
import { CompanySettings } from '@/features/company'
import { Login, ForgotPassword, ResetPassword } from '@/features/auth'
import { UserManagement } from '@/features/users'
import UserProfile from '@/pages/UserProfile'
import {
  VoucherTypeList,
  ChartOfAccounts,
  DefaultAccounts,
  ImportWizard,
  VoucherList,
  VoucherForm,
  VoucherTemplateManagementPage,
} from '@/features/accounting'
import { Customers } from '@/features/customers'
import { Suppliers } from '@/features/suppliers'
import { BankAccounts } from '@/features/bankaccounts'
import { AuditLogs } from '@/features/audit'
import AcceptInvitation from '@/pages/AcceptInvitation'
import ProtectedLayout from '@/layouts/ProtectedLayout'
import { RoleGuard, CompanyGuard, ErrorBoundary } from '@/components'

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
        path="/voucher-types"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant']}>
              <VoucherTypeList />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/vouchers"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <VoucherList />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/vouchers/new"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <VoucherForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/vouchers/:voucherId"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <VoucherForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/voucher-templates"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant', 'cfo']}>
              <VoucherTemplateManagementPage />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/chart-of-accounts"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant']}>
              <ChartOfAccounts />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/default-accounts"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant']}>
              <DefaultAccounts />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/accounting/imports"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant']}>
              <ImportWizard />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/customers"
        element={
          <ProtectedLayout>
            <Customers />
          </ProtectedLayout>
        }
      />
      <Route
        path="/suppliers"
        element={
          <ProtectedLayout>
            <ErrorBoundary>
              <Suppliers />
            </ErrorBoundary>
          </ProtectedLayout>
        }
      />
      <Route
        path="/bank-accounts"
        element={
          <ProtectedLayout>
            <ErrorBoundary>
              <BankAccounts />
            </ErrorBoundary>
          </ProtectedLayout>
        }
      />
      <Route
        path="/admin/audit-logs"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant']}>
              <AuditLogs />
            </RoleGuard>
          </ProtectedLayout>
        }
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
