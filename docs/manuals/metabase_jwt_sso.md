# Metabase JWT SSO Integration

This document describes the JWT-based Single Sign-On (SSO) integration between the accounting system and Metabase for embedded analytics.

## Overview

The accounting system uses JWT tokens to authenticate users with Metabase. This allows users to access embedded dashboards without needing to log in separately to Metabase.

## JWT Token Configuration

### Token Expiration

- **JWT Token Expiration**: 60 minutes
- **Recommended Refresh Interval**: 45 minutes (before expiration)

### Token Claims

The JWT token includes the following claims:

| Claim | Description |
|-------|-------------|
| `email` | User's email address |
| `first_name` | User's first name |
| `last_name` | User's last name |
| `groups` | Array of Metabase group names the user belongs to |
| `exp` | Token expiration timestamp |
| `iat` | Token issued at timestamp |

## Frontend Token Refresh

The frontend should implement automatic token refresh to ensure uninterrupted dashboard access. Here's a recommended implementation:

### React Implementation

```typescript
import { useEffect, useRef, useCallback } from 'react';

const TOKEN_REFRESH_INTERVAL = 45 * 60 * 1000; // 45 minutes in milliseconds

interface MetabaseEmbedConfig {
  iframeUrl: string;
  token: string;
  expiresAt: number;
}

export function useMetabaseTokenRefresh(
  companyId: string,
  onTokenRefresh: (config: MetabaseEmbedConfig) => void
) {
  const refreshTimerRef = useRef<NodeJS.Timeout | null>(null);

  const refreshToken = useCallback(async () => {
    try {
      const response = await fetch('/api/analytics/embed-config', {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
          'X-Company-Id': companyId,
        },
        credentials: 'include',
      });

      if (!response.ok) {
        throw new Error('Failed to refresh Metabase token');
      }

      const config: MetabaseEmbedConfig = await response.json();
      onTokenRefresh(config);

      // Schedule next refresh
      scheduleRefresh();
    } catch (error) {
      console.error('Metabase token refresh failed:', error);
      // Retry after 1 minute on failure
      refreshTimerRef.current = setTimeout(refreshToken, 60 * 1000);
    }
  }, [companyId, onTokenRefresh]);

  const scheduleRefresh = useCallback(() => {
    if (refreshTimerRef.current) {
      clearTimeout(refreshTimerRef.current);
    }
    refreshTimerRef.current = setTimeout(refreshToken, TOKEN_REFRESH_INTERVAL);
  }, [refreshToken]);

  useEffect(() => {
    // Start the refresh cycle
    scheduleRefresh();

    return () => {
      if (refreshTimerRef.current) {
        clearTimeout(refreshTimerRef.current);
      }
    };
  }, [scheduleRefresh]);

  return { refreshToken };
}
```

### Usage Example

```tsx
function DashboardView() {
  const [embedConfig, setEmbedConfig] = useState<MetabaseEmbedConfig | null>(null);
  const { companyId } = useCompanyContext();

  const { refreshToken } = useMetabaseTokenRefresh(companyId, (config) => {
    setEmbedConfig(config);
  });

  useEffect(() => {
    // Initial token fetch
    refreshToken();
  }, [refreshToken]);

  if (!embedConfig) {
    return <LoadingSpinner />;
  }

  return (
    <iframe
      src={embedConfig.iframeUrl}
      width="100%"
      height="800"
      frameBorder="0"
      allowTransparency
    />
  );
}
```

## Backend API Endpoints

### Get Embed Configuration

```
GET /api/analytics/embed-config
```

**Headers:**
- `X-Company-Id`: Company UUID (required)
- `Authorization`: Bearer token (required)

**Response:**
```json
{
  "iframeUrl": "https://metabase.example.com/embed/dashboard/...",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresAt": 1702656000000
}
```

### Generate SSO Token

```
POST /api/analytics/sso-token
```

**Headers:**
- `X-Company-Id`: Company UUID (required)
- `Authorization`: Bearer token (required)

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600
}
```

## Security Considerations

1. **Token Storage**: JWT tokens should not be stored in localStorage. Use secure HttpOnly cookies or session storage.

2. **HTTPS Only**: All communication with Metabase should be over HTTPS.

3. **Token Validation**: Metabase validates the JWT signature using a shared secret configured in the Metabase admin settings.

4. **Group Permissions**: User access to dashboards is controlled through Metabase groups, which are synced from the accounting system roles.

## User Sync Events

The system automatically syncs user changes to Metabase through Spring Events:

| Event | Action |
|-------|--------|
| `UserCreatedEvent` | Creates user in Metabase and assigns groups |
| `UserUpdatedEvent` | Updates user email/name in Metabase |
| `UserDeactivatedEvent` | Deactivates user in Metabase |
| `UserRoleChangedEvent` | Updates user group memberships |

All sync operations are:
- Asynchronous (non-blocking)
- Logged to the analytics audit trail
- Idempotent (safe to retry)

## Troubleshooting

### Token Expired Error

If users see "Token expired" errors in Metabase:
1. Check the frontend token refresh is working
2. Verify system clocks are synchronized
3. Check the JWT secret matches between systems

### User Not Found in Metabase

If the user sync failed:
1. Check the audit logs for sync errors
2. Verify the tenant is provisioned in Metabase
3. Trigger a manual sync via the admin API

### Group Access Denied

If users can't access expected dashboards:
1. Verify the role-to-group mapping
2. Check the user's groups in Metabase admin
3. Ensure the tenant group is properly configured
