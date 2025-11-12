# Epic 1: Project Foundation & Secure Authentication

**Expanded Goal:**
Set up a secure, scalable full-stack foundation with robust authentication, multi-tenancy, company/user management, and developer/devops tooling to enable all subsequent module builds and deployments.

```markdown
**Story 1.1: Initialize Project Repositories & DevOps**
As a developer,
I want to have a clear project structure, development tooling, and CI/CD baseline,
So that all team members can build, test, and deploy reliably from day one.
**Acceptance Criteria:**
1. Repository structure for Spring Boot (backend), React (frontend), and infra/config (docker, scripts) is clearly documented.
2. Docker Compose can start all dev dependencies: Supabase/Postgres (with dev schema seeded), Maildev (for password reset tests), Redis (if used).
3. Pre-commit hooks for linting/format; clear README for local setup/env vars/troubleshooting.
4. GitHub Actions: build/test/lint/publish for each module.
5. Health check endpoint `/health` returns 200 OK + info.
6. OpenAPI/Swagger UI at `/api/docs` with baseline template.
**Prerequisites:** None.
```

```markdown
**Story 1.2: Company Bootstrap & Multitenancy**
As an admin,
I want to initialize and configure one or more companies with isolated data and branding,
So that the system cleanly supports multiple tenants from the start.
**Acceptance Criteria:**
1. First login (or CLI/script) prompts to create the first company; reject duplicate company codes.
2. Company form validates: unique 10-digit tax code, company name (VN charset), address, logo.
3. All core data (users, chart of accounts, vouchers, master data) associated with a `company_id` and enforced at the DB and API layer.
4. Demo company auto-created with realistic data for preview/demo
5. Switching company context (if user belongs to multiple) works without session corruption.
6. Company branding (logo, name) appears in dashboard shell and voucher/report exports.
**Prerequisites:** Story 1.1
```

```markdown
**Story 1.3: User Registration & Secure Authentication**
As a user/admin,
I want secure registration, login, and account security features,
So that I can trust platform access is safe and compliant.
**Acceptance Criteria:**
1. Signup form (email, password, full name); shows password policy and enforces complexity.
2. Backend: password stored using Argon2 or Bcrypt; never logged/stored in plaintext.
3. JWT issued on login; access token short-lived, refresh token long-lived.
4. Session uses HttpOnly, Secure cookie setup (works with HTTPS locally/in prod).
5. "Remember me" persists session up to 30 days using refresh tokens/cookies.
6. "Forgot password" sends secure email from Maildev/Supabase, supports token expiration/reset link.
7. Repeated failed login attempts lock out account for 5 minutes and log IP/user agent.
8. Audit trail logs all logins, failed attempts, resets.
**Prerequisites:** Stories 1.1, 1.2
```

```markdown
**Story 1.4: Role-Based Access Control (RBAC)**
As an admin,
I want to define roles and enforce permissions,
So that API/UX only exposes functions allowed by user's assigned role.
**Acceptance Criteria:**
1. User entity includes a `role` field (`admin`, `accountant`, `chief_accountant`, `cfo`).
2. Permission matrix defined and documented (API + UI): which endpoints/resources each role can access.
3. Middleware checks JWT and role on each API request; 403 returned with context-aware error message.
4. UI hides menu items/screens based on role, but API rejects unauthorized backend access regardless of UI.
5. All role/permission changes are logged in audit trail.
6. Changing one's own role is not permitted (only admin/chief can edit others).
**Prerequisites:** Stories 1.2, 1.3
```

```markdown
**Story 1.5: User & Profile Management**
As an admin,
I want to list, filter, create, edit, or deactivate users and assign roles,
So that I can manage security and onboard/change staff.
**Acceptance Criteria:**
1. User listing supports filtering by role, status, and search by email/name.
2. Create/edit user form validates all fields; cannot duplicate email.
3. Deactivated users cannot log in (and are visually separated).
4. "Reset password" flow allows admin to send an email to users; link expires after 30 minutes.
5. Profile screen allows user to edit their own name, view effective role, change password.
6. Role badge displayed on each user in lists and details.
7. User creation, edit, activation, deactivation, and role changes are all audit-logged with who/when.
**Prerequisites:** Stories 1.2, 1.4
```

```markdown
**Story 1.6: Company Settings**
As an admin,
I want to configure company details, logo, address, tax code, and fiscal year,
So that branding appears correctly and platform has legal entity details for all outputs.
**Acceptance Criteria:**
1. Company Settings screen: logo upload (image size/type validated), name, address, tax code (unique check), fiscal year start, contact info.
2. Changes previewed before saving; logo scaled for dashboard header and reports.
3. Validation: no blank fields, VN address requirements, unique company/tax code enforced.
4. All changes to company settings written to audit log with old/new value, who, and when.
5. Display company logo and legal info in the print/export footer of each financial report.
**Prerequisites:** Story 1.2
```

```markdown
**Story 1.7: MVP Branding & App Layout**
As a user,
I want a branded, professional shell and responsive layout,
So that the UI is clear, trustworthy and usable for critical flows.
**Acceptance Criteria:**
1. Branded login page: logo, color scheme based on company, support for dark mode.
2. Authenticated layout has persistent sidebar, header (user avatar), and theme.
3. Logged-in username, company, and effective role always visible.
4. Professional, unobtrusive design (MUI tokens); all screens handle loading/error states.
5. Responsive to 1920x1080 and 1366x768; tablet screens at least usable for critical flows.
**Prerequisites:** Stories 1.2, 1.6
```

```markdown
**Story 1.8: Admin Demo Data Seed**
As a developer,
I want to bootstrap a demo company, users, and chart of accounts for tests,
So that everyone can develop, demo, and QA flows instantly.
**Acceptance Criteria:**
- CLI/dev command populates demo company, sample users (1/admin, 1/accountant, 1/chief, 1/cfo), and minimal chart of accounts.
- Demo data covers most mainline flows ("happy path").
- Clear, tested rollback (data reset, truncate tables); instructions in README.
- Demo company marked as [DEMO] in UI and audit logs.
**Prerequisites:** Stories 1.1, 1.2
```

---
