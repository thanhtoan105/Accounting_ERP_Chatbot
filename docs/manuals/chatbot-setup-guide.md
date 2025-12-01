# AI Chatbot Setup Guide

**Epic 9 - Story 9.0: MVP Voucher-Focused RAG Chatbot**

Date: 2025-11-24
Version: 1.0 - MVP Implementation

---

## Overview

This guide provides step-by-step instructions for setting up the AI RAG (Retrieval-Augmented Generation) chatbot for the accounting system. The chatbot uses:

- **Pinecone** - Vector database for storing embeddings
- **Azure OpenAI** - Embedding generation and LLM completions
- **n8n** - Automation pipeline for embedding workflow
- **PostgreSQL** - Audit logging and query history
- **Redis** - Caching for frequently asked questions

## Prerequisites

Before starting, ensure you have:

- [ ] Administrative access to Azure portal (for Azure OpenAI)
- [ ] Credit card for Azure OpenAI billing setup (pay-as-you-go)
- [ ] Pinecone account credentials (free tier available)
- [ ] Docker installed for n8n deployment
- [ ] PostgreSQL and Redis running (via docker-compose)

## Part 1: Azure OpenAI Setup

### 1.1 Create Azure OpenAI Resource

1. **Login to Azure Portal**
   - Navigate to https://portal.azure.com
   - Sign in with your Azure account

2. **Create Resource Group** (if not exists)
   ```
   Portal → Resource Groups → Create
   - Name: rg-accounting-ai-prod
   - Region: Southeast Asia (or your preferred region)
   ```

3. **Create Azure OpenAI Service**
   ```
   Portal → Create a resource → Search "Azure OpenAI"
   - Resource group: rg-accounting-ai-prod
   - Region: Southeast Asia
   - Name: openai-accounting-chatbot
   - Pricing tier: Standard S0 (pay-as-you-go)
   ```

4. **Wait for Deployment** (typically 2-5 minutes)

### 1.2 Deploy Embedding Model

1. **Navigate to Azure OpenAI Studio**
   ```
   Resource → Overview → Azure OpenAI Studio (button)
   ```

2. **Deploy Embedding Model**
   ```
   Studio → Deployments → Create new deployment
   - Model: text-embedding-ada-002
   - Deployment name: embedding-ada-002
   - Model version: Latest (2)
   - Tokens per Minute Rate Limit: 120,000 (adjust based on usage)
   ```

3. **Record Deployment Details**
   ```
   Deployment name: embedding-ada-002
   Model: text-embedding-ada-002
   Version: 2
   Endpoint: https://openai-accounting-chatbot.openai.azure.com/
   ```

### 1.3 Deploy Chat Completion Model

1. **Create Second Deployment**
   ```
   Studio → Deployments → Create new deployment
   - Model: gpt-35-turbo (or gpt-4 for better quality)
   - Deployment name: gpt-35-turbo
   - Model version: Latest (0125)
   - Tokens per Minute Rate Limit: 60,000
   ```

2. **Record Deployment Details**
   ```
   Deployment name: gpt-35-turbo
   Model: gpt-35-turbo
   Version: 0125
   ```

### 1.4 Get API Keys

1. **Navigate to Keys and Endpoint**
   ```
   Resource → Keys and Endpoint (left menu under Resource Management)
   ```

2. **Copy Configuration Details**
   ```
   Endpoint: https://openai-accounting-chatbot.openai.azure.com/
   Key 1: [COPY THIS - 32 character key]
   Location/Region: southeastasia
   ```

3. **Save Securely**
   - Store Key 1 in password manager or secret vault
   - Never commit to Git
   - Will be used as `AZURE_OPENAI_API_KEY` environment variable

### 1.5 Cost Estimation

**Typical Monthly Costs (MVP with 50 users):**
- Embeddings (text-embedding-ada-002): ~500,000 tokens/month = **$0.05**
- Completions (gpt-35-turbo): ~2M tokens/month = **$4.00**
- **Total: ~$4-5/month**

**Cost Optimization Tips:**
- Use Redis caching for repeated questions (reduces LLM calls by 40-60%)
- Set max_tokens=500 for answers (prevents long responses)
- Monitor usage via Azure Cost Management dashboard

---

