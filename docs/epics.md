# Accounting Platform – Epics and Stories

**Generated from:** Product Requirements Document (PRD)  
**Date:** 2025-01-27  
**Status:** Complete Epic and Story Breakdown

---

## Executive Summary

This document provides a comprehensive breakdown of all functional and non-functional requirements from the PRD into 10 deliverable epics, each containing detailed user stories with acceptance criteria. All 50 functional requirements (FR01-FR50) and 29 non-functional requirements (NFR1-NFR29) are mapped to specific epics and stories.

**Total Epics:** 10  
**Total Stories:** ~80-90 (varies by epic complexity)  
**Coverage:** 100% of PRD requirements mapped

---

## Epic Overview

| Epic | Name | Stories | Key Requirements Covered |
|------|------|---------|-------------------------|
| 1 | Project Foundation & Secure Authentication | 8 | FR01-FR08, NFR5-NFR9 |
| 2 | Master Data Management | 7 | FR09-FR12, NFR10-NFR14 |
| 3 | Voucher Engine & General Ledger Core | 7 | FR13-FR16, NFR10-NFR14 |
| 4 | Accounts Payable (AP) Module | 7 | FR17-FR21, NFR10-NFR14 |
| 5 | Accounts Receivable (AR) Module | 7 | FR22-FR26, NFR10-NFR14 |
| 6 | Cash & Bank Management | 6 | FR27-FR31, NFR10-NFR14 |
| 7 | Reporting Engine – Core Financials | 6 | FR32-FR36, NFR1-NFR4, NFR26 |
| 8 | BI Dashboard & Analytics | 5 | FR37, NFR1-NFR4, NFR15-NFR18 |
| 9 | AI RAG Chatbot & Contextual Help | 5 | FR38-FR42, NFR19-NFR22 |
| 10 | Administration & Utilities | 5 | FR43-FR50, NFR23-NFR29 |

---

## Requirements Mapping

### Functional Requirements (FR01-FR50)

#### Foundation & Authentication (FR01-FR08)
- **FR01:** Secure Login → **Epic 1, Story 1.3**
- **FR02:** Role-Based Access Control (RBAC) → **Epic 1, Story 1.4**
- **FR03:** Company Profile Management → **Epic 1, Story 1.6**
- **FR04:** Accounting Period Management → **Epic 1, Story 1.6** (partial), **Epic 10, Story 10.5**
- **FR05:** Period Closing Controls → **Epic 3, Story 3.6**, **Epic 10, Story 10.5**
- **FR06:** Prevent Posting to Closed Periods → **Epic 3, Story 3.6**
- **FR07:** Basic Audit Trail → **Epic 1, Story 1.3**, **Epic 3, Story 3.5**
- **FR08:** Health Check Endpoint → **Epic 1, Story 1.1**

#### Chart of Accounts & General Ledger Core (FR09-FR16)
- **FR09:** View and Manage Chart of Accounts (COA) per TT200 → **Epic 2, Story 2.1**
- **FR10:** Leaf-Only Posting Validation → **Epic 2, Story 2.1**, **Epic 3, Story 3.4**
- **FR11:** Mandatory Dimension Validation → **Epic 3, Story 3.4**
- **FR12:** COA Search → **Epic 2, Story 2.1**
- **FR13:** Create and Post Journal Vouchers → **Epic 3, Story 3.2**, **Epic 3, Story 3.3**
- **FR14:** Document Workflow - Draft & Posted → **Epic 3, Story 3.2**, **Epic 3, Story 3.3**
- **FR15:** Maker-Checker for General Journal (Optional) → **Epic 3, Story 3.3** (optional workflow)
- **FR16:** Generate Trial Balance (S06-DN) → **Epic 7, Story 7.1**

#### Accounts Payable (AP) (FR17-FR21)
- **FR17:** Supplier Master Data Management → **Epic 2, Story 2.3**
- **FR18:** Create Purchase Bills → **Epic 4, Story 4.1**
- **FR19:** Record Payments for Purchase Bills → **Epic 4, Story 4.3**
- **FR20:** AP Aging Report → **Epic 4, Story 4.4**
- **FR21:** Maker-Checker Workflow for AP → **Epic 4, Story 4.2**

#### Accounts Receivable (AR) (FR22-FR26)
- **FR22:** Customer Master Data Management → **Epic 2, Story 2.2**
- **FR23:** Create Sales Invoices → **Epic 5, Story 5.1**
- **FR24:** Record Customer Payments → **Epic 5, Story 5.3**
- **FR25:** AR Aging Report → **Epic 5, Story 5.4**
- **FR26:** Maker-Checker Workflow for AR → **Epic 5, Story 5.2**

#### Cash & Bank Management (FR27-FR31)
- **FR27:** Manage Cash/Bank Accounts → **Epic 2, Story 2.4**, **Epic 6, Story 6.1**
- **FR28:** Record Cash Receipts → **Epic 6, Story 6.2**
- **FR29:** Record Cash Payments → **Epic 6, Story 6.3**
- **FR30:** Real-time Bank/Cash Book Balance → **Epic 6, Story 6.4**
- **FR31:** Manual Bank Reconciliation → **Epic 6, Story 6.5**

