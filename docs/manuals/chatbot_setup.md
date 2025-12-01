# Chatbot Setup Manual - Story 9.0

This manual provides step-by-step instructions for setting up the MVP Voucher-Focused RAG Chatbot with external service integrations.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Pinecone Setup](#1-pinecone-setup)
3. [Azure OpenAI / OpenAI Setup](#2-azure-openai--openai-setup)
4. [n8n Workflow Automation Setup](#3-n8n-workflow-automation-setup)
5. [Backend Configuration](#4-backend-configuration)
6. [Testing & Verification](#5-testing--verification)
7. [Troubleshooting](#6-troubleshooting)

---

## Prerequisites

- [ ] Active email account for service signups
- [ ] Credit card (for Azure/OpenAI, free tier available)
- [ ] Docker installed (if self-hosting n8n)
- [ ] Backend application running locally

---

## 1. Pinecone Setup

### 1.1 Create Pinecone Account

1. Visit [https://www.pinecone.io/](https://www.pinecone.io/)
2. Click **"Start Free"** button
3. Sign up with your email address
4. Verify your email
5. Complete onboarding (select "Developer" profile)

**Free Tier Includes:**
- 100GB storage
- Serverless compute
- No credit card required

### 1.2 Create Vector Index

1. In Pinecone Console, click **"Create Index"**
2. Fill in index details:
   - **Name:** `accounting-embeddings`
   - **Dimensions:** `1536` (for OpenAI text-embedding-ada-002)
   - **Metric:** `cosine`
   - **Cloud:** `AWS`
   - **Region:** `us-east-1`
   - **Plan:** `Serverless` (Free)

3. Click **"Create Index"** (takes ~2 minutes to provision)

### 1.3 Get API Credentials

1. Navigate to **"API Keys"** in left sidebar
2. Copy your **API Key** (format: `pcsk_...` or `pc-...`)
3. Note the **Environment** (usually `us-east-1-aws` for serverless)

**Save these values:**
```bash
PINECONE_API_KEY=pcsk_XXXXXX_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
PINECONE_ENVIRONMENT=us-east-1-aws
PINECONE_INDEX_NAME=accounting-embeddings
```

### 1.4 Test Connection

Use Pinecone Console → Data → Query to verify index is accessible.

---

## 2. Azure OpenAI / OpenAI Setup

Choose **ONE** of the following options:

### Option A: Azure OpenAI (Recommended for Production)

#### 2.A.1 Create Azure Account

1. Visit [https://azure.microsoft.com/](https://azure.microsoft.com/)
2. Click **"Start free"** ($200 free credit for 30 days)
3. Sign up with Microsoft account
4. Complete verification (requires credit card, won't be charged during trial)

#### 2.A.2 Create Azure OpenAI Resource

1. In **Azure Portal**, search for **"Azure OpenAI"**
2. Click **"+ Create"**
3. Fill in resource details:
   - **Subscription:** Your subscription
   - **Resource group:** Create new → `accounting-ai-rg`
   - **Region:** `East US` or `West Europe` (check availability)
   - **Name:** `accounting-openai`
   - **Pricing tier:** `Standard S0`

4. Click **"Review + Create"** → **"Create"**
5. Wait for deployment (~2 minutes)

#### 2.A.3 Deploy AI Models

1. Open your **accounting-openai** resource
2. Go to **"Model deployments"** → **"Manage Deployments"** (opens Azure OpenAI Studio)
3. Click **"+ Create new deployment"**

**Deploy Model 1: Embeddings**
- **Model:** `text-embedding-ada-002`
- **Deployment name:** `text-embedding-ada-002` (keep same as model name)
- **Model version:** Auto-update to default
- **Deployment type:** Standard
- Click **"Create"**

**Deploy Model 2: Chat**
- **Model:** `gpt-35-turbo` (or `gpt-4` if available)
- **Deployment name:** `gpt-35-turbo` (keep same as model name)
- **Model version:** Auto-update to default
- **Deployment type:** Standard
- **Tokens per minute rate limit:** 10K (adjust based on usage)
- Click **"Create"**

#### 2.A.4 Get API Credentials

1. Go back to Azure Portal → Your OpenAI resource
2. Navigate to **"Keys and Endpoint"** (left sidebar)
3. Copy the following:
   - **KEY 1** → Save as `AZURE_OPENAI_API_KEY`
   - **Endpoint** → Save as `AZURE_OPENAI_ENDPOINT` (e.g., `https://accounting-openai.openai.azure.com/`)

**Save these values:**
```bash
AZURE_OPENAI_API_KEY=your-32-character-key-here
AZURE_OPENAI_ENDPOINT=https://accounting-openai.openai.azure.com/
AZURE_OPENAI_EMBEDDING_DEPLOYMENT_NAME=text-embedding-ada-002
AZURE_OPENAI_CHAT_DEPLOYMENT_NAME=gpt-35-turbo
```

---

### Option B: OpenAI (Easier for MVP Testing)

#### 2.B.1 Create OpenAI Account

1. Visit [https://platform.openai.com/signup](https://platform.openai.com/signup)
2. Sign up with email or Google account
3. Verify your email address

#### 2.B.2 Add Payment Method

1. Go to **"Billing"** → **"Payment methods"**
2. Add credit card (pay-as-you-go pricing)
3. Add initial credit ($5-10 recommended for testing)

**Estimated Costs for MVP Testing:**
- Embedding (ada-002): $0.0001 / 1K tokens (~$0.001 per voucher)
- Chat (gpt-3.5-turbo): $0.0015 / 1K tokens (~$0.01 per query)
- **Total for 100 queries:** ~$1-2

#### 2.B.3 Create API Key

1. Navigate to **"API keys"**
2. Click **"+ Create new secret key"**
3. **Name:** `accounting-chatbot`
4. **Permissions:** `All` (or `Read` if restrictive)
5. Copy the key (starts with `sk-...`)
6. **Save immediately** (won't be shown again!)

**Save this value:**
```bash
OPENAI_API_KEY=sk-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
```

---

## 3. n8n Workflow Automation Setup

Choose **ONE** of the following options:

### Option A: n8n Cloud (Recommended for MVP)

#### 3.A.1 Create n8n Cloud Account

1. Visit [https://n8n.io/](https://n8n.io/)
2. Click **"Start free"**
3. Sign up with email
4. Verify email
5. Complete onboarding

**Free Tier Includes:**
- 5,000 workflow executions/month
- Unlimited workflows
- No credit card required

#### 3.A.2 Import Voucher Embedding Workflow

1. In n8n dashboard, click **"+ New workflow"**
2. Click **"..."** (three dots) → **"Import from JSON"**
3. Paste the workflow JSON (provided below)
4. Click **"Import"**

#### 3.A.3 Configure Workflow Credentials

**Step 1: Configure Webhook Node**
1. Click the **"Webhook"** node (first node)
2. **Webhook URLs:**
   - Production URL: Copy this → Save as `N8N_WEBHOOK_URL`
3. **Authentication:** None (we'll use webhook secret in request body)
4. **HTTP Method:** POST
5. Click **"Save"**

**Step 2: Generate Webhook Secret**
1. Generate a random secret (use password generator):
   ```bash
   openssl rand -base64 32
   ```
2. Save as `N8N_WEBHOOK_SECRET`

**Step 3: Configure OpenAI Credentials**
1. Click the **"OpenAI"** node (embedding generation)
2. Click **"Create New Credential"**
3. **API Key:** Enter your OpenAI or Azure OpenAI key
4. **Resource Name:** (Azure only) Enter your resource name
5. Click **"Save"**

**Step 4: Configure Pinecone Credentials**
1. Click the **"Pinecone"** node (upsert vector)
2. Click **"Create New Credential"**
3. **API Key:** Enter your Pinecone API key
4. **Environment:** Enter your Pinecone environment (e.g., `us-east-1-aws`)
5. Click **"Save"**

#### 3.A.4 Test Workflow

1. Click **"Test workflow"** button (top right)
2. Send a test webhook request from your terminal:
   ```bash
   curl -X POST https://your-n8n-instance.app.n8n.cloud/webhook/voucher-embedding \
     -H "Content-Type: application/json" \
     -d '{
       "webhookSecret": "your-webhook-secret-here",
       "companyId": "1",
       "voucherId": "test-123",
       "header": {
         "voucherNumber": "TEST-001",
         "date": "2023-11-24",
         "description": "Test voucher"
       },
       "lines": [
         {
           "accountCode": "111",
           "accountName": "Cash",
           "debit": 1000000,
           "credit": 0,
           "description": "Test debit"
         }
       ],
       "summary": {
         "totalDebit": 1000000,
         "totalCredit": 1000000
       }
     }'
   ```

3. Check execution log:
   - ✅ Green checkmark = Success
   - ❌ Red X = Check error details

---

### Option B: Self-Hosted n8n (Advanced)

#### 3.B.1 Run n8n with Docker

```bash
# Create n8n data directory
mkdir -p ~/.n8n

# Run n8n container
docker run -d \
  --name n8n \
  -p 5678:5678 \
  -v ~/.n8n:/home/node/.n8n \
  -e N8N_BASIC_AUTH_ACTIVE=true \
  -e N8N_BASIC_AUTH_USER=admin \
  -e N8N_BASIC_AUTH_PASSWORD=your-secure-password \
  n8nio/n8n

# Check logs
docker logs -f n8n
```

#### 3.B.2 Access n8n

1. Open [http://localhost:5678](http://localhost:5678)
2. Log in with credentials (admin / your-secure-password)
3. Follow same steps as **Option A** above for workflow import and configuration

**Note:** For self-hosted, your webhook URL will be:
```
http://localhost:5678/webhook/voucher-embedding
```

---

## 4. Backend Configuration

### 4.1 Update application.yml

Add chatbot configuration block:

```yaml
chatbot:
  enabled: ${CHATBOT_ENABLED:true}
  rate-limit:
    requests-per-minute: ${CHATBOT_RATE_LIMIT_REQUESTS_PER_MINUTE:20}
  query:
    max-length: ${CHATBOT_MAX_QUERY_LENGTH:5000}
    confidence-threshold: ${CHATBOT_CONFIDENCE_THRESHOLD:0.5}

pinecone:
  api-key: ${PINECONE_API_KEY}
  environment: ${PINECONE_ENVIRONMENT:us-east-1-aws}
  index-name: ${PINECONE_INDEX_NAME:accounting-embeddings}
  top-k: ${PINECONE_TOP_K:10}
  score-threshold: ${PINECONE_SCORE_THRESHOLD:0.7}
  namespace-prefix: ${PINECONE_NAMESPACE_PREFIX:company}
  connection-timeout: ${PINECONE_CONNECTION_TIMEOUT:10000}
  read-timeout: ${PINECONE_READ_TIMEOUT:30000}

azure-openai:
  api-key: ${AZURE_OPENAI_API_KEY}
  endpoint: ${AZURE_OPENAI_ENDPOINT}
  embedding-deployment-name: ${AZURE_OPENAI_EMBEDDING_DEPLOYMENT_NAME:text-embedding-ada-002}
  chat-deployment-name: ${AZURE_OPENAI_CHAT_DEPLOYMENT_NAME:gpt-35-turbo}
  api-version: ${AZURE_OPENAI_API_VERSION:2024-02-15-preview}
  embedding:
    dimensions: ${AZURE_OPENAI_EMBEDDING_DIMENSIONS:1536}
  chat:
    max-tokens: ${AZURE_OPENAI_CHAT_MAX_TOKENS:500}
    temperature: ${AZURE_OPENAI_CHAT_TEMPERATURE:0.3}
  connection-timeout: ${AZURE_OPENAI_CONNECTION_TIMEOUT:10000}
  read-timeout: ${AZURE_OPENAI_READ_TIMEOUT:60000}

n8n:
  webhook:
    url: ${N8N_WEBHOOK_URL}
    secret: ${N8N_WEBHOOK_SECRET}
    retry:
      enabled: ${N8N_WEBHOOK_RETRY_ENABLED:true}
      max-retries: ${N8N_WEBHOOK_MAX_RETRIES:3}
      delay-ms: ${N8N_WEBHOOK_RETRY_DELAY_MS:1000,5000,15000}
    connection-timeout: ${N8N_WEBHOOK_CONNECTION_TIMEOUT:10000}
    read-timeout: ${N8N_WEBHOOK_READ_TIMEOUT:30000}
```

### 4.2 Create .env File

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```

2. Fill in your actual credentials in `.env` (never commit this file!)

3. Add `.env` to `.gitignore` if not already present:
   ```bash
   echo ".env" >> .gitignore
   ```

### 4.3 Restart Backend

```bash
cd backend
mvn spring-boot:run
```

Check logs for successful startup:
```
✓ Pinecone connection established
✓ Azure OpenAI service initialized
✓ N8n webhook service configured
✓ Chatbot controller registered at /api/v1/chatbot
```

---

## 5. Testing & Verification

### 5.1 Test Pinecone Connection

```bash
curl -X GET http://localhost:8080/api/v1/chatbot/health \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Expected response:**
```json
{
  "status": "UP",
  "service": "chatbot",
  "message": "Chatbot service is operational"
}
```

### 5.2 Test Embedding Pipeline

1. Create a test voucher via backend API
2. Check n8n execution log → Should see successful execution
3. Verify in Pinecone Console:
   - Go to **"Indexes"** → `accounting-embeddings`
   - Click **"Data"** tab
   - Search for namespace: `company-1`
   - Should see vector ID: `voucher-{id}`

### 5.3 Test Chatbot Query

```bash
curl -X POST http://localhost:8080/api/v1/chatbot/query \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Công nợ phải trả là bao nhiêu?",
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "language": "vi"
  }'
```

**Expected response:**
```json
{
  "queryId": "uuid-here",
  "answer": "Tổng công nợ phải trả hiện tại là...",
  "citations": [
    {
      "entityType": "voucher",
      "voucherNumber": "PC-2023-001",
      "excerpt": "Thanh toán nhà cung cấp ABC - 50,000,000 VND",
      "relevanceScore": 0.92,
      "link": "/vouchers/uuid"
    }
  ],
  "confidenceScore": 0.85,
  "confidenceLevel": "HIGH",
  "responseTimeMs": 1234
}
```

---

## 6. Troubleshooting

### Issue: "Pinecone connection failed"

**Causes:**
- Invalid API key
- Wrong environment name
- Index not created yet

**Solutions:**
1. Verify API key in `.env` matches Pinecone console
2. Check environment name (usually `us-east-1-aws` for serverless)
3. Wait 2-3 minutes after index creation for provisioning

---

### Issue: "Azure OpenAI authentication failed"

**Causes:**
- Invalid API key
- Wrong endpoint URL
- Deployment names don't match

**Solutions:**
1. Verify API key in Azure Portal → Keys and Endpoint
2. Ensure endpoint ends with trailing `/` (e.g., `https://...azure.com/`)
3. Check deployment names match exactly (case-sensitive)

---

### Issue: "n8n webhook timeout"

**Causes:**
- n8n service down
- Wrong webhook URL
- Webhook secret mismatch

**Solutions:**
1. Check n8n service status (for self-hosted: `docker logs n8n`)
2. Verify webhook URL in n8n matches `.env`
3. Check webhook secret matches in both places
4. Test webhook directly with curl (see section 3.A.4)

---

### Issue: "No results found (confidence < 0.5)"

**Causes:**
- No vouchers embedded yet
- Query too vague
- Wrong company namespace

**Solutions:**
1. Create test vouchers and wait for embedding
2. Use more specific accounting terms in query
3. Verify company ID in JWT token matches embedded data

---

## Cost Monitoring

### Pinecone
- Free tier: 100GB storage
- Monitor usage: Pinecone Console → Usage

### Azure OpenAI
- Free trial: $200 credit for 30 days
- Monitor usage: Azure Portal → Cost Management + Billing

### OpenAI
- Pay-as-you-go
- Monitor usage: OpenAI Platform → Usage
- Set spending limits: Settings → Billing → Usage limits

### n8n Cloud
- Free tier: 5,000 executions/month
- Monitor usage: n8n Dashboard → Usage

**Estimated Monthly Cost (MVP, 1000 queries/month):**
- Pinecone: $0 (free tier)
- Azure OpenAI: ~$2-5
- n8n: $0 (free tier)
- **Total: ~$2-5/month**

---

## Next Steps

After successful setup:
1. ✅ Mark Task 1 as complete in story file
2. ✅ Continue with Task 6: Frontend chatbot widget
3. ✅ Run end-to-end tests (Task 8)

---

## Support & Resources

- **Pinecone Docs:** https://docs.pinecone.io/
- **Azure OpenAI Docs:** https://learn.microsoft.com/en-us/azure/ai-services/openai/
- **OpenAI Docs:** https://platform.openai.com/docs
- **n8n Docs:** https://docs.n8n.io/
- **Project Issues:** Create GitHub issue with `[chatbot]` tag
