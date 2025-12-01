# Validation Report: Story 9.0 Context

**Document:** `docs/sprint-artifacts/stories/9-0-mvp-voucher-rag-chatbot-basic.context.xml`
**Checklist:** `.bmad/bmm/workflows/4-implementation/story-context/checklist.md`
**Date:** 2025-11-24
**Validator:** BMAD Story Context Validation Workflow

---

## Summary

**Overall: 10/10 passed (100%)**
**Critical Issues: 0**
**Status:** ✅ **READY FOR DEVELOPMENT**

### Pass Rate by Category
- Story Structure: 3/3 (100%)
- Documentation & Artifacts: 4/4 (100%)
- Technical Specifications: 3/3 (100%)

---

## Detailed Results

### ✓ PASS - Story fields (asA/iWant/soThat) captured

**Evidence:** Lines 13-15
```xml
<asA>an accountant or financial reviewer</asA>
<iWant>an AI chatbot that can answer natural language questions about voucher transactions in Vietnamese</iWant>
<soThat>I can quickly find voucher information, check account balances, and understand transaction history without manually searching through multiple screens</soThat>
```

All three story fields are present and match the source story file exactly.

---

### ✓ PASS - Acceptance criteria list matches story draft exactly (no invention)

**Evidence:** Lines 74-149

All 6 acceptance criteria captured accurately with success criteria:
- AC 9.0.1 - Embedding Trigger (webhook trigger within 200ms, fire-and-forget pattern)
- AC 9.0.2 - Idempotent Embedding (Pinecone namespace scoping, retry logic with exponential backoff)
- AC 9.0.3 - Chatbot Panel (floating widget, Vietnamese queries, loading indicators)
- AC 9.0.4 - Hybrid Retrieval with Citations (semantic search + metadata filters, confidence scoring)
- AC 9.0.5 - Audit Logging (chatbot_queries table, SHA-256 audit hash, AuditService integration)
- AC 9.0.6 - Feature Flag (CHATBOT_ENABLED environment variable, UI widget toggle)

No invention or modification detected. Perfect fidelity to source story.

---

### ✓ PASS - Tasks/subtasks captured as task list

**Evidence:** Lines 16-71

Comprehensive task breakdown with 9 main tasks, all mapped to acceptance criteria:
1. Setup Infrastructure and External Services (Pinecone, OpenAI, n8n, application.yml config)
2. Backend - Database Schema and Entities (Flyway migration, JPA entities)
3. Backend - External Service Integration (N8nWebhookService, EmbeddingService placeholder)
4. Backend - RAG Query Processing Services (PineconeClientWrapper, OpenAIClientWrapper, RAGQueryService, ChatbotService)
5. Backend - REST API Endpoints (ChatbotController, DTOs, integration tests)
6. Frontend - Chatbot Widget Component (feature structure, hooks, components, routing)
7. Frontend - Styling and UX Polish (Tailwind styles, loading states, error boundaries)
8. Testing and Validation (unit, integration, E2E, manual QA)
9. Documentation and Deployment Preparation (CLAUDE.md updates, setup manual, .env.example)

Tasks represent all major work items from the source story with proper AC traceability.

---

### ✓ PASS - Relevant docs (5-15) included with path and snippets

**Evidence:** Lines 152-213

**10 documentation artifacts** (within recommended 5-15 range):

1. **Epic 9 Tech Spec - Overview & Architecture**
   Path: `docs/sprint-artifacts/tech-spec-epic-9.md`
   Snippet: "Comprehensive technical specification covering chatbot architecture, n8n automation pipelines, Pinecone vector database integration, and RAG query processing..."

2. **Epic 9 Tech Spec - Services and Modules**
   Path: `docs/sprint-artifacts/tech-spec-epic-9.md`
   Snippet: "Defines ChatbotController, ChatbotService, EmbeddingService, RAGQueryService, N8nWebhookService, GuardrailService with responsibilities, inputs, outputs..."

