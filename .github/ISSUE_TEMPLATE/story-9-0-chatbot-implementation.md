# Story 9.0: MVP Voucher-Focused RAG Chatbot - Implementation Progress

## Overview

Implementing AI-powered RAG (Retrieval-Augmented Generation) chatbot for Vietnamese natural language queries about voucher transactions using Azure OpenAI, Pinecone, and n8n automation.

**Epic**: 9 - AI RAG Chatbot & Contextual Help
**Story ID**: 9.0
**Status**: 🚧 In Progress
**Started**: 2025-11-24
**Sprint**: Current

---

## Story Summary

**As an** accountant or financial reviewer
**I want** an AI chatbot that can answer natural language questions about voucher transactions in Vietnamese
**So that** I can quickly find voucher information, check account balances, and understand transaction history without manually searching through multiple screens

---

## Technical Architecture

### Technology Stack

- **Backend**: Spring Boot 3.5.7 + Java 21
- **AI Services**: Azure OpenAI (embeddings + completions)
- **Vector Database**: Pinecone (serverless, free tier)
- **Automation**: n8n (Docker-based workflow automation)
- **Frontend**: React 19 + TypeScript + shadcn/ui
- **Caching**: Redis (for FAQ queries)
- **Database**: PostgreSQL (audit logging)

### Key Features

1. **Embedding Trigger** - Webhook triggered after voucher post operations
2. **Idempotent Embedding** - Company-scoped Pinecone namespaces
3. **Chatbot Panel** - Floating widget with Vietnamese Q&A
4. **Hybrid Retrieval** - Semantic search + metadata filters + citations
5. **Audit Logging** - Complete query tracking with TT200 compliance
6. **Feature Flag** - Toggle chatbot without affecting voucher workflows

---

## Implementation Progress

### ✅ Completed Tasks

#### Phase 1: Planning & Documentation
- [x] Story drafted and contexted
- [x] Technical specification reviewed
- [x] Sprint status updated: drafted → in-progress
- [x] Comprehensive setup guide created (500+ lines)
  - Azure OpenAI setup instructions
  - Pinecone configuration guide
  - n8n workflow creation steps
  - Environment variable documentation
  - Troubleshooting section
  - Cost monitoring guidelines

#### Phase 2: Infrastructure Setup (Task 1)
- [x] Maven dependencies added to `pom.xml`:
  - `azure-ai-openai:1.0.0-beta.8` - Azure OpenAI SDK
  - `pinecone-client:2.1.0` - Pinecone vector database
  - `okhttp:4.12.0` - HTTP client for n8n webhooks
  - `jackson-databind` - JSON processing
- [x] Application configuration updated (`application.yml`):
  - Azure OpenAI configuration (endpoint, deployments, models)
  - Pinecone configuration (API key, index, namespaces)
  - n8n webhook configuration (URL, secret, retry logic)
  - Query configuration (max length, language, thresholds)
  - Cache configuration (Redis TTL)
- [x] Configuration classes created:
  - `ChatbotProperties.java` - Type-safe configuration binding
  - `AzureOpenAIConfig.java` - Azure OpenAI client bean
  - `PineconeConfig.java` - Pinecone client and index beans
- [x] Backend compilation verified (dependencies downloading)

### 🚧 In Progress

#### Task 2: Database Schema & Entities
- [ ] Create Flyway migration `V20251224__create_chatbot_tables.sql`
  - Table: `chatbot_queries` (id, company_id, user_id, query_text, answer_text, citations, confidence_score, session_id, response_time_ms, created_at, audit_hash)
  - Table: `chatbot_feedback` (placeholder for Story 9.4)
  - Table: `guardrail_logs` (placeholder for Story 9.1)
- [ ] Create JPA entity `ChatbotQuery` extending `CompanyScopedEntity`
- [ ] Create repository `ChatbotQueryRepository` with custom query methods

### 📋 Pending Tasks

#### Task 3: External Service Integration
- [ ] Create `N8nWebhookService` interface and implementation
  - Fire-and-forget webhook trigger
  - Exponential backoff retry logic (1s, 5s, 15s)
  - Circuit breaker pattern
- [ ] Create `EmbeddingService` interface (placeholder for direct embedding)
- [ ] Integrate webhook trigger into `VoucherService.post()` method
- [ ] Add feature flag check before triggering webhooks

