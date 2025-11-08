Accounting Monorepo
====================

Modules
-------
- backend: Spring Boot 3.5.7 (Java 21)
- frontend: React + TypeScript (Vite)

Getting Started
---------------
1. Docker: `docker compose up -d`
2. Backend: `cd backend && mvn spring-boot:run`
3. Frontend: `cd frontend && pnpm install && pnpm dev`

Services
--------
- Postgres: localhost:5432 (user/password: accounting)
- Maildev UI: http://localhost:1080
- Redis: localhost:6379

Troubleshooting
---------------
- Ensure Java 21 and Node 18+ installed
- Clear caches, reinstall modules if builds fail


Development
-----------

- Seed demo data (company + users + minimal COA):
  - Via CLI (recommended):
    - `cd backend && mvn spring-boot:run -Dspring-boot.run.arguments=--seedDemo=true`
    - Logs will include a line like: `[DEMO] Bootstrap executed. created=true, companyId=..., code=DEMO`
  - Rollback demo data:
    - `cd backend && mvn flyway:migrate -Dflyway.target=R__remove_demo_data.sql`
    - Or run the SQL in `backend/src/main/resources/db/migration/R__remove_demo_data.sql`

- Demo users (default password: `Demo@12345`):
  - admin@demo.local (role: admin)
  - accountant@demo.local (role: accountant)
  - chief@demo.local (role: chief_accountant)
  - cfo@demo.local (role: cfo)


