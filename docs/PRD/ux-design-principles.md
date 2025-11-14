# UX Design Principles

## Overall UX Vision

- Desktop-first accounting workspace optimized for speed and accuracy in voucher entry, review, and reporting.
- Minimal, information-dense layouts with clear hierarchy; reduce cognitive load via sensible defaults and inline validation.
- Embedded, read-only RAG guidance surfaces relevant TT200 excerpts and examples contextually (non-intrusive side panel or inline help).

## Key Interaction Paradigms

- Master-detail shell with left navigation (modules: GL, AP/AR, Cash/Bank, Reports, Admin).
- Table-first browsing (server-paginated) with quick filters, column presets, and keyboard shortcuts for power users.
- Form patterns with immediate validation (Zod/Yup), debounced lookups, and smart field ordering for fast data entry.
- Non-blocking toasts for success, inline errors for validation, modal for destructive actions only.

## Updated UX Decisions (2025-11-10)

- Design system: shadcn/ui (Tailwind + Radix) as base; custom components for accounting flows.
- Design direction: Hybrid “Table Pro + Dense Dashboard”.
- Navigation: persistent left sidebar; breadcrumbs on detail pages; global search (Ctrl+K).
- Voucher templates: users select a template (e.g., Cash Receipt, Cash Payment, Expense) that auto‑fills default Debit/Credit and highlights required dimensions; option to lock or override accounts.
- VoucherLineGrid: keyboard‑first editable grid with row menu, sticky balanced totals (✓/✗), per‑row dimensions, attachment control; validations enforce leaf‑only posting and required dimensions (131/331/154/621).
- DataTablePro for lists: server pagination/sorting/filtering/search; saved filter chips; bulk actions toolbar; row action menu; page size selector (10/20/30/50/100); keyboard shortcuts (↑/↓, Enter, Space, /, Esc, Ctrl+N).
- Inputs:
  - MoneyInput (vi‑VN formatting, 0/2 decimals, allowNegative config).
  - DateRangeFilter with presets (Today, 7 days, This/Last Month, QTD, YTD) and dual‑calendar.
- Sales Invoice: preview (PDF-like) with Edit/Send Email/Download PDF; payment tracking (Posted → Sent → Paid).
- Chatbot: floating [💬 Ask AI] and sidebar panel; quick actions (create voucher from description, find invoice by customer, explain balance sheet).
- Accessibility: visible focus, ARIA roles (grid/gridcell), skip links, high‑contrast toggle.

References:

- UX Spec: `docs/ux-design-specification.md`
- Color themes visualizer: `docs/ux-color-themes.html`
- Design directions mockups: `docs/ux-design-directions.html`
- Wireframes (low‑fi): `docs/wireframes.html`

## Core Screens and Views

- Login → Company landing
- Voucher List (filters: date range, account, status) → Voucher Form (draft/post)
- Customers, Suppliers master data (CRUD)
- Sales Invoice / Purchase Bill forms
- Cash Receipt / Cash Payment forms
- Trial Balance view (period selector) with export
- Financial Statements: Balance Sheet (B01-DN), Income Statement (B02-DN), Cash Flow (direct)
- Admin: Company settings, Period management, Users/Roles
- RAG Help Panel: context-aware guidance with citations

### Component Specifications (Implemented in Frontend Scaffold)

- DataTablePro: enterprise list table with server operations and bulk actions.
- VoucherLineGrid: editable line grid with validations and balancing.
- AccountPicker: TT200-aware combobox; non-postable accounts disabled.
- MoneyInput: locale-aware currency input with strict validation.
- DateRangeFilter: quick presets + dual-calendar.

## Accessibility

- Target AA-aligned color contrast; focus states on all interactive elements; semantic HTML and ARIA where appropriate.
- Keyboard navigation for grids and forms; skip links; screen-reader labels on form fields.

## Branding

- Clean, professional financial UI; neutral palette with accent for primary actions.
- Use Tailwind CSS with CSS variables for theming; Shadcn UI components for consistency; support easy brand override post-MVP.

