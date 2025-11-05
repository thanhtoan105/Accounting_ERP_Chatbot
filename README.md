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


