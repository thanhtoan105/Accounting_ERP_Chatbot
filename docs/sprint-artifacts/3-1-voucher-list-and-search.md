# Story 3.1: Voucher List and Search

Status: review

## Story

As an accountant or chief accountant,
I want to view, search, and filter vouchers with server-side pagination and real-time status counts,
so that I can efficiently navigate and manage voucher entries while maintaining performance with large datasets. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]

## Acceptance Criteria

1. Voucher list table displays columns: Voucher Number, Date, Type, Amount (total), Status (DRAFT/POSTED/REVERSED), Entered By (user name), Posted By (user name, if posted), AR/AP Entity (customer/supplier name if applicable), Reversal Badge (if reversed), Attachment Count. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
2. Filters (status, date range, account), search query text, and sort order persist in user session/localStorage per company. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
3. Live badge counts display draft vs posted voucher totals, updated in real-time after create/post operations. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
4. Fuzzy search supports unaccented Vietnamese text matching on voucher number, description fields; Unicode-aware search. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
5. Multi-column sorting supported (e.g., date DESC + status ASC); sort state persists in session. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
6. Delete action allowed only for vouchers with status=DRAFT and no references (no linked payments/receipts); requires mandatory reason field captured in audit log. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
7. Server-side pagination with page size selector (10/20/30/50/100); infinite scroll deferred to post-MVP. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
8. Empty state displays when no vouchers found: helpful message, "Create First Voucher" CTA, "Reset Filters" link. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
9. API error handling: retry button on error toast, detailed error message in modal, "Copy Error Details" button exports JSON for support. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
10. RBAC enforcement: non-admin users see only their company's vouchers; department/role scoping deferred to post-MVP. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]

## Tasks / Subtasks

