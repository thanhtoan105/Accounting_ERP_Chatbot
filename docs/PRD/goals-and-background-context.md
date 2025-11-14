# Goals and Background Context

## Goals

- Automate core accounting operations: invoicing, AR/AP tracking, cash receipts/payments, and consistent, automated GL posting.
- Provide near real-time financial dashboards (latency ≤ 5 phút), including revenue/expense analytics, AR/AP aging, and cash flow via BI (Metabase/Power BI).
- Support end-to-end statutory financial reporting: Balance Sheet (B01-DN), Income Statement (B02-DN), and Cash Flows (B03-DN), mapped to the Vietnamese Chart of Accounts (COA, TT200).
- Enable AI-powered “contextual Q&A” (accuracy ≥ 90% with source citations/trace; fallback if data incomplete), answering queries directly from indexed accounting data (ex: “Tình hình công nợ...”).
- Minimize manual work (reduce manual steps by ≥ 30%) and accounting errors (reduce misposting by ≥ 50%) through process automation and GL consistency.
- Guarantee compliance with enterprise standards: period locking, audit trail/journal logging, maker–checker process, and standardized COA.
- Enforce security and row-level access controls: strict, role-based permissions throughout all modules.
- Deliver a robust technical architecture: PostgreSQL (Supabase) database schema, Spring Boot backend, React frontend, Vector DB (Pinecone), and n8n-based pipeline for RAG/embedding.
- Showcase a seamless workflow in demo: user can initiate transactions, see real-time results in BI, and interact with an embedded chatbot for immediate, accurate answers—all with traceable source support.

## Background Context

This project is both a practical learning capstone and a response to real business pain points: finance teams and leadership need up-to-the-minute AR/AP and cashflow data, accessible through self-service analytics, dashboards, and AI-based Q&A—without delays from manual, ad hoc reporting.

The “why now”: curriculum and enterprise needs are converging:

- Educational goals: uniting accounting principles, database design, BI, and AI agents in a working ERP module.
- Business drivers: urgent demand for agility, accuracy, and transparency—shorter reporting cycles, compliance with TT200, and workforce enablement via automation and self-service.
- Solution boundaries and explicit MVP scope:
  - Single-currency (VND) only; deep e-invoice integration and multi-currency, IFRS compliance are postponed.
  - AI Q&A features must cite original data sources (with trace/guardrails), and provide clear fallback if data is incomplete/unavailable.
  - Security is central: every function is role-restricted, and audit trail is compulsory.
- Technical commitments: integration of regulated accounting schema, fast and accurate BI, automated RAG pipeline, and robust, explainable chatbot.
- The product will ship as a working ERP submodule, focused solely on the included accounting flows and reports—suitable for both demonstration and practical use within the stated constraints.

---
