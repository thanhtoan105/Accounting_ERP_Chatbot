## Context

### Background
Đây là đồ án tốt nghiệp hệ thống kế toán multi-tenant cho SME Việt Nam. Hệ thống cần một flow onboarding phù hợp với:
- Thực tế doanh nghiệp VN (cần hướng dẫn setup COA, kỳ kế toán)
- Tuân thủ pháp lý (NĐ 123/2020/NĐ-CP về hóa đơn điện tử)
- Best practices về security và multi-tenancy

### Constraints
- Phải tương thích với kiến trúc hiện có (CompanyContext, CompanyScopedEntity)
- Giữ nguyên invitation flow đã có, chỉ enhance
- Migration không được làm mất dữ liệu existing

### Stakeholders
- Sinh viên (developer)
- Giáo viên hướng dẫn (reviewer)
- Hội đồng bảo vệ (evaluator)

---

## Goals / Non-Goals

### Goals
1. Tạo flow onboarding production-ready cho multi-tenant SaaS
2. Đảm bảo security: tenant isolation, least privilege, audit trail
3. Tuân thủ thực tế VN: KYC nhẹ, COA preset (TT200/TT133)
4. Thể hiện kiến thức chuyên môn để bảo vệ trước hội đồng
5. Hỗ trợ ops/sales tạo tenant (B2B model)

### Non-Goals
1. Self-service signup (có thể thêm sau)
2. Trial/billing system
3. MFA implementation (out of scope)
4. Super admin portal (chỉ API endpoints)

---

## Decisions

### Decision 1: Admin-Invited Only Model

**What**: Chỉ ops/sales có thể tạo tenant mới, users phải được mời

**Why**:
- Phù hợp B2B enterprise model
- Kiểm soát chất lượng tenant (KYC)
- Giảm spam/fake accounts
- SME VN cần hỗ trợ setup chuẩn

**Alternatives considered**:
| Option | Pros | Cons |
|--------|------|------|
| Self-service signup | Faster growth, scalable | Spam risk, no KYC, complex billing |
| Hybrid | Best of both | Complex implementation |
| **Admin-invited** ✓ | Controlled, secure, VN-compliant | Manual bottleneck (acceptable for SME) |

---

### Decision 2.5: Repository-Level Tenant Scoping

**What**: Enforce companyId filtering at repository layer, not just header validation

**Why**:
- Header validation alone is insufficient (can be bypassed by bugs in service layer)
- Defense in depth: multiple layers of protection
- Prevents accidental cross-tenant queries from developer mistakes

**Implementation Pattern**:
```java
// 1. Base repository with automatic scoping
public interface CompanyScopedRepository<T extends CompanyScopedEntity> 
    extends JpaRepository<T, Long> {
    
    @Query("SELECT e FROM #{#entityName} e WHERE e.companyId = :companyId")
    List<T> findAllByCompanyId(@Param("companyId") Long companyId);
    
    default List<T> findAllScoped() {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new CompanyScopeViolationException("Company context required");
        }
        return findAllByCompanyId(companyId);
    }
}

// 2. Aspect to intercept and validate all repository calls
@Aspect
@Component
public class RepositoryScopeAspect {
    @Before("execution(* com.accounting.repository..*.*(..))")
    public void enforceCompanyScope(JoinPoint jp) {
        // Validate entity.companyId matches CompanyContext
        // Throw exception if mismatch detected
    }
}

// 3. Service layer always uses scoped methods
@Service
public class VoucherService {
    public List<Voucher> getAll() {
        // Automatically filtered by current company
        return voucherRepository.findAllScoped();
    }
}
```

**Layers of Protection**:
```
┌─────────────────────────────────────────────────────────┐
│ Layer 1: X-Company-Id Header Validation (Filter)        │
├─────────────────────────────────────────────────────────┤
│ Layer 2: CompanyContext ThreadLocal (Service)           │
├─────────────────────────────────────────────────────────┤
│ Layer 3: Repository Scoped Methods (Repository)         │
├─────────────────────────────────────────────────────────┤
│ Layer 4: CompanyScopeAspect Validation (AOP)            │
├─────────────────────────────────────────────────────────┤
│ Layer 5: DB CHECK Constraint (Database) - future        │
└─────────────────────────────────────────────────────────┘
```

---

### Decision 3: User.companyId NOT NULL

**What**: Enforce companyId bắt buộc ở DB level (trừ super_admin)

**Why**:
- Eliminate edge cases với null companyId
- Simplify frontend/backend logic
- Prevent data leakage từ unscoped queries

