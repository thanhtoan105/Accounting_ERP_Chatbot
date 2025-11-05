# accounting Product Requirements Document (PRD)

**Author:** thanhtoan
**Date:** 2025-10-30
**Project Level:** 4
**Target Scale:** enterprise

---

## Goals and Background Context

### Goals

- Automate core accounting operations: invoicing, AR/AP tracking, cash receipts/payments, and consistent, automated GL posting.
- Provide near real-time financial dashboards (latency ≤ 5 phút), including revenue/expense analytics, AR/AP aging, and cash flow via BI (Metabase/Power BI).
- Support end-to-end statutory financial reporting: Balance Sheet (B01-DN), Income Statement (B02-DN), and Cash Flows (B03-DN), mapped to the Vietnamese Chart of Accounts (COA, TT200).
- Enable AI-powered “contextual Q&A” (accuracy ≥ 90% with source citations/trace; fallback if data incomplete), answering queries directly from indexed accounting data (ex: “Tình hình công nợ...”).
- Minimize manual work (reduce manual steps by ≥ 30%) and accounting errors (reduce misposting by ≥ 50%) through process automation and GL consistency.
- Guarantee compliance with enterprise standards: period locking, audit trail/journal logging, maker–checker process, and standardized COA.
- Enforce security and row-level access controls: strict, role-based permissions throughout all modules.
- Deliver a robust technical architecture: PostgreSQL (Supabase) database schema, Spring Boot backend, React frontend, Vector DB (Pinecone), and n8n-based pipeline for RAG/embedding.
- Showcase a seamless workflow in demo: user can initiate transactions, see real-time results in BI, and interact with an embedded chatbot for immediate, accurate answers—all with traceable source support.

### Background Context

This project is both a practical learning capstone and a response to real business pain points: finance teams and leadership need up-to-the-minute AR/AP and cashflow data, accessible through self-service analytics, dashboards, and AI-based Q&A—without delays from manual, ad hoc reporting.

The “why now”: curriculum and enterprise needs are converging:
- Educational goals: uniting accounting principles, database design, BI, and AI agents in a working ERP module.
- Business drivers: urgent demand for agility, accuracy, and transparency—shorter reporting cycles, compliance with TT200, and workforce enablement via automation and self-service.
- Solution boundaries and explicit MVP scope:
  - Single-currency (VND) only; deep e-invoice integration and multi-currency, IFRS compliance are postponed.
  - AI Q&A features must cite original data sources (with trace/guardrails), and provide clear fallback if data is incomplete/unavailable.
  - Security is central: every function is role-restricted, and audit trail is compulsory.
- Technical commitments: integration of regulated accounting schema, fast and accurate BI, automated RAG pipeline, and robust, explainable chatbot.
- The product will ship as a working ERP submodule, focused solely on the included accounting flows and reports—suitable for both demonstration and practical use within the stated constraints.

---

## Requirements

### Functional Requirements

**1. Foundation & Authentication**
- **FR01: Secure Login:** The system must support email/password login with JWT authentication, including "Remember Me," password reset, and secure sessions using HttpOnly/Secure cookies.
- **FR02: Role-Based Access Control (RBAC):** The system must define four roles with clear permissions:
    - **Admin (Technical):** Manages users, system configurations, and company settings.
    - **Accountant:** Creates documents, views reports, manages master data.
    - **Chief Accountant:** Closes accounting periods, approves documents, full accounting access, views audit logs.
    - **CFO:** View-only access to financial reports and data.
- **FR03: Company Profile Management:** An Admin or Chief Accountant must be able to configure company information (name, tax code, address, logo, fiscal year start date) with validation according to Vietnamese standards.
- **FR04: Accounting Period Management:** The system must support the creation of fiscal years with monthly periods, display their status (Open/Closed), and allow a Chief Accountant or CFO to close periods with validation.
- **FR05: Period Closing Controls:** Before closing a period, the system must validate that there are no 'Draft' documents and all journal entries are balanced (Debit = Credit), require confirmation, and record an audit trail (closed_by, closed_at, hash_digest).
- **FR06: Prevent Posting to Closed Periods:** The system must reject any attempt to create/edit documents in a closed period and return a clear error message.
- **FR07: Basic Audit Trail:** The system must record an append-only log for critical actions: document creation (who/when), posting (who/when). Logs cannot be deleted or modified.
- **FR08: Health Check Endpoint:** The system must provide an API endpoint that returns a system status (200 OK) for infrastructure monitoring.

