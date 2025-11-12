# API Contracts

## Authentication

**POST /api/v1/auth/login**

```json
Request: { "email": "user@example.com", "password": "..." }
Response: { "data": { "accessToken": "...", "refreshToken": "..." }, "meta": {...} }
```

**POST /api/v1/auth/refresh**

```json
Request: { "refreshToken": "..." }
Response: { "data": { "accessToken": "..." }, "meta": {...} }
```

## Vouchers

**GET /api/v1/vouchers**

- Query params: `page`, `size`, `status`, `dateFrom`, `dateTo`, `companyId`
- Response: `{ "data": { "content": [...], "totalElements": 100, "totalPages": 10 }, ... }`

**POST /api/v1/vouchers**

- Request: `{ "date": "2025-10-30", "description": "...", "lines": [...] }`
- Response: `{ "data": { "id": "...", "voucherNumber": "VC2025-001" }, ... }`

## Standard Error Response

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Tổng Nợ phải bằng Tổng Có",
    "details": { "debitTotal": 1000000, "creditTotal": 900000 }
  },
  "meta": {
    "timestamp": "2025-10-30T12:00:00Z",
    "requestId": "req-abc123"
  }
}
```

---
