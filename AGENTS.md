 <!-- OPENSPEC:START -->

# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:

- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:

- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

## Development Commands

### Docker Services

- Start all services: `docker compose up -d`
- Stop all services: `docker compose down`
- View logs: `docker compose logs -f [service-name]`

### Backend (Java 21 + Spring Boot 3.5.7)

- Run development server: `cd backend && mvnd spring-boot:run -Dquickly`
- Run tests: `cd backend && mvnd test -Dquickly`
- Build: `cd backend && mvnd clean package -Dquickly`
- Compile: `cd backend && mvnd clean compile -Dquickly`
- Format code: `cd backend && mvnd spotless:apply -Dquickly`

### Flyway Migration Naming Convention

**Pattern**: `V<TIMESTAMP>__<description>.sql`

**Format**: `VYYYYMMDDNNN__snake_case_description.sql`

- `YYYY` = Year (4 digits)
- `MM` = Month (2 digits)
- `DD` = Day (2 digits)
- `NNN` = Sequence number for same day (001, 002, 003...)

**Examples**:

```
V20251208001__create_users_table.sql
V20251208002__add_email_index.sql
V20251209001__create_orders_table.sql
```

**Rules**:

1. **Always use timestamp format** for new migrations (avoids conflicts)
2. **Use snake_case** for description (underscores, not spaces)
3. **Double underscore** `__` separates version from description
4. **Never rename** migrations after they've been applied to any environment
5. **Repeatable migrations** use `R__` prefix (e.g., `R__refresh_views.sql`)

**If you need to fix an applied migration**:

- Create a NEW migration with the fix, don't modify the old one
- Run `flyway repair` if checksum validation fails

### Frontend (React + TypeScript + Vite)

- Install dependencies: `cd frontend && pnpm install`
- Run development server: `cd frontend && pnpm dev`
- Build for production: `cd frontend && pnpm build`
- Run tests: `cd frontend && pnpm test`
- Format code: `cd frontend && pnpm format:fix`
- Shadcn UI: `cd frontend && pnpm dlx shadcn@latest add [component]`

## Architecture Overview

This is a multi-tenant accounting system built as a monorepo.

### Backend (Spring Boot)

- **Multi-tenancy**: `CompanyScopedEntity` + `CompanyContext` (ThreadLocal) + `CompanyScopeAspect`.
- **Security**: JWT, RBAC, Spring Security.
- **Data**: PostgreSQL (Flyway), JPA/Hibernate.

### Frontend (React)

- **UI**: shadcn/ui, Tailwind.
- **Structure**: Feature-based (`src/features/`).
- **State**: React Query + Context API.

From now on, please do not add 'Co-authored-by' or any attribution footer to the git commit messages.
Do not auto create markdown (.md) file if user not requested.
Always call beads to create issues for team then spawn the subagents to join team and work on task.

