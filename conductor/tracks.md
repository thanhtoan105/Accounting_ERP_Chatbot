# Tracks

Master list of all work tracks. Status markers: `[ ]` Pending, `[~]` In Progress, `[x]` Completed.

---

## Active Epics (from bd)

### [~] Epic 7: Financial Reporting & Compliance

#### [ ] Track: Story 7.5 - Reporting Audit, Compliance, and Integrity
*ID: accounting-brq | Priority: 1 | Type: epic*

Full audit trail for report views/exports and chain-of-custody for snapshots per TT200 compliance.

Key Features:
- Audit timeline (who/when/IP/UA, filters, mapping version, snapshot hash)
- File integrity (footer hash, manifest JSON for batch exports)
- Retention (10y WORM storage for closed-period snapshots)
- Unauthorized attempt alerts
- Integrity checks (periodic replay verification)
- Legal holds
- Disaster recovery (cross-region backup)

#### [ ] Track: Story 7.6 - F01 Detailed Ledger Report
*ID: accounting-9j6 | Priority: 1 | Type: epic*

TT200-compliant F01 (Sổ kế toán chi tiết) subsidiary ledger report.

Key Features:
- TT200/ND2004/2014/BTC format templates
- Account + subsidiary detail selection
- DRAFT watermark for open periods
- Posted vouchers only, required dimension validation
- Drill-down to source voucher
- Export: Excel (sheet-protected) + PDF (signature fields)
- Performance: ≤2s for ≤10k rows

---

### [ ] Epic 8: BI Dashboard & Analytics

#### [ ] Track: Story 8.2 - Widget Configuration & Personalization
*ID: accounting-73a | Priority: 1 | Type: epic*

Dashboard widget personalization per Epic 8.2 ACs.

#### [ ] Track: Story 8.3 - Drill-Down, Filtering, Interactive Exploration
*ID: accounting-koo | Priority: 1 | Type: epic*

Interactive drill-down and filtering for BI dashboard.

#### [ ] Track: Story 8.4 - Trends, Forecasts, Predictive Analytics
*ID: accounting-d5c | Priority: 1 | Type: epic*

Trend visualization and simple forecasting.

#### [ ] Track: Story 8.5 - Data Quality, Integrity, BI Security
*ID: accounting-2el | Priority: 1 | Type: epic*

Data quality monitoring and BI security controls.

---

### [~] Epic 9: AI Chatbot (RAG)

#### [ ] Track: Chatbot Widget UI (Frontend)
*ID: accounting-3ik | Priority: 1 | Type: feature*

Implement frontend chatbot widget component per Story 9.0 AC 9.0.3.
- Floating widget, message history, loading states
- Citation rendering with clickable voucher links
- Confidence badges (HIGH/MEDIUM/LOW)

#### [ ] Track: Backend RAG Query Endpoint
*ID: accounting-1gq | Priority: 1 | Type: feature*

Backend `/api/v1/chatbot/query` endpoint.
- REST endpoint with RAG integration
- PineconeService + AzureOpenAIService
- Rate limiting: 20 queries/user/minute

#### [ ] Track: Nightly Batch Embedding
*ID: accounting-mmg | Priority: 2 | Type: feature*

Scheduled batch embedding for existing vouchers.

---

## Technical Debt

#### [ ] Track: Test Coverage Gaps from Epic 6
*ID: accounting-64s | Priority: 2 | Type: task*

Deferred test items from Epic 6 retrospective:
- Story 6.4 performance benchmarking
- Story 6.5 controller integration tests
- Story 6.6 multi-tenant isolation tests
- E2E coverage gaps

---

## Creating New Tracks

Use `/conductor-newtrack [description]` to create a new track with:
- Spec document (requirements, acceptance criteria)
- Phased implementation plan

Or create directly in `conductor/tracks/<track_id>/` with `spec.md` and `plan.md`.