**Migration Strategy**:
```sql
-- 1. Generate orphan user report for manual review
SELECT id, email, created_at, last_login_at 
FROM users 
WHERE company_id IS NULL 
ORDER BY last_login_at DESC;
-- Export to CSV for ops team review

-- 2. Notify orphan users (grace period: 30 days)
-- Send email: "Your account requires company assignment. Contact admin."

-- 3. After grace period: Soft delete orphans
UPDATE users 
SET status = 'INACTIVE', 
    deleted_at = NOW(),
    deactivation_reason = 'No company assignment after migration grace period'
WHERE company_id IS NULL AND is_super_admin = FALSE;

-- 4. Add constraint (only after all orphans handled)
ALTER TABLE users 
ADD CONSTRAINT chk_user_company 
CHECK (company_id IS NOT NULL OR is_super_admin = TRUE);
```

**⚠️ REJECTED Option**: Tự động gán orphan users vào default company
- **Lý do**: Vi phạm tenant isolation, ô nhiễm dữ liệu, rủi ro pháp lý
- **Thay thế**: Manual review + soft delete với grace period

---

### Decision 4: Enhanced Invitation Token

**What**: One-time, expiring, revocable tokens

**Implementation**:
```java
@Entity
public class Invitation {
    @Id
    private UUID id;
    
    private String email;
    private String token; // SHA256 hash
    private Role role;
    private Long companyId;
    
    private Instant createdAt;
    private Instant expiresAt; // default 24h
    private Instant acceptedAt;
    private Instant revokedAt;
    
    private Long invitedBy;
    private String ipAddress; // audit
}
```

**Token Flow**:
```
1. Admin clicks "Invite User"
2. Backend generates secure token, stores hash
3. Email sent with link: /invitation/accept?token=xxx
4. User clicks → validates token → creates account → joins company
5. Token marked as accepted (one-time use)
```

---

### Decision 5: Tenant Provisioning Workflow

**What**: Admin Portal UI cho Super Admin tạo tenant

**Workflow**:
```
┌─────────────────┐     ┌──────────────┐     ┌────────────────┐
│ Admin Portal UI │────▶│ Create Tenant│────▶│ Send Invitation│
│ (Form nhập)     │     │ + Copy COA   │     │ to Admin       │
└─────────────────┘     └──────────────┘     └────────────────┘
                               │
                               ▼
                        ┌──────────────┐
                        │ Company with │
                        │ - TT200 COA  │ ← Copy từ template có sẵn
                        │ - Default FY │
                        │ - Currency   │
                        └──────────────┘
```

**COA Seeding** (sử dụng function có sẵn trong database):
```sql
-- Đã có: tt200_chart_of_accounts_template (235 accounts)
-- Đã có function: seed_tt200_coa_from_template(company_id)

-- TenantProvisioningService chỉ cần gọi:
@Query(value = "SELECT seed_tt200_coa_from_template(:companyId)", nativeQuery = true)
void seedCOA(@Param("companyId") Long companyId);
```

**API Design**:
```
POST /api/v1/admin/tenants
{
  "companyName": "ABC Corp",
  "taxCode": "0123456789",
  "address": "123 Nguyễn Huệ, Q1, HCM",
  "legalRepresentative": "Nguyễn Văn A",
  "fiscalYearStart": "01-01",
  "currency": "VND",
  "adminEmail": "admin@abc.com",
  "adminName": "Nguyen Van A"
}

Response:
{
  "companyId": 123,
  "invitationId": "uuid",
  "message": "Invitation sent to admin@abc.com"
}
```

**Admin Portal UI Flow**:
```
1. Super Admin login → Sidebar menu "Quản trị" → "Tenant Management"
2. Click "Tạo Tenant mới" → Dialog/Drawer mở ra
3. Fill form với validation (tax code 10/14 digits, email format)
4. Submit → Backend xử lý
5. Toast: "Đã tạo tenant ABC Corp và gửi lời mời đến admin@abc.com"
6. Danh sách tenants refresh, hiển thị tenant mới
```

---

### Decision 6: Frontend Flow Redesign

**Current Flow (broken)**:
```
Login → hasNoCompany? → CompanySettings firstTimeSetup → Create company
```

**New Flow**:
```
Login → hasNoCompany? → AwaitingCompany page → "Contact admin for invitation"
       │
       └─▶ hasCompany → Dashboard

Invitation Accept:
Click link → InvitationAccept page → Create password → Auto-login → Onboarding wizard
```

**Route Structure**:
```typescript
// Public routes (no auth)
/invitation/accept/:token → InvitationAcceptPage

// Protected routes
/awaiting-company → AwaitingCompanyPage (when no company)
/onboarding → OnboardingWizard (first login after invitation)
/ → Dashboard (normal flow)
```

