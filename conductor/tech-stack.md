# Technology Stack

## Backend

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 (LTS) | Runtime - use records, pattern matching, virtual threads |
| Spring Boot | 3.5.7 | Framework - **Jakarta EE namespace required** |
| PostgreSQL | 16.4 | Database (driver 42.7.4) |
| Flyway | 11.10.0 | Migrations - **timestamp naming: `VYYYYMMDDNNN__*.sql`** |
| JJWT | 0.12.5 | JWT authentication |
| Redis | latest | Caching and session storage |
| Testcontainers | 1.21.3 | Integration tests - **MUST extend IntegrationTest** |
| Maven Daemon | latest | Build tool (`mvnd`) |

### Critical Backend Rules

```java
// ✅ CORRECT - Jakarta namespace
import jakarta.persistence.*;
import jakarta.validation.*;

// ❌ WRONG - javax is deprecated
import javax.persistence.*;
```

```sql
-- ✅ CORRECT - Timestamp naming
V20251220001__create_table.sql

-- ❌ WRONG - Collision risk
V33__create_table.sql
```

## Frontend

| Technology | Version | Purpose |
|------------|---------|---------|
| React | 19.1.1 | UI Framework - functional components, `use()` hook |
| TypeScript | 5.9.3 | Type safety - strict mode, `@/*` → `src/*` |
| Vite | 7.1.7 | Build tool - proxy `/api` → `localhost:8080` |
| Tailwind CSS | 4.1.16 | Styling - **CSS-based config only, no tailwind.config.js** |
| shadcn/ui | Radix-based | Components - `src/components/ui/*.tsx` |
| TanStack Query | 5.62.0 | Server state management |
| React Hook Form | 7.66.0 | Form handling |
| Zod | 4.1.12 | Validation - **v4 syntax** |
| i18next | 25.6.3 | Internationalization (vi, en) |
| Vitest | 2.1.4 | Testing - `import { vi } from 'vitest'` |

### Critical Frontend Rules

```css
/* ✅ CORRECT - v4 CSS config */
@import "tailwindcss";
@theme { --color-primary: #3b82f6; }
/* NO tailwind.config.js - v4 ignores it! */
```

```typescript
// ✅ CORRECT - Vitest
import { vi } from 'vitest'

// ❌ WRONG - Jest
import { jest } from '@jest/globals'
```

## Infrastructure

| Service | Purpose |
|---------|---------|
| Docker Compose | Local development orchestration |
| PostgreSQL | Primary database (localhost:5432) |
| Redis | Cache and sessions (localhost:6379) |
| Maildev | Email testing (localhost:1080) |

## AI/ML Stack

| Technology | Purpose |
|------------|---------|
| Azure OpenAI | LLM for RAG chatbot |
| Pinecone | Vector database for embeddings |
| n8n | Workflow orchestration for embedding pipeline |

## Multi-Tenancy Architecture

- **CompanyScopedEntity** - All tenant entities implement this interface
- **CompanyContext** - ThreadLocal company ID from `X-Company-Id` header
- **CompanyScopeAspect** - Validates company scope on all save operations
- **@PeriodProtected** - Protects closed accounting periods from modification

## Key Paths

```
backend/src/main/resources/db/migration/  # Flyway migrations
frontend/src/features/{domain}/           # Feature modules
frontend/src/components/ui/               # shadcn components
backend/.../test/IntegrationTest.java     # Test base class
```
