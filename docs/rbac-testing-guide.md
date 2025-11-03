# RBAC Testing Guide

This guide provides step-by-step instructions for testing Role-Based Access Control (RBAC) features in the accounting application.

## Overview

The application implements 4 user roles with different permission levels:
- **admin**: Full system access
- **accountant**: Create vouchers, view reports
- **chief_accountant**: Manage users, approve vouchers, access company settings
- **cfo**: View reports only (read-only access)

## Prerequisites

1. Backend running on `http://localhost:8080`
2. Frontend running on `http://localhost:5173`
3. Database with test users in different roles

### Setting Up Test Users

If you need to create test users, you can use the registration endpoint or directly insert into the database:

```sql
-- Example SQL (adjust as needed)
-- Note: Passwords must be BCrypt hashed
-- Example hash for password "password123": $2a$10$...

INSERT INTO users (email, password_hash, full_name, role, company_id, created_at, updated_at)
VALUES 
  ('admin@test.com', '$2a$10$...', 'Admin User', 'admin', 1, NOW(), NOW()),
  ('accountant@test.com', '$2a$10$...', 'Accountant User', 'accountant', 1, NOW(), NOW()),
  ('chief@test.com', '$2a$10$...', 'Chief Accountant', 'chief_accountant', 1, NOW(), NOW()),
  ('cfo@test.com', '$2a$10$...', 'CFO User', 'cfo', 1, NOW(), NOW());
```

Or use the backend API to create users (requires authentication).

## Test Scenarios

### Test 1: Admin User - Full Access

**Objective**: Verify admin has access to all features

**Steps**:
1. Navigate to `http://localhost:5173/login`
2. Login with admin credentials
3. Verify redirect to Dashboard (if company exists) or Company Settings (if no company)
4. Check navigation sidebar

**Expected Results**:
- ✅ Dashboard menu item visible
- ✅ Company Settings menu item visible
- ✅ User Management menu item visible
- ✅ Reports menu item visible
- ✅ Vouchers menu item visible
- ✅ Can navigate to all pages without 403 errors

**Manual Verification**:
- Click each menu item - all should load successfully
- No 403 Forbidden errors should appear

---

### Test 2: Accountant User - Restricted Access

**Objective**: Verify accountant can access limited features

**Steps**:
1. Logout if logged in
2. Navigate to login page
3. Login with accountant credentials
4. Observe navigation sidebar

**Expected Results**:
- ✅ Dashboard menu item visible
- ❌ Company Settings menu item **hidden**
- ❌ User Management menu item **hidden**
- ✅ Reports menu item visible
- ✅ Vouchers menu item visible

**Test Direct URL Access**:
1. While logged in as accountant, manually navigate to `/company` in browser address bar
2. Press Enter

**Expected**: 
- 403 Forbidden page appears
- Error message: "Access denied. Required role: Administrator or Chief Accountant"

3. Navigate to `/users`
4. **Expected**: 403 Forbidden page

5. Navigate to `/reports`
6. **Expected**: Reports page loads (if implemented)

7. Navigate to `/vouchers`
8. **Expected**: Vouchers page loads (if implemented)

---

### Test 3: Chief Accountant User - Management Access

**Objective**: Verify chief accountant can manage users and access company settings

**Steps**:
1. Login as chief accountant
2. Check navigation sidebar

**Expected Results**:
- ✅ Dashboard visible
- ✅ Company Settings visible
- ✅ User Management visible
- ✅ Reports visible
- ✅ Vouchers visible

**Test Access**:
1. Navigate to `/company`
2. **Expected**: Company Settings page loads

3. Navigate to `/users`
4. **Expected**: User Management page loads

---

### Test 4: CFO User - Read-Only Access

**Objective**: Verify CFO has minimal read-only access

**Steps**:
1. Login as CFO user
2. Check navigation sidebar

**Expected Results**:
- ✅ Dashboard visible
- ❌ Company Settings **hidden**
- ❌ User Management **hidden**
- ✅ Reports visible (read-only)
- ❌ Vouchers **hidden**

**Test Direct URL Access**:
1. Navigate to `/company`
2. **Expected**: 403 Forbidden

3. Navigate to `/users`
4. **Expected**: 403 Forbidden

5. Navigate to `/vouchers`
6. **Expected**: 403 Forbidden (or page not implemented)

6. Navigate to `/reports`
7. **Expected**: Reports page loads

---

### Test 5: Unauthenticated Access

**Objective**: Verify unauthenticated users are redirected to login

**Steps**:
1. Clear browser cookies/localStorage or use incognito mode
2. Navigate directly to `http://localhost:5173/` (protected route)

