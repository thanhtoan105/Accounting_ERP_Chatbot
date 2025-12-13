---
project_name: 'accounting'
user_name: 'thanhtoan'
date: '2025-12-09'
sections_completed: ['technology_stack', 'multi_tenancy_architecture', 'frontend_patterns', 'testing_rules']
status: 'complete'
rule_count: 45
optimized_for_llm: true
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

### Backend (Java/Spring Boot)
- **Java**: 21 (LTS) - use records, pattern matching, virtual threads where applicable
- **Spring Boot**: 3.5.7 - **Jakarta EE namespace required**:
  ```java
  import jakarta.persistence.*;      // NOT javax.persistence
  import jakarta.validation.*;       // NOT javax.validation
  ```
- **PostgreSQL**: 16.4 (driver 42.7.4)
- **Flyway**: 11.10.0 - **timestamp naming required**:
  ```
  V20251209001__description.sql     // CORRECT
  V33__description.sql              // WRONG - collision risk
  ```
- **JJWT**: 0.12.5 - JWT authentication
- **Lombok**: managed - prefer records for DTOs, `@Builder` for entities
- **Testcontainers**: 1.21.3 - **MUST extend IntegrationTest**:
  ```java
  // ✅ Shares container (~0.1s per test)
  class MyTest extends IntegrationTest { }

  // ❌ New container per class (~30s startup!)
  @Testcontainers class MyTest { }
  ```

### Frontend (React/TypeScript)
- **React**: 19.1.1 - functional components, `use()` hook for promises
- **TypeScript**: 5.9.3 - strict mode, `@/*` → `src/*`
- **Vite**: 7.1.7 - proxy `/api` → `localhost:8080`
- **Tailwind CSS**: 4.1.16 - **CSS-based config only**:
  ```css
  @import "tailwindcss";
  @theme { --color-primary: #3b82f6; }
  /* NO tailwind.config.js - v4 ignores it! */
  ```
- **shadcn/ui**: Radix - `src/components/ui/*.tsx`
- **TanStack Query**: 5.62.0 - server state
- **React Hook Form + Zod**: 7.66.0 / **4.1.12** - v4 patterns:
  ```typescript
  // ✅ v4: z.string().transform()
  // ❌ v3: z.coerce.date() doesn't exist
  ```
- **i18next**: 25.6.3 - locales: `en`, `vi`
- **Vitest**: 2.1.4 - `import { vi } from 'vitest'` NOT jest

### ⚠️ Critical Anti-Patterns (with consequences)
| Anti-Pattern | Consequence |
|--------------|-------------|
| `javax.*` imports | Compile failure in CI |
| `tailwind.config.js` | Styles silently ignored |
| `@Testcontainers` without `extends IntegrationTest` | 30s container startup per test class |
| `z.coerce.*` (Zod v3) | Runtime error |
| `V33__*.sql` naming | Migration collision |
| `@Autowired` on fields | Testability issues, Spring warns |

### 📁 Key Paths
- Migrations: `backend/src/main/resources/db/migration/VYYYYMMDDNNN__*.sql`
- Features: `frontend/src/features/{domain}/`
- UI components: `frontend/src/components/ui/*.tsx`
- Test base: `backend/.../test/IntegrationTest.java`

---

## Multi-Tenancy Architecture

### CompanyScopedEntity Pattern (CRITICAL)
Every tenant-specific entity **MUST** implement `CompanyScopedEntity`:

```java
@Entity
public class MyEntity implements CompanyScopedEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Override
    public Long getCompanyId() { return companyId; }

    public void setCompanyId(Long companyId) { this.companyId = companyId; }
}
```

**Why it matters**: `CompanyScopeAspect` intercepts ALL `save()`/`saveAll()` calls and validates that `entity.getCompanyId()` matches `CompanyContext.getCompanyId()`. Without this interface, cross-tenant data corruption is possible.

