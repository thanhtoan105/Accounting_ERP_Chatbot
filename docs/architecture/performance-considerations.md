# Performance Considerations

## Caching Strategy

- Redis caching for reports (Trial Balance, Aging, Financial Statements)
- Cache TTL: 1 hour (configurable)
- Manual invalidation: On GL post, period close
- Spring Cache abstraction with Redis backend

## Database Optimization

- Indexes on foreign keys, dates, account codes
- Avoid N+1 queries (use `@EntityGraph` or joins)
- Query optimization for complex reports
- Connection pooling: HikariCP

## Frontend Optimization

- React Query caching and background refetching
- Code splitting via Vite
- Lazy loading for routes
- TanStack Table virtualization for large tables
- Tailwind CSS JIT compilation for optimal bundle size

## Known Limitations

### Search Pagination Limitation

**Issue:** Search queries using native SQL with unaccented Vietnamese support return all matching results before pagination is applied in-memory.

**Impact:** Performance degradation with large result sets (>1000 records). Search queries load all matching records into memory before pagination.

**Current Implementation:**

- Native SQL queries with PostgreSQL `unaccent` function return all results
- Pagination is applied in-memory after query execution
- Acceptable for MVP with typical result sets (<1000 records)

**Affected Features:**

- Customer master data search (Story 2-2)
- Supplier master data search (Story 2-3)

**Future Optimization:**

- Implement database-level pagination in native queries (LIMIT/OFFSET)
- Consider full-text search solution (PostgreSQL FTS) for better scalability
- Add result set size limits for search queries

**Location:** `SupplierServiceImpl.java` (lines 87-103), `CustomerServiceImpl.java` (similar pattern)

---
