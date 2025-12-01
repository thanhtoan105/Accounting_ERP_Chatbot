# n8n Workflows for Accounting RAG Chatbot

**Story 9.0: MVP Voucher-Focused RAG Chatbot**

This folder contains n8n workflow definitions for the accounting RAG chatbot system using **Azure OpenAI** for embeddings/LLM and **Pinecone** for vector storage.

## 🔄 Workflows

### 1. Voucher Embedding Automation (Recommended - n8n Langchain)
- **File:** `embedding-workflow-azure-pinecone.json`
- **n8n ID:** `Cf26BZwsq8r9wuPB`
- **Webhook:** `POST /webhook/voucher-embedding`
- **Purpose:** Embeds voucher data into Pinecone when vouchers are posted
- **Features:**
  - Uses n8n native Langchain nodes (Azure OpenAI Embeddings + Pinecone Vector Store)
  - Automatic payload validation
  - Vietnamese text formatting for better semantic search
  - Company-scoped namespaces (`company_{companyId}`)

### 2. RAG Query Processing (Recommended - n8n Langchain)
- **File:** `rag-chatbot-query-azure-pinecone.json`
- **n8n ID:** `DQaYgnbZ2ryRSwM8`
- **Webhook:** `POST /webhook/chatbot-query`
- **Purpose:** Processes chatbot queries using RAG with Vietnamese accounting context
- **Features:**
  - AI Agent with Vietnamese accounting system prompt
  - Pinecone retrieval as AI tool
  - Window Buffer Memory for conversation context
  - Citation extraction with confidence scoring
  - Response time tracking

### 3. Legacy Workflows (HTTP Request based)
- `voucher-embedding-automation.json` - Uses HTTP Request nodes
- `rag-query-processing.json` - Uses HTTP Request nodes

## 📥 Import Instructions

### Option A: Already Deployed in n8n
The workflows are already created in your n8n instance:
- **Embedding Workflow ID:** `Cf26BZwsq8r9wuPB`
- **Query Workflow ID:** `DQaYgnbZ2ryRSwM8`

Just activate them in the n8n UI!

### Option B: Import from JSON
1. Start n8n: `docker-compose up -d n8n`
2. Open http://localhost:5678
3. Go to **Workflows** → **Import from File**
4. Import the JSON files

## 🔑 Configure Credentials

### 1. Azure OpenAI API
Create credential type: **Azure OpenAI API**
```
API Key: Your Azure OpenAI API key
Resource Name: your-resource-name
API Version: 2024-02-15-preview
```

### 2. Pinecone API
Create credential type: **Pinecone API**
```
API Key: Your Pinecone API key
```

## ⚙️ Pinecone Index Setup

Create a Pinecone index with:
```
Index Name: accounting-embeddings
Dimensions: 1536 (for text-embedding-ada-002)
Metric: cosine
Cloud: AWS
Region: us-east-1 (or your preferred)
```

## 🧪 Test Commands

### Test Embedding Webhook
```bash
curl -X POST http://localhost:5678/webhook/voucher-embedding \
  -H "Content-Type: application/json" \
  -d '{
    "company_id": "test-company-uuid",
    "voucher_id": "VC-12345",
    "header": {
      "voucher_number": "PC-2024-001",
      "voucher_date": "2024-01-15",
      "voucher_type": "PAYMENT",
      "description": "Thanh toán tiền mua hàng cho NCC ABC"
    },
    "lines": [
      {"account_code": "331", "account_name": "Phải trả nhà cung cấp", "debit_amount": 10000000, "credit_amount": 0},
      {"account_code": "111", "account_name": "Tiền mặt", "debit_amount": 0, "credit_amount": 10000000}
    ],
    "summary": {"total_debit": 10000000, "total_credit": 10000000}
  }'
```

### Test Query Webhook
```bash
curl -X POST http://localhost:5678/webhook/chatbot-query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Công nợ phải trả tháng này là bao nhiêu?",
    "company_id": "test-company-uuid",
    "user_id": 1,
    "session_id": "session-123",
    "language": "vi"
  }'
```

### Expected Query Response
```json
{
  "answer": "Tổng công nợ phải trả theo các phiếu...",
  "citations": [
    {
      "entityType": "VOUCHER",
      "entityId": "VC-12345",
      "voucherNumber": "PC-2024-001",
      "excerpt": "Phiếu kế toán số: PC-2024-001...",
      "relevanceScore": 0.85,
      "link": "/vouchers/VC-12345"
    }
  ],
  "confidenceScore": 0.82,
  "confidenceBadge": "HIGH",
  "queryId": "q_session-123_1706123456789",
  "sessionId": "session-123",
  "responseTimeMs": 1234,
  "language": "vi",
  "timestamp": "2024-01-24T10:30:00.000Z"
}
```

## 📊 Workflow Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    EMBEDDING WORKFLOW                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [Webhook] → [Validate] → [Prepare Doc] → [Pinecone Upsert]    │
│                ↓               ↑                                │
│           [Error Handler]  [Azure Embeddings]                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    QUERY WORKFLOW                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [Webhook] → [Validate] → [Extract Params] → [AI Agent]        │
│                ↓                               ↓   ↑            │
│           [Error]                          [Format Response]    │
│                                                ↓                │
│                              [Azure LLM] ←────┤                 │
│                              [Pinecone Tool] ←┤                 │
│                              [Azure Embed] ←──┤                 │
│                              [Memory Buffer] ←┘                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 🔗 Backend Integration

The backend calls these webhooks from:
- `N8nWebhookServiceImpl.java` - Triggers embedding on voucher post
- `ChatbotController.java` - Calls query webhook (or direct RAG)

See `docs/manuals/chatbot-setup-guide.md` for detailed setup instructions.
