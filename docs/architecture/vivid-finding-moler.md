# Use Case Diagram Implementation Plan
## Vietnamese Accounting System - TT200-Aligned ERP

**Created**: 2025-11-27
**System**: Compliance-first Accounting ERP for Vietnamese SMEs
**Scope**: 9 hierarchical use case diagrams covering 14 functional modules and ~140 use cases

---

## Executive Summary

Create a comprehensive set of UML use case diagrams organized in 4 tiers:
1. **System Overview** (1 diagram) - Executive/stakeholder view
2. **Workflow Diagrams** (3 diagrams) - Business process flows
3. **Module Diagrams** (4 diagrams) - Functional area details
4. **Administration** (1 diagram) - System management

**Total**: 9 Excalidraw diagrams with hybrid English/Vietnamese labeling, balanced detail level (~120-140 use cases), and key relationships only.

---

## Design Decisions (User-Approved)

| Aspect | Choice | Rationale |
|--------|--------|-----------|
| **Language** | Hybrid (English names + Vietnamese descriptions) | Matches codebase (English) while supporting business users (Vietnamese notes) |
| **Detail Level** | Balanced (~120-140 use cases) | Groups simple CRUD, separates complex workflows |
| **Diagram Count** | 9 diagrams | Optimal balance between comprehensiveness and maintainability |
| **Relationships** | Key only (5-8 per diagram) | Visual clarity while documenting all relationships in README |
| **Actor Inheritance** | Show in overview only | Provides architectural context without cluttering details |

---

## System Context

### Actors (4 Roles with Hierarchy)
```
ADMIN (Level 4)
  ↓
CHIEF_ACCOUNTANT (Level 3)
  ↓
CFO (Level 2)
  ↓
ACCOUNTANT (Level 1)
```

**Role Descriptions**:
- **ADMIN**: System administrator, full access, user management
- **CHIEF_ACCOUNTANT**: Approvals, period closing, audit oversight, can manage ACCOUNTANT/CFO
- **CFO**: Reporting, strategic visibility, read-mostly role
- **ACCOUNTANT**: Data entry, transaction creation, daily operations

### Functional Modules (14 Areas)
1. Authentication & Access Control
2. Master Data Management (COA, Customers, Suppliers, Bank Accounts)
3. General Ledger & Vouchers
4. Accounts Payable (Purchase Bills, Payments, AP Aging)
5. Accounts Receivable (Sales Invoices, Receipts, AR Aging)
6. Cash & Bank Management
7. Period Management
8. VAT & Compliance
9. Reporting & Analytics
10. System Administration
11. Audit & Data Integrity
12. File Management & Attachments
13. Import/Export Operations
14. AI Chatbot (Vietnamese RAG)

---

## Diagram Specifications

### Tier 1: System Context

#### Diagram 1: System Overview
**File**: `docs/architecture/use-cases/00-overview/usecase-overview-system-context.excalidraw`
**Canvas Size**: 1800x1400px
**Purpose**: 30,000-foot view for executives, stakeholders, and new team members

**Contents**:
- 4 actors positioned at cardinal points (North/South/East/West)
- 14 subsystem groups inside system boundary
- Actor hierarchy shown with UML generalization arrows
- Color-coded subsystems by functional area
- Legend explaining colors and actor hierarchy
- Vietnamese module names in tooltips/notes