3. **Epic 9 Tech Spec - Data Models - Entities**
   Path: `docs/sprint-artifacts/tech-spec-epic-9.md`
   Snippet: "JPA entities: ChatbotQuery extends CompanyScopedEntity (id, userId, queryText, answerText, citations JSONB, confidenceScore, sessionId...)..."

4. **Epic 9 Tech Spec - APIs and Interfaces**
   Path: `docs/sprint-artifacts/tech-spec-epic-9.md`
   Snippet: "REST endpoints: POST /api/v1/chatbot/query (ChatbotQueryRequest → ChatbotQueryResponse with answer, citations, confidenceScore)..."

5. **Epic 9 Tech Spec - Query Processing Flow**
   Path: `docs/sprint-artifacts/tech-spec-epic-9.md`
   Snippet: "13-step flow: User query → JWT validation → GuardrailService → RAGQueryService generates embedding → Pinecone query with metadata filters..."

6. **Epic 9 Tech Spec - Embedding Automation Flow**
   Path: `docs/sprint-artifacts/tech-spec-epic-9.md`
   Snippet: "n8n pipeline triggered after VoucherService.post(): fire-and-forget webhook → n8n validates payload → formats text for embedding → OpenAI Embedding API..."

7. **Epic 9 Tech Spec - Security NFRs**
   Path: `docs/sprint-artifacts/tech-spec-epic-9.md`
   Snippet: "JWT authentication for all chatbot endpoints. RBAC enforcement: Accountant sees only their vouchers (created_by filter), Chief Accountant sees all company data..."

8. **Epic 9 Tech Spec - Performance NFRs**
   Path: `docs/sprint-artifacts/tech-spec-epic-9.md`
   Snippet: "Target query response time P95 < 5 seconds (Pinecone + OpenAI). Fire-and-forget webhook < 200ms. Redis cache for FAQ queries (1-hour TTL)..."

9. **Epic 8 Tech Spec - Metabase Integration Patterns**
   Path: `docs/sprint-artifacts/tech-spec-epic-8.md`
   Snippet: "Story 8-0 demonstrates external service integration pattern: MetabaseService interface + implementation, JWT SSO with 10-minute token expiration..."

10. **Story 9.0 Dev Notes - Learnings from Story 8-0**
    Path: `docs/sprint-artifacts/stories/9-0-mvp-voucher-rag-chatbot-basic.md`
    Snippet: "Reusable patterns: Service interface + implementation (MetabaseService/MetabaseServiceImpl), JWT token handling with JJWT library and modern API..."

All entries include project-relative paths, titles, section names, and concise 2-3 sentence snippets with no invention.

---

### ✓ PASS - Relevant code references included with reason and line hints

**Evidence:** Lines 214-278

**9 code artifacts** with comprehensive metadata:

1. **MetabaseService** (interface)
   Path: `backend/src/main/java/com/accounting/service/MetabaseService.java`
   Lines: 8-35
   Reason: "Reference implementation pattern for external service integration. ChatbotService, EmbeddingService, RAGQueryService should follow this interface + implementation pattern..."

2. **MetabaseServiceImpl** (service-impl)
   Path: `backend/src/main/java/com/accounting/service/impl/MetabaseServiceImpl.java`
   Lines: 20-80
   Reason: "JWT token generation pattern using JJWT library with modern API (Jwts.builder().claims().expiration().signWith()). Demonstrates @Value property injection..."

3. **AnalyticsController** (controller)
   Path: `backend/src/main/java/com/accounting/controller/AnalyticsController.java`
   Lines: 14-65
   Reason: "REST controller pattern with @PreAuthorize for RBAC enforcement. Demonstrates role-based access control with hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')..."

4. **VoucherService.postSalesInvoiceVoucher** (interface)
   Path: `backend/src/main/java/com/accounting/service/VoucherService.java`
   Lines: 91-101
   Reason: "Integration point for n8n webhook trigger. After voucher post operation, ChatbotService should trigger N8nWebhookService.triggerEmbedding()..."

