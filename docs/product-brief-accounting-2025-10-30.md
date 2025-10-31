# Product Brief: accounting

**Date:** 2025-10-30
**Author:** thanhtoan
**Status:** Draft for PM Review

---

## Executive Summary

Vietnam’s chief accountants, CFOs, and accounting students face complex regulatory compliance and slow, error-prone financial closes due to fragmented, generic tools and a lack of embedded TT200/2014/TT-BTC policy enforcement. This Product Brief proposes an Accounting ERP purpose-built for Vietnam, combining out-of-the-box policy alignment, automated accounting workflows, and an advanced AI-powered RAG chatbot that delivers real-time, context-aware guidance and compliance checks.

Focused on the needs of SMEs and accounting education, the solution offers TT200-compliant COA, drill-down statutory statements, validation at every entry, and onboarding designed for rapid time-to-value. The platform leverages a modern stack—Spring Boot, React JS + Vite, TailwindCSS/MUI, Supabase, and Azure OpenAI—to ensure security, scalability, and automation. Its compliance-first design aims to cut month-end close times by 30–40%, halve posting errors, and enable teams to self-serve over 80% of regulatory Q&A, supporting rapid adoption and audit-readiness for Vietnam’s evolving financial landscape.

---

## Problem Statement

Vietnamese chief accountants, CFOs, and accounting students struggle with fragmented, manual workflows that frequently misalign with TT200/2014/TT-BTC requirements. This is most acute in:
- Chart of Accounts (COA) mapping (e.g., incorrect GL postings, improper parent/leaf account assignments)
- Posting rules enforcement (non-leaf-only constraints not applied)
- Financial statement format compliance (B01-DN, B02-DN, B03-DN gaps).

These teams routinely spend 2–3 days per month on manual compliance checks, exposing organizations to audit delays and financial penalties. Current generic accounting tools such as Excel or basic ERP modules lack TT200 guardrails—offering no automated validation or embedded regulatory guidance. Manual validation bottlenecks remain, and AI-based contextual help is absent, forcing teams to rely on slow, costly external consultants.

The MVP will focus on core accounting operations (invoicing, AR/AP, GL, TT200-compliant financial statements), with single-currency (VND) support. Deep e-invoice integration, multi-currency, and IFRS will be excluded to maintain a three-month, focused MVP scope.

Crucially, compliance-first design will:
- Enforce double-entry accounting and immutable audit trails
- Embed TT200 COA and validation rules, preventing >50% of common posting errors, strengthening audit readiness

Without automated policy guidance—TT200 validation, field-level compliance checks, inline article-specific help—teams lack confidence, resorting to spreadsheet-based workarounds and paid consulting, raising cost-per-close.

The integrated RAG chatbot (≥90% accuracy and cited sources) becomes an on-demand compliance advisor. This substantially lowers onboarding time (by ≥30%), reduces support ticket volume, and enables staff to resolve 80% of policy questions in-session.

Before:
A 50-person company takes 10–15 business days to finish month-end close, requiring 8+ hours of consulting, with 15+ error-driven posting reworks.

After (MVP expected):
Close cycle shrinks to 5–7 days (after 2 cycles), errors drop to 2–3 per month, and teams independently handle 80% of regulatory Q&A—saving 40+ staff hours and $500+ per cycle in consulting.

---

## Proposed Solution

Launch a compliance-first Accounting ERP purpose-built for Vietnam, fully aligned with TT200/2014/TT-BTC. The solution automates all core accounting operations—AR/AP, GL, and TT200-specific statutory statements (B01-DN, B02-DN, B03-DN)—with a seamless, policy-driven workflow.

Key Features & Differentiators:
- Preconfigured TT200 COA & Policy Guardrails: Out-of-the-box account structure, posting rules (enforcement of leaf-only GL), and statement formats eliminate common alignment issues.
- Embedded Field-Level Compliance Validation: Real-time checks for all entries, ensuring only TT200-compliant postings reach the ledger.
- AI-Powered RAG Chatbot: Guides users with quick, accurate policy interpretations, links to the relevant articles, and context-aware help—reducing wait times for expert guidance.
- Immutable Audit Trails: Every action is logged; double-entry is enforced, simplifying auditing and review.
- Single-currency (VND) Simplicity: Laser-focused MVP removes distractions—no multi-currency or deep e-invoice complexity, ensuring fast, reliable delivery.

