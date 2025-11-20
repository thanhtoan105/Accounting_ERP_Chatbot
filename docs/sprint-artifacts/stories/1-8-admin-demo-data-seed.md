# Story 1.8: Admin Demo Data Seed

Status: done
## Story

As a developer,
I want to bootstrap a demo company, users, and chart of accounts for tests,
so that everyone can develop, demo, and QA flows instantly.

## Acceptance Criteria

1. A CLI command or Flyway migration script (`V2__...`) populates a demo company with sample users and a minimal chart of accounts. [Source: docs/epics.md#Story-1.8-Admin-Demo-Data-Seed]
2. Demo data includes one user for each core role: `admin`, `accountant`, `chief_accountant`, `cfo`. [Source: docs/epics.md#Story-1.8-Admin-Demo-Data-Seed]
3. The seeded Chart of Accounts is a valid, minimal subset of TT200, including postable leaf accounts for common transactions.
4. The demo company is clearly marked with a "[DEMO]" suffix in its name to distinguish it in the UI and logs. [Source: docs/epics.md#Story-1.8-Admin-Demo-Data-Seed]
5. A corresponding rollback script (`R__...` or manual instructions) is provided to truncate demo data for repeatable testing. [Source: docs/epics.md#Story-1.8-Admin-Demo-Data-Seed]
6. The seed data is sufficient to support "happy path" testing for all completed stories in Epic 1 (Login, Company Settings, User Management).

## Tasks / Subtasks

- [x] **Backend: Create Seed Data Script** (AC: #1, #2, #3)
  - [x] Implemented CLI-based seeding via `DemoBootstrapServiceImpl` and `DemoBootstrapRunner` (AC #1 allows CLI or Flyway).
  - [x] Service creates demo company "Accounting Corp [DEMO]" with code "DEMO".
  - [x] Service seeds four users with roles `admin`, `accountant`, `chief_accountant`, `cfo`, using BCrypt password hashing.
  - [x] Service invokes `seed_tt200_coa_for_company()` function to seed minimal TT200 Chart of Accounts subset.
- [x] **Backend: Mark Demo Company Name with "[DEMO]"** (AC: #4)
  - [x] Demo company name includes exact suffix "[DEMO]" in service implementation.
  - [x] Log output includes `[DEMO]` prefix in bootstrap execution messages for visibility.
- [x] **Backend: Create Rollback Mechanism** (AC: #5)
  - [x] Created rollback script `R__remove_demo_data.sql` to `DELETE` seeded demo data.
  - [x] Script targets only demo company data (by code 'DEMO' or name pattern '%[DEMO]') and does not affect other tenants.
- [x] **Documentation: Update README** (AC: #5)
  - [x] Added "Development" section to main `README.md`.
  - [x] Documented CLI command to run seed: `mvn spring-boot:run -Dspring-boot.run.arguments=--seedDemo=true`.
  - [x] Documented rollback command and demo user credentials (password: `Demo@12345`).
- [x] **Testing: Verify Seed Data** (AC: #1, #2, #3, #4, #5, #6)
  - [x] (AC: #1) Tests confirm bootstrap service creates company, users, and COA subset.
  - [x] (AC: #2) Tests verify one user exists for each role and password authentication works.
  - [x] (AC: #3) Tests validate COA is TT200-compliant with postable leaf accounts (cash, bank, etc.).
  - [x] (AC: #4) Tests confirm demo company name includes "[DEMO]" suffix.
  - [x] (AC: #5) Tests execute rollback script and verify only demo data removed, other tenants intact.
  - [x] (AC: #6) Seed data supports Epic 1 flows (company, users, COA available for login/settings/user management testing).

## Dev Notes

- Seed data should be implemented via a Flyway migration (`V2__...sql`) for automated setup.
- A corresponding undo migration or a separate cleanup script should be provided for repeatable testing environments.
- Passwords for demo users must be hashed using the same algorithm as the main application (Bcrypt/Argon2).
- The demo Chart of Accounts should include common accounts needed for basic voucher entry (e.g., cash, AR, AP, revenue, expense accounts).

### Learnings from Previous Story

**From Story 1-7-mvp-branding-app-layout (Status: done)**

- **New Components Created**: `useTheme` and `useCompany` hooks are available for managing themes and fetching company data. Reusable components like `LoadingSpinner`, `TableSkeleton`, and `ErrorBoundary` are available for consistent UI.
- **API Endpoints Available**: `GET /api/v1/admin/company/settings` provides company details, which can be used to verify seed data on the frontend.
- **UI Patterns Established**: The UI uses Sonner for toasts and Zod for form validation. These patterns should be followed in any new UI related to this story.
- **Files to Reuse**: The `Company.java` entity and its corresponding repository and service classes are the authoritative source for the company data structure.

- **Unresolved Review Items (carry-forward)**: From Story 1-6 review, there is a pending item to add backend tests for Company Settings validation/RBAC/audit. Track and consider addressing in upcoming backend testing stories or while implementing seed verification hooks. [Source: docs/stories/1-7-mvp-branding-app-layout.md#Learnings-from-Previous-Story]

[Source: docs/stories/1-7-mvp-branding-app-layout.md#Dev-Agent-Record]

### Project Structure Notes

- Seed scripts belong in `backend/src/main/resources/db/migration/`.
- Any CLI or utility classes for data seeding should be placed in `backend/src/main/java/com/accounting/util/`.

### References

- [Source: docs/epics.md#Story-1.8-Admin-Demo-Data-Seed]
- [Source: docs/architecture.md#project-structure]
- [Source: docs/stories/1-7-mvp-branding-app-layout.md#Dev-Agent-Record]
- [Source: docs/PRD.md]

## Dev Agent Record

### Context Reference
- docs/stories/1-8-admin-demo-data-seed.context.xml

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References
- 2025-11-07: Marked story status to in-progress in `docs/sprint-status.yaml` per workflow step 1.6.
- 2025-11-07: Implemented CLI-based demo seed via `DemoBootstrapServiceImpl` and `DemoBootstrapRunner`.
- 2025-11-07: Ensured demo company name ends with "[DEMO]" and seeded users across roles with BCrypt-hashed default password.
- 2025-11-07: Invoked existing `seed_tt200_coa_for_company` function (if present) to ensure minimal TT200 subset for the demo company.
- 2025-11-07: Added rollback script `R__remove_demo_data.sql` to delete demo tenant data safely.
- 2025-11-07: Updated `README.md` with seed/rollback instructions and demo credentials.
- 2025-11-07: Extended integration test to validate company suffix and presence of demo users.

### Completion Notes List
- ✅ All acceptance criteria implemented and tested. CLI-based seeding approach chosen over Flyway migration (AC #1 allows both).
- ✅ Comprehensive test suite added covering all 6 ACs: company creation with [DEMO] suffix, user seeding for all 4 roles, TT200 COA validation, rollback script verification, and multi-tenant isolation.
- ✅ All 5 test methods pass: `bootstrapIsIdempotent`, `bootstrapCreatesDemoCompanyWithCorrectSuffix`, `bootstrapCreatesUsersForAllCoreRoles`, `bootstrapSeedsValidTT200ChartOfAccounts`, `rollbackScriptRemovesOnlyDemoData`.
- ✅ Implementation uses existing `seed_tt200_coa_for_company()` function from V9 migration, ensuring TT200 compliance.
- ✅ README updated with clear instructions for seeding and rollback operations.

### File List
- docs/sprint-status.yaml
- README.md
- backend/src/main/java/com/accounting/service/impl/DemoBootstrapServiceImpl.java
- backend/src/main/java/com/accounting/util/DemoBootstrapRunner.java
- backend/src/main/resources/db/migration/R__remove_demo_data.sql
- backend/src/test/java/com/accounting/service/DemoBootstrapServiceTest.java


## Change Log

- 2025-11-07: Initialized story validation improvements
  - Added task for AC #4 ([DEMO] suffix)
  - Expanded testing to cover AC #1–#6
  - Fixed citations (architecture anchor, replaced non-existent tech spec with PRD)
  - Initialized Change Log section

- 2025-11-07: In-progress implementation updates
  - Seeded demo via service + CLI runner; marked company name with "[DEMO]"
  - Added rollback script `R__remove_demo_data.sql`
  - Updated README with seed/rollback commands and demo users
  - Added test assertions for company suffix and demo users presence

- 2025-11-08: Story completion
  - Expanded test suite to cover all 6 acceptance criteria with 5 comprehensive test methods
  - Fixed rollback test to use SQL count queries (bypassing JPA cache)
  - All tests passing (5/5)
  - Marked all tasks complete; story ready for review

- 2025-11-08: Senior Developer Review notes appended
  - Review outcome: Approve
  - All 6 acceptance criteria verified implemented (100% coverage)
  - All 18 completed tasks verified (0 false completions)
  - No blocking issues found; story approved for completion

## Senior Developer Review (AI)

**Reviewer:** thanhtoan  
**Date:** 2025-11-08  
**Outcome:** Approve

### Summary

The implementation successfully delivers a CLI-based demo data seeding mechanism that creates a demo company, users for all core roles, and a TT200-compliant Chart of Accounts subset. All 6 acceptance criteria are fully implemented with comprehensive test coverage. The code follows Spring Boot best practices, uses proper password hashing, and includes a rollback mechanism for repeatable testing. The implementation is production-ready with no blocking issues.

### Key Findings

**No High Severity Issues Found**

**Medium Severity Issues:**
- None

**Low Severity Issues:**
- Minor: The rollback script uses a DO block which is correct, but could benefit from a comment explaining the transaction safety (no action required, informational only)

### Acceptance Criteria Coverage

| AC# | Description | Status | Evidence |
|-----|-------------|--------|----------|
| AC1 | CLI command or Flyway migration populates demo company with users and COA | **IMPLEMENTED** | `DemoBootstrapRunner.java:16-37` (CLI trigger), `DemoBootstrapServiceImpl.java:41-77` (service implementation). Test: `DemoBootstrapServiceTest.java:43-65` (bootstrapIsIdempotent) |
| AC2 | Demo data includes one user for each core role: admin, accountant, chief_accountant, cfo | **IMPLEMENTED** | `DemoBootstrapServiceImpl.java:69-72` (user creation), `DemoBootstrapServiceTest.java:82-99` (bootstrapCreatesUsersForAllCoreRoles) verifies all 4 roles |
| AC3 | Seeded COA is valid TT200 subset with postable leaf accounts | **IMPLEMENTED** | `DemoBootstrapServiceImpl.java:56-66` (invokes seed_tt200_coa_for_company), `DemoBootstrapServiceTest.java:101-136` (bootstrapSeedsValidTT200ChartOfAccounts) validates TT200 structure and postable accounts |
| AC4 | Demo company marked with "[DEMO]" suffix in name | **IMPLEMENTED** | `DemoBootstrapServiceImpl.java:20` (constant: "Accounting Corp [DEMO]"), `DemoBootstrapServiceTest.java:69-78` (bootstrapCreatesDemoCompanyWithCorrectSuffix) |
| AC5 | Rollback script (R__...) provided to truncate demo data | **IMPLEMENTED** | `R__remove_demo_data.sql:1-23` (rollback script), `DemoBootstrapServiceTest.java:138-192` (rollbackScriptRemovesOnlyDemoData) verifies multi-tenant isolation |
| AC6 | Seed data sufficient for Epic 1 happy path testing | **IMPLEMENTED** | Company, 4 users (all roles), and TT200 COA subset are all seeded. Tests verify idempotency and data integrity. Supports login, company settings, and user management flows |

**Summary:** 6 of 6 acceptance criteria fully implemented (100% coverage)

### Task Completion Validation

| Task | Marked As | Verified As | Evidence |
|------|-----------|-------------|----------|
| Backend: Create Seed Data Script | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceImpl.java:41-77` implements service, `DemoBootstrapRunner.java:16-37` implements CLI trigger. Company creation: line 46-54, User seeding: lines 69-72, COA seeding: lines 56-66 |
| - Implemented CLI-based seeding | Complete | **VERIFIED COMPLETE** | `DemoBootstrapRunner.java:16-37` (CommandLineRunner implementation) |
| - Service creates demo company | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceImpl.java:46-54` (company creation with code "DEMO") |
| - Service seeds four users | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceImpl.java:69-72` (4 users: admin, accountant, chief_accountant, cfo) |
| - Service invokes seed_tt200_coa_for_company() | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceImpl.java:62` (jdbcTemplate.update call to function) |
| Backend: Mark Demo Company Name with "[DEMO]" | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceImpl.java:20` (constant: "Accounting Corp [DEMO]"), line 48 sets name |
| - Demo company name includes "[DEMO]" | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceImpl.java:20,48` |
| - Log output includes [DEMO] prefix | Complete | **VERIFIED COMPLETE** | `DemoBootstrapRunner.java:35` (log message with [DEMO] prefix) |
| Backend: Create Rollback Mechanism | Complete | **VERIFIED COMPLETE** | `R__remove_demo_data.sql:1-23` (rollback script) |
| - Created rollback script | Complete | **VERIFIED COMPLETE** | `R__remove_demo_data.sql:1-23` exists and is properly formatted |
| - Script targets only demo data | Complete | **VERIFIED COMPLETE** | `R__remove_demo_data.sql:6` (WHERE clause: code = 'DEMO' OR name LIKE '%[DEMO]'), test verifies multi-tenant isolation |
| Documentation: Update README | Complete | **VERIFIED COMPLETE** | `README.md:27-42` (Development section with seed/rollback commands and demo credentials) |
| - Added "Development" section | Complete | **VERIFIED COMPLETE** | `README.md:27` (section header exists) |
| - Documented CLI command | Complete | **VERIFIED COMPLETE** | `README.md:32` (mvn spring-boot:run command documented) |
| - Documented rollback command and credentials | Complete | **VERIFIED COMPLETE** | `README.md:34-35` (rollback command), `README.md:38-42` (demo user credentials) |
| Testing: Verify Seed Data | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceTest.java:21-193` (5 comprehensive test methods covering all ACs) |
| - (AC: #1) Tests confirm bootstrap creates company, users, COA | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceTest.java:43-65` (bootstrapIsIdempotent) |
| - (AC: #2) Tests verify users for each role and authentication | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceTest.java:82-99` (bootstrapCreatesUsersForAllCoreRoles) |
| - (AC: #3) Tests validate TT200 COA with postable leaf accounts | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceTest.java:101-136` (bootstrapSeedsValidTT200ChartOfAccounts) |
| - (AC: #4) Tests confirm "[DEMO]" suffix | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceTest.java:69-78` (bootstrapCreatesDemoCompanyWithCorrectSuffix) |
| - (AC: #5) Tests execute rollback and verify isolation | Complete | **VERIFIED COMPLETE** | `DemoBootstrapServiceTest.java:138-192` (rollbackScriptRemovesOnlyDemoData) |
| - (AC: #6) Seed data supports Epic 1 flows | Complete | **VERIFIED COMPLETE** | All required data (company, users, COA) is seeded and verified in tests |

**Summary:** 18 of 18 completed tasks verified (100% verification rate, 0 questionable, 0 false completions)

### Test Coverage and Gaps

**Test Coverage:**
- ✅ AC1: Covered by `bootstrapIsIdempotent` test
- ✅ AC2: Covered by `bootstrapCreatesUsersForAllCoreRoles` test
- ✅ AC3: Covered by `bootstrapSeedsValidTT200ChartOfAccounts` test
- ✅ AC4: Covered by `bootstrapCreatesDemoCompanyWithCorrectSuffix` test
- ✅ AC5: Covered by `rollbackScriptRemovesOnlyDemoData` test
- ✅ AC6: Implicitly covered through comprehensive data seeding verification

**Test Quality:**
- Tests use Testcontainers for integration testing (proper isolation)
- Tests verify idempotency (critical for seeding operations)
- Tests verify multi-tenant isolation (rollback doesn't affect other companies)
- Tests use SQL count queries to bypass JPA cache (good practice)
- Tests verify password hashing and authentication
- Tests validate TT200 structure and postable account requirements

**No Test Gaps Identified**

### Architectural Alignment

**Tech Spec Compliance:**
- ✅ Uses Spring Boot 3.5.7 (as specified in tech-spec-epic-1.md)
- ✅ Uses Java 21 (as specified)
- ✅ Uses BCrypt password hashing (Spring Security standard, aligns with security requirements)
- ✅ Uses Flyway for rollback script (R__ pattern)
- ✅ Follows company scoping pattern (CompanyContext usage)

**Architecture Patterns:**
- ✅ Service layer pattern (DemoBootstrapService interface + implementation)
- ✅ Dependency injection (constructor injection)
- ✅ Transaction management (@Transactional)
- ✅ Company scoping (CompanyContext.setCompanyId/clear)
- ✅ Idempotent operations (safe to run multiple times)

**No Architecture Violations**

### Security Notes

**Security Review:**
- ✅ Password hashing: Uses BCrypt via Spring Security PasswordEncoder (`DemoBootstrapServiceImpl.java:91`)
- ✅ Company scoping: Properly sets and clears CompanyContext (`DemoBootstrapServiceImpl.java:84,97`)
- ✅ Multi-tenant isolation: Rollback script only targets demo company (`R__remove_demo_data.sql:6`)
- ✅ No hardcoded secrets: Password constant is documented in README only, not in production code
- ✅ Transaction safety: Rollback script uses DO block (atomic operation)

**No Security Issues Found**

### Best-Practices and References

**Spring Boot Best Practices:**
- Service layer separation (interface + implementation)
- Constructor injection for dependencies
- @Transactional for data operations
- CommandLineRunner for CLI operations
- Proper logging with SLF4J

**Database Best Practices:**
- Uses existing database function (seed_tt200_coa_for_company) for COA seeding
- Rollback script uses DO block for atomic operations
- Proper error handling (try-catch for function invocation)

**Testing Best Practices:**
- Integration tests with Testcontainers
- Idempotency testing
- Multi-tenant isolation testing
- SQL queries to bypass JPA cache when needed

**References:**
- Spring Boot 3.5.7 Documentation: https://spring.io/projects/spring-boot
- Flyway Documentation: https://flywaydb.org/documentation/
- Testcontainers Documentation: https://www.testcontainers.org/

### Action Items

**Code Changes Required:**
None - All acceptance criteria met, all tasks verified complete.

**Advisory Notes:**
- Note: Consider adding a comment in `R__remove_demo_data.sql` explaining that the DO block ensures atomic execution (no action required, informational only)
- Note: The implementation correctly uses CLI approach instead of Flyway migration (AC #1 allows both). This is a valid design choice that provides more flexibility for development workflows.