**2. Chart of Accounts & General Ledger Core**
- **FR09: View Chart of Accounts (COA) per TT200:** The system must display the COA in a 3-level structure (1xx, 11x, 111) compliant with Circular 200, showing account code, Vietnamese name, account type, normal balance side (Debit/Credit), account group (Asset/Liability/Equity/Revenue/Expense), and a 'postable' flag.
- **FR10: Leaf-Only Posting Validation:** The system must only permit posting to leaf accounts (those with no sub-accounts) and block posting to parent accounts.
- **FR11: Mandatory Dimension Validation:** The system must validate entries based on metadata in an `account_controls` table:
    - Account 131 (AR): requires `customer_id`.
    - Account 331 (AP): requires `vendor_id`.
    - Accounts 154/621 (COGS/Expenses): requires `cost_center_id` and/or `item_id`.
- **FR12: COA Search:** The system must support searching by account code (exact/prefix) and Vietnamese name with unaccented support (e.g., "nha" matches "nhà").
- **FR13: Create and Post Journal Vouchers:** The system must allow the creation of journal vouchers with an auto-generated number (format: VC{YYYY}-{seq}), date, description, optional attachments, and multiple detail lines (account, Dr, Cr). Validations must include: Dr or Cr per line (not both, not neither), positive amounts, valid/postable accounts, required dimensions, date within an open period, and Total Debit = Total Credit before posting.
- **FR14: Document Workflow - Draft & Posted:** The system must support two statuses:
    - **Draft:** Can be edited/deleted with a confirmation dialog.
    - **Posted:** Read-only. Adjustments require creating a reversal voucher referencing the original.
- **FR15: Maker-Checker for General Journal (Optional):** The system must support an optional maker-checker approval workflow for General Journals (disabled for MVP, configurable for production).
- **FR16: Generate Trial Balance (S06-DN):** The system must automatically generate a Trial Balance report showing Account Code, Name, Opening Balance, Debit/Credit transactions, and Closing Balance, with filtering by period/account range and CSV/Excel export.

**3. Accounts Payable (AP)**
- **FR17: Supplier Master Data Management:** The system must support CRUD operations for suppliers with fields for code (auto-gen), name, tax code, address, etc., linked to account 331.
- **FR18: Create Purchase Bills:** The system must allow the creation of purchase bills with invoice number, date, supplier, line items (description, qty, price), VAT rate selection (0%, 5%, 10%, exempt), and automated journal entry (Dr Expense / Cr AP). Statuses: Unpaid / Paid / Partially Paid.
- **FR19: Record Payments for Purchase Bills:** The system must allow the creation of payment vouchers linked to purchase bills, automatically updating the bill's status and remaining balance.
- **FR20: AP Aging Report:** The system must generate an AP Aging report grouped by supplier, showing total payables and overdue amounts by age bracket (Current, 1-30, 31-60, 61-90, >90 days).
- **FR21: Maker-Checker Workflow for AP:** The system must implement a dual-approval process for large purchase bills and payments (Maker: Accountant, Checker: Chief Accountant/CFO).

**4. Accounts Receivable (AR)**
- **FR22: Customer Master Data Management:** The system must support CRUD operations for customers, linked to account 131.
- **FR23: Create Sales Invoices:** The system must allow the creation of sales invoices with an automated journal entry (Dr AR / Cr Revenue). Statuses: Unpaid / Paid / Partially Paid.
- **FR24: Record Customer Payments:** The system must allow the creation of receipt vouchers linked to sales invoices, automatically updating the invoice's status and remaining balance.
- **FR25: AR Aging Report:** The system must generate an AR Aging report grouped by customer, with standard aging brackets.
- **FR26: Maker-Checker Workflow for AR:** The system must implement a dual-approval process for large sales invoices and receipts.

