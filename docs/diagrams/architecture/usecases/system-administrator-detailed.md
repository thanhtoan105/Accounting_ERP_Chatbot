# System Administrator - Detailed Use Cases

```mermaid
graph TD
    %% Define styles
    classDef adminStyle fill:#e8f5e9,stroke:#388e3c,stroke-width:2px
    classDef usecaseStyle fill:#e3f2fd,stroke:#1976d2,stroke-width:2px
    classDef systemStyle fill:#f9fbff,stroke:#1976d2,stroke-width:2px,stroke-dasharray: 5 5

    %% Main actor
    Admin["<div style='font-weight:bold; font-size:14px;'><center>👤<br/>System<br/>Administrator</center></div>"]:::adminStyle

    %% System boundary (subgraph)
    subgraph SystemAdmin["<div style='font-weight:bold; font-size:16px; color:#1976d2;'>🖥️ System Administration Module</div>"]

        %% User Management Use Cases
        subgraph UserMgmt["👥 User Management"]
            CreateUser["Create User Account"]:::usecaseStyle
            UpdateUser["Update User Profile"]:::usecaseStyle
            DeactivateUser["Deactivate User Account"]:::usecaseStyle
            ResetPassword["Reset User Password"]:::usecaseStyle
            AssignRoles["Assign User Roles"]:::usecaseStyle
            ManagePermissions["Manage User Permissions"]:::usecaseStyle
        end

        %% Company Management Use Cases
        subgraph CompanyMgmt["🏢 Company Management"]
            CompanySettings["Configure Company Settings"]:::usecaseStyle
            CompanyProfile["Manage Company Profile"]:::usecaseStyle
            SetFiscalYear["Set Fiscal Year"]:::usecaseStyle
            TaxSettings["Configure Tax Settings"]:::usecaseStyle
            CurrencySettings["Manage Currency Settings"]:::usecaseStyle
        end

        %% System Configuration Use Cases
        subgraph SysConfig["⚙️ System Configuration"]
            EmailSettings["Configure Email Settings"]:::usecaseStyle
            BackupSettings["Manage Backup Settings"]:::usecaseStyle
            SecurityPolicies["Configure Security Policies"]:::usecaseStyle
            SystemNotifications["Manage System Notifications"]:::usecaseStyle
            ViewLogs["View System Logs"]:::usecaseStyle
        end

        %% Multi-tenancy Use Cases
        subgraph MultiTenant["🌐 Multi-tenancy"]
            CreateCompany["Create New Company"]:::usecaseStyle
            ManageSubscriptions["Manage Company Subscriptions"]:::usecaseStyle
            MonitorUsage["Monitor Resource Usage"]:::usecaseStyle
            TenantManagement["Tenant Isolation Management"]:::usecaseStyle
        end
    end

    %% Actor to Use Case connections
    Admin --> CreateUser
    Admin --> UpdateUser
    Admin --> DeactivateUser
    Admin --> ResetPassword
    Admin --> AssignRoles
    Admin --> ManagePermissions

    Admin --> CompanySettings
    Admin --> CompanyProfile
    Admin --> SetFiscalYear
    Admin --> TaxSettings
    Admin --> CurrencySettings

    Admin --> EmailSettings
    Admin --> BackupSettings
    Admin --> SecurityPolicies
    Admin --> SystemNotifications
    Admin --> ViewLogs

    Admin --> CreateCompany
    Admin --> ManageSubscriptions
    Admin --> MonitorUsage
    Admin --> TenantManagement

    %% Include relationships (dashed arrows)
    ManagePermissions -.-> AssignRoles
    ManagePermissions -.-> UpdateUser
    CreateCompany -.-> CompanySettings
    TenantManagement -.-> MonitorUsage

    %% Extension relationships (dotted arrows)
    ViewLogs -.-> ExportLogs["Export System Logs"]
    ManageSubscriptions -.-> UpgradePlan["Upgrade Subscription Plan"]
    SecurityPolicies -.-> Configure2FA["Configure Two-Factor Auth"]

    %% Style the extension use cases
    classDef extensionStyle fill:#fff3e0,stroke:#f57c00,stroke-width:2px
    class ExportLogs,UpgradePlan,Configure2FA extensionStyle

    %% Add legend
    subgraph Legend["Legend"]
        L1["━━━ Association"]
        L2["- - - Include/Extend"]
        L3["👤 Actor"]
        L4["⭕ Use Case"]
    end

    %% Styling for legend elements
    style L1 fill:#f9fbff,stroke:#666,stroke-width:1px
    style L2 fill:#f9fbff,stroke:#666,stroke-width:1px,stroke-dasharray: 3 3
    style L3 fill:#e8f5e9,stroke:#388e3c,stroke-width:2px
    style L4 fill:#e3f2fd,stroke:#1976d2,stroke-width:2px
```

## Use Case Details

### 👥 User Management
- **Create User Account**: Add new users to the system with default roles
- **Update User Profile**: Modify user information and preferences
- **Deactivate User Account**: Disable user access while preserving data
- **Reset User Password**: Reset forgotten or compromised passwords
- **Assign User Roles**: Grant appropriate role-based permissions
- **Manage User Permissions**: Fine-tune access controls and permissions

### 🏢 Company Management
- **Configure Company Settings**: Set up company-specific configurations
- **Manage Company Profile**: Update company information and branding
- **Set Fiscal Year**: Define accounting periods and fiscal calendars
- **Configure Tax Settings**: Set up tax rates and compliance rules
- **Manage Currency Settings**: Configure multiple currency support

### ⚙️ System Configuration
- **Configure Email Settings**: Set up SMTP and notification templates
- **Manage Backup Settings**: Configure automated backup schedules
- **Configure Security Policies**: Define password rules and access controls
- **Manage System Notifications**: Set up system alerts and notifications
- **View System Logs**: Monitor system activity and error logs

### 🌐 Multi-tenancy
- **Create New Company**: Onboard new tenant organizations
- **Manage Company Subscriptions**: Handle billing and subscription plans
- **Monitor Resource Usage**: Track tenant resource consumption
- **Tenant Isolation Management**: Ensure data separation between tenants

### 🔗 Relationships
- **Include relationships** (mandatory): `ManagePermissions` includes `AssignRoles`, `CreateCompany` includes `CompanySettings`
- **Extend relationships** (optional): `ViewLogs` extends to `Export System Logs`, `SecurityPolicies` extends to `Configure Two-Factor Auth`