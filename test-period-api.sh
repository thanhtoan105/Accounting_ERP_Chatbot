#!/bin/bash

# Test script to diagnose period API issue

echo "=== Testing Period API Endpoints ==="
echo ""

# First, login to get JWT token
echo "1. Logging in to get JWT token..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@example.com",
    "password": "password"
  }')

echo "Login response: $LOGIN_RESPONSE"
echo ""

# Extract access token (using jq if available, otherwise manual parsing)
if command -v jq &> /dev/null; then
  ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken // .data.accessToken // empty')
else
  # Fallback: simple grep/sed parsing
  ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
fi

if [ -z "$ACCESS_TOKEN" ]; then
  echo "❌ Failed to get access token. Login failed or invalid credentials."
  echo "Response was: $LOGIN_RESPONSE"
  exit 1
fi

echo "✅ Got access token: ${ACCESS_TOKEN:0:20}..."
echo ""

# Test /api/v1/periods/open
echo "2. Testing GET /api/v1/periods/open..."
OPEN_PERIODS_RESPONSE=$(curl -s -X GET http://localhost:8080/api/v1/periods/open \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json")

echo "Open periods response:"
echo "$OPEN_PERIODS_RESPONSE" | jq '.' 2>/dev/null || echo "$OPEN_PERIODS_RESPONSE"
echo ""

# Test /api/v1/periods/current
echo "3. Testing GET /api/v1/periods/current..."
CURRENT_PERIOD_RESPONSE=$(curl -s -X GET http://localhost:8080/api/v1/periods/current \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json")

echo "Current period response:"
echo "$CURRENT_PERIOD_RESPONSE" | jq '.' 2>/dev/null || echo "$CURRENT_PERIOD_RESPONSE"
echo ""

# Check database directly
echo "4. Checking database for periods..."
echo "Run this SQL query:"
echo "SELECT COUNT(*) as period_count FROM accounting_periods WHERE company_id = 1;"
echo ""

echo "=== Test Complete ==="
