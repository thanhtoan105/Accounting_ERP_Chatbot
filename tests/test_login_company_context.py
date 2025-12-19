"""Test login flow and company context header fix."""
from playwright.sync_api import sync_playwright

def test_login_and_company_pages():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        
        # Enable console logging for debugging
        page.on("console", lambda msg: print(f"[BROWSER] {msg.type}: {msg.text}"))
        
        # Navigate to login page
        print("1. Navigating to login page...")
        page.goto("http://localhost:5173/login")
        page.wait_for_load_state("networkidle")
        
        # Take screenshot of login page
        page.screenshot(path="/tmp/01_login_page.png")
        print("   Screenshot saved: /tmp/01_login_page.png")
        
        # Fill login form
        print("2. Filling login form...")
        page.fill('[data-testid="email-input"]', "user1@demo-01.demo.local")
        page.fill('[data-testid="password-input"]', "password")
        
        # Take screenshot before submit
        page.screenshot(path="/tmp/02_login_filled.png")
        print("   Screenshot saved: /tmp/02_login_filled.png")
        
        # Click login button
        print("3. Clicking login button...")
        page.click('[data-testid="login-button"]')
        
        # Wait for navigation after login
        page.wait_for_timeout(2000)  # Wait for success toast and redirect
        page.wait_for_load_state("networkidle")
        
        # Take screenshot after login
        page.screenshot(path="/tmp/03_after_login.png")
        print(f"   Current URL: {page.url}")
        print("   Screenshot saved: /tmp/03_after_login.png")
        
        # Check if we're on dashboard/home page
        if "/login" not in page.url:
            print("✅ Login successful - redirected from login page")
        else:
            print("❌ Still on login page - login may have failed")
            browser.close()
            return False
        
        # Test navigating to UserManagement page
        print("4. Navigating to UserManagement page...")
        page.goto("http://localhost:5173/admin/users")
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(1000)
        
        page.screenshot(path="/tmp/04_user_management.png")
        print(f"   Current URL: {page.url}")
        print("   Screenshot saved: /tmp/04_user_management.png")
        
        # Check for error messages
        content = page.content()
        if "Missing company context" in content or "X-Company-Id header required" in content:
            print("❌ ERROR: Missing company context error still appears!")
            browser.close()
            return False
        else:
            print("✅ No 'Missing company context' error on UserManagement page")
        
        # Test VoucherList page
        print("5. Navigating to VoucherList page...")
        page.goto("http://localhost:5173/vouchers")
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(1000)
        
        page.screenshot(path="/tmp/05_vouchers.png")
        print(f"   Current URL: {page.url}")
        print("   Screenshot saved: /tmp/05_vouchers.png")
        
        content = page.content()
        if "Missing company context" in content or "X-Company-Id header required" in content:
            print("❌ ERROR: Missing company context error on VoucherList page!")
            browser.close()
            return False
        else:
            print("✅ No 'Missing company context' error on VoucherList page")
        
        # Test Customers page
        print("6. Navigating to Customers page...")
        page.goto("http://localhost:5173/customers")
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(1000)
        
        page.screenshot(path="/tmp/06_customers.png")
        print(f"   Current URL: {page.url}")
        print("   Screenshot saved: /tmp/06_customers.png")
        
        content = page.content()
        if "Missing company context" in content or "X-Company-Id header required" in content:
            print("❌ ERROR: Missing company context error on Customers page!")
            browser.close()
            return False
        else:
            print("✅ No 'Missing company context' error on Customers page")
        
        print("\n" + "="*50)
        print("✅ ALL TESTS PASSED - Company context fix is working!")
        print("="*50)
        
        browser.close()
        return True

if __name__ == "__main__":
    test_login_and_company_pages()
