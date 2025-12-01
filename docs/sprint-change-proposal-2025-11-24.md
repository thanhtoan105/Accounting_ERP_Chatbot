# Sprint Change Proposal - Deadline-Driven Scope Adjustment

**Date:** 2025-11-24  
**Trigger:** Deadline pressure (3 days until demo) requiring scope reduction to ensure demo-ready features  
**Status:** Approved  
**Change Scope:** Moderate (requires backlog reorganization and story modifications)

---

## 1. Issue Summary

### Problem Statement
With only 3 days remaining until the demo presentation, the current sprint scope includes incomplete stories and missing critical features required for a complete accounting system demonstration. The project needs to deliver:
- A complete, usable accounting website
- Core financial reports (Trial Balance, Balance Sheet, Income Statement)
- BI Dashboard integration (Metabase)
- AI RAG Chatbot functionality

### Discovery Context
- **Story 5.6 (Revenue & VAT Handling)**: Status "drafted" - implementation in progress but not complete
- **Epic 7 (Financial Reports)**: All stories in backlog - not started
- **Epic 8 (BI Dashboard)**: All stories in backlog - not started  
- **Epic 9 (AI Chatbot)**: All stories in backlog - not started

### Evidence
- Sprint status shows Epic 5 incomplete (5.6 drafted, 5.7 backlog)
- Epics 7-9 entirely in backlog
- Demo requires complete system with reports, dashboard, and AI features
- Current implementation focus on core accounting flows (Epic 1-5) is solid but missing reporting/analytics layer

---

## 2. Impact Analysis

### Epic Impact Assessment

#### Epic 5: Accounts Receivable (AR) Module
**Current Status:**
- Stories 5.1-5.5: ✅ DONE
- Story 5.6: ⏳ DRAFTED (in progress)
- Story 5.7: 📋 BACKLOG

**Required Changes:**
- **Story 5.6**: Complete with **reduced scope** - focus on basic VAT calculation only
  - KEEP: VAT rate validation, basic VAT calculation, GL splits, basic validation
  - DEFER: VAT reports (ND123 export), VAT corrections workflow, credit notes
- **Story 5.7**: Defer to post-demo (audit trail already covered in Epic 1-4)

**Impact:** Low - Core AR functionality (5.1-5.5) is complete. Story 5.6 with basic VAT is sufficient for demo.

#### Epic 7: Financial Reports
**Current Status:**
- All stories: 📋 BACKLOG

**Required Changes:**
- **Story 7.1 MVP**: Implement basic Trial Balance (S06-DN)
  - Basic period selector and account listing
  - Simple Excel export (defer PDF, drill-down, advanced validation)
- **Story 7.2 MVP**: Implement basic Balance Sheet (B01-DN) and Income Statement (B02-DN)
  - Basic templates with hardcoded account mappings for MVP
  - Simple Excel export
  - Defer: Multi-period comparison, variance analysis, advanced mapping
- **Stories 7.3-7.6**: Defer completely to post-demo

**Impact:** Medium - Financial reports are demo-critical but MVP scope is sufficient.

#### Epic 8: BI Dashboard & Analytics
**Current Status:**
- All stories: 📋 BACKLOG

**Required Changes:**
- **Metabase Integration MVP**: Embed Metabase dashboard with JWT SSO
  - Install and configure Metabase (Docker or standalone)
  - Connect to existing PostgreSQL database
  - Create basic dashboards (Revenue vs Expenses, AR/AP balances, Cash position)
  - Embed in React app using Metabase Embedding SDK with JWT authentication
  - Defer: Custom data pipeline, widget personalization, advanced drill-down, forecasting

**Impact:** Low - Metabase handles most complexity. Integration is straightforward.

#### Epic 9: AI RAG Chatbot
**Current Status:**
- All stories: 📋 BACKLOG

**Required Changes:**
- **Story 9.0 MVP**: Implement Voucher-Focused RAG
  - Setup Pinecone index and connection
  - Basic embedding pipeline for voucher data
  - Simple chatbot UI component (React)
  - Basic Q&A endpoint (`/api/v1/chatbot/query`)
  - Vietnamese language support
  - Citation display (voucher IDs with links)
  - Defer: Advanced security, full documentation RAG, workflow automation, feedback system, analytics

**Impact:** Medium - RAG setup requires Pinecone configuration and embedding pipeline, but MVP scope is manageable.

### Artifact Conflict Analysis

#### PRD Impact
- **No conflicts** - MVP scope aligns with core PRD objectives
- Deferred features are enhancements, not core requirements

