# Epic List

**Epic 1: Project Foundation & Secure Authentication**  
_Goal:_ Set up the full-stack dev environment (Spring Boot, React, Supabase), implement robust authentication and RBAC, company creation, and initial user management.  
_Stories: 6–8_

**Epic 2: Master Data Management**  
_Goal:_ CRUD for core master data (Chart of Accounts—preconfigured, Customers, Suppliers, Bank Accounts, Company Settings); initial migration scripts; validation logic.  
_Stories: 6–10_

**Epic 3: Voucher Engine & General Ledger Core**  
_Goal:_ Core voucher entry, journal logic, posting/unposting with double-entry validation, basic audit trail, leaf-only posting enforcement, period selector, closing/opening logic for fiscal periods.  
_Stories: 8–14_

**Epic 4: Accounts Payable (AP) Module**  
_Goal:_ Purchase Bills, supplier management, AP Aging report, linked cash payments, approval workflow for large bills/payments, VAT handling on bills.  
_Stories: 8–12_

**Epic 5: Accounts Receivable (AR) Module**  
_Goal:_ Sales invoices, customer management, AR Aging, cash receipts, reminders/alerts for overdue invoices, linked receipt posting.  
_Stories: 8–12_

**Epic 6: Cash & Bank Management**  
_Goal:_ Cash Receipt and Cash Payment flows, manage cash/bank accounts, running balances, upload bank statement for manual reconciliation.  
_Stories: 8–10_

**Epic 7: Reporting Engine - Core Financials**  
_Goal:_ Generate and export Trial Balance, Balance Sheet (B01-DN), Income Statement (B02-DN), Cash Flow Statement (B03-DN), F01, with drill-down and correct TT200 mapping.  
_Stories: 8–12_

**Epic 8: BI Dashboard & Analytics**  
_Goal:_ Implement data pipeline from transactional tables to dashboard widgets, real-time financial metrics, AR/AP/cash trends, and multi-period comparison; performance optimization for analytics queries.  
_Stories: 6–8_

**Epic 9: AI RAG Chatbot & Contextual Help**  
_Goal:_ RAG-powered accounting assistant with embedded TT200 help, vector DB integration, nightly indexing, context-aware chat on all key screens, citation w/ data source in answers.  
_Stories: 6–10_

**Epic 10: Administration & Utilities**  
_Goal:_ Period management (close/open), import/export utilities (COA, balances, master data), notification center, audit log browsing, backup/restore, user feedback & onboarding improvements.  
_Stories: 7–10_

> **Note:** Detailed epic breakdown with full story specifications is available in [epics.md](./epics.md)

---
