## 1. Database Schema

### 1.1 Invitations Enhancement
- [x] 1.1.1 Create migration `V20251209001__enhance_invitations.sql`
  - Add `revoked_at TIMESTAMP`
  - Add `accepted_at TIMESTAMP`
  - Add `ip_address VARCHAR(45)`
  - Add `invited_by BIGINT REFERENCES users(id)`
  - Add `token_hash VARCHAR(64)` for security
  - Add index on `token` for fast lookup
  - Add index on `token_hash` for secure lookups
  - Add composite index on `email, company_id`

### 1.2 Tenant Provisioning Audit
- [x] 1.2.1 Create migration `V20251209002__tenant_provisioning_audit.sql`
  - Create `tenant_provisions` table for audit trail
  - Fields: id, company_id, created_by, coa_preset, admin_email, admin_name, invitation_id, metadata (JSONB), created_at

### 1.3 User Company Constraint
- [x] 1.3.1 Create migration `V20251209003__user_super_admin.sql`
  - Add `is_super_admin BOOLEAN DEFAULT FALSE` to users
  - Add CHECK constraint: `company_id IS NOT NULL OR is_super_admin = TRUE` (NOT VALID for graceful migration)
  - NOTE: Constraint validation deferred until orphan users are handled

---

## 2. Backend Implementation

### 2.1 Entity Updates
- [x] 2.1.1 Update `Invitation.java`
  - Add expiresAt, revokedAt, acceptedAt, ipAddress, invitedBy, tokenHash fields
  - Add isExpired(), isRevoked(), isAccepted(), isActive() helper methods

- [x] 2.1.2 Update `User.java`
  - Add isSuperAdmin field
  - Field mapped to `is_super_admin` column

### 2.2 DTOs
- [x] 2.2.1 Create `TenantProvisionRequest.java`
  - companyName, taxCode, address, legalRepresentative, fiscalYearStart, currency
  - adminEmail, adminName, coaPreset
  - Validation annotations (tax code pattern, email, etc.)

- [x] 2.2.2 Create `TenantProvisionResponse.java`
  - companyId, companyName, invitationId, adminEmail, message

- [x] 2.2.3 `AcceptInvitationRequest.java` already exists
  - password, confirmPassword, fullName fields

- [x] 2.2.4 `InvitationResponse.java` updated
  - Has email, companyName, role, expiresAt

- [x] 2.2.5 Create `InvitationValidateResponse.java`
  - valid, email, companyName, role, expiresAt, status, errorMessage
  - Static factory methods for valid/invalid responses

### 2.3 Services
- [x] 2.3.1 Create `TenantProvisioningService.java`
  - provisionTenant(request): Company + COA copy + Invitation
  - Uses `seed_tt200_coa_from_template(company_id)` via native query
  - Uses @Transactional for atomicity

- [x] 2.3.2 Update `InvitationService.java`
  - createInvitation(): Generate secure token, set expiry
  - createInvitationForCompany(): For super admin tenant provisioning
  - validateInvitation(): Check expired/revoked/accepted
  - acceptInvitation(): Create user, mark accepted, log IP
  - revokeInvitation(): Set revokedAt
  - resendInvitation(): Revoke old + create new

- [ ] 2.3.3 Update `AuthService.java` (OPTIONAL - handled by CompanyGuard on frontend)
  - login(): Block if user.companyId is null (and not super_admin)

### 2.4 Controllers
- [x] 2.4.1 Create `TenantController.java` in `/controller/admin/`
  - POST /api/v1/admin/tenants - Provision new tenant
  - Require SUPER_ADMIN role via @PreAuthorize

- [x] 2.4.2 Update `InvitationController.java`
  - GET /api/v1/invitations/{token} - Get invitation info (public)
  - POST /api/v1/invitations/{token}/accept - Accept invitation (public)
  - POST /api/v1/invitations/{id}/revoke - Revoke invitation
  - POST /api/v1/invitations/{id}/resend - Resend invitation

### 2.5 Security
- [ ] 2.5.1 Update `CompanyContextFilter.java` (OPTIONAL - stricter validation)
  - Strict validation: X-Company-Id must match user.companyId
  - Log warning for mismatches

