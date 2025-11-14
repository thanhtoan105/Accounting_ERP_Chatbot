# Epic 2: Master Data Management

**Expanded Goal:**
Enable precise, compliant master data management for all legal entities, customers, suppliers, and bank/cash accounts, with robust import/export flows, auditability, and baseline data integrity, to ensure all transactional and reporting modules operate with clean, well-governed reference data.

```markdown
**Story 2.1: Chart of Accounts (COA) – TT200 Preload & Read-Only Management**
As a chief accountant/admin, I want the TT200 COA to be preloaded, viewable in a tree, and protected from accidental edits so all postings are mapped correctly from day one.
**Acceptance Criteria:**
1. Full TT200 COA (≥154 accounts), seeded on initial migration; re-usable as import for fresh companies.
2. Each account has: code, name (VN, accents allowed), type (Asset, Liability, ...), normal side (Debit/Credit), postable flag, parent (nullable), and ordering position.
3. Tree view: Expand/collapse to 3 levels minimum; shows full account hierarchy and allows navigation (keyboard and mouse).
4. UX: Select/search input with typeahead for account code or name; unaccented search must match accented field reliably.
5. Filtering by account class (e.g., asset, income), postable-only toggle, and code prefix filter (e.g., all "131").
6. "View Details" modal must clearly display all account fields; "Postable" visually distinguished with tag or icon.
7. COA CRUD operations: Admin and chief accountant can create, update, soft delete, activate, and deactivate accounts via table UI with Sheet modal form. Table displays 6 columns: Account Code, Account Name, Account Type (Debit/Credit/Hermaphrodite), Account Name in English, Description, Status (In Use/Out of Use). Form includes 5 fields: Account Number (numeric, required), Account Name (required), Primary Account (optional parent), Account Type/Characteristic (required: "Debit Balance", "Credit Balance", "Hermaphrodite", "No Balance"), Description (optional). All operations require RBAC (admin/chief_accountant roles) and are logged in audit.
8. Only postable-leaf accounts appear in voucher account pickers. Root accounts cannot be added to vouchers (validation both client and server).
9. API endpoint for COA returns hierarchy, allows filter by postable, code prefix, and parent–child relationships for cascading selectors.
10. No two accounts can ever have the same code per company. Attempt to add/seed duplicate code must fail with detailed error message.
11. "Balance" column available in COA tree (optional for MVP), showing real-time balance for that account in the current period.
**Prerequisites:** Epic 1.
```

```markdown
**Story 2.2: Customer Master Data Management (CRUD)**
As an accountant,
I want robust CRUD for all customers, including search/filter, import/export,
So that AR tracking, validations, and compliance are always real-time accurate.
**Acceptance Criteria:**
1. List view: Supports pagination (20 per page), sorting by name or code, filter on status (active/inactive), and typeahead search.
2. Add form: All required except for optional fields; real-time validation (e.g., phone number, email format, tax code checksum).
3. Autogenerates Customer Code (CUST-YYYY-NNNN) and ensures globally unique per company; number increments even if prior code deleted/deactivated.
4. Detect and reject duplicate customers by tax code (and optionally email/cell), displaying exactly where conflict occurred.
5. Inactive customers appear grayed-out and are moved to bottom or separate tab.
6. Users can deactivate/reactivate, with tooltip explaining that linked AR data will not be deleted.
7. CRUD actions update real-time (websockets or polling), so multiple users see live changes.
8. Show customer AR summary (open invoices, total owed, average payment days) in details panel.
9. Can export customer list to CSV/Excel.
10. Attempt to delete customer with existing invoices/payments blocked, with explanatory modal.
11. All field edits, activations/deactivations, and import actions logged to the audit trail with before/after data and responsible user.
**Prerequisites:** Story 2.1.
```

