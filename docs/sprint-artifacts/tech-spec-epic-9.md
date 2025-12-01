# Epic Technical Specification: AI RAG Chatbot & Contextual Help

Date: 2025-11-24
Author: thanhtoan
Epic ID: 9
Status: Draft

---

## Overview

Epic 9 delivers an AI-powered Retrieval-Augmented Generation (RAG) chatbot that provides contextual assistance within the accounting system. The chatbot leverages n8n automation pipelines and Pinecone vector database to enable natural language queries in Vietnamese over voucher data, accounts receivable/payable records, and accounting documentation. This addresses PRD requirements FR38-FR42 by providing intelligent Q&A with source citations, automated data embedding, and context-aware guidance. The MVP focuses on voucher-centric RAG (Story 9.0) to validate the integration early, with full chatbot features in subsequent stories.

The chatbot architecture integrates with the existing Spring Boot backend and React frontend, maintaining strict RBAC enforcement and company-level data isolation. All queries are audit-logged with user context, question text, answers, citations, and confidence scores to ensure traceability and regulatory compliance per TT200 standards.

## Objectives and Scope

**In Scope:**

- AI chatbot widget integrated into the main application UI (expandable floating panel)
- Natural language Q&A in Vietnamese and English for voucher, AR/AP, and documentation queries
- Automated n8n pipeline to embed voucher data into Pinecone after each save/post operation
- Hybrid retrieval (semantic + keyword + metadata filters) with source citations and confidence indicators
- Context-aware guided workflows and step-by-step assistance for common accounting tasks
- User feedback capture, guardrail monitoring, and usage analytics dashboard
- Full RBAC enforcement: chatbot respects user roles and company-scoped data access
- Audit trail for all chatbot interactions (user, timestamp, query, answer, citations)
- Feature flag capability to disable chatbot without affecting voucher workflows

**Out of Scope:**

- Real-time streaming responses (buffered responses acceptable for MVP)
- Multi-language support beyond Vietnamese and English
- Voice input/output or speech recognition
- Integration with external knowledge bases or third-party documentation systems
- Advanced personalization or user preference learning (deferred to post-MVP)
- Mobile-optimized chatbot UI (desktop and tablet only for MVP)

## System Architecture Alignment

The AI RAG chatbot aligns with the system's multi-tenant, security-first architecture:

- **Backend Services (Spring Boot):** New `ChatbotService`, `EmbeddingService`, and `RAGQueryService` handle chatbot logic, Pinecone integration, and query processing. These services integrate with existing `CompanyContext`, `JwtAuthenticationFilter`, and `AuditService` to enforce multi-tenancy and audit logging.

- **Frontend (React + TypeScript):** A new `ChatbotWidget` component (floating panel) is added to `ProtectedLayout`, accessible from all authenticated pages. The widget communicates with backend via `/api/v1/chatbot/*` endpoints.

- **Data Architecture:** New entities `ChatbotQuery`, `ChatbotFeedback`, and `GuardrailLog` extend `CompanyScopedEntity` for multi-tenant support. Pinecone namespaces are scoped per `company_id` to ensure data isolation.

- **Security Architecture:** All chatbot endpoints enforce JWT authentication and RBAC. Queries are filtered by user permissions (e.g., CFO sees all data, Accountant sees only their created documents). Sensitive data (e.g., password fields) are excluded from embeddings.

- **Integration Points:**
  - **n8n Webhook:** Triggered after voucher save/post (via `VoucherService` and `SalesInvoiceService`) to send voucher data for embedding
  - **Pinecone Vector DB:** Stores embeddings with metadata (company_id, voucher_id, entity_type, period_id) for hybrid retrieval
  - **OpenAI/Anthropic LLM:** Used for query processing and response generation (configurable via environment variables)
  - **Redis Cache:** Caches frequently asked questions and their answers to reduce LLM costs and improve latency

- **Architectural Constraints:**
  - Must not introduce performance degradation to voucher posting workflows (n8n webhook is fire-and-forget with retry logic)
  - Must maintain existing audit trail standards (append-only, immutable logs)
  - Must comply with TT200 data retention requirements (chatbot logs retained for 10 years)

## Detailed Design

### Services and Modules

| Service/Module | Responsibility | Key Inputs | Key Outputs | Owner |
|----------------|----------------|------------|-------------|-------|
| `ChatbotController` | REST API endpoints for chatbot queries and feedback | JWT token, query text, session ID | Answer with citations, confidence score | Backend |
| `ChatbotService` | Orchestrates query processing, RAG retrieval, LLM interaction | User query, company context, role permissions | Formatted answer, source citations, confidence | Backend |
| `EmbeddingService` | Manages Pinecone embedding lifecycle (create, update, delete) | Voucher data, invoice data, document text | Embedding vectors with metadata | Backend |
| `RAGQueryService` | Hybrid retrieval (semantic + keyword + metadata filters) | Query embedding, company_id, filters | Relevant document chunks with scores | Backend |
| `N8nWebhookService` | Triggers n8n workflows for embedding automation | Voucher/invoice save event payload | Webhook response status | Backend |
| `GuardrailService` | Validates query safety, detects hallucinations, enforces RBAC | Query text, user role, company context | Pass/fail with reason | Backend |
| `ChatbotAnalyticsService` | Tracks usage metrics, query patterns, feedback trends | Query logs, feedback records | Analytics dashboard data | Backend |
| `ChatbotWidget` (React) | Frontend floating panel for chatbot interactions | User input, session state | Rendered chat UI with messages | Frontend |
| `useChatbot` hook | React hook managing chatbot state and API calls | Query text, session ID | Query response, loading state, error | Frontend |
| `ChatbotMessageList` | Displays conversation history with citations | Chat messages array | Rendered message list | Frontend |
| `ChatbotFeedback` | Captures user feedback (thumbs up/down, comments) | Message ID, feedback type | Feedback submitted confirmation | Frontend |

**Module Organization:**

- **Backend:** `com.accounting.service.chatbot.*`, `com.accounting.controller.ChatbotController`
- **Frontend:** `src/features/chatbot/*`, `src/components/chatbot/*`
- **n8n Workflows:** External n8n instance with webhook endpoints for embedding automation

