# Story 9.1: Chatbot Widget Integration and Security

Status: ready-for-dev

## Story

As a user,
I want an in-app chatbot widget that honors my role and data permissions,
so I get secure, context-relevant help without leaving my workflow.

## Acceptance Criteria

1. **AC 9.1.1 - Widget UI:** Chatbot widget loads on all main pages (Dashboard, Vouchers, Sales Invoices, etc.) as a floating panel (bottom-right corner). Widget hideable via close button. Keyboard shortcut `Ctrl+Shift+C` toggles widget. Loading state, error handling, and fallback message if backend/network down.

2. **AC 9.1.2 - RBAC Enforcement:** SSO session and RBAC enforced at API level. Chatbot only responds to questions against data user is allowed to access per role permissions. All queries logged with user context. Unauthorized queries return 403 error with clear message.

3. **AC 9.1.3 - Privacy Mode:** Never show/trace confidential data in suggestions or answers if user lacks permission. Attempts to access restricted data are blocked, logged in `guardrail_logs`, and return "Bạn không có quyền truy cập dữ liệu này" (You don't have permission to access this data).

4. **AC 9.1.4 - Session Management:** Widget UI allows: view full chat history, download conversation as text file, clear session (with confirmation). Conversations stored per user and session ID; hidden from other users unless admin/auditor role.

5. **AC 9.1.5 - Input Sanitization:** Frontend sanitizes user input to detect/prevent dangerous patterns (SQL injection, XSS, code injection, external links). Backend validates query text length (max 5000 chars), blocks unsafe prompts.

6. **AC 9.1.6 - Help & Onboarding:** Help menu includes: chatbot onboarding guide, privacy/data use explainer, report-issue button (opens feedback form with pre-filled context).

7. **AC 9.1.7 - Comprehensive Audit:** All chatbot sessions, queries, errors, and security events logged with: user ID, session ID, chat ID, event timestamp, event hash. Suspicious patterns (e.g., frequent RBAC violations, rapid failed queries) flagged for admin review.

## Tasks / Subtasks

### Frontend Tasks

- [ ] Task 1: Extend ChatbotWidget with keyboard shortcuts (AC: 9.1.1)
  - [ ] 1.1: **EXTEND existing** `src/features/chatbot/components/ChatbotWidget.tsx` (DO NOT recreate)
  - [ ] 1.2: Add keyboard shortcut `Ctrl+Shift+C` using useKeyboardShortcuts hook (see implementation below)
  - [ ] 1.3: Verify feature flag check (`VITE_CHATBOT_ENABLED`) already exists from Story 9.0
  - [ ] 1.4: Create `src/features/chatbot/hooks/useKeyboardShortcuts.ts`:
    ```typescript
    // useKeyboardShortcuts.ts - Keyboard shortcut handler
    import { useEffect, useCallback } from 'react';

    interface ShortcutConfig {
      key: string;
      ctrlKey?: boolean;
      shiftKey?: boolean;
      altKey?: boolean;
      callback: () => void;
    }

    export function useKeyboardShortcuts(shortcuts: ShortcutConfig[]) {
      const handleKeyDown = useCallback((event: KeyboardEvent) => {
        for (const shortcut of shortcuts) {
          const keyMatch = event.key.toLowerCase() === shortcut.key.toLowerCase();
          const ctrlMatch = shortcut.ctrlKey ? event.ctrlKey || event.metaKey : true;
          const shiftMatch = shortcut.shiftKey ? event.shiftKey : true;
          const altMatch = shortcut.altKey ? event.altKey : true;
          
          if (keyMatch && ctrlMatch && shiftMatch && altMatch) {
            event.preventDefault();
            shortcut.callback();
            return;
          }
        }
      }, [shortcuts]);

      useEffect(() => {
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
      }, [handleKeyDown]);
    }

    // Usage in ChatbotWidget:
    // useKeyboardShortcuts([{ key: 'c', ctrlKey: true, shiftKey: true, callback: toggleWidget }]);
    ```

- [ ] Task 2: Create ChatbotPanel and message components (AC: 9.1.1)
  - [ ] 2.1: Create `ChatbotPanel.tsx` with header + tabs (Chat / History / Help)
  - [ ] 2.2: Create `ChatbotMessageList.tsx` to render conversation with citations
  - [ ] 2.3: Create `ChatbotComposer.tsx` for input + send + validation banner
  - [ ] 2.4: Create `ChatbotCitation.tsx` for clickable citation links
  - [ ] 2.5: Add loading states, error handling, and network fallback UI

- [ ] Task 3: Extend useChatbot hook (AC: 9.1.1, 9.1.4)
  - [ ] 3.1: **EXTEND existing** `src/features/chatbot/hooks/useChatbot.ts` (created in Story 9.0)
  - [ ] 3.2: Add rate limiting (429) handling with user-friendly message:
    ```typescript
    if (error?.response?.status === 429) {
      const retryAfter = error.response.headers['retry-after'] || 60;
      setError(`Bạn đã gửi quá nhiều tin nhắn. Vui lòng thử lại sau ${retryAfter} giây.`);
    }
    ```
  - [ ] 3.3: Implement retry logic with exponential backoff

- [ ] Task 4: Implement useChatSession hook (AC: 9.1.4)
  - [ ] 4.1: Create `src/features/chatbot/hooks/useChatSession.ts`
  - [ ] 4.2: Generate and store sessionId in localStorage with key format: `chatbot_session_${userId}_${companyId}`
    ```typescript
    // Session ID generation and storage pattern
    const getOrCreateSessionId = (userId: string, companyId: string): string => {
      const key = `chatbot_session_${userId}_${companyId}`;
      let sessionId = localStorage.getItem(key);
      if (!sessionId) {
        sessionId = crypto.randomUUID();
        localStorage.setItem(key, sessionId);
      }
      return sessionId;
    };
    ```
  - [ ] 4.3: Implement GET `/api/v1/chatbot/history` query
  - [ ] 4.4: Implement DELETE `/api/v1/chatbot/session/{sessionId}` mutation

- [ ] Task 5: Session management UI (AC: 9.1.4)
  - [ ] 5.1: Create `ChatbotSessionActions.tsx` with download, clear, view history buttons
  - [ ] 5.2: Implement download conversation as `.txt` file
  - [ ] 5.3: Add clear session confirmation dialog
  - [ ] 5.4: Ensure session isolation (users can only see their own sessions)

- [ ] Task 6: Input sanitization (AC: 9.1.5)
  - [ ] 6.0: **Install DOMPurify dependency first:**
    ```bash
    cd frontend && pnpm add dompurify @types/dompurify
    ```
  - [ ] 6.1: Create `src/features/chatbot/utils/sanitize.ts` using DOMPurify
  - [ ] 6.2: Implement `sanitizeUserInput()` with strict config (no HTML tags)
  - [ ] 6.3: Add regex detection for SQL keywords, `<script>`, `javascript:` URLs
  - [ ] 6.4: Enforce max length 5000 chars client-side
  - [ ] 6.5: Implement `sanitizeResponse()` for safe markdown rendering

- [ ] Task 7: Help menu and onboarding (AC: 9.1.6)
  - [ ] 7.1: Create `ChatbotHelpMenu.tsx` component
  - [ ] 7.2: Add onboarding guide modal (how to use the chatbot)
  - [ ] 7.3: Add privacy/data use explainer modal
  - [ ] 7.4: Add report-issue button (opens feedback form with context)

- [ ] Task 8: Accessibility (AC: 9.1.1)
  - [ ] 8.1: Add ARIA labels to all interactive elements
  - [ ] 8.2: Implement screen reader live region for new messages
  - [ ] 8.3: Add focus trapping within the widget when open
  - [ ] 8.4: Test with keyboard-only navigation

### Backend Tasks

- [ ] Task 9: GuardrailService implementation (AC: 9.1.2, 9.1.3, 9.1.5)
  - [ ] 9.0: **CREATE NEW SERVICE** - GuardrailService does NOT exist from Story 9.0 (only table was created)
  - [ ] 9.1: Create interface `com.accounting.service.chatbot.GuardrailService`:
    ```java
    public interface GuardrailService {
        ValidationResult validateInput(String queryText, UserContext userContext);
        boolean enforceRBAC(ChatbotQuery query, UserContext userContext);
        boolean enforcePrivacyMode(List<Citation> citations, UserContext userContext);
        void logViolation(String queryText, String reason, UserContext userContext);
    }
    ```
  - [ ] 9.2: Create `GuardrailServiceImpl` with `@Service` and `@RequiredArgsConstructor`
  - [ ] 9.3: Implement `validateInput()` - block unsafe patterns (SQL injection, XSS, prompt injection)
  - [ ] 9.4: Implement `enforceRBAC()` with role-scoped data filtering (see RBAC matrix below)
  - [ ] 9.5: Implement `enforcePrivacyMode()` - strict deny on restricted scope
  - [ ] 9.6: Add regex patterns for detection:
    ```java
    private static final Pattern SQL_INJECTION = Pattern.compile(
        "(?i)(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|ALTER|CREATE|TRUNCATE)\\s",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(<script|javascript:|on\\w+\\s*=)",
        Pattern.CASE_INSENSITIVE
    );
    ```
  - [ ] 9.7: Implement query length validation (max 5000 chars from `chatbot.query.max-length` config)

- [ ] Task 10: ChatbotController security integration (AC: 9.1.2, 9.1.3)
  - [ ] 10.1: Add JWT validation via existing JwtAuthenticationFilter
  - [ ] 10.2: Extract companyId, userId, roles from SecurityContext
  - [ ] 10.3: Call GuardrailService before RAG query processing
  - [ ] 10.4: Return 403 with "RBAC_VIOLATION" error code on unauthorized access
  - [ ] 10.5: Return 400 with "INVALID_QUERY" for blocked input patterns

- [ ] Task 11: Session management endpoints (AC: 9.1.4)
  - [ ] 11.1: Implement GET `/api/v1/chatbot/history` with userId filtering
  - [ ] 11.2: Implement DELETE `/api/v1/chatbot/session/{sessionId}` with ownership check
  - [ ] 11.3: Add pagination to history endpoint (limit, cursor params)
  - [ ] 11.4: Ensure session isolation - users can only access their own sessions

- [ ] Task 12: Rate limiting (AC: 9.1.2)
  - [ ] 12.0: **Choose implementation approach:** Bucket4j (recommended) or Spring @RateLimiter
  - [ ] 12.1: Add Bucket4j Maven dependency:
    ```xml
    <!-- pom.xml -->
    <dependency>
        <groupId>com.bucket4j</groupId>
        <artifactId>bucket4j-core</artifactId>
        <version>8.10.1</version>
    </dependency>
    <dependency>
        <groupId>com.bucket4j</groupId>
        <artifactId>bucket4j-redis</artifactId>
        <version>8.10.1</version>
    </dependency>
    ```
  - [ ] 12.2: Create `ChatbotRateLimiter` service:
    ```java
    @Service
    @RequiredArgsConstructor
    public class ChatbotRateLimiter {
        private final RedisTemplate<String, String> redisTemplate;
        
        @Value("${chatbot.rate-limit.queries-per-minute:20}")
        private int queriesPerMinute;
        
        public boolean tryConsume(Long userId) {
            String key = "chatbot:ratelimit:user:" + userId;
            Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.classic(queriesPerMinute, Refill.greedy(queriesPerMinute, Duration.ofMinutes(1))))
                .build();
            return bucket.tryConsume(1);
        }
        
        public long getRetryAfterSeconds(Long userId) {
            // Return seconds until next available token
            return 60; // Simplified: always 60s for rate limit window
        }
    }
    ```
  - [ ] 12.3: Integrate in ChatbotController before processing query
  - [ ] 12.4: Return 429 with `Retry-After` header:
    ```java
    if (!rateLimiter.tryConsume(userId)) {
        throw new RateLimitExceededException(rateLimiter.getRetryAfterSeconds(userId));
    }
    ```
  - [ ] 12.5: Log rate limit events with user context via AuditService

- [ ] Task 13: Comprehensive audit logging (AC: 9.1.7)
  - [ ] 13.0: **CHOICE:** Create dedicated `ChatbotAuditService` OR extend existing `AuditService`
    - **Recommended:** Create `ChatbotAuditService` that delegates to `AuditService` for consistency
  - [ ] 13.1: Define audit event types enum:
    ```java
    public enum ChatbotAuditEventType {
        CHATBOT_QUERY_SUBMITTED,
        CHATBOT_QUERY_BLOCKED,
        CHATBOT_RESPONSE_RETURNED,
        CHATBOT_SESSION_CLEARED,
        CHATBOT_HISTORY_DOWNLOADED,
        CHATBOT_RATE_LIMITED
    }
    ```
  - [ ] 13.2: Compute eventHash = SHA-256(userId + sessionId + timestamp + eventType + payloadDigest)
  - [ ] 13.3: Integrate with existing AuditService for append-only logging
  - [ ] 13.4: Log to guardrail_logs table for RBAC violations
  - [ ] 13.5: Implement suspicious pattern detection (>10 violations/hour → flag for review)

- [ ] Task 14: Flyway migrations (AC: 9.1.7)
  - [ ] 14.1: Create `V20251220001__add_session_id_to_chatbot_queries.sql` (if needed)
  - [ ] 14.2: Add indexes on session_id, created_at for history queries

### Testing Tasks

- [ ] Task 15: Frontend tests (AC: 9.1.1, 9.1.5)
  - [ ] 15.1: Unit tests for sanitizeUserInput with malicious inputs
  - [ ] 15.2: Unit tests for useChatbot hook with mocked API
  - [ ] 15.3: Component tests for ChatbotWidget mount in ProtectedLayout
  - [ ] 15.4: Test keyboard shortcut Ctrl+Shift+C toggle
  - [ ] 15.5: Test error fallback UI on network failure

- [ ] Task 16: Backend security tests (AC: 9.1.2, 9.1.3, 9.1.5)
  - [ ] 16.1: RBAC matrix tests: each role vs entity scope (allowed/denied)
  - [ ] 16.2: Test Accountant cannot query Chief Accountant's vouchers
  - [ ] 16.3: Test guardrail blocks SQL injection patterns
  - [ ] 16.4: Test guardrail blocks XSS attempts
  - [ ] 16.5: Test query length validation (>5000 chars rejected)
  - [ ] 16.6: Test rate limiting (21st query returns 429)

- [ ] Task 17: Session management tests (AC: 9.1.4)
  - [ ] 17.1: Test GET /history returns only user's own sessions
  - [ ] 17.2: Test DELETE session with ownership check
  - [ ] 17.3: Test session isolation - user A cannot see user B's sessions

- [ ] Task 18: Audit logging tests (AC: 9.1.7)
  - [ ] 18.1: Verify AuditService entries for each event type
  - [ ] 18.2: Verify eventHash generation
  - [ ] 18.3: Verify guardrail_logs entries for RBAC violations
  - [ ] 18.4: Test suspicious pattern flagging

- [ ] Task 19: E2E tests with Playwright (AC: 9.1.1, 9.1.4, 9.1.6)
  - [ ] 19.1: Widget opens/closes on button click
  - [ ] 19.2: Widget toggles with Ctrl+Shift+C
  - [ ] 19.3: Type query, receive response with citations
  - [ ] 19.4: Download conversation as text file
  - [ ] 19.5: Clear session with confirmation
  - [ ] 19.6: Help menu displays onboarding and privacy explainer

## Dev Notes

### ⚠️ BUILD UPON STORY 9.0 - CRITICAL CONTEXT

**Story 9.0 (Status: done)** created the foundational chatbot infrastructure. This story EXTENDS it with security features.

**Existing Components to REUSE (do NOT recreate):**
- `ChatbotWidget.tsx` - Already exists at `frontend/src/features/chatbot/components/` - EXTEND it
- `useChatbot.ts` hook - Already exists - ADD rate limit handling (429)
- `ChatbotController.java` - Already exists - ADD GuardrailService integration
- `ChatbotService.java` - Already exists - EXTEND with security checks
- `chatbot_queries` table - Already exists via `V20251229__create_chatbot_tables.sql`
- `guardrail_logs` table - Already exists (placeholder from 9.0)

**Patterns from Story 9.0 to Follow:**
- Service interface + implementation pattern (see `ChatbotService.java` / `ChatbotServiceImpl.java`)
- `@ConditionalOnProperty(prefix = "chatbot", name = "enabled")` for feature flags
- Constructor injection with `@RequiredArgsConstructor` (Lombok)
- JWT context extraction: `CompanyContext.getCompanyId()`, `SecurityUtils.getCurrentUserId()`
- Confidence scoring formula: `(avgScore × 0.7) + (citationCount/5 × 0.3)`

**Story 9.0 File Locations (Reference):**
- Backend services: `backend/src/main/java/com/accounting/service/`
- Frontend components: `frontend/src/features/chatbot/components/`
- Frontend hooks: `frontend/src/features/chatbot/hooks/`
- Migrations: `backend/src/main/resources/db/migration/`

---

### Architecture Decisions

**Frontend Architecture (Oracle Recommendation):**
- Mount `ChatbotWidget` once inside `ProtectedLayout` for presence on all authenticated routes
- Use `@tanstack/react-query` for server state (queries/history)
- Use local component state for UI (open/close, input, privacy mode, help menu)
- Store `sessionId` in `localStorage` keyed by userId/companyId to prevent cross-user leakage

**Component Structure:**
```
src/features/chatbot/
├── components/
│   ├── ChatbotWidget.tsx       # Container with open/close, keyboard shortcut
│   ├── ChatbotPanel.tsx        # Header + tabs (Chat / History / Help)
│   ├── ChatbotMessageList.tsx  # Conversation + citations
│   ├── ChatbotComposer.tsx     # Input + send + validation
│   ├── ChatbotCitation.tsx     # Clickable citation links
│   ├── ChatbotSessionActions.tsx # Download, clear, view history
│   └── ChatbotHelpMenu.tsx     # Onboarding, privacy, report issue
├── hooks/
│   ├── useChatbot.ts           # POST /chatbot/query mutation
│   ├── useChatSession.ts       # Session management, history
│   └── useKeyboardShortcuts.ts # Keyboard shortcut handler
├── utils/
│   └── sanitize.ts             # DOMPurify input/output sanitization
└── index.ts
```

**Backend Service Layering:**
```
ChatbotController
    └── ChatbotService (orchestration)
            ├── GuardrailService (validate → RBAC → privacy mode)
            ├── RAGQueryService (Pinecone retrieval with metadata filters)
            └── ChatbotAuditService (append-only event logging)
```

**RBAC Enforcement Strategy:**

RBAC is enforced at the **RAGQueryService level** via Pinecone metadata filters:

| Role | Data Scope | Pinecone Filter Implementation |
|------|------------|-------------------------------|
| Accountant | Own vouchers only | `metadata.created_by == userId` |
| Chief Accountant | All company data | `metadata.company_id == companyId` (no created_by filter) |
| CFO | View-only all data | `metadata.company_id == companyId` (no created_by filter) |
| Admin | Full access + logs | `metadata.company_id == companyId` + guardrail_logs access |

**Implementation in RAGQueryServiceImpl:**
```java
private Map<String, Object> buildRBACFilter(Long companyId, Long userId, String role) {
    Map<String, Object> filter = new HashMap<>();
    filter.put("company_id", companyId.toString());
    
    // Role-based scoping
    if ("ACCOUNTANT".equals(role)) {
        // Accountants can only see their own vouchers
        filter.put("created_by", userId.toString());
    }
    // Chief Accountant, CFO, Admin see all company data (no created_by filter)
    
    return filter;
}
```

**Privacy Mode Implementation:**
- Default deny: block any response that cannot be strictly backed by permitted data
- If citations reference restricted entities → block response and log violation
- Return localized error: "Bạn không có quyền truy cập dữ liệu này"

### Input Sanitization Strategy (Librarian Pattern)

Using DOMPurify with strict configuration:

```typescript
// CHAT_INPUT_CONFIG - for user input (no HTML allowed)
const CHAT_INPUT_CONFIG: DOMPurify.Config = {
  ALLOWED_TAGS: [],
  ALLOWED_ATTR: [],
  KEEP_CONTENT: true,
  SANITIZE_DOM: true,
  SANITIZE_NAMED_PROPS: true,
};

// CHAT_RESPONSE_CONFIG - for markdown rendering (safe subset)
const CHAT_RESPONSE_CONFIG: DOMPurify.Config = {
  ALLOWED_TAGS: ["p", "br", "strong", "em", "code", "pre", "ul", "ol", "li", "a", "span"],
  ALLOWED_ATTR: ["href", "target", "rel", "class"],
  FORBID_TAGS: ["script", "style", "iframe", "form", "input"],
  FORBID_ATTR: ["onerror", "onload", "onclick", "onmouseover"],
};
```

**Additional Validations:**
- Max length: 5000 characters
- Regex patterns to detect: SQL keywords, `<script>`, `javascript:` URLs
- Disallow external links in user input
- Remove control characters

### Audit Event Types

| Event Type | Description | Logged To |
|------------|-------------|-----------|
| `CHATBOT_QUERY_SUBMITTED` | User submitted a query | chatbot_queries, AuditService |
| `CHATBOT_QUERY_BLOCKED` | Query blocked by guardrails | guardrail_logs, AuditService |
| `CHATBOT_RESPONSE_RETURNED` | Successful response with citations | chatbot_queries |
| `CHATBOT_SESSION_CLEARED` | User cleared their session | AuditService |
| `CHATBOT_HISTORY_DOWNLOADED` | User downloaded conversation | AuditService |
| `CHATBOT_RATE_LIMITED` | Rate limit exceeded | AuditService |

### Accessibility Requirements

- All interactive elements have ARIA labels
- Screen reader live region announces new messages
- Focus trapping within widget when open
- Keyboard shortcut `Ctrl+Shift+C` for toggle, `Escape` to close
- `Enter` to send (configurable to `Ctrl+Enter`)

### Project Structure Notes

**Frontend paths:**
- Components: `src/features/chatbot/components/`
- Hooks: `src/features/chatbot/hooks/`
- Utils: `src/features/chatbot/utils/`
- Mount point: `src/layouts/ProtectedLayout.tsx`

**Backend paths:**
- Controller: `com.accounting.controller.ChatbotController` (extend existing)
- Service: `com.accounting.service.chatbot.GuardrailService`
- Audit: `com.accounting.service.chatbot.ChatbotAuditService`
- Migrations: `backend/src/main/resources/db/migration/`

**Existing code to integrate with:**
- `JwtAuthenticationFilter` for JWT validation
- `CompanyContext` for multi-tenancy
- `AuditService` for append-only audit logging
- `@PreAuthorize` annotations for method-level security

### References

- [Source: docs/sprint-artifacts/tech-spec-epic-9.md] - Full technical specification
- [Source: docs/epics/epic-9-ai-rag-chatbot-contextual-help.md] - Epic and story definitions
- [Source: docs/architecture/security-architecture.md] - JWT, RBAC, audit trail patterns
- [Source: docs/architecture/project-structure.md] - Directory organization
- [Librarian: Vercel AI SDK useChat hook] - Chat session management pattern
- [Librarian: DOMPurify] - Input sanitization patterns
- [Librarian: shadcn/ui Sheet] - Floating panel component
- [Librarian: Radix Dialog] - Accessibility patterns

### Dependencies to Add

**Frontend (package.json):**
```json
{
  "dompurify": "^3.0.0",
  "@types/dompurify": "^3.0.0",
  "react-markdown": "^9.0.0",
  "@tailwindcss/typography": "^0.5.13"
}
```

**Backend (pom.xml):**
- No new dependencies required (existing Spring Security, Redis sufficient)

### Key Integration Points

1. **ProtectedLayout.tsx**: Add `<ChatbotWidget />` component
2. **feature flag**: Check `import.meta.env.VITE_CHATBOT_ENABLED` before rendering
3. **ChatbotController**: Inject `GuardrailService`, call before RAG processing
4. **Rate Limiter**: Use Redis-backed Bucket4j or Spring's `@RateLimiter`

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

## Validation Report

**Validator:** Scrum Master Agent (Bob)
**Date:** 2025-12-20
**Checklist:** `_bmad/bmm/workflows/4-implementation/create-story/checklist.md`

### Improvements Applied

| # | Issue Type | Description | Fix Applied |
|---|------------|-------------|-------------|
| 1 | ✗ CRITICAL | Missing useKeyboardShortcuts hook implementation | Added complete implementation code in Task 1.4 |
| 2 | ✗ CRITICAL | Missing Rate Limiter backend pattern | Added Bucket4j implementation with code example in Task 12 |
| 3 | ⚠ PARTIAL | GuardrailService needs CREATE not EXTEND | Clarified as CREATE NEW SERVICE in Task 9.0 with interface code |
| 4 | ⚠ PARTIAL | DOMPurify dependency not installed | Added pnpm install step in Task 6.0 |
| 5 | ⚡ ENHANCE | Missing ChatbotAuditService guidance | Added choice guidance and enum in Task 13 |
| 6 | ⚡ ENHANCE | RBAC matrix missing implementation details | Added Pinecone filter implementation code |
| 7 | ⚡ ENHANCE | Missing Story 9.0 learnings integration | Added "BUILD UPON STORY 9.0" section |
| 8 | ⚡ ENHANCE | Session ID storage pattern incomplete | Added explicit localStorage key format and code example |
| 9 | ✨ OPTIMIZE | AC numbering inconsistent (#1 vs 9.1.1) | Standardized all ACs to 9.1.x format |
| 10 | ✨ OPTIMIZE | useChatbot marked as CREATE | Changed to EXTEND existing hook from Story 9.0 |
| 11 | ✨ OPTIMIZE | Rate limit 429 handling missing | Added frontend error handling code in Task 3.2 |

### Post-Validation Score

- **Overall:** 27/27 items (100%)
- **Critical Issues:** 0 (fixed)
- **Story Status:** ✅ Ready for Development

### Recommended Dev Agent Execution Order

1. **Backend First (security foundation):**
   - Task 9 → Task 12 → Task 10 → Task 13 → Task 11 → Task 14
2. **Frontend Second (UI + security integration):**
   - Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 → Task 7 → Task 8
3. **Testing Last:**
   - Task 15 → Task 16 → Task 17 → Task 18 → Task 19
