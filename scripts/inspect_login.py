#!/usr/bin/env python3
"""Inspect the login page to understand why tests are failing."""
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    
    print("Navigating to login page...")
    page.goto('http://localhost:5173/login')
    
    print("Waiting for network idle...")
    page.wait_for_load_state('networkidle')
    
    print("Taking screenshot...")
    page.screenshot(path='/tmp/login_inspect.png', full_page=True)
    
    print("Page title:", page.title())
    print("Page URL:", page.url())
    
    # Check for email input
    email_inputs = page.locator('[data-testid="email-input"]').all()
    print(f"Found {len(email_inputs)} email inputs with data-testid")
    
    # Check for any input elements
    all_inputs = page.locator('input').all()
    print(f"Found {len(all_inputs)} total input elements")
    
    # List all inputs with their attributes
    for i, inp in enumerate(all_inputs):
        try:
            attrs = {
                'type': inp.get_attribute('type'),
                'name': inp.get_attribute('name'),
                'id': inp.get_attribute('id'),
                'data-testid': inp.get_attribute('data-testid'),
            }
            print(f"  Input {i}: {attrs}")
        except:
            pass
    
    # Check console errors
    print("\nChecking for errors in page content...")
    content = page.content()
    if 'error' in content.lower():
        print("Page may contain error messages")
    
    browser.close()
    print("\nScreenshot saved to /tmp/login_inspect.png")
