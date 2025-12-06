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


# CLAUDE. md

This file provides guidance to Claude Code (claude. ai/code) when working with this repository.

## Development Commands

### Docker Services

- Start all services: `docker compose up -d`
- Stop all services: `docker compose down`
- View logs: `docker compose logs -f [service-name]`

### Backend (Java 21 + Spring Boot 3.5.7)

- Run development server: `cd backend && mvn spring-boot:run`
- Run tests: `cd backend && mvn test`
- Build: `cd backend && mvn clean package`
- Compile: `cd backend && mvn clean compile`
- Format code: `cd backend && mvn spotless:apply`

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

## MCP Tool Usage Guidelines

### 1. Codebase Intelligence (Nia AI)
**Tool**: `search_codebase` / `nia_package_search_hybrid`

- **Goal**: Understand architectural patterns or find implementation details across the entire monorepo.
- **When to use**:
  - Before starting a new feature to understand existing patterns (e.g., "How is `CompanyContext` propagated in async tasks?").
  - When debugging complex cross-module issues.
- **Example Prompt**: "Use Nia to search the codebase for all usages of `CompanyScopedEntity` to understand the multi-tenancy implementation."

### 2. Deep Research & Documentation (Nia AI)
**Tool**: `nia_deep_research_agent` / `index_documentation`

- **Goal**: Research external libraries or index new documentation sources.
- **When to use**:
  - When proposing a new library comparison (e.g., "Compare Zod vs Yup for this project").
  - When the project uses a specific library version (e.g., Spring Boot 3.5.7) and you need up-to-date specs.
- **Example Prompt**: "Use Nia to research the breaking changes in Spring Boot 3.5.7 regarding Security filter chains."

### 3. Agent Context Sharing (Nia AI)
**Tool**: `save_context` / `retrieve_context` (or `nia_context` with actions)

- **Goal**: Preserve conversation history, plans, and decisions when switching tasks or sessions.
- **Actions**:
  - **Save**: Captures conversation history, edited files, and decisions.
  - **Retrieve**: Restores a previous working state.
- **Workflow Strategy**:
  1.   **Checkpointing**: After completing a "Plan Phase" (see below), explicitly save the context.
      * *Command*: "Save this context as 'Completed Phase 1 - Auth Setup'."
  2.   **Handoff**: If you need to switch to a different agent or come back later, use retrieve.
      * *Command*: "Retrieve context for 'Auth Setup'."

## Table UI Standards (shadcn)
- **Search**: Input field for filtering.
- **Refresh**: Button to reload data.
- **Pagination**: Page size selector (10, 20, 50) + navigation.

## Development Rules

- **Do not create markdown files** unless explicitly requested.
- **Always Use Nia MCP** for deep understanding, search codebase, find relevance, context management and some task relevance.

---

# BMAD Method + Beads Integration

This project uses **BMAD Method** for structured AI-driven development and **Beads** (`bd`) for issue tracking and agent memory.

## 🎯 Quick Reference: Which Tool When?

| Situation | Tool | Command |
|-----------|------|---------|
| Starting a new feature/epic | BMAD | `*workflow-init` → Choose track |
| Creating requirements | BMAD PM Agent | `*prd` or `*tech-spec` |
| Designing architecture | BMAD Architect | `*create-architecture` |
| Finding next task to work on | Beads | `bd ready --json` |
| Tracking work in progress | Beads | `bd update <id> --status in_progress` |
| Discovered new bug/TODO | Beads | `bd create "Title" -t bug -p 1` |
| Completing a task | Beads | `bd close <id> --reason "Done"` |
| Viewing dependencies | Beads | `bd dep tree <id>` |
| Ending session | Both | See "Landing the Plane" below |

---

## 🔗 Beads Issue Tracking

### Session Start (MANDATORY)

```bash
# ALWAYS run first to see unblocked work
bd ready --json

# Check overall project status
bd stats
```

### During Work

```bash
# Start working on a task
bd update <id> --status in_progress --json

# Create issues for discovered work
bd create "Found: Need input validation" -t bug -p 1 --json

# Link discovered work to parent
bd dep add <new-id> <parent-id> --type discovered-from

# View dependency tree
bd dep tree <id>

# Check what's blocked
bd blocked
```

### Issue Types & Priorities

```bash
# Types: bug | feature | task | epic | chore
# Priority: 0 (critical) → 4 (low), default=2

bd create "Epic: Auth System" -t epic -p 1
bd create "Login UI design" -t task -p 2
bd create "Critical security bug" -t bug -p 0
```

### Dependencies