### Data Models and Contracts

**Backend Entities:**

```java
@Entity
@Table(name = "chatbot_queries")
public class ChatbotQuery extends CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String queryText;

    @Column(columnDefinition = "TEXT")
    private String answerText;

    @Column(columnDefinition = "jsonb")
    private String citations; // JSON array of source references

    @Column
    private Float confidenceScore;

    @Column(nullable = false)
    private String sessionId;

    @Column
    private String language; // "vi" or "en"

    @Column
    private Integer responseTimeMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Audit hash for integrity verification
    @Column(length = 64)
    private String auditHash;
}

@Entity
@Table(name = "chatbot_feedback")
public class ChatbotFeedback extends CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long queryId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String feedbackType; // "helpful", "not_helpful", "report_issue"

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

@Entity
@Table(name = "guardrail_logs")
public class GuardrailLog extends CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String queryText;

    @Column(nullable = false)
    private String blockReason; // "rbac_violation", "unsafe_content", "hallucination_detected"

    @Column(columnDefinition = "jsonb")
    private String context; // Additional context as JSON

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
```

**Pinecone Metadata Schema:**

```json
{
  "company_id": "UUID",
  "entity_type": "voucher|sales_invoice|purchase_bill|documentation",
  "entity_id": "Long",
  "voucher_number": "String",
  "period_id": "Long",
  "created_by": "Long",
  "text_content": "String (original text for citation)",
  "indexed_at": "ISO 8601 timestamp"
}
```

**API Request/Response Contracts:**

```typescript
// POST /api/v1/chatbot/query
interface ChatbotQueryRequest {
  query: string;
  sessionId: string;
  language?: "vi" | "en";
  contextFilters?: {
    periodId?: number;
    entityType?: string;
  };
}

interface ChatbotQueryResponse {
  answer: string;
  citations: Citation[];
  confidenceScore: number;
  queryId: number;
  responseTimeMs: number;
}

interface Citation {
  entityType: string;
  entityId: number;
  voucherNumber?: string;
  excerpt: string;
  relevanceScore: number;
  link?: string; // Deep link to entity in app
}

// POST /api/v1/chatbot/feedback
interface FeedbackRequest {
  queryId: number;
  feedbackType: "helpful" | "not_helpful" | "report_issue";
  comment?: string;
}
```

### APIs and Interfaces

**REST Endpoints:**

| Method | Path | Description | Request Body | Response | Auth |
|--------|------|-------------|--------------|----------|------|
| POST | `/api/v1/chatbot/query` | Submit a natural language query | `ChatbotQueryRequest` | `ChatbotQueryResponse` | JWT |
| POST | `/api/v1/chatbot/feedback` | Submit feedback for a query | `FeedbackRequest` | `200 OK` | JWT |
| GET | `/api/v1/chatbot/history` | Retrieve user's chat history | Query params: `sessionId`, `limit` | `ChatbotQuery[]` | JWT |
| GET | `/api/v1/chatbot/analytics` | Get usage analytics (admin only) | Query params: `startDate`, `endDate` | `AnalyticsData` | JWT + Admin |
| DELETE | `/api/v1/chatbot/session/{sessionId}` | Clear a chat session | - | `204 No Content` | JWT |
| POST | `/api/v1/webhooks/n8n/embedding` | n8n webhook for embedding updates | `EmbeddingPayload` | `200 OK` | Webhook secret |

**n8n Webhook Payload (from backend to n8n):**

```json
{
  "companyId": "UUID",
  "entityType": "voucher",
  "entityId": 12345,
  "voucherNumber": "VC2025-00123",
  "data": {
    "date": "2025-11-24",
    "description": "Phiếu nhập kho",
    "lines": [
      {
        "accountCode": "156",
        "accountName": "Hàng hóa",
        "debit": 10000000,
        "credit": 0,
        "description": "Nhập kho tháng 11"
      }
    ],
    "totalDebit": 10000000,
    "totalCredit": 10000000,
    "status": "posted"
  },
  "triggeredAt": "2025-11-24T10:30:00Z"
}
```

**Error Responses:**

| Status Code | Error Code | Description |
|-------------|------------|-------------|
| 400 | `INVALID_QUERY` | Query text is empty or exceeds max length (5000 chars) |
| 403 | `RBAC_VIOLATION` | User lacks permission to query requested data scope |
| 429 | `RATE_LIMIT_EXCEEDED` | User has exceeded query rate limit (20 queries/minute) |
| 500 | `LLM_ERROR` | OpenAI/Anthropic API error |
| 503 | `PINECONE_UNAVAILABLE` | Pinecone service is down |

### Workflows and Sequencing

**Query Processing Flow:**

```text
1. User submits query via ChatbotWidget
   ↓
2. Frontend sends POST /api/v1/chatbot/query with JWT
   ↓
3. ChatbotController validates JWT, extracts companyId, userId, role
   ↓
4. GuardrailService validates query safety and RBAC scope
   ↓ (if pass)
5. RAGQueryService generates query embedding via OpenAI
   ↓
6. RAGQueryService queries Pinecone with:
   - Query embedding (semantic search)
   - Metadata filters: company_id = {companyId}
   - RBAC filters: created_by = {userId} (if role = Accountant)
   - Top 10 results with relevance scores > 0.7
   ↓
7. ChatbotService retrieves original text from database using entity IDs
   ↓
8. ChatbotService constructs LLM prompt with context:
   - System: "Bạn là trợ lý kế toán. Chỉ trả lời dựa trên dữ liệu được cung cấp."
   - Context: Retrieved document chunks with metadata
   - User query
   ↓
9. ChatbotService calls OpenAI/Anthropic API for completion
   ↓
10. ChatbotService parses response, extracts citations
   ↓
11. ChatbotQuery entity saved to database with audit hash
   ↓
12. AuditService logs query event (append-only)
   ↓
13. Response returned to frontend with answer, citations, confidence
   ↓
14. ChatbotWidget renders answer with citation links
```