5. **CompanyScopedEntity** (interface)
   Path: `backend/src/main/java/com/accounting/repository/CompanyScopedEntity.java`
   Lines: 3-5
   Reason: "All chatbot entities (ChatbotQuery, ChatbotFeedback, GuardrailLog) MUST extend CompanyScopedEntity for automatic company-scoped filtering..."

6. **JwtAuthenticationFilter** (filter)
   Path: `backend/src/main/java/com/accounting/security/JwtAuthenticationFilter.java`
   Lines: 21-81
   Reason: "JWT token extraction and validation pattern. Demonstrates how to extract userId and role from JWT token using JwtTokenProvider..."

7. **SecurityConfig** (config)
   Path: `backend/src/main/java/com/accounting/config/SecurityConfig.java`
   Lines: 20-76
   Reason: "Security configuration with JWT filter chain. All /api/v1/chatbot/* endpoints will be automatically protected by JwtAuthenticationFilter..."

8. **Analytics feature barrel export** (barrel-export)
   Path: `frontend/src/features/analytics/index.ts`
   Lines: 1-4
   Reason: "Barrel export pattern for feature modules. Chatbot feature should follow same structure: frontend/src/features/chatbot/index.ts exporting pages, components, services, types..."

9. **Analytics service** (service)
   Path: `frontend/src/features/analytics/services/analytics.ts`
   Lines: 1-33
   Reason: "Axios service pattern for API calls. Chatbot service (frontend/src/features/chatbot/services/chatbot.ts) should follow same pattern with TypeScript interfaces..."

Each artifact includes project-relative path, kind, symbol name, line range, and detailed reason explaining relevance to the story.

---

### ✓ PASS - Interfaces/API contracts extracted if applicable

**Evidence:** Lines 321-385

**9 interface definitions:**

1. **POST /api/v1/chatbot/query** (REST endpoint)
   Signature: `ChatbotQueryRequest → ChatbotQueryResponse`
   Details: Request: { query, sessionId, language?, contextFilters? }. Response: { answer, citations, confidenceScore, queryId, responseTimeMs }. JWT required, RBAC enforced.

2. **POST /api/v1/chatbot/feedback** (REST endpoint)
   Signature: `FeedbackRequest → 200 OK`
   Details: Request: { queryId, feedbackType, comment? }. Saves to chatbot_feedback table. JWT required.

3. **GET /api/v1/chatbot/history** (REST endpoint)
   Signature: `Query params: sessionId, limit → ChatbotQuery[]`
   Details: Retrieve user's chat history filtered by sessionId. JWT required, user can only access their own sessions.

4. **DELETE /api/v1/chatbot/session/{sessionId}** (REST endpoint)
   Signature: `sessionId → 204 No Content`
   Details: Clear a chat session by sessionId. Soft-delete or mark as inactive. JWT required.

5. **POST /api/v1/webhooks/n8n/embedding** (Webhook endpoint)
   Signature: `EmbeddingPayload → 200 OK`
   Details: Internal webhook called by backend to trigger n8n embedding automation. Protected by N8N_WEBHOOK_SECRET shared secret (not JWT).

6. **ChatbotService.processQuery** (Service method)
   Signature: `(queryText, userId, companyId, sessionId) → ChatbotQueryResponse`
   Details: Orchestrates RAG query processing: validate → retrieve → construct prompt → OpenAI completion → parse → save → audit log → return.

7. **RAGQueryService.retrieveRelevantVouchers** (Service method)
   Signature: `(query, companyId, filters) → List<RetrievedDocument>`
   Details: Hybrid retrieval: generate embedding → query Pinecone with namespace filter → retrieve top 10 results (score > 0.7) → load voucher details.

8. **N8nWebhookService.triggerEmbedding** (Service method)
   Signature: `(VoucherEmbeddingPayload) → CompletableFuture<Void>`
   Details: Fire-and-forget webhook trigger with retry logic (3 attempts: 1s, 5s, 15s). Non-blocking async operation.

9. **CompanyScopedEntity.getCompanyId** (Interface method)
   Signature: `() → Long`
   Details: All chatbot entities must implement this interface. Used by CompanyScopeAspect for automatic company filtering.

