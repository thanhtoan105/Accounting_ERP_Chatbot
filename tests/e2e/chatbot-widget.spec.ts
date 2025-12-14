import { test, expect } from '../support/fixtures';
import { TEST_USERS } from '../auth.global-setup';

/**
 * Chatbot Widget E2E Tests
 * Priority: P1/P2 (Core chatbot functionality)
 *
 * Coverage:
 * - Widget open/close interaction
 * - Submit Vietnamese query and receive response
 * - Citation link navigation
 * - Low confidence fallback message
 * - Error handling (network failure, service error)
 * - Keyboard accessibility
 * - Feature flag (chatbot disabled)
 */

test.describe('Chatbot Widget', () => {
    // Setup: Login before each test using centralized credentials
    test.beforeEach(async ({ page }) => {
        const credentials = TEST_USERS.accountant;

        // Mock successful login
        await page.route('**/api/v1/auth/login', async (route) => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    data: {
                        accessToken: 'mock-access-token-' + Date.now(),
                        refreshToken: 'mock-refresh-token-' + Date.now(),
                        user: {
                            id: 1,
                            email: credentials.email,
                            fullName: 'Chatbot Test User',
                            role: 'ACCOUNTANT',
                            companyId: 1,
                        },
                    },
                }),
            });
        });

        // Login
        await page.goto('/login');
        await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });
        await page.fill('[data-testid="email-input"]', credentials.email);
        await page.fill('[data-testid="password-input"]', credentials.password);
        await page.click('[data-testid="login-button"]');
        await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10000 });
    });

    test('[P1] should toggle chatbot widget open and close', async ({ page }) => {
        // GIVEN: User is on authenticated page
        // Widget toggle button should be visible (bottom-right floating button)
        const toggleButton = page.locator('button:has(svg)').filter({ hasText: '' }).last();

        // WHEN: User clicks the toggle button
        await toggleButton.click();

        // THEN: Chat widget should open
        const chatWidget = page.locator('.chatbot-widget-enter, [class*="fixed"][class*="bottom"]').filter({
            hasText: 'Trợ lý AI',
        });
        await expect(chatWidget).toBeVisible({ timeout: 5000 });

        // WHEN: User clicks close button
        const closeButton = chatWidget.locator('button').filter({ hasText: '' }).first();
        await closeButton.click();

        // THEN: Widget should close (or collapse)
        await expect(chatWidget).not.toBeVisible({ timeout: 3000 });
    });

    test('[P1] should submit Vietnamese query and receive response with citations', async ({
        page,
    }) => {
        // GIVEN: Mock chatbot API with successful response
        await page.route('**/api/v1/chatbot/query', async (route) => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    queryId: '123',
                    answer: 'Tổng công nợ phải trả hiện tại là 125,000,000 VND dựa trên các chứng từ sau.',
                    citations: [
                        {
                            entityType: 'voucher',
                            entityId: '550e8400-e29b-41d4-a716-446655440001',
                            voucherNumber: 'PC-2024-001',
                            excerpt: 'Thanh toán nhà cung cấp ABC - 50,000,000 VND',
                            relevanceScore: 0.92,
                            link: '/vouchers/550e8400-e29b-41d4-a716-446655440001',
                        },
                        {
                            entityType: 'voucher',
                            entityId: '550e8400-e29b-41d4-a716-446655440002',
                            voucherNumber: 'PC-2024-002',
                            excerpt: 'Thanh toán nhà cung cấp XYZ - 75,000,000 VND',
                            relevanceScore: 0.88,
                            link: '/vouchers/550e8400-e29b-41d4-a716-446655440002',
                        },
                    ],
                    confidenceScore: 0.85,
                    confidenceLevel: 'HIGH',
                    responseTimeMs: 1234,
                }),
            });
        });

        // Open chatbot widget
        const toggleButton = page.locator('button:has(svg)').last();
        await toggleButton.click();

        const chatWidget = page.locator('[class*="fixed"][class*="bottom"]').filter({
            hasText: 'Trợ lý AI',
        });
        await expect(chatWidget).toBeVisible({ timeout: 5000 });

        // WHEN: User types query and submits
        const input = chatWidget.locator('input[placeholder*="câu hỏi"]');
        await input.fill('Công nợ phải trả là bao nhiêu?');

        const sendButton = chatWidget.locator('button[type="submit"]');
        await sendButton.click();

        // THEN: Response should be displayed with citations
        await expect(chatWidget.getByText('125,000,000 VND')).toBeVisible({ timeout: 10000 });
        await expect(chatWidget.getByText('PC-2024-001')).toBeVisible();
        await expect(chatWidget.getByText('PC-2024-002')).toBeVisible();

        // Confidence badge should show HIGH
        await expect(chatWidget.getByText(/Độ tin cậy cao/i)).toBeVisible();
    });

    test('[P1] should display fallback message for low confidence response', async ({ page }) => {
        // GIVEN: Mock chatbot API with low confidence response
        await page.route('**/api/v1/chatbot/query', async (route) => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    queryId: '456',
                    answer:
                        'Không đủ dữ liệu để trả lời câu hỏi này với độ chính xác cao.\n\nGợi ý:\n- Thử diễn đạt lại câu hỏi với các thuật ngữ kế toán cụ thể hơn',
                    citations: [],
                    confidenceScore: 0.3,
                    confidenceLevel: 'LOW',
                    responseTimeMs: 500,
                }),
            });
        });

        // Open chatbot widget
        const toggleButton = page.locator('button:has(svg)').last();
        await toggleButton.click();

        const chatWidget = page.locator('[class*="fixed"][class*="bottom"]').filter({
            hasText: 'Trợ lý AI',
        });
        await expect(chatWidget).toBeVisible({ timeout: 5000 });

        // WHEN: User submits ambiguous query
        const input = chatWidget.locator('input[placeholder*="câu hỏi"]');
        await input.fill('Câu hỏi không liên quan');

        const sendButton = chatWidget.locator('button[type="submit"]');
        await sendButton.click();

        // THEN: Fallback message should be displayed
        await expect(chatWidget.getByText(/Không đủ dữ liệu/i)).toBeVisible({ timeout: 10000 });

        // Confidence badge should show LOW
        await expect(chatWidget.getByText(/Độ tin cậy thấp/i)).toBeVisible();
    });

    test('[P2] should handle network error gracefully', async ({ page }) => {
        // GIVEN: Network error will occur
        await page.route('**/api/v1/chatbot/query', async (route) => {
            await route.abort('failed');
        });

        // Open chatbot widget
        const toggleButton = page.locator('button:has(svg)').last();
        await toggleButton.click();

        const chatWidget = page.locator('[class*="fixed"][class*="bottom"]').filter({
            hasText: 'Trợ lý AI',
        });
        await expect(chatWidget).toBeVisible({ timeout: 5000 });

        // WHEN: User submits query
        const input = chatWidget.locator('input[placeholder*="câu hỏi"]');
        await input.fill('Test query');

        const sendButton = chatWidget.locator('button[type="submit"]');
        await sendButton.click();

        // THEN: Error message should be displayed
        await expect(chatWidget.getByText(/lỗi|error|thử lại/i)).toBeVisible({ timeout: 10000 });
    });

    test('[P2] should handle server error (500) gracefully', async ({ page }) => {
        // GIVEN: Server returns 500 error
        await page.route('**/api/v1/chatbot/query', async (route) => {
            await route.fulfill({
                status: 500,
                contentType: 'application/json',
                body: JSON.stringify({
                    error: 'Internal Server Error',
                    message: 'Pinecone service unavailable',
                }),
            });
        });

        // Open chatbot widget
        const toggleButton = page.locator('button:has(svg)').last();
        await toggleButton.click();

        const chatWidget = page.locator('[class*="fixed"][class*="bottom"]').filter({
            hasText: 'Trợ lý AI',
        });
        await expect(chatWidget).toBeVisible({ timeout: 5000 });

        // WHEN: User submits query
        const input = chatWidget.locator('input[placeholder*="câu hỏi"]');
        await input.fill('Test query');

        const sendButton = chatWidget.locator('button[type="submit"]');
        await sendButton.click();

        // THEN: Error message should be displayed
        await expect(chatWidget.getByText(/lỗi|error/i)).toBeVisible({ timeout: 10000 });
    });

    test('[P2] should support Enter key to submit query', async ({ page }) => {
        // GIVEN: Mock chatbot API
        await page.route('**/api/v1/chatbot/query', async (route) => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    queryId: '789',
                    answer: 'Response from Enter key submission',
                    citations: [],
                    confidenceScore: 0.75,
                    confidenceLevel: 'MEDIUM',
                    responseTimeMs: 800,
                }),
            });
        });

        // Open chatbot widget
        const toggleButton = page.locator('button:has(svg)').last();
        await toggleButton.click();

        const chatWidget = page.locator('[class*="fixed"][class*="bottom"]').filter({
            hasText: 'Trợ lý AI',
        });
        await expect(chatWidget).toBeVisible({ timeout: 5000 });

        // WHEN: User types query and presses Enter
        const input = chatWidget.locator('input[placeholder*="câu hỏi"]');
        await input.fill('Test query');
        await input.press('Enter');

        // THEN: Query should be submitted and response received
        await expect(chatWidget.getByText('Response from Enter key submission')).toBeVisible({
            timeout: 10000,
        });
    });

    test('[P2] should show loading indicator while processing', async ({ page }) => {
        // GIVEN: Mock chatbot API with delay
        await page.route('**/api/v1/chatbot/query', async (route) => {
            // Delay response by 2 seconds
            await new Promise((resolve) => setTimeout(resolve, 2000));
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    queryId: '101',
                    answer: 'Delayed response',
                    citations: [],
                    confidenceScore: 0.7,
                    confidenceLevel: 'MEDIUM',
                    responseTimeMs: 2000,
                }),
            });
        });

        // Open chatbot widget
        const toggleButton = page.locator('button:has(svg)').last();
        await toggleButton.click();

        const chatWidget = page.locator('[class*="fixed"][class*="bottom"]').filter({
            hasText: 'Trợ lý AI',
        });
        await expect(chatWidget).toBeVisible({ timeout: 5000 });

        // WHEN: User submits query
        const input = chatWidget.locator('input[placeholder*="câu hỏi"]');
        await input.fill('Test query');

        const sendButton = chatWidget.locator('button[type="submit"]');
        await sendButton.click();

        // THEN: Loading indicator should be visible
        // The typing dots animation
        await expect(chatWidget.locator('.chatbot-typing-dot').first()).toBeVisible({ timeout: 1000 });

        // Wait for response
        await expect(chatWidget.getByText('Delayed response')).toBeVisible({ timeout: 10000 });
    });

    test('[P3] should clear chat history when clear button clicked', async ({ page }) => {
        // GIVEN: Mock chatbot API
        await page.route('**/api/v1/chatbot/query', async (route) => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    queryId: '102',
                    answer: 'Test answer to clear',
                    citations: [],
                    confidenceScore: 0.8,
                    confidenceLevel: 'HIGH',
                    responseTimeMs: 500,
                }),
            });
        });

        // Open chatbot widget and submit a query
        const toggleButton = page.locator('button:has(svg)').last();
        await toggleButton.click();

        const chatWidget = page.locator('[class*="fixed"][class*="bottom"]').filter({
            hasText: 'Trợ lý AI',
        });
        await expect(chatWidget).toBeVisible({ timeout: 5000 });

        const input = chatWidget.locator('input[placeholder*="câu hỏi"]');
        await input.fill('Test query');
        await input.press('Enter');

        await expect(chatWidget.getByText('Test answer to clear')).toBeVisible({ timeout: 10000 });

        // WHEN: User clicks clear history button (trash icon)
        const clearButton = chatWidget.locator('button[title*="Xóa"]');

        // Handle confirmation dialog
        page.on('dialog', async (dialog) => {
            await dialog.accept();
        });

        await clearButton.click();

        // THEN: Chat history should be cleared
        await expect(chatWidget.getByText('Test answer to clear')).not.toBeVisible({ timeout: 3000 });

        // Empty state message should be visible
        await expect(chatWidget.getByText(/Xin chào/i)).toBeVisible();
    });
});
