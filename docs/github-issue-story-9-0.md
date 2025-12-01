# [Story 9.0] MVP Voucher-Focused RAG Chatbot - Implementation Progress

## 📋 Summary

Implementing AI-powered RAG chatbot for Vietnamese natural language queries about voucher transactions.

**Tech Stack**: Spring Boot + Azure OpenAI + Pinecone + n8n + React
**Status**: 🚧 In Progress (Infrastructure setup phase completed)
**Started**: 2025-11-24

## 🎯 User Story

**As an** accountant or financial reviewer
**I want** an AI chatbot that can answer natural language questions about voucher transactions in Vietnamese
**So that** I can quickly find voucher information without manually searching through multiple screens

## ✅ Completed (Phase 1: Infrastructure)

### Documentation
- [x] Comprehensive setup guide created (500+ lines)
  - Azure OpenAI setup with embedding + completion models
  - Pinecone vector database configuration
  - n8n workflow automation steps
  - Environment variables and troubleshooting

### Backend Configuration
- [x] Maven dependencies added:
  - `azure-ai-openai:1.0.0-beta.8`
  - `pinecone-client:2.1.0`
  - `okhttp:4.12.0`
- [x] `application.yml` configuration:
  - Azure OpenAI (endpoint, deployments, models)
  - Pinecone (API key, index, namespaces)
  - n8n webhook (URL, secret, retry logic)
- [x] Configuration classes:
  - `ChatbotProperties.java` - Type-safe config
  - `AzureOpenAIConfig.java` - Azure OpenAI client bean
  - `PineconeConfig.java` - Pinecone client and index beans

### Verification
- [x] Backend compiles successfully with new dependencies
- [x] Sprint status updated: drafted → in-progress

## 🚧 In Progress (Phase 2: Database)

- [ ] Create Flyway migration `V20251224__create_chatbot_tables.sql`
- [ ] Create `ChatbotQuery` entity extending `CompanyScopedEntity`
- [ ] Create `ChatbotQueryRepository` with custom methods

## 📋 Upcoming Tasks

### Phase 3: Service Layer (Tasks 3-4)
- [ ] `N8nWebhookService` - Fire-and-forget webhook with retry
- [ ] `RAGQueryService` - Hybrid retrieval (Pinecone + metadata filters)
- [ ] `ChatbotService` - Orchestration with LLM prompts

### Phase 4: REST API (Task 5)
- [ ] `ChatbotController` with security
- [ ] `POST /api/v1/chatbot/query` endpoint
- [ ] DTOs and integration tests

### Phase 5: Frontend (Task 6)
- [ ] `ChatbotWidget.tsx` - Floating widget
- [ ] `useChatbot.ts` hook with React Query
- [ ] `CitationList.tsx` - Citation rendering

### Phase 6: Polish & Testing (Tasks 7-8)
- [ ] UX improvements and animations
- [ ] Unit tests (70% backend, 60% frontend)
- [ ] E2E tests with Playwright
- [ ] Vietnamese language QA

## 🔐 Environment Variables Needed

```bash
# Azure OpenAI
AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
AZURE_OPENAI_API_KEY=***
AZURE_OPENAI_EMBEDDING_DEPLOYMENT=embedding-ada-002
AZURE_OPENAI_COMPLETION_DEPLOYMENT=gpt-35-turbo

# Pinecone
PINECONE_API_KEY=pc-***
PINECONE_ENVIRONMENT=us-east-1-aws
PINECONE_INDEX_NAME=accounting-embeddings

# n8n
N8N_WEBHOOK_URL=http://localhost:5678/webhook/voucher-embedding
N8N_WEBHOOK_SECRET=***

# Feature Flag
CHATBOT_ENABLED=true
```

## ⚠️ Blockers

### External Services Setup Required:
1. **Azure OpenAI** - Create resource, deploy models, get API key
2. **Pinecone** - Create account, create index (1536 dimensions)
3. **n8n** - Deploy Docker, create embedding workflow

📖 **Setup Guide**: `docs/manuals/chatbot-setup-guide.md`

## 💰 Cost Estimate

~$10/month for 100 active users (50 queries each):
- Azure OpenAI: $10.00
- Pinecone (free tier): $0.00
- n8n (self-hosted): $0.00

## 🎯 Acceptance Criteria Progress

| AC | Description | Status |
|----|-------------|--------|
| 9.0.1 | Embedding trigger (webhook after voucher post) | ⚙️ Config ready |
| 9.0.2 | Idempotent embedding (company namespaces) | ⚙️ Config ready |
| 9.0.3 | Chatbot panel (floating widget) | ⏳ Pending |
| 9.0.4 | Hybrid retrieval with citations | ⚙️ Config ready |
| 9.0.5 | Audit logging | ⏳ Pending |
| 9.0.6 | Feature flag | ✅ Complete |

## 📂 Files Modified

### Created
- `docs/manuals/chatbot-setup-guide.md`
- `backend/src/main/java/com/accounting/config/chatbot/ChatbotProperties.java`
- `backend/src/main/java/com/accounting/config/chatbot/AzureOpenAIConfig.java`
- `backend/src/main/java/com/accounting/config/chatbot/PineconeConfig.java`

### Modified
- `backend/pom.xml` (added AI dependencies)
- `backend/src/main/resources/application.yml` (added chatbot config)
- `docs/sprint-artifacts/sprint-status.yaml` (updated story status)

## 📚 References

- [Tech Spec](docs/sprint-artifacts/tech-spec-epic-9.md)
- [Story File](docs/sprint-artifacts/stories/9-0-mvp-voucher-rag-chatbot-basic.md)
- [Setup Guide](docs/manuals/chatbot-setup-guide.md)

## 🏷️ Labels

`epic-9` `story-9.0` `ai-chatbot` `rag` `azure-openai` `pinecone` `in-progress` `backend` `frontend`

---

**Next Steps**: Complete database schema (Task 2) and begin service layer implementation (Tasks 3-4)
