# Finance Manager - Detailed Use Cases

```mermaid
graph TD
    %% Define styles
    classDef managerStyle fill:#fff3e0,stroke:#f57c00,stroke-width:2px
    classDef usecaseStyle fill:#fff3e0,stroke:#f57c00,stroke-width:2px
    classDef systemStyle fill:#f9fbff,stroke:#f57c00,stroke-width:2px,stroke-dasharray: 5 5

    %% Main actor
    Manager["<div style='font-weight:bold; font-size:14px;'><center>👨‍💼<br/>Finance<br/>Manager</center></div>"]:::managerStyle

    %% System boundary (subgraph)
    subgraph FinancialMgmt["<div style='font-weight:bold; font-size:16px; color:#f57c00;'>💰 Financial Management Module</div>"]:::systemStyle

        %% Financial Reporting Use Cases
        subgraph FinancialReporting["<div style='font-weight:bold; font-size:14px;'>📊 Financial Reporting</div>"]
            BalanceSheet["Generate Balance Sheet"]:::usecaseStyle
            IncomeStatement["Generate Income Statement"]:::usecaseStyle
            CashFlow["Generate Cash Flow Statement"]:::usecaseStyle
            CustomReports["Create Custom Reports"]:::usecaseStyle
            ExportReports["Export Financial Reports"]:::usecaseStyle
            ScheduleReports["Schedule Report Generation"]:::usecaseStyle
        end

        %% Period Management Use Cases
        subgraph PeriodMgmt["<div style='font-weight:bold; font-size:14px;'>📅 Period Management</div>"]
            OpenPeriod["Open Accounting Period"]:::usecaseStyle
            ClosePeriod["Close Accounting Period"]:::usecaseStyle
            ReopenPeriod["Reopen Closed Period"]:::usecaseStyle
            PeriodLocking["Manage Period Locking"]:::usecaseStyle
            ReviewAdjustments["Review Period Adjustments"]:::usecaseStyle
        end

        %% Approval and Review Use Cases
        subgraph ApprovalReview["<div style='font-weight:bold; font-size:14px;'>✅ Approval & Review</div>"]
            ApproveLargeTx["Approve Large Transactions"]:::usecaseStyle
            ReviewVouchers["Review Voucher Batches"]:::usecaseStyle
            ApproveEntries["Approve Journal Entries"]:::usecaseStyle
            BudgetVariance["Review Budget Variance"]:::usecaseStyle
            AuthorizeClosing["Authorize Period Closing"]:::usecaseStyle
        end

        %% Budget Management Use Cases
        subgraph BudgetMgmt["<div style='font-weight:bold; font-size:14px;'>📈 Budget Management</div>"]
            CreateBudget["Create Annual Budget"]:::usecaseStyle
            MonitorBudget["Monitor Budget Performance"]:::usecaseStyle
            AdjustBudget["Adjust Budget Allocations"]:::usecaseStyle
            BudgetReports["Generate Budget Reports"]:::usecaseStyle
        end

        %% Compliance and Control Use Cases
        subgraph ComplianceControl["<div style='font-weight:bold; font-size:14px;'>🛡️ Compliance & Control</div>"]
            ReviewControls["Review Internal Controls"]:::usecaseStyle
            ComplianceMetrics["Monitor Compliance Metrics"]:::usecaseStyle
            ComplianceReports["Generate Compliance Reports"]:::usecaseStyle
            ReviewAuditTrails["Review Audit Trails"]:::usecaseStyle
        end

        %% Dashboard and Analytics Use Cases
        subgraph DashboardAnalytics["<div style='font-weight:bold; font-size:14px;'>📊 Dashboard & Analytics</div>"]
            Dashboard["View Financial Dashboard"]:::usecaseStyle
            RatioAnalysis["Analyze Financial Ratios"]:::usecaseStyle
            KPIs["Monitor Key Performance Indicators"]:::usecaseStyle
            Forecasts["Create Financial Forecasts"]:::usecaseStyle
        end
    end

    %% Actor to Use Case connections
    Manager --> BalanceSheet
    Manager --> IncomeStatement
    Manager --> CashFlow
    Manager --> CustomReports
    Manager --> ExportReports
    Manager --> ScheduleReports

    Manager --> OpenPeriod
    Manager --> ClosePeriod
    Manager --> ReopenPeriod
    Manager --> PeriodLocking
    Manager --> ReviewAdjustments

    Manager --> ApproveLargeTx
    Manager --> ReviewVouchers
    Manager --> ApproveEntries
    Manager --> BudgetVariance
    Manager --> AuthorizeClosing

    Manager --> CreateBudget
    Manager --> MonitorBudget
    Manager --> AdjustBudget
    Manager --> BudgetReports

    Manager --> ReviewControls
    Manager --> ComplianceMetrics
    Manager --> ComplianceReports
    Manager --> ReviewAuditTrails

    Manager --> Dashboard
    Manager --> RatioAnalysis
    Manager --> KPIs
    Manager --> Forecasts

    %% Include relationships (dashed arrows)
    ClosePeriod -.-> ReviewAdjustments
    ClosePeriod -.-> AuthorizeClosing
    ApproveLargeTx -.-> ReviewVouchers
    CustomReports -.-> ExportReports
    ComplianceReports -.-> ReviewAuditTrails

    %% Extension relationships (dotted arrows)
    ExportReports -.-> ExportExcel["Export to Excel"]
    ExportReports -.-> ExportPDF["Export to PDF"]
    Dashboard -.-> CustomDashboard["Create Custom Dashboard"]
    MonitorBudget -.-> BudgetAlerts["Set Budget Alerts"]

    %% Style the extension use cases
    classDef extensionStyle fill:#e8f5e9,stroke:#388e3c,stroke-width:2px
    class ExportExcel,ExportPDF,CustomDashboard,BudgetAlerts extensionStyle

    %% Add notes
    noteForManager["<div style='font-size:11px; font-style:italic;'>Finance Manager has:<br/>• Financial oversight<br/>• Period management<br/>• Approval authority<br/>• Strategic reporting</div>"]
    noteForClosePeriod["<div style='font-size:11px; font-style:italic;'>Requires validation of<br/>all period transactions<br/>and management approval</div>"]
    noteForApproveLargeTx["<div style='font-size:11px; font-style:italic;'>For transactions exceeding<br/>predefined threshold limits</div>"]

    %% Position notes
    Manager --- noteForManager
    ClosePeriod --- noteForClosePeriod
    ApproveLargeTx --- noteForApproveLargeTx

    %% Legend
    classDef legendStyle fill:#f9fbff,stroke:#666,stroke-width:1px
    subgraph Legend["<div style='font-weight:bold; font-size:12px;'>Legend</div>"]:::legendStyle
        L1["━━━ Association"]
        L2["- - - Include/Extend"]
        L3["👨‍💼 Manager"]
        L4["⭕ Use Case"]
    end

    style L1 fill:#f9fbff,stroke:#666,stroke-width:1px
    style L2 fill:#f9fbff,stroke:#666,stroke-width:1px,stroke-dasharray: 3 3
    style L3 fill:#fff3e0,stroke:#f57c00,stroke-width:2px
    style L4 fill:#fff3e0,stroke:#f57c00,stroke-width:2px
```

