# Epic Technical Specification: Project Foundation & Secure Authentication

Date: 2025-10-31
Author: thanhtoan
Epic ID: 1
Status: Draft

---

## Overview

This epic establishes the full-stack foundation for the accounting ERP, delivering a production-ready Spring Boot 3.5.7 backend (Java 21), React TypeScript frontend, PostgreSQL (Supabase) database integration, JWT-based authentication, and RBAC enforcement. It also sets up CI/CD, Docker-based local development, baseline observability, and documentation so subsequent epics (Master Data, Voucher Engine, AP/AR, Reporting, BI, RAG) can build on a secure, consistent platform.

Outcomes include a running backend with security scaffolding, a React shell with MUI design system, initial entities for users/roles/companies, OpenAPI docs, and Flyway migrations. The epic focuses on platform readiness and secure auth rather than business features.

## Objectives and Scope

In scope:
- Backend bootstrapping (Maven, Spring Boot 3.5.7, Java 21), security, auth endpoints, RBAC guardrails.
- Frontend bootstrapping (React + TS + Vite, MUI), login screen and authenticated shell.
- Database integration (PostgreSQL via Supabase), Flyway baseline, seed for demo users/company.
- CI/CD pipeline, Docker Compose dev stack, health checks, OpenAPI.

Out of scope:
- Master data CRUD (moved to Epic 2), voucher engine (Epic 3), AP/AR (Epics 4–5), reporting/BI (Epics 7–8), chatbot (Epic 9).

## System Architecture Alignment

Aligns to Architecture decisions: Spring Boot backend + React frontend, REST API with JWT, PostgreSQL via Supabase, Redis-ready cache, MUI UI framework. Security aligns with PRD FR01–FR07 and NFR5–NFR9; documentation via SpringDoc satisfies NFR21; containerization and CI/CD satisfy NFR28–NFR29.

Epic-to-architecture mapping: Epic 1 components reside under `controller/auth/`, `security/`, `config/` on backend and `pages/Login.tsx`, `services/auth.ts` on frontend. Initial entities: `users`, `roles`, `companies`. OpenAPI exposed at `/api/docs`; health check at `/health`.

## Detailed Design

### Services and Modules

Backend packages:
- `controller/auth/`: login, refresh, logout endpoints.
- `security/`: JWT filter chain, password encoding, RBAC config.
- `entity/`: `User`, `Role`, `Company`.
- `repository/`: `UserRepository`, `RoleRepository`, `CompanyRepository`.
- `service/impl/`: `AuthService`, `UserService`.
- `config/`: CORS, OpenAPI, Flyway.

Frontend:
- `pages/Login.tsx`, `App.tsx` with protected routes.
- `services/auth.ts` with axios client + interceptors.
- MUI theme + layout shell; basic `Dashboard.tsx`.

### Data Models and Contracts

Entities (PostgreSQL via Supabase):
- `users(id, email, password_hash, role, company_id, created_at, updated_at)`
- `roles(id, name, permissions)`
- `companies(id, code, name, tax_code, created_at, updated_at)`

Auth payloads:
- Login request: `{ email, password }`
- Login response: `{ data: { accessToken, refreshToken }, meta }`
- Refresh request: `{ refreshToken }`
- Refresh response: `{ data: { accessToken }, meta }`

### APIs and Interfaces

Endpoints:
- `POST /api/v1/auth/login` → 200 with tokens; 401 on invalid credentials.
- `POST /api/v1/auth/refresh` → 200 with new access token.
- `POST /api/v1/auth/logout` → 204; server-side token invalidation if implemented.
- `GET /health` → 200 OK.
- OpenAPI at `/api/docs`.

### Workflows and Sequencing

Bootstrap order:
1) Initialize backend (security, entities, Flyway baseline).
2) Initialize frontend (Vite + MUI + login flow).
3) Wire JWT and CORS; expose `/health`, `/api/docs`.
4) Seed demo company and admin user for smoke tests.
5) CI/CD and Docker Compose integration.

## Non-Functional Requirements

### Performance

Targets (MVP): page load < 2s, simple auth API < 1s, report generation N/A for this epic; backend cold start < 3s.
DB: indexes on email, role, company_id; use HikariCP defaults; avoid N+1 queries.
Caching: prepare Redis integration (disabled by default) for later reports.

### Security

JWT auth with HttpOnly/Secure cookies; Argon2/Bcrypt hashing; OWASP-aligned validation; rate limit login attempts; no secrets in code.
RBAC enforcement at API layer; 403 on forbidden; detailed audit for auth events planned in later epics.