## Part 2: Pinecone Setup

### 2.1 Create Pinecone Account

1. **Sign Up**
   - Navigate to https://www.pinecone.io
   - Click "Start Free" or "Sign Up"
   - Use work email: [your-email@company.com]
   - Verify email

2. **Choose Plan**
   - Select "Starter" plan (free tier)
   - Free tier includes:
     - 1 project
     - 1 index
     - 100K vectors
     - Sufficient for MVP with ~5,000 vouchers

### 2.2 Create Index

1. **Create New Index**
   ```
   Dashboard → Indexes → Create Index

   Settings:
   - Name: accounting-embeddings
   - Dimensions: 1536 (Azure OpenAI ada-002 dimensions)
   - Metric: cosine
   - Pod Type: Starter (free)
   - Region: us-east-1-aws (or closest to your region)
   - Metadata Config: Leave default (allows all metadata)
   ```

2. **Wait for Index Creation** (30-60 seconds)

3. **Verify Index Status**
   ```
   Status should show: "Ready"
   Metrics: 0 vectors, 0 namespaces (initial state)
   ```

### 2.3 Get API Key

1. **Navigate to API Keys**
   ```
   Dashboard → API Keys (left menu)
   ```

2. **Copy Details**
   ```
   Environment: us-east-1-aws
   API Key: [COPY THIS - starts with pc-xxx]
   ```

3. **Test Connection** (optional, via curl)
   ```bash
   curl -X GET https://controller.us-east-1-aws.pinecone.io/actions/indexes \
     -H "Api-Key: YOUR_API_KEY"
   ```

   Expected response: JSON list with "accounting-embeddings" index

### 2.4 Understanding Namespaces

**Multi-Tenancy Design:**
- Each company gets a dedicated namespace: `company-{companyId}`
- Example:
  - Company A (UUID: 123e4567-...): `company-123e4567-e89b-12d3-a456-426614174000`
  - Company B (UUID: 789abcde-...): `company-789abcde-f012-34d5-b678-901234567890`

**Benefits:**
- Complete data isolation between companies
- No risk of cross-company data leakage
- Easy to delete all data for a specific company (delete namespace)

---

## Part 3: n8n Automation Setup

### 3.1 Deploy n8n Docker Container

1. **Create n8n Directory**
   ```bash
   cd /home/thanhtoan/code/accounting
   mkdir -p docker/n8n
   ```

2. **Add n8n Service to docker-compose.yml**

   Open `docker-compose.yml` and add:

   ```yaml
   n8n:
     image: n8nio/n8n:latest
     container_name: accounting-n8n
     restart: unless-stopped
     ports:
       - "5678:5678"
     environment:
       - N8N_HOST=localhost
       - N8N_PORT=5678
       - N8N_PROTOCOL=http
       - NODE_ENV=production
       - WEBHOOK_URL=http://localhost:5678/
       - GENERIC_TIMEZONE=Asia/Ho_Chi_Minh
       - N8N_ENCRYPTION_KEY=${N8N_ENCRYPTION_KEY}
     volumes:
       - ./docker/n8n:/home/node/.n8n
     networks:
       - accounting-network
   ```

3. **Generate Encryption Key**
   ```bash
   # Generate random 32-character key
   openssl rand -hex 16

   # Add to .env file
   echo "N8N_ENCRYPTION_KEY=your-generated-key" >> .env
   ```

4. **Start n8n**
   ```bash
   docker-compose up -d n8n
   ```

5. **Access n8n UI**
   - Navigate to http://localhost:5678
   - Create admin account on first login:
     - Email: admin@accounting.local
     - Password: [Strong password - store securely]

### 3.2 Import Pre-Built Workflows (Recommended)

Pre-built workflow JSON files are available in `docs/n8n-workflows/`:

1. **Import Workflows**
   ```
   n8n UI → Workflows → Import from File
   ```
   
   Import these files:
   - `voucher-embedding-automation.json` - Embeds vouchers into Pinecone
   - `rag-query-processing.json` - Processes chatbot queries with RAG

2. **Configure Credentials After Import**
   
   Create two HTTP Header Auth credentials:
   
   | Credential Name | Header Name | Header Value |
   |-----------------|-------------|--------------|
   | Azure OpenAI API Key | `api-key` | Your Azure OpenAI key |
   | Pinecone API Key | `Api-Key` | Your Pinecone key |

