# Accounting ERP System with AI Chatbot

[English](#english) | [Tiếng Việt](#tiếng-việt)

---

## English

### Overview

A comprehensive multi-tenant accounting ERP system built for Vietnamese businesses, featuring AI-powered RAG chatbot assistance, real-time analytics, and compliance with Vietnamese accounting standards (Thông tư 200/2014/TT-BTC).

### Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 21, Spring Boot 3.5.7, Spring Security, JPA/Hibernate |
| **Frontend** | React 19, TypeScript, Vite 7, Tailwind CSS 4, shadcn/ui |
| **Database** | PostgreSQL 16 (TimescaleDB), Flyway migrations |
| **Cache** | Redis 7 |
| **Analytics** | Metabase (embedded dashboards) |
| **AI/RAG** | n8n workflows, Azure OpenAI, Pinecone vector DB |
| **Testing** | JUnit 5, Testcontainers, Vitest, Playwright |

### Features

#### Core Accounting
- **Chart of Accounts** - Vietnamese standard account system (Thông tư 200)
- **General Ledger** - Journal entries with double-entry bookkeeping
- **Vouchers** - Multi-type voucher management with templates
- **Cash Book** - Cash and bank transaction tracking

#### Accounts Receivable (AR)
- Sales invoices with VAT handling
- Customer management
- AR aging reports
- Customer statements
- Receipt processing

#### Accounts Payable (AP)
- Purchase bills with approval workflow
- Supplier management
- AP aging reports
- Supplier statements
- Payment processing

#### Financial Reports
- Trial Balance with drill-down
- Multi-period comparison
- Statutory reports (Vietnamese compliance)
- VAT reports (Input/Output)
- Bank reconciliation

#### Analytics & BI
- Executive KPI dashboards
- Revenue/Expense charts
- Cash flow analysis
- Metabase embedded analytics

#### AI Chatbot (RAG)
- Natural language queries for accounting data
- Vietnamese accounting regulations assistance
- Thông tư 200 document parsing and retrieval

#### Administration
- Multi-tenant architecture
- Role-based access control (RBAC)
- Audit logging
- User & company management

### Project Structure

```
accounting/
├── backend/                 # Spring Boot application
│   ├── src/main/java/
│   │   └── com/accounting/
│   │       ├── controller/  # REST API endpoints
│   │       ├── service/     # Business logic
│   │       ├── entity/      # JPA entities
│   │       ├── repository/  # Data access
│   │       ├── dto/         # Data transfer objects
│   │       ├── security/    # JWT & RBAC
│   │       └── seed/        # Demo data seeder
│   └── src/main/resources/
│       └── db/migration/    # Flyway SQL migrations
├── frontend/                # React application
│   └── src/
│       ├── features/        # Feature modules
│       │   ├── accounting/  # Core accounting pages
│       │   ├── analytics/   # BI dashboards
│       │   ├── chatbot/     # AI assistant
│       │   └── ...
│       ├── components/      # Shared UI components
│       ├── services/        # API clients
│       └── i18n/            # Internationalization (EN/VI)
├── tests/                   # E2E & integration tests
│   ├── e2e/                 # Playwright E2E tests
│   ├── api/                 # API integration tests
│   └── load/                # Load testing
├── docs/                    # Documentation
│   ├── architecture/        # System architecture
│   ├── PRD/                 # Product requirements
│   └── epics/               # User stories
└── docker/                  # Docker configurations
```

### Getting Started

#### Prerequisites

- Java 21 (recommend using SDKMAN)
- Node.js 18+ (check `.nvmrc`)
- pnpm 8+
- Docker & Docker Compose
- Maven Daemon (mvnd) - optional but recommended

#### Quick Start

```bash
# 1. Start infrastructure services
docker compose up -d

# 2. Start backend (with demo data)
cd backend
mvnd spring-boot:run -Dspring-boot.run.arguments=--seedDemo=true

# 3. Start frontend (new terminal)
cd frontend
pnpm install
pnpm dev
```

#### Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| Frontend | http://localhost:5173 | See demo users below |
| Backend API | http://localhost:8080 | - |
| Metabase | http://localhost:3000 | Setup required |
| PostgreSQL | localhost:5432 | accounting / accounting123 |
| Redis | localhost:6379 | - |

#### Demo Users

All demo users use password: `Demo@12345`

| Email | Role | Description |
|-------|------|-------------|
| admin@demo.local | Admin | Full system access |
| accountant@demo.local | Accountant | Standard accounting operations |
| chief@demo.local | Chief Accountant | Approval & review access |
| cfo@demo.local | CFO | Executive dashboards & reports |

### Development Commands

#### Backend

```bash
cd backend

# Run with hot reload
mvnd spring-boot:run -Dquickly

# Run with demo data seed
mvnd spring-boot:run -Dspring-boot.run.arguments=--seedDemo=true

# Run tests
mvnd test -Dquickly

# Build
mvnd clean package -Dquickly

# Format code
mvnd spotless:apply -Dquickly
```

#### Frontend

```bash
cd frontend

# Development server
pnpm dev

# Build for production
pnpm build

# Run unit tests
pnpm test

# Lint check
pnpm lint

# Format code
pnpm format:fix
```

#### E2E Tests

```bash
# Run Playwright tests
pnpm exec playwright test

# Run with UI
pnpm exec playwright test --ui
```

### API Documentation

The backend exposes RESTful APIs. Key endpoints:

| Module | Base Path | Description |
|--------|-----------|-------------|
| Auth | `/api/auth` | Login, registration, JWT tokens |
| Companies | `/api/companies` | Company management |
| Chart of Accounts | `/api/chart-of-accounts` | Account management |
| Vouchers | `/api/vouchers` | Voucher CRUD & posting |
| Sales Invoices | `/api/sales-invoices` | AR invoices |
| Purchase Bills | `/api/purchase-bills` | AP bills |
| Payments | `/api/payments` | Payment processing |
| Reports | `/api/reports` | Financial reports |
| Chatbot | `/api/chatbot` | AI assistant queries |

### Environment Variables

Create `.env` file from `.env.example`:

```bash
cp .env.example .env
```

Key variables:

| Variable | Description |
|----------|-------------|
| `SPRING_DATASOURCE_URL` | PostgreSQL connection string |
| `JWT_SECRET` | JWT signing secret |
| `METABASE_EMBEDDING_SECRET` | Metabase SSO embedding |
| `N8N_WEBHOOK_BASE_URL` | n8n workflow automation |
| `AZURE_OPENAI_*` | Azure OpenAI credentials |
| `PINECONE_*` | Pinecone vector DB credentials |

### Contributing

1. Create feature branch from `main`
2. Follow conventional commits (`feat:`, `fix:`, `refactor:`)
3. Ensure tests pass
4. Submit PR with description

### License

Proprietary - All rights reserved.

---

## Tiếng Việt

### Tổng Quan

Hệ thống ERP kế toán đa người dùng toàn diện được xây dựng cho doanh nghiệp Việt Nam, tích hợp chatbot AI hỗ trợ bằng công nghệ RAG, phân tích thời gian thực và tuân thủ chuẩn mực kế toán Việt Nam (Thông tư 200/2014/TT-BTC).

### Công Nghệ Sử Dụng

| Tầng | Công nghệ |
|------|-----------|
| **Backend** | Java 21, Spring Boot 3.5.7, Spring Security, JPA/Hibernate |
| **Frontend** | React 19, TypeScript, Vite 7, Tailwind CSS 4, shadcn/ui |
| **Cơ sở dữ liệu** | PostgreSQL 16 (TimescaleDB), Flyway migrations |
| **Cache** | Redis 7 |
| **Phân tích** | Metabase (dashboard nhúng) |
| **AI/RAG** | n8n workflows, Azure OpenAI, Pinecone vector DB |
| **Kiểm thử** | JUnit 5, Testcontainers, Vitest, Playwright |

### Tính Năng

#### Kế Toán Cơ Bản
- **Hệ thống tài khoản** - Theo Thông tư 200
- **Sổ cái** - Bút toán kép
- **Chứng từ** - Quản lý đa loại chứng từ với mẫu
- **Sổ quỹ** - Theo dõi giao dịch tiền mặt và ngân hàng

#### Công Nợ Phải Thu (AR)
- Hóa đơn bán hàng với xử lý VAT
- Quản lý khách hàng
- Báo cáo tuổi nợ phải thu
- Sao kê khách hàng
- Xử lý phiếu thu

#### Công Nợ Phải Trả (AP)
- Hóa đơn mua hàng với quy trình phê duyệt
- Quản lý nhà cung cấp
- Báo cáo tuổi nợ phải trả
- Sao kê nhà cung cấp
- Xử lý thanh toán

#### Báo Cáo Tài Chính
- Bảng cân đối thử với drill-down
- So sánh đa kỳ
- Báo cáo pháp định (tuân thủ Việt Nam)
- Báo cáo thuế GTGT (đầu vào/đầu ra)
- Đối chiếu ngân hàng

#### Phân Tích & BI
- Dashboard KPI điều hành
- Biểu đồ doanh thu/chi phí
- Phân tích dòng tiền
- Phân tích nhúng Metabase

#### Chatbot AI (RAG)
- Truy vấn dữ liệu kế toán bằng ngôn ngữ tự nhiên
- Hỗ trợ quy định kế toán Việt Nam
- Phân tích và truy xuất tài liệu Thông tư 200

#### Quản Trị
- Kiến trúc đa tenant
- Phân quyền theo vai trò (RBAC)
- Ghi nhật ký kiểm toán
- Quản lý người dùng & công ty

### Cấu Trúc Dự Án

```
accounting/
├── backend/                 # Ứng dụng Spring Boot
│   ├── src/main/java/
│   │   └── com/accounting/
│   │       ├── controller/  # API endpoints
│   │       ├── service/     # Logic nghiệp vụ
│   │       ├── entity/      # JPA entities
│   │       ├── repository/  # Truy cập dữ liệu
│   │       ├── dto/         # Đối tượng truyền dữ liệu
│   │       ├── security/    # JWT & RBAC
│   │       └── seed/        # Dữ liệu demo
│   └── src/main/resources/
│       └── db/migration/    # Flyway SQL migrations
├── frontend/                # Ứng dụng React
│   └── src/
│       ├── features/        # Module tính năng
│       │   ├── accounting/  # Trang kế toán chính
│       │   ├── analytics/   # Dashboard BI
│       │   ├── chatbot/     # Trợ lý AI
│       │   └── ...
│       ├── components/      # Component UI dùng chung
│       ├── services/        # API clients
│       └── i18n/            # Đa ngôn ngữ (EN/VI)
├── tests/                   # Test E2E & tích hợp
│   ├── e2e/                 # Playwright E2E tests
│   ├── api/                 # API integration tests
│   └── load/                # Load testing
├── docs/                    # Tài liệu
│   ├── architecture/        # Kiến trúc hệ thống
│   ├── PRD/                 # Yêu cầu sản phẩm
│   └── epics/               # User stories
└── docker/                  # Cấu hình Docker
```

### Bắt Đầu

#### Yêu Cầu Hệ Thống

- Java 21 (khuyến nghị sử dụng SDKMAN)
- Node.js 18+ (xem `.nvmrc`)
- pnpm 8+
- Docker & Docker Compose
- Maven Daemon (mvnd) - tùy chọn nhưng khuyến nghị

#### Khởi Động Nhanh

```bash
# 1. Khởi động các dịch vụ hạ tầng
docker compose up -d

# 2. Khởi động backend (với dữ liệu demo)
cd backend
mvnd spring-boot:run -Dspring-boot.run.arguments=--seedDemo=true

# 3. Khởi động frontend (terminal mới)
cd frontend
pnpm install
pnpm dev
```

#### Điểm Truy Cập

| Dịch vụ | URL | Thông tin đăng nhập |
|---------|-----|---------------------|
| Frontend | http://localhost:5173 | Xem người dùng demo bên dưới |
| Backend API | http://localhost:8080 | - |
| Metabase | http://localhost:3000 | Cần thiết lập |
| PostgreSQL | localhost:5432 | accounting / accounting123 |
| Redis | localhost:6379 | - |

#### Người Dùng Demo

Tất cả người dùng demo sử dụng mật khẩu: `Demo@12345`

| Email | Vai trò | Mô tả |
|-------|---------|-------|
| admin@demo.local | Admin | Toàn quyền hệ thống |
| accountant@demo.local | Kế toán viên | Nghiệp vụ kế toán cơ bản |
| chief@demo.local | Kế toán trưởng | Phê duyệt & kiểm tra |
| cfo@demo.local | CFO | Dashboard điều hành & báo cáo |

### Lệnh Phát Triển

#### Backend

```bash
cd backend

# Chạy với hot reload
mvnd spring-boot:run -Dquickly

# Chạy với dữ liệu demo
mvnd spring-boot:run -Dspring-boot.run.arguments=--seedDemo=true

# Chạy test
mvnd test -Dquickly

# Build
mvnd clean package -Dquickly

# Format code
mvnd spotless:apply -Dquickly
```

#### Frontend

```bash
cd frontend

# Server phát triển
pnpm dev

# Build production
pnpm build

# Chạy unit tests
pnpm test

# Kiểm tra lint
pnpm lint

# Format code
pnpm format:fix
```

#### E2E Tests

```bash
# Chạy Playwright tests
pnpm exec playwright test

# Chạy với UI
pnpm exec playwright test --ui
```

### Tài Liệu API

Backend cung cấp RESTful APIs. Các endpoint chính:

| Module | Đường dẫn | Mô tả |
|--------|-----------|-------|
| Auth | `/api/auth` | Đăng nhập, đăng ký, JWT tokens |
| Công ty | `/api/companies` | Quản lý công ty |
| Hệ thống tài khoản | `/api/chart-of-accounts` | Quản lý tài khoản |
| Chứng từ | `/api/vouchers` | CRUD & hạch toán chứng từ |
| Hóa đơn bán | `/api/sales-invoices` | Hóa đơn AR |
| Hóa đơn mua | `/api/purchase-bills` | Hóa đơn AP |
| Thanh toán | `/api/payments` | Xử lý thanh toán |
| Báo cáo | `/api/reports` | Báo cáo tài chính |
| Chatbot | `/api/chatbot` | Truy vấn trợ lý AI |

### Biến Môi Trường

Tạo file `.env` từ `.env.example`:

```bash
cp .env.example .env
```

Các biến chính:

| Biến | Mô tả |
|------|-------|
| `SPRING_DATASOURCE_URL` | Chuỗi kết nối PostgreSQL |
| `JWT_SECRET` | Secret ký JWT |
| `METABASE_EMBEDDING_SECRET` | SSO embedding Metabase |
| `N8N_WEBHOOK_BASE_URL` | Tự động hóa workflow n8n |
| `AZURE_OPENAI_*` | Thông tin Azure OpenAI |
| `PINECONE_*` | Thông tin Pinecone vector DB |

### Đóng Góp

1. Tạo nhánh tính năng từ `main`
2. Tuân theo conventional commits (`feat:`, `fix:`, `refactor:`)
3. Đảm bảo tests pass
4. Gửi PR với mô tả chi tiết

### Giấy Phép

Độc quyền - Bảo lưu mọi quyền.
