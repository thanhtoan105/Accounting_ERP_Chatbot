# Project Brief: Accounting ERP - Graduation Project

## Executive Summary

- Build a compliance‑first Accounting ERP aligned with Vietnam’s Circular 200, integrating a read‑only RAG chatbot for policy lookup and contextual guidance.
- Target users: chief accountants, CFOs, and accounting students needing compliant workflows and faster reporting.
- Value: TT200‑aligned workflows and statements, embedded policy help with citations, and a focused MVP within 3 months.

## Problem Statement

- Current: Many VN businesses use generic/fragmented tools; workflows are manual and error‑prone; TT200 alignment is inconsistent.
- Pain: COA mapping/posting rules misaligned; slow month‑end close; lack of embedded guidance.
- Impact (MVP target): 30–40% faster close (after 2 cycles), 50% fewer posting errors, on‑demand TT200 statements.

## Proposed Solution

- MVP modules: GL, AP/AR basics, Cash & Bank, TT200 financial statements; enforce double‑entry, TT200 COA, posting rules, period close.
- AI assistant: RAG chatbot indexing TT200 and help docs; read‑only answers with citations and examples.
- Tech stack: React (FE), Spring Boot (BE), PostgreSQL/Supabase, Pinecone; CSV/SQL views for Metabase/PowerBI later; n8n for RAG pipelines.
- Differentiators: compliance‑by‑design; embedded policy help; practical 3‑month MVP scope.

## Target Users

- Primary: Chief Accountant (VN enterprises) — needs correct COA/posting, fast close, reliable statements, audit trail.
- Secondary: CFO/Finance Director — needs on‑demand TT200 financials, variance and cash views.
- Secondary: Accounting Students/Junior Accountants — needs guided workflows and policy explanations.

## Goals & Success Metrics

- Objectives: deliver MVP in 3 months; reduce month‑end close by 30–40%; cut posting errors by 50%.
- User metrics: voucher posting time −25%; onboarding CSAT ≥ 4/5; policy lookup success ≥ 85%.
- KPIs: TT200 validation ≥ 98%; balanced vouchers at first submit ≥ 95%; p95 read < 300ms, write < 800ms; WAA ≥ 5 with ≥ 80% 8‑week retention.

## MVP Scope (3 months)

- Core features: GL (TT200 COA, journals, period close/lock, trial balance); AP/AR basics (customers/suppliers, invoices/bills, receipts/payments, simple aging); Cash & Bank (cashbook/bank book, manual reconciliation); TT200 Balance Sheet, P&L, Cash Flow; RAG chatbot (read‑only); Admin (company, periods, roles) with minimal audit.
- Out of scope: inventory costing, fixed assets, tax automation; BI embedding (CSV/SQL views only); advanced n8n flows; multi‑entity/currency, approvals/DOA, e‑invoice/bank integrations.
- Success criteria: TT200 statements ≥ 98% validation; close time −30% after 2 cycles; posting errors −50%; RAG answers with citations ≥ 95%; p95 perf within targets.

## Post‑MVP Vision

- Phase 2: Inventory (avg/FIFO), Fixed Assets (depreciation, disposals), BI embedding, RAG v2 (task guidance, explain voucher).
- Long‑term (12–24m): multi‑entity/currency, consolidation, approvals; tax packs; ecosystem integrations; AI copiloting (draft postings, anomaly detection).
- Expansion: education edition; partner marketplace; IFRS/next circular alignment track.

## Technical Considerations

- Platform: desktop‑first web app; mobile responsive for key flows; VN locale.
- Frontend: React + TypeScript, MUI (or similar), react‑query, schema validation (Zod/Yup).
- Backend: Spring Boot (Java 17+), REST, Spring Security (RBAC), MapStruct, JPA/Hibernate.
- Database: PostgreSQL (Supabase); Flyway migrations; UTC timestamps; strict FK.
- Vector DB: Pinecone; nightly batch indexing via n8n.
- Hosting: containerized services, CI/CD, object storage for exports, simple CDN for FE.
- Security/Compliance: RBAC (accountant/chief accountant/CFO), JWT, audit events; TT200 invariants (COA immutability, double‑entry, period locks).
- Observability: structured logs, request IDs, slow query logs, basic dashboards.

## Constraints & Assumptions

- Constraints: student‑scale budget (open‑source/free tiers); 3‑month MVP; small team; single‑tenant/company/currency; no complex integrations in MVP.
- Assumptions: pilot provides TT200‑aligned COA/opening balances; Supabase/Pinecone available; one pilot company and chief accountant for feedback; TT200 remains valid through MVP (monitor updates).

## Risks & Open Questions

- Risks: compliance drift; data accuracy; RAG efficacy; performance; adoption/onboarding; security/audit gaps.
- Open questions: pilot scope and cutover; RAG corpus sources and update cadence; reporting details beyond base TT200; role matrix/permissions; MVP KPI exports; hosting targets within budget.

## Appendices

- Glossary: TT200, COA, GL, AP, AR, RAG, RBAC, p95, DOA.
- References: Circular 200/2014/TT‑BTC; internal policy docs; mapping tables; pilot opening balances; RAG corpus sources.
- Artifacts: architecture diagram, module boundaries (GL/AP/AR/Cash), sample TT200 statements.
- Templates: voucher, invoice, bill, receipt/payment, period‑close checklist.

## Next Steps

1. Confirm brief with stakeholders; incorporate feedback.
2. Plan MVP delivery (3 months): scope freeze, milestones, roles, risks/mitigations.
3. Prepare pilot data: TT200 COA and opening balances; define cutover.
4. Stand up environments: Supabase Postgres, Pinecone, CI/CD; seed reference data.
5. Begin implementation sprints; track KPIs and validation targets.

### PM Handoff

This Project Brief provides the full context for Accounting ERP - Graduation Project. Please start in 'PRD Generation Mode', review the brief thoroughly to work with the user to create the PRD section by section as the template indicates, asking for any necessary clarification or suggesting improvements.
