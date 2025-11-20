# Supplier Portal - Detailed Use Cases

```mermaid
graph TD
    %% Define styles
    classDef supplierStyle fill:#fff8e1,stroke:#f9a825,stroke-width:2px
    classDef usecaseStyle fill:#fff8e1,stroke:#f9a825,stroke-width:2px
    classDef systemStyle fill:#f9fbff,stroke:#f9a825,stroke-width:2px,stroke-dasharray: 5 5
    classDef externalStyle fill:#e8f5e9,stroke:#388e3c,stroke-width:1px,stroke-dasharray: 2 2

    %% Main actor
    Supplier["<div style='font-weight:bold; font-size:14px;'><center>🏢<br/>Supplier<br/>Portal</center></div>"]:::supplierStyle

    %% System boundary (subgraph)
    subgraph SupplierIntegration["<div style='font-weight:bold; font-size:16px; color:#f9a825;'>🔌 Supplier Integration Module</div>"]:::systemStyle

        %% Bill Submission Use Cases
        subgraph BillSubmission["<div style='font-weight:bold; font-size:14px;'>🧾 Bill Submission</div>"]
            SubmitBill["Submit New Bill"]:::usecaseStyle
            UploadDocuments["Upload Bill Documents"]:::usecaseStyle
            ValidateBillInfo["Validate Bill Information"]:::usecaseStyle
            TrackBillStatus["Track Bill Status"]:::usecaseStyle
            ResubmitBill["Resubmit Rejected Bill"]:::usecaseStyle
            WithdrawBill["Withdraw Submitted Bill"]:::usecaseStyle
        end

        %% Payment Information Use Cases
        subgraph PaymentInfo["<div style='font-weight:bold; font-size:14px;'>💳 Payment Information</div>"]
            ViewPaymentStatus["View Payment Status"]:::usecaseStyle
            CheckPaymentHistory["Check Payment History"]:::usecaseStyle
            DownloadRemittance["Download Payment Remittance"]:::usecaseStyle
            UpdatePaymentDetails["Update Payment Details"]:::usecaseStyle
            PaymentPreferences["Set Payment Preferences"]:::usecaseStyle
        end

        %% Account Management Use Cases
        subgraph AccountMgmt["<div style='font-weight:bold; font-size:14px;'>⚙️ Account Management</div>"]
            RegisterAccount["Register Supplier Account"]:::usecaseStyle
            UpdateProfile["Update Company Profile"]:::usecaseStyle
            ManageContacts["Manage Contact Information"]:::usecaseStyle
            UpdateBanking["Update Banking Details"]:::usecaseStyle
            ManageUserAccess["Manage User Access"]:::usecaseStyle
        end

        %% Communication Use Cases
        subgraph Communication["<div style='font-weight:bold; font-size:14px;'>💬 Communication</div>"]
            ReceiveNotifications["Receive Notifications"]:::usecaseStyle
            SendQueries["Send Query Messages"]:::usecaseStyle
            RespondToQueries["Respond to Queries"]:::usecaseStyle
            DownloadDocs["Download Supporting Documents"]:::usecaseStyle
            CommunicationHistory["Access Communication History"]:::usecaseStyle
        end

        %% Reporting Use Cases
        subgraph Reporting["<div style='font-weight:bold; font-size:14px;'>📊 Reporting</div>"]
            TransactionSummary["View Transaction Summary"]:::usecaseStyle
            GenerateAgingReports["Generate Aging Reports"]:::usecaseStyle
            InvoiceHistory["Download Invoice History"]:::usecaseStyle
            AccountStatements["View Account Statements"]:::usecaseStyle
            ExportFinancialData["Export Financial Data"]:::usecaseStyle
        end

        %% Integration Use Cases
        subgraph Integration["<div style='font-weight:bold; font-size:14px;'>🔗 Integration</div>"]
            APISubmission["API Bill Submission"]:::usecaseStyle
            AutomatedProcessing["Automated Invoice Processing"]:::usecaseStyle
            RealTimeUpdates["Real-time Status Updates"]:::usecaseStyle
            EDI["Electronic Data Interchange"]:::usecaseStyle
            SystemHealth["System Health Check"]:::usecaseStyle
        end
    end

    %% Actor to Use Case connections
    Supplier --> SubmitBill
    Supplier --> UploadDocuments
    Supplier --> ValidateBillInfo
    Supplier --> TrackBillStatus
    Supplier --> ResubmitBill
    Supplier --> WithdrawBill

    Supplier --> ViewPaymentStatus
    Supplier --> CheckPaymentHistory
    Supplier --> DownloadRemittance
    Supplier --> UpdatePaymentDetails
    Supplier --> PaymentPreferences

    Supplier --> RegisterAccount
    Supplier --> UpdateProfile
    Supplier --> ManageContacts
    Supplier --> UpdateBanking
    Supplier --> ManageUserAccess

    Supplier --> ReceiveNotifications
    Supplier --> SendQueries
    Supplier --> RespondToQueries
    Supplier --> DownloadDocs
    Supplier --> CommunicationHistory

    Supplier --> TransactionSummary
    Supplier --> GenerateAgingReports
    Supplier --> InvoiceHistory
    Supplier --> AccountStatements
    Supplier --> ExportFinancialData

    Supplier --> APISubmission
    Supplier --> AutomatedProcessing
    Supplier --> RealTimeUpdates
    Supplier --> EDI
    Supplier --> SystemHealth

    %% Include relationships (dashed arrows)
    SubmitBill -.-> UploadDocuments
    SubmitBill -.-> ValidateBillInfo
    TrackBillStatus -.-> ReceiveNotifications
    CheckPaymentHistory -.-> DownloadRemittance
    AccountStatements -.-> TransactionSummary

    %% Extension relationships (dotted arrows)
    SubmitBill -.-> RecurringBill["Submit Recurring Bill"]
    ValidateBillInfo -.-> ValidateTaxInfo["Validate Tax Information"]
    ReceiveNotifications -.-> NotificationPrefs["Set Notification Preferences"]
    GenerateAgingReports -.-> ScheduleReportGen["Schedule Report Generation"]
    ExportFinancialData -.-> ExportToAccounting["Export to Accounting System"]

    %% Style the extension use cases
    classDef extensionStyle fill:#e3f2fd,stroke:#1976d2,stroke-width:2px
    class RecurringBill,ValidateTaxInfo,NotificationPrefs,ScheduleReportGen,ExportToAccounting extensionStyle

    %% Add security note
    SecurityNote["<div style='background:#e8f5e9; border:1px solid #388e3c; padding:8px; border-radius:4px; font-size:11px;'><strong>🔒 Security:</strong> Limited to<br/>own company data only<br/>No access to other<br/>supplier information</div>"]:::externalStyle

    %% Add notes
    noteForSupplier["<div style='font-size:11px; font-style:italic;'>Supplier Portal:<br/>• External system integration<br/>• Limited access permissions<br/>• Self-service capabilities<br/>• Automated processing</div>"]
    noteForSubmitBill["<div style='font-size:11px; font-style:italic;'>Supports various bill formats<br/>with automatic validation<br/>and document attachment</div>"]
    noteForAPISubmission["<div style='font-size:11px; font-style:italic;'>For automated bill submission<br/>from supplier accounting systems<br/>with real-time processing</div>"]

    %% Position notes and warnings
    Supplier --- noteForSupplier
    SubmitBill --- noteForSubmitBill
    APISubmission --- noteForAPISubmission
    Supplier --- SecurityNote

    %% Legend
    classDef legendStyle fill:#f9fbff,stroke:#666,stroke-width:1px
    subgraph Legend["<div style='font-weight:bold; font-size:12px;'>Legend</div>"]:::legendStyle
        L1["━━━ Association"]
        L2["- - - Include/Extend"]
        L3["🏢 Supplier Portal"]
        L4["⭕ Use Case"]
        L5["🔒 External Access"]
    end

    style L1 fill:#f9fbff,stroke:#666,stroke-width:1px
    style L2 fill:#f9fbff,stroke:#666,stroke-width:1px,stroke-dasharray: 3 3
    style L3 fill:#fff8e1,stroke:#f9a825,stroke-width:2px
    style L4 fill:#fff8e1,stroke:#f9a825,stroke-width:2px
    style L5 fill:#e8f5e9,stroke:#388e3c,stroke-width:1px,stroke-dasharray: 2 2
```

