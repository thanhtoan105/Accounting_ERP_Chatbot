## ADDED Requirements

### Requirement: Company Membership Enforcement
The system SHALL require all users (except super administrators) to belong to a company.

#### Scenario: User with company logs in
- **WHEN** a user with a valid companyId attempts to log in with correct credentials
- **THEN** the login succeeds
- **AND** the user receives a JWT token with company context

#### Scenario: User without company logs in
- **WHEN** a user without a companyId (and not a super admin) attempts to log in with correct credentials
- **THEN** the login succeeds
- **AND** the JWT token includes claim `requiresCompany: true`
- **AND** the user is only allowed to access `/api/v1/auth/*` and `/api/v1/invitations/*` endpoints
- **AND** all other API calls return 403 Forbidden with message "Company membership required"

#### Scenario: Super admin without company allowed
- **WHEN** a super admin user without a companyId attempts to log in
- **THEN** the login succeeds
- **AND** the user receives a JWT token without company context

---

### Requirement: Company Context Validation
The system SHALL validate that the X-Company-Id header matches the authenticated user's company.

#### Scenario: Matching company header
- **WHEN** authenticated user sends request with X-Company-Id matching their companyId
- **THEN** the request is processed with the specified company context

#### Scenario: Mismatched company header
- **WHEN** authenticated user sends request with X-Company-Id NOT matching their companyId
- **THEN** the system returns a 403 Forbidden error
- **AND** logs a security warning with user ID and attempted company ID

#### Scenario: Missing company header with user context
- **WHEN** authenticated user sends request without X-Company-Id header
- **THEN** the system uses the user's default companyId as context

---

### Requirement: Awaiting Company State
The system SHALL provide a dedicated state for users who have authenticated but do not belong to any company.

#### Scenario: Frontend routing for no company
- **WHEN** user logs in and has no companyId
- **THEN** the frontend redirects to the "Awaiting Company" page
- **AND** displays instructions to contact an administrator for an invitation
