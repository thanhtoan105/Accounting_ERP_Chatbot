# Metabase Integration Tests

## Overview

Integration tests for Metabase API using Testcontainers. These tests spin up a real Metabase instance to verify API contract compatibility.

## Prerequisites

- Docker running locally
- At least 4GB RAM available for containers
- Maven or mvnd installed

## Test Files

| File | Description |
|------|-------------|
| `MetabaseTestContainer.java` | Helper class for Metabase container setup and authentication |
| `MetabaseApiIntegrationTest.java` | Integration tests for MetabaseApiClient |

## Running Tests

### Run all Metabase integration tests

```bash
cd backend
mvnd test -Dtest=MetabaseApiIntegrationTest -DfailIfNoTests=false
```

### Run a specific test method

```bash
cd backend
mvnd test -Dtest=MetabaseApiIntegrationTest#testMetabaseHealthCheck
```

### Enable disabled tests

Tests are `@Disabled` by default because they take 2-5 minutes. To run them:

```bash
cd backend
mvnd test -Dtest=MetabaseApiIntegrationTest -DexcludedGroups="" -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition
```

## Notes

### Startup Time
- Metabase container takes **2-5 minutes** to fully initialize
- The container uses `waitingFor(Wait.forHttp("/api/health").forStatusCode(200))` to ensure readiness
- Initial setup (creating admin user) adds additional time on first run

### Container Image
- Uses `metabase/metabase:v0.50.36` to match production
- H2 embedded database is used for simplicity (no persistence needed for tests)

### Initial Setup
- Metabase requires initial setup before API is usable
- `MetabaseTestContainer.setupMetabaseAndGetSessionToken()` handles:
  1. Getting the setup token from `/api/session/properties`
  2. Completing setup via `POST /api/setup`
  3. Authenticating to get a session token

### Memory Requirements
- Container configured with `JAVA_TOOL_OPTIONS=-Xmx1g`
- Ensure Docker has at least 4GB RAM allocated

## Test Coverage

| Test | Description | Status |
|------|-------------|--------|
| `testMetabaseContainerIsRunning` | Verify container started | ✅ Implemented |
| `testMetabaseHealthCheck` | GET /api/health returns 200 | ✅ Implemented |
| `testMetabaseSetupCompleted` | Verify initial setup succeeded | ✅ Implemented |
| `testMetabaseApiClientAuthentication` | Test MetabaseApiClient auth | ✅ Implemented |
| `testUserProvisioningStub` | User create/update/deactivate | 🔲 Stub |
| `testGroupManagementStub` | Group CRUD operations | 🔲 Stub |
| `testDatabaseConnectionStub` | Database connection CRUD | 🔲 Stub |
| `testJwtSsoFlowStub` | JWT SSO token generation | 🔲 Stub |

## Expanding Tests

To implement the stub tests, follow this pattern:

```java
@Test
@Order(5)
void testUserProvisioning() {
    // Create user
    MetabaseUserResponse user = metabaseApiClient.createUser(
        "test@example.com", "Test", "User");
    assertThat(user.id()).isNotNull();
    
    // Find user
    Optional<MetabaseUserResponse> found = metabaseApiClient.findUserByEmail("test@example.com");
    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(user.id());
    
    // Update user
    MetabaseUserResponse updated = metabaseApiClient.updateUser(
        user.id(), null, "Updated", "Name");
    assertThat(updated.firstName()).isEqualTo("Updated");
    
    // Deactivate user
    metabaseApiClient.deactivateUser(user.id());
    Optional<MetabaseUserResponse> deactivated = metabaseApiClient.findUserByEmail("test@example.com");
    assertThat(deactivated).isEmpty(); // Inactive users are filtered out
}
```

## Troubleshooting

### Container fails to start
- Check Docker is running: `docker ps`
- Check available memory: `docker system info | grep Memory`
- Check logs: `docker logs <container_id>`

### Authentication fails
- Ensure Metabase setup completed successfully
- Check admin credentials match `MetabaseTestContainer.ADMIN_EMAIL` and `ADMIN_PASSWORD`

### Tests timeout
- Increase `withStartupTimeout()` in `MetabaseTestContainer.createContainer()`
- Check system resources (CPU, memory)