---

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|------------|
| Ops bottleneck | Slow tenant creation | Automation, SLA, self-service later |
| Breaking change | Existing users blocked | Migration script, grace period |
| Email delivery | Users can't accept invitation | Retry mechanism, resend UI |
| Token security | Account takeover | Short expiry, hash storage, IP logging |
| Demo data corruption | Testing impacted | Separate demo seeding, isDemo flag |

---

## Migration Plan

### Phase 1: Database Schema (non-breaking)
1. Add new columns to `invitations` table (expiresAt, revokedAt, ipAddress)
2. Create `tenant_provisions` audit table
3. **DO NOT** add NOT NULL constraint yet

### Phase 2: Backend Implementation
1. Implement TenantController với provisioning API
2. Enhance InvitationService với expiry logic
3. Update CompanyContextFilter cho strict validation
4. Add audit logging

### Phase 3: Frontend Implementation
1. Create InvitationAcceptPage
2. Create AwaitingCompanyPage
3. Update CompanyGuard logic
4. Remove firstTimeSetup from CompanySettings

### Phase 4: Migration & Enforcement
1. Run migration script cho orphan users
2. Add NOT NULL constraint
3. Update demo seeding
4. Deploy & monitor

### Rollback Plan
```sql
-- If critical issues:
ALTER TABLE users ALTER COLUMN company_id DROP NOT NULL;
-- Frontend: revert to previous CompanyGuard logic
```

---

## Open Questions

1. **Demo environment**: Giữ nguyên demo seeding hay tạo ops CLI riêng?
   - **Proposed**: Giữ demo seeding cho development, thêm ops CLI cho production

2. **Super admin role**: Cần không? Để làm gì?
   - **Proposed**: Không cần cho MVP, có thể thêm sau

3. **Email service**: Dùng Resend hiện có hay cần queue?
   - **Proposed**: Dùng Resend trực tiếp, thêm retry logic

4. **Invitation resend**: Admin có thể resend invitation không?
   - **Proposed**: Có, revoke token cũ và tạo token mới

---

## Architecture Diagrams

### Tenant Provisioning Sequence

```
┌─────┐          ┌─────────────────┐          ┌────────────────┐          ┌───────┐
│ Ops │          │ TenantController│          │ProvisionService│          │ Email │
└──┬──┘          └────────┬────────┘          └───────┬────────┘          └───┬───┘
   │                      │                           │                       │
   │  POST /tenants       │                           │                       │
   │─────────────────────▶│                           │                       │
   │                      │  provisionTenant()        │                       │
   │                      │──────────────────────────▶│                       │
   │                      │                           │                       │
   │                      │  ┌─────────────────────┐  │                       │
   │                      │  │ 1. Create Company   │  │                       │
   │                      │  │ 2. Seed COA (TT200) │  │                       │
   │                      │  │ 3. Create Invitation│  │                       │
   │                      │  └─────────────────────┘  │                       │
   │                      │                           │                       │
   │                      │                           │  sendInvitation()     │
   │                      │                           │──────────────────────▶│
   │                      │                           │                       │
   │                      │◀──────────────────────────│                       │
   │  201 Created         │                           │                       │
   │◀─────────────────────│                           │                       │
```

### Invitation Accept Sequence

```
┌──────┐        ┌──────────────────┐        ┌─────────────────┐        ┌──────────┐
│ User │        │ InvitationAccept │        │ InvitationCtrl  │        │ AuthCtrl │
└──┬───┘        └────────┬─────────┘        └────────┬────────┘        └────┬─────┘
   │                     │                           │                      │
   │  Click email link   │                           │                      │
   │────────────────────▶│                           │                      │
   │                     │  GET /invitation/:token   │                      │
   │                     │──────────────────────────▶│                      │
   │                     │  {email, companyName}     │                      │
   │                     │◀──────────────────────────│                      │
   │                     │                           │                      │
   │  Enter password     │                           │                      │
   │────────────────────▶│                           │                      │
   │                     │  POST /invitation/accept  │                      │
   │                     │──────────────────────────▶│                      │
   │                     │                           │                      │
   │                     │  ┌─────────────────────┐  │                      │
   │                     │  │ 1. Validate token   │  │                      │
   │                     │  │ 2. Create user      │  │                      │
   │                     │  │ 3. Mark accepted    │  │                      │
   │                     │  └─────────────────────┘  │                      │
   │                     │                           │                      │
   │                     │                           │  login()             │
   │                     │                           │─────────────────────▶│
   │                     │                           │  {token, user}       │
   │                     │◀──────────────────────────│◀─────────────────────│
   │  Redirect dashboard │                           │                      │
   │◀────────────────────│                           │                      │
```