**5. Cash & Bank Management**
- **FR27: Manage Cash/Bank Accounts:** The system must manage a list of cash/bank accounts, linked to accounts 111 (Cash) and 112 (Bank).
- **FR28: Record Cash Receipts:** The system must allow the creation of receipt vouchers with automated journal entries (Dr Cash/Bank / Cr Related Account).
- **FR29: Record Cash Payments:** The system must allow the creation of payment vouchers with automated journal entries (Dr Related Account / Cr Cash/Bank).
- **FR30: Real-time Bank/Cash Book Balance:** The system must automatically update balances after each transaction, showing opening, in, out, and closing balances.
- **FR31: Manual Bank Reconciliation:** The system must support uploading a bank statement (CSV) for manual matching against system transactions, flagging reconciled items, and reporting discrepancies.

**6. Financial Reports**
- **FR32: Generate Balance Sheet (B01-DN):** The system must generate the Balance Sheet report per Circular 200 format, with comparative columns (opening/closing) and PDF/Excel export.
- **FR33: Generate Income Statement (B02-DN):** The system must generate the Income Statement per Circular 200 format, with period comparison and PDF/Excel export.
- **FR34: Generate Cash Flow Statement (B03-DN):** The system must generate the Cash Flow Statement using the direct method, with classification of activities and PDF/Excel export.
- **FR35: Generate Account Balance Sheet (F01):** The system must generate the F01 report for tax authorities, detailed by account, with Excel export.
- **FR36: Drill-Down from Reports to Vouchers:** Users must be able to click a number on a financial report and see the list of underlying transactions that constitute it.
- **FR37: Real-time BI Dashboard:** The system must provide a dashboard (latency ≤5 minutes, auto-refresh) showing Revenue vs. Expense, AR Aging, AP Aging, and Cash Flow.

**7. AI RAG Chatbot**
- **FR38: Chatbot UI:** The system must provide a chatbot interface as an expandable floating panel.
- **FR39: Natural Language Q&A (Vietnamese):** The chatbot must answer questions like "Which customers have overdue debts?" or "What is this month's cash flow?".
- **FR40: Automated Embedding Pipeline:** An n8n workflow must automatically index accounting data into Pinecone nightly at 2:00 AM, with email alerts on failure.
- **FR41: Source Citation in Answers:** The chatbot must provide citations (e.g., TT200 article, link to source data) and a confidence score for each answer.
- **FR42: Context-Aware Guidance:** The chatbot should provide helpful suggestions based on the user's current screen (e.g., on the voucher screen, offer guidance on TT200-compliant entry).

**8. Admin & Utilities**
- **FR43: Import Data from Excel:** The system must support importing COA, opening balances, and customer/supplier lists from Excel templates.
- **FR44: Export Master Data for Backup:** The system must allow the export of all master data to CSV/Excel.
- **FR45: User Management:** The Admin must be able to create, edit, disable, and assign roles to users.
- **FR46: Notification Center & Global Search:** The UI must include a notification center and a global search bar for finding vouchers, customers, etc.

**9. Security & Compliance**
- **FR47: Enforce RBAC at API Level:** Permissions must be enforced on the backend. A forbidden action attempt must return a 403 error.
- **FR48: Immutable Audit Trail:** The audit trail must be append-only, stored in a separate table with referential integrity.
- **FR49: Data Validation & Integrity:** The system must enforce double-entry (Dr=Cr), foreign key constraints, positive amounts only, valid dates, and required fields at both frontend and backend levels.
- **FR50: 100% Compliance with Circular 200/2014/TT-BTC:** The system must fully adhere to the COA structure, financial report formats, data retention rules (10 years), and period closing procedures.

### Non-Functional Requirements

**1. Performance (NFR1-NFR4)**
-   **NFR1 (Response Times):** The system must have acceptable response times for typical accounting operations (guidelines: page load < 2s, simple form submission < 1s, report generation < 5s for typical date ranges). No strict P95 targets in MVP; optimize as needed.
-   **NFR2 (Cold Start):** The system must cold start in under 3 seconds.
-   **NFR3 (Concurrency):** The system must support at least 20 concurrent users for MVP. Scalability to 50+ is deferred to Post-MVP.
-   **NFR4 (Database Queries):** Queries must be optimized with proper indexes (on foreign keys, dates, account codes), avoidance of N+1 patterns, and monitoring for slow queries (>1s).