**Expected**:
- Redirect to `/login` page
- Cannot access protected routes

**Test Multiple Routes**:
1. Try `/company` - should redirect to login
2. Try `/users` - should redirect to login
3. Try `/reports` - should redirect to login

---

### Test 6: RoleGuard Component Behavior

**Objective**: Test RoleGuard component renders correctly

**Setup**:
1. Login as accountant (limited permissions)
2. Open browser developer tools (F12)
3. Check console for errors

**Navigate to Protected Routes**:
1. Go to `/company`
2. **Expected**: 
   - 403 Forbidden page displays
   - Error message shown
   - "Go Back" and "Go Home" buttons visible

3. Click "Go Home"
4. **Expected**: Redirects to Dashboard

5. Click browser back button
6. **Expected**: Returns to previous page

---

### Test 7: Navigation Menu Filtering

**Objective**: Verify navigation menu dynamically filters based on role

**Steps for Each Role**:

#### Admin:
1. Login as admin
2. Count visible menu items
3. **Expected**: All 5 items visible (Dashboard, Company, Users, Reports, Vouchers)

#### Accountant:
1. Login as accountant
2. Count visible menu items
3. **Expected**: 3 items visible (Dashboard, Reports, Vouchers)
4. Verify Company Settings and User Management are not in the list

#### Chief Accountant:
1. Login as chief accountant
2. **Expected**: 5 items visible (same as admin)

#### CFO:
1. Login as CFO
2. **Expected**: 2 items visible (Dashboard, Reports)

---

## Browser Console Testing

Open browser developer console (F12) and test role utilities:

```javascript
// These utilities are available through the useRole hook
// To test in console, you'd need to access them through React DevTools
// or check the actual implementation in components

// Example: Check if current user has admin role
// This would be done in a React component using:
// const { hasRole, isAdmin } = useRole()
// isAdmin() // returns true/false
```

## API Testing (Backend RBAC)

**Note**: Backend RBAC enforcement is not yet fully implemented per the context.xml spec. These tests verify frontend behavior.

### Future Backend Tests (When Implemented):

1. **Test API Endpoint Protection**:
   ```bash
   # Test as accountant trying to access admin endpoint
   curl -X GET http://localhost:8080/api/v1/users \
     -H "Authorization: Bearer <accountant_token>"
   # Expected: 403 Forbidden
   ```

2. **Test Role Validation**:
   ```bash
   # Test with invalid/missing role in JWT
   # Expected: 401 Unauthorized
   ```

3. **Test Permission Matrix**:
   - Verify each endpoint respects role requirements
   - Test all CRUD operations per role

## Known Limitations

1. **User State on Refresh**: 
   - User object is stored in React state only
   - On page refresh, user object may be lost (token persists)
   - Dashboard may show "User" instead of actual name on refresh

2. **Backend Enforcement**: 
   - Frontend hides UI but backend doesn't enforce all permissions yet
   - Users could potentially call API endpoints directly
   - Full RBAC implementation requires backend `@PreAuthorize` annotations

3. **Missing Features** (from context.xml):
   - Role change audit logging
   - User invitation system
   - Self-role-change prevention
   - Permission matrix documentation

## Troubleshooting

### Issue: Navigation items not filtering correctly

**Check**:
1. Verify user role is returned in login response
2. Check browser console for errors
3. Verify `useRole` hook is working
4. Check `utils/roles.ts` for role matching logic

### Issue: 403 page shows incorrect message

**Check**:
1. Verify `RoleGuard` component is wrapping the route
2. Check `requiredRoles` prop is set correctly
3. Verify `Forbidden403` component receives correct message

### Issue: Redirect loops

**Check**:
1. Verify authentication state in `useAuth` hook
2. Check `ProtectedLayout` redirect logic
3. Verify token storage/retrieval

## Test Checklist

- [ ] Admin can access all routes
- [ ] Accountant cannot access `/company` or `/users`
- [ ] Chief Accountant can access all management routes
- [ ] CFO can only access Dashboard and Reports
- [ ] Navigation menu filters correctly per role
- [ ] Direct URL access shows 403 for unauthorized routes
- [ ] Unauthenticated users redirected to login
- [ ] RoleGuard component displays correct error messages
- [ ] Dashboard displays user information correctly
- [ ] Login redirects based on company existence

## Next Steps

1. Implement backend RBAC enforcement
2. Add user profile/me endpoint to persist user state
3. Implement remaining RBAC features from context.xml
4. Add comprehensive unit and integration tests
5. Create permission matrix documentation