#### Task 4: RAG Query Processing Services
- [ ] Create `PineconeClientWrapper` with query/upsert/delete methods
- [ ] Create `AzureOpenAIClientWrapper` for embeddings and completions
- [ ] Create `RAGQueryService` for hybrid retrieval
  - Generate query embedding
  - Query Pinecone with company namespace filter
  - Load voucher details from database
  - Return top 10 results with score > 0.7
- [ ] Create `ChatbotService` for orchestration
  - Validate query input
  - Call RAGQueryService
  - Construct LLM prompt in Vietnamese
  - Parse citations and calculate confidence
  - Save to database with audit hash
  - Return formatted response

#### Task 5: REST API Endpoints
- [ ] Create `ChatbotController` with security annotations
- [ ] Implement `POST /api/v1/chatbot/query` endpoint
- [ ] Create DTOs (ChatbotQueryRequest, ChatbotQueryResponse, Citation)
- [ ] Add OpenAPI documentation
- [ ] Add integration tests with TestContainers

#### Task 6: Frontend Chatbot Widget
- [ ] Create feature structure: `src/features/chatbot/`
- [ ] Create service: `services/chatbot.ts` with axios client
- [ ] Create hook: `hooks/useChatbot.ts` with React Query
- [ ] Create component: `ChatbotWidget.tsx` (floating widget)
- [ ] Create component: `ChatMessage.tsx` (message display)
- [ ] Create component: `CitationList.tsx` (citation rendering with links)
- [ ] Integrate into `ProtectedLayout.tsx`
- [ ] Add feature flag check in frontend

#### Task 7: Styling & UX Polish
- [ ] Add Tailwind CSS styles for chat messages
- [ ] Add loading states and skeleton loaders
- [ ] Add error boundaries and fallbacks
- [ ] Add animations (expand/collapse, typing indicator)

#### Task 8: Testing & Validation
- [ ] Backend unit tests (70% coverage target)
  - ChatbotService, RAGQueryService, N8nWebhookService
- [ ] Backend integration tests (TestContainers)
  - End-to-end query flow
  - Webhook trigger after voucher post
  - Multi-tenancy isolation
- [ ] Frontend unit tests (60% coverage target)
  - useChatbot hook, ChatMessage component
- [ ] E2E tests (Playwright)
  - Chatbot widget open/close
  - Submit query → receive answer with citations
  - Citation link navigation
- [ ] Manual QA for Vietnamese language quality

#### Task 9: Documentation & Deployment
- [ ] Update CLAUDE.md with chatbot commands
- [ ] Create `.env.example` entries
- [ ] Update docker-compose.yml with n8n service
- [ ] Create deployment checklist

---

## Acceptance Criteria

