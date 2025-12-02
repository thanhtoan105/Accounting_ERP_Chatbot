#!/bin/bash

# =============================================================================
# Chatbot Setup Verification Script
# =============================================================================
# This script verifies that all external services are configured correctly
# for the AI Chatbot (Story 9.0)
#
# Usage: ./scripts/verify-chatbot-setup.sh
# =============================================================================

set -e  # Exit on error

echo "========================================="
echo "Chatbot Setup Verification"
echo "========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Load environment variables
if [ -f .env ]; then
    echo "✓ Loading environment variables from .env"
    export $(cat .env | grep -v '^#' | xargs)
else
    echo -e "${RED}✗ .env file not found${NC}"
    echo "  Please copy .env.example to .env and fill in your credentials"
    exit 1
fi

echo ""
echo "========================================="
echo "1. Verifying Environment Variables"
echo "========================================="

# Function to check environment variable
check_env_var() {
    local var_name=$1
    local var_value=${!var_name}

    if [ -z "$var_value" ]; then
        echo -e "${RED}✗ $var_name is not set${NC}"
        return 1
    elif [[ "$var_value" == *"your-"* ]] || [[ "$var_value" == *"XXXXXX"* ]]; then
        echo -e "${YELLOW}⚠ $var_name has placeholder value${NC}"
        return 1
    else
        echo -e "${GREEN}✓ $var_name is set${NC}"
        return 0
    fi
}

# Check required variables
echo ""
echo "Checking Pinecone configuration..."
check_env_var "PINECONE_API_KEY"
check_env_var "PINECONE_ENVIRONMENT"
check_env_var "PINECONE_INDEX_NAME"

echo ""
echo "Checking Azure OpenAI configuration..."
check_env_var "AZURE_OPENAI_API_KEY"
check_env_var "AZURE_OPENAI_ENDPOINT"
check_env_var "AZURE_OPENAI_EMBEDDING_DEPLOYMENT_NAME"
check_env_var "AZURE_OPENAI_CHAT_DEPLOYMENT_NAME"

echo ""
echo "Checking n8n webhook configuration..."
check_env_var "N8N_WEBHOOK_URL"
check_env_var "N8N_WEBHOOK_SECRET"

echo ""
echo "========================================="
echo "2. Testing Pinecone Connection"
echo "========================================="
echo ""

