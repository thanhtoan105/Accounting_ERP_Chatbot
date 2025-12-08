# Testing Guide: Story 1.3 - User Authentication & Security

Hướng dẫn test toàn bộ flow từ backend đến frontend cho Story 1.3.

**Note:** User registration functionality has been removed. Users must be created by administrators (via database or admin interface). For testing purposes, you can create users directly in the database or use the test helper method `createUser()` in integration tests.

## 🚀 Quick Start với Postman

**Tải Postman Collection để test dễ hơn:**

📁 **File:** `docs/postman/Accounting-Auth-Collection.postman_collection.json`

📖 **Hướng dẫn:** Xem `docs/postman/README.md`

**Lợi ích:**

- ✅ Tự động save tokens sau login
- ✅ Tự động validate responses
- ✅ Auto-handle cookies và token refresh
- ✅ Test scripts tự động cho mọi request

**Import vào Postman:** File → Import → Chọn file collection → Done!

## Prerequisites (Yêu cầu trước khi test)

### 1. Backend Setup

- PostgreSQL database đang chạy (localhost:5432)
- Database `accounting_dev` đã được tạo
- Maven dependencies đã được download
- Port 8080 available cho backend

### 2. Frontend Setup

- Node.js và pnpm/npm đã được cài đặt
- Dependencies đã được install
- Port 5173 available cho frontend (hoặc port khác nếu config khác)

### 3. Environment Variables (Optional - cho email)

```bash
# Resend Email (optional - nếu không có sẽ log warning)
export RESEND_API_KEY="re_xxxxxxxxx"
export RESEND_FROM_EMAIL="noreply@yourdomain.com"
export FRONTEND_URL="http://localhost:5173"

# JWT Secret (optional - có default)
export JWT_SECRET="your-secret-key-change-this-in-production-minimum-256-bits"
```

---

## Part 1: Backend Testing (API Testing)

### Step 1: Khởi động Backend

```bash
cd backend
mvnd spring-boot:run
```

**Kiểm tra:**

- Backend chạy thành công trên port 8080
- Database migrations chạy thành công (users, audit_logs tables được tạo)
- Health endpoint hoạt động: `curl http://localhost:8080/api/v1/health`

### Step 2: Create Test User (Manual Setup)

**Note:** Registration endpoint has been removed. Users must be created by administrators.

**For testing, you can create a user directly in the database:**

```sql
-- Insert user with hashed password (using BCrypt)
-- Password: TestPassword123!
-- BCrypt hash for "TestPassword123!" (strength 12): $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyY5p5G5G5Gq
INSERT INTO users (email, password_hash, full_name, role, company_id, failed_login_count, created_at, updated_at)
VALUES (
  'test@example.com',
  '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyY5p5G5G5Gq', -- Change this to actual hash
  'Test User',
  'USER',
  NULL,
  0,
  NOW(),
  NOW()
);
```

**Or use the integration test helper method** (if running tests):

The `AuthControllerIntegrationTest` class includes a `createUser()` helper method that creates users for testing.

### Step 3: Test Login Endpoint

#### 3.1. Test Login thành công

```bash
# Note: User must be created by admin first (see Step 2)
# Sau đó login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "email": "login@example.com",
    "password": "TestPassword123!",
    "rememberMe": false
  }'
```

**Expected Response (200 OK):**

```json
{
	"data": {
		"accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
		"user": {
			"id": 1,
			"email": "login@example.com",
			"fullName": "Login User",
			"role": "USER",
			"companyId": null
		}
	}
}
```

**Kiểm tra:**

- ✅ Response có accessToken
- ✅ Response có user data
- ✅ Cookie `refreshToken` được set (HttpOnly, Secure)
- ✅ Check cookies.txt hoặc browser dev tools để thấy cookie

#### 3.2. Test Login với remember me

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "email": "login@example.com",
    "password": "TestPassword123!",
    "rememberMe": true
  }'
```

**Kiểm tra:**

- ✅ Refresh token cookie có max-age = 30 days (2592000 seconds)
- ✅ Thay vì default 7 days

#### 3.3. Test Login với invalid credentials

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "login@example.com",
    "password": "WrongPassword123!"
  }'
```

