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
  AccountControls,
  PurchaseBillList,
  PurchaseBillForm,
  SalesInvoiceList,
  SalesInvoiceForm,
  PaymentList,
  PaymentForm,
  ReceiptList,
  ReceiptForm,
  APAgingReport,
  VATReportList,
  VATCorrectionList,
  APAuditTimeline,
  APAuditAbuseView,
  APAuditBackupList,
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
        path="/purchase-bills"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <PurchaseBillList />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/purchase-bills/new"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <PurchaseBillForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/purchase-bills/:billId"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <PurchaseBillForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/sales-invoices"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <SalesInvoiceList />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/sales-invoices/new"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <SalesInvoiceForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/sales-invoices/:invoiceId"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <SalesInvoiceForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/accounting/receipts"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <ReceiptList />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/accounting/receipts/new"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant']}>
              <ReceiptForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/accounting/receipts/:receiptId"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <ReceiptForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/accounting/receipts/:receiptId/edit"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant']}>
              <ReceiptForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/payments"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <PaymentList />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/payments/new"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant']}>
              <PaymentForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/payments/:paymentId"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <PaymentForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/payments/:paymentId/edit"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant']}>
              <PaymentForm />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/ap-aging"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <APAgingReport />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/vat/reports/input"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <VATReportList />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/vat/corrections"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant', 'cfo']}>
              <VATCorrectionList />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/accounting/ap-audit/timeline"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'accountant', 'chief_accountant', 'cfo']}>
              <APAuditTimeline />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/accounting/ap-audit/abuse"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant', 'cfo']}>
              <APAuditAbuseView />
            </RoleGuard>
          </ProtectedLayout>
        }
      />
      <Route
        path="/accounting/ap-audit/backups"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant', 'cfo']}>
              <APAuditBackupList />
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
        path="/account-controls"
        element={
          <ProtectedLayout>
            <RoleGuard requiredRoles={['admin', 'chief_accountant', 'cfo']}>
              <AccountControls />
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
