# Decision Architecture

## Executive Summary

Enterprise accounting ERP system using Spring Boot 3.5.7 backend with React TypeScript frontend, PostgreSQL via Supabase, and Pinecone for AI RAG capabilities. Separate backend/frontend architecture with REST API communication, JWT authentication, and comprehensive implementation patterns to ensure AI agent consistency.

---

## Project Initialization

### Backend (Spring Boot)

First implementation story should execute Spring Boot project initialization:

**Via Spring Initializer (https://start.spring.io/):**

```
Project: Maven
Language: Java
Spring Boot: 3.5.7
Project Metadata:
  Group: com.accounting
  Artifact: accounting-backend
  Name: accounting-backend
  Package name: com.accounting
  Packaging: Jar
  Java: 21

Dependencies:
- Spring Boot Starter Web
- Spring Boot Starter Data JPA
- Spring Boot Starter Security
- Spring Boot Starter Validation
- Spring Boot Starter Cache
- Spring Data Redis
- PostgreSQL Driver
- Flyway Migration
- Lombok
- SpringDoc OpenAPI (Swagger)
```

**Or via Spring CLI:**
```bash
spring init --dependencies=web,data-jpa,security,validation,cache,data-redis,postgresql,flyway,lombok --build=maven --java-version=21 --packaging=jar --name=accounting-backend --package-name=com.accounting --groupId=com.accounting --artifactId=accounting-backend --version=0.0.1-SNAPSHOT accounting-backend
```

This establishes the base architecture with these decisions:
- **Framework:** Spring Boot 3.5.7 (PROVIDED BY STARTER)
- **Build Tool:** Maven (PROVIDED BY STARTER)
- **Java Version:** 21 (PROVIDED BY STARTER)
- **Web Framework:** Spring MVC (PROVIDED BY STARTER)
- **ORM:** Spring Data JPA with Hibernate (PROVIDED BY STARTER)
- **Security:** Spring Security (PROVIDED BY STARTER)
- **Database:** PostgreSQL Driver (PROVIDED BY STARTER)
- **Migration:** Flyway (PROVIDED BY STARTER)
- **Caching:** Spring Cache with Redis (PROVIDED BY STARTER)

### Frontend (React + TypeScript)

First implementation story should execute React project initialization:

```bash
pnpm create vite frontend --template react-ts
cd frontend
pnpm install
```

Then add required dependencies:
```bash
pnpm add @mui/material @mui/x-data-grid @emotion/react @emotion/styled
pnpm add @tanstack/react-query axios
pnpm add @supabase/supabase-js
pnpm add date-fns
pnpm add -D @types/node
```

This establishes the base architecture with these decisions:
- **Framework:** React with TypeScript (PROVIDED BY STARTER)
- **Build Tool:** Vite (PROVIDED BY STARTER)
- **Package Manager:** pnpm (USER PREFERENCE)
- **UI Library:** MUI + MUI X Data Grid (DECISION)
- **Data Fetching:** TanStack Query (DECISION)
- **HTTP Client:** Axios (DECISION)

---

## Decision Summary

| Category | Decision | Version | Affects Epics | Rationale |
| -------- | -------- | ------- | ------------- | --------- |
| Backend Framework | Spring Boot | 3.5.7 | All | Enterprise Java standard, rich ecosystem, Spring Security integration |
| Frontend Framework | React with TypeScript | Latest stable (React 18+) | All | Industry standard, large ecosystem, MUI compatibility |
| Build Tool (Backend) | Maven | Latest | All | Standard for Spring Boot projects |
| Build Tool (Frontend) | Vite | Latest stable | All | Fast dev server, modern bundling |
| Package Manager | pnpm | Latest stable | Frontend | Faster, efficient disk usage |
| Database | PostgreSQL (Supabase) | PostgreSQL 15+ | All | ACID compliance, TT200 compliance needs, Supabase integration |
| ORM | Spring Data JPA + Hibernate | 6.x (via Spring Boot 3.5.7) | All | Industry standard for Spring Boot |
| API Pattern | REST | - | All client-facing | Simple, well-understood, OpenAPI support |
| Authentication | JWT with Spring Security 6 | Spring Security 6.x | Epic 1 | Stateless, scalable, industry standard |
| UI Library | MUI + MUI X Data Grid | MUI 6.x, MUI X 8.x | All | WCAG AA compliance, dense tables, UX spec requirement |
| Data Fetching | TanStack Query | 5.x | All | Caching, optimistic updates, background refetching |
| HTTP Client | Axios | Latest stable | All | Promise-based, interceptors for auth |
| Caching | Redis | Redis 7.x | Epic 7, 8 | Fast report caching, NFR26 requirement |
| Vector Database | Pinecone | Latest stable | Epic 9 | PRD requirement, scalable vector search |
| Background Jobs | Spring @Scheduled + @Async | Spring Boot 3.5.7 | Epic 8, 9 | Built-in, simple for MVP |
| Email Service | Resend | Latest API v1 | Epic 1, 10 | Modern, developer-friendly |
| File Storage | Supabase Storage | Latest API | Epic 3, 4, 5, 6 | Consistent with Supabase stack, built-in security |
| Search | PostgreSQL FTS + unaccent | PostgreSQL 15+ | Epic 2 | Native Vietnamese unaccented search support |
| Real-time Updates | Polling (5 min) | - | Epic 8 | Simple, meets NFR requirement (<5 min latency) |
| Deployment | Docker + TBD | Docker latest | All | NFR28 requirement, production target TBD |
| Migration Tool | Flyway | Latest stable | All | Versioned migrations, Spring Boot integration |
| API Documentation | OpenAPI/Swagger | SpringDoc 2.3.x | All | Self-documenting API, NFR21 requirement |

---

## Project Structure

```
accounting/
├── backend/                           # Spring Boot backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/accounting/
│   │   │   │   ├── AccountingApplication.java
│   │   │   │   ├── controller/       # REST controllers
│   │   │   │   │   ├── auth/
│   │   │   │   │   ├── voucher/
│   │   │   │   │   ├── customer/
│   │   │   │   │   ├── supplier/
│   │   │   │   │   ├── report/
│   │   │   │   │   └── admin/
│   │   │   │   ├── service/          # Business logic
│   │   │   │   │   ├── impl/
│   │   │   │   │   ├── voucher/
│   │   │   │   │   ├── gl/
│   │   │   │   │   ├── report/
│   │   │   │   │   └── rag/
│   │   │   │   ├── repository/       # JPA repositories
│   │   │   │   ├── entity/            # JPA entities
│   │   │   │   ├── dto/               # DTOs
│   │   │   │   ├── config/            # Configuration
│   │   │   │   ├── security/          # Security config
│   │   │   │   ├── exception/         # Exception handlers
│   │   │   │   └── util/              # Utilities
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/     # Flyway migrations
│   │   └── test/                      # Tests
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/                          # React frontend
│   ├── src/
│   │   ├── components/
│   │   │   ├── common/                # Shared components
│   │   │   ├── forms/                 # Form components
│   │   │   ├── tables/                # Table components
│   │   │   └── charts/                # Chart components
│   │   ├── pages/                     # Page components
│   │   │   ├── Dashboard.tsx
│   │   │   ├── VoucherList.tsx
│   │   │   ├── VoucherForm.tsx
│   │   │   └── Reports.tsx
│   │   ├── hooks/                     # Custom hooks
│   │   ├── services/                  # API services
│   │   ├── types/                      # TypeScript types
│   │   ├── utils/                      # Utilities
│   │   └── App.tsx
│   ├── public/
│   ├── package.json
│   └── Dockerfile
│
├── docker-compose.yml                 # Local development
├── n8n/                               # n8n workflows
│   └── workflows/
│       └── rag-indexing.json
└── docs/                              # Documentation
```

---

## Epic to Architecture Mapping

| Epic | Backend Location | Frontend Location | Database Tables | Key Technologies |
|------|------------------|-------------------|-----------------|------------------|
| Epic 1: Foundation & Auth | `controller/auth/`, `security/` | `pages/Login.tsx`, `services/auth.ts` | `users`, `roles`, `companies` | Spring Security 6, JWT, MUI |
| Epic 2: Master Data | `controller/customer/`, `controller/supplier/` | `pages/Customers.tsx`, `pages/Suppliers.tsx` | `customers`, `suppliers`, `chart_of_accounts` | Spring Data JPA, MUI Data Grid |
| Epic 3: Voucher Engine | `controller/voucher/`, `service/gl/` | `pages/VoucherForm.tsx` | `vouchers`, `voucher_lines`, `journal_entries` | Spring Data JPA, MUI Data Grid |
| Epic 4: AP Module | `controller/purchase/` | `pages/PurchaseBills.tsx` | `purchase_bills`, `ap_payments` | Spring Data JPA, Maker-Checker |
| Epic 5: AR Module | `controller/sales/` | `pages/SalesInvoices.tsx` | `sales_invoices`, `ar_receipts` | Spring Data JPA, Approval workflow |
| Epic 6: Cash & Bank | `controller/cash/` | `pages/CashBook.tsx` | `cash_receipts`, `cash_payments`, `bank_reconciliations` | Spring Data JPA, Supabase Storage |
| Epic 7: Reporting | `controller/report/`, `service/report/` | `pages/Reports.tsx` | Query from `journal_entries`, `vouchers` | Spring Data JPA, Redis cache, PDF/Excel |
| Epic 8: BI Dashboard | `controller/dashboard/`, `service/analytics/` | `pages/Dashboard.tsx` | Materialized views, Redis cache | Redis, React Query, Chart.js |
| Epic 9: AI RAG | `controller/chatbot/`, `service/rag/` | `components/Chatbot.tsx` | External: Pinecone | Pinecone SDK, OpenAI embeddings |
| Epic 10: Admin | `controller/admin/` | `pages/Admin.tsx` | `audit_logs`, `system_settings` | Audit trail, file import/export |

---

## Technology Stack Details

### Core Technologies

**Backend:**
- Spring Boot 3.5.7
- Java 21
- Spring Data JPA 6.x (via Spring Boot)
- Spring Security 6.x
- PostgreSQL Driver (latest)
- Flyway (latest)
- Lombok (latest)
- SpringDoc OpenAPI 2.3.x

**Frontend:**
- React 18+ (latest stable)
- TypeScript 5.x
- Vite (latest stable)
- pnpm (latest stable)
- MUI 6.x
- MUI X Data Grid 8.x
- TanStack Query 5.x
- Axios (latest stable)
- date-fns (latest)

**Infrastructure:**
- PostgreSQL 15+ (via Supabase)
- Redis 7.x
- Pinecone (latest)
- Supabase Storage (latest)
- Docker (latest)

### Integration Points

**Backend ↔ Frontend:**
- REST API: `/api/v1/*`
- Authentication: JWT tokens in HttpOnly cookies
- CORS: Configured for frontend origin

**Backend ↔ Database:**
- Spring Data JPA repositories
- Flyway migrations for schema versioning
- Connection pooling: HikariCP (Spring Boot default)

**Backend ↔ Supabase:**
- JDBC connection string to PostgreSQL
- Supabase Storage REST API for file uploads

**Backend ↔ Pinecone:**
- Pinecone Java SDK (or REST API via RestClient)
- Vector embeddings: OpenAI `text-embedding-3-small` (1536 dimensions)

**Backend ↔ n8n:**
- n8n webhook endpoints for triggering workflows
- Scheduled workflows: RAG indexing at 2 AM daily

**Frontend ↔ Backend:**
- Axios for HTTP requests
- React Query for caching and state management

**Frontend ↔ Supabase:**
- `@supabase/supabase-js` for direct Storage operations (if needed)

---

## Implementation Patterns

These patterns ensure consistent implementation across all AI agents:

### Naming Patterns

**REST API Endpoints:**
- Base URL: `/api/v1`
- Plural resources: `/api/v1/vouchers`, `/api/v1/customers`
- Route parameters: `{id}`, `{voucherId}` (camelCase)
- Example: `GET /api/v1/vouchers/{voucherId}/lines`

**Database Tables:**
- Snake_case, plural: `vouchers`, `voucher_lines`, `chart_of_accounts`
- Columns: snake_case: `voucher_id`, `company_id`, `created_at`
- Foreign keys: `{referenced_table}_id`: `customer_id`, `supplier_id`
- Primary keys: `id` (UUID or BIGSERIAL)

**Java Classes:**
- Entities: PascalCase, singular: `Voucher`, `Customer`
- DTOs: `{Entity}DTO`: `VoucherDTO`
- Repositories: `{Entity}Repository`: `VoucherRepository`
- Services: `{Entity}Service`: `VoucherService`
- Controllers: `{Entity}Controller`: `VoucherController`

**React Components:**
- PascalCase: `VoucherList`, `VoucherForm`
- Files: `VoucherList.tsx` (match component name)
- Hooks: `useVouchers`, `useAuth` (camelCase with `use` prefix)

### Structure Patterns

**Backend Package Structure:**
```
com.accounting
├── controller/       # REST controllers
├── service/          # Business logic
│   └── impl/         # Implementations
├── repository/       # JPA repositories
├── entity/           # JPA entities
├── dto/              # DTOs
├── config/           # Configuration
├── security/         # Security config
├── exception/        # Exception handlers
└── util/             # Utilities
```

**Frontend Folder Structure:**
```
src/
├── components/       # Reusable components
│   ├── common/      # Shared
│   ├── forms/       # Form components
│   └── tables/      # Table components
├── pages/            # Page components
├── hooks/            # Custom hooks
├── services/         # API services
├── types/             # TypeScript types
├── utils/             # Utilities
└── constants/         # Constants
```

### Format Patterns

**API Response Format:**
```typescript
{
  data: T,                    // Actual data
  error?: {                   // Only on error
    code: string,
    message: string,
    details?: any
  },
  meta?: {
    timestamp: string,        // ISO 8601
    requestId: string
  }
}
```

**Date Format:**
- API: ISO 8601 `"2025-10-30T12:00:00Z"`
- UI: Vietnamese `dd/MM/yyyy` (e.g., "30/10/2025")
- Database: PostgreSQL `TIMESTAMP` or `DATE`

**Number Format:**
- Backend: `BigDecimal` for monetary amounts
- JSON: Numbers (no formatting): `1000000`
- UI: Formatted `1.000.000,00 VND`

### Communication Patterns

**HTTP Methods:**
- `GET`: Retrieve (idempotent)
- `POST`: Create
- `PUT`: Full update (idempotent)
- `PATCH`: Partial update
- `DELETE`: Delete (idempotent)

**HTTP Status Codes:**
- `200 OK`, `201 Created`, `204 No Content`
- `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`
- `500 Internal Server Error`

### Lifecycle Patterns

**Loading States:**
- Frontend: Skeleton loaders for tables, spinner for forms
- Backend: Async operations return immediately

**Error Recovery:**
- Retry: 3 retries with exponential backoff
- User-facing: Clear messages in Vietnamese
- Logging: Full context (user, company, request)

**Form State:**
- Draft auto-save: Every 30 seconds or on blur
- Validation: Real-time on field change, full on submit
- Optimistic updates: Show success, rollback on error

### Location Patterns

**API Routes:**
```
/api/v1/
├── auth/
├── vouchers/
├── customers/
├── suppliers/
├── reports/
└── admin/
```

**Static Assets:**
- Frontend: `/public/`
- File uploads: Supabase Storage `/vouchers/{voucherId}/attachments/{filename}`

### Consistency Patterns

**Date Format:**
- UI: Vietnamese `dd/MM/yyyy`
- API: ISO 8601
- Database: PostgreSQL `TIMESTAMP`

**Logging:**
- Structured JSON: `{"timestamp":"...","level":"INFO","logger":"...","message":"...","userId":"...","companyId":"..."}`

**User-Facing Errors:**
- Vietnamese language
- Clear and actionable

**Transaction IDs:**
- Format: `{TYPE}-{YYYY}-{SEQUENCE}` (e.g., `VC2025-001`, `INV2025-123`)

---

## Data Architecture

### Multi-Tenancy Strategy

- Row-level filtering via `company_id` on all tables
- Spring Data JPA filter at repository level
- API-level enforcement via security filter
- Supabase RLS policies as additional layer

### Core Entities

**Users & Authentication:**
- `users` (id, email, password_hash, role, company_id, ...)
- `roles` (id, name, permissions)
- `companies` (id, name, tax_code, ...)

**Chart of Accounts:**
- `chart_of_accounts` (id, code, name, type, parent_id, company_id, postable, ...)
- Hierarchical structure (3 levels: 1xx, 11x, 111)

**Transactions:**
- `vouchers` (id, voucher_number, date, description, status, company_id, ...)
- `voucher_lines` (id, voucher_id, account_id, debit, credit, ...)
- `journal_entries` (id, voucher_id, account_id, debit, credit, period_id, ...)

**Master Data:**
- `customers` (id, code, name, tax_code, company_id, ...)
- `suppliers` (id, code, name, tax_code, company_id, ...)
- `bank_accounts` (id, account_number, bank_name, company_id, ...)

**AP/AR:**
- `purchase_bills` (id, bill_number, supplier_id, date, total, status, ...)
- `sales_invoices` (id, invoice_number, customer_id, date, total, status, ...)
- `ap_payments`, `ar_receipts`

**Audit:**
- `audit_logs` (id, entity_type, entity_id, action, user_id, timestamp, changes, ...)

---

## API Contracts

### Authentication

**POST /api/v1/auth/login**
```json
Request: { "email": "user@example.com", "password": "..." }
Response: { "data": { "accessToken": "...", "refreshToken": "..." }, "meta": {...} }
```

**POST /api/v1/auth/refresh**
```json
Request: { "refreshToken": "..." }
Response: { "data": { "accessToken": "..." }, "meta": {...} }
```

### Vouchers

**GET /api/v1/vouchers**
- Query params: `page`, `size`, `status`, `dateFrom`, `dateTo`, `companyId`
- Response: `{ "data": { "content": [...], "totalElements": 100, "totalPages": 10 }, ... }`

**POST /api/v1/vouchers**
- Request: `{ "date": "2025-10-30", "description": "...", "lines": [...] }`
- Response: `{ "data": { "id": "...", "voucherNumber": "VC2025-001" }, ... }`

### Standard Error Response

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Tổng Nợ phải bằng Tổng Có",
    "details": { "debitTotal": 1000000, "creditTotal": 900000 }
  },
  "meta": {
    "timestamp": "2025-10-30T12:00:00Z",
    "requestId": "req-abc123"
  }
}
```

---

## Security Architecture

### Authentication

- JWT access tokens (15-30 min expiry)
- JWT refresh tokens (7-30 days, stored in HttpOnly cookies)
- Spring Security filter chain validates tokens
- Password hashing: Argon2 or Bcrypt

### Authorization

- Role-Based Access Control (RBAC): Admin, Accountant, Chief Accountant, CFO
- Spring Security method-level security
- API-level permission checks
- Company-level data isolation

### Data Protection

- Encryption in transit: HTTPS/TLS
- Encryption at rest: Database-level encryption
- Audit trail: Immutable append-only logs
- Cryptographic hashing for audit log integrity

---

## Performance Considerations

### Caching Strategy

- Redis caching for reports (Trial Balance, Aging, Financial Statements)
- Cache TTL: 1 hour (configurable)
- Manual invalidation: On GL post, period close
- Spring Cache abstraction with Redis backend

### Database Optimization

- Indexes on foreign keys, dates, account codes
- Avoid N+1 queries (use `@EntityGraph` or joins)
- Query optimization for complex reports
- Connection pooling: HikariCP

### Frontend Optimization

- React Query caching and background refetching
- Code splitting via Vite
- Lazy loading for routes
- MUI Data Grid virtualization for large tables

---

## Deployment Architecture

### Containerization

- Docker Compose for local development
- Separate Dockerfiles for backend and frontend
- Production target: TBD (AWS ECS, Railway, or self-hosted)

### Environment Configuration

- Backend: `application.yml` with profiles (dev, staging, prod)
- Frontend: Environment variables for API endpoints
- Secrets: Environment variables, never in code

---

## Development Environment

### Prerequisites

- Java 21+
- Node.js 18+
- pnpm 8+
- Docker and Docker Compose
- PostgreSQL 15+ (via Supabase or local)
- Redis 7+

### Setup Commands

**Backend:**
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

**Frontend:**
```bash
cd frontend
pnpm install
pnpm dev
```

**Docker Compose:**
```bash
docker-compose up -d
```

---

## Architecture Decision Records (ADRs)

### ADR-001: Separate Backend/Frontend Architecture

**Decision:** Use separate Spring Boot backend and React frontend projects.

**Rationale:** Enterprise requirements, team separation, independent scaling, technology flexibility.

**Alternatives Considered:**
- Next.js full-stack (not suitable for Java backend requirement)
- Monolithic Spring MVC with Thymeleaf (not suitable for React frontend requirement)

### ADR-002: Spring Boot 3.5.7 with Java 21

**Decision:** Use Spring Boot 3.5.7 with Java 21.

**Rationale:** Java 21 is the current LTS with improved performance, virtual threads support across Spring ecosystem (Boot 3.x compatible), and long-term vendor support.

**Alternatives Considered:**
- Spring Boot 2.x (older, less secure)
- Java 17 (previous LTS; acceptable but we prefer latest LTS for support window and features)

### ADR-003: PostgreSQL via Supabase

**Decision:** Use PostgreSQL via Supabase rather than standalone PostgreSQL.

**Rationale:** Supabase provides managed PostgreSQL, built-in auth, storage, real-time capabilities, simplifies operations.

**Alternatives Considered:**
- Standalone PostgreSQL (more operational overhead)
- MongoDB (not suitable for relational accounting data)

### ADR-004: Pinecone for Vector Database

**Decision:** Use Pinecone for RAG vector storage.

**Rationale:** PRD requirement, scalable, managed service, good Java SDK support.

**Alternatives Considered:**
- pgvector in PostgreSQL (considered, but PRD specifies Pinecone)
- Self-hosted vector DB (more operational overhead)

### ADR-005: MUI + MUI X Data Grid

**Decision:** Use Material-UI with MUI X Data Grid.

**Rationale:** UX spec requirement, WCAG AA compliance, dense table support, professional appearance.

**Alternatives Considered:**
- Custom components (too much development time)
- Other UI libraries (UX spec specifies MUI)

### ADR-006: JWT Authentication with Spring Security

**Decision:** Use JWT tokens with Spring Security 6.

**Rationale:** Stateless, scalable, industry standard, good Spring Boot integration.

**Alternatives Considered:**
- Session-based auth (not scalable, stateful)
- OAuth2 (overkill for internal application)

### ADR-007: Redis Caching

**Decision:** Use Redis for report caching.

**Rationale:** NFR26 requirement, fast in-memory storage, Spring Boot integration.

**Alternatives Considered:**
- In-memory caching (not distributed, lost on restart)
- Database query caching (slower than Redis)

---

_Generated by BMAD Decision Architecture Workflow v1.3.2_
_Date: 2025-10-30_
_For: thanhtoan_

