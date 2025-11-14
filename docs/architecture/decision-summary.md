# Decision Summary

| Category              | Decision                    | Version                     | Affects Epics     | Rationale                                                             |
| --------------------- | --------------------------- | --------------------------- | ----------------- | --------------------------------------------------------------------- |
| Backend Framework     | Spring Boot                 | 3.5.7                       | All               | Enterprise Java standard, rich ecosystem, Spring Security integration |
| Frontend Framework    | React with TypeScript       | Latest stable (React 18+)   | All               | Industry standard, large ecosystem, MUI compatibility                 |
| Build Tool (Backend)  | Maven                       | Latest                      | All               | Standard for Spring Boot projects                                     |
| Build Tool (Frontend) | Vite                        | Latest stable               | All               | Fast dev server, modern bundling                                      |
| Package Manager       | pnpm                        | Latest stable               | Frontend          | Faster, efficient disk usage                                          |
| Database              | PostgreSQL (Supabase)       | PostgreSQL 15+              | All               | ACID compliance, TT200 compliance needs, Supabase integration         |
| ORM                   | Spring Data JPA + Hibernate | 6.x (via Spring Boot 3.5.7) | All               | Industry standard for Spring Boot                                     |
| API Pattern           | REST                        | -                           | All client-facing | Simple, well-understood, OpenAPI support                              |
| Authentication        | JWT with Spring Security 6  | Spring Security 6.x         | Epic 1            | Stateless, scalable, industry standard                                |
| UI Library            | Shadcn UI + Tailwind CSS    | Latest stable               | All               | Accessible, customizable, modern design system                        |
| Data Tables           | TanStack Table              | 8.x                         | All               | Headless, flexible, powerful table functionality                      |
| Data Fetching         | TanStack Query              | 5.x                         | All               | Caching, optimistic updates, background refetching                    |
| Icons                 | Lucide React                | Latest stable               | All               | Modern, consistent icon set                                           |
| Styling               | Tailwind CSS                | 3.x                         | All               | Utility-first CSS, responsive design                                  |
| HTTP Client           | Axios                       | Latest stable               | All               | Promise-based, interceptors for auth                                  |
| Caching               | Redis                       | Redis 7.x                   | Epic 7, 8         | Fast report caching, NFR26 requirement                                |
| Vector Database       | Pinecone                    | Latest stable               | Epic 9            | PRD requirement, scalable vector search                               |
| Background Jobs       | Spring @Scheduled + @Async  | Spring Boot 3.5.7           | Epic 8, 9         | Built-in, simple for MVP                                              |
| Email Service         | Resend                      | Latest API v1               | Epic 1, 10        | Modern, developer-friendly                                            |
| File Storage          | Supabase Storage            | Latest API                  | Epic 3, 4, 5, 6   | Consistent with Supabase stack, built-in security                     |
| Search                | PostgreSQL FTS + unaccent   | PostgreSQL 15+              | Epic 2            | Native Vietnamese unaccented search support                           |
| Real-time Updates     | Polling (5 min)             | -                           | Epic 8            | Simple, meets NFR requirement (<5 min latency)                        |
| Deployment            | Docker + TBD                | Docker latest               | All               | NFR28 requirement, production target TBD                              |
| Migration Tool        | Flyway                      | Latest stable               | All               | Versioned migrations, Spring Boot integration                         |
| API Documentation     | OpenAPI/Swagger             | SpringDoc 2.3.x             | All               | Self-documenting API, NFR21 requirement                               |

---
