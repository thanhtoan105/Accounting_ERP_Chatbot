# Security Architecture

## Authentication

- JWT access tokens (15-30 min expiry)
- JWT refresh tokens (7-30 days, stored in HttpOnly cookies)
- Spring Security filter chain validates tokens
- Password hashing: Argon2 or Bcrypt

## Authorization

- Role-Based Access Control (RBAC): Admin, Accountant, Chief Accountant, CFO
- Spring Security method-level security
- API-level permission checks
- Company-level data isolation

## Data Protection

- Encryption in transit: HTTPS/TLS
- Encryption at rest: Database-level encryption
- Audit trail: Immutable append-only logs
- Cryptographic hashing for audit log integrity

---
