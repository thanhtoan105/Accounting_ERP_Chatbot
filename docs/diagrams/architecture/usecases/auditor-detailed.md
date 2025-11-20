# Auditor - Detailed Use Cases

```mermaid
graph TD
    %% Define styles
    classDef auditorStyle fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    classDef usecaseStyle fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    classDef systemStyle fill:#f9fbff,stroke:#7b1fa2,stroke-width:2px,stroke-dasharray: 5 5
    classDef readonlyStyle fill:#ffebee,stroke:#d32f2f,stroke-width:1px,stroke-dasharray: 2 2

    %% Main actor
    Auditor["<div style='font-weight:bold; font-size:14px;'><center>🔍<br/>External<br/>Auditor</center></div>"]:::auditorStyle

    %% System boundary (subgraph)
    subgraph AuditCompliance["<div style='font-weight:bold; font-size:16px; color:#7b1fa2;'>🛡️ Audit and Compliance Module</div>"]:::systemStyle

        %% Audit Trail Review Use Cases
        subgraph AuditTrail["<div style='font-weight:bold; font-size:14px;'>📋 Audit Trail Review</div>"]
            ViewHistory["View Transaction History"]:::usecaseStyle
            TrackChanges["Track Transaction Changes"]:::usecaseStyle
            ReviewActivity["Review User Activity Logs"]:::usecaseStyle
            ExamineModifications["Examine Modified Records"]:::usecaseStyle
            VerifyIntegrity["Verify Transaction Integrity"]:::usecaseStyle
            AuditTimeline["Generate Audit Timeline"]:::usecaseStyle
        end

        %% Compliance Checking Use Cases
        subgraph ComplianceChecking["<div style='font-weight:bold; font-size:14px;'>✅ Compliance Checking</div>"]
            CheckStandards["Check Accounting Standards"]:::usecaseStyle
            VerifyTaxCompliance["Verify Tax Compliance"]:::usecaseStyle
            ReviewControls["Review Internal Controls"]:::usecaseStyle
            ValidateClosing["Validate Period Closing"]:::usecaseStyle
            CheckRegulatory["Check Regulatory Compliance"]:::usecaseStyle
        end

        %% Audit Reporting Use Cases
        subgraph AuditReporting["<div style='font-weight:bold; font-size:14px;'>📊 Audit Reporting</div>"]
            AuditFindings["Generate Audit Findings"]:::usecaseStyle
            ExceptionReports["Create Exception Reports"]:::usecaseStyle
            DocumentObservations["Document Audit Observations"]:::usecaseStyle
            GenerateComplianceReports["Generate Compliance Reports"]:::usecaseStyle
            AuditSummary["Create Audit Summary"]:::usecaseStyle
            ExportAuditPackage["Export Audit Package"]:::usecaseStyle
        end

        %% Investigation Use Cases
        subgraph Investigation["<div style='font-weight:bold; font-size:14px;'>🔎 Investigation</div>"]
            InvestigateAnomalies["Investigate Anomalies"]:::usecaseStyle
            TraceTransaction["Trace Transaction Flow"]:::usecaseStyle
            VerifyAttachments["Verify Document Attachments"]:::usecaseStyle
            CrossReference["Cross-Reference Data"]:::usecaseStyle
            ValidateDocuments["Validate Supporting Documents"]:::usecaseStyle
        end

        %% Read-Only Data Access Use Cases
        subgraph ReadOnlyAccess["<div style='font-weight:bold; font-size:14px;'>👁️ Read-Only Data Access</div>"]
            ViewStatements["View Financial Statements"]:::usecaseStyle
            ExamineBalances["Examine Account Balances"]:::usecaseStyle
            ReviewTrialBalance["Review Trial Balance"]:::usecaseStyle
            AccessHistorical["Access Historical Data"]:::usecaseStyle
            ViewCompanySettings["View Company Settings"]:::usecaseStyle
            CheckPermissions["Check User Permissions"]:::usecaseStyle
        end

        %% Audit Tools Use Cases
        subgraph AuditTools["<div style='font-weight:bold; font-size:14px;'>🔧 Audit Tools</div>"]
            AuditFilters["Use Audit Filters"]:::usecaseStyle
            DataSampling["Apply Data Sampling"]:::usecaseStyle
            StatisticalReports["Generate Statistical Reports"]:::usecaseStyle
            CreateWorkpapers["Create Audit Workpapers"]:::usecaseStyle
            ManageEvidence["Manage Audit Evidence"]:::usecaseStyle
        end
    end

    %% Actor to Use Case connections
    Auditor --> ViewHistory
    Auditor --> TrackChanges
    Auditor --> ReviewActivity
    Auditor --> ExamineModifications
    Auditor --> VerifyIntegrity
    Auditor --> AuditTimeline

    Auditor --> CheckStandards
    Auditor --> VerifyTaxCompliance
    Auditor --> ReviewControls
    Auditor --> ValidateClosing
    Auditor --> CheckRegulatory

    Auditor --> AuditFindings
    Auditor --> ExceptionReports
    Auditor --> DocumentObservations
    Auditor --> GenerateComplianceReports
    Auditor --> AuditSummary
    Auditor --> ExportAuditPackage

    Auditor --> InvestigateAnomalies
    Auditor --> TraceTransaction
    Auditor --> VerifyAttachments
    Auditor --> CrossReference
    Auditor --> ValidateDocuments

    Auditor --> ViewStatements
    Auditor --> ExamineBalances
    Auditor --> ReviewTrialBalance
    Auditor --> AccessHistorical
    Auditor --> ViewCompanySettings
    Auditor --> CheckPermissions

    Auditor --> AuditFilters
    Auditor --> DataSampling
    Auditor --> StatisticalReports
    Auditor --> CreateWorkpapers
    Auditor --> ManageEvidence

    %% Include relationships (dashed arrows)
    AuditFindings -.-> InvestigateAnomalies
    ExceptionReports -.-> ReviewActivity
    AuditSummary -.-> DocumentObservations
    ValidateClosing -.-> ExamineModifications
    CrossReference -.-> TraceTransaction

    %% Extension relationships (dotted arrows)
    ExportAuditPackage -.-> PDFReport["Generate PDF Audit Report"]
    StatisticalReports -.-> TrendAnalysis["Create Trend Analysis"]
    CreateWorkpapers -.-> AttachEvidence["Attach Evidence Files"]
    InvestigateAnomalies -.-> ReportSuspicious["Report Suspicious Activity"]

    %% Style the extension use cases
    classDef extensionStyle fill:#e3f2fd,stroke:#1976d2,stroke-width:2px
    class PDFReport,TrendAnalysis,AttachEvidence,ReportSuspicious extensionStyle

    %% Add security warning
    SecurityWarning["<div style='background:#ffebee; border:1px solid #d32f2f; padding:8px; border-radius:4px; font-size:11px;'><strong>🔒 Important:</strong> Auditor has<br/>read-only permissions<br/>No modification capabilities</div>"]:::readonlyStyle

    %% Add notes
    noteForAuditor["<div style='font-size:11px; font-style:italic;'>External Auditor:<br/>• Read-only access<br/>• Historical data review<br/>• Compliance verification<br/>• Audit documentation</div>"]
    noteForInvestigateAnomalies["<div style='font-size:11px; font-style:italic;'>Detailed investigation of<br/>unusual patterns or<br/>suspicious transactions</div>"]
    noteForExportAuditPackage["<div style='font-size:11px; font-style:italic;'>Complete audit documentation<br/>with evidence and findings</div>"]

    %% Position notes and warnings
    Auditor --- noteForAuditor
    InvestigateAnomalies --- noteForInvestigateAnomalies
    ExportAuditPackage --- noteForExportAuditPackage
    Auditor --- SecurityWarning

    %% Legend
    classDef legendStyle fill:#f9fbff,stroke:#666,stroke-width:1px
    subgraph Legend["<div style='font-weight:bold; font-size:12px;'>Legend</div>"]:::legendStyle
        L1["━━━ Association"]
        L2["- - - Include/Extend"]
        L3["🔍 Auditor"]
        L4["⭕ Use Case"]
        L5["🔒 Read-Only Access"]
    end

    style L1 fill:#f9fbff,stroke:#666,stroke-width:1px
    style L2 fill:#f9fbff,stroke:#666,stroke-width:1px,stroke-dasharray: 3 3
    style L3 fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    style L4 fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    style L5 fill:#ffebee,stroke:#d32f2f,stroke-width:1px,stroke-dasharray: 2 2
```