**2. Security (NFR5-NFR9)**
-   **NFR5 (RBAC):** Role-based permissions must be enforced at the API level; frontend UI hiding is not a security boundary.
-   **NFR6 (Authentication):** The system must use secure authentication with JWTs, strong password hashing (Bcrypt/Argon2), and no hardcoded secrets (use environment variables).
-   **NFR7 (Data in Transit):** HTTPS/TLS is mandatory for production, with cookies flagged as `HttpOnly` and `Secure`.
-   **NFR8 (Audit Trail):** The audit trail must be append-only, with no modifications or deletions allowed, stored in a separate table with referential integrity.
-   **NFR9 (Security Best Practices):** MVP will include input validation, parameterized queries (to prevent SQL injection), default XSS protection (React), and CSRF protection. Full OWASP compliance, rate limiting, and WAF are deferred.

**3. Compliance & Data Integrity (NFR10-NFR14)**
-   **NFR10 (Circular 200 Compliance):** The system must fully comply with Circular 200 regarding COA structure, financial statement formats, and data retention (10 years).
-   **NFR11 (Double-Entry Invariant):** Every voucher must be balanced (Total Debit = Total Credit), with validation enforced at both frontend and backend.
-   **NFR12 (Period Close Integrity):** The system must prevent any modifications to data within a closed accounting period.
-   **NFR13 (Referential Integrity):** All database relationships must be enforced with foreign key constraints to prevent orphaned records.
-   **NFR14 (Data Validation):** The system must enforce validation rules for account code formats, positive-only amounts, and dates within open periods.

**4. Usability (NFR15-NFR18)**
-   **NFR15 (Vietnamese UI):** The UI must be mandatorily in Vietnamese, including all labels, messages, number formats (e.g., `1.000.000,00`), and date formats (`dd/MM/yyyy`).
-   **NFR16 (Responsive UI):** The desktop-first design must support common resolutions (1920x1080, 1366x768) and be responsive for key flows on tablets. Mobile optimization is deferred.
-   **NFR17 (Onboarding):** The system must provide a good onboarding experience (target: reduce time by ≥30% with RAG chatbot aid), including in-app help, sample data, and a getting-started guide.
-   **NFR18 (User Feedback):** The system must include an embedded CSAT survey (target: ≥4/5) and a bug report form.

**5. Maintainability & Quality (NFR19-NFR22)**
-   **NFR19 (Modular Architecture):** The codebase must follow a modular architecture with clear separation of layers (API, domain, persistence) and bounded contexts (GL, AP/AR, etc.), following Spring Boot best practices.
-   **NFR20 (Test Coverage):** The system must have at least 60% test coverage for the business logic layer, including unit and integration tests for critical flows. E2E tests are deferred.
-   **NFR21 (Documentation):** The code must be documented with JavaDoc for public APIs, READMEs per module, OpenAPI/Swagger for APIs, and database schema documentation.
-   **NFR22 (Database Migrations):** The system must use Flyway for versioned database migrations, with a documented rollback strategy.

**6. Localization (NFR23)**
-   **NFR23 (i18n Support):** The system must have i18n infrastructure with Vietnamese as mandatory and English as optional/partial for MVP.

**7. Observability (NFR24-NFR25)**
-   **NFR24 (Structured Logging):** The system must provide structured logging with request ID tracking and masked sensitive data. Centralized logging is deferred.
-   **NFR25 (Basic Monitoring):** The system must provide a health check endpoint, database connection pool monitoring, and slow query logs. Full metrics/tracing is deferred.

**8. Caching (NFR26)**
-   **NFR26 (Redis Caching):** The system must use Redis to cache reports (trial balance, aging, financial statements), with manual invalidation after posts or period close.

**9. Browser & Platform Support (NFR27)**
-   **NFR27 (Browser Support):** The system must support the latest 2 versions of Chrome, Edge, and Safari. IE11 is not supported.

**10. Deployment (NFR28-NFR29)**
-   **NFR28 (Containerization):** The application must be containerized using Docker for both frontend and backend, with Docker Compose for local development. Kubernetes is deferred.
-   **NFR29 (CI/CD Pipeline):** There must be a CI/CD pipeline (e.g., GitHub Actions) for automated builds, tests, and deployment to staging, with a manual approval gate for production.

---

## User Journeys

