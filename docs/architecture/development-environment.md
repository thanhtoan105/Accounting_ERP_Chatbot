# Development Environment

## Prerequisites

- Java 21+
- Node.js 18+
- pnpm 8+
- Docker and Docker Compose
- PostgreSQL 15+ (via Supabase or local)
- Redis 7+

## Setup Commands

**Backend:**

```bash
cd backend
mvnd clean install
mvnd spring-boot:run
```

**Frontend:**

```bash
cd frontend
pnpm install
pnpm dev
```

**Docker Compose:**

```bash
docker-compose up -d
```

---