Why This Will Succeed:
- Localized to Vietnamese regulations, not a global “one size fits all” tool.
- Time-to-value: Immediate positive impact—cut close times, reduce errors, and increase team confidence in compliance.
- Eases onboarding for accountants and students alike, enabling rapid ramp-up and self-reliance.

User Experience Vision:
Accountants (from students to CFOs) feel empowered, compliant, and more productive. With every entry and report, compliance is automatic, and help is available instantly—so users can focus on decision-making, not chasing regulations.

---

## Target Users

### Primary User Segment

Chief Accountants and Finance Leaders
- Professionals in Vietnamese SMEs or mid-sized organizations (20–300 employees)
- Responsible for ensuring TT200 compliance, month-end close, and statutory financial reporting (especially B01-DN, B02-DN, B03-DN)
- Currently rely on siloed, generic ERP modules or spreadsheets, leading to slow, error-prone, manually validated closes
- Pain points: fear of noncompliance, frequent posting mistakes, audit stress, high external consulting costs, lost time to manual checks

### Secondary User Segment

Accounting Students & Junior Accountants
- Early-career professionals or university students tasked with real-world accounting practice
- Need TT200 policy-compliant workflows for training, self-study, and rapid onboarding in VN companies
- Pain points: steep learning curve, no embedded regulatory help, confusion over compliant processes, frequent need for supervisor support

---

## Goals and Success Metrics

### Business Objectives

- Achieve 30–40% reduction in month-end close time for target customers within two close cycles
- Cut external audit/consulting costs by $500+ per company per close cycle, via automation and guided compliance
- Increase market adoption among VN SMEs with a TT200-focused value proposition
- Establish product recognition for policy-compliant accounting workflows within 6 months

### User Success Metrics

- >80% of regulatory and policy questions resolved through in-app RAG chatbot (self-serve rate)
- 50%+ reduction in corrective posting errors (as measured in first three months of use)
- Average user onboarding time reduced by ≥30% (measured from first login to independent close task completion)
- End-user satisfaction (NPS >8/10) regarding compliance confidence and usability

### Key Performance Indicators (KPIs)

1. Average month-end close duration (days)
2. Number of TT200 posting errors per cycle
3. % of compliance queries answered via in-app chatbot
4. Hours spent on external consulting per close
5. User onboarding time (days to first autonomous close)

---

## Strategic Alignment and Financial Impact

### Financial Impact

- Reduce month-end close from 10–15 to 5–7 business days (after 2 cycles), saving 40+ staff hours per cycle
- Cut external consulting spend by $500+ per close cycle via self-serve compliance and validations
- >50% reduction in posting errors, lowering rework time and audit adjustments
- Lower compliance risk through enforced TT200 rules and immutable audit trails (fewer findings/penalties)
- Faster reporting enables earlier decision-making; expected uplift in finance team throughput (10–20%)
### Company Objectives Alignment

- Compliance excellence in Vietnam (TT200/2014/TT-BTC-first product strategy)
- Operational efficiency: measurable reductions in close time, rework, and support tickets
- Market penetration in VN SMEs with localized, regulation-aligned workflows
- AI enablement: safe, auditable RAG for finance operations with source citations
### Strategic Initiatives

- TT200 Compliance Program: preconfigured COA, posting rules, and statement templates (B01-DN, B02-DN, B03-DN)
- AI/RAG Enablement: Pinecone vector DB + n8n ingestion workflows; LLM/GPT with citations
- Data Governance & Audit: immutable trails, period locks, double-entry enforcement
- Pilot Program: 3+ SME pilots to validate KPIs (close time, error rate, self-serve %)
- Enablement & Education: training mode for students/juniors; documentation and policy help
---