Each interface includes name, kind, signature, path, and detailed implementation notes.

---

### ✓ PASS - Constraints include applicable dev rules and patterns

**Evidence:** Lines 312-320

**6 comprehensive constraint categories:**

1. **Multi-tenancy** (line 313)
   - All chatbot entities MUST extend CompanyScopedEntity for automatic company filtering
   - Pinecone namespaces MUST be scoped by company_id (format: `company_{UUID}`)
   - RAG queries MUST filter by company context extracted from JWT token
   - Test multi-tenant isolation: User from Company A cannot query Company B's vouchers

2. **Security** (line 314)
   - JWT authentication required for all chatbot endpoints
   - RBAC enforcement: Accountant sees only their vouchers (created_by filter), Chief Accountant sees all company data
   - Input sanitization: Max query length 5000 chars, block SQL injection patterns
   - Webhook authentication: n8n webhook protected by shared secret (N8N_WEBHOOK_SECRET)
   - Exclude sensitive fields from embeddings: password_hash, refresh_token, api_keys

3. **Performance** (line 315)
   - Fire-and-forget webhook trigger (< 200ms, non-blocking)
   - Target query response time: P95 < 5 seconds (Pinecone + OpenAI)
   - Rate limiting: 20 queries per user per minute
   - Redis caching for frequently asked questions (1-hour TTL)
   - Support 20 concurrent queries per company

4. **Testing** (line 316)
   - Unit tests: 70% coverage for backend chatbot package (JUnit 5 + Mockito)
   - Integration tests: End-to-end flows with TestContainers (PostgreSQL, Redis)
   - E2E tests: Playwright for chatbot widget UI interactions
   - Manual QA: Vietnamese language quality assessment

5. **Code structure - Backend** (line 317)
   Complete package structure specified:
   - `com.accounting.controller.ChatbotController`
   - `com.accounting.service.{ChatbotService, EmbeddingService, RAGQueryService}`
   - `com.accounting.service.impl.chatbot.{ChatbotServiceImpl, N8nWebhookServiceImpl, PineconeClientWrapper, OpenAIClientWrapper}`
   - `com.accounting.dto.{ChatbotQueryRequest, ChatbotQueryResponse, Citation}`
   - `com.accounting.entity.{ChatbotQuery, ChatbotFeedback, GuardrailLog}`
   - `com.accounting.repository.ChatbotQueryRepository`

6. **Code structure - Frontend** (line 318)
   Feature structure specified:
   - `frontend/src/features/chatbot/{components, hooks, services, types, index.ts}`
   - Components: ChatbotWidget, ChatMessage, CitationList
   - Hooks: useChatbot (React Query + state management)
   - Services: chatbot.ts (axios API client)
   - Types: chatbot.ts (TypeScript interfaces)
   - Barrel export: index.ts

7. **Database** (line 319)
   - Migration: `V20251224__create_chatbot_tables.sql` creates chatbot_queries, chatbot_feedback, guardrail_logs
   - JSONB columns for citations and context
   - Indexes required on company_id, user_id, session_id, created_at

All constraints are specific, measurable, and directly applicable to implementation.

---

### ✓ PASS - Dependencies detected from manifests and frameworks

**Evidence:** Lines 279-309

Comprehensive dependency breakdown across three categories:

**Backend dependencies (lines 280-290):** 10 items
- 7 existing: spring-boot-starter-web (3.5.7), spring-boot-starter-data-jpa (3.5.7), spring-boot-starter-data-redis (3.5.7), spring-boot-starter-security (3.5.7), postgresql (42.7.4), flyway-core (11.10.0), jjwt-api (0.12.5)
- 3 NEW to add: pinecone-client (0.7.2+) in Task 4.1, openai-java (0.18.0+) in Task 4.2, okhttp (4.12.0+) in Task 3.1

