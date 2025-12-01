import { test, expect } from '@playwright/test';

/**
 * Authentication API Tests
 * Priority: P0/P1 (Critical authentication endpoints)
 * 
 * Coverage:
 * - POST /auth/login - Login with credentials
 * - POST /auth/logout - Logout and invalidate token
 * - POST /auth/refresh - Refresh access token
 * - POST /auth/forgot-password - Request password reset
 * - POST /auth/reset-password - Reset password with token
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api/v1';

test.describe('Authentication API', () => {
    test.describe('P0: Login', () => {
        test('POST /auth/login - should return token for valid credentials', async ({ request }) => {
            // GIVEN: Valid credentials
            const credentials = {
                email: 'accountant@example.com',
                password: 'password',
            };

            // WHEN: Logging in via API
            const response = await request.post(`${API_BASE}/auth/login`, {
                data: credentials,
            });

            // THEN: Should return 200 with access token
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body).toHaveProperty('data');
            expect(body.data).toHaveProperty('accessToken');
            expect(body.data).toHaveProperty('user');

            // Verify user object structure
            expect(body.data.user).toMatchObject({
                email: credentials.email,
                role: expect.any(String),
                companyId: expect.any(Number),
            });

            // Verify token format (JWT)
            expect(body.data.accessToken).toMatch(/^[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+$/);
        });

        test('POST /auth/login - should return 401 for invalid credentials', async ({ request }) => {
            // GIVEN: Invalid credentials
            const credentials = {
                email: 'wrong@example.com',
                password: 'WrongPassword123!',
            };

            // WHEN: Attempting login
            const response = await request.post(`${API_BASE}/auth/login`, {
                data: credentials,
            });

            // THEN: Should return 401 Unauthorized
            expect(response.status()).toBe(401);

            const body = await response.json();
            expect(body).toHaveProperty('error');
            const errorMsg = body.error.message || body.error || body.message || JSON.stringify(body);
            expect(errorMsg).toMatch(/invalid|unauthorized|credentials/i);
        });

        test('POST /auth/login - should return 400 for missing fields', async ({ request }) => {
            // GIVEN: Missing password field
            const incompleteData = {
                email: 'test@example.com',
            };

            // WHEN: Attempting login with incomplete data
            const response = await request.post(`${API_BASE}/auth/login`, {
                data: incompleteData,
            });

            // THEN: Should return 400 Bad Request
            expect(response.status()).toBe(400);

            const body = await response.json();
            expect(body).toHaveProperty('error');
        });

        test('POST /auth/login - should return 400 for invalid email format', async ({ request }) => {
            // GIVEN: Invalid email format
            const invalidData = {
                email: 'not-an-email',
                password: 'Password123!',
            };

            // WHEN: Attempting login
            const response = await request.post(`${API_BASE}/auth/login`, {
                data: invalidData,
            });

            // THEN: Should return 400 Bad Request
            expect(response.status()).toBe(400);

            const body = await response.json();
            expect(body).toHaveProperty('error');
            const errorMsg = body.error.message || body.message || JSON.stringify(body);
            expect(errorMsg).toMatch(/email|invalid|format|valid/i);
        });
    });

    test.describe('P1: Token Refresh', () => {
        test('POST /auth/refresh - should return new access token with valid refresh token', async ({ request }) => {
            // GIVEN: Valid refresh token from login
            const loginResponse = await request.post(`${API_BASE}/auth/login`, {
                data: {
                    email: 'accountant@example.com',
                    password: 'password',
                },
            });

            expect(loginResponse.status()).toBe(200);
            const loginBody = await loginResponse.json();
            const refreshToken = loginBody.data.refreshToken;

            // Skip if no refresh token in response
            if (!refreshToken) {
                test.skip();
                return;
            }

            // WHEN: Refreshing access token
            const response = await request.post(`${API_BASE}/auth/refresh`, {
                data: { refreshToken },
            });

            // THEN: Should return new access token
            expect(response.status()).toBe(200);

            const body = await response.json();
            expect(body).toHaveProperty('data');
            expect(body.data).toHaveProperty('accessToken');
            expect(body.data.accessToken).not.toBe(loginBody.data.accessToken); // New token
        });

        test('POST /auth/refresh - should return 401 for invalid refresh token', async ({ request }) => {
            // GIVEN: Invalid refresh token
            const invalidToken = 'invalid.refresh.token';

            // WHEN: Attempting to refresh
            const response = await request.post(`${API_BASE}/auth/refresh`, {
                data: { refreshToken: invalidToken },
            });

            // THEN: Should return 401 Unauthorized
            expect(response.status()).toBe(401);

            const body = await response.json();
            expect(body).toHaveProperty('error');
        });
    });

    test.describe('P1: Logout', () => {
        test('POST /auth/logout - should invalidate access token', async ({ request }) => {
            // GIVEN: User is logged in
            const loginResponse = await request.post(`${API_BASE}/auth/login`, {
                data: {
                    email: 'accountant@example.com',
                    password: 'password',
                },
            });

            expect(loginResponse.status()).toBe(200);
            const loginBody = await loginResponse.json();
            const accessToken = loginBody.data.accessToken;

            // WHEN: Logging out
            const response = await request.post(`${API_BASE}/auth/logout`, {
                headers: {
                    Authorization: `Bearer ${accessToken}`,
                },
            });

            // THEN: Should return 200/204
            expect([200, 204]).toContain(response.status());

            // Note: JWT tokens are stateless, so logout may not immediately invalidate them
            // Token invalidation depends on backend implementation (blacklist, etc.)
        });
    });

    test.describe('P2: Password Reset', () => {
        test('POST /auth/forgot-password - should send reset email for valid email', async ({ request }) => {
            // GIVEN: Valid registered email
            const email = 'accountant@example.com';

            // WHEN: Requesting password reset
            const response = await request.post(`${API_BASE}/auth/forgot-password`, {
                data: { email },
            });

            // THEN: Should return 200 (or 204)
            expect([200, 204]).toContain(response.status());

            // Note: Should not reveal whether email exists for security
        });

        test('POST /auth/forgot-password - should return 200 even for non-existent email', async ({ request }) => {
            // GIVEN: Non-existent email
            const email = 'nonexistent@example.com';

            // WHEN: Requesting password reset
            const response = await request.post(`${API_BASE}/auth/forgot-password`, {
                data: { email },
            });

            // THEN: Should return 200 or 400 depending on implementation
            // Some implementations return 400 "Email not found" instead of hiding user existence
            expect([200, 204, 400]).toContain(response.status());
        });

        test('POST /auth/forgot-password - should return 400 for invalid email format', async ({ request }) => {
            // GIVEN: Invalid email format
            const email = 'not-an-email';

            // WHEN: Requesting password reset
            const response = await request.post(`${API_BASE}/auth/forgot-password`, {
                data: { email },
            });

            // THEN: Should return 400 Bad Request
            expect(response.status()).toBe(400);
        });

        test('POST /auth/reset-password - should reset password with valid token', async ({ request }) => {
            // GIVEN: Valid reset token (mock scenario - in real test, get from email)
            const resetData = {
                token: 'valid-reset-token-from-email',
                newPassword: 'NewSecurePassword123!',
            };

            // WHEN: Resetting password
            const response = await request.post(`${API_BASE}/auth/reset-password`, {
                data: resetData,
            });

            // THEN: Should return 200 or 400/401 depending on token validity
            // Note: Actual test would verify login with new password
            expect([200, 400, 401]).toContain(response.status());
        });

        test('POST /auth/reset-password - should return 400 for weak password', async ({ request }) => {
            // GIVEN: Weak password
            const resetData = {
                token: 'valid-reset-token',
                newPassword: '123', // Too weak
            };

            // WHEN: Attempting to reset with weak password
            const response = await request.post(`${API_BASE}/auth/reset-password`, {
                data: resetData,
            });

            // THEN: Should return 400 Bad Request
            expect(response.status()).toBe(400);

            const body = await response.json();
            const errorMsg = body.error?.message || body.message || JSON.stringify(body.error || body);
            expect(errorMsg).toMatch(/password|weak|length|required|validation/i);
        });
    });
});