## Use Case Details

### 🧾 Bill Submission
- **Submit New Bill**: Enter and submit new invoices to the accounting system
- **Upload Bill Documents**: Attach supporting documents (PDFs, images)
- **Validate Bill Information**: Verify bill data before submission
- **Track Bill Status**: Monitor approval and payment status
- **Resubmit Rejected Bill**: Correct and resubmit rejected invoices
- **Withdraw Submitted Bill**: Cancel submitted bills (if not processed)

### 💳 Payment Information
- **View Payment Status**: Check current payment status for bills
- **Check Payment History**: Review historical payment records
- **Download Payment Remittance**: Obtain payment advice and details
- **Update Payment Details**: Modify payment information
- **Set Payment Preferences**: Configure payment methods and timing

### ⚙️ Account Management
- **Register Supplier Account**: Create new supplier portal account
- **Update Company Profile**: Maintain company information
- **Manage Contact Information**: Update contact details and roles
- **Update Banking Details**: Modify bank account information
- **Manage User Access**: Control user permissions within supplier organization

### 💬 Communication
- **Receive Notifications**: Get system alerts and status updates
- **Send Query Messages**: Ask questions about bills or payments
- **Respond to Queries**: Reply to inquiries from accounting team
- **Download Supporting Documents**: Access required documentation
- **Access Communication History**: Review message history