**Expected Response (401 Unauthorized):**

```json
{
	"error": {
		"code": "UNAUTHORIZED",
		"message": "Invalid credentials"
	}
}
```

**Kiểm tra trong database:**

```sql
SELECT email, failed_login_count, locked_until
FROM users
WHERE email = 'login@example.com';
```

- ✅ `failed_login_count` tăng lên
- ✅ Sau 5 lần sai, `locked_until` được set

#### 3.4. Test Account Lockout

```bash
# Thử login sai 5 lần
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{
      "email": "lockout@example.com",
      "password": "WrongPassword123!"
    }'
done

# Sau đó thử login với password đúng
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "lockout@example.com",
    "password": "TestPassword123!"
  }'
```

**Expected Response (423 Locked):**

```json
{
	"error": {
		"code": "ACCOUNT_LOCKED",
		"message": "Account is locked. Please try again later."
	}
}
```

**Kiểm tra audit logs:**

```sql
SELECT action, reason, ip_address, user_agent, created_at
FROM audit_logs
WHERE email = 'lockout@example.com'
ORDER BY created_at DESC;
```

- ✅ Có 5 LOGIN_FAILURE với reason='INVALID_CREDENTIALS'
- ✅ Có 1 LOGIN_FAILURE với reason='ACCOUNT_LOCKED'

### Step 4: Test Refresh Token Endpoint

#### 4.1. Test Refresh token thành công

```bash
# Trước tiên login để có refresh token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "email": "login@example.com",
    "password": "TestPassword123!"
  }'

# Sau đó refresh token
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -c cookies.txt
```

**Expected Response (200 OK):**

```json
{
	"data": {
		"accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
		"user": {
			"id": 1,
			"email": "login@example.com",
			"fullName": "Login User",
			"role": "USER",
			"companyId": null
		}
	}
}
```

**Kiểm tra:**

- ✅ Access token mới được generate
- ✅ Refresh token cookie được rotate (cookie mới được set)
- ✅ Old refresh token không còn valid

#### 4.2. Test Refresh với invalid token

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -b cookies.txt
# Không có refreshToken cookie hoặc cookie invalid
```

**Expected Response (401 Unauthorized):**

```json
{
	"error": {
		"code": "UNAUTHORIZED",
		"message": "Refresh token not found"
	}
}
```

### Step 5: Test Forgot Password Flow

#### 5.1. Test Forgot password

```bash
# Tạo user trước
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "reset@example.com",
    "password": "TestPassword123!",
    "fullName": "Reset User"
  }'

# Request password reset
curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "reset@example.com"
  }'
```

**Expected Response (200 OK):**

```json
{
	"data": {
		"message": "If an account with that email exists, a reset link has been sent."
	}
}
```

**Kiểm tra:**

- ✅ Response luôn là 200 (không reveal email existence)
- ✅ Reset token được tạo trong database
- ✅ Reset token expiry = 30 minutes từ now
- ✅ Email được gửi (nếu có RESEND_API_KEY) hoặc log warning

**Kiểm tra database:**

```sql
SELECT email, reset_token, reset_token_expiry
FROM users
WHERE email = 'reset@example.com';
```

**Kiểm tra audit logs:**

```sql
SELECT action, created_at
FROM audit_logs
WHERE email = 'reset@example.com'
  AND action = 'PASSWORD_RESET_REQUESTED';
```

#### 5.2. Test Reset password

```bash
# Lấy reset token từ database hoặc email
RESET_TOKEN="your-reset-token-here"

curl -X POST http://localhost:8080/api/v1/auth/reset-password \
  -H "Content-Type: application/json" \
  -d "{
    \"token\": \"$RESET_TOKEN\",
    \"password\": \"NewPassword123!\"
  }"
```

**Expected Response (200 OK):**

```json
{
	"data": {
		"message": "Password has been reset successfully"
	}
}
```

**Kiểm tra:**

- ✅ Password được update trong database (hashed)
- ✅ Reset token được invalidated (set null)
- ✅ Có thể login với password mới
- ✅ Không thể dùng lại reset token

**Kiểm tra audit logs:**

```sql
SELECT action, created_at
FROM audit_logs
WHERE email = 'reset@example.com'
  AND action = 'PASSWORD_RESET_COMPLETED';