3. **Set Environment Variables**
   
   Go to **Settings** → **Variables** and add:
   ```
   AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
   AZURE_OPENAI_EMBEDDING_DEPLOYMENT=embedding-ada-002
   AZURE_OPENAI_COMPLETION_DEPLOYMENT=gpt-35-turbo
   PINECONE_HOST=https://accounting-embeddings-xxx.svc.us-east-1-aws.pinecone.io
   ```

4. **Activate Workflows**
   - Open each imported workflow
   - Toggle **Active** switch to ON
   - Note the Production Webhook URLs

---

### 3.3 Manual Workflow Creation (Alternative)

If you prefer to create workflows manually, follow these steps:

1. **Create New Workflow**
   ```
   n8n UI → Workflows → Add Workflow
   - Name: Voucher Embedding Automation
   - Description: Embeds voucher data into Pinecone after post operation
   ```

2. **Add Webhook Trigger Node**
   ```
   Add Node → Trigger → Webhook

   Settings:
   - HTTP Method: POST
   - Path: voucher-embedding
   - Authentication: Header Auth
   - Header Name: X-Webhook-Secret
   - Header Value: [Generate random secret - see below]
   - Response Code: 200
   - Response Mode: When Last Node Finishes
   ```

   **Generate Webhook Secret:**
   ```bash
   openssl rand -base64 32
   # Save this as N8N_WEBHOOK_SECRET in .env
   ```

3. **Add Function Node - Format Voucher Text**
   ```
   Add Node → Function
   Name: Format Voucher for Embedding

   Code:
   const payload = $json;
   const voucher = payload.voucher;
   const lineItems = payload.lineItems || [];

   // Format voucher text for embedding
   const voucherText = `
   Voucher Number: ${voucher.voucherNumber}
   Date: ${voucher.voucherDate}
   Description: ${voucher.description || 'N/A'}
   Total Debit: ${voucher.totalDebit}
   Total Credit: ${voucher.totalCredit}
   Period: ${voucher.periodCode || 'N/A'}

   Line Items:
   ${lineItems.map(line => `
   - Account: ${line.accountCode} (${line.accountName})
     Debit: ${line.debit || 0}
     Credit: ${line.credit || 0}
     Description: ${line.description || 'N/A'}
   `).join('\n')}

   Related Entities:
   Customer: ${voucher.customerName || 'N/A'}
   Supplier: ${voucher.supplierName || 'N/A'}
   `.trim();

   return {
     voucherText,
     voucherId: voucher.id,
     companyId: payload.companyId,
     entityType: 'voucher',
     metadata: {
       voucherNumber: voucher.voucherNumber,
       voucherDate: voucher.voucherDate,
       periodId: voucher.periodId,
       totalDebit: voucher.totalDebit,
       totalCredit: voucher.totalCredit,
       createdBy: voucher.createdBy
     }
   };
   ```

4. **Add HTTP Request Node - Azure OpenAI Embeddings**
   ```
   Add Node → HTTP Request
   Name: Generate Embedding (Azure OpenAI)

   Settings:
   - Method: POST
   - URL: https://openai-accounting-chatbot.openai.azure.com/openai/deployments/embedding-ada-002/embeddings?api-version=2023-05-15
   - Authentication: Generic Credential Type
     - Header Auth:
       - Name: api-key
       - Value: [Your Azure OpenAI API Key]
   - Body Content Type: JSON
   - Specify Body: Using Fields Below
   - Body Parameters:
     {
       "input": "={{ $json.voucherText }}"
     }
   ```

5. **Add Function Node - Prepare Pinecone Upsert**
   ```
   Add Node → Function
   Name: Prepare Pinecone Upsert

   Code:
   const embedding = $json.data[0].embedding;
   const metadata = $('Format Voucher for Embedding').item.json;

   return {
     vectors: [{
       id: `voucher-${metadata.voucherId}`,
       values: embedding,
       metadata: {
         ...metadata.metadata,
         companyId: metadata.companyId,
         entityType: metadata.entityType,
         embeddedAt: new Date().toISOString()
       }
     }]
   };
   ```