**Journey 1: Procure-to-Pay (AP Clerk & Chief Accountant)**

1.  **Persona:** AP Clerk (Maker) & Chief Accountant (Checker)
2.  **Goal:** Process a vendor bill and pay it accurately and on time, with proper controls.
3.  **Steps:**
    *   An **AP Clerk** receives a bill from a new vendor. They log in, navigate to "Suppliers", and create a new supplier record.
    *   The Clerk navigates to "Purchase Bills" and creates a new bill, entering the vendor, invoice number, and date. For each line item (e.g., raw materials), they select an expense account. **The system validates the account against TT200 and checks for mandatory dimensions (e.g., requires `cost_center_id` for account 621).**
    *   The Clerk selects the applicable **VAT rate (e.g., 10%)**, as required by FR18, for record-keeping.
    *   The bill's total exceeds the approval threshold. Upon submission, it enters a "Pending Approval" state.
    *   The **Chief Accountant** receives an **in-app notification (via the bell icon)**. They log in, review the bill details, and click "Approve". The bill is now "Posted" and reflected in the AP Aging report.
    *   On the due date, the **AP Clerk** creates a new payment voucher. **The voucher is explicitly linked to the approved bill (via `bill_id`) for traceability.**
    *   The system automatically generates the correct GL entry (Dr. AP, Cr. Bank), and the bill's status changes to "Paid".
    *   The Clerk views the AP Aging report and confirms the bill is no longer outstanding.

**Journey 2: Order-to-Cash (AR Clerk & CFO)**

1.  **Persona:** AR Clerk (Maker) & CFO (Viewer)
2.  **Goal:** Invoice an existing customer, ensure timely payment, and provide visibility to management.
3.  **Steps:**
    *   An **AR Clerk** logs in and navigates to "Sales Invoices" to create an invoice for an **existing customer**.
    *   They select the customer, add line items, and set the due date. The invoice total is below the threshold for mandatory approval.
    *   Upon submission, **the invoice is auto-posted** as per the rules defined in FR26. The system generates the correct GL entry (Dr. AR, Cr. Revenue).
    *   **The system automatically schedules a reminder.** Three days before the due date, it sends an alert to the AR Clerk's notification center about the upcoming payment.
    *   The **CFO** logs in to the BI Dashboard, views the AR Aging report, and sees the outstanding invoice.
    *   After the customer pays, the **AR Clerk** creates a "Cash Receipt", applying the payment to the invoice. The status updates to "Paid", and the GL is updated.
    *   The **CFO** later refreshes the dashboard and sees the updated AR and cash balances.

**Journey 3: Financial Closing & Reporting (Chief Accountant)**

1.  **Persona:** Chief Accountant
2.  **Goal:** Close the monthly accounting period securely and generate accurate financial statements.
3.  **Steps:**
    *   On the first day of the new month, the **Chief Accountant** logs in to close the previous month.
    *   They run the "Trial Balance" to ensure all accounts are in balance.
    *   They navigate to "Accounting Periods" and initiate the "Close Period" action. The system runs its pre-flight checks.
    *   After confirmation, the period is locked. The Chief Accountant then navigates to the **Audit Trail module to verify that the period close event was logged correctly with their user ID and a timestamp**, satisfying FR5.
    *   They proceed to the "Reports" module. They generate the "Income Statement", which **uses the system's automatic mapping from GL accounts to the official B02-DN line items as required by TT200**.
    *   They export the report to PDF.
    *   A manager asks about a specific expense. The Chief Accountant uses the **RAG Chatbot**: "What transactions make up the 'Software Subscription' expense for last month?"
    *   The chatbot replies with a list of vouchers, **displaying a confidence score of 95% and providing a source citation for each item (e.g., "Voucher #VC2025-123, posted by accountant@demo.com")**, fulfilling FR41.

---

## UX Design Principles

### Overall UX Vision

- Desktop-first accounting workspace optimized for speed and accuracy in voucher entry, review, and reporting.
- Minimal, information-dense layouts with clear hierarchy; reduce cognitive load via sensible defaults and inline validation.
- Embedded, read-only RAG guidance surfaces relevant TT200 excerpts and examples contextually (non-intrusive side panel or inline help).

### Key Interaction Paradigms

