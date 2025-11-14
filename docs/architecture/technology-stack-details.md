# Technology Stack Details

## Core Technologies

**Backend:**

- Spring Boot 3.5.7
- Java 21
- Spring Data JPA 6.x (via Spring Boot)
- Spring Security 6.x
- PostgreSQL Driver (latest)
- Flyway (latest)
- Lombok (latest)
- SpringDoc OpenAPI 2.3.x

**Frontend:**

- React 18+ (latest stable)
- TypeScript 5.x
- Vite (latest stable)
- pnpm (latest stable)
- Shadcn UI (latest stable)
- Tailwind CSS 3.x
- TanStack Table 8.x
- TanStack Query 5.x
- Lucide React (latest stable)
- Axios (latest stable)
- date-fns (latest)

**Infrastructure:**

- PostgreSQL 15+ (via Supabase)
- Redis 7.x
- Pinecone (latest)
- Supabase Storage (latest)
- Docker (latest)

## Integration Points

**Backend ↔ Frontend:**

- REST API: `/api/v1/*`
- Authentication: JWT tokens in HttpOnly cookies
- CORS: Configured for frontend origin

**Backend ↔ Database:**

- Spring Data JPA repositories
- Flyway migrations for schema versioning
- Connection pooling: HikariCP (Spring Boot default)

**Backend ↔ Supabase:**

- JDBC connection string to PostgreSQL
- Supabase Storage REST API for file uploads

**Backend ↔ Pinecone:**

- Pinecone Java SDK (or REST API via RestClient)
- Vector embeddings: OpenAI `text-embedding-3-small` (1536 dimensions)

**Backend ↔ n8n:**

- n8n webhook endpoints for triggering workflows
- Scheduled workflows: RAG indexing at 2 AM daily

**Frontend ↔ Backend:**

- Axios for HTTP requests
- React Query for caching and state management

**Frontend ↔ Supabase:**

- `@supabase/supabase-js` for direct Storage operations (if needed)

---