### Reliability/Availability

Health check `/health` returns 200; graceful shutdown; error handling with structured responses; Docker Compose for consistent local runtime.

### Observability

SpringDoc OpenAPI at `/api/docs`; structured JSON logging; basic request ID correlation; slow query logging; add metrics/tracing in later epics.

## Dependencies and Integrations

Backend:
- Spring Boot 3.5.7 (Maven), Spring Security 6, Spring Data JPA/Hibernate, Flyway, Lombok.
- PostgreSQL 15+ (Supabase) driver; HikariCP connection pool.
- SpringDoc OpenAPI latest (2.6.x+).

Frontend:
- React 19+, TypeScript 5.x, Vite.
- MUI 7.3.4 (Material UI), MUI X Data Grid (latest compatible with MUI 7.3.4).
- Axios, TanStack Query 5.x.

Infra and planned integrations:
- Docker/Docker Compose for local dev.
- Redis 7.x (prepared, disabled for this epic).
- Pinecone (planned in Epic 9).
- n8n for RAG indexing (planned).

## Acceptance Criteria (Authoritative)

1) User can log in with valid credentials; receives access/refresh tokens; invalid credentials return 401 with structured error.
2) Refresh flow issues a new access token with a valid refresh token; invalid/expired refresh returns 401.
3) RBAC enforced: protected endpoints deny access (403) for insufficient role; health check `/health` returns 200 without auth.
4) OpenAPI available at `/api/docs` and documents auth endpoints and models.
5) Flyway baseline migration applies successfully against PostgreSQL (Supabase); demo admin + company seed runs without errors.
6) Frontend Login screen authenticates and stores session securely (HttpOnly cookie), then routes to an authenticated shell with placeholder Dashboard.
7) Docker Compose starts local stack; backend and frontend connect; smoke test for login succeeds.

## Traceability Mapping

- AC1 → PRD FR01; Spec: Authentication; Components: `AuthController`, `AuthService`, `UserRepository`; Test: login success/failure, 401 cases.
- AC2 → PRD FR01; Spec: Token refresh; Components: `AuthController`, security filter; Test: refresh success/expired token handling.
- AC3 → PRD FR02, FR47; Spec: RBAC; Components: Spring Security config; Test: role-based 403s, public `/health`.
- AC4 → PRD NFR21; Spec: OpenAPI; Components: SpringDoc config; Test: `/api/docs` reachable and accurate.
- AC5 → PRD Admin setup; Spec: Flyway; Components: migrations; Test: migration runs on clean DB, seed present.
- AC6 → PRD UX/Login; Spec: Frontend auth; Components: `Login.tsx`, `services/auth.ts`; Test: UI auth flow and secure cookie behavior.
- AC7 → PRD NFR28/NFR29; Spec: Dev environment; Components: Docker Compose; Test: stack up, login smoke.

## Risks, Assumptions, Open Questions

Risks:
- Password hashing or JWT misconfiguration could weaken security; mitigation: enforce Argon2/Bcrypt, rotate keys, pen test auth endpoints.
- CORS or cookie flags (HttpOnly/Secure/SameSite) mis-set in dev vs prod; mitigation: env-driven config with safe defaults and smoke tests.
- Version drift (React 19+, MUI 7.3.4) may surface compatibility issues; mitigation: lock versions, run template app smoke, upgrade guides referenced.

Assumptions:
- Supabase PostgreSQL 15+ available with required network access.
- Docker and Java 21/Node 18+ available for dev machines.
- Email provider (Resend) available for password reset in later epics.

Open Questions:
- Use Argon2id vs Bcrypt as default? (Prefer Argon2id if library/runtime stable.)
- Store refresh tokens in DB for invalidation vs stateless approach?
- Require MFA for admin accounts in MVP or defer to Epic 10?

## Test Strategy Summary

Scope: auth endpoints, RBAC guards, migrations, minimal UI login flow.

Levels:
- Unit: services (password hashing, token issuance, RBAC checks).
- Integration: `/auth/login`, `/auth/refresh`, `/health` with Spring context and test DB.
- E2E (smoke): UI login flow, secure cookie presence, redirect to dashboard.

Tooling:
- Backend: JUnit 5, Spring Boot Test, Testcontainers (Postgres).
- Frontend: Vitest + Testing Library for login form; optional Playwright smoke.

Coverage targets (MVP): 60% business logic layer for auth, 80%+ on token and RBAC branches.