### CompanyContext Rules
- `CompanyContext.getCompanyId()` - ThreadLocal, set by `CompanyContextFilter` from `X-Company-Id` header
- **NEVER** hardcode company IDs - always use `CompanyContext.getCompanyId()`
- **NEVER** query without company scope in repository methods:
  ```java
  // ✅ CORRECT - scoped query
  List<Voucher> findByCompanyIdAndStatus(Long companyId, String status);

  // ❌ WRONG - unscoped, security vulnerability
  List<Voucher> findByStatus(String status);
  ```

### Period Protection (Accounting-Specific)
Methods that modify financial data **MUST** use `@PeriodProtected`:

```java
@PeriodProtected(dateParam = "dto.voucherDate", operation = "CREATE_VOUCHER")
public VoucherDTO createVoucher(VoucherCreateDTO dto) {
    // Only executes if voucherDate is in an OPEN period
}
```

**Behavior when period is closed**:
1. Logs `PERIOD_BLOCK_ATTEMPT` audit entry
2. Throws `BusinessException` with code `"PERIOD_CLOSED"`
3. Returns HTTP 400 to client

### Entity Creation Checklist
When creating a new entity:
- [ ] Implement `CompanyScopedEntity` if tenant-specific
- [ ] Add `@Column(name = "company_id", nullable = false)`
- [ ] Repository methods include `companyId` parameter
- [ ] Use `@PeriodProtected` on financial mutation methods
- [ ] Add `@Version` for optimistic locking on concurrent-access entities

### ⚠️ Multi-Tenancy Anti-Patterns
| Anti-Pattern | Risk |
|--------------|------|
| Entity without `CompanyScopedEntity` | Cross-tenant data leakage |
| Repository method without `companyId` filter | Security vulnerability |
| Hardcoded company ID | Data corruption |
| Missing `@PeriodProtected` on financial mutations | Audit compliance failure |
| Direct SQL without `WHERE company_id = ?` | Complete bypass of security |

---

## Frontend Patterns & API Integration

### API Layer (⚠️ SECURITY CRITICAL)
Use `fetchWithAuth` or `axiosInstance` for **ALL** API calls:
- `Authorization: Bearer <token>` (auto-refreshes on 401)
- `X-Company-Id` header (required for multi-tenant security)
- Token refresh with redirect to `/login` on failure

> ⚠️ **SECURITY**: Using raw `fetch()` bypasses multi-tenant security and can expose data to wrong companies!

```typescript
// ✅ CORRECT - use fetchWithAuth
import { fetchWithAuth } from '@/utils/axios'
const res = await fetchWithAuth(`${API_BASE}/vouchers`)
const data = await res.json()
return data.data  // Backend wraps in { data: ... }

// ❌ WRONG - SECURITY RISK
fetch('/api/v1/vouchers')  // No auth, no company context!
```

### TanStack Query Patterns (CRITICAL for Multi-Tenancy)
**Query keys MUST include companyId** for cache isolation:

```typescript
import { getCompanyId } from '@/utils/axios'

// ✅ CORRECT - company-scoped cache
const { data } = useQuery({
  queryKey: ['vouchers', getCompanyId(), filters],
  queryFn: () => getVouchers(filters),
})

// ❌ WRONG - data leaks between companies!
const { data } = useQuery({
  queryKey: ['vouchers'],  // Missing companyId
  queryFn: () => getVouchers(filters),
})
```

### Mutation Pattern
```typescript
const mutation = useMutation({
  mutationFn: createVoucher,
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['vouchers'] })
    toast.success(t('voucher.created'))
  },
  onError: (error) => {
    toast.error(error.message || t('error.generic'))
  },
})
```

### Toast Notifications (sonner)
```typescript
import { toast } from 'sonner'

// ✅ CORRECT - consistent UX
toast.success(t('voucher.saved'))
toast.error(t('error.generic'))

// ❌ WRONG - breaks UX consistency
alert('Saved!')
console.error(error)  // User sees nothing!
```

