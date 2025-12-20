## ADDED Requirements

### Requirement: Tenant Provisioning
The system SHALL provide an API for operators to provision new tenants (companies) with initial configuration.

#### Scenario: Successful tenant provisioning
- **WHEN** operator submits valid tenant provisioning request with company name, tax code, COA preset, and admin email
- **THEN** the system creates a new company with the specified configuration
- **AND** seeds the chart of accounts based on the selected preset (TT200 or TT133)
- **AND** creates an invitation for the initial admin user
- **AND** sends an invitation email to the admin
- **AND** returns the company ID and invitation ID

#### Scenario: Duplicate tax code rejected
- **WHEN** operator submits a tenant provisioning request with a tax code that already exists
- **THEN** the system returns a 409 Conflict error
- **AND** does not create any company or invitation

#### Scenario: Invalid admin email
- **WHEN** operator submits a tenant provisioning request with an invalid email format
- **THEN** the system returns a 400 Bad Request error
- **AND** includes validation error details

---

### Requirement: COA Template Copying
The system SHALL copy the chart of accounts from the existing template when provisioning a tenant.

#### Scenario: TT200 template applied
- **WHEN** tenant is provisioned
- **THEN** the system calls `seed_tt200_coa_from_template(company_id)` function
- **AND** all 235 TT200 accounts are copied to the new company
- **AND** all accounts are associated with the new company_id

---

### Requirement: Tenant Provisioning Audit
The system SHALL maintain an audit trail of all tenant provisioning activities.

#### Scenario: Provisioning logged
- **WHEN** a tenant is successfully provisioned
- **THEN** the system records the provisioning event with timestamp, operator ID, company ID, and configuration details
- **AND** the audit record is immutable

---

### Requirement: Operator Authorization
The system SHALL restrict tenant provisioning to authorized operators only.

#### Scenario: Authorized operator
- **WHEN** a user with SUPER_ADMIN role attempts to provision a tenant
- **THEN** the request is processed

#### Scenario: Unauthorized user
- **WHEN** a user without SUPER_ADMIN role attempts to provision a tenant
- **THEN** the system returns a 403 Forbidden error