**Key Elements**:
- System boundary rectangle (1600x1200px)
- 14 subsystem ovals with English names:
  - Authentication & Access Control (blue #2563eb)
  - Master Data Management (green #16a34a)
  - General Ledger & Vouchers (purple #9333ea)
  - Accounts Payable (orange #ea580c)
  - Accounts Receivable (orange #ea580c)
  - Cash & Bank Management (teal #0d9488)
  - Period Management (red #dc2626)
  - VAT & Compliance (yellow #eab308)
  - Reporting & Analytics (indigo #4f46e5)
  - System Administration (gray #6b7280)
  - Audit & Data Integrity (brown #92400e)
  - File Management (pink #ec4899)
  - Import/Export (cyan #06b6d4)
  - AI Chatbot (lime #84cc16)

**Actor Inheritance**:
```
CHIEF_ACCOUNTANT ──▷ ACCOUNTANT (generalization)
```
*Note*: Only show this inheritance in overview diagram. Detail diagrams will flatten.

**Associations**: Show only high-level associations (1-2 per actor to major subsystems)

**Estimated Time**: 2 hours

---

### Tier 2: Workflow Diagrams

#### Diagram 2: Procure-to-Pay (P2P) Workflow
**File**: `docs/architecture/use-cases/01-workflows/usecase-workflow-procure-to-pay.excalidraw`
**Canvas Size**: 1400x1800px (portrait)
**Purpose**: End-to-end purchase bill to payment workflow

**Actors Involved**: ACCOUNTANT, CHIEF_ACCOUNTANT, CFO

**Use Cases** (~14 use cases):
1. Manage Suppliers (ACCOUNTANT, CHIEF_ACCOUNTANT)
2. Create Purchase Bill (ACCOUNTANT)
3. Edit Draft Purchase Bill (ACCOUNTANT)
4. Attach Documents to Bill (ACCOUNTANT)
5. Submit Bill for Approval (ACCOUNTANT)
6. Approve Purchase Bill (CHIEF_ACCOUNTANT)
7. Reject Purchase Bill (CHIEF_ACCOUNTANT) «extend»
8. Post Purchase Bill to GL (System, triggered by approval)
9. Create Supplier Payment (ACCOUNTANT)
10. Allocate Payment to Bills (ACCOUNTANT) - FIFO or manual
11. Post Payment (ACCOUNTANT, CHIEF_ACCOUNTANT)
12. Reverse Payment (CHIEF_ACCOUNTANT) «extend»
13. View AP Aging Report (CFO, ACCOUNTANT, CHIEF_ACCOUNTANT)
14. Generate Supplier Statement (CHIEF_ACCOUNTANT)

**Key Relationships** (5-7 arrows):
- "Submit Bill for Approval" «include» "Validate Purchase Bill"
- "Approve Purchase Bill" «include» "Post Purchase Bill to GL"
- "Post Purchase Bill to GL" «include» "Update AP Balance"
- "Allocate Payment to Bills" «extend» "Override FIFO Allocation"
- "Post Payment" «include» "Create GL Voucher"

**Layout**: Vertical flow (top to bottom) following process sequence

**Vietnamese Descriptions** (add as Excalidraw text notes):
- "Manage Suppliers" → "Quản lý nhà cung cấp"
- "Create Purchase Bill" → "Tạo hóa đơn mua hàng"
- "Approve Purchase Bill" → "Phê duyệt hóa đơn mua"
- etc.

**Estimated Time**: 2 hours

---

#### Diagram 3: Order-to-Cash (O2C) Workflow
**File**: `docs/architecture/use-cases/01-workflows/usecase-workflow-order-to-cash.excalidraw`
**Canvas Size**: 1400x1800px (portrait)
**Purpose**: End-to-end sales invoice to receipt workflow

**Actors Involved**: ACCOUNTANT, CHIEF_ACCOUNTANT, CFO

**Use Cases** (~15 use cases):
1. Manage Customers (ACCOUNTANT, CHIEF_ACCOUNTANT)
2. Create Sales Invoice (ACCOUNTANT)
3. Edit Draft Invoice (ACCOUNTANT)
4. Create Credit Note (ACCOUNTANT) «extend»
5. Attach Documents to Invoice (ACCOUNTANT)
6. Submit Invoice for Approval (ACCOUNTANT)
7. Approve Sales Invoice (CHIEF_ACCOUNTANT)
8. Reject Sales Invoice (CHIEF_ACCOUNTANT) «extend»
9. Post Invoice to GL (System, triggered by approval)
10. Create Customer Receipt (ACCOUNTANT)
11. Allocate Receipt to Invoices (ACCOUNTANT)
12. Post Receipt (ACCOUNTANT, CHIEF_ACCOUNTANT)
13. Reverse Receipt (CHIEF_ACCOUNTANT) «extend»
14. View AR Aging Report (CFO, ACCOUNTANT, CHIEF_ACCOUNTANT)
15. Send Overdue Reminders (System, CHIEF_ACCOUNTANT)
16. Generate Customer Statement (CHIEF_ACCOUNTANT)
17. Manage Statement Disputes (CHIEF_ACCOUNTANT)

**Key Relationships** (6-8 arrows):
- "Submit Invoice for Approval" «include» "Validate Sales Invoice"
- "Approve Sales Invoice" «include» "Post Invoice to GL"
- "Post Invoice to GL" «include» "Update AR Balance"
- "Create Customer Receipt" «extend» "Link to Specific Invoice"
- "Post Receipt" «include» "Create GL Voucher"
- "View AR Aging Report" «include» "Calculate Aging Buckets"

**Layout**: Vertical flow with reconciliation branch on right side

**Estimated Time**: 2.5 hours

---

#### Diagram 4: Period Closing & Reporting Workflow
**File**: `docs/architecture/use-cases/01-workflows/usecase-workflow-period-closing-reporting.excalidraw`
**Canvas Size**: 1600x1200px (landscape)
**Purpose**: Month-end closing and financial reporting process

**Actors Involved**: CHIEF_ACCOUNTANT, CFO, ADMIN

**Use Cases** (~13 use cases):
1. Create Accounting Period (CHIEF_ACCOUNTANT, ADMIN)
2. Set Period as Current (CHIEF_ACCOUNTANT)
3. Run Data Integrity Checks (CHIEF_ACCOUNTANT)
4. Review Unposted Vouchers (CHIEF_ACCOUNTANT)
5. Post Pending Vouchers (CHIEF_ACCOUNTANT)
6. Close Accounting Period (CHIEF_ACCOUNTANT)
7. Reopen Period (ADMIN only) «extend»
8. Generate Trial Balance (CFO, CHIEF_ACCOUNTANT)
9. Generate Balance Sheet (B01-DN) (CFO, CHIEF_ACCOUNTANT)
10. Generate Income Statement (B02-DN) (CFO, CHIEF_ACCOUNTANT)
11. Generate VAT Report (CHIEF_ACCOUNTANT)
12. View Financial Dashboard (CFO, CHIEF_ACCOUNTANT)
13. Export Reports (Excel/PDF) (CFO, CHIEF_ACCOUNTANT)

**Key Relationships** (5-6 arrows):
- "Close Accounting Period" «include» "Run Data Integrity Checks"
- "Close Accounting Period" «include» "Validate All Vouchers Posted"
- "Generate Trial Balance" «include» "Calculate Account Balances"
- "Generate Balance Sheet" «include» "Aggregate Trial Balance Data"

**Layout**: Horizontal flow (left to right) with reporting branch at bottom

**Estimated Time**: 2 hours

---

### Tier 3: Functional Module Diagrams

#### Diagram 5: General Ledger & Voucher Management
**File**: `docs/architecture/use-cases/02-modules/usecase-module-general-ledger-vouchers.excalidraw`
**Canvas Size**: 1600x1400px
**Purpose**: Complete voucher lifecycle and GL operations

**Actors Involved**: ACCOUNTANT, CHIEF_ACCOUNTANT, CFO (view only)

**Use Cases** (~20 use cases):
1. Create Manual Voucher (ACCOUNTANT)
2. Edit Draft Voucher (ACCOUNTANT)
3. Delete Draft Voucher (ACCOUNTANT)
4. Save Voucher as Draft (ACCOUNTANT) - autosave
5. Recover Draft Voucher (ACCOUNTANT)
6. Add Voucher Line Items (ACCOUNTANT)
7. Validate Double-Entry (System)
8. Validate Leaf-Only Posting (System) - TT200 rule
9. Attach Documents to Voucher (ACCOUNTANT)
10. Post Voucher to GL (ACCOUNTANT, CHIEF_ACCOUNTANT)
11. Unpost Voucher (CHIEF_ACCOUNTANT only)
12. Reverse Posted Voucher (CHIEF_ACCOUNTANT)
13. View Voucher History (All roles)
14. Export Voucher History (CHIEF_ACCOUNTANT, CFO)
15. Create Voucher Template (ACCOUNTANT, CHIEF_ACCOUNTANT)
16. Manage Voucher Templates (ACCOUNTANT, CHIEF_ACCOUNTANT)
17. Apply Template to New Voucher (ACCOUNTANT)
18. View Journal Entries (All roles) - read-only
19. Search Vouchers (All roles)
20. Filter Vouchers by Period (All roles)

**Key Relationships** (7-8 arrows):
- "Post Voucher to GL" «include» "Validate Double-Entry"
- "Post Voucher to GL" «include» "Validate Leaf-Only Posting"
- "Post Voucher to GL" «include» "Create Journal Entries"
- "Reverse Posted Voucher" «include» "Create Reversing Voucher"
- "Apply Template to New Voucher" «include» "Pre-fill Line Items"

**Layout**: Left side = creation/editing, Right side = posting/reversal, Bottom = templates

**Estimated Time**: 2.5 hours

---

#### Diagram 6: Master Data & Configuration
**File**: `docs/architecture/use-cases/02-modules/usecase-module-master-data-configuration.excalidraw`
**Canvas Size**: 1800x1400px
**Purpose**: Master data CRUD and system configuration

**Actors Involved**: ADMIN, CHIEF_ACCOUNTANT, ACCOUNTANT, CFO (view only)

**Use Cases** (~26 use cases):

**Chart of Accounts** (4 use cases):
1. View Chart of Accounts (All roles)
2. Search Accounts by Code/Name (All roles)
3. Filter Accounts by Type (All roles)
4. Manage COA (ADMIN only) - TT200 preconfigured, read-only for others

**Customers** (6 use cases):
5. Manage Customer Master Data (ACCOUNTANT, CHIEF_ACCOUNTANT) - includes CRUD
6. Search Customers (All roles)
7. View Customer AR Summary (ACCOUNTANT, CHIEF_ACCOUNTANT, CFO)
8. Import Customers (Batch) (ACCOUNTANT, CHIEF_ACCOUNTANT)
9. Export Customers (Excel/CSV) (ACCOUNTANT, CHIEF_ACCOUNTANT)
10. Activate/Deactivate Customer (CHIEF_ACCOUNTANT)

**Suppliers** (6 use cases):
11. Manage Supplier Master Data (ACCOUNTANT, CHIEF_ACCOUNTANT)
12. Search Suppliers (All roles)
13. View Supplier AP Summary (ACCOUNTANT, CHIEF_ACCOUNTANT, CFO)
14. Import Suppliers (Batch) (ACCOUNTANT, CHIEF_ACCOUNTANT)
15. Export Suppliers (Excel/CSV) (ACCOUNTANT, CHIEF_ACCOUNTANT)
16. Activate/Deactivate Supplier (CHIEF_ACCOUNTANT)

**Bank Accounts** (5 use cases):
17. Manage Bank Accounts (ACCOUNTANT, CHIEF_ACCOUNTANT)
18. View Bank Account Balance Tooltip (All roles)
19. Import Bank Accounts (ACCOUNTANT, CHIEF_ACCOUNTANT)
20. Export Bank Accounts (ACCOUNTANT, CHIEF_ACCOUNTANT)
21. Track Bank Reconciliation Dates (ACCOUNTANT, CHIEF_ACCOUNTANT)

**Company Settings** (5 use cases):
22. Configure Company Information (ADMIN)
23. Set Default GL Accounts (ADMIN, CHIEF_ACCOUNTANT)
24. Configure Payment Terms (ADMIN, CHIEF_ACCOUNTANT)
25. Set Approval Thresholds (ADMIN, CHIEF_ACCOUNTANT)
26. Manage Advanced Settings (ADMIN)

**Key Relationships** (6-7 arrows):
- "Manage Customer Master Data" «include» "Auto-Generate Customer Code"
- "Manage Supplier Master Data" «include» "Auto-Generate Supplier Code"
- "Import Customers" «include» "Validate Import Data"
- "Import Customers" «include» "Generate Error Report" «extend»

**Layout**: Grid layout with 4 quadrants (COA, Customers, Suppliers, Bank Accounts + Settings)

**Estimated Time**: 3 hours

---

#### Diagram 7: AR & AP Operations
**File**: `docs/architecture/use-cases/02-modules/usecase-module-ar-ap-operations.excalidraw`
**Canvas Size**: 2000x1600px (largest diagram)
**Purpose**: Comprehensive AR and AP transaction operations

**Actors Involved**: ACCOUNTANT, CHIEF_ACCOUNTANT, CFO

**Layout**: Two-column layout (AR left, AP right, shared operations at bottom)

**AR Section - Use Cases** (~19 use cases):
1. Create Sales Invoice (ACCOUNTANT)
2. Edit Draft Invoice (ACCOUNTANT)
3. Delete Draft Invoice (ACCOUNTANT)
4. Create Credit Note (ACCOUNTANT)
5. Submit Invoice for Approval (ACCOUNTANT)
6. Approve Invoice (CHIEF_ACCOUNTANT)
7. Reject Invoice (CHIEF_ACCOUNTANT)
8. Create Customer Receipt (ACCOUNTANT)
9. Allocate Receipt to Invoices (ACCOUNTANT)
10. Post Receipt (ACCOUNTANT, CHIEF_ACCOUNTANT)
11. Reverse Receipt (CHIEF_ACCOUNTANT)
12. View AR Aging Report (All roles)
13. Drill Down AR Aging by Customer (All roles)
14. Export AR Aging (Excel/PDF) (CFO, CHIEF_ACCOUNTANT)
15. Configure AR Reminders (CHIEF_ACCOUNTANT)
16. Trigger Manual Reminder (CHIEF_ACCOUNTANT)
17. Generate Customer Statement (CHIEF_ACCOUNTANT)
18. Import Customer Reconciliation (CHIEF_ACCOUNTANT)
19. Manage Statement Disputes (CHIEF_ACCOUNTANT)

**AP Section - Use Cases** (~19 use cases):
20. Create Purchase Bill (ACCOUNTANT)
21. Edit Draft Bill (ACCOUNTANT)
22. Delete Draft Bill (ACCOUNTANT)
23. Submit Bill for Approval (ACCOUNTANT)
24. Approve Bill (CHIEF_ACCOUNTANT)
25. Reject Bill (CHIEF_ACCOUNTANT)
26. Create Supplier Payment (ACCOUNTANT)
27. Allocate Payment to Bills (ACCOUNTANT) - FIFO
28. Override FIFO Allocation (ACCOUNTANT)
29. Post Payment (ACCOUNTANT, CHIEF_ACCOUNTANT)
30. Reverse Payment (CHIEF_ACCOUNTANT)
31. View AP Aging Report (All roles)
32. Drill Down AP Aging by Supplier (All roles)
33. Export AP Aging (Excel/PDF) (CFO, CHIEF_ACCOUNTANT)
34. Generate Supplier Statement (CHIEF_ACCOUNTANT)
35. Import Supplier Reconciliation (CHIEF_ACCOUNTANT)
36. Manage Supplier Statement Disputes (CHIEF_ACCOUNTANT)
37. View AP Audit Timeline (CHIEF_ACCOUNTANT, ADMIN)
38. Export AP Audit Trail (CHIEF_ACCOUNTANT, ADMIN)

**Key Relationships** (8-10 arrows):
- "Submit Invoice for Approval" «include» "Validate Invoice Data"
- "Approve Invoice" «include» "Post to GL"
- "Create Customer Receipt" «extend» "Link to Specific Invoice"
- "Allocate Receipt to Invoices" «include» "Update Invoice Status"
- "Submit Bill for Approval" «include» "Validate Bill Data"
- "Approve Bill" «include» "Post to GL"
- "Allocate Payment to Bills" «include» "Calculate FIFO Allocation"
- "View AR Aging Report" «include» "Calculate Aging Buckets"

**Estimated Time**: 3.5 hours (largest and most complex diagram)

---

#### Diagram 8: Reporting, Analytics & Compliance
**File**: `docs/architecture/use-cases/02-modules/usecase-module-reporting-analytics-compliance.excalidraw`
**Canvas Size**: 1800x1400px
**Purpose**: Financial reporting, BI dashboards, and compliance features

**Actors Involved**: CFO, CHIEF_ACCOUNTANT, ACCOUNTANT

**Use Cases** (~24 use cases):

**Core Financial Reports** (7 use cases):
1. Generate Trial Balance (S06-DN) (CFO, CHIEF_ACCOUNTANT)
2. Generate Balance Sheet (B01-DN) (CFO, CHIEF_ACCOUNTANT)
3. Generate Income Statement (B02-DN) (CFO, CHIEF_ACCOUNTANT)
4. Generate Cash Flow Statement (B03-DN) (CFO, CHIEF_ACCOUNTANT) - backlog
5. Export Reports (Excel) (CFO, CHIEF_ACCOUNTANT)
6. Export Reports (PDF) (CFO, CHIEF_ACCOUNTANT)
7. Schedule Automated Reports (CHIEF_ACCOUNTANT) - backlog

**AR/AP Aging & Dashboards** (5 use cases):
8. View AR Aging Dashboard (All roles)
9. View AP Aging Dashboard (All roles)
10. View AR Overdue Metrics (CFO, CHIEF_ACCOUNTANT)
11. View Top Overdue Customers (CFO, CHIEF_ACCOUNTANT)
12. Refresh Aging Cache (CHIEF_ACCOUNTANT)

**Metabase BI Integration** (4 use cases):
13. Access Metabase Dashboard (CFO, CHIEF_ACCOUNTANT)
14. View Revenue/Expense Dashboard (CFO, CHIEF_ACCOUNTANT)
15. View Cash Flow Dashboard (CFO, CHIEF_ACCOUNTANT)
16. View AR/AP Summary Dashboard (CFO, CHIEF_ACCOUNTANT)

**VAT Reporting** (4 use cases):
17. Generate Output VAT Report (CHIEF_ACCOUNTANT)
18. Create VAT Correction (CHIEF_ACCOUNTANT)
19. View VAT Report History (CHIEF_ACCOUNTANT, CFO)
20. Export VAT Report (CHIEF_ACCOUNTANT)

**AI Chatbot** (4 use cases):
21. Query Voucher Data (Vietnamese) (All roles)
22. View Chatbot Citations (All roles)
23. View Confidence Scores (All roles)
24. Search Historical Queries (All roles)

**Key Relationships** (6-7 arrows):
- "Generate Balance Sheet" «include» "Aggregate Trial Balance Data"
- "Generate Income Statement" «include» "Calculate Period P&L"
- "View AR Aging Dashboard" «include» "Load Cached Aging Data"
- "Query Voucher Data" «include» "Search Pinecone Vector DB"
- "Query Voucher Data" «include» "Generate LLM Response"

**Layout**: Four sections (top-left: Core Reports, top-right: Dashboards, bottom-left: VAT, bottom-right: AI Chatbot)

**Estimated Time**: 2.5 hours

---

### Tier 4: Administration

#### Diagram 9: System Administration & Security
**File**: `docs/architecture/use-cases/03-admin/usecase-admin-system-administration-security.excalidraw`
**Canvas Size**: 1800x1600px
**Purpose**: User management, system configuration, audit, and security

**Actors Involved**: ADMIN, CHIEF_ACCOUNTANT

**Use Cases** (~23 use cases):

**User Management** (8 use cases):
1. Invite New User (ADMIN, CHIEF_ACCOUNTANT)
2. Create User Account (ADMIN, CHIEF_ACCOUNTANT)
3. Edit User Information (ADMIN, CHIEF_ACCOUNTANT)
4. Assign User Role (ADMIN, CHIEF_ACCOUNTANT) - respects hierarchy
5. Deactivate User (ADMIN, CHIEF_ACCOUNTANT)
6. Activate User (ADMIN, CHIEF_ACCOUNTANT)
7. Reset User Password (ADMIN, CHIEF_ACCOUNTANT)
8. View User Activity Log (ADMIN)

**Company Management** (4 use cases):
9. Configure Company Settings (ADMIN)
10. Set Company Branding (ADMIN)
11. Manage Multi-Tenant Isolation (ADMIN)
12. Seed Demo Data (ADMIN) - development only

**Period Management** (4 use cases):
13. Create Accounting Period (CHIEF_ACCOUNTANT, ADMIN)
14. Close Accounting Period (CHIEF_ACCOUNTANT)
15. Reopen Accounting Period (ADMIN only)
16. View Period Status (All roles)

**Audit & Compliance** (5 use cases):
17. View Audit Log (ADMIN, CHIEF_ACCOUNTANT)
18. Export Audit Trail (ADMIN, CHIEF_ACCOUNTANT)
19. Run Data Integrity Check (ADMIN, CHIEF_ACCOUNTANT)
20. View Integrity Findings (ADMIN, CHIEF_ACCOUNTANT)
21. Export Integrity Report (ADMIN, CHIEF_ACCOUNTANT)

**System Health** (2 use cases):
22. View System Health Status (ADMIN)
23. Run Diagnostics (ADMIN)

**Key Relationships** (5-6 arrows):
- "Assign User Role" «include» "Validate Role Hierarchy"
- "Close Accounting Period" «include» "Run Data Integrity Check"
- "Run Data Integrity Check" «include» "Validate GL Balances"
- "Run Data Integrity Check" «include» "Check Orphaned Records"

**Layout**: Four quadrants (top-left: Users, top-right: Company, bottom-left: Periods, bottom-right: Audit/Health)

**Estimated Time**: 2.5 hours

---

## Implementation Guidelines

### Excalidraw Technical Standards

#### Grid & Alignment
- **Grid Size**: 50px
- **Element Spacing**: 100px between use cases, 150px between sections
- **Actor Positioning**: 200px outside system boundary
- **Text Snap**: All coordinates must be multiples of 50

#### Element Sizing
- **Actor (stick figure)**: Width 80px, Height 120px
- **Use Case (ellipse)**: Width 180-240px (auto-adjust based on text), Height 80px
- **System Boundary (rectangle)**: Width/height based on content + 200px padding
- **Section Grouping (rectangle, dashed)**: Width/height based on grouped use cases + 50px padding

#### Typography
- **Use Case Names**: 14pt sans-serif, bold, black (#1e1e1e)
- **Actor Names**: 12pt sans-serif, bold, black (#1e1e1e)
- **Stereotypes** (<<include>>, <<extend>>): 10pt sans-serif, italic, gray (#6b7280)
- **Vietnamese Descriptions**: 11pt sans-serif, gray (#6b7280), positioned below English name

#### Color Scheme (Module Color Coding)
- **Authentication & Security**: Blue #2563eb
- **Master Data**: Green #16a34a
- **General Ledger**: Purple #9333ea
- **AR & AP**: Orange #ea580c
- **Cash & Bank**: Teal #0d9488
- **Period Management**: Red #dc2626
- **VAT & Compliance**: Yellow #eab308 (darker text #92400e)
- **Reporting & Analytics**: Indigo #4f46e5
- **System Administration**: Gray #6b7280
- **Audit**: Brown #92400e
- **File Management**: Pink #ec4899
- **Import/Export**: Cyan #06b6d4
- **AI Chatbot**: Lime #84cc16 (darker text #65a30d)

Apply background color to use case ellipses at 20% opacity, stroke at 100% opacity.

#### Arrow Styling
- **Association**: Solid line, no arrowhead, 2px width
- **Include**: Dashed line, open arrowhead, 2px width, <<include>> label
- **Extend**: Dashed line, open arrowhead, 2px width, <<extend>> label
- **Generalization**: Solid line, hollow triangle arrowhead, 2px width

### Vietnamese Descriptions Pattern

For each use case, add a small text element directly below the ellipse:

```
English Name (in ellipse): "Manage Customer Master Data"
Vietnamese Description (below): "Quản lý dữ liệu chính khách hàng"
```

**Implementation in Excalidraw**:
1. Create use case ellipse with English name (centered text)
2. Create separate text element with Vietnamese description
3. Position Vietnamese text 10px below ellipse bottom edge
4. Group ellipse + Vietnamese text together (select both → right-click → Group)
5. Use gray color (#6b7280) for Vietnamese text

### Actor Inheritance (Overview Diagram Only)

In Diagram 1 (System Overview), show role hierarchy:

```
┌─────────────────┐
│  ACCOUNTANT     │
│   (stick figure)│
└─────────────────┘
        △
        │ (generalization arrow: solid line, hollow triangle)
        │
┌─────────────────┐
│CHIEF_ACCOUNTANT │
│   (stick figure)│
└─────────────────┘
```

**Implementation**:
1. Position CHIEF_ACCOUNTANT above ACCOUNTANT
2. Draw arrow from CHIEF_ACCOUNTANT to ACCOUNTANT
3. Use solid line with hollow triangle pointing to ACCOUNTANT
4. Label relationship: "inherits from" (optional, can omit for clarity)

**Note**: This inheritance is ONLY shown in Diagram 1. All other diagrams (2-9) flatten the relationships (each actor shows all their use cases explicitly).

---

## File Structure & Organization

```
docs/
└── architecture/
    └── use-cases/
        ├── README.md (navigation index + methodology)
        ├── CHANGELOG.md (version tracking)
        ├── VIETNAMESE_GLOSSARY.md (English-Vietnamese term mapping)
        ├── 00-overview/
        │   └── usecase-overview-system-context.excalidraw
        ├── 01-workflows/
        │   ├── usecase-workflow-procure-to-pay.excalidraw
        │   ├── usecase-workflow-order-to-cash.excalidraw
        │   └── usecase-workflow-period-closing-reporting.excalidraw
        ├── 02-modules/
        │   ├── usecase-module-general-ledger-vouchers.excalidraw
        │   ├── usecase-module-master-data-configuration.excalidraw
        │   ├── usecase-module-ar-ap-operations.excalidraw
        │   └── usecase-module-reporting-analytics-compliance.excalidraw
        └── 03-admin/
            └── usecase-admin-system-administration-security.excalidraw
```

### README.md Contents
- Overview of diagram organization
- How to navigate between diagrams
- Color coding legend
- Actor role descriptions
- Vietnamese-English glossary reference
- Relationship documentation (all <<include>>/<<extend>> not shown in diagrams)
- Maintenance guidelines

### VIETNAMESE_GLOSSARY.md Contents
Table mapping all English use case names to Vietnamese translations (for business users who need full Vietnamese documentation).

---

## Implementation Sequence

### Phase 1: Foundation (Days 1-2)
1. Create directory structure
2. Create Diagram 1 (System Overview)
3. Create README.md with legend and navigation
4. Create VIETNAMESE_GLOSSARY.md starter

**Deliverables**: Overview diagram + documentation foundation

### Phase 2: Workflows (Days 3-4)
1. Create Diagram 2 (Procure-to-Pay)
2. Create Diagram 3 (Order-to-Cash)
3. Create Diagram 4 (Period Closing & Reporting)

**Deliverables**: 3 workflow diagrams showing end-to-end processes

### Phase 3: Modules (Days 5-7)
1. Create Diagram 5 (General Ledger & Vouchers)
2. Create Diagram 6 (Master Data & Configuration)
3. Create Diagram 7 (AR & AP Operations) - largest, allocate extra time
4. Create Diagram 8 (Reporting, Analytics & Compliance)

**Deliverables**: 4 detailed module diagrams

### Phase 4: Administration & Finalization (Day 8)
1. Create Diagram 9 (System Administration & Security)
2. Complete README.md with all diagram links
3. Complete VIETNAMESE_GLOSSARY.md
4. Create CHANGELOG.md
5. Review all diagrams for consistency
6. Validate JSON syntax for all .excalidraw files

**Deliverables**: Complete diagram set + documentation

### Validation Steps (After Each Diagram)
1. Visual inspection: No overlapping elements, readable text
2. JSON validation: `node -e "JSON.parse(require('fs').readFileSync('FILE.excalidraw', 'utf8')); console.log('✓ Valid')"`
3. Cross-reference with controllers: Ensure use cases map to existing endpoints
4. Color consistency: Verify color scheme matches specification
5. Vietnamese descriptions: Check for typos and accuracy

---

## Critical Files to Reference During Implementation

### Backend Controllers (Use Case Mapping)
- `/home/thanhtoan/code/accounting/backend/src/main/java/com/accounting/controller/` (all subfolders)
  - `ar/` → AR use cases
  - `ap/` → AP use cases
  - `sales/` → Sales invoice use cases
  - `payment/` → Payment use cases
  - `auth/` → Authentication use cases
  - `period/` → Period management use cases

### Entity Definitions (System Boundaries)
- `/home/thanhtoan/code/accounting/backend/src/main/java/com/accounting/entity/` (all entities)

### Role & Security Definitions
- `/home/thanhtoan/code/accounting/backend/src/main/java/com/accounting/enums/Role.java` - Actor hierarchy
- `/home/thanhtoan/code/accounting/backend/src/main/java/com/accounting/security/SecurityConfig.java` - Authorization rules
- `/home/thanhtoan/code/accounting/frontend/src/utils/roles.ts` - Frontend role helpers

### Story Files (Feature Specifications)
- `/home/thanhtoan/code/accounting/docs/sprint-artifacts/stories/` (all .md files)
  - `5-1-*.md` → Sales invoice features
  - `5-2-*.md` → Invoice approval
  - `5-3-*.md` → Customer receipts
  - `5-4-*.md` → AR aging
  - `5-5-*.md` → Customer statements
  - `4-*-*.md` → AP features
  - `3-*-*.md` → GL/Voucher features
  - etc.

### System Architecture
- `/home/thanhtoan/code/accounting/CLAUDE.md` - System overview and architecture patterns

---

## Quality Assurance Checklist

### Per-Diagram Checklist
- [ ] All use cases have English names (no typos)
- [ ] All use cases have Vietnamese descriptions (positioned below ellipse)
- [ ] Actors positioned correctly (outside system boundary)
- [ ] Color coding consistent with specification
- [ ] Grid alignment (all coordinates multiples of 50px)
- [ ] Spacing consistent (100px between use cases, 150px between sections)
- [ ] Key relationships shown (5-8 arrows max)
- [ ] Relationship stereotypes labeled (<<include>>, <<extend>>)
- [ ] No overlapping elements
- [ ] Text readable (no cutoff, proper sizing)
- [ ] Vietnamese text grouped with use case ellipses
- [ ] JSON validates successfully

### Cross-Diagram Checklist
- [ ] Use case names consistent across diagrams (same feature = same name)
- [ ] Actor names consistent (ADMIN, CHIEF_ACCOUNTANT, CFO, ACCOUNTANT)
- [ ] Color scheme consistent (same module = same color)
- [ ] Vietnamese translations consistent (same term = same translation)
- [ ] No duplicate diagrams of same content
- [ ] Workflow diagrams reference module diagrams correctly
- [ ] README navigation links all work

### Documentation Checklist
- [ ] README.md complete with all sections
- [ ] VIETNAMESE_GLOSSARY.md complete with all terms
- [ ] CHANGELOG.md created with version 1.0
- [ ] All diagrams linked in README
- [ ] Color legend documented in README
- [ ] Actor roles documented in README
- [ ] Relationship documentation in README (for relationships not shown in diagrams)

---

## Estimated Timeline Summary

| Phase | Diagrams | Time | Cumulative |
|-------|----------|------|------------|
| Phase 1: Foundation | 1 diagram + docs | 3 hours | 3 hours |
| Phase 2: Workflows | 3 diagrams | 6 hours | 9 hours |
| Phase 3: Modules | 4 diagrams | 11 hours | 20 hours |
| Phase 4: Admin & Finalization | 1 diagram + docs | 3 hours | 23 hours |
| **Total** | **9 diagrams** | **~23 hours** | **~23 hours** |

**Suggested Schedule**: 8 working days (3 hours/day average)

---

## Success Criteria

### Functional Criteria
1. ✅ All 4 actors represented consistently across diagrams
2. ✅ All 14 functional modules covered
3. ✅ ~120-140 use cases documented (balanced detail level)
4. ✅ Key workflows (P2P, O2C, Period Closing) clearly illustrated
5. ✅ All use cases map to existing controllers/endpoints
6. ✅ Actor permissions reflect actual RBAC implementation

### Technical Criteria
1. ✅ All .excalidraw files validate as proper JSON
2. ✅ No visual overlaps or readability issues
3. ✅ Consistent color scheme and typography
4. ✅ Grid-aligned elements (50px grid)
5. ✅ Proper UML notation (associations, include, extend, generalization)

### Documentation Criteria
1. ✅ README.md provides clear navigation
2. ✅ VIETNAMESE_GLOSSARY.md complete for business users
3. ✅ CHANGELOG.md tracks versions
4. ✅ All relationships documented (even if not all shown visually)
5. ✅ Maintenance guidelines included

### Stakeholder Criteria
1. ✅ **Developers**: Can cross-reference diagrams to code easily
2. ✅ **Business Users**: Can understand processes via Vietnamese descriptions
3. ✅ **Executives**: Can get high-level overview from Diagram 1
4. ✅ **Auditors**: Can trace compliance features in workflows
5. ✅ **Trainers**: Can use diagrams for onboarding new team members

---

## Next Steps After Plan Approval

1. **User confirms plan** → Proceed to execution
2. **Start with Phase 1**: Create overview diagram and documentation structure
3. **Iterate through phases**: Create workflow diagrams, then module diagrams, then admin
4. **Validate continuously**: Check JSON syntax and visual quality after each diagram
5. **Review with user**: After Phase 2 (workflows complete), check if approach is meeting expectations
6. **Finalize**: Complete all diagrams and documentation, run full QA checklist

---

## Maintenance Strategy (Post-Implementation)

### When to Update Diagrams
- **New Feature Added**: Add use case to relevant module diagram
- **New Epic Completed**: May require new module diagram or expansion of existing
- **Role Changes**: Update actor associations
- **Workflow Changes**: Update workflow diagrams if process changes

### Version Control
- Use CHANGELOG.md to track versions
- Naming: `v1.0` (initial), `v1.1` (minor updates), `v2.0` (major restructure)
- Consider timestamped backups: `usecase-overview-system-context-v1.0-20251127.excalidraw`

### Quarterly Review
- Review diagrams quarterly against current codebase
- Update Vietnamese translations if terminology changes
- Ensure new features from recent sprints are reflected

---

## End of Plan

This plan provides a complete roadmap for creating 9 hierarchical use case diagrams covering the entire Vietnamese accounting system. The hybrid English/Vietnamese approach with balanced detail level will serve multiple stakeholders while maintaining consistency with the codebase.