- [x] 2.5.2 Update `SecurityConfig.java`
  - Allow /api/v1/invitations/{token} public
  - Allow /api/v1/invitations/{token}/accept public
  - Allow /api/v1/invitations/validate/* public
  - Require SUPER_ADMIN for /api/v1/admin/**

### 2.6 Email Templates
- [ ] 2.6.1 Create invitation email template (DEFERRED - email service exists, template enhancement optional)
  - Company name, inviter name, role
  - Accept link with token
  - Expiry warning

---

## 3. Frontend Implementation

### 3.0 Admin Portal UI
- [x] 3.0.1 Create `features/admin/pages/TenantManagementPage.tsx`
  - List existing tenants (DataTable with search, pagination)
  - "Tạo Tenant mới" button opens dialog/drawer
  - Show tenant status, created date, admin email

- [x] 3.0.2 Create `features/admin/components/CreateTenantDialog.tsx`
  - Fields: companyName, taxCode, address, legalRep, adminEmail, adminName
  - Tax code validation (10 or 14 digits)
  - Email validation
  - Submit → POST /api/v1/admin/tenants
  - Success toast: "Đã gửi lời mời đến {email}"

- [x] 3.0.3 Create `features/admin/api/tenantApi.ts`
  - createTenant(request): POST /api/v1/admin/tenants
  - listTenants(): GET /api/v1/admin/tenants
  - getTenantDetails(id): GET /api/v1/admin/tenants/{id}

- [x] 3.0.4 Update `routes/AppRoutes.tsx`
  - Add `/admin/tenants` route (requires SUPER_ADMIN)
  - Add to sidebar menu for super admin only

### 3.1 Pages
- [x] 3.1.1 Update `AcceptInvitation.tsx`
  - Validate token on mount
  - Show company name, role info
  - Password form with validation
  - Submit → create account → auto-login → redirect to dashboard

- [x] 3.1.2 Create `AwaitingCompanyPage.tsx`
  - Display "You don't belong to any company yet"
  - Instructions to contact administrator
  - Logout button

- [ ] 3.1.3 Create `OnboardingWizardPage.tsx` (OPTIONAL - post-MVP)
  - First-time user guided tour
  - COA overview, period setup, preferences

### 3.2 Components
- [x] 3.2.1 Update `CompanyGuard.tsx`
  - Redirect to /awaiting-company if no companyId (except super_admin)
  - Added allowNoCompany prop for specific routes

- [x] 3.2.2 Update `roles.ts`
  - Added super_admin role
  - Updated role hierarchy and permissions

- [x] 3.2.3 Update `LoginForm.tsx`
  - Remove redirect to /company when hasNoCompany
  - Always navigate to / and let CompanyGuard handle routing

### 3.3 Routes
- [x] 3.3.1 Update `AppRoutes.tsx`
  - Add `/invite/:token` route (public) - already existed
  - Add `/awaiting-company` (protected)
  - Add `/admin/tenants` (super_admin only)

### 3.4 API Integration
- [x] 3.4.1 `invitationApi.ts` already exists with:
  - validateToken(token): Get invitation info
  - acceptInvitation(token, password): Accept and create account

### 3.5 Cleanup
- [x] 3.5.1 Update `CompanySettings.tsx`
  - Remove createCompany flow
  - Always show edit mode (only accessible if has company)

---

## 4. Testing

### 4.1 Backend Tests
- [x] 4.1.1 `TenantProvisioningServiceTest.java`
  - Test provision creates company, COA, invitation
  - Test transaction rollback on failure

- [x] 4.1.2 `InvitationServiceTest.java`
  - Test token generation and hashing
  - Test expiry validation
  - Test accept flow
  - Test revoke and resend

- [x] 4.1.3 `TenantControllerIntegrationTest.java`
  - Test full provision endpoint
  - Test authorization (only SUPER_ADMIN)

### 4.2 Frontend Tests
- [x] 4.2.1 `InvitationAcceptPage.test.tsx`
  - Test token validation display
  - Test password submission
  - Test error handling

- [x] 4.2.2 `CompanyGuard.test.tsx`
  - Test redirect to awaiting-company

---

## 5. Migration & Deployment

### 5.1 Data Migration
- [ ] 5.1.1 Create orphan user report script
  - Identify users with null companyId
  - Generate migration plan
  - **OUTPUT CSV** for manual review before migration

- [ ] 5.1.2 Run orphan user migration
  - **DO NOT** auto-assign to default company (violates tenant isolation)
  - **ONLY** Option: Soft delete orphans with grace period notification
  - Document decisions in migration log

### 5.2 Super Admin Bootstrap
- [ ] 5.2.1 Create CLI command for super admin creation
  - `mvnd spring-boot:run -Dbootstrap.superadmin.email=xxx -Dbootstrap.superadmin.password=xxx`
  - Only works when no super admin exists
  - Audit log the creation

### 5.3 Demo Seeding Update
- [ ] 5.3.1 Update `DemoBootstrapServiceImpl.java`
  - Create company BEFORE users
  - Ensure all demo users have companyId
  - Add isDemo flag to company

### 5.4 Documentation
- [ ] 5.4.1 Update API documentation
  - Document new endpoints
  - Update Postman collection

- [ ] 5.4.2 Update README
  - Add onboarding flow explanation
  - Add ops provisioning guide

---

## 6. Verification

### 6.1 Manual Testing Checklist
- [ ] 6.1.1 Test complete flow:
  1. Ops provisions new tenant
  2. Admin receives invitation email
  3. Admin clicks link, creates password
  4. Admin logs in, sees dashboard
  5. Admin invites accountant
  6. Accountant accepts, joins company

- [ ] 6.1.2 Test edge cases:
  - Expired token
  - Already accepted token
  - Revoked token
  - User without company tries to login

### 6.2 Security Testing
- [ ] 6.2.1 Test token security
  - Cannot guess token
  - Token hash not reversible
  - Rate limiting works

- [ ] 6.2.2 Test tenant isolation
  - User cannot access other company data
  - X-Company-Id spoofing blocked
