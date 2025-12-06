# Project Context

## Purpose
A multi-tenant accounting system designed for Vietnamese SMEs. The application provides comprehensive financial management capabilities including:
- Chart of accounts (Vietnam Standard TT200)
- General ledger and journal entry management
- Bank reconciliation
- Statutory reporting (B01, B02, B03, F01 per TT200 regulations)
- Cash/bank audit trails and compliance
- AI-powered chatbot for accounting assistance
- Multi-company support with row-level security

## Tech Stack

### Backend
- **Runtime**: Java 21 (LTS)
- **Framework**: Spring Boot 3.5.7
- **Database**: PostgreSQL with Flyway migrations
- **Caching**: Redis
- **ORM**: JPA/Hibernate
- **Security**: Spring Security + JWT (jjwt 0.12.5)
- **API Docs**: SpringDoc OpenAPI (Swagger)
- **Reporting**: JasperReports 6.21.3, DynamicReports 6.20.1, Apache POI 5.3.0
- **AI Integration**: Azure OpenAI SDK, Pinecone (vector DB)
- **Email**: Resend
- **Testing**: JUnit 5, Testcontainers, Spring Security Test

### Frontend
- **Framework**: React 19.1 with TypeScript 5.9
- **Build Tool**: Vite 7.1
- **UI Components**: shadcn/ui (Radix primitives) + Tailwind CSS 4.1
- **State Management**: TanStack React Query 5.62
- **Forms**: React Hook Form 7.66 + Zod 4.1 validation
- **Tables**: TanStack React Table 8.21
- **Routing**: React Router DOM 7.9
- **i18n**: i18next (English/Vietnamese)
- **Testing**: Vitest 2.1, Testing Library, Playwright (E2E)

### Infrastructure
- **Containerization**: Docker Compose
- **Package Manager**: pnpm (frontend), Maven (backend)
- **Code Quality**: Spotless (Java), ESLint + Prettier (TypeScript)
- **Git Hooks**: Husky

## Project Conventions

### Code Style

#### Backend (Java)
- Format with Spotless: `mvn spotless:apply`
- Import order: `java, javax, org, com`
- All tenant-scoped entities must extend `CompanyScopedEntity`
- Use Lombok for boilerplate reduction
- DTOs in `/dto` package, separate from entities

#### Frontend (TypeScript)
- Format with Prettier: `pnpm format:fix`
- Lint with ESLint: `pnpm lint`
- Use path aliases: `@/features/...`, `@/components/...`
- Components use PascalCase, utilities use camelCase
- Prefer functional components with hooks

### Architecture Patterns

#### Backend
- **Multi-tenancy**: Row-level security via `CompanyScopedEntity` base class + `CompanyContext` (ThreadLocal) + `CompanyScopeAspect` (AOP)
- **Layered Architecture**: Controller → Service → Repository
- **Package Structure**:
  ```
  com.accounting/
  ├── annotation/     # Custom annotations
  ├── aspect/         # AOP aspects (multi-tenancy, audit)
  ├── config/         # Spring configuration
  ├── controller/     # REST controllers (by domain)
  ├── dto/            # Data transfer objects
  ├── entity/         # JPA entities
  ├── enums/          # Enumeration types
  ├── exception/      # Custom exceptions
  ├── repository/     # Spring Data JPA repositories
  ├── scheduled/      # Scheduled tasks
  ├── security/       # JWT, authentication
  ├── service/        # Business logic
  └── util/           # Utility classes
  ```

#### Frontend
- **Feature-first Organization**: Each domain is a self-contained feature
- **Structure**:
  ```
  src/features/
  ├── accounting/     # Core accounting (vouchers, trial balance, reports)
  ├── analytics/      # Business analytics & Metabase integration
  ├── audit/          # Audit trail viewing
  ├── auth/           # Authentication flows
  ├── bankaccounts/   # Bank account management
  ├── chatbot/        # AI assistant
  ├── company/        # Company settings
  ├── customers/      # Customer master data
  ├── dashboard/      # Main dashboard
  ├── suppliers/      # Supplier master data
  └── users/          # User management
  ```
- **State**: React Query for server state, Context API for client state
- **Protected Routes**: `ProtectedLayout` wrapper for authenticated pages

### UI Standards (shadcn/ui)
- All data tables must include:
  - Search input for filtering
  - Refresh button to reload data
  - Row count display
  - Page size selector (10, 20, 50)
  - Pagination controls

### Testing Strategy

#### Backend
- Unit tests with JUnit 5
- Integration tests with Testcontainers (PostgreSQL)
- Security tests with Spring Security Test
- Run: `mvn test`
- Single test: `mvn test -Dtest=ClassName`
- Parallel execution: 3 forks with class-level parallelism

#### Frontend
- Unit tests with Vitest + Testing Library
- E2E tests with Playwright
- Run unit tests: `pnpm test`
- Run E2E: `npx playwright test`

### Git Workflow
- **Branching**: Feature branches from `main`
- **Branch naming**: `epic-N-description` or `feature/description`
- **Commits**: Conventional commits (`feat:`, `fix:`, `chore:`, etc.)
- **No attribution footer**: Do not include `Co-Authored-By` in commits
- **Issue Tracking**: Use `bd` (beads) CLI, not markdown TODOs

### Database Migrations
- Flyway migrations in `backend/src/main/resources/db/migration/`
- Naming: `VYYYYMMDDNNN__description.sql` (e.g., `V20251203001__create_audit_table.sql`)
- Check migration status: `mvn flyway:info`

## Domain Context

### Vietnamese Accounting Standards
- **TT200**: Thong Tu 200 - Vietnamese Accounting Standards for enterprises
- **Chart of Accounts**: 9-level hierarchical structure following TT200
- **Statutory Reports**:
  - B01: Balance Sheet (Bảng cân đối kế toán)
  - B02: Income Statement (Báo cáo kết quả hoạt động kinh doanh)
  - B03: Cash Flow Statement (Báo cáo lưu chuyển tiền tệ)
  - F01: Financial Statement Notes (Thuyết minh báo cáo tài chính)

### Accounting Periods
- Fiscal year management with period-based reporting
- Period opening/closing workflows
- Trial balance generation per period

### Multi-tenancy
- Each company is a separate tenant
- All data is scoped by `company_id`
- Users can belong to multiple companies
- Company context set via JWT claims

## Important Constraints

### Security
- JWT tokens for authentication (short-lived access + refresh)
- No secrets in code (use `.env` files)
- Row-level security enforced at repository level
- Audit logging for all financial transactions

### Compliance
- Vietnamese accounting regulations (TT200)
- Audit trail requirements for all journal entries
- Data retention policies for financial records
- Export capabilities for regulatory submission

### Performance
- Redis caching for frequently accessed data
- Optimized SQL queries for reporting
- Pagination required for all list endpoints
- Background processing for large report generation

## External Dependencies

### Services
- **PostgreSQL**: Primary database
- **Redis**: Caching layer
- **Azure OpenAI**: AI chatbot embeddings and completions
- **Pinecone**: Vector database for semantic search
- **Resend**: Transactional email service
- **n8n**: Workflow automation webhooks

### Development Tools
- **Metabase**: Embedded analytics (via SDK)
- **Swagger/OpenAPI**: API documentation at `/api/docs`

### AI Agent Tooling
- **Nia MCP**: Codebase search and documentation indexing
- **Beads (bd)**: Issue tracking and dependency management
- **BMAD Method**: Structured AI-driven development workflows
- **OpenSpec**: Change proposal and specification management
