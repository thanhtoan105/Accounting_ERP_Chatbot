# Permission Matrix

This document defines the role-based access control (RBAC) permissions for the accounting system API endpoints.

## Roles

- **ADMIN**: Technical administrator with full system access
- **ACCOUNTANT**: Standard user who can create and manage vouchers, view reports
- **CHIEF_ACCOUNTANT**: Senior accountant who can approve vouchers, close periods, manage users
- **CFO**: Chief Financial Officer who can view reports and audit logs

## API Endpoint Permissions

### Authentication Endpoints

| Endpoint | Method | ADMIN | ACCOUNTANT | CHIEF_ACCOUNTANT | CFO | Public |
|----------|--------|-------|------------|------------------|-----|--------|
| `/api/v1/auth/login` | POST | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/auth/register` | POST | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/auth/refresh` | POST | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/auth/forgot-password` | POST | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/auth/reset-password` | POST | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/auth/logout` | POST | ✅ | ✅ | ✅ | ✅ | ✅ |

### User Management Endpoints

| Endpoint | Method | ADMIN | ACCOUNTANT | CHIEF_ACCOUNTANT | CFO | Public |
|----------|--------|-------|------------|------------------|-----|--------|
| `/api/v1/users` | GET | ✅ | ❌ | ✅ | ❌ | ❌ |
| `/api/v1/users/{id}` | GET | ✅ | ❌ | ✅ | ❌ | ❌ |
| `/api/v1/users/{id}/role` | PUT | ✅ | ❌ | ✅ | ❌ | ❌ |
| `/api/v1/users` | POST | ✅ | ❌ | ✅ | ❌ | ❌ |

**Notes:**
- Users cannot change their own role (enforced at API level)
- Only ADMIN and CHIEF_ACCOUNTANT can view and manage users
- Role assignment requires ADMIN or CHIEF_ACCOUNTANT role

### Invitation Endpoints

| Endpoint | Method | ADMIN | ACCOUNTANT | CHIEF_ACCOUNTANT | CFO | Public |
|----------|--------|-------|------------|------------------|-----|--------|
| `/api/v1/invitations` | POST | ✅ | ❌ | ✅ | ❌ | ❌ |
| `/api/v1/invitations/{token}` | GET | ❌ | ❌ | ❌ | ❌ | ✅ |
| `/api/v1/invitations/{token}/accept` | POST | ❌ | ❌ | ❌ | ❌ | ✅ |

**Notes:**
- Creating invitations requires ADMIN or CHIEF_ACCOUNTANT role
- Invitation validation and acceptance are public (no authentication required)
- Invitation tokens must be valid and not expired

### Company Management Endpoints

| Endpoint | Method | ADMIN | ACCOUNTANT | CHIEF_ACCOUNTANT | CFO | Public |
|----------|--------|-------|------------|------------------|-----|--------|
| `/api/v1/companies` | GET | ✅ | ✅ | ✅ | ✅ | ❌ |
| `/api/v1/companies/{id}` | GET | ✅ | ✅ | ✅ | ✅ | ❌ |
| `/api/v1/companies/{id}` | PUT | ✅ | ❌ | ✅ | ❌ | ❌ |
| `/api/v1/companies` | POST | ✅ | ❌ | ✅ | ❌ | ❌ |

**Notes:**
- All authenticated users can view their company information
- Only ADMIN or CHIEF_ACCOUNTANT can modify company settings

### Voucher Endpoints (Future)

| Endpoint | Method | ADMIN | ACCOUNTANT | CHIEF_ACCOUNTANT | CFO | Public |
|----------|--------|-------|------------|------------------|-----|--------|
| `/api/v1/vouchers` | GET | ✅ | ✅ | ✅ | ✅ | ❌ |
| `/api/v1/vouchers` | POST | ✅ | ✅ | ✅ | ❌ | ❌ |
| `/api/v1/vouchers/{id}` | PUT | ✅ | ✅ | ✅ | ❌ | ❌ |
| `/api/v1/vouchers/{id}/approve` | POST | ✅ | ❌ | ✅ | ❌ | ❌ |

**Notes:**
- Voucher creation and editing available to ACCOUNTANT, CHIEF_ACCOUNTANT, and ADMIN
- Voucher approval requires CHIEF_ACCOUNTANT or ADMIN role
- CFO can view vouchers but cannot create or approve

### Report Endpoints (Future)

| Endpoint | Method | ADMIN | ACCOUNTANT | CHIEF_ACCOUNTANT | CFO | Public |
|----------|--------|-------|------------|------------------|-----|--------|
| `/api/v1/reports` | GET | ✅ | ✅ | ✅ | ✅ | ❌ |
| `/api/v1/reports/{id}` | GET | ✅ | ✅ | ✅ | ✅ | ❌ |
| `/api/v1/reports/{id}/export` | GET | ✅ | ✅ | ✅ | ✅ | ❌ |

**Notes:**
- All authenticated roles can view reports
- Report export available to all roles

### Audit Log Endpoints

| Endpoint | Method | ADMIN | ACCOUNTANT | CHIEF_ACCOUNTANT | CFO | Public |
|----------|--------|-------|------------|------------------|-----|--------|
| `/api/v1/audit-logs` | GET | ✅ | ❌ | ✅ | ✅ | ❌ |
| `/api/v1/audit-logs/{id}` | GET | ✅ | ❌ | ✅ | ✅ | ❌ |

**Notes:**
- Audit logs visible to ADMIN, CHIEF_ACCOUNTANT, and CFO
- ACCOUNTANT cannot view audit logs

### Health Check Endpoints

| Endpoint | Method | ADMIN | ACCOUNTANT | CHIEF_ACCOUNTANT | CFO | Public |
|----------|--------|-------|------------|------------------|-----|--------|
| `/api/v1/health` | GET | ✅ | ✅ | ✅ | ✅ | ✅ |

**Notes:**
- Health check endpoint is public (no authentication required)

## Permission Summary by Role

### ADMIN
- ✅ Full system access
- ✅ User management (create, update, assign roles)
- ✅ Company management
- ✅ Invitation management
- ✅ Voucher management (create, edit, approve)
- ✅ Report access
- ✅ Audit log access

### CHIEF_ACCOUNTANT
- ✅ User management (view, create, assign roles)
- ✅ Company management
- ✅ Invitation management
- ✅ Voucher management (create, edit, approve)
- ✅ Report access
- ✅ Audit log access
- ❌ Cannot change own role

### ACCOUNTANT
- ✅ Voucher management (create, edit)
- ✅ Report access
- ❌ User management
- ❌ Company settings modification
- ❌ Invitation management
- ❌ Voucher approval
- ❌ Audit log access

### CFO
- ✅ Report access (view and export)
- ✅ Audit log access
- ❌ Voucher management
- ❌ User management
- ❌ Company settings modification

## Security Notes

1. **UI Hiding vs API Enforcement**: UI may hide features based on role, but all API endpoints MUST enforce authorization at the backend level. Manual API access attempts must be blocked.

2. **Error Messages**: 
   - 403 Forbidden: Access denied with role requirement message (e.g., "Access denied. Required role: ADMIN")
   - 401 Unauthorized: Missing or invalid authentication token, or missing role claim in JWT

3. **Self-Role-Change Prevention**: Users cannot change their own role, even if they have ADMIN privileges. This is enforced at both API and UI levels.

4. **Role Validation**: Invalid role values are rejected with 400 Bad Request (not 403 Forbidden).

5. **JWT Token Requirements**: All JWT access tokens must include the user's role in the token claims. Missing role claim results in 401 Unauthorized.

## Last Updated

2025-11-01