6. **Add HTTP Request Node - Pinecone Upsert**
   ```
   Add Node → HTTP Request
   Name: Upsert to Pinecone

   Settings:
   - Method: POST
   - URL: https://accounting-embeddings-PROJECT_ID.svc.us-east-1-aws.pinecone.io/vectors/upsert
     (Replace PROJECT_ID with your Pinecone project ID from dashboard)
   - Authentication: Generic Credential Type
     - Header Auth:
       - Name: Api-Key
       - Value: [Your Pinecone API Key]
   - Body Content Type: JSON
   - Specify Body: Using JSON
   - JSON:
     {
       "vectors": "={{ $json.vectors }}",
       "namespace": "company-={{ $('Format Voucher for Embedding').item.json.companyId }}"
     }
   ```

7. **Add Error Handling**
   ```
   For each HTTP Request node:
   - Go to Settings → Error Handling
   - Continue on Fail: Enable
   - Set Error Workflow: (Optional - create separate error notification workflow)
   ```

8. **Save and Activate Workflow**
   ```
   - Click "Save" (top right)
   - Toggle "Active" switch to ON
   - Copy Production Webhook URL:
     http://localhost:5678/webhook/voucher-embedding
   ```

### 3.3 Test Webhook

1. **Test with curl**
   ```bash
   curl -X POST http://localhost:5678/webhook/voucher-embedding \
     -H "Content-Type: application/json" \
     -H "X-Webhook-Secret: YOUR_N8N_WEBHOOK_SECRET" \
     -d '{
       "companyId": "123e4567-e89b-12d3-a456-426614174000",
       "voucher": {
         "id": 1,
         "voucherNumber": "PC-2025-001",
         "voucherDate": "2025-01-15",
         "description": "Payment to supplier ABC",
         "totalDebit": 10000000,
         "totalCredit": 10000000,
         "periodId": 1,
         "createdBy": 1
       },
       "lineItems": [
         {
           "accountCode": "331",
           "accountName": "Phải trả người bán",
           "debit": 0,
           "credit": 10000000,
           "description": "Supplier ABC"
         },
         {
           "accountCode": "111",
           "accountName": "Tiền mặt",
           "debit": 10000000,
           "credit": 0,
           "description": "Cash payment"
         }
       ]
     }'
   ```

2. **Verify in n8n Executions**
   ```
   n8n UI → Executions → Check latest execution
   - Status should be "Success"
   - Check each node's output data
   ```

3. **Verify in Pinecone**
   ```
   Pinecone Dashboard → accounting-embeddings index
   - Vector count should increase by 1
   - Namespace: company-123e4567-e89b-12d3-a456-426614174000
   ```

---

## Part 4: Application Configuration

### 4.1 Update application.yml

Add the following configuration to `backend/src/main/resources/application.yml`:

```yaml
# AI Chatbot Configuration
chatbot:
  enabled: ${CHATBOT_ENABLED:true}

  # Azure OpenAI Configuration
  azure-openai:
    endpoint: ${AZURE_OPENAI_ENDPOINT:https://openai-accounting-chatbot.openai.azure.com/}
    api-key: ${AZURE_OPENAI_API_KEY}
    api-version: "2023-05-15"
    embedding-deployment: ${AZURE_OPENAI_EMBEDDING_DEPLOYMENT:embedding-ada-002}
    completion-deployment: ${AZURE_OPENAI_COMPLETION_DEPLOYMENT:gpt-35-turbo}
    max-tokens: ${AZURE_OPENAI_MAX_TOKENS:500}
    temperature: ${AZURE_OPENAI_TEMPERATURE:0.3}
    timeout-seconds: ${AZURE_OPENAI_TIMEOUT:30}

  # Pinecone Configuration
  pinecone:
    api-key: ${PINECONE_API_KEY}
    environment: ${PINECONE_ENVIRONMENT:us-east-1-aws}
    index-name: ${PINECONE_INDEX_NAME:accounting-embeddings}
    namespace-prefix: "company-"
    top-k: 10
    score-threshold: 0.7

  # n8n Webhook Configuration
  n8n:
    webhook-url: ${N8N_WEBHOOK_URL:http://localhost:5678/webhook/voucher-embedding}
    webhook-secret: ${N8N_WEBHOOK_SECRET}
    retry-attempts: 3
    retry-delays: 1000,5000,15000  # milliseconds: 1s, 5s, 15s
    timeout-seconds: 10

  # Query Configuration
  query:
    max-length: 5000
    default-language: vi
    confidence-threshold-low: 0.5
    confidence-threshold-high: 0.8

  # Caching Configuration
  cache:
    enabled: ${REDIS_ENABLED:true}
    ttl-hours: 1
```