**Embedding Automation Flow (n8n):**

```text
1. User posts a voucher via VoucherService.post()
   ↓
2. VoucherService triggers N8nWebhookService.triggerEmbedding()
   ↓
3. N8nWebhookService sends POST to n8n webhook (fire-and-forget, async)
   ↓
   [n8n Workflow]
4. n8n receives webhook payload
   ↓
5. n8n validates company_id and entity_id
   ↓
6. n8n fetches full voucher details from database (optional enrichment)
   ↓
7. n8n formats text for embedding:
   - "Phiếu {voucherNumber} ngày {date}: {description}. Chi tiết: {lines summary}"
   ↓
8. n8n calls OpenAI Embedding API (text-embedding-ada-002)
   ↓
9. n8n upserts embedding to Pinecone:
   - Namespace: company_{companyId}
   - ID: voucher_{entityId}
   - Vector: embedding
   - Metadata: {company_id, entity_type, voucher_number, ...}
   ↓
10. n8n logs success/failure to monitoring system
   ↓
11. (On failure) n8n retries up to 3 times with exponential backoff
   ↓
12. (On persistent failure) n8n sends alert email to admin
```

**Feedback Capture Flow:**

```text
1. User clicks thumbs up/down or "Report Issue" on a chatbot message
   ↓
2. Frontend sends POST /api/v1/chatbot/feedback
   ↓
3. ChatbotController validates JWT and queryId ownership
   ↓
4. ChatbotFeedback entity saved to database
   ↓
5. (If feedbackType = "report_issue") Notification sent to admin queue
   ↓
6. ChatbotAnalyticsService updates aggregate metrics (cached in Redis)
   ↓
7. Frontend shows "Thank you for your feedback" toast
```

## Non-Functional Requirements

### Performance

**Response Time Targets:**

- Chatbot query processing: P95 < 5 seconds (includes Pinecone retrieval + LLM generation)
- Chatbot widget load time: < 1 second
- n8n webhook trigger: < 200ms (fire-and-forget, async)
- Embedding creation (n8n): < 10 seconds per voucher

**Throughput:**

- Support 20 concurrent chatbot queries per company (MVP target aligns with NFR3)
- n8n webhook queue: Handle up to 100 voucher posts/minute without data loss
- Rate limiting: 20 queries per user per minute to prevent abuse and control LLM costs

**Caching Strategy:**

- Redis cache for frequently asked questions (FAQ patterns): 1-hour TTL
- Cache invalidation: Manual invalidation after period close or major data changes
- Cache key format: `chatbot:faq:{companyId}:{queryHash}`

**Cost Optimization:**

- Use OpenAI `gpt-3.5-turbo` for MVP (cheaper than GPT-4, sufficient for Vietnamese accounting queries)
- Embedding model: `text-embedding-ada-002` (cost-effective, good performance)
- Limit context window to top 10 retrieved chunks (max 8000 tokens) to control LLM costs
- Monitor daily LLM API spend per company; alert if exceeds $10/day

### Security

**Authentication & Authorization:**

- All chatbot endpoints require valid JWT token (issued by existing `JwtAuthenticationFilter`)
- RBAC enforcement at query time:
  - **Accountant:** Can only query vouchers/invoices created by them (`created_by = userId`)
  - **Chief Accountant:** Can query all company data
  - **CFO:** View-only access to all company data
  - **Admin:** Full access including analytics and guardrail logs
- Company-level data isolation: Pinecone namespaces scoped by `company_id`
- Webhook authentication: n8n webhook endpoint protected by shared secret (environment variable)

**Data Protection:**

- Exclude sensitive fields from embeddings: `password_hash`, `refresh_token`, `api_keys`
- Mask PII in guardrail logs: replace user emails with `user_***`
- Encrypt chatbot query/answer text at rest using PostgreSQL encryption
- HTTPS/TLS enforced for all API communication (per NFR7)

**Guardrails:**

- Query content filtering: Block queries containing SQL injection patterns, XSS attempts, or unsafe prompts
- Hallucination detection: Require LLM responses to include specific citation markers; reject responses without citations
- RBAC violation detection: Log and block queries attempting to access unauthorized data scopes
- Rate limiting: 403 response after 20 queries/minute; temporary ban after 100 failed queries/hour

**Audit Trail:**

- All chatbot queries logged in `chatbot_queries` table with audit hash (SHA-256 of query + timestamp + userId)
- Guardrail violations logged in `guardrail_logs` table (append-only)
- Integration with existing `AuditService` for consistency with TT200 audit requirements (NFR8)

### Reliability/Availability

**Fault Tolerance:**

- n8n webhook failures: Retry with exponential backoff (3 attempts: 1s, 5s, 15s delay)
- Pinecone unavailability: Return graceful error message to user ("Vector database unavailable, please try again later")
- LLM API failures: Return fallback response ("I'm currently unavailable. Please contact support or check back later.")
- Circuit breaker pattern for external services (Pinecone, OpenAI): Open circuit after 5 consecutive failures

**Degraded Mode:**

