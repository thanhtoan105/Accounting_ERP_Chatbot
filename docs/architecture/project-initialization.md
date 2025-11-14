# Project Initialization

## Backend (Spring Boot)

First implementation story should execute Spring Boot project initialization:

**Via Spring Initializer (https://start.spring.io/):**

```
Project: Maven
Language: Java
Spring Boot: 3.5.7
Project Metadata:
  Group: com.accounting
  Artifact: accounting-backend
  Name: accounting-backend
  Package name: com.accounting
  Packaging: Jar
  Java: 21

Dependencies:
- Spring Boot Starter Web
- Spring Boot Starter Data JPA
- Spring Boot Starter Security
- Spring Boot Starter Validation
- Spring Boot Starter Cache
- Spring Data Redis
- PostgreSQL Driver
- Flyway Migration
- Lombok
- SpringDoc OpenAPI (Swagger)
```

**Or via Spring CLI:**

```bash
spring init --dependencies=web,data-jpa,security,validation,cache,data-redis,postgresql,flyway,lombok --build=maven --java-version=21 --packaging=jar --name=accounting-backend --package-name=com.accounting --groupId=com.accounting --artifactId=accounting-backend --version=0.0.1-SNAPSHOT accounting-backend
```

This establishes the base architecture with these decisions:

- **Framework:** Spring Boot 3.5.7 (PROVIDED BY STARTER)
- **Build Tool:** Maven (PROVIDED BY STARTER)
- **Java Version:** 21 (PROVIDED BY STARTER)
- **Web Framework:** Spring MVC (PROVIDED BY STARTER)
- **ORM:** Spring Data JPA with Hibernate (PROVIDED BY STARTER)
- **Security:** Spring Security (PROVIDED BY STARTER)
- **Database:** PostgreSQL Driver (PROVIDED BY STARTER)
- **Migration:** Flyway (PROVIDED BY STARTER)
- **Caching:** Spring Cache with Redis (PROVIDED BY STARTER)

## Frontend (React + TypeScript)

First implementation story should execute React project initialization:

```bash
pnpm create vite frontend --template react-ts
cd frontend
pnpm install
```

Then add Tailwind CSS:

```bash
pnpm add -D tailwindcss postcss autoprefixer
pnpx tailwindcss init -p
```

Then add Shadcn UI:

```bash
pnpm add -D @shadcn/ui
pnpx shadcn@latest init
```

Configuration for `shadcn init`:

- Style: Default
- Base color: Slate
- CSS variables: Yes

Then add required dependencies:

```bash
pnpm add @tanstack/react-query axios
pnpm add @tanstack/react-table
pnpm add @supabase/supabase-js
pnpm add date-fns
pnpm add class-variance-authority clsx tailwind-merge
pnpm add lucide-react
pnpm add -D @types/node
```

Add commonly needed Shadcn components:

```bash
pnpx shadcn@latest add button input form table dialog card select dropdown-menu toast
```

This establishes the base architecture with these decisions:

- **Framework:** React with TypeScript (PROVIDED BY STARTER)
- **Build Tool:** Vite (PROVIDED BY STARTER)
- **Package Manager:** pnpm (USER PREFERENCE)
- **UI Library:** Shadcn UI + Tailwind CSS (DECISION)
- **Styling:** Tailwind CSS with CSS variables (DECISION)
- **Data Tables:** TanStack Table (DECISION)
- **Icons:** Lucide React (DECISION)
- **Data Fetching:** TanStack Query (DECISION)
- **HTTP Client:** Axios (DECISION)

---