```bash
# Types: blocks (default) | related | parent-child | discovered-from

# Task B is blocked by Task A
bd dep add <task-b> <task-a> --type blocks

# Related issues (soft link)
bd dep add <id1> <id2> --type related

# Child issue
bd dep add <child-id> <parent-id> --type parent-child
```

---

## 🛬 Landing the Plane (Session End Protocol)

**When ending a session, complete ALL steps.  The plane has NOT landed until `git push` succeeds.**

### Step-by-Step Checklist

```bash
# 1. FILE ISSUES for remaining work
bd create "TODO: Add integration tests" -t task -p 2 --json

# 2. RUN QUALITY GATES (if code changes were made)
cd backend && mvn test
cd frontend && pnpm test
cd backend && mvn spotless:apply
cd frontend && pnpm format:fix

# 3. UPDATE BEADS - close finished, update status
bd close <id1> <id2> --reason "Implemented" --json
bd update <id3> --status blocked --json

# 4.  SYNC AND PUSH (MANDATORY - DO NOT SKIP)
git pull --rebase
# If conflicts in . beads/beads.jsonl:
#   git checkout --theirs .beads/beads.jsonl
#   bd import -i .beads/beads.jsonl
bd sync
git add .
git commit -m "feat: <summary of work>"
git push  # ← MUST complete successfully

# 5.  VERIFY clean state
git status  # Must show "up to date with origin"

# 6.  PROVIDE NEXT SESSION PROMPT
bd ready --json  # Show next work item
```

### Summary Template

After landing, provide:
- ✅ **Completed**: What was done this session
- 📋 **Issues Filed**: New issues created for follow-up
- 🧪 **Quality Gates**: All passing / issues filed
- 🔄 **Git Status**: Confirmed pushed to remote
- ➡️ **Next Session**: `"Continue work on bd-XXX: [title].  [context]"`

---

## 🔄 Integrating BMAD Stories with Beads

### Converting PRD to Beads Issues

After BMAD creates epics and stories, convert them to Beads:

```bash
# Create Epic
bd create "Epic: User Authentication" -t epic -p 1
# Returns: bd-a1b2

# Create child tasks (auto-hierarchical IDs)
bd create "Design login UI" -t task -p 2
# Returns: bd-a1b2. 1

bd create "Implement JWT backend" -t task -p 1
# Returns: bd-a1b2.2

bd create "Write unit tests" -t task -p 2
# Returns: bd-a1b2. 3

# Set dependencies
bd dep add bd-a1b2. 3 bd-a1b2.2  # Tests depend on backend
```

### Workflow: BMAD Story → Beads → Implementation

```
1. SM agent creates story file (*create-story)
2. Convert to Beads: bd create "Story title" -t task
3. DEV agent implements (*dev-story)
4. Update status: bd update <id> --status in_progress
5.  Complete: bd close <id> --reason "Implemented"
6.  Sync: bd sync && git push
```

---

## ⚡ Quick Commands Reference

```bash
# === BEADS ESSENTIALS ===
bd ready                    # Find unblocked work
bd ready --json             # JSON for programmatic use
bd list --status open       # All open issues
bd show <id>                # Issue details
bd stats                    # Project overview

# === ISSUE MANAGEMENT ===
bd create "Title" -t task -p 2 --json
bd update <id> --status in_progress
bd close <id> --reason "Done"
bd dep add <blocked> <blocker>
bd dep tree <id>

# === SYNC & GIT ===
bd sync                     # Force immediate sync
bd hooks install            # Install git hooks (recommended)

# === BMAD WORKFLOWS ===
*workflow-init              # Start new project
*workflow-status            # Check current phase
*prd                        # Create PRD (PM agent)
*tech-spec                  # Create tech spec (PM agent)
*create-architecture        # Design system (Architect)
*sprint-planning            # Initialize sprint (SM)
*create-story               # Draft story (SM)
*dev-story                  # Implement (DEV)
*code-review                # Review code (DEV)
```

---

## 🚨 Critical Rules

1. **NEVER skip `bd sync` and `git push`** at session end
2. **Use FRESH CHATS** for each BMAD workflow
3. **Query `bd ready`** at session start - don't rely on context memory
4. **File issues for ALL discovered work** - bugs, TODOs, ideas
5. **Use Nia MCP** for codebase search and context preservation
6. **Test before committing** if code was changed

---

From now on, please do not add 'Co-authored-by' or any attribution footer to the git commit messages.
Before do any task, read bd skills and use nia deep search first. use frontend skills when design frontend
Do not auto create markdown file if user not request