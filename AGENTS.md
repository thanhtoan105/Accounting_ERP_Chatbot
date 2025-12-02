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


## Issue Tracking with bd (beads)

IMPORTANT: This project uses bd (beads) for ALL issue tracking. Do NOT use markdown TODOs, task lists, or other tracking methods.

### Why bd?

- Dependency-aware: Track blockers and relationships between issues
- Git-friendly: Auto-syncs to JSONL for version control
- Agent-optimized: JSON output, ready work detection, discovered-from links
- Prevents duplicate tracking systems and confusion

### Quick Start

Check for ready work:
```bash
bd ready --json
```

Create new issues:
```bash
bd create "Issue title" -t bug|feature|task -p 0-4 --json
bd create "Issue title" -p 1 --deps discovered-from:bd-123 --json
bd create "Subtask" --parent <epic-id> --json  # Hierarchical subtask (gets ID like epic-id.1)
```

Claim and update:
```bash
bd update bd-42 --status in_progress --json
bd update bd-42 --priority 1 --json
```

Complete work:
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

1. Check ready work: `bd ready` shows unblocked issues
2. Claim your task: `bd update <id> --status in_progress`
3. Work on it: Implement, test, document
4. Discover new work? Create linked issue:
   - `bd create "Found bug" -p 1 --deps discovered-from:<parent-id>`
5. Complete: `bd close <id> --reason "Done"`
6. Commit together: Always commit the `.beads/issues.jsonl` file with code changes so issue state stays in sync

### Auto-Sync

bd automatically syncs with git:
- Exports to `.beads/issues.jsonl` after changes (5s debounce)
- Imports from JSONL when newer (e.g., after `git pull`)
- No manual export/import needed!

### MCP Server (Recommended)

If using Claude or MCP-compatible clients, install the beads MCP server:

```bash
pip install beads-mcp
```

Add to MCP config (e.g., `~/.config/claude/config.json`):
```json
{
  "beads": {
    "command": "beads-mcp",
    "args": []
  }
}
```

Then use mcp__beads__* functions instead of CLI commands.

### Managing AI-Generated Planning Documents

AI assistants often create planning and design documents during development:
- PLAN.md, IMPLEMENTATION.md, ARCHITECTURE.md
- DESIGN.md, CODEBASE_SUMMARY.md, INTEGRATION_PLAN.md
- TESTING_GUIDE.md, TECHNICAL_DESIGN.md, and similar files

Best Practice: Use a dedicated directory for these ephemeral files

Recommended approach:
- Create a `history/` directory in the project root
- Store ALL AI-generated planning/design docs in `history/`
- Keep the repository root clean and focused on permanent project files
- Only access `history/` when explicitly asked to review past planning

Example .gitignore entry (optional):
```
# AI planning documents (ephemeral)
history/
```

Benefits:
- Clean repository root
- Clear separation between ephemeral and permanent documentation
- Easy to exclude from version control if desired
- Preserves planning history for archeological research
- Reduces noise when browsing the project

### CLI Help

Run `bd <command> --help` to see all available flags for any command.
For example: `bd create --help` shows `--parent`, `--deps`, `--assignee`, etc.

### Important Rules

- Use bd for ALL task tracking
- Always use `--json` flag for programmatic bd commands
- Link discovered work with `discovered-from` dependencies
- Check `bd ready` before asking "what should I work on?"
- Store AI planning docs in `history/` directory
- Run `bd <cmd> --help` to discover available flags
- Do NOT create markdown TODO lists
- Do NOT use external issue trackers
- Do NOT duplicate tracking systems
- Do NOT clutter repo root with planning documents