## MVP Scope

### Core Features (Must Have)

- Preconfigured TT200/2014/TT-BTC-compliant Chart of Accounts (COA) and enforce leaf-only GL posting rules
- Automated workflows for core accounting operations: invoicing (AR), supplier bills (AP), general ledger with mandatory dimension validation (cost centers, customers, vendors), period closing with validation (no drafts, Dr=Cr check, immutable period lock), and TT200-standard financial statements (B01-DN, B02-DN, B03-DN) with drill-down to source vouchers. VAT handling (0/5/10/exempt) with automatic GL mapping to account 3331.
- Field-level validation and inline compliance help with direct article citations
- Apply an AI Chatbot using a RAG (Retrieval-Augmented Generation) architecture, where accounting data is indexed in a vector database (Pinecone). When users ask questions, the system retrieves relevant records and generates answers with an LLM/GPT based on internal data. We use n8n to orchestrate an automated workflow that embeds accounting data into the vector database, enabling the chatbot to answer questions such as “What is our current receivables/payables position?” accurately from real context and live system data. This is a database-driven chatbot, not a document-based chatbot.
- Immutable audit trail and enforced double-entry validation
- Self-serve onboarding flow with guided compliance setup for SMEs and training mode for students

### Out of Scope for MVP

- Deep e-invoice integration (e.g., tax authority direct connect)
- Multi-currency accounting and IFRS reporting
- Customizable workflow engine or advanced approval hierarchies
- Integrations with non-standard third-party HRM or CRM systems
- Full mobile-native app (web-responsive only)
- AI-powered predictive analytics or forecasting

### MVP Success Criteria

- At least three pilot SME organizations achieve >30% reduction in close duration and >50% error reduction after two cycles
- ≥80% user self-serve policy lookup rate via chatbot
- Solution passes independent compliance review for TT200 alignment
- Average onboarding time for new teams reduced by ≥30%
- NPS ≥8/10 on compliance confidence and close efficiency after MVP use

---

## Post-MVP Vision

### Phase 2 Features

- Deep e-invoice integration and direct tax authority connections
- Multi-currency accounting and preliminary IFRS support
- Advanced analytics: dashboards, predictive close estimations, anomaly detection
- Customizable workflow engine; granular approval hierarchies; role-based access extensions
- Mobility: native mobile app clients for accountants on the go
- More integrations: payroll, HRM, custom CRM connectors

### Long-term Vision

- Become Vietnam’s reference SaaS for end-to-end, compliance-assured accounting and reporting
- Extend platform to support evolving compliance frameworks (e.g., TT133, global standards)
- AI-powered continuous compliance: proactive error detection, live audit-prep, built-in regulatory updates
- Virtual training center: simulation mode for upskilling accountants, students, and partners
- Ecosystem growth: enable third-party extensions and integrations via open API

### Expansion Opportunities

- Extend ready-for-compliance framework to ASEAN/SEA markets with similar regulatory demands
- Partner with universities and accounting bodies for endorsed student onboarding and certification programs
- Launch vertical-specific modules (e.g., construction, retail, services)
- Collaborate with audit firms, regulatory agencies, and business software vendors for ecosystem synergy

---

## Technical Considerations

### Platform Requirements

- Web application optimized for Chrome, Edge, and Firefox on modern Windows (10+) and MacOS
- Responsive layout for 13–15" laptops and large secondary monitors
- Printable TT200 statements with direct export to PDF/Excel
- Support for Vietnamese language date, number, and currency formats
- Accessibility: keyboard navigation, screen reader support (WCAG AA suggested)

### Technology Preferences

- Frontend: React (JS) with Vite for fast, modular development
- UI Libraries: TailwindCSS and Material UI (MUI) for flexible, modern interface design
- Backend: Spring Boot (Java) for scalable, robust API and service layer
- Database: PostgreSQL hosted on Supabase for reliable, scalable data management and storage
- AI: Azure OpenAI Service for LLM-based features and vector search/embedding
- Integration: n8n for workflow orchestration and pipeline automation (optional, if retained)
- Auth: Supabase Auth for simple, secure authentication with social/enterprise login options

