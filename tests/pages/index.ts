/**
 * Page Object Model - Index
 *
 * Central export for all page objects.
 * Import from this file for cleaner test code.
 */

export { BasePage } from './BasePage';
export { LoginPage } from './LoginPage';
export { DashboardPage } from './DashboardPage';
export { CustomersPage, type CustomerData } from './CustomersPage';
export { SuppliersPage, type SupplierData } from './SuppliersPage';
export { VoucherListPage, VoucherFormPage, type VoucherLineData } from './VouchersPage';
export {
  SalesInvoiceListPage,
  SalesInvoiceFormPage,
  type InvoiceLineItemData,
} from './SalesInvoicesPage';
export {
  PurchaseBillListPage,
  PurchaseBillFormPage,
  type BillLineItemData,
} from './PurchaseBillsPage';
export {
  PaymentListPage,
  PaymentFormPage,
  type PaymentAllocationData,
} from './PaymentsPage';
export {
  ReceiptListPage,
  ReceiptFormPage,
  type ReceiptAllocationData,
} from './ReceiptsPage';
export { ProfilePage, type ProfileData } from './ProfilePage';
export { BankAccountsPage, type BankAccountData } from './BankAccountsPage';
export { AnalyticsPage } from './AnalyticsPage';