## Use Case Details

### 📋 Audit Trail Review
- **View Transaction History**: Access complete transaction logs
- **Track Transaction Changes**: Monitor all modifications to transaction data
- **Review User Activity Logs**: Examine user login and action history
- **Examine Modified Records**: Focus on changed or deleted records
- **Verify Transaction Integrity**: Validate data consistency and accuracy
- **Generate Audit Timeline**: Create chronological audit reports

### ✅ Compliance Checking
- **Check Accounting Standards**: Verify compliance with GAAP/IFRS
- **Verify Tax Compliance**: Ensure tax regulations are followed
- **Review Internal Controls**: Evaluate control effectiveness
- **Validate Period Closing**: Check period-end procedures
- **Check Regulatory Compliance**: Verify adherence to industry regulations

### 📊 Audit Reporting
- **Generate Audit Findings**: Document audit discoveries
- **Create Exception Reports**: Report non-compliance issues
- **Document Audit Observations**: Record audit notes and comments
- **Generate Compliance Reports**: Create compliance status reports
- **Create Audit Summary**: Produce executive summary reports
- **Export Audit Package**: Bundle all audit documentation

### 🔎 Investigation
- **Investigate Anomalies**: Examine unusual transaction patterns
- **Trace Transaction Flow**: Follow transactions through the system
- **Verify Document Attachments**: Check supporting documentation
- **Cross-Reference Data**: Validate data across different modules
- **Validate Supporting Documents**: Confirm authenticity of evidence