- If n8n is down: Voucher posting continues normally (fire-and-forget webhook won't block); embeddings queued for retry
- If chatbot is down: Feature flag `chatbot.enabled=false` hides widget UI without affecting voucher workflows
- If Redis cache fails: Chatbot queries fall back to direct database/LLM calls (slower but functional)

**Data Consistency:**

- Idempotent embedding upserts: Pinecone upserts with same ID overwrite existing embeddings (prevents duplicates)
- Eventual consistency acceptable: Embeddings may lag voucher posts by up to 30 seconds (async n8n pipeline)
- Periodic reconciliation job: Nightly job (3:00 AM) validates all posted vouchers have corresponding embeddings; re-triggers missing embeddings

**Availability Target:**

- MVP target: 95% uptime for chatbot service (aligned with overall system availability)
- Graceful degradation: Chatbot unavailability does not impact core voucher/posting workflows

### Observability

**Structured Logging:**

- All chatbot queries logged with structured fields: `userId`, `companyId`, `queryText`, `responseTimeMs`, `confidenceScore`, `citationCount`
- Log levels:
  - INFO: Successful queries
  - WARN: Low confidence responses (< 0.5), rate limit warnings
  - ERROR: LLM API failures, Pinecone errors, guardrail violations
- Request ID tracking: `X-Request-Id` header propagated through all services

**Metrics:**

- Query volume per company (daily/hourly aggregates)
- Average response time by company and query type
- LLM API cost per company (tracked via token usage)
- Cache hit/miss ratio
- Guardrail violation rate by type (`rbac_violation`, `unsafe_content`, `hallucination_detected`)
- n8n webhook success/failure rate

**Alerting:**

- Alert if chatbot query P95 latency > 10 seconds (sustained for 5 minutes)
- Alert if LLM API error rate > 5% (over 15-minute window)
- Alert if n8n webhook failure rate > 10% (over 15-minute window)
- Alert if daily LLM cost per company > $10
- Alert on persistent guardrail violations (> 10 violations/hour by single user)

**Dashboards:**

- Admin dashboard (`/api/v1/chatbot/analytics`) showing:
  - Total queries, unique users, avg response time
  - Top queries by frequency
  - Feedback sentiment breakdown (helpful vs. not helpful)
  - LLM cost trends by company
  - Guardrail violation summary

## Dependencies and Integrations

**Backend Dependencies (Maven - pom.xml):**

| Dependency | Version | Purpose |
|------------|---------|---------|
| `spring-boot-starter-web` | 3.5.7 | REST API framework |
| `spring-boot-starter-data-jpa` | 3.5.7 | Database ORM |
| `spring-boot-starter-data-redis` | 3.5.7 | Redis caching for FAQ queries |
| `spring-boot-starter-security` | 3.5.7 | JWT authentication, RBAC |
| `postgresql` | 42.7.4 | Database driver |
| `flyway-core` | 11.10.0 | Database migrations for chatbot tables |
| **NEW:** `pinecone-client` | 0.7.2+ | Pinecone vector database SDK (to be added) |
| **NEW:** `openai-java` | 0.18.0+ | OpenAI API client for embeddings & completions (to be added) |
| **NEW:** `okhttp` | 4.12.0+ | HTTP client for n8n webhook calls (to be added) |
| `lombok` | (provided) | Boilerplate reduction |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.13 | API documentation |

**Frontend Dependencies (package.json):**

| Dependency | Version | Purpose |
|------------|---------|---------|
| `react` | 19.1.1 | UI framework |
| `react-router-dom` | 7.9.5 | Routing |
| `@tanstack/react-query` | 5.62.0 | Server state management for chatbot queries |
| `axios` | 1.7.9 | HTTP client |
| `@radix-ui/react-dialog` | 1.1.15 | Modal for chatbot widget |
| `lucide-react` | 0.552.0 | Icons for chatbot UI |
| **NEW:** `react-markdown` | 9.0.0+ | Markdown rendering for chatbot responses (to be added) |
| **NEW:** `@tailwindcss/typography` | 0.5.13+ | Typography styling for markdown (to be added) |
| `tailwindcss` | 4.1.16 | Styling framework |
| `zod` | 4.1.12 | Request/response validation |

**External Services:**

| Service | Version/Plan | Purpose | Configuration |
|---------|--------------|---------|---------------|
| **Pinecone** | Serverless (Free tier) | Vector database for embeddings storage | - API Key: `PINECONE_API_KEY` (env var)<br>- Environment: `PINECONE_ENVIRONMENT` (e.g., `us-east-1-aws`)<br>- Index name: `accounting-embeddings`<br>- Dimensions: 1536 (OpenAI ada-002)<br>- Metric: cosine similarity |
| **OpenAI** | Pay-as-you-go | Embedding generation & LLM completions | - API Key: `OPENAI_API_KEY` (env var)<br>- Embedding model: `text-embedding-ada-002`<br>- Completion model: `gpt-3.5-turbo`<br>- Max tokens: 2000 per response<br>- Temperature: 0.3 (more deterministic) |
| **n8n** | Self-hosted (Docker) | Automation workflow for embedding pipeline | - Webhook URL: `N8N_WEBHOOK_URL` (env var)<br>- Webhook secret: `N8N_WEBHOOK_SECRET` (env var)<br>- Workflow: "Voucher Embedding Automation"<br>- Trigger: HTTP webhook<br>- Retry policy: 3 attempts with exponential backoff |
| **Redis** | 7.x (Docker) | Caching for FAQ queries | - Host: `REDIS_HOST` (default: localhost)<br>- Port: `REDIS_PORT` (default: 6379)<br>- Password: `REDIS_PASSWORD` (env var)<br>- Database: 0 (default) |
| **PostgreSQL** | 16.x (Docker) | Primary database for chatbot entities | - Existing connection configuration<br>- New tables: `chatbot_queries`, `chatbot_feedback`, `guardrail_logs`<br>- JSONB column support required |

**Integration Points with Existing Services:**

| Integration | Existing Service | Epic 9 Service | Integration Method |
|-------------|------------------|----------------|-------------------|
| Authentication | `JwtAuthenticationFilter` | `ChatbotController` | JWT token validation, user/company extraction |
| Authorization | `CompanyContext`, `SecurityConfig` | `ChatbotService`, `RAGQueryService` | Company-scoped queries, RBAC enforcement |
| Audit Logging | `AuditService` | `ChatbotService` | Append-only audit logs for queries |
| Voucher Posting | `VoucherService`, `SalesInvoiceService` | `N8nWebhookService` | Webhook trigger after successful post |
| User Management | `UserRepository` | `ChatbotAnalyticsService` | User metadata for analytics |
| Period Management | `AccountingPeriod` | `RAGQueryService` | Filter embeddings by period |

**Environment Variables (application.yml additions):**

```yaml
# AI Chatbot Configuration
chatbot:
  enabled: ${CHATBOT_ENABLED:true}
  rate-limit:
    queries-per-minute: ${CHATBOT_RATE_LIMIT:20}
  cost-limit:
    daily-per-company-usd: ${CHATBOT_COST_LIMIT:10.0}

# OpenAI Configuration
openai:
  api-key: ${OPENAI_API_KEY}
  embedding-model: ${OPENAI_EMBEDDING_MODEL:text-embedding-ada-002}
  completion-model: ${OPENAI_COMPLETION_MODEL:gpt-3.5-turbo}
  max-tokens: ${OPENAI_MAX_TOKENS:2000}
  temperature: ${OPENAI_TEMPERATURE:0.3}
  timeout-seconds: ${OPENAI_TIMEOUT:30}

# Pinecone Configuration
pinecone:
  api-key: ${PINECONE_API_KEY}
  environment: ${PINECONE_ENVIRONMENT:us-east-1-aws}
  index-name: ${PINECONE_INDEX_NAME:accounting-embeddings}
  namespace-prefix: ${PINECONE_NAMESPACE_PREFIX:company_}
  timeout-seconds: ${PINECONE_TIMEOUT:10}

# n8n Webhook Configuration
n8n:
  webhook-url: ${N8N_WEBHOOK_URL}
  webhook-secret: ${N8N_WEBHOOK_SECRET}
  timeout-seconds: ${N8N_TIMEOUT:5}
  retry-attempts: ${N8N_RETRY_ATTEMPTS:3}
```

**Database Migrations (Flyway):**

- `V20251224__create_chatbot_tables.sql`: Create `chatbot_queries`, `chatbot_feedback`, `guardrail_logs` tables
- `V20251225__add_chatbot_indexes.sql`: Add indexes on `company_id`, `user_id`, `session_id`, `created_at` for query performance

## Acceptance Criteria (Authoritative)

**Story 9.0: Voucher-Focused RAG MVP (n8n Pipeline)**

1. **AC 9.0.1 - Embedding Trigger:** After every successful voucher save/post operation, the backend triggers an n8n webhook that receives: company ID, voucher header (number, date, description), line items (account code, account name, debit, credit), related customers/vendors, and summary balances (total debit/credit).

2. **AC 9.0.2 - Idempotent Embedding:** Embeddings are stored in Pinecone under company-specific namespaces (`company_{companyId}`). Re-indexing the same voucher ID is idempotent (overwrites existing embedding). Retry logic handles n8n unavailability with exponential backoff (3 attempts) and logs all failures.

3. **AC 9.0.3 - Chatbot Panel:** A minimal in-app chatbot panel (floating, expandable) allows Vietnamese questions like "Tình hình công nợ hiện tại ra sao?" (What is the current AR/AP status?). Panel accessible from all authenticated pages via icon in header. Queries call backend `/api/v1/chatbot/query` endpoint.

4. **AC 9.0.4 - Hybrid Retrieval with Citations:** RAG query performs hybrid retrieval (semantic + metadata filters) over Pinecone + ledger aggregates. Responses ALWAYS include: (a) natural-language answer in Vietnamese, (b) citation list with voucher/invoice IDs and links, (c) confidence indicator (0.0-1.0). If no evidence found (confidence < 0.5), chatbot replies "Không đủ dữ liệu" with next-step suggestions.

5. **AC 9.0.5 - Audit Logging:** Each chatbot query is audit-logged in `chatbot_queries` table with: user ID, company ID, timestamp, prompt text, answer summary, citations JSON, confidence score, response time. Errors surfaced to user with retry guidance.

6. **AC 9.0.6 - Feature Flag:** Feature flag `chatbot.enabled` (env variable) allows disabling chatbot widget UI without affecting voucher posting workflows. When disabled, chatbot icon hidden from UI; voucher workflows continue normally.

**Story 9.1: Chatbot Widget Integration and Security**

7. **AC 9.1.1 - Widget UI:** Chatbot widget loads on all main pages (Dashboard, Vouchers, Sales Invoices, etc.) as a floating panel (bottom-right corner). Widget hideable via close button. Keyboard shortcut `Ctrl+Shift+C` toggles widget. Loading state, error handling, and fallback message if backend/network down.

8. **AC 9.1.2 - RBAC Enforcement:** SSO session and RBAC enforced at API level. Chatbot only responds to questions against data user is allowed to access per role permissions. All queries logged with user context. Unauthorized queries return 403 error with clear message.

9. **AC 9.1.3 - Privacy Mode:** Never show/trace confidential data in suggestions or answers if user lacks permission. Attempts to access restricted data are blocked, logged in `guardrail_logs`, and return "Bạn không có quyền truy cập dữ liệu này" (You don't have permission to access this data).

10. **AC 9.1.4 - Session Management:** Widget UI allows: view full chat history, download conversation as text file, clear session (with confirmation). Conversations stored per user and session ID; hidden from other users unless admin/auditor role.

11. **AC 9.1.5 - Input Sanitization:** Frontend sanitizes user input to detect/prevent dangerous patterns (SQL injection, XSS, code injection, external links). Backend validates query text length (max 5000 chars), blocks unsafe prompts.

12. **AC 9.1.6 - Help & Onboarding:** Help menu includes: chatbot onboarding guide, privacy/data use explainer, report-issue button (opens feedback form with pre-filled context).

13. **AC 9.1.7 - Comprehensive Audit:** All chatbot sessions, queries, errors, and security events logged with: user ID, session ID, chat ID, event timestamp, event hash. Suspicious patterns (e.g., frequent RBAC violations, rapid failed queries) flagged for admin review.

**Story 9.2: Contextual Retrieval-Augmented Generation (RAG) for Q&A**

14. **AC 9.2.1 - Hybrid Search:** Chatbot uses hybrid search (semantic + keyword + metadata filters) to locate relevant document/ledger excerpts. Supports Vietnamese and English queries. Pinecone retrieval with top 10 results (relevance score > 0.7).

15. **AC 9.2.2 - Citation Requirement:** Every answer includes citations: clickable links to doc/transaction source, excerpt summary at top, full trace chain on expand. Answers NEVER returned without at least one source citation.

16. **AC 9.2.3 - No Fabrication Guardrail:** Chatbot never fabricates accounting figures/numbers. Always pulls from actual transactions/docs with timestamp and context. Guardrails block answer generation if evidence unavailable; returns "I don't know" message.

17. **AC 9.2.4 - Filtered Queries:** For queries requesting ledger info, required filters enforced: company ID, period range, user role. Configurable answer detail level per role (e.g., Accountant sees only their vouchers, Chief Accountant sees all).

18. **AC 9.2.5 - Error Handling:** If data out-of-date or index failed, clear error message given with workaround suggestion (e.g., "Data may be outdated. Try refreshing, or contact support.").

19. **AC 9.2.6 - Confidence Threshold:** Hardcoded "I don't know" or redirect-to-human triggers if system confidence < 0.5. Explain reason (e.g., "No relevant documents found") and show confidence badge to user.

20. **AC 9.2.7 - Conversation Context:** User can refine/follow-up question, reset context (clears session), view prior history thread. Follow-up questions maintain session ID for context continuity.

21. **AC 9.2.8 - Data Versioning:** Indexed data audited with version/timestamp in answer metadata. Model outputs rate-limited (20 queries/user/minute). Monitored for drift/hallucinations via feedback loop.

22. **AC 9.2.9 - Nightly Indexing:** New voucher/ledger data indexed nightly at 3:00 AM and after every major release. Indexing logs errors per doc/transaction; requires admin review to close gaps.

**Story 9.3: Workflow Automation and Guided Journeys**

23. **AC 9.3.1 - FAQ Triggers:** FAQ trigger phrases recognized (e.g., "how to post a journal", "how to upload opening balance"). Chatbot offers walk-through with step-by-step guide (text + checklist + links to screen sections).

24. **AC 9.3.2 - Context-Aware Guidance:** Guided flows context-aware based on user's current screen/module. Gives inline advice or navigation quick-links (e.g., points to voucher screen when asked about posting).

25. **AC 9.3.3 - Related Actions:** Suggests related actions/documents (e.g., links "see VAT export" after VAT Q&A, proposes "open reconciliation screen").

26. **AC 9.3.4 - Step Tracking:** Step completion tracking: user can check-off inline steps and review previous steps. Chatbot saves incomplete sessions for resume later.

27. **AC 9.3.5 - Frustration Detection:** Tracks failed/frustrated interaction patterns (e.g., repeated "I don't know" responses, rapid query retries). Prompts for feedback or offers escalation to human.

28. **AC 9.3.6 - Escalation Flows:** Escalation options: open support ticket (pre-filled with context), email admin, or join group support channel (if configured).

29. **AC 9.3.7 - Automation Audit:** All automation/integration actions audited with: user, session, screen, parameters, outcome.

30. **AC 9.3.8 - Controlled Plug-ins:** Model supports controlled plug-ins (e.g., n8n task triggers) only for white-listed flows. Admin-editable whitelist in application config.

**Story 9.4: Feedback, Training Data Capture, and Guardrail Monitoring**

31. **AC 9.4.1 - Feedback UI:** After every answer, user can rate (thumbs up/down, with optional comment). Open issue reports go to admin inbox and are ticketed/audited.

32. **AC 9.4.2 - Feedback Integration:** Feedback integrated into RAG retraining/refresh process (weekly review cycle) with admin approval step. No end-user PII or sensitive data leaks to models without explicit consent.

33. **AC 9.4.3 - Guardrail Logs:** Each time AI/LLM blocks/restricts answer, creates context+reason record in `guardrail_logs`. Admin view shows top blocked queries, breakdown by type, user, and trend.

34. **AC 9.4.4 - Periodic Reviews:** Periodic reviews required of feedback, blocked queries, model audit logs (weekly or after major update). All reviews/audits tracked with approval and closure structure.

35. **AC 9.4.5 - Override Workflow:** User requests for access/override of restricted responses require approval workflow and audit (with full event chain/log).

36. **AC 9.4.6 - Data Export:** Admins can download anonymized feedback and guardrail data for regulatory submission or security review. Data purge requires dual signoff.

**Story 9.5: Usage Analytics and Continuous Improvement**

37. **AC 9.5.1 - Analytics Dashboard:** Dashboard (`/api/v1/chatbot/analytics`, admin-only) shows: query volume, unique users, active/inactive users by role/company, success/deflection metrics, QA scores, feedback trends.

38. **AC 9.5.2 - Performance Metrics:** Tracks average latency per question, slowest queries, fallbacks/"I don't know" rates, escalation counts, top repeated issues.

39. **AC 9.5.3 - Quality Metrics:** Answer traceability (with sources), manual QA scoring, feedback outcome over time with drilldown by answer and user segment.

40. **AC 9.5.4 - Anomaly Monitoring:** Monitors and alerts for abnormal failures: data mismatch, hallucination detection, RBAC policy violation, API faults. Escalation triggers admin notification.

41. **AC 9.5.5 - Model Tracking:** Tracks retraining/model update events, with sharpness/drift benchmarks after each deploy. Auto alerts for drops in accuracy/conformance.

42. **AC 9.5.6 - Export & DR:** Export/download permitted for anonymized data for audit/regulatory/compliance. Direct connection to DR/backup process.

## Traceability Mapping

| AC# | Spec Section | Component(s)/API(s) | Test Idea |
|-----|--------------|---------------------|-----------|
| 9.0.1 | Detailed Design > Workflows > Embedding Flow | `N8nWebhookService`, `VoucherService`, `SalesInvoiceService` | Unit test: verify webhook payload format. Integration test: post voucher, verify n8n webhook called with correct data |
| 9.0.2 | Detailed Design > Data Models | Pinecone namespace, `EmbeddingService` | Integration test: embed same voucher twice, verify idempotent (same vector ID). Test retry logic with mock n8n failure |
| 9.0.3 | Detailed Design > APIs > POST /chatbot/query | `ChatbotWidget`, `ChatbotController` | E2E test: type Vietnamese query, verify panel renders, API called. Unit test: controller endpoint validation |
| 9.0.4 | Detailed Design > Workflows > Query Processing | `RAGQueryService`, `ChatbotService` | Integration test: query with mock Pinecone results, verify answer includes citations + confidence. Edge case: no results (confidence < 0.5) |
| 9.0.5 | Detailed Design > Data Models > ChatbotQuery | `ChatbotService`, `AuditService`, `chatbot_queries` table | Unit test: verify audit hash generation. Integration test: query execution creates audit log with all required fields |
| 9.0.6 | NFR > Reliability > Feature Flag | `application.yml`, `ChatbotWidget` | Integration test: set `chatbot.enabled=false`, verify widget hidden, voucher post still works |
| 9.1.1 | Detailed Design > Services > ChatbotWidget | `ChatbotWidget`, `useChatbot` hook | E2E test: verify widget on Dashboard, Vouchers, Sales Invoices. Test keyboard shortcut `Ctrl+Shift+C`. Test error fallback |
| 9.1.2 | NFR > Security > RBAC Enforcement | `ChatbotController`, `GuardrailService` | Integration test: Accountant queries Chief Accountant's voucher, expect 403. Verify all queries logged with user context |
| 9.1.3 | NFR > Security > Guardrails | `GuardrailService`, `guardrail_logs` table | Integration test: Accountant attempts restricted query, verify blocked + logged. Test privacy mode hides data |
| 9.1.4 | Detailed Design > APIs > GET /chatbot/history, DELETE /session | `ChatbotController` | Integration test: create session, fetch history, verify returned. Test session isolation (user A can't see user B) |
| 9.1.5 | NFR > Security > Input Sanitization | `ChatbotWidget`, `ChatbotController` | Unit test: validate input sanitization (SQL injection, XSS). Test backend rejects queries > 5000 chars |
| 9.1.6 | Detailed Design > Frontend > Help Menu | `ChatbotWidget`, Help modal | E2E test: click help button, verify modal renders with onboarding guide, privacy explainer, report button |
| 9.1.7 | NFR > Observability > Structured Logging | `ChatbotService`, `AuditService` | Integration test: execute query, verify logs contain userId, sessionId, chatId, event hash. Test flagging logic |
| 9.2.1 | Detailed Design > Workflows > Query Processing (step 5-6) | `RAGQueryService`, Pinecone | Integration test: submit Vietnamese + English queries, verify hybrid search. Test top 10 filter, relevance > 0.7 |
| 9.2.2 | Detailed Design > Data Models > Citation | `ChatbotService` | Unit test: verify response parser extracts citations. Integration test: query with mock results, verify clickable citation links |
| 9.2.3 | NFR > Security > Guardrails > No Fabrication | `GuardrailService`, `ChatbotService` | Integration test: query with no Pinecone results, verify "I don't know" response (no fabricated numbers) |
| 9.2.4 | Detailed Design > APIs > ChatbotQueryRequest filters | `RAGQueryService` | Integration test: Accountant queries vouchers, verify only their vouchers in context. Test period filter enforcement |
| 9.2.5 | Detailed Design > APIs > Error Responses | `ChatbotController` | Integration test: simulate Pinecone failure, verify clear error message returned with workaround suggestion |
| 9.2.6 | Detailed Design > Data Models > confidenceScore | `ChatbotService` | Unit test: verify confidence < 0.5 triggers "I don't know". Integration test: verify confidence badge rendered in UI |
| 9.2.7 | Detailed Design > Data Models > sessionId | `ChatbotWidget`, `useChatbot` hook | E2E test: submit query, follow-up query, verify session ID maintained. Test reset context clears session |
| 9.2.8 | NFR > Observability > Metrics > Rate Limiting | `ChatbotController`, Redis | Integration test: submit 21 queries in 1 minute, verify 21st returns 429. Test drift monitoring (manual QA) |
| 9.2.9 | NFR > Reliability > Reconciliation Job | Scheduled job (Spring @Scheduled) | Integration test: create voucher, skip n8n, run reconciliation job, verify embedding re-triggered |
| 9.3.1-9.3.8 | Story 9.3 deferred | N/A | Deferred to post-MVP |
| 9.4.1-9.4.6 | Story 9.4 deferred | N/A | Deferred to post-MVP |
| 9.5.1-9.5.6 | Story 9.5 deferred | N/A | Deferred to post-MVP |

## Risks, Assumptions, Open Questions

**Risks:**

1. **RISK-01: External API Cost Overrun**
   - **Description:** OpenAI API costs may exceed budget ($10/company/day) if query volume spikes or users submit very long queries
   - **Mitigation:** Implement rate limiting (20 queries/minute/user), query length limits (5000 chars), and cost monitoring alerts. Consider caching frequently asked questions to reduce LLM calls
   - **Owner:** Backend team

2. **RISK-02: Pinecone Free Tier Limits**
   - **Description:** Pinecone Serverless free tier has limitations (1M vectors, 100K queries/month). May need to upgrade if exceeds limits
   - **Mitigation:** Monitor usage via Pinecone dashboard. Plan for upgrade to Starter plan ($70/month) if approaching limits
   - **Owner:** DevOps team

3. **RISK-03: n8n Workflow Complexity**
   - **Description:** n8n embedding automation workflow may be complex to maintain and debug if failures occur
   - **Mitigation:** Document n8n workflow thoroughly. Implement comprehensive logging and alerting. Consider migrating to backend-native embedding service if n8n proves too brittle
   - **Owner:** Backend team

4. **RISK-04: LLM Hallucination Despite Guardrails**
   - **Description:** LLM may still fabricate answers or provide incorrect information despite RAG guardrails
   - **Mitigation:** Strict citation requirements (no answer without source). Confidence thresholds. User feedback loop to flag incorrect answers. Manual QA review of chatbot responses weekly
   - **Owner:** QA team + Product owner

5. **RISK-05: Vietnamese Language Support Quality**
   - **Description:** OpenAI GPT-3.5-turbo may have lower quality for Vietnamese compared to English
   - **Mitigation:** Test with Vietnamese accounting terminology during MVP. Consider switching to GPT-4 or Claude for better Vietnamese support if quality insufficient. Provide fallback option for users to switch to English
   - **Owner:** Backend team + Product owner

6. **RISK-06: Performance Degradation on Voucher Posting**
   - **Description:** n8n webhook trigger may slow down voucher posting workflows
   - **Mitigation:** Fire-and-forget async webhook with circuit breaker. Monitor posting latency P95. Feature flag to disable chatbot if performance issues
   - **Owner:** Backend team

**Assumptions:**

1. **ASSUME-01:** OpenAI API and Pinecone will maintain 99%+ uptime during MVP phase
2. **ASSUME-02:** n8n Docker instance will be deployed and maintained by DevOps team (not part of Epic 9 scope)
3. **ASSUME-03:** Users have stable internet connectivity for chatbot interactions (no offline mode required for MVP)
4. **ASSUME-04:** Vietnamese accounting terminology is sufficiently represented in OpenAI's training data for basic Q&A
5. **ASSUME-05:** 20 concurrent chatbot queries per company is sufficient for MVP (NFR3 concurrency target)
6. **ASSUME-06:** Redis is already deployed and available for caching (from existing infrastructure)

**Open Questions:**

1. **Q-01:** Should we support fallback to English if Vietnamese quality is poor? Or require Vietnamese-only for consistency?
   - **Owner:** Product owner
   - **Target resolution:** Before Story 9.0 implementation

2. **Q-02:** What is the retention policy for chatbot query logs? 10 years per TT200, or shorter period acceptable?
   - **Owner:** Legal/Compliance team
   - **Target resolution:** Before Story 9.0 implementation

3. **Q-03:** Should admin analytics dashboard (`/api/v1/chatbot/analytics`) be accessible to CFO role, or Admin only?
   - **Owner:** Product owner
   - **Target resolution:** Before Story 9.5 implementation (deferred)

4. **Q-04:** Do we need to integrate with existing support ticket system for escalation flows (Story 9.3), or implement simple email-based escalation?
   - **Owner:** Product owner + Support team
   - **Target resolution:** Before Story 9.3 implementation (deferred)

5. **Q-05:** Should we use OpenAI exclusively, or evaluate alternatives (Anthropic Claude, Google Gemini) for better Vietnamese support?
   - **Owner:** Backend team + Product owner
   - **Target resolution:** During Story 9.0 implementation (can pivot if quality insufficient)

## Test Strategy Summary

**Test Levels:**

1. **Unit Tests (JUnit 5 + Mockito)**
   - All service classes (`ChatbotService`, `EmbeddingService`, `RAGQueryService`, `GuardrailService`)
   - Test data model validation (`ChatbotQuery`, `ChatbotFeedback`, `GuardrailLog`)
   - Test webhook payload formatting (`N8nWebhookService`)
   - Test confidence score calculation, citation extraction, audit hash generation
   - Target: 70% code coverage for chatbot package

2. **Integration Tests (Spring Boot Test + TestContainers)**
   - End-to-end chatbot query flow (POST /chatbot/query → Pinecone mock → LLM mock → response)
   - n8n webhook trigger after voucher post (verify webhook called with correct payload)
   - RBAC enforcement (Accountant can't query other users' vouchers)
   - Audit logging (verify `chatbot_queries` and `guardrail_logs` entries created)
   - Redis caching (verify FAQ cache hit/miss)
   - Rate limiting (verify 429 response after 20 queries/minute)
   - Feature flag (verify widget hidden when `chatbot.enabled=false`)
   - Database migrations (Flyway validation)

3. **E2E Tests (Playwright)**
   - Chatbot widget UI interactions (open/close, type query, view response with citations)
   - Citation links (click citation link, navigate to voucher detail)
   - Session management (view history, clear session, download conversation)
   - Keyboard shortcuts (`Ctrl+Shift+C` toggle widget)
   - Error handling (network failure, backend error, low confidence response)
   - Help menu (onboarding guide, privacy explainer, report issue)

4. **API Tests (Playwright API Testing)**
   - All chatbot endpoints (`/api/v1/chatbot/*`)
   - Request/response contract validation (Zod schemas)
   - Error response codes (400, 403, 429, 500, 503)
   - Authentication (JWT validation, unauthorized access returns 401)

5. **Manual QA**
   - Vietnamese language quality assessment (accounting terminology accuracy)
   - Hallucination detection (verify no fabricated numbers in answers)
   - Citation quality (verify excerpts are relevant and accurate)
   - Usability testing (chatbot UI/UX, help documentation clarity)
   - Performance testing (query response time P95 < 5s under load)

**Test Data:**

- **Seed data:** 50 vouchers, 20 sales invoices, 10 customers, 5 suppliers (multiple companies)
- **Pinecone test index:** Separate test namespace (`company_test_{companyId}`) with pre-embedded test vouchers
- **Mock LLM responses:** Predefined OpenAI responses for deterministic testing
- **Test users:** Accountant, Chief Accountant, CFO, Admin roles with different data access permissions

**Coverage of Acceptance Criteria:**

- **Story 9.0 (MVP):** All 6 ACs covered by integration + E2E tests
- **Story 9.1:** All 7 ACs covered by integration + E2E tests
- **Story 9.2:** All 9 ACs covered by integration + E2E + manual QA tests
- **Stories 9.3-9.5:** Deferred to post-MVP

**Edge Cases & Negative Tests:**

- Query with no results (empty Pinecone response)
- Query with low confidence score (< 0.5)
- Unauthorized query (Accountant queries Chief Accountant's voucher)
- Rate limit exceeded (21st query in 1 minute)
- Invalid query input (empty string, > 5000 chars, SQL injection patterns)
- n8n webhook failure (timeout, 500 error, retry exhaustion)
- Pinecone unavailable (503 error)
- OpenAI API failure (timeout, rate limit, 500 error)
- Redis cache unavailable (fallback to direct LLM calls)
- Feature flag disabled (verify voucher workflows unaffected)

**Test Automation:**

- CI/CD pipeline runs unit + integration tests on every commit
- E2E tests run nightly and before release
- Manual QA performed before each story demo
- Performance tests run weekly against staging environment