# Test Pinecone connection
if command -v curl &> /dev/null; then
    echo "Testing Pinecone API connectivity..."

    PINECONE_RESPONSE=$(curl -s -w "\n%{http_code}" \
        -H "Api-Key: $PINECONE_API_KEY" \
        "https://controller.$PINECONE_ENVIRONMENT.pinecone.io/databases")

    HTTP_CODE=$(echo "$PINECONE_RESPONSE" | tail -n1)

    if [ "$HTTP_CODE" = "200" ]; then
        echo -e "${GREEN}✓ Pinecone API connection successful${NC}"

        # Check if index exists
        if echo "$PINECONE_RESPONSE" | grep -q "$PINECONE_INDEX_NAME"; then
            echo -e "${GREEN}✓ Index '$PINECONE_INDEX_NAME' exists${NC}"
        else
            echo -e "${YELLOW}⚠ Index '$PINECONE_INDEX_NAME' not found${NC}"
            echo "  Please create the index in Pinecone console"
        fi
    elif [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
        echo -e "${RED}✗ Pinecone authentication failed (HTTP $HTTP_CODE)${NC}"
        echo "  Check your PINECONE_API_KEY"
    else
        echo -e "${RED}✗ Pinecone API request failed (HTTP $HTTP_CODE)${NC}"
    fi
else
    echo -e "${YELLOW}⚠ curl not found, skipping connectivity tests${NC}"
fi

echo ""
echo "========================================="
echo "3. Testing Azure OpenAI Connection"
echo "========================================="
echo ""

if command -v curl &> /dev/null; then
    echo "Testing Azure OpenAI API connectivity..."

    # Remove trailing slash from endpoint
    AZURE_ENDPOINT="${AZURE_OPENAI_ENDPOINT%/}"

    AZURE_RESPONSE=$(curl -s -w "\n%{http_code}" \
        -H "api-key: $AZURE_OPENAI_API_KEY" \
        "$AZURE_ENDPOINT/openai/deployments?api-version=2024-02-15-preview")

    HTTP_CODE=$(echo "$AZURE_RESPONSE" | tail -n1)

    if [ "$HTTP_CODE" = "200" ]; then
        echo -e "${GREEN}✓ Azure OpenAI API connection successful${NC}"

        # Check if embedding deployment exists
        if echo "$AZURE_RESPONSE" | grep -q "$AZURE_OPENAI_EMBEDDING_DEPLOYMENT_NAME"; then
            echo -e "${GREEN}✓ Embedding deployment '$AZURE_OPENAI_EMBEDDING_DEPLOYMENT_NAME' exists${NC}"
        else
            echo -e "${YELLOW}⚠ Embedding deployment '$AZURE_OPENAI_EMBEDDING_DEPLOYMENT_NAME' not found${NC}"
        fi

        # Check if chat deployment exists
        if echo "$AZURE_RESPONSE" | grep -q "$AZURE_OPENAI_CHAT_DEPLOYMENT_NAME"; then
            echo -e "${GREEN}✓ Chat deployment '$AZURE_OPENAI_CHAT_DEPLOYMENT_NAME' exists${NC}"
        else
            echo -e "${YELLOW}⚠ Chat deployment '$AZURE_OPENAI_CHAT_DEPLOYMENT_NAME' not found${NC}"
        fi
    elif [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
        echo -e "${RED}✗ Azure OpenAI authentication failed (HTTP $HTTP_CODE)${NC}"
        echo "  Check your AZURE_OPENAI_API_KEY and AZURE_OPENAI_ENDPOINT"
    else
        echo -e "${RED}✗ Azure OpenAI API request failed (HTTP $HTTP_CODE)${NC}"
    fi
fi

echo ""
echo "========================================="
echo "4. Testing n8n Webhook"
echo "========================================="
echo ""

if command -v curl &> /dev/null; then
    echo "Testing n8n webhook endpoint..."

    # Create test payload
    TEST_PAYLOAD=$(cat <<EOF
{
  "webhookSecret": "$N8N_WEBHOOK_SECRET",
  "companyId": "1",
  "voucherId": "test-verification-$(date +%s)",
  "header": {
    "voucherNumber": "TEST-VERIFY",
    "date": "$(date +%Y-%m-%d)",
    "description": "Setup verification test"
  },
  "lines": [
    {
      "accountCode": "111",
      "accountName": "Cash",
      "debit": 1000000,
      "credit": 0,
      "description": "Test line"
    },
    {
      "accountCode": "511",
      "accountName": "Revenue",
      "debit": 0,
      "credit": 1000000,
      "description": "Test line"
    }
  ],
  "summary": {
    "totalDebit": 1000000,
    "totalCredit": 1000000
  }
}
EOF
)

    N8N_RESPONSE=$(curl -s -w "\n%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        -d "$TEST_PAYLOAD" \
        "$N8N_WEBHOOK_URL")

    HTTP_CODE=$(echo "$N8N_RESPONSE" | tail -n1)

    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
        echo -e "${GREEN}✓ n8n webhook responded successfully (HTTP $HTTP_CODE)${NC}"

        # Check response for success indicator
        if echo "$N8N_RESPONSE" | grep -q "success"; then
            echo -e "${GREEN}✓ Webhook processing successful${NC}"
        else
            echo -e "${YELLOW}⚠ Webhook responded but processing status unclear${NC}"
        fi
    elif [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
        echo -e "${RED}✗ n8n webhook authentication failed (HTTP $HTTP_CODE)${NC}"
        echo "  Check your N8N_WEBHOOK_SECRET"
    elif [ "$HTTP_CODE" = "404" ]; then
        echo -e "${RED}✗ n8n webhook not found (HTTP $HTTP_CODE)${NC}"
        echo "  Check your N8N_WEBHOOK_URL"
    else
        echo -e "${RED}✗ n8n webhook request failed (HTTP $HTTP_CODE)${NC}"
    fi
fi

echo ""
echo "========================================="
echo "5. Backend Service Check"
echo "========================================="
echo ""

if command -v curl &> /dev/null; then
    echo "Checking if backend is running..."

    HEALTH_RESPONSE=$(curl -s -w "\n%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
    HTTP_CODE=$(echo "$HEALTH_RESPONSE" | tail -n1)

    if [ "$HTTP_CODE" = "200" ]; then
        echo -e "${GREEN}✓ Backend is running${NC}"
        echo ""
        echo "You can now test the chatbot API:"
        echo "  GET http://localhost:8080/api/v1/chatbot/health"
    elif [ "$HTTP_CODE" = "000" ]; then
        echo -e "${YELLOW}⚠ Backend is not running${NC}"
        echo "  Start with: cd backend && mvn spring-boot:run"
    else
        echo -e "${YELLOW}⚠ Backend health check returned HTTP $HTTP_CODE${NC}"
    fi
else
    echo -e "${YELLOW}⚠ curl not found, cannot check backend status${NC}"
fi

echo ""
echo "========================================="
echo "Summary"
echo "========================================="
echo ""
echo "Setup verification complete!"
echo ""
echo "Next steps:"
echo "  1. Fix any errors (marked with ✗) above"
echo "  2. Address warnings (marked with ⚠) if needed"
echo "  3. Start backend: cd backend && mvn spring-boot:run"
echo "  4. Test chatbot API: curl http://localhost:8080/api/v1/chatbot/health"
echo ""
echo "For detailed setup instructions, see:"
echo "  docs/manuals/chatbot_setup.md"
echo ""