- [x] Build VoucherListPage component with DataTablePro (AC: #1, #7, #8)
  - [x] Create `frontend/src/features/accounting/pages/Vouchers/VoucherList.tsx` with TanStack Table integration
  - [x] Implement column definitions: Voucher Number, Date, Type, Amount, Status, Entered By, Posted By, AR/AP Entity, Reversal Badge, Attachment Count
  - [x] Add server-side pagination with page size selector (10/20/30/50/100)
  - [x] Implement empty state with "Create First Voucher" CTA and "Reset Filters" link
- [x] Implement filtering and search functionality (AC: #2, #4)
  - [x] Add filter controls: status dropdown, date range picker, account selector
  - [x] Implement fuzzy search with unaccented Vietnamese text matching (voucher number, description)
  - [x] Persist filter/search/sort state in localStorage per company
  - [x] Wire filters to backend API query parameters
- [x] Add live badge counts and real-time updates (AC: #3)
  - [x] Create badge component showing draft vs posted counts
  - [x] Implement real-time updates after create/post operations (optimistic updates or polling)
  - [x] Wire to GET /api/v1/vouchers/count endpoint
- [x] Implement multi-column sorting (AC: #5)
  - [x] Configure TanStack Table for multi-column sort
  - [x] Persist sort state in session/localStorage
  - [x] Wire sort parameters to backend API
- [x] Build delete functionality with audit logging (AC: #6)
  - [x] Add delete action in row menu (only for DRAFT status)
  - [x] Create delete confirmation dialog with mandatory reason field
  - [x] Wire to DELETE /api/v1/vouchers/{id} endpoint
  - [x] Ensure reason is captured in audit log
- [x] Implement error handling UI (AC: #9)
  - [x] Add error toast with retry button
  - [x] Create error details modal with "Copy Error Details" button (exports JSON)
  - [x] Handle network errors, validation errors, and business rule violations
- [x] Enforce RBAC and company scoping (AC: #10)
  - [x] Apply RoleGuard to VoucherListPage route
  - [x] Verify backend API enforces company scoping via CompanyContext
  - [ ] Test cross-company data isolation
- [x] Create backend API endpoints (AC: #1, #2, #4, #5, #7)
  - [x] Implement GET /api/v1/vouchers with pagination, filtering, search, sorting
  - [x] Implement GET /api/v1/vouchers/count for badge counts
  - [x] Implement DELETE /api/v1/vouchers/{id} with reason parameter
  - [x] Add server-side fuzzy search with unaccented Vietnamese support
  - [x] Ensure company scoping via CompanyScopeAspect
- [x] Add testing subtasks (AC: #1-#10)
  - [x] Unit tests for VoucherListPage component (filtering, sorting, pagination)
  - [x] Integration tests for GET /api/v1/vouchers endpoint (pagination, filters, search, company scoping)
  - [x] Integration tests for DELETE /api/v1/vouchers/{id} (RBAC, audit logging, reason capture)
  - [ ] E2E tests for voucher list workflow (deferred to post-MVP if needed)

## Dev Notes

### Requirements Context Summary

- **Voucher list with comprehensive columns:** Display all required fields including voucher number, date, status, amounts, user information, AR/AP entities, reversal indicators, and attachment counts for complete voucher visibility. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
- **Server-side pagination and filtering:** Implement efficient pagination with configurable page sizes (10/20/30/50/100) and persistent filter/search/sort state to handle large voucher datasets without performance degradation. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#performance]
- **Fuzzy Vietnamese search:** Support unaccented Vietnamese text matching on voucher number and description fields with Unicode-aware search capabilities for user-friendly querying. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
- **Real-time status counts:** Display live badge counts for draft vs posted vouchers, updated automatically after create/post operations to provide immediate feedback on voucher status distribution. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]
- **Delete with audit trail:** Allow deletion only for draft vouchers with no references, requiring mandatory reason field that is captured in audit log for compliance and traceability. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search] [Source: docs/sprint-artifacts/tech-spec-epic-3.md#security]
- **RBAC and company isolation:** Enforce role-based access control and company-level data isolation to ensure users only see vouchers for their company, with department/role scoping deferred to post-MVP. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search] [Source: docs/architecture/security-architecture.md]
- **Error handling and user experience:** Provide comprehensive error handling with retry mechanisms, detailed error messages, and exportable error details for support troubleshooting. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search]

### Structure Alignment Summary

- **Reuse established patterns from Epic 2:** Leverage the DataTablePro patterns, filter persistence, and pagination implementations from master data management pages (customers, suppliers) to maintain UI consistency. [Source: docs/sprint-artifacts/2-2-customer-master-data-management-crud.md] [Source: docs/sprint-artifacts/2-3-supplier-master-data-management-crud.md]
- **Follow feature-first structure:** Place VoucherListPage under `frontend/src/features/accounting/pages/Vouchers/` to align with feature-first architecture and maintain clear module boundaries. [Source: docs/architecture/project-structure.md]
- **Backend API patterns:** Follow REST endpoint conventions established in Epic 2 (`/api/v1/vouchers`), use standard pagination response format `{ data: { content: VoucherDTO[], totalElements: number, totalPages: number }, meta: {...} }`, and enforce company scoping via `CompanyScopeAspect`. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#apis-and-interfaces] [Source: docs/architecture/data-architecture.md]
- **Reuse audit logging infrastructure:** Extend `AuditLogService` from Story 2.7 to log delete operations with reason field, following the established audit payload format with entity metadata and field-level changes. [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#completion-notes-list]

### Learnings from Previous Story (2-7)

**From Story 2-7-audit-trail-data-integrity-for-master-data (Status: done)**

- **Audit Service Infrastructure:** Reuse `AuditServiceImpl` with builder pattern for consistent audit entry creation. The service supports entity metadata, field-level diffs, IP/user-agent capture, and failure reason logging. Use `auditService.logVoucherDeleted()` method following the pattern established for master data entities. [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#completion-notes-list]
- **DataTablePro Patterns:** Story 2.7 created comprehensive audit log UI with TanStack Table, filters, pagination, and page size selector. Reuse these patterns for VoucherListPage to maintain UI consistency. Key components: `frontend/src/features/audit/pages/AuditLogPage.tsx` for reference. [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#file-list]
- **Filter Persistence:** Story 2.7 implemented localStorage persistence for filters per company. Apply same pattern for voucher list filters (status, date range, account, search, sort) to improve user experience. [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#completion-notes-list]
- **RBAC Enforcement:** All audit endpoints use `@PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")` and company scoping via `CompanyContext.getCompanyId()`. Apply same pattern to voucher endpoints, ensuring Accountant+ roles can view vouchers. [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#senior-developer-review-ai]
- **Company Scoping:** All queries filtered by `CompanyContext.getCompanyId()` to prevent cross-company data access. Ensure voucher repository queries use `CompanyScopeAspect` or explicit company filtering. [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#senior-developer-review-ai]
- **No Unresolved Review Items:** Previous story review closed with no unresolved action items, so no carry-over blockers for this iteration. [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#senior-developer-review-ai]

### Project Structure Notes

- **Backend Structure:** Place voucher controller under `backend/src/main/java/com/accounting/controller/voucher/` following the modular structure. Service layer under `backend/src/main/java/com/accounting/service/voucher/` or `service/gl/` as specified in tech spec. Repository under `backend/src/main/java/com/accounting/repository/`. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#system-architecture-alignment]
- **Frontend Structure:** Create voucher pages under `frontend/src/features/accounting/pages/Vouchers/` with `VoucherList.tsx` as the main component. Reuse shared components from `@/components/ui` (DataTablePro, filters, badges) and `@/components/app` (layout components). [Source: docs/sprint-artifacts/tech-spec-epic-3.md#frontend-modules] [Source: docs/architecture/project-structure.md]
- **API Endpoints:** Follow REST convention `/api/v1/vouchers` with standard query parameters for pagination (`page`, `size`), filtering (`status`, `dateFrom`, `dateTo`, `accountId`), search (`search`), and sorting (`sort`). Response format: `{ data: { content: VoucherDTO[], totalElements: number, totalPages: number }, meta: {...} }`. [Source: docs/sprint-artifacts/tech-spec-epic-3.md#rest-endpoints]

### Testing Strategy

- **Test Coverage Pattern:** Follow the comprehensive testing approach from Story 2.7, which achieved 28 tests across all acceptance criteria with integration, unit, and negative test scenarios. [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#testing-subtasks-mapped-to-acs]
- **Integration Testing:** Use `@SpringBootTest` with `IntegrationTest` base class for API endpoint tests, covering RBAC enforcement, company scoping, filtering, pagination, and error handling. Reference `AuditLogControllerIT` (13 tests) as a pattern for voucher endpoint integration tests. [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#senior-developer-review-ai]
- **Unit Testing:** Create unit tests for service layer validation logic, following patterns from `AuditServiceImplSerializationTest` (8 tests) for serialization and data validation. [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#senior-developer-review-ai]
- **Negative Testing:** Include negative test scenarios for blocked operations (e.g., deleting posted vouchers, cross-company access), following the pattern from `AuditServiceNegativeTest` (1 test). [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#senior-developer-review-ai]
- **RBAC Testing:** Reference the RBAC testing guide for role-based access control validation patterns, ensuring proper test user setup and permission verification. [Source: docs/rbac-testing-guide.md]
- **Test Organization:** Organize tests by acceptance criteria, with clear mapping between ACs and test classes (e.g., AC2 → `VoucherControllerIT`, AC6 → `VoucherServiceDeleteTest`). [Source: docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#testing-subtasks-mapped-to-acs]

### References

- docs/sprint-artifacts/tech-spec-epic-3.md#story-31-voucher-list-and-search
- docs/sprint-artifacts/tech-spec-epic-3.md#apis-and-interfaces
- docs/sprint-artifacts/tech-spec-epic-3.md#performance
- docs/sprint-artifacts/tech-spec-epic-3.md#security
- docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md
- docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#testing-subtasks-mapped-to-acs
- docs/sprint-artifacts/2-7-audit-trail-data-integrity-for-master-data.md#senior-developer-review-ai
- docs/sprint-artifacts/2-2-customer-master-data-management-crud.md
- docs/sprint-artifacts/2-3-supplier-master-data-management-crud.md
- docs/architecture/security-architecture.md
- docs/architecture/data-architecture.md
- docs/architecture/project-structure.md
- docs/architecture/architecture-decision-records-adrs.md
- docs/rbac-testing-guide.md

## Change Log

- 2025-11-13: Initial draft created with acceptance criteria, task plan, and structural alignment guidance.
- 2025-11-13: Auto-improved based on validation feedback - added Testing Strategy subsection with references to Story 2.7 testing patterns, added ADR and RBAC testing guide references.

## Dev Agent Record

### Context Reference

- docs/sprint-artifacts/3-1-voucher-list-and-search.context.xml

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- **Backend Enhancements:**

  - Enhanced `VoucherListDTO` to include `arApEntity` field (customer/supplier name extracted from voucher lines)
  - Updated API response format to match spec: `{ data: { content: VoucherDTO[], totalElements, totalPages }, meta: {...} }`
  - Added `accountId` filtering support to GET /api/v1/vouchers endpoint
  - Added GET /api/v1/vouchers/count endpoint alias (existing /counts endpoint also available)
  - Updated `VoucherServiceImpl.toListDTO()` to extract AR/AP Entity from voucher lines (checks customerId and vendorId)
  - Injected `CustomerRepository` and `SupplierRepository` into `VoucherServiceImpl` for entity name lookup

- **Frontend Implementation:**

  - Created comprehensive `VoucherList` component with TanStack Table integration
  - Implemented all required columns: Voucher Number, Date, Type, Amount, Status, Entered By, Posted By, AR/AP Entity, Reversal Badge, Attachment Count
  - Added server-side pagination with page size selector (10/20/30/50/100)
  - Implemented localStorage persistence for filters, search, sort state per company (using `activeCompanyId` from localStorage)
  - Added live badge counts component showing draft/posted/unposted totals with polling (30s interval)
  - Implemented multi-column sorting with TanStack Table (persisted in localStorage)
  - Added delete functionality with confirmation dialog and mandatory reason field
  - Implemented comprehensive error handling: toast with retry button, error details modal with "Copy Error Details" button
  - Added empty state with "Create First Voucher" CTA and "Reset Filters" link
  - Applied RoleGuard to route with roles: ['admin', 'accountant', 'chief_accountant', 'cfo']
  - Updated frontend types to match new API response format
  - Added accountId parameter to VoucherQueryParams

- **Pattern Reuse:**

  - Followed DataTablePro patterns from AuditLogPage for consistency
  - Reused filter persistence pattern from Story 2.7
  - Maintained feature-first structure: `frontend/src/features/accounting/pages/Vouchers/`

- **Testing:**
  - Created comprehensive unit tests for VoucherList component covering filtering, sorting, pagination, localStorage persistence, error handling, and all UI interactions
  - Updated integration tests to match new API response format (data.content, meta object)
  - Added integration tests for accountId filtering, AR/AP Entity extraction, date range filtering, /count endpoint alias, and audit logging verification
  - All tests follow patterns from Story 2.7 (AuditLogControllerIT) for consistency

### File List

**Backend:**

- `backend/src/main/java/com/accounting/dto/VoucherListDTO.java` - Added arApEntity field
- `backend/src/main/java/com/accounting/service/VoucherService.java` - Added accountId parameter to findAll method
- `backend/src/main/java/com/accounting/service/impl/VoucherServiceImpl.java` - Enhanced toListDTO to extract AR/AP Entity, added accountId filtering, injected CustomerRepository and SupplierRepository
- `backend/src/main/java/com/accounting/controller/voucher/VoucherController.java` - Updated response format, added accountId parameter, added /count endpoint alias

**Frontend:**

- `frontend/src/types/voucher.ts` - Added arApEntity to VoucherListDTO, updated PaginatedResponse format, added accountId to VoucherQueryParams
- `frontend/src/services/voucher.ts` - Updated to handle new response format, added accountId parameter
- `frontend/src/features/accounting/pages/Vouchers/VoucherList.tsx` - New comprehensive component with all features
- `frontend/src/features/accounting/pages/Vouchers/index.ts` - Barrel export
- `frontend/src/features/accounting/index.ts` - Added VoucherList export
- `frontend/src/routes/AppRoutes.tsx` - Added /vouchers route with RoleGuard

**Tests:**

- `frontend/src/features/accounting/pages/Vouchers/__tests__/VoucherList.test.tsx` - Comprehensive unit tests for VoucherList component (filtering, sorting, pagination, localStorage persistence, error handling)
- `backend/src/test/java/com/accounting/controller/voucher/VoucherControllerIntegrationTest.java` - Updated integration tests for new API response format, added tests for accountId filtering, AR/AP Entity, date range filtering, /count endpoint, audit logging verification
