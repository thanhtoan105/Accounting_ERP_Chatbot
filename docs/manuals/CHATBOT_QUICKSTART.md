# Chatbot Quick Start Guide

**5-minute setup for Story 9.0 MVP**

## Overview

This guide gets you up and running with the AI chatbot in 5 steps:

1. ✅ Copy environment template
2. ☁️ Set up external services (Pinecone, Azure OpenAI, n8n)
3. 🔧 Configure credentials
4. ✅ Verify setup
5. 🚀 Test chatbot

**Estimated time:** 30-45 minutes (mostly account creation)

---

## Step 1: Copy Environment Template (1 min)

```bash
# From project root
cp .env.example .env
```

---

## Step 2: Set Up External Services (20-30 min)

### 2.1 Pinecone (Vector Database) - 5 minutes

1. Go to https://www.pinecone.io/ → **Start Free**
2. Create account (no credit card required)
3. Create index:
   - Name: `accounting-embeddings`
   - Dimensions: `1536`
   - Metric: `cosine`
   - Region: `us-east-1` (AWS)
   - Plan: Serverless
4. Copy API key from **API Keys** page

**Save to .env:**
```bash
PINECONE_API_KEY=pcsk_YOUR_KEY_HERE
PINECONE_ENVIRONMENT=us-east-1-aws
PINECONE_INDEX_NAME=accounting-embeddings
```

---

### 2.2 Azure OpenAI (AI Models) - 10-15 minutes

**Option A: Azure OpenAI (Production-ready)**

1. Go to https://azure.microsoft.com/ → **Free trial**
2. Create Azure OpenAI resource:
   - Portal → Search "Azure OpenAI" → Create
   - Region: East US
   - Name: `accounting-openai`
3. Deploy models (in Azure OpenAI Studio):
   - `text-embedding-ada-002` (embeddings)
   - `gpt-35-turbo` (chat)
4. Get credentials:
   - Portal → Your resource → **Keys and Endpoint**
   - Copy **KEY 1** and **Endpoint**

**Save to .env:**
```bash
AZURE_OPENAI_API_KEY=YOUR_KEY_HERE
AZURE_OPENAI_ENDPOINT=https://accounting-openai.openai.azure.com/
AZURE_OPENAI_EMBEDDING_DEPLOYMENT_NAME=text-embedding-ada-002
AZURE_OPENAI_CHAT_DEPLOYMENT_NAME=gpt-35-turbo
```

**Option B: OpenAI (Faster for testing)**

1. Go to https://platform.openai.com/signup
2. Add payment method (Billing → Payment methods)
3. Create API key (API keys → Create new key)

**Save to .env:**
```bash
OPENAI_API_KEY=sk-YOUR_KEY_HERE
# Comment out Azure variables above
```

**💰 Cost estimate:** ~$2-5/month for MVP testing (100-1000 queries)

---

### 2.3 n8n (Workflow Automation) - 5-10 minutes

**Option A: n8n Cloud (Easiest)**

1. Go to https://n8n.io/ → **Start free**
2. Create account (5000 free executions/month)
3. Import workflow:
   - Dashboard → **+ New workflow**
   - **...** menu → **Import from JSON**
   - Paste content from `docs/manuals/n8n-voucher-embedding-workflow.json`
4. Configure credentials:
   - Click **OpenAI** node → Add credential (your OpenAI/Azure key)
   - Click **Pinecone** node → Add credential (your Pinecone key)
5. Get webhook URL:
   - Click **Webhook** node
   - Copy **Production URL**
6. Generate secret:
   ```bash
   openssl rand -base64 32
   ```

**Save to .env:**
```bash
N8N_WEBHOOK_URL=https://your-instance.app.n8n.cloud/webhook/voucher-embedding
N8N_WEBHOOK_SECRET=YOUR_GENERATED_SECRET_HERE
```

**Option B: Self-hosted (Docker)**

```bash
docker run -d --name n8n -p 5678:5678 -v ~/.n8n:/home/node/.n8n n8nio/n8n
```

