# Postman Collection: Accounting API - Authentication

Postman collection để test các endpoints của Story 1.3: User Authentication & Security.

**Note:** Registration endpoints have been removed. Users must be created by administrators. The "Auth - Register" section in this collection is deprecated and will return 404 if the endpoint no longer exists.

## Cài đặt

### 1. Import Collection vào Postman

1. Mở Postman
2. Click **Import** button
3. Chọn file `Accounting-Auth-Collection.postman_collection.json`
4. Collection sẽ được import với tên: **Accounting API - Authentication (Story 1.3)**

### 2. Tạo Environment (Optional nhưng Recommended)

Tạo một Postman Environment với các variables:

```
baseUrl: http://localhost:8080
testEmail: test@example.com
testPassword: TestPassword123!
```

Hoặc có thể sử dụng Collection Variables (đã được set sẵn trong collection).

## Collection Structure

### 📁 Health Check
- **GET /api/v1/health** - Kiểm tra backend đang chạy

### 📁 Auth - Register (DEPRECATED - REMOVED)
**Note:** Registration functionality has been removed. Users must be created by administrators.
- ~~**Register - Success**~~ - REMOVED
- ~~**Register - Weak Password**~~ - REMOVED
- ~~**Register - Duplicate Email**~~ - REMOVED
- ~~**Register - Invalid Email Format**~~ - REMOVED

### 📁 Auth - Login
- **Login - Success** - Login thành công (auto-save access token và refresh token)
- **Login - With Remember Me** - Login với remember me checked
- **Login - Invalid Credentials** - Test với wrong password
- **Login - Account Lockout** - Test account lockout (chạy 5 lần)

### 📁 Auth - Refresh Token
- **Refresh - Success** - Refresh access token (auto-save new token)
- **Refresh - No Token** - Test refresh không có token

### 📁 Auth - Logout
- **POST /api/v1/auth/logout** - Logout và clear tokens

### 📁 Auth - Forgot Password
- **POST /api/v1/auth/forgot-password** - Request password reset

### 📁 Auth - Reset Password
- **Reset - Success** - Reset password với valid token
- **Reset - Invalid/Expired Token** - Test với invalid token

### 📁 Protected Endpoint Example
- **GET /api/v1/companies** - Example của protected endpoint (cần access token)

## Collection Variables

Collection đã được cấu hình với các variables sau:

| Variable | Mô tả | Auto-set |
|----------|-------|----------|
| `baseUrl` | Backend base URL | ❌ |
| `accessToken` | JWT access token | ✅ (sau login) |
| `refreshToken` | Refresh token (cookie) | ✅ (sau login) |
| `testEmail` | Test email | ❌ |
| `testPassword` | Test password | ❌ |
| `resetToken` | Password reset token | ❌ (manual từ DB/email) |
| `userId` | User ID | ✅ (sau register) |

## Auto-Testing Features

Collection đã được cấu hình với **automatic test scripts**:

✅ **Auto-save tokens:** Login requests tự động save `accessToken` và `refreshToken`  
✅ **Response validation:** Tất cả requests đều có test scripts để validate responses  
✅ **Cookie handling:** Refresh token cookies được tự động handle  
✅ **Token rotation:** Refresh token requests tự động update access token

## Usage Flow

### 1. Quick Test Flow

```
1. Health Check (đảm bảo backend chạy)
2. Auth - Register → Register - Success
3. Auth - Login → Login - Success
4. Protected Endpoint Example (test với token)
5. Auth - Refresh Token → Refresh - Success
6. Auth - Logout
```

### 2. Complete Test Flow

```
1. Health Check
2. Auth - Register:
   - Register - Success
   - Register - Weak Password
   - Register - Duplicate Email (chạy sau Success)
   - Register - Invalid Email Format
3. Auth - Login:
   - Login - Success
   - Login - With Remember Me
   - Login - Invalid Credentials
   - Login - Account Lockout (chạy 5 lần)
4. Auth - Refresh Token:
   - Refresh - Success
   - Refresh - No Token
5. Auth - Forgot Password
6. Auth - Reset Password:
   - Reset - Success (cần token từ DB/email)
   - Reset - Invalid/Expired Token
7. Protected Endpoint Example
8. Auth - Logout
```

### 3. Password Reset Flow

```
1. Auth - Forgot Password (với email đã register)
2. Lấy reset token từ database:
   ```sql
   SELECT reset_token FROM users WHERE email = 'test@example.com';
   ```
3. Set collection variable `resetToken` = token từ DB
4. Auth - Reset Password → Reset - Success
5. Test login với password mới
```

## Test Scripts

Mỗi request đều có test scripts để tự động validate:

- ✅ Status code validation
- ✅ Response structure validation
- ✅ Error code validation
- ✅ Token/cookie validation
- ✅ Auto-save variables

Xem test results trong **Test Results** tab của Postman response.

## Troubleshooting

### Backend không chạy
- Kiểm tra backend đang chạy: `http://localhost:8080/api/v1/health`
- Nếu port khác, update `baseUrl` variable

### Token không được save
- Đảm bảo đã chạy **Login - Success** trước
- Check collection variables trong Postman

### Reset token không có
- Password reset token được tạo trong database sau khi gọi Forgot Password
- Lấy từ DB: `SELECT reset_token FROM users WHERE email = '...'`
- Set vào collection variable `resetToken`

### Cookies không work
- Postman tự động handle cookies
- Check cookies trong **Cookies** tab của Postman
- Nếu cần, manually add cookie: `refreshToken` = value từ response

## Notes

- Tất cả requests đều có test scripts tự động
- Access token tự động được save và sử dụng cho protected endpoints
- Refresh token cookies được tự động handle
- Variables tự động được update sau mỗi request thành công

## Collection File

File: `docs/postman/Accounting-Auth-Collection.postman_collection.json`

Import vào Postman để bắt đầu test!