**Frontend dependencies (lines 291-303):** 11 items
- 9 existing: react (19.1.1), react-router-dom (7.9.5), @tanstack/react-query (5.62.0), axios (1.7.9), @radix-ui/react-dialog (1.1.15), lucide-react (0.552.0), tailwindcss (4.1.16), zod (4.1.12)
- 2 NEW to add: react-markdown (9.0.0+) in Task 6.5, @tailwindcss/typography (0.5.13+) in Task 7.1

**External services (lines 304-308):** 3 items
- **Pinecone:** Vector database for embeddings storage. Serverless free tier. API Key: PINECONE_API_KEY (env var). Environment: us-east-1-aws. Index: accounting-embeddings. Dimensions: 1536 (OpenAI ada-002). Metric: cosine similarity.
- **OpenAI:** Embedding generation and LLM completions. Pay-as-you-go. API Key: OPENAI_API_KEY (env var). Embedding model: text-embedding-ada-002. Completion model: gpt-3.5-turbo. Max tokens: 2000 per response. Temperature: 0.3.
- **n8n:** Automation workflow for embedding pipeline. Self-hosted Docker. Webhook URL: N8N_WEBHOOK_URL (env var). Webhook secret: N8N_WEBHOOK_SECRET (env var). Workflow: "Voucher Embedding Automation". Retry policy: 3 attempts with exponential backoff.

All dependencies include version ranges and clear indication of existing vs. to-be-added status with task references.

---

### ✓ PASS - Testing standards and locations populated

**Evidence:** Lines 386-468

**Standards section (lines 387-416):**
- **Backend (JUnit 5 + Mockito + TestContainers):** Unit tests with mocked dependencies. Target 70% code coverage. Test structure mirrors src/main/java. Naming: *Test.java for unit, *IntegrationTest.java for integration. Assertions with AssertJ.
- **Frontend (Vitest + Testing Library + jsdom):** Component tests with mocked API. Test structure in __tests__ directories. Naming: *.test.tsx for components, *.test.ts for services. Target 60% coverage.
- **E2E (Playwright):** Full user flows in tests/e2e/ directory. Naming: *.spec.ts. Fixtures for authentication and test data.
- **Manual QA:** Vietnamese language quality assessment for accounting terminology accuracy. Hallucination detection. Citation quality verification. Usability testing.

**Locations section (lines 417-424):**
- Backend unit tests: `backend/src/test/java/com/accounting/service/{ChatbotServiceTest, RAGQueryServiceTest, N8nWebhookServiceTest}`
- Backend integration tests: `backend/src/test/java/com/accounting/controller/ChatbotControllerIntegrationTest`
- Frontend unit tests: `frontend/src/features/chatbot/__tests__/{useChatbot.test.ts, ChatMessage.test.tsx, CitationList.test.tsx}`
- Frontend component tests: `frontend/src/features/chatbot/components/__tests__/ChatbotWidget.test.tsx`
- E2E tests: `tests/e2e/chatbot-widget.spec.ts`, `tests/e2e/chatbot-query-flow.spec.ts`
- API tests: `tests/api/chatbot-api.spec.ts`

**Test ideas section (lines 425-468):** 28+ specific test scenarios:
- **AC 9.0.1 (Embedding Trigger):** 3 test ideas - payload construction, integration test with 200ms timing, fire-and-forget pattern
- **AC 9.0.2 (Idempotent Embedding):** 3 test ideas - idempotency check, retry logic with exponential backoff, failure logging
- **AC 9.0.3 (Chatbot Panel):** 4 test ideas - widget rendering on all pages, Vietnamese query submission, component state management, error boundary
- **AC 9.0.4 (Hybrid Retrieval):** 4 test ideas - query with citations and confidence, metadata filtering, no results edge case, citation link navigation
- **AC 9.0.5 (Audit Logging):** 4 test ideas - database entry verification, audit hash generation (SHA-256), AuditService integration, error handling with guidance
- **AC 9.0.6 (Feature Flag):** 3 test ideas - CHATBOT_ENABLED=false behavior, voucher posting unaffected, clear log messages
- **Multi-tenancy isolation:** 3 test ideas - company namespace filtering, RBAC enforcement (Accountant vs Chief Accountant), cross-company leak test
- **Vietnamese language quality:** 3 test ideas - various query types ("Công nợ phải trả?", "Phiếu chi tháng 11?", "Tổng thanh toán NCC ABC?"), accounting terminology accuracy, confidence score calibration