### Architecture Considerations

- Modular microservices or well-separated backend modules to enable fast future evolution (multi-company, multi-currency)
- Secure, auditable data access: RBAC, field-level access, activity logging, and encrypted at rest
- Scalable RAG layer: loosely-coupled Pinecone index and LLM API, designed for future multi-source expansion
- Compliance-first by design: fully versioned configuration, audit mode, and strong tenancy isolation (SMEs)
- API-first for future mobile or 3rd-party integrations

---

## Constraints and Assumptions

### Constraints

- 3-month MVP timeline (feature freeze required for launch discipline)
- SME/edu accounts only—no multi-tenancy for large enterprise yet
- Limited to single-currency (VND) transactions and standard VN date/number formats for MVP
- Compliance: strict TT200/2014/TT-BTC focus—other standards handled post-MVP
- Azure OpenAI and Supabase costs must stay within initial budget
- No mobile-native delivery for MVP (web-only, responsive design)
- Security: data encrypted at rest, access RBAC enforced at API

### Key Assumptions

- Target SMEs will adopt a web-based, TT200-compliant platform with minimal in-person consulting
- Supabase cloud infrastructure meets finance sector reliability/security needs
- Azure OpenAI service availability consistent for production workloads
- Accountants, CFOs, and students will use built-in help/chatbot instead of seeking external consultants
- Minimum 3 pilot SMEs will complete onboarding and participate in MVP evaluation with actual TT200 data
- All required TT200/2014/TT-BTC statement templates available at project start

### Key Risks

- TT200/2014/TT-BTC rules or formats change before/after MVP launch (compliance drift)
- Integration issues: Azure OpenAI or Supabase service outages/latency impact core flows
- AI RAG accuracy falls below expectation, creating compliance risk or user mistrust
- User resistance to workflow change, slow onboarding, or low digital literacy in SME staff
- Insufficient pilot users/data for credible impact demonstration
- Unexpected budget or infrastructure overages (cloud/API usage)

### Open Questions

- What level of explainability/traceability does the AI guidance require to pass audit/review?
- Are there sector-specific reporting nuances (e.g., construction/retail) that impact MVP design?
- Will MOF or other regulators require certification before broader market launch?
- Best approaches for long-term pricing/licensing (SME vs. edu vs. larger org)
- What is the minimum training required for user self-sufficiency?

### Areas Needing Further Research

- Real-world TT200/2014/TT-BTC compliance pain points (via user interviews/surveys)
- SME appetite for policy-driven automation and AI chatbots in finance
- AI chatbot “hallucination” management: retrieval relevance, validation layers
- Security and privacy compliance for finance data on Supabase/Azure
- Emerging competitive offerings and regulator stances on AI in accounting

---

## Appendices

### A. Research Summary

- Preliminary desk research reviewed TT200 and 2014/TT-BTC regulatory documents and templates
- Consulted with chief accountants at 2 mid-sized VN companies regarding compliance pain points
- Synthesized challenges from local Facebook/Zalo accountant groups on TT200 issues, especially COA mapping and statement formatting
- Collected market reviews of VN-focused digital accounting solutions, identifying product and support gaps
- Benchmarked 3 non-VN ERP SaaS providers (SAP Business One, Odoo, Oracle NetSuite)

### B. Stakeholder Input

- Early feedback from chief accountants, SME CFOs, and university accounting faculty
- Pain points, feature priorities, and MVP validation goals defined through scoping calls
- Additional input from external auditor (regulatory, audit-readiness recommendations)

### C. References

- TT200/2014/TT-BTC Circular and official templates
- Internal project scoping notes and user interview summaries
- VN Ministry of Finance public guidelines and resources
- Benchmarked competitor product documentation (public domain)

---

_This Product Brief serves as the foundational input for Product Requirements Document (PRD) creation._

_Next Steps: Handoff to Product Manager for PRD development using the `workflow prd` command._


