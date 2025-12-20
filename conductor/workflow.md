# Development Workflow

## Methodology: TDD + Context-Driven Development

We follow Test-Driven Development within a context-driven structure:

1. **Read Context First** - Review `product.md`, `tech-stack.md`, and track spec/plan
2. **Write Failing Test** - Capture the expected behavior
3. **Implement Minimally** - Just enough to pass the test
4. **Refactor** - Clean up while tests stay green
5. **Update Track** - Mark tasks complete with commit SHA

## Task Lifecycle

### Starting Work

1. Check ready issues: `bd ready --json`
2. Pick a track from `conductor/tracks.md` or bd issue
3. Read the track's `spec.md` and `plan.md`
4. Mark task `[~]` when starting

### During Development

```bash
# Backend TDD cycle
cd backend
mvnd test -Dtest=MyTest           # Run specific test
mvnd test                         # Run all tests
mvnd spring-boot:run              # Run dev server

# Frontend TDD cycle
cd frontend
pnpm test                         # Run tests
pnpm test:watch                   # Watch mode
pnpm dev                          # Dev server
```

### Completing Work

1. Ensure tests pass
2. Format code:
   ```bash
   cd backend && mvnd spotless:apply
   cd frontend && pnpm format:fix
   ```
3. Commit with conventional format
4. Mark task `[x]` with 7-char commit SHA

## Commit Message Convention

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types

| Type | When |
|------|------|
| `feat` | New feature |
| `fix` | Bug fix |
| `test` | Adding/fixing tests |
| `refactor` | Code change that doesn't add feature or fix bug |
| `docs` | Documentation only |
| `chore` | Build, tooling, dependencies |
| `style` | Formatting, no code change |

### Examples

```
feat(voucher): add VAT calculation to invoice posting
fix(auth): handle token refresh race condition
test(gl): add integration tests for period close
refactor(coa): extract account validation to service
```

## Coverage Requirements

- **Backend**: >80% line coverage for business logic
- **Frontend**: >70% coverage for components and hooks
- **Integration Tests**: All API endpoints must have tests

## Status Markers

Throughout conductor files and bd issues:

| Marker | Meaning |
|--------|---------|
| `[ ]` | Pending / New |
| `[~]` | In Progress |
| `[x]` | Completed (followed by commit SHA) |

## Quality Gates

Before marking work complete:

1. [ ] All tests pass
2. [ ] Code formatted (`spotless:apply` / `pnpm format:fix`)
3. [ ] No TypeScript errors (`pnpm build`)
4. [ ] Backend compiles (`mvnd clean compile`)
5. [ ] Manual verification if UI changes

## Phase Verification

At end of each phase in a plan:

1. Summarize completed work
2. Get user confirmation before proceeding
3. Update plan status markers
4. Commit with phase completion message

## Issue Tracking Integration

This project uses **bd (beads)** for persistent issue tracking:

```bash
bd ready --json              # See available work
bd update <id> --status in_progress
bd close <id> --reason "Completed"
```

Conductor tracks complement bd by adding specs and phased plans to work items.