All test ideas are specific, measurable, and directly traceable to acceptance criteria.

---

### ✓ PASS - XML structure follows story-context template format

**Evidence:** Lines 1-470

Document structure matches template exactly:
- **Line 1:** Root element `<story-context id=".bmad/bmm/workflows/4-implementation/story-context/template" v="1.0">`
- **Lines 2-10:** `<metadata>` section with epicId (9), storyId (0), title, status (in-progress), generatedAt (2025-11-24), generator, sourceStoryPath
- **Lines 12-72:** `<story>` section with asA, iWant, soThat, and tasks
- **Lines 74-149:** `<acceptanceCriteria>` section with all 6 ACs
- **Lines 151-310:** `<artifacts>` section with docs (10 items), code (9 items), and dependencies (backend, frontend, external-services)
- **Lines 312-320:** `<constraints>` section with 7 constraint categories
- **Lines 321-385:** `<interfaces>` section with 9 interface definitions
- **Lines 386-470:** `<tests>` section with standards, locations, and ideas subsections
- **Line 470:** Closing `</story-context>` tag

All template placeholders properly replaced with actual content:
- ✓ `{{epic_id}}` → 9
- ✓ `{{story_id}}` → 0
- ✓ `{{story_title}}` → MVP Voucher-Focused RAG Chatbot
- ✓ `{{story_status}}` → in-progress
- ✓ `{{date}}` → 2025-11-24
- ✓ `{{story_path}}` → docs/sprint-artifacts/stories/9-0-mvp-voucher-rag-chatbot-basic.md
- ✓ All content sections populated (no empty placeholders like `{{docs_artifacts}}`)

No structural deviations detected. Perfect template compliance.

---

## Failed Items

**None**

---

## Partial Items

**None**

---

## Recommendations

### 1. Must Fix
**None required.** The context file is fully compliant with all checklist requirements.

### 2. Should Improve
**Optional Enhancement:** Consider adding a "Known Limitations" or "Out of Scope" section to the constraints to explicitly document what is NOT included in this MVP story (e.g., multi-turn conversation context, documentation RAG beyond vouchers, advanced guardrails). This would further clarify boundaries for the developer.

However, this information is already captured in the story file's "Dev Notes > Technical Debt and Future Enhancements" section (lines 543-562), which is referenced in the context. Current documentation is sufficient.

### 3. Consider
**Optional Enhancement:** Add a diagram reference (architecture diagram, sequence diagram, or data flow diagram) to the artifacts.docs section if such diagrams exist in the Epic 9 tech spec. Visual references can accelerate developer onboarding, especially for complex integrations like RAG query processing flow.

However, for a developer-ready context file, the current level of detail with 10 documentation references and 9 code references is already excellent.

---

## Conclusion

**Status:** ✅ **APPROVED - READY FOR DEVELOPMENT**

This story context file demonstrates exemplary quality across all validation dimensions. It provides a comprehensive, well-structured reference for developers to implement the MVP Voucher-Focused RAG Chatbot feature with confidence. The context successfully bridges the gap between high-level requirements (PRD, Architecture, Tech Spec) and implementation-ready guidance (code patterns, interfaces, constraints, test ideas).

**Key Strengths:**
1. **Completeness:** 100% checklist compliance with no gaps
2. **Accuracy:** Perfect fidelity to source story with no invention
3. **Traceability:** Clear mapping from ACs to tasks to test ideas
4. **Actionability:** Specific, measurable guidance (code locations, line numbers, package structures)
5. **Context-richness:** 10 documentation artifacts + 9 code references provide comprehensive background

The story can confidently proceed from "ready-for-dev" to "in-progress" status.

---

**Report Generated:** 2025-11-24
**Validation Tool:** BMAD Story Context Validation Workflow v6
**Agent:** Bob (Scrum Master)
