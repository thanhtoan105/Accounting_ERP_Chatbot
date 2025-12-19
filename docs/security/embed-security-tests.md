# Embed Security Tests Documentation

## Overview

This document describes the security tests for Metabase embed URL tampering prevention, implemented to satisfy **AC 8.0.3**.

## Acceptance Criteria

> AC 8.0.3: Security test - forcing company_id in embed URL is ignored

The locked `company_id` in embed URLs cannot be overridden by URL parameter manipulation, JWT tampering, or any other means.

## Architecture

### JWT-based Security Model

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Frontend      │────▶│   Backend API   │────▶│   Metabase      │
│   (React)       │     │   (Spring Boot) │     │   (SSO JWT)     │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │                       │
        │                       │                       │
        ▼                       ▼                       ▼
   URL params            JWT generation           JWT validation
   (ignored)           (locked company_id)      (signature check)
```

### Security Flow

1. User authenticates → Backend identifies user's `company_id`
2. Backend generates JWT with **locked** `company_id` claim
3. JWT is signed with `METABASE_JWT_SECRET`
4. Frontend cannot modify the JWT without invalidating signature
5. Metabase validates JWT signature before extracting claims

## Test Files

### Backend Unit Tests

**File:** `backend/src/test/java/com/accounting/security/EmbedSecurityTest.java`

| Test Category | Test Name | Description |
|--------------|-----------|-------------|
| JWT Generation | `embedConfig_shouldIncludeLockedCompanyId` | Verifies JWT contains correct company_id |
| JWT Generation | `embedConfig_shouldIncludeTenantGroup` | Verifies JWT includes tenant group |
| JWT Generation | `generatedJwt_shouldHaveValidSignature` | Validates JWT signature |
| Tampering Detection | `tamperedJwt_shouldBeRejected` | Modified JWT fails signature check |
| Tampering Detection | `jwtWithWrongSignature_shouldThrowSignatureException` | Wrong secret is detected |
| Expiration | `expiredJwt_shouldBeRejected` | Expired tokens are rejected |
| Expiration | `expiredJwt_shouldThrowExpiredJwtException` | Proper exception is thrown |
| Multi-tenant | `userFromCompanyA_shouldGetJwtWithCompanyAIdOnly` | Company A user gets Company A JWT |
| Multi-tenant | `companyAJwt_cannotAccessCompanyBData` | Cross-tenant access is blocked |
| Token Replay | `sameToken_shouldValidateConsistentlyWithinExpiryWindow` | Valid tokens work consistently |

### Test Utilities

**File:** `backend/src/test/java/com/accounting/security/JwtTestUtils.java`

| Method | Purpose |
|--------|---------|
| `createValidEmbedJwt()` | Creates a valid JWT for testing |
| `createExpiredJwt()` | Creates an expired JWT |
| `tamperCompanyId()` | Modifies company_id without updating signature |
| `createJwtWithWrongSecret()` | Creates JWT with wrong signing key |
| `validateJwt()` | Validates JWT signature and expiration |
| `extractCompanyId()` | Extracts company_id from valid JWT |

### E2E Tests (Playwright)

**File:** `tests/e2e/embed-security.spec.ts`

| Test Category | Test Name | Priority |
|--------------|-----------|----------|
| URL Tampering | `tampering with company_id in URL should not change data access` | P0 |
| URL Tampering | `adding extra company_id query params should be ignored` | P0 |
| Invalid JWT | `request with malformed JWT should return 401` | P1 |
| Invalid JWT | `request with expired JWT should return 401` | P1 |
| Invalid JWT | `request without authorization header should return 401` | P1 |
| JWT Security | `modified JWT payload should be rejected by backend` | P1 |
| JWT Security | `JWT with wrong signing key should be rejected` | P2 |
| Multi-tenant | `user from company A cannot access company B embed data` | P0 |

## Running Tests

### Backend Tests

```bash
cd backend && mvnd test -Dtest=EmbedSecurityTest
```

### E2E Tests

```bash
pnpm exec playwright test tests/e2e/embed-security.spec.ts
```

## Security Considerations

### What's Protected

1. **JWT Signature Verification**: Any modification to the JWT payload invalidates the signature
2. **Locked company_id**: The company_id is set server-side based on authenticated user
3. **URL Parameter Ignoring**: Query parameters for company_id are ignored; JWT is authoritative
4. **Token Expiration**: JWTs expire after 60 minutes
5. **Tenant Isolation**: Users can only access data for their assigned company

### Attack Vectors Tested

| Attack Vector | Defense | Test Coverage |
|--------------|---------|---------------|
| URL parameter injection | Server ignores URL params, uses JWT | E2E tests |
| JWT payload tampering | HMAC signature verification | Unit tests |
| Expired token replay | Expiration check | Unit + E2E tests |
| Cross-tenant access | company_id locked in JWT | Unit + E2E tests |
| Invalid/malformed JWT | Parser validation | Unit + E2E tests |

## Related Files

- `MetabaseEmbedServiceImpl.java` - JWT generation with locked company_id
- `JwtTokenProvider.java` - General JWT utilities
- `JwtAuthenticationFilter.java` - Request authentication filter