### 4.2 Update .env File

Add these environment variables to `.env` file (create if not exists):

```bash
# ===================================
# AI Chatbot Environment Variables
# ===================================

# Feature Flag
CHATBOT_ENABLED=true

# Azure OpenAI Configuration
AZURE_OPENAI_ENDPOINT=https://openai-accounting-chatbot.openai.azure.com/
AZURE_OPENAI_API_KEY=your-azure-openai-api-key-here
AZURE_OPENAI_EMBEDDING_DEPLOYMENT=embedding-ada-002
AZURE_OPENAI_COMPLETION_DEPLOYMENT=gpt-35-turbo
AZURE_OPENAI_MAX_TOKENS=500
AZURE_OPENAI_TEMPERATURE=0.3

# Pinecone Configuration
PINECONE_API_KEY=your-pinecone-api-key-here
PINECONE_ENVIRONMENT=us-east-1-aws
PINECONE_INDEX_NAME=accounting-embeddings

# n8n Configuration
N8N_WEBHOOK_URL=http://localhost:5678/webhook/voucher-embedding
N8N_WEBHOOK_SECRET=your-generated-webhook-secret-here
N8N_ENCRYPTION_KEY=your-generated-encryption-key-here
```

**Security Notes:**
- ⚠️ **NEVER commit .env file to Git**
- Add `.env` to `.gitignore`
- Use Azure Key Vault or AWS Secrets Manager for production
- Rotate API keys quarterly

### 4.3 Update .env.example

Create `.env.example` with placeholder values for documentation:

```bash
# Azure OpenAI Configuration
AZURE_OPENAI_ENDPOINT=https://your-resource-name.openai.azure.com/
AZURE_OPENAI_API_KEY=your-api-key-here
AZURE_OPENAI_EMBEDDING_DEPLOYMENT=embedding-ada-002
AZURE_OPENAI_COMPLETION_DEPLOYMENT=gpt-35-turbo

# Pinecone Configuration
PINECONE_API_KEY=pc-your-api-key-here
PINECONE_ENVIRONMENT=us-east-1-aws
PINECONE_INDEX_NAME=accounting-embeddings

# n8n Configuration
N8N_WEBHOOK_URL=http://localhost:5678/webhook/voucher-embedding
N8N_WEBHOOK_SECRET=generate-with-openssl-rand-base64-32
N8N_ENCRYPTION_KEY=generate-with-openssl-rand-hex-16
```

---

## Part 5: Verification Checklist

### 5.1 Azure OpenAI Verification

- [ ] Resource created and deployed successfully
- [ ] Embedding deployment (text-embedding-ada-002) is active
- [ ] Completion deployment (gpt-35-turbo) is active
- [ ] API key copied and stored securely
- [ ] Test embedding generation (via Azure OpenAI Studio Playground)

### 5.2 Pinecone Verification

- [ ] Account created and email verified
- [ ] Index "accounting-embeddings" created with 1536 dimensions
- [ ] API key copied and stored securely
- [ ] Test connection via curl (returns index list)

### 5.3 n8n Verification

- [ ] n8n container running (docker ps shows "accounting-n8n")
- [ ] n8n UI accessible at http://localhost:5678
- [ ] Voucher Embedding Automation workflow created and activated
- [ ] Webhook endpoint tested with curl (returns 200 OK)
- [ ] Execution log shows successful embedding generation
- [ ] Pinecone vector count increased after test

### 5.4 Application Configuration Verification

- [ ] application.yml updated with chatbot configuration
- [ ] .env file created with all required variables
- [ ] .env added to .gitignore
- [ ] Backend compiles without errors (mvn clean compile)
- [ ] Logs show "Chatbot feature enabled" on startup

---

## Part 6: Troubleshooting

### Issue: Azure OpenAI 401 Unauthorized