#### Financial Reports (FR32-FR37)
- **FR32:** Generate Balance Sheet (B01-DN) → **Epic 7, Story 7.2**
- **FR33:** Generate Income Statement (B02-DN) → **Epic 7, Story 7.2**
- **FR34:** Generate Cash Flow Statement (B03-DN) → **Epic 7, Story 7.2**
- **FR35:** Generate Account Balance Sheet (F01) → **Epic 7, Story 7.6**
- **FR36:** Drill-Down from Reports to Vouchers → **Epic 7, Story 7.1**, **Epic 7, Story 7.2**
- **FR37:** Real-time BI Dashboard → **Epic 8, Story 8.1**

#### AI RAG Chatbot (FR38-FR42)
- **FR38:** Chatbot UI → **Epic 9, Story 9.1**
- **FR39:** Natural Language Q&A (Vietnamese) → **Epic 9, Story 9.2**
- **FR40:** Automated Embedding Pipeline → **Epic 9, Story 9.2** (n8n workflow)
- **FR41:** Source Citation in Answers → **Epic 9, Story 9.2**
- **FR42:** Context-Aware Guidance → **Epic 9, Story 9.3**

#### Admin & Utilities (FR43-FR46)
- **FR43:** Import Data from Excel → **Epic 2, Story 2.6**, **Epic 10, Story 10.4**
- **FR44:** Export Master Data for Backup → **Epic 2, Story 2.6**, **Epic 10, Story 10.4**
- **FR45:** User Management → **Epic 1, Story 1.5**, **Epic 10, Story 10.2**
- **FR46:** Notification Center & Global Search → **Epic 10, Story 10.5** (partial), distributed across epics

#### Security & Compliance (FR47-FR50)
- **FR47:** Enforce RBAC at API Level → **Epic 1, Story 1.4** (distributed across all epics)
- **FR48:** Immutable Audit Trail → **Epic 3, Story 3.5**, **Epic 10, Story 10.3**
- **FR49:** Data Validation & Integrity → **Epic 3, Story 3.4** (distributed across all epics)
- **FR50:** 100% Compliance with Circular 200/2014/TT-BTC → **Epic 2, Story 2.1**, **Epic 7, Story 7.2** (distributed across all epics)

### Non-Functional Requirements (NFR1-NFR29)

#### Performance (NFR1-NFR4)
- **NFR1:** Response Times → **Epic 7, Story 7.1**, **Epic 8, Story 8.1** (distributed)
- **NFR2:** Cold Start → **Epic 1, Story 1.1**
- **NFR3:** Concurrency → **Epic 1, Story 1.1** (infrastructure)
- **NFR4:** Database Queries → **Epic 2, Story 2.1**, **Epic 7, Story 7.1** (distributed)

#### Security (NFR5-NFR9)
- **NFR5:** RBAC → **Epic 1, Story 1.4** (distributed across all epics)
- **NFR6:** Authentication → **Epic 1, Story 1.3**
- **NFR7:** Data in Transit → **Epic 1, Story 1.3**
- **NFR8:** Audit Trail → **Epic 3, Story 3.5**, **Epic 10, Story 10.3**
- **NFR9:** Security Best Practices → **Epic 1, Story 1.3** (distributed)

#### Compliance & Data Integrity (NFR10-NFR14)
- **NFR10:** Circular 200 Compliance → **Epic 2, Story 2.1**, **Epic 7, Story 7.2** (distributed)
- **NFR11:** Double-Entry Invariant → **Epic 3, Story 3.4**
- **NFR12:** Period Close Integrity → **Epic 3, Story 3.6**
- **NFR13:** Referential Integrity → **Epic 2, Story 2.7** (distributed)
- **NFR14:** Data Validation → **Epic 3, Story 3.4** (distributed)

#### Usability (NFR15-NFR18)
- **NFR15:** Vietnamese UI → **Epic 1, Story 1.7** (distributed across all epics)
- **NFR16:** Responsive UI → **Epic 1, Story 1.7** (distributed)
- **NFR17:** Onboarding → **Epic 1, Story 1.8**, **Epic 9, Story 9.3**
- **NFR18:** User Feedback → **Epic 9, Story 9.4**, **Epic 10, Story 10.5**

#### Maintainability & Quality (NFR19-NFR22)
- **NFR19:** Modular Architecture → **Epic 1, Story 1.1** (distributed)
- **NFR20:** Test Coverage → **Epic 1, Story 1.1** (distributed)
- **NFR21:** Documentation → **Epic 1, Story 1.1** (distributed)
- **NFR22:** Database Migrations → **Epic 1, Story 1.1**, **Epic 2, Story 2.6**

#### Localization (NFR23)
- **NFR23:** i18n Support → **Epic 1, Story 1.7** (distributed)