### 👁️ Read-Only Data Access
- **View Financial Statements**: Access published financial reports
- **Examine Account Balances**: Review account balances and details
- **Review Trial Balance**: Examine trial balance reports
- **Access Historical Data**: View historical transaction data
- **View Company Settings**: Understand system configuration
- **Check User Permissions**: Verify access control setup

### 🔧 Audit Tools
- **Use Audit Filters**: Apply filters to focus audit scope
- **Apply Data Sampling**: Use statistical sampling techniques
- **Generate Statistical Reports**: Create analytical reports
- **Create Audit Workpapers**: Document audit procedures
- **Manage Audit Evidence**: Organize and store evidence

### 🔗 Key Relationships
- **Include relationships**: `AuditFindings` includes `InvestigateAnomalies`, `ValidateClosing` includes `ExamineModifications`
- **Extend relationships**: `ExportAuditPackage` extends to `Generate PDF Audit Report`, `InvestigateAnomalies` extends to `Report Suspicious Activity`

### 🔒 Security and Access Control
- **Read-Only Access**: Auditor cannot modify any data
- **Historical Access**: Complete access to historical data
- **No System Configuration**: Cannot change system settings
- **Audit Logging**: All auditor activities are logged

### 📊 Audit Workflow Integration
1. **Planning Phase**: Review company settings and access historical data
2. **Field Work**: Use audit tools, investigate anomalies, verify compliance
3. **Reporting**: Generate findings, create reports, document observations
4. **Wrap-up**: Export audit package with all evidence and findings

### ⚠️ Critical Audit Areas
- Period closing procedures and controls
- Large or unusual transactions
- User access and permission changes
- System configuration modifications
- Compliance with regulatory requirements