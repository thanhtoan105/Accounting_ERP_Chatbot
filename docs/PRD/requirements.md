# Requirements

## Functional Requirements

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

- **FR09: View and Manage Chart of Accounts (COA) per TT200:** The system must display the COA in a table view with 6 columns: Account Code, Account Name, Account Type (Debit/Credit/Hermaphrodite), Account Name in English, Description, and Status (In Use/Out of Use). The system must support CRUD operations (create, update, soft delete, activate, deactivate) via a Sheet modal form. The form must include 5 fields: Account Number (numeric, 1-4 digits, required), Account Name (required), Primary Account (optional parent selection via searchable combobox), Account Type/Characteristic (required combobox with options: "Debit Balance", "Credit Balance", "Hermaphrodite", "No Balance"), and Description (optional textarea). All operations require RBAC (admin/chief_accountant roles). The COA structure must be compliant with Circular 200, showing account code, Vietnamese name, account type, normal balance side (Debit/Credit/Hermaphrodite), account group (Asset/Liability/Equity/Revenue/Expense), and a 'postable' flag. The system must also support extended fields: Account Name in English and Description for enhanced account management.
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
- **Note:** Story 9.0 (Voucher-Focused RAG MVP) delivers an early subset of FR38–FR41 focused on voucher/AR/AP data with citations; the remaining functionality stays scoped to Epic 9.

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

## Non-Functional Requirements

**1. Performance (NFR1-NFR4)**

- **NFR1 (Response Times):** The system must have acceptable response times for typical accounting operations (guidelines: page load < 2s, simple form submission < 1s, report generation < 5s for typical date ranges). No strict P95 targets in MVP; optimize as needed.
- **NFR2 (Cold Start):** The system must cold start in under 3 seconds.
- **NFR3 (Concurrency):** The system must support at least 20 concurrent users for MVP. Scalability to 50+ is deferred to Post-MVP.
- **NFR4 (Database Queries):** Queries must be optimized with proper indexes (on foreign keys, dates, account codes), avoidance of N+1 patterns, and monitoring for slow queries (>1s).

**2. Security (NFR5-NFR9)**

- **NFR5 (RBAC):** Role-based permissions must be enforced at the API level; frontend UI hiding is not a security boundary.
- **NFR6 (Authentication):** The system must use secure authentication with JWTs, strong password hashing (Bcrypt/Argon2), and no hardcoded secrets (use environment variables).
- **NFR7 (Data in Transit):** HTTPS/TLS is mandatory for production, with cookies flagged as `HttpOnly` and `Secure`.
- **NFR8 (Audit Trail):** The audit trail must be append-only, with no modifications or deletions allowed, stored in a separate table with referential integrity.
- **NFR9 (Security Best Practices):** MVP will include input validation, parameterized queries (to prevent SQL injection), default XSS protection (React), and CSRF protection. Full OWASP compliance, rate limiting, and WAF are deferred.

**3. Compliance & Data Integrity (NFR10-NFR14)**

- **NFR10 (Circular 200 Compliance):** The system must fully comply with Circular 200 regarding COA structure, financial statement formats, and data retention (10 years).
- **NFR11 (Double-Entry Invariant):** Every voucher must be balanced (Total Debit = Total Credit), with validation enforced at both frontend and backend.
- **NFR12 (Period Close Integrity):** The system must prevent any modifications to data within a closed accounting period.
- **NFR13 (Referential Integrity):** All database relationships must be enforced with foreign key constraints to prevent orphaned records.
- **NFR14 (Data Validation):** The system must enforce validation rules for account code formats, positive-only amounts, and dates within open periods.

**4. Usability (NFR15-NFR18)**

- **NFR15 (Vietnamese UI):** The UI must be mandatorily in Vietnamese, including all labels, messages, number formats (e.g., `1.000.000,00`), and date formats (`dd/MM/yyyy`).
- **NFR16 (Responsive UI):** The desktop-first design must support common resolutions (1920x1080, 1366x768) and be responsive for key flows on tablets. Mobile optimization is deferred.
- **NFR17 (Onboarding):** The system must provide a good onboarding experience (target: reduce time by ≥30% with RAG chatbot aid), including in-app help, sample data, and a getting-started guide.
- **NFR18 (User Feedback):** The system must include an embedded CSAT survey (target: ≥4/5) and a bug report form.

**5. Maintainability & Quality (NFR19-NFR22)**

- **NFR19 (Modular Architecture):** The codebase must follow a modular architecture with clear separation of layers (API, domain, persistence) and bounded contexts (GL, AP/AR, etc.), following Spring Boot best practices.
- **NFR20 (Test Coverage):** The system must have at least 60% test coverage for the business logic layer, including unit and integration tests for critical flows. E2E tests are deferred.
- **NFR21 (Documentation):** The code must be documented with JavaDoc for public APIs, READMEs per module, OpenAPI/Swagger for APIs, and database schema documentation.
- **NFR22 (Database Migrations):** The system must use Flyway for versioned database migrations, with a documented rollback strategy.

**6. Localization (NFR23)**

- **NFR23 (i18n Support):** The system must have i18n infrastructure with Vietnamese as mandatory and English as optional/partial for MVP.

**7. Observability (NFR24-NFR25)**

- **NFR24 (Structured Logging):** The system must provide structured logging with request ID tracking and masked sensitive data. Centralized logging is deferred.
- **NFR25 (Basic Monitoring):** The system must provide a health check endpoint, database connection pool monitoring, and slow query logs. Full metrics/tracing is deferred.

**8. Caching (NFR26)**

- **NFR26 (Redis Caching):** The system must use Redis to cache reports (trial balance, aging, financial statements), with manual invalidation after posts or period close.

**9. Browser & Platform Support (NFR27)**

- **NFR27 (Browser Support):** The system must support the latest 2 versions of Chrome, Edge, and Safari. IE11 is not supported.

**10. Deployment (NFR28-NFR29)**

- **NFR28 (Containerization):** The application must be containerized using Docker for both frontend and backend, with Docker Compose for local development. Kubernetes is deferred.
- **NFR29 (CI/CD Pipeline):** There must be a CI/CD pipeline (e.g., GitHub Actions) for automated builds, tests, and deployment to staging, with a manual approval gate for production.

---
