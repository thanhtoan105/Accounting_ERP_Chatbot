# Epic 9: AI RAG Chatbot & Contextual Help

**Expanded Goal:**
Deliver an AI-powered Retrieval-Augmented Generation (RAG) chatbot tightly integrated with the accounting and documentation system. Enable secure, traceable contextual Q&A, semantic search over docs and ledgers, workflow assistance, feedback capture, and usage analytics—drives productivity, support, and regulatory compliance.

```markdown
**Story 9.0: Voucher-Focused RAG MVP (n8n Pipeline)**
As an accountant or reviewer, I want a working chatbot that can answer voucher / AR / AP questions with citations based on freshly created vouchers, so that stakeholders can validate the RAG integration early in the program.
**Acceptance Criteria:**
1. After every successful voucher save/post, the backend triggers an n8n webhook that receives company ID, voucher header, line items, related customers/vendors, and summary balances for embedding into Pinecone.
2. Embeddings are stored per company namespace; re-indexing is idempotent and handles retries/logging if n8n is unavailable.
3. A minimal in-app chatbot panel allows Vietnamese questions like "Tình hình công nợ hiện tại ra sao?", calling a backend `/chatbot/query` endpoint that performs hybrid retrieval over Pinecone + ledger aggregates.
4. Responses always include: natural-language answer, citation list (voucher/invoice IDs with links or reference numbers), and confidence indicator; if no evidence is found, chatbot replies with "Không đủ dữ liệu" and suggests next steps.
5. Each chatbot query is audit-logged with user, company, timestamp, prompt, answer summary, and citation references; errors are surfaced to the user with retry guidance.
6. Feature flag or environment toggle allows disabling the chatbot without affecting voucher flows.
**Prerequisites:** Story 3.2, PRD FR38–FR41
```

```markdown
**Story 9.1: Chatbot Widget Integration and Security**
As a user, I want an in-app chatbot widget that honors my role and data permissions, so I get secure, context-relevant help without leaving my workflow.
**Acceptance Criteria:**
1. Widget loads on all main pages (hideable, floating, accessible via shortcut); loading state, error handling, and fallback if backend/network down.
2. SSO session and RBAC enforced—chatbot only responds to questions against data and docs user is allowed to access; all queries logged with user and context.
3. Privacy mode: never shows/show traces of confidential data in suggestions or answers if user lacks permission; attempts blocked and logged.
4. Widget UI allows full audit/download/clear chat; conversations stored user-by-user and hidden from other roles unless permitted (admin/audit).
5. Frontend sanitization: detects/prevents dangerous input/output code injection/links.
6. Help menu has chatbot onboarding, privacy/data use explainer, and report-issue button.
7. All sessions, queries, and errors are audit-logged with user/session, chat ID, and event hash; suspicious/frequent error patterns flagged for review.
**Prerequisites:** Epic 8
```

```markdown
**Story 9.2: Contextual Retrieval-Augmented Generation (RAG) for Q&A**
As a user, I want to ask natural language questions and get relevant, cited answers from our documentation, policies, and select (read-only) ledger data, so that I can self-serve support and compliance needs.
**Acceptance Criteria:**
1. Chatbot uses hybrid search (semantic + keyword + metadata filters) to locate relevant doc/ledger excerpts; supports Vietnamese and English queries.
2. Every answer includes citations: clickable links to doc/transaction source, summary at top, full trace chain on expand; answers never returned without source.
3. Chatbot never fabricates accounting figures/numbers; always pulls from actual transactions/docs with timestamp and context; guardrails block if evidence unavailable.
4. For queries requesting ledger info: required filters are enforced (company, period, role), configurable answer detail level per role (e.g., no drilldown for clerk).
5. Error handling: if data out-of-date or index failed, clear error given and fallback with workaround suggestion (contact support, link).
6. Hardcoded "I don't know" or redirect-to-human triggers if system confidence < threshold; explain reason and show confidence badge to user.
7. User can refine/follow-up question, reset context, view prior history thread.
8. Indexed data audited with version/timestamp in answer; model outputs rate-limited and monitored for drift/hallucinations; feedback linked.
9. New docs/ledger data indexed nightly and after every major release; indexing logs errors per doc/tx and required admin review to close gaps.
**Prerequisites:** Story 9.1
```

```markdown
**Story 9.3: Workflow Automation and Guided Journeys**
As a user, I want the chatbot to initiate and guide me through frequent workflows (e.g., "how to post a journal", "how to upload opening balance"), so I resolve issues faster and reduce errors.
**Acceptance Criteria:**
1. FAQ trigger phrases recognized; chatbot offers walk-through with step-by-step guide (text + checklist + links to screen sections).
2. Guided flows context-aware: tracks user location (screen/module) to give inline advice or navigation quick-links; e.g., points to voucher screen when asked about posting.
3. Suggests related actions/documents (e.g., links "see VAT export" after VAT Q&A, proposes "open reconciliation screen").
4. Step completion tracking: user can check-off inline and review previous steps; chatbot saves incomplete sessions for resume.
5. Tracks failed/frustrated interaction patterns, prompts for feedback or offers escalation to human.
6. Escalation flows: optionally open support ticket (pre-filled context), email admin, or join group support room.
7. All automation/integration actions audited (user, session, screen, parameters, outcome).
8. Model supports controlled plug-ins, e.g., n8n task triggers, only for white-listed flows (admin-editable list).
**Prerequisites:** Story 9.2
```

```markdown
**Story 9.4: Feedback, Training Data Capture, and Guardrail Monitoring**
As a product owner/auditor, I want to capture user feedback and monitor system guardrails, so the AI assistant continuously improves without compliance risk.
**Acceptance Criteria:**
1. After every answer, user can rate (helpful/not, with comment); open issue reports go to admin inbox and are ticketed/audited.
2. Feedback integrated into RAG retraining/refresh process (weekly) with admin review step; no end-user PI or sensitive data leaks to models without explicit consent.
3. Guardrail logs: each time AI/LLM blocks/restricts answer, creates context+reason record; admin view shows top blocked queries, breakdown by type, user, and trend.
4. Periodic reviews required of feedback, blocked queries, and model audit logs (weekly or after major update); all reviews/audits are themselves tracked with approval and closure structure.
5. User requests for access/override of restricted responses require approval workflow and audit (with full event chain/log).
6. Admins can download anonymized feedback and guardrail data for regulatory submission or security review; purge requires dual signoff.
**Prerequisites:** Story 9.3
```

```markdown
**Story 9.5: Usage Analytics and Continuous Improvement**
As a product owner or admin, I want comprehensive analytics on chatbot usage, answer quality, and operational issues, so we can prove value and drive iterative enhancement.
**Acceptance Criteria:**
1. Dashboard shows query volume, unique users, active/inactive users by role/company, success/deflection metrics, QA scores, feedback trends.
2. Tracks average latency per question, slowest queries, fallbacks/"I don't know" rates, escalation counts, top repeated issues.
3. Quality metrics: answer traceability (with sources), manual QA scoring, feedback outcome over time with drilldown by answer and user segment.
4. Monitors and alerts for abnormal failures (data mismatch, hallucination detection, RBAC policy violation, API faults); escalation triggers admin notification.
5. Tracks retraining/model update events, with sharpness/drift benchmarks after each deploy; auto alerts for drops in accuracy/conformance.
6. Export/download permitted for anonymized data for audit/regulatory/compliance; direct connection to DR/backup process.
**Prerequisites:** Story 9.4
```
