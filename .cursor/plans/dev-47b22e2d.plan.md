<!-- 47b22e2d-08e2-4dc8-974d-67af3b9ad794 cf01a83f-e702-4a89-a5fd-0c7eedd0a3f8 -->
# Execute Story 1.1: Initialize Project Repositories & DevOps (normal)

### Scope

- Implement `docs/stories/1-1-initialize-project-repositories-devops.md` with Status set to Approved and guided by `docs/stories/1-1-initialize-project-repositories-devops.context.xml`.
- Deliverables map to ACs #1–#6; no scope beyond Acceptance Criteria.

### Approach

1. Load story + context

- Read the story markdown fully and the context XML at `docs/stories/1-1-initialize-project-repositories-devops.context.xml`; treat context as authoritative.

2. Scaffolding (AC #1)

- Backend: Create Spring Boot 3.5.7 (Java 21) skeleton in `backend/` with `controller/`, `service/`, `repository/`, `entity/`, `config/`, `security/`.
- Frontend: Vite React + TS in `frontend/` with MUI, TanStack Query, Axios; `src/pages`, `src/components`, `src/services`, `src/hooks`, `src/types`, `src/utils`.
- Infra: Add `docker-compose.yml`, `n8n/`, `docs/` placeholders if missing.

3. Local dev stack (AC #2)

- Compose services: Postgres (Supabase-compatible), Maildev, optional Redis; healthchecks, named networks/volumes.
- Seed dev schema via init SQL or migration placeholder.

4. DX & quality gates (AC #3)

- Frontend: ESLint + Prettier, Husky pre-commit.
- Backend: Spotless + Checkstyle and Git hooks.
- Root and module READMEs covering setup/env/troubleshooting.

5. CI/CD (AC #4)

- GitHub Actions: backend (build/test/lint/package with Maven cache), frontend (install/lint/test/build with pnpm cache).

6. Backend health + API docs (AC #5–#6)

- `/health` controller returns 200 with build info.
- SpringDoc OpenAPI + Swagger UI at `/api/docs`.

7. Tests (AC #3, #5)

- Backend JUnit smoke test for `/health`.
- Frontend Vitest smoke test for app render + env resolution.

8. Story updates

- Update story checklist boxes, file lists, and completion notes; link CI runs.

### Key Files (to be created/updated)

- Root: `docker-compose.yml`, `README.md`
- Backend: `backend/pom.xml`, `backend/src/main/java/.../controller/HealthController.java`, `backend/src/test/java/.../HealthControllerTest.java`, `backend/src/main/resources/application.yml`
- Frontend: `frontend/package.json`, `frontend/vite.config.ts`, `frontend/src/main.tsx`, `frontend/src/pages/App.tsx`
- CI: `.github/workflows/backend.yml`, `.github/workflows/frontend.yml`
- Hooks/Configs: `frontend/.eslintrc.cjs`, `frontend/.prettierrc`, `frontend/.husky/pre-commit`, `backend/checkstyle.xml`, `backend/pom.xml` Spotless
- Docs: `docs/stories/1-1-initialize-project-repositories-devops.md`

### Checkpoints & Evidence

- Compose: `docker compose up` starts services healthy.
- CI: Both workflows pass.
- Endpoints: `GET /health` returns 200 + build info; Swagger UI at `/api/docs`.
- Tests: All pass locally and in CI.

### Risks/Assumptions

- Package managers: pnpm for frontend, Maven for backend.
- Redis optional; include but disabled by default.
- Supabase compatibility via Postgres image with correct extensions if needed.

### To-dos

- [ ] Load story and read context XML; confirm ACs and constraints
- [ ] Create Spring Boot 3.5.7 Java 21 backend skeleton in backend/
- [ ] Create Vite React TS frontend with MUI and TanStack Query
- [ ] Add docker-compose with Postgres, Maildev, optional Redis + healthchecks
- [ ] Configure ESLint/Prettier/Husky; Spotless/Checkstyle; READMEs
- [ ] Add GitHub Actions for backend and frontend with caching
- [ ] Implement /health and SpringDoc OpenAPI Swagger UI
- [ ] Add JUnit health test and Vitest app smoke test
- [ ] Run compose and CI; update story status and completion notes