- Master-detail shell with left navigation (modules: GL, AP/AR, Cash/Bank, Reports, Admin).
- Table-first browsing (server-paginated) with quick filters, column presets, and keyboard shortcuts for power users.
- Form patterns with immediate validation (Zod/Yup), debounced lookups, and smart field ordering for fast data entry.
- Non-blocking toasts for success, inline errors for validation, modal for destructive actions only.

### Core Screens and Views

- Login → Company landing
- Voucher List (filters: date range, account, status) → Voucher Form (draft/post)
- Customers, Suppliers master data (CRUD)
- Sales Invoice / Purchase Bill forms
- Cash Receipt / Cash Payment forms
- Trial Balance view (period selector) with export
- Financial Statements: Balance Sheet (B01-DN), Income Statement (B02-DN), Cash Flow (direct)
- Admin: Company settings, Period management, Users/Roles
- RAG Help Panel: context-aware guidance with citations

### Accessibility

- Target AA-aligned color contrast; focus states on all interactive elements; semantic HTML and ARIA where appropriate.
- Keyboard navigation for grids and forms; skip links; screen-reader labels on form fields.

### Branding

- Clean, professional financial UI; neutral palette with accent for primary actions.
- Use Tailwind CSS with CSS variables for theming; Shadcn UI components for consistency; support easy brand override post-MVP.

### Target Device and Platforms

- Desktop-first Web Responsive (primary resolutions: 1920×1080, 1366×768); tablet-friendly for reports and approvals.
- Vietnamese locale default; English optional/partial.

### Detailed UI Specifications

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

## User Interface Design Goals

{{ui_design_goals}}

---

## Epic List

**Epic 1: Project Foundation & Secure Authentication**  
*Goal:* Set up the full-stack dev environment (Spring Boot, React, Supabase), implement robust authentication and RBAC, company creation, and initial user management.  
*Stories: 6–8*

**Epic 2: Master Data Management**  
*Goal:* CRUD for core master data (Chart of Accounts—preconfigured, Customers, Suppliers, Bank Accounts, Company Settings); initial migration scripts; validation logic.  
*Stories: 6–10*

**Epic 3: Voucher Engine & General Ledger Core**  
*Goal:* Core voucher entry, journal logic, posting/unposting with double-entry validation, basic audit trail, leaf-only posting enforcement, period selector, closing/opening logic for fiscal periods.  
*Stories: 8–14*

**Epic 4: Accounts Payable (AP) Module**  
*Goal:* Purchase Bills, supplier management, AP Aging report, linked cash payments, approval workflow for large bills/payments, VAT handling on bills.  
*Stories: 8–12*

**Epic 5: Accounts Receivable (AR) Module**  
*Goal:* Sales invoices, customer management, AR Aging, cash receipts, reminders/alerts for overdue invoices, linked receipt posting.  
*Stories: 8–12*

**Epic 6: Cash & Bank Management**  
*Goal:* Cash Receipt and Cash Payment flows, manage cash/bank accounts, running balances, upload bank statement for manual reconciliation.  
*Stories: 8–10*

**Epic 7: Reporting Engine - Core Financials**  
*Goal:* Generate and export Trial Balance, Balance Sheet (B01-DN), Income Statement (B02-DN), Cash Flow Statement (B03-DN), F01, with drill-down and correct TT200 mapping.  
*Stories: 8–12*

**Epic 8: BI Dashboard & Analytics**  
*Goal:* Implement data pipeline from transactional tables to dashboard widgets, real-time financial metrics, AR/AP/cash trends, and multi-period comparison; performance optimization for analytics queries.  
*Stories: 6–8*

**Epic 9: AI RAG Chatbot & Contextual Help**  
*Goal:* RAG-powered accounting assistant with embedded TT200 help, vector DB integration, nightly indexing, context-aware chat on all key screens, citation w/ data source in answers.  
*Stories: 6–10*

**Epic 10: Administration & Utilities**  
*Goal:* Period management (close/open), import/export utilities (COA, balances, master data), notification center, audit log browsing, backup/restore, user feedback & onboarding improvements.  
*Stories: 7–10*

> **Note:** Detailed epic breakdown with full story specifications is available in [epics.md](./epics.md)

---

## Out of Scope

{{out_of_scope}}
