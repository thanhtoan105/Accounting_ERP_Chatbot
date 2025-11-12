# Project Structure

```
accounting/
├── backend/                           # Spring Boot backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/accounting/
│   │   │   │   ├── AccountingApplication.java
│   │   │   │   ├── controller/       # REST controllers
│   │   │   │   │   ├── auth/
│   │   │   │   │   ├── voucher/
│   │   │   │   │   ├── customer/
│   │   │   │   │   ├── supplier/
│   │   │   │   │   ├── report/
│   │   │   │   │   └── admin/
│   │   │   │   ├── service/          # Business logic
│   │   │   │   │   ├── impl/
│   │   │   │   │   ├── voucher/
│   │   │   │   │   ├── gl/
│   │   │   │   │   ├── report/
│   │   │   │   │   └── rag/
│   │   │   │   ├── repository/       # JPA repositories
│   │   │   │   ├── entity/            # JPA entities
│   │   │   │   ├── dto/               # DTOs
│   │   │   │   ├── config/            # Configuration
│   │   │   │   ├── security/          # Security config
│   │   │   │   ├── exception/         # Exception handlers
│   │   │   │   └── util/              # Utilities
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/     # Flyway migrations
│   │   └── test/                      # Tests
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/                          # React frontend
│   ├── src/
│   │   ├── features/                   # Feature-first modules
│   │   │   ├── auth/ (pages, components, services, index.ts)
│   │   │   ├── accounting/ (ChartOfAccounts, Vouchers/*)
│   │   │   ├── company/ (CompanySettings)
│   │   │   ├── users/ (UserManagement)
│   │   │   └── dashboard/ (Dashboard)
│   │   ├── components/
│   │   │   ├── app/ (app-sidebar.tsx, nav-main.tsx, index.ts)
│   │   │   ├── voucher/ (DeleteVoucherDialog.tsx, VoucherLineItemGrid.tsx, index.ts)
│   │   │   └── ui/ (shadcn primitives)
│   │   ├── layouts/ProtectedLayout.tsx
│   │   ├── routes/AppRoutes.tsx
│   │   ├── hooks/                      # Custom hooks
│   │   ├── services/                   # API services
│   │   ├── types/                      # TypeScript types
│   │   ├── utils/                      # Utilities
│   │   └── App.tsx
│   ├── public/
│   ├── package.json
│   └── Dockerfile
│
├── docker-compose.yml                 # Local development
├── n8n/                               # n8n workflows
│   └── workflows/
│       └── rag-indexing.json
└── docs/                              # Documentation
```

---
