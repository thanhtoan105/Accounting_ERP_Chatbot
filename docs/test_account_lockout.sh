#!/bin/bash

# Test Account Lockout Flow
# This script tests the account lockout mechanism

EMAIL="lockout-test@example.com"
PASSWORD="TestPassword123!"
WRONG_PASSWORD="WrongPassword123!"

echo "=== Account Lockout Test ==="
echo "Email: $EMAIL"
echo ""

# Step 1: Register user (if not exists)
echo "1. Registering user (if needed)..."
REGISTER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$EMAIL\",
    \"password\": \"$PASSWORD\",
    \"fullName\": \"Lockout Test User\"
  }")
echo "$REGISTER_RESPONSE" | jq '.' 2>/dev/null || echo "$REGISTER_RESPONSE"
echo ""

# Step 2: Try wrong password 4 times (should NOT lock)
echo "2. Attempting wrong password 4 times (should NOT lock account)..."
for i in {1..4}; do
  echo "Attempt $i:"
  RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d "{
      \"email\": \"$EMAIL\",
      \"password\": \"$WRONG_PASSWORD\"
    }")
  STATUS=$(echo "$RESPONSE" | jq -r '.error.code // "SUCCESS"' 2>/dev/null || echo "ERROR")
  echo "  Status: $STATUS"
  
  # Check database
  echo "  Checking database..."
  psql -U accounting -d accounting_dev -c "SELECT email, failed_login_count, locked_until FROM users WHERE email = '$EMAIL';" 2>/dev/null | grep -A 2 "$EMAIL" || echo "  (Could not check DB)"
  echo ""
done

# Step 3: Try wrong password 5th time (should lock account)
echo "3. Attempting wrong password 5th time (should LOCK account)..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$EMAIL\",
    \"password\": \"$WRONG_PASSWORD\"
  }")
STATUS=$(echo "$RESPONSE" | jq -r '.error.code // "SUCCESS"' 2>/dev/null || echo "ERROR")
echo "Status: $STATUS"
echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
echo ""

# Check database after 5th attempt
echo "Checking database after 5th attempt..."
psql -U accounting -d accounting_dev -c "SELECT email, failed_login_count, locked_until FROM users WHERE email = '$EMAIL';" 2>/dev/null | grep -A 2 "$EMAIL" || echo "(Could not check DB)"
echo ""

# Step 4: Try wrong password 6th time (should return 423 Locked)
echo "4. Attempting wrong password 6th time (should return 423 LOCKED)..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$EMAIL\",
    \"password\": \"$WRONG_PASSWORD\"
  }")
STATUS=$(echo "$RESPONSE" | jq -r '.error.code // "SUCCESS"' 2>/dev/null || echo "ERROR")
echo "Status: $STATUS"
echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
echo ""

# Step 5: Try correct password (should still return 423 Locked)
echo "5. Attempting correct password while locked (should return 423 LOCKED)..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$EMAIL\",
    \"password\": \"$PASSWORD\"
  }")
STATUS=$(echo "$RESPONSE" | jq -r '.error.code // "SUCCESS"' 2>/dev/null || echo "ERROR")
echo "Status: $STATUS"
echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
echo ""

echo "=== Test Complete ==="
echo "Expected:"
echo "  - Attempts 1-4: 401 UNAUTHORIZED (not locked)"
echo "  - Attempt 5: 401 UNAUTHORIZED (account gets locked after this)"
echo "  - Attempt 6+: 423 LOCKED (account is locked)"