### Internationalization (i18n)
- Default: **Vietnamese (vi)**, Fallback: **English (en)**
- ALL user-facing strings MUST use `t()`:

```typescript
const { t } = useTranslation()
return <h1>{t('voucher.title')}</h1>  // ✅
return <h1>Voucher List</h1>  // ❌ Hardcoded
```

### Loading & Error States
```typescript
const { data, isLoading, error } = useQuery(...)

if (isLoading) return <LoadingSpinner />
if (error) return <ErrorDisplay message={error.message} />
return <DataDisplay data={data} />
```

### ⚠️ Frontend Anti-Patterns
| Anti-Pattern | Risk |
|--------------|------|
| `fetch()` without `fetchWithAuth` | **SECURITY** - bypasses tenant isolation |
| Query key without `companyId` | Data leaks between companies |
| Hardcoded strings | i18n compliance failure |
| `alert()` or `console.error` for user feedback | UX inconsistency |
| No loading/error states | Poor user experience |
| `response.data` (should be `response.data.data`) | Wrong data extraction |

---

## Testing Rules

### Backend Integration Tests
**ALL integration tests MUST extend `IntegrationTest`:**

```java
// ✅ CORRECT - shares PostgreSQL container
@SpringBootTest
public class VoucherServiceTest extends IntegrationTest {
    @Autowired
    private VoucherService voucherService;

    @Test
    void shouldCreateVoucher() {
        // Test with real database
    }
}

// ❌ WRONG - 30s container startup per class
@SpringBootTest
@Testcontainers
public class VoucherServiceTest {
    @Container
    static PostgreSQLContainer<?> postgres = ...;  // DON'T DO THIS
}
```

**IntegrationTest provides:**
- Shared PostgreSQL 16.4-alpine container via Testcontainers
- `flyway.clean()` + `flyway.migrate()` before each test
- Proper Spring context configuration

### Backend Unit Tests
For service unit tests (no database):
```java
@ExtendWith(MockitoExtension.class)
class VoucherValidationServiceTest {
    @Mock
    private VoucherRepository voucherRepository;

    @InjectMocks
    private VoucherValidationServiceImpl service;

    @Test
    void shouldValidateVoucher() {
        // Mock-based test
    }
}
```

### Frontend Tests (Vitest)
```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

// Mock company context for multi-tenant tests
vi.mock('@/utils/axios', () => ({
  getCompanyId: vi.fn(() => 1),
  fetchWithAuth: vi.fn(),
}))

describe('VoucherList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('displays vouchers', async () => {
    render(<VoucherList />)
    await waitFor(() => {
      expect(screen.getByText('Voucher')).toBeInTheDocument()
    })
  })
})
```

### Test File Naming
| Type | Backend Pattern | Frontend Pattern |
|------|-----------------|------------------|
| Unit test | `*Test.java` | `*.test.ts(x)` |
| Integration test | `*IntegrationTest.java` | `*.spec.ts(x)` |
| Location | `src/test/java/...` | `__tests__/` or co-located |

### ⚠️ Testing Anti-Patterns
| Anti-Pattern | Risk |
|--------------|------|
| `@Testcontainers` without `extends IntegrationTest` | 30s startup per class |
| Using Jest imports (`@jest/*`) | Wrong test framework |
| Tests without company context mocking | Multi-tenant bugs |
| No `flyway.clean()` between tests | Test pollution |
| Mocking `CompanyContext` incorrectly | Security bugs pass tests |

---

## Usage Guidelines

**For AI Agents:**
- Read this file before implementing any code
- Follow ALL rules exactly as documented
- When in doubt, prefer the more restrictive option
- Pay special attention to ⚠️ SECURITY and CRITICAL markers

**For Humans:**
- Keep this file lean and focused on agent needs
- Update when technology stack changes
- Review quarterly for outdated rules
- Remove rules that become obvious over time

---

_Last Updated: 2025-12-09_