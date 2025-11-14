## Component Diagram

```mermaid
flowchart TB
  subgraph Frontend [Frontend (React + TS + Vite)]
    UI[shadcn/ui Components]
    Routes[React Router]
    AuthCtx[Auth Hooks/Context]
    Services[Axios Typed Services]
    UI --> Routes
    UI --> Services
    Routes --> AuthCtx
  end

  subgraph Backend [Backend (Spring Boot 3.5, Java 21)]
    Controller[REST Controllers /api/v1/*]
    Security[Spring Security + JWT Filters]
    CompanyCtx[CompanyContext + Filters + AOP]
    Service[Service Layer]
    Repo[JPA Repositories]
    Entities[JPA Entities]
    Flyway[Flyway Migrations]
    Controller --> Security
    Controller --> CompanyCtx
    Controller --> Service
    Service --> Repo
    Repo --> Entities
    Flyway --> Entities
  end

  subgraph Infra [Infrastructure]
    DB[(PostgreSQL)]
    Redis[(Redis)]
    Maildev[(Maildev)]
  end

  Services <---> Controller
  Repo <---> DB
  Security <---> Redis
  Controller --> Maildev
```