### 📊 Reporting
- **View Transaction Summary**: Overview of transactions and status
- **Generate Aging Reports**: Create receivable aging analyses
- **Download Invoice History**: Export complete invoice records
- **View Account Statements**: Access account statements
- **Export Financial Data**: Download data for internal analysis

### 🔗 Integration
- **API Bill Submission**: Automated bill submission via API
- **Automated Invoice Processing**: System-driven invoice validation
- **Real-time Status Updates**: Live status synchronization
- **Electronic Data Interchange**: EDI transaction support
- **System Health Check**: Monitor system availability and performance

### 🔗 Key Relationships
- **Include relationships**: `SubmitBill` includes `UploadDocuments` and `ValidateBillInfo`, `TrackBillStatus` includes `ReceiveNotifications`
- **Extend relationships**: `SubmitBill` extends to `Submit Recurring Bill`, `ExportFinancialData` extends to `Export to Accounting System`

### 🔒 Security and Access Control
- **Data Isolation**: Suppliers can only access their own data
- **Limited Permissions**: No access to other supplier information
- **Secure Authentication**: Multi-factor authentication required
- **Audit Logging**: All supplier activities are logged

### 📊 Integration Capabilities
- **REST API**: For modern system integration
- **EDI Support**: Traditional electronic data interchange
- **File Upload**: Manual document submission
- **Real-time Updates**: Live status synchronization
- **Webhook Support**: Event-driven notifications

### 🚀 Automation Features
- **Automatic Validation**: Bill data validation on submission
- **Duplicate Detection**: Prevent duplicate invoice submission
- **Status Notifications**: Automated status updates via email/SMS
- **Document Processing**: OCR and document validation
- **Payment Reconciliation**: Automatic payment matching

### 📈 Supplier Self-Service Benefits
- **24/7 Access**: Submit bills anytime, anywhere
- **Real-time Status**: Track approval and payment progress
- **Document Management**: Maintain complete bill documentation
- **Communication Hub**: Centralized communication with accounting team
- **Data Analytics**: Access to payment and aging reports

### 🔧 Technical Integration Points
- **Accounting System Integration**: Seamless data flow
- **Bank Integration**: Payment status synchronization
- **Email System**: Automated notifications
- **File Storage**: Document management
- **Reporting Engine**: Real-time report generation