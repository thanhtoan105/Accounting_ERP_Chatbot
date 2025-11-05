# Hướng dẫn Test Forgot Password với Resend Email

## 📋 Setup Resend API Key

### Bước 1: Lấy Resend API Key
1. Đăng ký tài khoản tại: https://resend.com
2. Vào Dashboard → API Keys
3. Tạo API Key mới hoặc dùng key có sẵn

### Bước 2: Set Environment Variables
```bash
# Set trong terminal trước khi start backend
export RESEND_API_KEY="re_your_api_key_here"
export RESEND_FROM_EMAIL="onboarding@resend.dev"  # hoặc email đã verified
export FRONTEND_URL="http://localhost:5173"

# Sau đó restart backend
cd backend
mvn spring-boot:run
```

### Bước 3: Verify Configuration
Backend sẽ log:
- ✅ "Password reset email sent successfully..." → API key OK
- ⚠️ "Resend API key not configured..." → Chưa set API key (reset token vẫn được tạo và log ra)

## 🧪 Test Flow

### Option 1: Test với API Key (Email thật)
1. **Đăng ký user:**
   - Mở: http://localhost:5173/register
   - Đăng ký với email thật: `your-email@example.com`
   - Password: `TestPassword123!`

2. **Test forgot password:**
   - Mở: http://localhost:5173/forgot-password
   - Nhập email đã đăng ký
   - Click "SEND RESET LINK"
   - Kiểm tra email inbox để nhận reset link

3. **Reset password:**
   - Click link trong email hoặc copy token
   - Mở: http://localhost:5173/reset-password?token=YOUR_TOKEN
   - Nhập password mới
   - Submit → Login với password mới

### Option 2: Test không có API Key (Lấy token từ DB)
1. **Đăng ký user:**
   - Mở: http://localhost:5173/register
   - Đăng ký với bất kỳ email nào

2. **Request reset password:**
   - Mở: http://localhost:5173/forgot-password
   - Nhập email đã đăng ký
   - Click "SEND RESET LINK"
   - Backend sẽ log reset token (vì không có API key)

3. **Lấy reset token từ database:**
   ```sql
   SELECT email, reset_token, reset_token_expiry 
   FROM users 
   WHERE email = 'your-email@example.com';
   ```

4. **Test reset password:**
   - Mở: http://localhost:5173/reset-password?token=TOKEN_FROM_DB
   - Nhập password mới
   - Submit → Login với password mới

## 🔍 Kiểm tra Backend Logs

Sau khi request forgot-password, kiểm tra backend logs:

### Nếu có API Key:
```
INFO  - Password reset email sent successfully to user@example.com with ID: abc123
```

### Nếu không có API Key:
```
WARN  - Resend API key not configured. Email sending will be disabled.
WARN  - Email sending skipped - Resend API key not configured. Reset token for user@example.com: abc-123-def-456
```

## 📊 Kiểm tra Database

```sql
-- Kiểm tra reset token đã được tạo
SELECT id, email, reset_token, reset_token_expiry, 
       (reset_token_expiry > NOW()) as is_valid
FROM users 
WHERE email = 'your-email@example.com';

-- Kiểm tra audit logs
SELECT action, created_at, ip_address 
FROM audit_logs 
WHERE email = 'your-email@example.com' 
  AND action = 'PASSWORD_RESET_REQUESTED'
ORDER BY created_at DESC;
```

## ✅ Expected Behavior

1. **Frontend:**
   - Hiển thị success message: "If an account with that email exists, a reset link has been sent."
   - Redirect về login sau 3 giây

2. **Backend:**
   - Tạo reset token (UUID)
   - Set expiry = 30 minutes
   - Gửi email (nếu có API key)
   - Log audit event: `PASSWORD_RESET_REQUESTED`

3. **Email (nếu có API key):**
   - Subject: "Reset Your Password"
   - Contains: Reset link với format: `http://localhost:5173/reset-password?token=...`
   - Expiry: 30 minutes

## 🐛 Troubleshooting

### Email không được gửi
- Kiểm tra `RESEND_API_KEY` đã được set chưa
- Kiểm tra backend logs để xem error
- Verify email domain trong Resend dashboard

### Reset token không work
- Kiểm tra token không expired: `reset_token_expiry > NOW()`
- Kiểm tra token format đúng không
- Token chỉ dùng được 1 lần (sẽ bị clear sau reset)

### Frontend không nhận được response
- Kiểm tra network tab trong browser DevTools
- Kiểm tra backend đang chạy trên port 8080
- Kiểm tra CORS configuration

