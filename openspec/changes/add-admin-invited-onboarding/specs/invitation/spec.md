## ADDED Requirements

### Requirement: Secure Invitation Token
The system SHALL generate secure, time-limited invitation tokens.

#### Scenario: Token generation
- **WHEN** an invitation is created
- **THEN** the system generates a cryptographically secure random token
- **AND** stores only the SHA-256 hash of the token in the database
- **AND** sets an expiration time (default 24 hours)

#### Scenario: Token validation - valid
- **WHEN** user submits a valid, unexpired, unrevoked, unaccepted token
- **THEN** the system returns the invitation details (email, company name, role)

#### Scenario: Token validation - expired
- **WHEN** user submits an expired token
- **THEN** the system returns a 410 Gone error
- **AND** the response indicates "Invitation has expired"

#### Scenario: Token validation - already accepted
- **WHEN** user submits a token that has already been accepted
- **THEN** the system returns a 410 Gone error
- **AND** the response indicates "Invitation has already been used"

#### Scenario: Token validation - revoked
- **WHEN** user submits a revoked token
- **THEN** the system returns a 410 Gone error
- **AND** the response indicates "Invitation has been revoked"

---

### Requirement: Invitation Acceptance
The system SHALL allow users to accept invitations and create their accounts.

#### Scenario: Successful acceptance
- **WHEN** user submits valid token with password
- **THEN** the system creates a new user account with the specified email and password
- **AND** assigns the user to the invitation's company with the specified role
- **AND** marks the invitation as accepted with timestamp and IP address
- **AND** returns authentication tokens for immediate login

#### Scenario: Password validation
- **WHEN** user submits a password that doesn't meet security requirements
- **THEN** the system returns a 400 Bad Request error
- **AND** includes password policy violations in the response

#### Scenario: Email already exists
- **WHEN** user tries to accept invitation but email is already registered
- **THEN** the system returns a 409 Conflict error
- **AND** the response indicates "Email is already registered"

---

### Requirement: Invitation Revocation
The system SHALL allow administrators to revoke pending invitations.

#### Scenario: Revoke pending invitation
- **WHEN** admin revokes a pending invitation
- **THEN** the system marks the invitation as revoked with timestamp
- **AND** the invitation token becomes invalid

#### Scenario: Cannot revoke accepted invitation
- **WHEN** admin tries to revoke an already-accepted invitation
- **THEN** the system returns a 400 Bad Request error
- **AND** the response indicates "Cannot revoke an accepted invitation"

---

### Requirement: Invitation Resend
The system SHALL allow administrators to resend invitations.

#### Scenario: Resend invitation
- **WHEN** admin requests to resend an invitation
- **THEN** the system revokes the old token
- **AND** generates a new token with fresh expiration
- **AND** sends a new invitation email
- **AND** returns the new invitation details

---

### Requirement: Invitation Rate Limiting
The system SHALL limit the rate of invitation operations to prevent abuse.

#### Scenario: Creation rate limit enforced
- **WHEN** admin creates more than 5 invitations within 1 hour
- **THEN** the system returns a 429 Too Many Requests error
- **AND** the response includes the retry-after time

#### Scenario: Validation rate limit enforced
- **WHEN** any client attempts to validate more than 10 tokens within 1 minute from the same IP
- **THEN** the system returns a 429 Too Many Requests error
- **AND** logs the IP address for security monitoring

#### Scenario: Accept rate limit enforced
- **WHEN** any client attempts to accept invitations more than 5 times within 5 minutes from the same IP
- **THEN** the system returns a 429 Too Many Requests error
- **AND** the response uses generic error message to prevent enumeration

---

### Requirement: Invitation Audit Trail
The system SHALL maintain an audit trail of all invitation activities.

#### Scenario: Invitation created logged
- **WHEN** an invitation is created
- **THEN** the system records: inviter ID, invitee email, company ID, role, timestamp

#### Scenario: Invitation accepted logged
- **WHEN** an invitation is accepted
- **THEN** the system records: token hash, created user ID, IP address, timestamp

#### Scenario: Invitation revoked logged
- **WHEN** an invitation is revoked
- **THEN** the system records: revoker ID, reason (if provided), timestamp
