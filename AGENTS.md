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

From now on, please do not add 'Co-authored-by' or any attribution footer to the git commit messages.
Do not auto create markdown (.md) file if user not requested.
Always call beads to create issues for team then spawn the subagents to join team and work on task.

## Issue Tracking with bd (beads)

**IMPORTANT**: This project uses **bd (beads)** for ALL issue tracking. Do NOT use markdown TODOs, task lists, or other tracking methods.

### Why bd?

- Dependency-aware: Track blockers and relationships between issues
- Git-friendly: Auto-syncs to JSONL for version control
- Agent-optimized: JSON output, ready work detection, discovered-from links
- Prevents duplicate tracking systems and confusion

### Quick Start

**Check for ready work:**
```bash
bd ready --json
```

**Create new issues:**
```bash
bd create "Issue title" -t bug|feature|task -p 0-4 --json
bd create "Issue title" -p 1 --deps discovered-from:bd-123 --json
bd create "Subtask" --parent <epic-id> --json  # Hierarchical subtask (gets ID like epic-id.1)
```

**Claim and update:**
```bash
bd update bd-42 --status in_progress --json
bd update bd-42 --priority 1 --json
```

**Complete work:**
```bash
bd close bd-42 --reason "Completed" --json
```

### Issue Types

- `bug` - Something broken
- `feature` - New functionality
- `task` - Work item (tests, docs, refactoring)
- `epic` - Large feature with subtasks
- `chore` - Maintenance (dependencies, tooling)

### Priorities

- `0` - Critical (security, data loss, broken builds)
- `1` - High (major features, important bugs)
- `2` - Medium (default, nice-to-have)
- `3` - Low (polish, optimization)
- `4` - Backlog (future ideas)

### Workflow for AI Agents

1. **Check ready work**: `bd ready` shows unblocked issues
2. **Claim your task**: `bd update <id> --status in_progress`
3. **Work on it**: Implement, test, document
4. **Discover new work?** Create linked issue:
   - `bd create "Found bug" -p 1 --deps discovered-from:<parent-id>`
5. **Complete**: `bd close <id> --reason "Done"`
6. **Commit together**: Always commit the `.beads/issues.jsonl` file together with the code changes so issue state stays in sync with code state

### Auto-Sync

bd automatically syncs with git:
- Exports to `.beads/issues.jsonl` after changes (5s debounce)
- Imports from JSONL when newer (e.g., after `git pull`)
- No manual export/import needed!

### Important Rules

- ✅ Use bd for ALL task tracking
- ✅ Always use `--json` flag for programmatic use
- ✅ Link discovered work with `discovered-from` dependencies
- ✅ Check `bd ready` before asking "what should I work on?"
- ✅ Store AI planning docs in `history/` directory
- ✅ Run `bd <cmd> --help` to discover available flags
- ❌ Do NOT create markdown TODO lists
- ❌ Do NOT use external issue trackers
- ❌ Do NOT duplicate tracking systems
- ❌ Do NOT clutter repo root with planning documents