```

#### 5.3. Test Reset password với expired token

```bash
# Sử dụng token đã expired hoặc invalid
curl -X POST http://localhost:8080/api/v1/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "token": "expired-token",
    "password": "NewPassword123!"
  }'
```

**Expected Response (400 Bad Request):**

```json
{
	"error": {
		"code": "ERROR",
		"message": "Invalid or expired reset token"
	}
}
```

### Step 6: Test Logout Endpoint

```bash
# Login trước
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "email": "login@example.com",
    "password": "TestPassword123!"
  }'

# Logout
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -b cookies.txt \
  -c cookies.txt
```

**Expected Response (204 No Content):**

**Kiểm tra:**

- ✅ Refresh token cookie được cleared (max-age=0)
- ✅ Sau khi logout, không thể refresh token nữa

### Step 7: Test Protected Endpoint với JWT

```bash
# Login để lấy access token
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "email": "login@example.com",
    "password": "TestPassword123!"
  }')

ACCESS_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.accessToken')

# Test protected endpoint (ví dụ: một endpoint khác nếu có)
curl -X GET http://localhost:8080/api/v1/companies \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

**Kiểm tra:**

- ✅ Với valid token: Request thành công
- ✅ Không có token hoặc invalid token: 401 Unauthorized

---

## Part 2: Frontend Testing (UI Testing)

### Step 1: Khởi động Frontend

```bash
cd frontend
pnpm install  # hoặc npm install nếu chưa có dependencies
pnpm dev      # hoặc npm run dev
```

**Kiểm tra:**

- Frontend chạy thành công trên port 5173 (hoặc port khác)
- Không có lỗi compile/console errors

### Step 2: Test Registration Page

#### 2.1. Test UI và Validation

1. **Mở browser:** `http://localhost:5173/register` (hoặc route tương ứng)

2. **Test password policy display:**

   - ✅ Nhập password → thấy checklist password requirements
   - ✅ Checklist cập nhật real-time khi nhập
   - ✅ Checkmarks xanh khi requirement được đáp ứng

3. **Test validation:**

   - Submit form trống → thấy validation errors
   - Nhập invalid email → thấy error "Invalid email address"
   - Nhập weak password → thấy error về password complexity

4. **Test registration thành công:**
   - Nhập: email="newuser@example.com", password="TestPassword123!", fullName="New User"
   - Submit → redirect hoặc hiển thị success message

#### 2.2. Test error handling

1. **Test duplicate email:**

   - Đăng ký email đã tồn tại
   - ✅ Thấy error "Email already exists"

2. **Test network error:**
   - Tắt backend
   - Submit form → ✅ Thấy error message

### Step 3: Test Login Page

#### 3.1. Test UI và Validation

1. **Mở browser:** `http://localhost:5173/login`

2. **Test form validation:**

   - Submit form trống → thấy validation errors
   - Invalid email format → thấy error

3. **Test login thành công:**

   - Nhập: email="login@example.com", password="TestPassword123!"
   - Click "Log in" → ✅ Redirect đến dashboard hoặc success

4. **Test remember me:**
   - ✅ Checkbox "Remember me" hiển thị
   - ✅ Check "Remember me" → refresh token có expiry 30 days

#### 3.2. Test error handling

1. **Test invalid credentials:**

   - Nhập sai password → ✅ Thấy error "Invalid email or password"

2. **Test account locked:**

   - Login sai 5 lần → ✅ Thấy error "Account is locked. Please try again later."

3. **Test network error:**
   - Tắt backend → ✅ Thấy error message

### Step 4: Test Forgot Password Flow

#### 4.1. Test Forgot Password Page

1. **Từ Login page:** Click "Forgot password?" link

2. **Test forgot password:**
   - Nhập email đã đăng ký → Submit
   - ✅ Thấy success message: "If an account with that email exists, a reset link has been sent."
   - ✅ Check email inbox (nếu có RESEND_API_KEY) hoặc check logs