#### Architecture Impact
- **No conflicts** - Architecture already supports:
  - Metabase integration (standard PostgreSQL connection)
  - Pinecone integration (ADR-004 already decided)
  - Report generation (existing voucher/GL data structure)

#### UI/UX Impact
- **Minimal** - New pages/components needed:
  - Financial Reports page (Trial Balance, Balance Sheet, Income Statement)
  - Metabase dashboard embed page
  - Chatbot widget component
- All can use existing shadcn/ui components and patterns

---

## 3. Recommended Approach

### Selected Path: Hybrid - Complete Essential Features with MVP Scope

**Rationale:**
1. **Story 5.6**: Essential for AR completeness - complete with basic VAT scope (4-6 hours)
2. **Epic 7**: Core financial reports are demo-critical - MVP sufficient (6-8 hours)
3. **Epic 8**: Metabase provides quick dashboard solution - low effort, high value (3-4 hours)
4. **Epic 9**: AI chatbot is differentiating feature - MVP shows capability (6-8 hours)

**Total Estimated Effort:** 19-26 hours (fits within 3-day window with focused work)

### Effort Distribution

**Day 1:**
- Morning (4h): Complete Story 5.6 with basic VAT calculation
- Afternoon (4h): Setup Metabase integration and basic dashboards

**Day 2:**
- Morning (6h): Implement Epic 7 MVP (Trial Balance, Balance Sheet, Income Statement)
- Afternoon (2h): Setup Epic 9 RAG MVP (Pinecone connection, basic embedding)

**Day 3:**
- Morning (4h): Integration testing and bug fixes
- Afternoon (4h): Demo script preparation and documentation

### Risk Assessment

| Feature | Risk Level | Mitigation |
|---------|-----------|------------|
| Story 5.6 (Basic VAT) | Low | Already in progress, well-understood |
| Metabase Integration | Low | Proven tool, simple JWT SSO integration |
| Epic 7 Reports | Medium | Requires careful testing, use existing GL data |
| Epic 9 RAG MVP | Medium | Pinecone setup, embedding pipeline needs testing |

---

## 4. Detailed Change Proposals

### Change Proposal 1: Story 5.6 Scope Reduction

**Story:** [STORY-5-6] Revenue & VAT Handling  
**Section:** Acceptance Criteria and Tasks

**OLD:**
- Full implementation including VAT reports (AC-VAT-005), VAT corrections (AC-VAT-006), credit notes (AC-VAT-004)

**NEW:**
- **KEEP (Essential):**
  - AC-VAT-001: VAT rate validation with override warning
  - AC-VAT-002: GL splits on post (Dr AR, Cr Revenue, Cr VAT Output)
  - AC-VAT-003: Basic VAT totals validation
  - AC-VAT-007: Idempotent posting
- **DEFER (Post-demo):**
  - AC-VAT-004: Credit notes (mark as future enhancement)
  - AC-VAT-005: VAT reports and ND123 export (mark as future enhancement)
  - AC-VAT-006: VAT corrections workflow (mark as future enhancement)

**Rationale:** Basic VAT calculation is sufficient for demo. Advanced features can be added post-demo without affecting core functionality.

**Files to Modify:**
- `docs/sprint-artifacts/stories/5-6-revenue-vat-handling.md` - Update status and add deferral notes
- `docs/sprint-artifacts/sprint-status.yaml` - Update story status

---

### Change Proposal 2: Epic 7 MVP Implementation

**Epic:** [EPIC-7] Financial Reports  
**Stories:** 7.1 MVP, 7.2 MVP (new stories)

**NEW Stories to Create:**

**Story 7.1-MVP: Trial Balance (S06-DN) - Basic**
- Period selector (last 3 periods)
- Account listing with opening Dr/Cr, period Dr/Cr, closing Dr/Cr
- Basic balance validation (sum Dr = sum Cr)
- Simple Excel export (no PDF, no drill-down)
- Defer: Advanced validation, drill-down, PDF export, snapshot reproducibility

**Story 7.2-MVP: Balance Sheet & Income Statement - Basic**
- Basic B01-DN (Balance Sheet) template
- Basic B02-DN (Income Statement) template
- Hardcoded account mappings for MVP (can be configurable later)
- Simple Excel export
- Defer: Multi-period comparison, variance analysis, advanced mapping, PDF export

**Rationale:** Core financial reports are essential for demo. MVP scope provides functional reports without advanced features.

**Files to Create:**
- `docs/sprint-artifacts/stories/7-1-mvp-trial-balance-basic.md`
- `docs/sprint-artifacts/stories/7-2-mvp-balance-sheet-income-statement-basic.md`

**Files to Modify:**
- `docs/sprint-artifacts/sprint-status.yaml` - Add new story entries

---

