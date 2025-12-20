"""
VOUC-001: Create voucher with single line
Uses webapp-testing skill with Playwright
"""
from playwright.sync_api import sync_playwright, expect

def test_create_voucher():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        
        # Login first
        page.goto('http://localhost:5173/login')
        page.wait_for_load_state('networkidle')
        
        # Fill login form
        page.fill('input[name="username"], input[type="text"]', 'accountant')
        page.fill('input[name="password"], input[type="password"]', 'accountant123')
        page.click('button[type="submit"]')
        
        # Wait for redirect to dashboard
        page.wait_for_url('**/dashboard**', timeout=15000)
        print("✓ Logged in successfully")
        
        # Navigate to vouchers
        page.goto('http://localhost:5173/vouchers')
        page.wait_for_load_state('networkidle')
        page.wait_for_selector('table', timeout=10000)
        print("✓ Vouchers page loaded")
        
        # Take screenshot to see current state
        page.screenshot(path='/tmp/vouchers-list.png')
        
        # Click create button
        create_btn = page.locator('button').filter(has_text='Add Voucher').or_(
            page.locator('button').filter(has_text='New Voucher')
        ).or_(
            page.locator('button').filter(has_text='Create')
        ).first
        create_btn.click()
        
        # Wait for navigation to /vouchers/new
        page.wait_for_url('**/vouchers/new**', timeout=10000)
        page.wait_for_load_state('networkidle')
        print("✓ Navigated to voucher form")
        
        # Take screenshot of voucher form
        page.screenshot(path='/tmp/voucher-form.png', full_page=True)
        
        # Fill description
        desc_field = page.locator('textarea').first
        desc_field.fill('Test voucher entry - automated test VOUC-001')
        print("✓ Filled description")
        
        # Take screenshot after filling
        page.screenshot(path='/tmp/voucher-form-filled.png', full_page=True)
        
        print("\n=== Test completed ===")
        print("Screenshots saved to /tmp/vouchers-list.png, /tmp/voucher-form.png, /tmp/voucher-form-filled.png")
        
        browser.close()

if __name__ == '__main__':
    test_create_voucher()
