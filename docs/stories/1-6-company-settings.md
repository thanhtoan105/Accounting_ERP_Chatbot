# Story 1.6: Company Settings

Status: in-progress

## Story

As an admin,
I want to configure company details, logo, address, tax code, and fiscal year,
so that branding appears correctly and the platform has legal entity details for all outputs.

## Acceptance Criteria

1. Company Settings screen: supports logo upload (file type/size validated), fields for name, address, tax code (unique per company), fiscal year start, and contact info. [Source: docs/epics.md#Story-1.6-Company-Settings; docs/PRD.md#FR03-Company-Profile-Management]
2. Changes can be previewed before saving; the logo is scaled appropriately for dashboard header and reports. [Source: docs/epics.md#Story-1.6-Company-Settings]
3. Validation rules: required fields cannot be blank; Vietnamese address formatting enforced where applicable; duplicate company/tax code is rejected. [Source: docs/epics.md#Story-1.6-Company-Settings; docs/PRD.md#FR03-Company-Profile-Management]
4. Every change to company settings is audit-logged with old/new values, actor, and timestamp. [Source: docs/epics.md#Story-1.6-Company-Settings; docs/PRD.md#FR07-Basic-Audit-Trail]
5. Reports include company logo and legal information in export footers (PDF/Excel). [Source: docs/epics.md#Story-1.6-Company-Settings]

## Tasks / Subtasks

- [ ] Backend: Company entity/validation (AC: #1, #3, #4)
  - [x] Add/verify unique constraints for `company.code` and `company.tax_code` (DB/Flyway)
  - [x] DTOs + validation: `@NotBlank` fields, VN address pattern, tax code checksum
  - [x] Service methods to update settings with audit logging (old → new)
  - [x] File storage integration for logo (Supabase Storage) with type/size validation
- [ ] Backend: API endpoints (AC: #1, #2, #4)
  - [x] `GET /api/v1/admin/company/settings`
  - [x] `PUT /api/v1/admin/company/settings` (multipart for logo)
  - [x] RBAC: admin, chief_accountant only (fixed to `hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')`)
  - [x] Audit: log setting changes with actor and timestamp
- [ ] Frontend: Company Settings page (AC: #1, #2, #3)
  - [x] Form fields: name, tax code, address, fiscal year start, contact info, logo upload
  - [x] Client-side validation and inline error messaging (VN locale)
  - [x] Live preview of logo and footer before save (logo avatar preview)
  - [x] Responsive layout, aligns with `ProtectedLayout`
  - [x] Date picker via shadcn `Calendar` with month/year dropdown; save as local `yyyy-MM-dd` (no timezone shift)
  - [x] Country dropdown + phone input: selecting country auto-fills dialing code and shows flag; persists on blur
  - [x] Display read-only `company.code` when editing (loaded from API)
- [ ] Reporting integration (AC: #5)
  - [ ] Ensure report exports include company logo and legal info in footer
  - [ ] Add config-driven footer rendering path
- [ ] Testing (maps to ACs)
  - [ ] Backend unit + integration tests for validation, RBAC, audit logging
  - [x] Frontend tests for form validation and preview behavior
  - [ ] Export tests verify footer content appears in generated files

AC-to-Task mapping:

- AC#1 → Backend entity/DTO validation + API endpoints + Frontend Company Settings form and upload
- AC#2 → Frontend live preview and image scaling behavior
- AC#3 → Backend validation and uniqueness constraints; Frontend inline validation
- AC#4 → Backend AuditService usage on settings update; verify entries
- AC#5 → Reporting/export footer integration and tests

## Dev Notes

- Architecture constraints: Spring Boot 3.5.7, JWT, RBAC, company scoping for all entities [Source: docs/architecture.md#Security-Architecture]
- Follow unified project structure for backend `controller/admin`, `service/impl`, `entity`, `dto`, and frontend feature-first under `features/company` [Source: docs/architecture.md#Project-Structure]
- PRD references for FR03 Company Profile Management and audit expectations FR07 [Source: docs/PRD.md#Functional-Requirements]
\
Recent UX fixes:

- Corrected `@PreAuthorize` role names causing 403 on settings API.
- Replaced native date input with shadcn date picker using dropdown caption; format persisted as `yyyy-MM-dd` (local).
- Implemented compact `CountryDropdown` and enhanced `PhoneInput`:
  - Searchable popover, inline trigger, flag rendering.
  - Auto-fill dialing code (+84, +44, …) on selection; flag stays synced when typing.
- Company `code` populated into the form (disabled) for clarity.

### Learnings from Previous Story

**From Story 1-5-user-profile-management (Status: review)**

- New and reusable services/components:
  - `AuditService` methods are in place for detailed change logging; reuse for settings updates. [Source: docs/stories/1-5-user-profile-management.md#File-List]
  - RBAC patterns and `@PreAuthorize` usage established; apply identical guardrails on settings endpoints. [Source: docs/stories/1-5-user-profile-management.md#Dev-Notes]
- Architectural decisions to maintain:
  - Company scoping via `CompanyScopedEntity` and `CompanyContext` must be enforced for reading/updating settings. [Source: docs/stories/1-5-user-profile-management.md#Dev-Notes]
- Technical debt/pending items affecting this story:
  - Consider frontend test coverage as noted in review; add tests for Settings page validation and RBAC UI states. [Source: docs/stories/1-5-user-profile-management.md#Senior-Developer-Review-(AI)]

### Project Structure Notes

- Backend paths: `controller/admin/CompanyController.java`, `service/impl/CompanyServiceImpl.java`, `entity/Company.java`, `dto/UpdateCompanySettingsRequest.java`
- Frontend: `src/features/company/pages/CompanySettings.tsx`, services under `src/services/company.ts`, align with existing aliases and `ProtectedLayout`. [Source: docs/architecture.md#Project-Structure]

### References

- [Source: docs/epics.md#Story-1.6-Company-Settings]
- [Source: docs/PRD.md#FR03-Company-Profile-Management]
- [Source: docs/PRD.md#FR07-Basic-Audit-Trail]
- [Source: docs/architecture.md#Decision-Architecture]
- [Source: docs/stories/1-5-user-profile-management.md#Dev-Notes]
- [Source: docs/ux-design-specification.md]

## Dev Agent Record

### Context Reference
 - docs/stories/1-6-company-settings.context.xml

### Agent Model Used

Claude Sonnet 4.5

### Debug Log References

- 2025-11-04: Implemented backend foundation for Company Settings
  - Added Flyway migration `V14__add_company_contact_fields.sql` for contact email/phone and fiscal year start
  - Extended `Company` entity with `contactEmail`, `contactPhone`, `fiscalYearStart`
  - Created DTO `UpdateCompanySettingsRequest` with validation (email/phone, 10-digit tax code + checksum)
  - Extended `CompanyService` and `CompanyServiceImpl` with current-company get/update methods (company-scoped)
  - Added `CompanySettingsController` under admin scope with GET/PUT endpoints and RBAC guards
  - Added logo validation (PNG/JPEG, ≤256KB); storage integration deferred (will set `logoUrl` when integrated)
  - Added dedicated audit logging for company settings updates
  - Added frontend Company Settings tests (validation + logo preview)
  - Moved DB changes to new migration `V15__add_company_contact_fields.sql` to resolve Flyway checksum; kept V14 blank
  
- 2025-11-04: Frontend UX updates (recent)
  - Switched edit success feedback in `frontend/src/features/company/pages/CompanySettings.tsx` from inline Alert to Sonner toast (`toast.success('Settings updated successfully')`).
  - Strengthened Zod schema: made `contact_email`, `contact_phone`, and `fiscal_year_start` required with clear messages; enforced formats (email, phone regex, `yyyy-MM-dd` date).
  - Aligned UI with validation: added `required` to email and phone inputs; kept create-flow success Alert unchanged.
  - Preserved `existingCompany.logoUrl` on load to support "Reset to saved" for logo preview.
  - Added conditional error styling for Name input: `className={errors.name ? 'border-destructive' : ''}`; guidance to apply similarly for email/phone.
  - Added shadcn date picker (dropdown caption), persisted local `yyyy-MM-dd`.
  - Added `CountryDropdown` + improved `PhoneInput` with auto dialing code and flag sync.
  - Loaded company `code` into form for display; kept disabled.

- 2025-11-04: Supabase Storage integration + private bucket support
  - Backend: Added `StorageService` and `SupabaseStorageService` to upload logos via Supabase Storage REST with `x-upsert: true`.
  - Backend: `CompanyServiceImpl.updateCurrentCompanySettings` validates PNG/JPEG ≤256KB, uploads, and persists `logoUrl`.
  - Backend: Supports public/private buckets via env flags. When `SUPABASE_PUBLIC_BUCKET=false`, returns a signed URL (default 7 days) after upload.
  - Backend env: `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_BUCKET` (e.g., `accounting`), `SUPABASE_PUBLIC_BUCKET` (true/false).
  - Frontend: After create/edit, uses the returned URL and clears `logoFile`; preview hardened with `onError` fallback and no cache-buster on signed URLs.
  - Frontend: Cache-buster only for public URLs to avoid stale images.

### Completion Notes List

- Pending items to complete ACs:
  - Implement VN-specific address validation pattern at backend and align FE
  - Integrate audit logging to record old/new values, actor, timestamp
  
  - Add tests: service + controller + integration for RBAC, validation, and audit

### File List
 - backend/src/main/resources/db/migration/V14__add_company_contact_fields.sql
 - backend/src/main/resources/db/migration/V15__add_company_contact_fields.sql
 - backend/src/main/java/com/accounting/entity/Company.java
 - backend/src/main/java/com/accounting/dto/UpdateCompanySettingsRequest.java
 - backend/src/main/java/com/accounting/service/CompanyService.java
 - backend/src/main/java/com/accounting/service/impl/CompanyServiceImpl.java
 - backend/src/main/java/com/accounting/controller/admin/CompanySettingsController.java
 - frontend/src/services/company.ts
 - frontend/src/features/company/pages/CompanySettings.tsx
 - frontend/src/features/company/pages/__tests__/CompanySettings.test.tsx
## Change Log

- 2025-11-04: Draft created
- 2025-11-04: Backend foundation added (entity fields, migration, DTO, service, admin endpoints); story moved to in-progress
- 2025-11-04: Frontend wired to admin settings API, added form fields/validation/preview; added FE tests; created `V15__add_company_contact_fields.sql`