### Change Proposal 3: Epic 8 Metabase Integration

**Epic:** [EPIC-8] BI Dashboard  
**Approach:** Metabase Integration MVP (new story)

**NEW Story to Create:**

**Story 8.0-MVP: Metabase Dashboard Integration**
- Install and configure Metabase (Docker or standalone)
- Connect Metabase to existing PostgreSQL database
- Create basic dashboards:
  - Revenue vs Expenses (current period)
  - AR/AP balances summary
  - Cash position
- Implement JWT SSO authentication for Metabase
- Embed Metabase dashboard in React app using `@metabase/embedding-sdk-react`
- Company-scoped data access (Metabase filters by company_id)
- Defer: Custom data pipeline, widget personalization, advanced drill-down, forecasting, analytics

**Rationale:** Metabase provides enterprise-grade BI capabilities with minimal development effort. Integration is straightforward and provides immediate value.

**Files to Create:**
- `docs/sprint-artifacts/stories/8-0-mvp-metabase-integration.md`

**Files to Modify:**
- `docs/sprint-artifacts/sprint-status.yaml` - Add new story entry
- `docs/architecture/architecture-decision-records-adrs.md` - Add ADR for Metabase integration

---

### Change Proposal 4: Epic 9 RAG Chatbot MVP

**Epic:** [EPIC-9] AI RAG Chatbot  
**Story:** 9.0 MVP (new story, based on existing Story 9.0 but simplified)

**NEW Story to Create:**

**Story 9.0-MVP: Voucher-Focused RAG Chatbot - Basic**
- Setup Pinecone index and connection (using existing ADR-004 decision)
- Basic embedding pipeline: embed voucher data (header, line items, customer/vendor info) on voucher save/post
- Simple chatbot UI component (React, floating widget)
- Basic Q&A endpoint: `POST /api/v1/chatbot/query`
  - Hybrid search (semantic + keyword) over Pinecone
  - Returns answer with citations (voucher IDs)
- Vietnamese language support
- Basic error handling and "I don't know" responses
- Defer: Advanced security, full documentation RAG, workflow automation, feedback system, analytics

**Rationale:** RAG chatbot is a differentiating feature. MVP demonstrates capability with voucher data, can be extended post-demo.

**Files to Create:**
- `docs/sprint-artifacts/stories/9-0-mvp-voucher-rag-chatbot-basic.md`

**Files to Modify:**
- `docs/sprint-artifacts/sprint-status.yaml` - Add new story entry

---

## 5. Implementation Handoff

### Change Scope Classification: **Moderate**

Requires backlog reorganization and story modifications. Development team can implement directly with PM guidance.

### Handoff Recipients

**Primary:**
- **Development Team**: Implement MVP features per change proposals
- **Product Manager (PM)**: Review and approve story modifications, provide guidance

**Supporting:**
- **Architecture**: Review Metabase integration approach (if needed)
- **Testing**: Focus on critical path testing for demo scenarios

### Responsibilities

**Development Team:**
1. Complete Story 5.6 with basic VAT scope
2. Implement Epic 7 MVP (Stories 7.1-MVP, 7.2-MVP)
3. Implement Epic 8 MVP (Story 8.0-MVP - Metabase integration)
4. Implement Epic 9 MVP (Story 9.0-MVP - RAG chatbot)
5. Integration testing and bug fixes
6. Demo preparation

**Product Manager:**
1. Review and approve all story modifications
2. Provide clarification on MVP scope boundaries
3. Review demo script and ensure coverage

**Success Criteria:**
- ✅ Story 5.6 completed with basic VAT functionality
- ✅ Trial Balance, Balance Sheet, Income Statement reports functional
- ✅ Metabase dashboard embedded and accessible
- ✅ RAG chatbot answers voucher-related questions with citations
- ✅ All features tested and demo-ready
- ✅ Demo script prepared with clear user flows

---

## 6. Next Steps

1. **Immediate (Today):**
   - Update `sprint-status.yaml` with new story entries and status changes
   - Create new MVP story files for Epic 7, 8, 9
   - Update Story 5.6 with scope reduction notes

2. **Day 1:**
   - Development: Complete Story 5.6, setup Metabase

3. **Day 2:**
   - Development: Implement Epic 7 MVP, setup Epic 9 RAG MVP

4. **Day 3:**
   - Development: Integration testing, bug fixes
   - PM: Demo script preparation, final review

---

## Approval

**Status:** ✅ Approved  
**Approved By:** thanhtoan  
**Date:** 2025-11-24  
**Next Action:** Update sprint status and create MVP story files

---

_Generated by BMAD Correct Course Workflow_  
_Date: 2025-11-24_

