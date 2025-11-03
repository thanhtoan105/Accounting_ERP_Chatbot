# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Docker Services

- Start all services: `docker compose up -d`
- Stop all services: `docker compose down`
- View logs: `docker compose logs -f [service-name]`

### Backend (Java 21 + Spring Boot 3.5.7)

- Run development server: `cd backend && mvn spring-boot:run`
- Run tests: `cd backend && mvn test`
- Build: `cd backend && mvn clean package`
- Format code: `cd backend && mvn spotless:apply`
- Run specific test: `cd backend && mvn test -Dtest=ClassName`

### Frontend (React + TypeScript + Vite)

- Install dependencies: `cd frontend && pnpm install`
- Run development server: `cd frontend && pnpm dev`
- Build for production: `cd frontend && pnpm build`
- Run tests: `cd frontend && pnpm test`
- Run tests in watch mode: `cd frontend && pnpm test:watch`
- Format code: `cd frontend && pnpm format:fix`
- Lint code: `cd frontend && pnpm lint`

## Architecture Overview

This is a multi-tenant accounting system built as a monorepo with clear separation between backend and frontend.

### Backend Architecture (Spring Boot)

**Multi-tenancy Pattern**: The system uses row-level security through a company-scoped architecture:

- Every business entity extends `CompanyScopedEntity` interface (gets `companyId` field)
- `CompanyContext` manages the current company ID in ThreadLocal storage
- `CompanyContextFilter` extracts company from JWT and sets context
- `CompanyScopeAspect` and `CompanyScopeEnforcer` automatically apply company filtering

**Security Stack**:

- JWT-based authentication with access/refresh tokens
- Spring Security with custom filter chain
- Password encoding with BCrypt
- Role-based access control (RBAC) foundation

**Database Layer**:

- PostgreSQL with Flyway migrations
- JPA/Hibernate with validation
- Automatic audit trails via `AuditLog` entity

**Key Packages**:

- `entity/`: JPA entities (Company, User, Customer, AuditLog)
- `repository/`: JPA repositories with company-scoped specifications
- `security/`: JWT, password encoding, company context management
- `service/`: Business logic layer
- `controller/`: REST API endpoints with OpenAPI documentation

**Configuration**:

- Main config: `application.yml`
- Security: `SecurityConfig` class
- Database migrations in `src/main/resources/db/migration/`

### Frontend Architecture (React + TypeScript)

**UI Framework**: Material-UI (MUI) with Joy UI components
**Routing**: React Router with protected/authenticated route layouts
**State Management**: React hooks and context
**API Communication**: Axios with TypeScript type definitions
**Build Tool**: Vite with proxy to backend on `/api`

**Key Directories**:

- `src/components/`: Reusable UI components
- `src/pages/`: Route-level components
- `src/services/`: API service layer
- `src/hooks/`: Custom React hooks
- `src/utils/`: Utility functions

**Layout Pattern**:

- `ProtectedLayout`: Main app layout with company switcher
- `AuthLayout`: Centered layout for login/register pages

### Development Services

- **PostgreSQL**: Primary database (localhost:5432)
- **pgAdmin**: Database management UI (localhost:5050)
- **Redis**: Caching and session storage (localhost:6379)
- **Maildev**: Email testing UI (localhost:1080)

### Project Structure & Workflows

**BMAD Framework**: The project uses a structured development workflow with epic-based planning:

- Epic definitions in `docs/stories/`
- Sprint status tracking in `docs/sprint-status.yaml`
- Context XML files for technical specifications

**API Documentation**: Available at `http://localhost:8080/api/docs` (Swagger UI)

**Testing Strategy**:

- Backend: JUnit 5 + TestContainers for integration tests
- Frontend: Vitest + Testing Library + jsdom
- Coverage reporting configured for both

**Code Quality**:

- Backend: Spotless for code formatting
- Frontend: ESLint + Prettier
- Husky for pre-commit hooks
- TypeScript for type safety

## Key Development Patterns

**Company Scoping**: When working with repositories, always use the company context:

```java
// Automatic filtering through CompanyScopedEntity
List<Customer> customers = customerRepository.findAll(); // Automatically filtered by current company
```

**API Endpoints**: Follow the pattern `/api/v1/[resource]` with proper authentication:

```java
@GetMapping("/api/v1/customers")
public ResponseEntity<List<Customer>> getCustomers() // Requires JWT
```

**Frontend Routes**: Protected routes require authentication, auth routes are public:

```typescript
// Protected routes require JWT token
<Route path="/" element={<ProtectedLayout><CompanySettings /></ProtectedLayout>} />
// Auth routes are publicly accessible
<Route path="/login" element={<AuthLayout><Login /></AuthLayout>} />
```

**Database Migrations**: Use Flyway with numbered versions (V1**, V2**, etc.) and descriptive names.

Do not create markdown files unless explicitly requested by the user