#### Observability (NFR24-NFR25)
- **NFR24:** Structured Logging → **Epic 1, Story 1.1**, **Epic 10, Story 10.3**
- **NFR25:** Basic Monitoring → **Epic 1, Story 1.1**, **Epic 8, Story 8.5**

#### Caching (NFR26)
- **NFR26:** Redis Caching → **Epic 7, Story 7.1**, **Epic 8, Story 8.1**

#### Browser & Platform Support (NFR27)
- **NFR27:** Browser Support → **Epic 1, Story 1.7** (distributed)

#### Deployment (NFR28-NFR29)
- **NFR28:** Containerization → **Epic 1, Story 1.1**
- **NFR29:** CI/CD Pipeline → **Epic 1, Story 1.1**

---

## Detailed Epic Breakdowns

For detailed story breakdowns with acceptance criteria, see the individual epic files:

1. [Epic 1: Project Foundation & Secure Authentication](./epics/epic-1-project-foundation-secure-authentication.md)
2. [Epic 2: Master Data Management](./epics/epic-2-master-data-management.md)
3. [Epic 3: Voucher Engine & General Ledger Core](./epics/epic-3-voucher-engine-general-ledger-core.md)
4. [Epic 4: Accounts Payable (AP) Module](./epics/epic-4-accounts-payable-ap-module.md)
5. [Epic 5: Accounts Receivable (AR) Module](./epics/epic-5-accounts-receivable-ar-module.md)
6. [Epic 6: Cash & Bank Management](./epics/epic-6-cash-bank-management.md)
7. [Epic 7: Reporting Engine – Core Financials](./epics/epic-7-reporting-engine-core-financials.md)
8. [Epic 8: BI Dashboard & Analytics](./epics/epic-8-bi-dashboard-analytics.md)
9. [Epic 9: AI RAG Chatbot & Contextual Help](./epics/epic-9-ai-rag-chatbot-contextual-help.md)
10. [Epic 10: Administration & Utilities](./epics/epic-10-administration-utilities.md)

---

## Epic Dependencies

```
Epic 1 (Foundation)
  ├── Epic 2 (Master Data) ──┐
  │                          │
  └── Epic 3 (Voucher Engine) ──┐
                                │
        Epic 4 (AP) ────────────┼─── Epic 6 (Cash/Bank)
        Epic 5 (AR) ────────────┤
                                │
        Epic 7 (Reporting) ─────┤
                                │
        Epic 8 (BI Dashboard) ──┤
                                │
        Epic 9 (AI Chatbot) ────┤
                                │
        Epic 10 (Admin) ─────────┘
```

**Key Dependencies:**
- Epic 1 must be completed first (foundation)
- Epic 2 must be completed before Epic 3 (master data needed for vouchers)
- Epic 3 must be completed before Epic 4, 5, 6 (voucher engine is core)
- Epic 4, 5, 6 can be developed in parallel after Epic 3
- Epic 7 depends on Epic 3, 4, 5, 6 (reporting needs transactional data)
- Epic 8 depends on Epic 7 (BI needs reports)
- Epic 9 can be developed in parallel but depends on Epic 3+ for data access
- Epic 10 can be developed in parallel but depends on Epic 1 for user management

---

## Story Estimation Summary

| Epic | Estimated Stories | Complexity | Priority |
|------|------------------|------------|----------|
| Epic 1 | 8 | High | Critical |
| Epic 2 | 7 | Medium | High |
| Epic 3 | 7 | High | Critical |
| Epic 4 | 7 | High | High |
| Epic 5 | 7 | High | High |
| Epic 6 | 6 | Medium | High |
| Epic 7 | 6 | High | High |
| Epic 8 | 5 | Medium | Medium |
| Epic 9 | 5 | High | Medium |
| Epic 10 | 5 | Medium | Medium |
| **Total** | **63** | | |

---

## Validation Checklist

- [x] All 50 functional requirements (FR01-FR50) mapped to epics/stories
- [x] All 29 non-functional requirements (NFR1-NFR29) mapped to epics/stories
- [x] Each epic has clear goal and expanded description
- [x] Each story has acceptance criteria
- [x] Dependencies between epics identified
- [x] Story prerequisites documented
- [x] RBAC requirements distributed across all epics
- [x] Audit trail requirements distributed across all epics
- [x] Compliance requirements (TT200) distributed across relevant epics
- [x] Performance requirements addressed in relevant epics

---

## Next Steps

1. **Review and Refine:** Product Manager and Tech Lead review epic breakdowns
2. **Story Grooming:** Detailed story refinement sessions for each epic
3. **Sprint Planning:** Prioritize epics and stories for sprint allocation
4. **Technical Design:** Create technical specifications for complex stories
5. **Implementation:** Begin development with Epic 1 (Foundation)

---

**Document Status:** ✅ Complete  
**Last Updated:** 2025-01-27  
**Maintained By:** Product Team