**Symptoms:** HTTP 401 error when calling embeddings/completions API

**Solutions:**
1. Verify API key is correct (Keys and Endpoint section in Azure)
2. Check endpoint URL matches your resource (no trailing slash)
3. Ensure api-version parameter is included (2023-05-15)
4. Verify deployment name matches exactly (case-sensitive)

### Issue: Pinecone Connection Timeout

**Symptoms:** Timeout when connecting to Pinecone index

**Solutions:**
1. Check API key is correct and not expired
2. Verify index name exactly matches (case-sensitive: "accounting-embeddings")
3. Ensure environment matches (us-east-1-aws)
4. Check firewall/network allows outbound HTTPS to Pinecone
5. Try curl test to verify connectivity

### Issue: n8n Webhook Not Triggering

**Symptoms:** Voucher post succeeds but no embedding created

**Solutions:**
1. Check n8n container is running: `docker ps | grep n8n`
2. Verify workflow is "Active" (toggle switch in n8n UI)
3. Check webhook secret matches in both n8n and backend .env
4. Review n8n execution logs for errors
5. Test webhook with curl to isolate issue
6. Check backend logs for webhook trigger attempt

### Issue: Embedding Dimensions Mismatch

**Symptoms:** Pinecone error "vector dimension mismatch"

**Solutions:**
1. Verify Pinecone index dimensions: 1536 (for Azure OpenAI ada-002)
2. If wrong, delete and recreate index with correct dimensions
3. Note: Cannot change dimensions of existing index

### Issue: High Azure OpenAI Costs

**Symptoms:** Unexpected high charges

**Solutions:**
1. Enable Redis caching to reduce duplicate calls
2. Reduce max_tokens from 500 to 300
3. Set up Azure Cost Management alerts ($10 threshold)
4. Review query logs to identify expensive queries
5. Consider switching to gpt-35-turbo instead of gpt-4

---

## Part 7: Next Steps

After completing this setup:

1. **Run Backend**: `cd backend && mvn spring-boot:run`
2. **Check Logs**: Verify "Chatbot feature enabled" message
3. **Run Frontend**: `cd frontend && pnpm dev`
4. **Test Integration**: Post a test voucher and verify embedding created
5. **Test Query**: Use chatbot widget to query test voucher

See [Integration Testing Guide](./chatbot-integration-testing.md) for detailed testing procedures.

---

## Appendix A: Cost Monitoring

### Azure OpenAI Cost Dashboard

1. Navigate to Azure Portal → Cost Management + Billing
2. Add filter: Resource = "openai-accounting-chatbot"
3. Set budget alert: $50/month threshold
4. Review daily: Cost Analysis → Daily Costs view

### Expected Costs (100 active users)

| Service | Usage | Cost/Month |
|---------|-------|------------|
| Azure OpenAI Embeddings | 1M tokens | $0.10 |
| Azure OpenAI Completions | 5M tokens | $10.00 |
| Pinecone (Free Tier) | 100K vectors | $0.00 |
| n8n (Self-hosted) | Docker container | $0.00 |
| **Total** | | **~$10.10** |

---

## Appendix B: Security Best Practices

1. **API Key Rotation**
   - Rotate Azure OpenAI keys quarterly
   - Rotate Pinecone keys quarterly
   - Update n8n webhook secret after any security incident

2. **Network Security**
   - Use Azure Private Endpoint for production (removes public internet access)
   - Restrict n8n webhook to internal network only
   - Enable Azure OpenAI virtual network integration

3. **Monitoring**
   - Enable Azure Application Insights for API call logging
   - Set up alerts for failed authentication attempts
   - Monitor for unusual query patterns (potential abuse)

4. **Data Privacy**
   - Exclude sensitive fields from embeddings (passwords, credit cards)
   - Implement data retention policy (auto-delete old queries after 2 years)
   - GDPR compliance: Add user data export/delete endpoints

---

## Support

For issues or questions:
- Backend issues: Check `backend/logs/application.log`
- n8n issues: Check n8n execution logs in UI
- Azure issues: Use Azure Support portal
- Pinecone issues: Check Pinecone status page or support docs

**Last Updated:** 2025-11-24
**Maintained By:** thanhtoan
**Version:** 1.0.0
