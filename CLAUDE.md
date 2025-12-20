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

Note: This project uses bd (beads) for issue tracking. Use `bd` commands instead of markdown TODOs. See AGENTS.md for workflow details.

This file provides guidance when working with this repository.

## Development Commands

### Docker Services

- Start all services: `docker compose up -d`
- Stop all services: `docker compose down`
- View logs: `docker compose logs -f [service-name]`

### Backend (Java 21 + Spring Boot 3.5.7)

- Run development server: `cd backend && mvnd spring-boot:run`
- Run tests: `cd backend && mvnd test`
- Build: `cd backend && mvnd clean package`
- Compile: `cd backend && mvnd clean compile`
- Format code: `cd backend && mvnd spotless:apply`

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

# Beads Village MCP - Quick Reference

## Workflow

### Leader Agent
```
init(team, leader=true) → add(tags=["role"]) → assign(id,role) → monitor
```

### Worker Agent
```
init(team, role="fe/be/mobile") → claim() → reserve(paths) → work → done(id,msg) → restart
```

## Core Tools

| Tool | Use | Key Args |
|------|-----|----------|
| `init` | Join workspace (FIRST) | `ws`, `team`, `role`, `leader` |
| `claim` | Get next task (filtered by role) | - |
| `done` | Complete task | `id`, `msg` |
| `add` | Create issue | `title`, `desc`, `typ`, `pri`, `tags` |
| `assign` | Assign to role (leader only) | `id`, `role` |

## Query Tools

| Tool | Use |
|------|-----|
| `ls` | List issues (status=open/closed/all) |
| `ready` | Get claimable tasks |
| `show` | Get issue details (id) |

## File Locking

| Tool | Use |
|------|-----|
| `reserve` | Lock files (paths[], ttl, reason) |
| `release` | Unlock files |
| `reservations` | Check locks |

## Messaging

| Tool | Use |
|------|-----|
| `msg` | Send message (subj, to, global) |
| `inbox` | Get messages |
| `broadcast` | Team-wide announcement |
| `discover` | Find agents in team |

## Maintenance

| Tool | Use |
|------|-----|
| `sync` | Git sync |
| `cleanup` | Remove old issues (days) |
| `doctor` | Fix database |
| `status` | Workspace overview |

## Response Fields

`id`=ID, `t`=title, `p`=priority(0-4), `s`=status, `f`=from, `b`=body, `tags`=role tags

## Priority

0=critical, 1=high, 2=normal, 3=low, 4=backlog

## Types

task, bug, feature, epic, chore

## Role Tags

`fe`=frontend, `be`=backend, `mobile`, `devops`, `qa`

## Rules

1. Always `init()` first
2. Leader: `init(leader=true)` to assign tasks
3. Worker: `init(role="fe/be/...")` to auto-filter tasks
4. Always `reserve()` before editing files
5. Create issues for work >2min
6. Restart session after `done()`

## Example: Multi-Agent Setup

```python
# Leader creates tasks
init(team="proj", leader=true)
add(title="Login API", tags=["be"])
add(title="Login form", tags=["fe"])

# BE agent claims BE tasks
init(team="proj", role="be")
claim()  # Gets "Login API"

# FE agent claims FE tasks
init(team="proj", role="fe")
claim()  # Gets "Login form"
```

From now on, please do not add 'Co-authored-by' or any attribution footer to the git commit messages.
Before do any task, read bd skills and use nia deep search first. use frontend skills when design frontend
Do not auto create markdown (.md) file if user not requested.
Always spawn subagents when needed.
