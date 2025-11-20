# Development Commands
- Backend: `cd backend && mvn clean package` (Build), `mvn test` (Test), `mvn spotless:apply` (Format)
- Backend Single Test: `cd backend && mvn test -Dtest=ClassName`
- Frontend: `cd frontend && pnpm install` (Install), `pnpm build` (Build), `pnpm test` (Test), `pnpm lint` (Lint)

# Architecture & Structure
- Monorepo: Java 21 Spring Boot 3.5.7 (Backend) + React TypeScript Vite (Frontend)
- Multi-tenancy: Row-level security via `CompanyScopedEntity` and `CompanyContext`
- Database: PostgreSQL with Flyway migrations (`backend/src/main/resources/db/migration/`)
- Frontend: Feature-first (`src/features/`), shadcn/ui, `ProtectedLayout`
- API: `/api/v1/...` endpoints, documented at `/api/docs` (Swagger)

# Code Style & Guidelines
- Backend: Use Spotless. All entities must implement `CompanyScopedEntity` if tenant-specific.
- Frontend: Use ESLint/Prettier. Import via aliases (`@/features/...`).
- UI: Use shadcn/ui. Tables must have search, refresh, count, and pagination.
- Security: JWT auth. No secrets in code.
- Migrations: Numbered Flyway versions (e.g., `V1__Description.sql`).