## Use Case Details

### 📊 Financial Reporting
- **Generate Balance Sheet**: Create comprehensive balance sheet reports
- **Generate Income Statement**: Produce profit and loss statements
- **Generate Cash Flow Statement**: Create cash flow analysis reports
- **Create Custom Reports**: Build tailored financial reports
- **Export Financial Reports**: Export reports in various formats
- **Schedule Report Generation**: Automate periodic report generation

### 📅 Period Management
- **Open Accounting Period**: Open new accounting periods for transactions
- **Close Accounting Period**: Close periods and prevent modifications
- **Reopen Closed Period**: Reopen periods for corrections (with authorization)
- **Manage Period Locking**: Control access to specific periods
- **Review Period Adjustments**: Examine and approve period-end adjustments

### ✅ Approval & Review
- **Approve Large Transactions**: Authorize transactions exceeding thresholds
- **Review Voucher Batches**: Examine and approve batches of vouchers
- **Approve Journal Entries**: Review and authorize journal entries
- **Review Budget Variance**: Analyze budget vs actual variances
- **Authorize Period Closing**: Approve period closing process

### 📈 Budget Management
- **Create Annual Budget**: Establish yearly budget allocations
- **Monitor Budget Performance**: Track budget vs actual spending
- **Adjust Budget Allocations**: Modify budget distributions as needed
- **Generate Budget Reports**: Create budget analysis and variance reports

### 🛡️ Compliance & Control
- **Review Internal Controls**: Verify internal control procedures
- **Monitor Compliance Metrics**: Track regulatory compliance indicators
- **Generate Compliance Reports**: Create compliance documentation
- **Review Audit Trails**: Examine transaction audit logs

### 📊 Dashboard & Analytics
- **View Financial Dashboard**: Access real-time financial metrics
- **Analyze Financial Ratios**: Calculate and analyze key financial ratios
- **Monitor Key Performance Indicators**: Track critical business metrics
- **Create Financial Forecasts**: Generate financial projections and forecasts

### 🔗 Key Relationships
- **Include relationships**: `ClosePeriod` includes `ReviewAdjustments` and `AuthorizeClosing`
- **Extend relationships**: `ExportReports` extends to `Export to Excel/PDF`, `MonitorBudget` extends to `Set Budget Alerts`

### ⚠️ Critical Control Points
- Large transaction approvals require dual verification
- Period closing needs management authorization
- Budget adjustments require proper justification
- Compliance reports must be generated regularly