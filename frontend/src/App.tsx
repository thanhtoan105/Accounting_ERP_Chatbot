import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Dashboard from './pages/Dashboard'
import CompanySettings from './pages/Admin/CompanySettings'
import Login from './pages/Login'
import ForgotPassword from './pages/ForgotPassword'
import ResetPassword from './pages/ResetPassword'
import UserManagement from './pages/UserManagement'
import UserProfile from './pages/UserProfile'
import ChartOfAccounts from './pages/ChartOfAccounts'
import VoucherList from './pages/VoucherList'
import VoucherForm from './pages/VoucherForm'
import AcceptInvitation from './pages/AcceptInvitation'
import { Toaster } from 'sonner'
import ProtectedLayout from './layouts/ProtectedLayout'
import RoleGuard from './components/RoleGuard'
import CompanyGuard from './components/CompanyGuard'

function App() {
  return (
    <>
      <Toaster richColors />
      <BrowserRouter>
        <Routes>
          {/* Auth routes - public */}
          <Route path="/login" element={<Login />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />

          {/* Public invitation acceptance route */}
          <Route path="/invite/:token" element={<AcceptInvitation />} />

          {/* Protected routes */}
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

          {/* Default redirect */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </>
  )
}

export default App