## Target Device and Platforms

- Desktop-first Web Responsive (primary resolutions: 1920×1080, 1366×768); tablet-friendly for reports and approvals.
- Vietnamese locale default; English optional/partial.

## Detailed UI Specifications

1. Login Screen
   - Email/Password fields
   - Remember me checkbox
   - Forgot password link
2. Dashboard (Company Landing - detailed)
   - Header: Company name, Current period, User menu
   - Key Metrics Cards (4 widgets)
   - Quick Actions (4 buttons)
   - Recent Vouchers Table (last 10)
   - Pending Tasks Panel
   - Period Summary Chart
3. Top Navigation (persistent across all screens)
   - Dashboard | Transactions | Master Data | Reports | Period Mgmt | Settings
   - Global Search (center)
   - Notifications Bell (right)
   - RAG Help Button (right)
   - User Profile Menu (right)
4. Transactions Section
   4.1 Voucher List
   - Filters: Date range, Account, Status, Search
   - Table: Voucher No, Date, Description, Amount, Status
   - Actions: Edit, View, Delete (if draft)
   - [+ New Voucher] button (top-right)
     4.2 Voucher Form (create/edit)
   - Sticky Header: Voucher No, Date, Description, Status
   - Editable Grid: Line items with inline editing
   - Sticky Footer: Totals, Balance check, Action buttons
   - Keyboard shortcuts: Tab, Enter, Ctrl+S
     4.3 Sales Invoice List → Invoice Form
   - Customer selector with search
   - Line items with auto-calculation
   - VAT handling
   - Email invoice option
     4.4 Purchase Bill List → Bill Form
   - Similar to Invoice Form
     4.5 Cash Receipt List → Receipt Form
   - Link to invoice (optional)
   - Cash/Bank account selector
     4.6 Cash Payment List → Payment Form
   - Similar to Receipt Form
5. Master Data Section
   5.1 Customers List → Customer Form (CRUD)
   - Table with search/filter
   - Form: Code (auto), Name, Tax ID, Address, Contact
     5.2 Suppliers List → Supplier Form (CRUD)
   - Similar to Customers
     5.3 Chart of Accounts (view-only in MVP, pre-configured TT200)
   - Tree view with hierarchy
   - Account details: Code, Name, Type, Balance
     5.4 Bank Accounts List → Form
   - Account number, Bank name, Current balance
6. Reports Section
   6.1 Trial Balance
   - Period selector
   - Account range filter
   - Drill-down to vouchers
   - Export: PDF, Excel, CSV
     6.2 Financial Statements
   - Tab view: Balance Sheet | Income Statement | Cash Flow
   - Period comparison (current vs previous)
   - Collapsible sections
   - Drill-down capability
   - Export: PDF, Excel (with TT200 formatting)
     6.3 AP/AR Aging Reports
   - Aging buckets table
   - Drill-down to invoices/bills
   - Export
     6.4 Cash Book / Bank Book
   - Date range selector
   - Transaction list with running balance
   - Export
7. Period Management
   - Fiscal Year setup
   - Period list with status (Open/Closed)
   - Close Period action (with confirmation)
   - Audit log of period operations
8. Admin/Settings Section
   8.1 Company Settings
   - Company info, Logo, Tax ID
   - Fiscal year settings
     8.2 Users & Roles
   - User list with role badges
   - User form: Email, Name, Role selector
   - Role permissions matrix (view-only in MVP)
     8.3 Import/Export
   - Import COA, Opening balances, Master data
   - Export backup data
9. RAG Help Panel (floating, persistent)
   - Trigger: Floating button (bottom-right)
   - Expandable panel (slide from right)
   - Context-aware suggestions based on current screen
   - Chat interface with search input
   - Responses with citations (TT200 article numbers)
   - Collapsible/minimizable
10. Notification Center

- Dropdown from bell icon
- List of notifications with timestamp
- Mark as read
- Link to relevant screen

---