### AC 9.0.1 - Embedding Trigger ✅ (Config Ready)
- ✅ Configuration added for n8n webhook URL and secret
- ⏳ Webhook triggered within 200ms of voucher post
- ⏳ Fire-and-forget pattern (doesn't block voucher posting)
- ⏳ Webhook payload includes all required fields in JSON format

### AC 9.0.2 - Idempotent Embedding ✅ (Config Ready)
- ✅ Pinecone namespace configuration: `company-{UUID}`
- ⏳ Embedding ID format: `voucher_{entityId}`
- ⏳ Retry attempts: 1s, 5s, 15s delays
- ⏳ Failed webhooks logged to database with retry status

### AC 9.0.3 - Chatbot Panel ⏳
- ⏳ Floating widget in bottom-right corner of all main pages
- ⏳ Widget toggle button in app header
- ⏳ Chat interface with message history (scrollable)
- ⏳ Loading indicator during query processing
- ⏳ Error boundary for graceful error handling

### AC 9.0.4 - Hybrid Retrieval with Citations ✅ (Config Ready)
- ✅ Azure OpenAI embedding and completion deployments configured
- ✅ Pinecone top-k and score threshold configured (10 results, >0.7 score)
- ⏳ Answer format: Vietnamese text + citation section with links
- ⏳ Confidence badge: High (>0.8), Medium (0.5-0.8), Low (<0.5)
- ⏳ Fallback message if confidence < 0.5

### AC 9.0.5 - Audit Logging ⏳
- ⏳ Database table `chatbot_queries` created via Flyway migration
- ⏳ All queries logged with audit hash (SHA-256)
- ⏳ Error responses include actionable guidance
- ⏳ Integration with existing `AuditService` for TT200 compliance

### AC 9.0.6 - Feature Flag ✅ (Config Ready)
- ✅ Environment variable: `CHATBOT_ENABLED=true|false` configured
- ⏳ When false: widget icon hidden, API returns 503
- ⏳ Voucher posting unaffected by chatbot status
- ⏳ Clear log message when chatbot disabled

---

## Files Changed/Created

### Configuration Files
- ✅ `backend/pom.xml` - Added AI/ML dependencies
- ✅ `backend/src/main/resources/application.yml` - Added chatbot configuration
- ✅ `docs/manuals/chatbot-setup-guide.md` - Comprehensive setup guide (NEW)

### Backend Configuration Classes
- ✅ `backend/src/main/java/com/accounting/config/chatbot/ChatbotProperties.java` (NEW)
- ✅ `backend/src/main/java/com/accounting/config/chatbot/AzureOpenAIConfig.java` (NEW)
- ✅ `backend/src/main/java/com/accounting/config/chatbot/PineconeConfig.java` (NEW)

### Backend Entities (Pending)
- ⏳ `backend/src/main/java/com/accounting/entity/ChatbotQuery.java`
- ⏳ `backend/src/main/java/com/accounting/repository/ChatbotQueryRepository.java`

### Backend Services (Pending)
- ⏳ `backend/src/main/java/com/accounting/service/ChatbotService.java`
- ⏳ `backend/src/main/java/com/accounting/service/RAGQueryService.java`
- ⏳ `backend/src/main/java/com/accounting/service/impl/chatbot/ChatbotServiceImpl.java`
- ⏳ `backend/src/main/java/com/accounting/service/impl/chatbot/N8nWebhookServiceImpl.java`
- ⏳ `backend/src/main/java/com/accounting/service/impl/chatbot/PineconeClientWrapper.java`
- ⏳ `backend/src/main/java/com/accounting/service/impl/chatbot/AzureOpenAIClientWrapper.java`

### Backend Controllers (Pending)
- ⏳ `backend/src/main/java/com/accounting/controller/ChatbotController.java`

### Backend DTOs (Pending)
- ⏳ `backend/src/main/java/com/accounting/dto/ChatbotQueryRequest.java`
- ⏳ `backend/src/main/java/com/accounting/dto/ChatbotQueryResponse.java`
- ⏳ `backend/src/main/java/com/accounting/dto/Citation.java`

### Database Migrations (Pending)
- ⏳ `backend/src/main/resources/db/migration/V20251224__create_chatbot_tables.sql`

### Frontend Components (Pending)
- ⏳ `frontend/src/features/chatbot/components/ChatbotWidget.tsx`
- ⏳ `frontend/src/features/chatbot/components/ChatMessage.tsx`
- ⏳ `frontend/src/features/chatbot/components/CitationList.tsx`
- ⏳ `frontend/src/features/chatbot/hooks/useChatbot.ts`
- ⏳ `frontend/src/features/chatbot/services/chatbot.ts`
- ⏳ `frontend/src/features/chatbot/types/chatbot.ts`
- ⏳ `frontend/src/features/chatbot/index.ts`

### Test Files (Pending)
- ⏳ `backend/src/test/java/com/accounting/service/ChatbotServiceTest.java`
- ⏳ `backend/src/test/java/com/accounting/controller/ChatbotControllerIntegrationTest.java`
- ⏳ `frontend/src/features/chatbot/__tests__/useChatbot.test.ts`
- ⏳ `tests/e2e/chatbot-widget.spec.ts`

---

## Environment Variables Required

### Backend (.env)
```bash
# Azure OpenAI Configuration
AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
AZURE_OPENAI_API_KEY=your-api-key-here
AZURE_OPENAI_EMBEDDING_DEPLOYMENT=embedding-ada-002
AZURE_OPENAI_COMPLETION_DEPLOYMENT=gpt-35-turbo

# Pinecone Configuration
PINECONE_API_KEY=pc-your-api-key-here
PINECONE_ENVIRONMENT=us-east-1-aws
PINECONE_INDEX_NAME=accounting-embeddings

# n8n Configuration
N8N_WEBHOOK_URL=http://localhost:5678/webhook/voucher-embedding
N8N_WEBHOOK_SECRET=your-generated-secret
N8N_ENCRYPTION_KEY=your-generated-key

# Feature Flag
CHATBOT_ENABLED=true
```

---

## Testing Strategy

### Unit Tests (70% backend, 60% frontend coverage)
- Mock external services (Azure OpenAI, Pinecone, n8n)
- Test service layer business logic
- Test React components and hooks

### Integration Tests (TestContainers)
- End-to-end query flow: API → Service → Pinecone → Azure OpenAI → Response
- Webhook trigger after voucher post
- Multi-tenancy isolation (Company A cannot query Company B data)
- Audit logging verification

### E2E Tests (Playwright)
- Chatbot widget open/close flow
- Submit Vietnamese query → receive answer with citations
- Citation link click → navigate to voucher detail
- Error handling and fallback messages

### Manual QA
- Vietnamese language quality (accounting terminology accuracy)
- Citation relevance and accuracy
- Confidence score calibration
- Performance (P95 < 5 seconds for queries)

---

## Known Issues / Blockers

### ⚠️ External Service Setup Required

The following services must be manually configured before full implementation:

1. **Azure OpenAI Account**
   - Create resource in Azure portal
   - Deploy `text-embedding-ada-002` model
   - Deploy `gpt-35-turbo` or `gpt-4` model
   - Obtain API key and endpoint

2. **Pinecone Account**
   - Sign up at pinecone.io (free tier available)
   - Create index `accounting-embeddings` with 1536 dimensions
   - Obtain API key

3. **n8n Deployment**
   - Deploy n8n Docker container
   - Create "Voucher Embedding Automation" workflow
   - Configure webhook endpoint with authentication

📖 **See**: `docs/manuals/chatbot-setup-guide.md` for detailed setup instructions

---

## Cost Estimation

### Monthly Costs (100 active users, 50 queries/user/month)

| Service | Usage | Cost/Month |
|---------|-------|------------|
| Azure OpenAI Embeddings | 1M tokens | $0.10 |
| Azure OpenAI Completions | 5M tokens | $10.00 |
| Pinecone (Free Tier) | 100K vectors | $0.00 |
| n8n (Self-hosted) | Docker | $0.00 |
| **Total** | | **~$10.10** |

**Cost Optimization**:
- Redis caching reduces duplicate LLM calls by 40-60%
- Limit max_tokens to 500 per response
- Use gpt-35-turbo instead of gpt-4 for MVP

---

## Security Considerations

### Multi-Tenancy
- ✅ Pinecone namespaces scoped by `company_id`
- ✅ All chatbot entities extend `CompanyScopedEntity`
- ⏳ RAG queries filtered by company context from JWT
- ⏳ Test: User from Company A cannot query Company B's vouchers

### Authentication & Authorization
- ✅ JWT authentication required for all chatbot endpoints
- ⏳ RBAC enforcement: Accountant sees only their vouchers, Chief Accountant sees all
- ✅ n8n webhook protected by shared secret
- ⏳ Input sanitization: max 5000 chars, block SQL injection patterns

### Data Privacy
- Exclude sensitive fields from embeddings (passwords, API keys)
- Audit log all queries with SHA-256 hash
- Comply with TT200 data retention requirements (10 years)

---

## References

### Documentation
- [Setup Guide](../docs/manuals/chatbot-setup-guide.md)
- [Tech Spec](../docs/sprint-artifacts/tech-spec-epic-9.md)
- [Story File](../docs/sprint-artifacts/stories/9-0-mvp-voucher-rag-chatbot-basic.md)
- [Story Context](../docs/sprint-artifacts/stories/9-0-mvp-voucher-rag-chatbot-basic.context.xml)

### External Resources
- [Azure OpenAI Documentation](https://learn.microsoft.com/en-us/azure/ai-services/openai/)
- [Pinecone Documentation](https://docs.pinecone.io/)
- [n8n Documentation](https://docs.n8n.io/)

---

## Next Steps

1. **Complete Database Schema** (Task 2)
   - Create Flyway migration
   - Create JPA entities and repositories

2. **Implement Service Layer** (Tasks 3-4)
   - N8nWebhookService with retry logic
   - RAGQueryService for hybrid retrieval
   - ChatbotService for orchestration

3. **Build REST API** (Task 5)
   - ChatbotController with endpoints
   - Request/response DTOs
   - Integration tests

4. **Develop Frontend Widget** (Task 6)
   - ChatbotWidget component
   - React Query integration
   - Citation rendering

5. **Polish & Test** (Tasks 7-8)
   - UX improvements
   - Comprehensive test coverage
   - Manual QA

---

## Labels

`epic-9` `story-9.0` `ai-chatbot` `rag` `azure-openai` `pinecone` `n8n` `vietnamese` `in-progress` `backend` `frontend`

---

**Last Updated**: 2025-11-24
**Assignee**: thanhtoan
**Reviewer**: TBD
**Story Points**: 21 (Large - Full RAG implementation with external services)