```markdown
**Story 2.3: Supplier Master Data Management (CRUD)**
As an accountant,
I want robust CRUD for all suppliers with import/export,
So that AP tracking, validations, and integration to bills/payments is seamless.
**Acceptance Criteria:**
1. List view: Supports pagination (20 per page), sorting by name or code, filter on status (active/inactive), and typeahead search.
2. Add form: All required except for optional fields; real-time validation (e.g., phone number, email format, tax code checksum).
3. Autogenerates Supplier Code (SUP-YYYY-NNNN) and ensures globally unique per company; number increments even if prior code deleted/deactivated.
4. Detect and reject duplicate suppliers by tax code (and optionally email/cell), displaying exactly where conflict occurred.
5. Inactive suppliers appear grayed-out and are moved to bottom or separate tab.
6. Users can deactivate/reactivate, with tooltip explaining that linked AP data will not be deleted.
7. CRUD actions update real-time (websockets or polling), so multiple users see live changes.
8. Show supplier AP summary (open bills, total owed, average payment days) in details panel.
9. Can export supplier list to CSV/Excel.
10. Attempt to delete supplier with existing bills/payments blocked, with explanatory modal.
11. All field edits, activations/deactivations, and import actions logged to the audit trail with before/after data and responsible user.
**Prerequisites:** Story 2.2.
```

```markdown
**Story 2.4: Bank Account Management (CRUD)**
As an accountant, I want to manage cash and bank accounts for all cash receipt/payment flows.
**Acceptance Criteria:**
1. List view shows all cash and bank accounts; enables filtering by type/status, sort by last balance update.
2. "Add" form: Account number (required, must be unique), bank name (validated from list, open entry allowed), opening balance (must match accounting entry at migration).
3. On edit, audit trail logs all changes with reason if field value changed.
4. Cannot delete bank/cash account if referenced in any voucher, period, or reconciliation.
5. Closing (inactivating) an account removes it from pickers, but does not break referential integrity with historical vouchers.
6. Tooltip on bank account selector shows current and prior period balances.
7. Export list of accounts and balances to Excel for audit.
8. Multi-company: Accounts scoped to company_id.

**Prerequisites:** Stories 2.1, 2.2
```

```markdown
**Story 2.5: Company Settings Expansion (Advanced Fields)**
As an admin,
I want to update legal/fiscal settings and reporting options with audit,
So that regulatory compliance and custom numbering/logos are always traceable.
**Acceptance Criteria:**
1. Fiscal year is pre-selected on company creation but can be edited (future years only, no overlap allowed).
2. Currency is VND (locked/read-only in MVP).
3. Admin can set up VAT rates for invoices/bills; must validate unique % per company.
4. Document sequences: optionally editable; cannot change to a code that causes a collision with existing records.
5. Report export config: logo, footer lines—shows preview of financial statements with these values before saving.
6. All changes are transactional: field edit either succeeds fully with all validations or fails leaving old values untouched.
7. Full audit trail on any change.
**Prerequisites:** Story 2.4
```

```markdown
**Story 2.6: Data Import & Migration**
As a developer/admin,
I want to import opening balances, customer/supplier lists, and validate migration for smooth project cut-over,
So that initial rollout or bulk ops never lose compliance or leave orphans.
**Acceptance Criteria:**
1. Import wizard accepts only template files (Excel/CSV); checks that required headers and data types are present.
2. Validator parses all rows before saving: highlights errors and summary after validation (e.g., "Row 12: Invalid tax code, Row 25: Duplicate supplier").
3. On commit, bulk creates master records within transaction; if any row fails, no partial import occurs.
4. Opening balance import only available to admin, only before first period close. Enforces Dr = Cr invariant and warning if negative or zero-value entries.
5. All import errors downloadable as report for offline review.
5. Migration script logs result: inserted count, skipped, errors, and reference to audit trail.
6. "Demo import" uses sample data compatible with dev demo company.
**Prerequisites:** Story 2.1, 2.2, 2.3
```

```markdown
**Story 2.7: Audit Trail & Data Integrity for Master Data**
As an admin/auditor,
I want full audit and orphan detection on all master records,
So that no silent/incorrect changes, failed deletes, or stale data ever persist.
**Acceptance Criteria:**
1. UI "Audit Log" per entity: shows all changes for that customer/supplier/account, filterable by date/user/action type.
2. Exposes audit log API for download/filtering (respecting role-based access).
3. Log format includes: record type, affected fields (old/new), user/email/role, datetime, IP/user agent if present.
4. Detects and logs failed modification attempts (e.g., user tried to delete protected account).
5. Data integrity check endpoint for admin: runs background job to check for orphaned master records across all entities (should return zero in normal state).
6. Bulk actions are split-out in logs for transparency (each import row has separate audit entry).
**Prerequisites:** Story 2.2, 2.3, 2.4, 2.5, 2.6
```
