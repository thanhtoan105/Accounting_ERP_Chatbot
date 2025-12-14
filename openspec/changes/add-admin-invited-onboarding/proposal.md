# Change: Add Admin-Invited Onboarding Flow

## Why

Hệ thống hiện tại không có flow onboarding production-ready:
- Public registration đã bị xóa, chỉ có demo seeding
- User có thể tồn tại mà không có company (companyId nullable)
- Không có cách tạo tenant mới ngoài việc chạy seed script
- Flow "firstTimeSetup" không phù hợp với thực tế doanh nghiệp VN

Đây là đồ án tốt nghiệp và cần một giải pháp phù hợp với thực tế SME Việt Nam, tuân thủ pháp lý (NĐ 123/2020/NĐ-CP về hóa đơn điện tử), và thể hiện kiến thức chuyên môn về security/architecture.

## What Changes

### 1. Tenant Provisioning (Admin Portal UI)
- Thêm trang **Admin Portal** cho Super Admin tạo tenant mới
- **Frontend**: Form tạo tenant với validation
- **Backend**: Endpoint `/api/v1/admin/tenants`
- Workflow: Fill form → Tạo company → Copy COA từ template → Gửi invitation
- **COA Seeding**: Sử dụng function có sẵn `seed_tt200_coa_from_template(company_id)` - KHÔNG cần tạo mới
- **VN Compliance Fields**:
  - Tax code validation (10 or 14 digits, check digit)
  - Legal company name
  - Business address
  - Legal representative name
  - Invoice series config (placeholder for future e-invoice integration)

### 2. User Registration via Invitation Only
- **BREAKING**: Bỏ flow "firstTimeSetup" tự tạo company
- User PHẢI được mời vào company đã tồn tại
- Invitation token: one-time, có expiry, có thể revoke

### 3. Enhanced Invitation Flow
- Email verification bắt buộc
- Token expiry (24h default)
- Rate limiting (5 invites/hour)
- Audit logging cho mọi invitation event

### 4. Backend Security Hardening
- **BREAKING**: `user.companyId` NOT NULL (trừ super_admin)
- **DECISION**: Cho phép login nhưng restrict access (không block login hoàn toàn)
  - User không có company vẫn login được → redirect AwaitingCompany
  - Backend trả về `requiresCompany: true` trong JWT claims
  - Chỉ cho phép access `/api/v1/auth/*` và `/api/v1/invitations/*`
- Validate `X-Company-Id` header khớp với `user.companyId`
- **Repository-level scoping**: Enforce `companyId` filter trong tất cả repositories
- Rate limiting cho public invitation endpoints (5 attempts/minute)

### 4.1 Super Admin Bootstrap
- Thêm CLI command để tạo super admin đầu tiên: `mvnd spring-boot:run -Dbootstrap.superadmin=true`
- Super admin có thể access `/api/v1/admin/*` endpoints mà không cần companyId

### 5. Frontend Guards
- Redirect đến "Awaiting Invitation" page nếu chưa có company
- Remove firstTimeSetup mode trong CompanySettings
- Add invitation accept page

## Impact

### Affected Specs
- `tenant-onboarding` (NEW) - Tenant provisioning flow
- `user-auth` (NEW) - Authentication & authorization 
- `invitation` (NEW) - User invitation system

### Affected Code

#### Backend
- `entity/User.java` - Add NOT NULL constraint cho companyId
- `entity/Invitation.java` - Add expiry, revoked fields
- `entity/Tenant.java` (NEW) - Tenant/Company provisioning entity
- `controller/admin/TenantController.java` (NEW) - Ops portal endpoints
- `controller/InvitationController.java` - Enhance với email verify
- `security/CompanyContextFilter.java` - Strict validation
- `service/TenantProvisioningService.java` (NEW) - Tenant setup logic

#### Frontend
- `features/admin/pages/TenantManagementPage.tsx` (NEW) - Admin Portal UI
- `features/admin/components/CreateTenantForm.tsx` (NEW) - Form tạo tenant
- `features/auth/pages/InvitationAccept.tsx` (NEW)
- `features/auth/pages/AwaitingCompany.tsx` (NEW)
- `components/guards/CompanyGuard.tsx` - Remove firstTimeSetup bypass
- `features/company/pages/CompanySettings.tsx` - Remove firstTimeSetup mode
- `routes/AppRoutes.tsx` - Add invitation routes + admin routes

#### Database
- `V20251209001__add_tenant_provisioning.sql`
- `V20251209002__user_company_not_null.sql`
- `V20251209003__enhance_invitations.sql`

### Breaking Changes Summary
1. Users without company cannot login
2. Existing users with null companyId need migration
3. FirstTimeSetup UI flow removed
4. Demo seeding must create proper tenant structure