Then follow same steps as Option A (webhook URL will be `http://localhost:5678/webhook/voucher-embedding`)

---

## Step 3: Configure Credentials (2 min)

Edit `.env` file with your actual values from Step 2:

```bash
# Required variables
CHATBOT_ENABLED=true
PINECONE_API_KEY=pcsk_...
AZURE_OPENAI_API_KEY=...
AZURE_OPENAI_ENDPOINT=...
N8N_WEBHOOK_URL=...
N8N_WEBHOOK_SECRET=...
```

**Don't commit .env!** It's already in `.gitignore`.

---

## Step 4: Verify Setup (2 min)

Run verification script:

```bash
./scripts/verify-chatbot-setup.sh
```

**Expected output:**
```
✓ Pinecone API connection successful
✓ Azure OpenAI API connection successful
✓ n8n webhook responded successfully
```

Fix any errors (marked with ✗) before proceeding.

---

## Step 5: Test Chatbot (5 min)

### 5.1 Start Backend

```bash
cd backend
mvn spring-boot:run
```

Wait for:
```
✓ Chatbot controller registered at /api/v1/chatbot
```

### 5.2 Test Health Endpoint

```bash
curl http://localhost:8080/api/v1/chatbot/health
```

**Expected response:**
```json
{
  "status": "UP",
  "service": "chatbot",
  "message": "Chatbot service is operational"
}
```

### 5.3 Create Test Voucher

Use Postman/Thunder Client to POST to:
```
POST http://localhost:8080/api/v1/vouchers
Authorization: Bearer YOUR_JWT_TOKEN
```

This will trigger the embedding pipeline (check n8n execution log).

### 5.4 Query Chatbot

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
  "queryId": "...",
  "answer": "Tổng công nợ phải trả hiện tại là...",
  "citations": [...],
  "confidenceScore": 0.85,
  "confidenceLevel": "HIGH",
  "responseTimeMs": 1234
}
```

---

## Troubleshooting

### "Pinecone connection failed"
- Verify API key in `.env` matches Pinecone console
- Check index name is exactly `accounting-embeddings`
- Wait 2-3 minutes after index creation

### "Azure OpenAI authentication failed"
- Verify endpoint ends with `/` (e.g., `https://...azure.com/`)
- Check deployment names match exactly (case-sensitive)
- Ensure models are deployed (not just uploaded)

### "n8n webhook timeout"
- Check n8n service is running
- Test webhook URL directly with curl
- Verify webhook secret matches

### "No results found (confidence < 0.5)"
- Create test vouchers first
- Wait for embedding pipeline to complete (check n8n)
- Use more specific accounting terms

---

## Next Steps

✅ **Task 1 complete!** External services configured.

**Continue with:**
- Task 6: Frontend chatbot widget (React components)
- Task 8: End-to-end testing
- Task 9: Documentation

---

## Cost Management

### Monitor Usage

- **Pinecone:** Console → Usage (100GB free)
- **Azure OpenAI:** Portal → Cost Management
- **OpenAI:** Platform → Usage
- **n8n:** Dashboard → Usage (5K executions/month free)

### Set Limits

**Azure:**
- Portal → Cost Management → Budgets → Create budget

**OpenAI:**
- Platform → Settings → Billing → Usage limits

### Estimated Costs (MVP)

| Service | Free Tier | Paid Tier (1000 queries/month) |
|---------|-----------|--------------------------------|
| Pinecone | ✅ 100GB | $0 |
| Azure OpenAI | ✅ $200 credit | ~$3 |
| n8n | ✅ 5K executions | $0 |
| **Total** | **$0** | **~$3/month** |

---

## Support

- **Detailed manual:** `docs/manuals/chatbot_setup.md`
- **n8n workflow:** `docs/manuals/n8n-voucher-embedding-workflow.json`
- **Project issues:** Create GitHub issue with `[chatbot]` tag

---

**Done!** Your chatbot is ready for development. 🎉