#### 4.2. Test Reset Password Page

1. **Mở reset password page:** `http://localhost:5173/reset-password?token=YOUR_TOKEN`

2. **Test reset password:**

   - Nhập new password và confirm password
   - ✅ Password requirements checklist hiển thị
   - ✅ Validation real-time
   - Submit → ✅ Redirect to login với success message

3. **Test error handling:**
   - Expired token → ✅ Thấy error "Invalid or expired reset token"
   - Invalid token → ✅ Thấy error

### Step 5: Test Session Management

#### 5.1. Test Token Refresh

1. **Login thành công**
2. **Mở Browser DevTools → Network tab**
3. **Đợi access token expire (hoặc force refresh)**
4. **Gọi một API request** → ✅ Tự động refresh token và retry request

#### 5.2. Test Logout

1. **Đã login**
2. **Click logout** → ✅ Redirect to login
3. **Kiểm tra:**
   - ✅ Access token cleared
   - ✅ Refresh token cookie cleared
   - ✅ Không thể gọi protected APIs nữa

---

## Part 3: End-to-End Testing (E2E Flow)

### Test Flow 1: Complete Registration → Login → Logout

1. **Registration:**

   ```bash
   # Backend
   curl -X POST http://localhost:8080/api/v1/auth/register \
     -H "Content-Type: application/json" \
     -d '{
       "email": "e2e@example.com",
       "password": "E2EPassword123!",
       "fullName": "E2E User"
     }'
   ```

   - ✅ Hoặc test qua UI frontend

2. **Login:**

   ```bash
   curl -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -c cookies.txt \
     -d '{
       "email": "e2e@example.com",
       "password": "E2EPassword123!"
     }'
   ```

   - ✅ Hoặc test qua UI frontend

3. **Verify tokens:**

   - ✅ Access token trong response
   - ✅ Refresh token trong cookie
   - ✅ Có thể gọi protected endpoints

4. **Logout:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/auth/logout \
     -b cookies.txt \
     -c cookies.txt
   ```
   - ✅ Tokens cleared
   - ✅ Không thể gọi protected endpoints

### Test Flow 2: Password Reset Complete Flow

1. **Register user**
2. **Request password reset** (forgot-password)
3. **Check email** để lấy reset token
4. **Reset password** với token
5. **Login với password mới** → ✅ Thành công
6. **Thử dùng lại reset token** → ✅ Fail (token đã invalidated)

### Test Flow 3: Account Lockout Flow

1. **Register user**
2. **Login sai password 5 lần**
3. **Check database:** `failed_login_count = 5`, `locked_until` được set
4. **Thử login với password đúng** → ✅ Fail với error "Account locked"
5. **Đợi 5 phút hoặc reset locked_until trong DB**
6. **Login lại** → ✅ Thành công, `failed_login_count` reset về 0

---

## Part 4: Database Verification

### Kiểm tra User Table

```sql
-- Xem tất cả users
SELECT id, email, full_name, role, company_id,
       locked_until, failed_login_count,
       reset_token IS NOT NULL as has_reset_token,
       created_at, updated_at
FROM users;

-- Kiểm tra password được hash (không phải plaintext)
SELECT email, password_hash
FROM users
WHERE email = 'test@example.com';
-- ✅ password_hash phải là bcrypt hash format: $2a$...
```

### Kiểm tra Audit Logs

```sql
-- Xem tất cả audit logs
SELECT id, user_id, email, action, reason,
       ip_address, user_agent, created_at
FROM audit_logs
ORDER BY created_at DESC;

-- Filter theo action
SELECT * FROM audit_logs WHERE action = 'LOGIN_SUCCESS';
SELECT * FROM audit_logs WHERE action = 'LOGIN_FAILURE';
SELECT * FROM audit_logs WHERE action = 'PASSWORD_RESET_REQUESTED';
SELECT * FROM audit_logs WHERE action = 'PASSWORD_RESET_COMPLETED';

-- Kiểm tra IP và User-Agent được log
SELECT action, ip_address, user_agent, created_at
FROM audit_logs
WHERE email = 'test@example.com'
ORDER BY created_at DESC;
```

---

## Part 5: Security Checks

### 5.1. Password Security

- ✅ Password không xuất hiện trong logs
- ✅ Password không xuất hiện trong API responses
- ✅ Password được hash trong database (bcrypt)
- ✅ Password verification không compare plaintext

### 5.2. Token Security

- ✅ Access token có expiry ngắn (30 phút default)
- ✅ Refresh token trong HttpOnly cookie (không accessible từ JS)
- ✅ Refresh token có Secure flag (HTTPS only)
- ✅ Token rotation khi refresh

### 5.3. Cookie Security

```bash
# Kiểm tra cookie headers
curl -v -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"email":"test@example.com","password":"TestPassword123!"}'

# Kiểm tra response headers:
# ✅ Set-Cookie: refreshToken=...; HttpOnly; Secure; Path=/
```

### 5.4. Rate Limiting

- ✅ Account locked sau 5 failed attempts
- ✅ Lockout duration: 5 minutes
- ✅ Failed attempts được log với IP và User-Agent

---

## Part 6: Automated Test Execution

### Run Backend Tests

```bash
cd backend
mvnd test
```

**Kiểm tra test results:**

- ✅ PasswordEncoderTest: Password hashing tests
- ✅ JwtTokenProviderTest: JWT generation/validation tests
- ✅ AuthServiceImplTest: Service logic tests
- ✅ AuthControllerIntegrationTest: API endpoint tests

### Run Frontend Tests

```bash
cd frontend
pnpm test
# hoặc
npm run test
```

**Kiểm tra test results:**

- ✅ Register.test.tsx: Registration form validation
- ✅ Login.test.tsx: Login form và error handling
- ✅ ForgotPassword.test.tsx: Forgot password flow
- ✅ ResetPassword.test.tsx: Reset password validation

---

## Troubleshooting

### Backend không start

**Vấn đề:** Database connection error

```bash
# Kiểm tra PostgreSQL đang chạy
sudo systemctl status postgresql

# Kiểm tra database tồn tại
psql -U accounting -d accounting_dev -c "\dt"
```

**Vấn đề:** Port 8080 đã được sử dụng

```bash
# Thay đổi port trong application.yml
server:
  port: 8081
```

### Frontend không connect được backend

**Vấn đề:** CORS errors

```bash
# Kiểm tra SecurityConfig cho phép CORS
# Hoặc config proxy trong vite.config.ts
```

**Vấn đề:** API calls fail

```bash
# Kiểm tra backend đang chạy
curl http://localhost:8080/api/v1/health

# Kiểm tra network tab trong browser DevTools
```

### Email không được gửi

**Vấn đề:** RESEND_API_KEY chưa được set

```bash
# Check logs - sẽ thấy warning:
# "Resend API key not configured. Email sending will be disabled."

# Set environment variable:
export RESEND_API_KEY="re_xxxxxxxxx"
```

**Kiểm tra:**

- Token vẫn được tạo và lưu trong database
- Có thể test reset password bằng cách lấy token từ DB

---

## Quick Test Checklist

- [ ] Backend starts successfully
- [ ] Database migrations run (users, audit_logs tables)
- [ ] Registration endpoint works
- [ ] Password validation works
- [ ] Login endpoint works
- [ ] JWT tokens generated correctly
- [ ] Refresh token in HttpOnly cookie
- [ ] Token refresh works
- [ ] Account lockout works (5 failed attempts)
- [ ] Password reset flow works (forgot → reset)
- [ ] Email sent (if RESEND_API_KEY configured)
- [ ] Audit logs created for all events
- [ ] Frontend registration page works
- [ ] Frontend login page works
- [ ] Frontend forgot password flow works
- [ ] Frontend reset password page works
- [ ] Token refresh works automatically
- [ ] Logout clears tokens

---

## Next Steps

Sau khi test thành công:

1. Review code và test coverage
2. Run code-review workflow nếu có
3. Deploy to staging environment (nếu có)
4. Test với production-like configuration
