# Accountant - Detailed Use Cases

```mermaid
graph TD
    %% Define styles
    classDef accountantStyle fill:#e8f5e9,stroke:#388e3c,stroke-width:2px
    classDef usecaseStyle fill:#e8f5e9,stroke:#388e3c,stroke-width:2px
    classDef systemStyle fill:#f9fbff,stroke:#388e3c,stroke-width:2px,stroke-dasharray: 5 5

    %% Main actor
    Accountant["<div style='font-weight:bold; font-size:14px;'><center>👩‍💼<br/>Accountant</center></div>"]:::accountantStyle

    %% System boundary (subgraph)
    subgraph AccountingOps["<div style='font-weight:bold; font-size:16px; color:#388e3c;'>📋 Daily Accounting Operations</div>"]:::systemStyle

        %% Voucher Management Use Cases
        subgraph VoucherMgmt["<div style='font-weight:bold; font-size:14px;'>🧾 Voucher Management</div>"]
            CreateSalesVoucher["Create Sales Voucher"]:::usecaseStyle
            CreatePurchaseVoucher["Create Purchase Voucher"]:::usecaseStyle
            CreateJournal["Create Journal Entry"]:::usecaseStyle
            EditVoucher["Edit Draft Voucher"]:::usecaseStyle
            DeleteVoucher["Delete Draft Voucher"]:::usecaseStyle
            PostVoucher["Post Voucher"]:::usecaseStyle
            UnpostVoucher["Unpost Voucher"]:::usecaseStyle
            ReverseVoucher["Reverse Voucher"]:::usecaseStyle
        end

        %% Chart of Accounts Use Cases
        subgraph COA["<div style='font-weight:bold; font-size:14px;'>📊 Chart of Accounts</div>"]
            ViewCOA["View Chart of Accounts"]:::usecaseStyle
            SearchAccount["Search Account Codes"]:::usecaseStyle
            AccountDetails["View Account Details"]:::usecaseStyle
            AccountBalance["View Account Balance"]:::usecaseStyle
            AccountStatements["Generate Account Statements"]:::usecaseStyle
        end

        %% Purchase Bill Management Use Cases
        subgraph BillMgmt["<div style='font-weight:bold; font-size:14px;'>🧾 Purchase Bill Management</div>"]
            CreateBill["Create Purchase Bill"]:::usecaseStyle
            UploadAttachments["Upload Bill Attachments"]:::usecaseStyle
            ValidateBill["Validate Bill Details"]:::usecaseStyle
            SubmitBill["Submit Bill for Approval"]:::usecaseStyle
            TrackBill["Track Bill Status"]:::usecaseStyle
            RecordPayments["Record Bill Payments"]:::usecaseStyle
        end

        %% Payment Processing Use Cases
        subgraph PaymentProcessing["<div style='font-weight:bold; font-size:14px;'>💳 Payment Processing</div>"]
            CreatePayment["Create Payment Voucher"]:::usecaseStyle
            ProcessPayment["Process Supplier Payments"]:::usecaseStyle
            RecordReceipts["Record Customer Receipts"]:::usecaseStyle
            ReconcileBank["Reconcile Bank Transactions"]:::usecaseStyle
            PaymentMethods["Manage Payment Methods"]:::usecaseStyle
            PaymentReports["Generate Payment Reports"]:::usecaseStyle
        end

        %% Reporting and Analysis Use Cases
        subgraph ReportingAnalysis["<div style='font-weight:bold; font-size:14px;'>📈 Reporting & Analysis</div>"]
            TrialBalance["Generate Trial Balance"]:::usecaseStyle
            AccountLedger["View Account Ledger"]:::usecaseStyle
            TransactionReports["Generate Transaction Reports"]:::usecaseStyle
            BankReconciliation["Create Bank Reconciliation Statement"]:::usecaseStyle
            AgingReports["Generate Aging Reports"]:::usecaseStyle
        end

        %% Data Management Use Cases
        subgraph DataMgmt["<div style='font-weight:bold; font-size:14px;'>💾 Data Management</div>"]
            ImportTransactions["Import Transactions"]:::usecaseStyle
            ExportData["Export Transaction Data"]:::usecaseStyle
            BackupData["Backup Voucher Data"]:::usecaseStyle
            ManageTemplates["Manage Transaction Templates"]:::usecaseStyle
        end
    end

    %% Actor to Use Case connections
    Accountant --> CreateSalesVoucher
    Accountant --> CreatePurchaseVoucher
    Accountant --> CreateJournal
    Accountant --> EditVoucher
    Accountant --> DeleteVoucher
    Accountant --> PostVoucher
    Accountant --> UnpostVoucher
    Accountant --> ReverseVoucher

    Accountant --> ViewCOA
    Accountant --> SearchAccount
    Accountant --> AccountDetails
    Accountant --> AccountBalance
    Accountant --> AccountStatements

    Accountant --> CreateBill
    Accountant --> UploadAttachments
    Accountant --> ValidateBill
    Accountant --> SubmitBill
    Accountant --> TrackBill
    Accountant --> RecordPayments

    Accountant --> CreatePayment
    Accountant --> ProcessPayment
    Accountant --> RecordReceipts
    Accountant --> ReconcileBank
    Accountant --> PaymentMethods
    Accountant --> PaymentReports

    Accountant --> TrialBalance
    Accountant --> AccountLedger
    Accountant --> TransactionReports
    Accountant --> BankReconciliation
    Accountant --> AgingReports

    Accountant --> ImportTransactions
    Accountant --> ExportData
    Accountant --> BackupData
    Accountant --> ManageTemplates

    %% Include relationships (dashed arrows)
    PostVoucher -.-> ValidateBill
    PostVoucher -.-> ValidateDoubleEntry["Validate Double Entry"]
    CreateBill -.-> UploadAttachments
    ProcessPayment -.-> ValidateBill
    BankReconciliation -.-> ReconcileBank
    TrialBalance -.-> AccountLedger

    %% Extension relationships (dotted arrows)
    CreateSalesVoucher -.-> RecurringVoucher["Create Recurring Voucher"]
    CreatePurchaseVoucher -.-> BillFromPO["Create Bill from PO"]
    TransactionReports -.-> ScheduleReport["Schedule Report"]
    AccountStatements -.-> EmailStatement["Email Statement"]

    %% Style the extension use cases
    classDef extensionStyle fill:#fff3e0,stroke:#f57c00,stroke-width:2px
    class ValidateDoubleEntry,RecurringVoucher,BillFromPO,ScheduleReport,EmailStatement extensionStyle

    %% Add notes
    noteForAccountant["<div style='font-size:11px; font-style:italic;'>Accountant responsibilities:<br/>• Daily operations<br/>• Transaction processing<br/>• Data entry and validation<br/>• Basic reporting</div>"]
    noteForPostVoucher["<div style='font-size:11px; font-style:italic;'>Requires double-entry validation<br/>and period checking</div>"]
    noteForProcessPayment["<div style='font-size:11px; font-style:italic;'>Includes bank reconciliation<br/>and payment method validation</div>"]

    %% Position notes
    Accountant --- noteForAccountant
    PostVoucher --- noteForPostVoucher
    ProcessPayment --- noteForProcessPayment

    %% Legend
    classDef legendStyle fill:#f9fbff,stroke:#666,stroke-width:1px
    subgraph Legend["<div style='font-weight:bold; font-size:12px;'>Legend</div>"]:::legendStyle
        L1["━━━ Association"]
        L2["- - - Include/Extend"]
        L3["👩‍💼 Accountant"]
        L4["⭕ Use Case"]
    end

    style L1 fill:#f9fbff,stroke:#666,stroke-width:1px
    style L2 fill:#f9fbff,stroke:#666,stroke-width:1px,stroke-dasharray: 3 3
    style L3 fill:#e8f5e9,stroke:#388e3c,stroke-width:2px
    style L4 fill:#e8f5e9,stroke:#388e3c,stroke-width:2px
```

## Use Case Details

### 🧾 Voucher Management
- **Create Sales Voucher**: Record sales transactions and revenue
- **Create Purchase Voucher**: Document purchase transactions
- **Create Journal Entry**: Make manual journal adjustments
- **Edit Draft Voucher**: Modify unposted vouchers
- **Delete Draft Voucher**: Remove invalid draft vouchers
- **Post Voucher**: Finalize vouchers to the ledger
- **Unpost Voucher**: Reverse posted vouchers (with authorization)
- **Reverse Voucher**: Create reversal entries for corrections

### 📊 Chart of Accounts
- **View Chart of Accounts**: Browse the complete account structure
- **Search Account Codes**: Find specific accounts quickly
- **View Account Details**: Examine account information and balances
- **View Account Balance**: Check current account balances
- **Generate Account Statements**: Create account activity statements

### 🧾 Purchase Bill Management
- **Create Purchase Bill**: Enter supplier invoices into the system
- **Upload Bill Attachments**: Attach supporting documents
- **Validate Bill Details**: Verify bill accuracy and completeness
- **Submit Bill for Approval**: Send bills to approvers
- **Track Bill Status**: Monitor approval workflow progress
- **Record Bill Payments**: Record payment transactions against bills

### 💳 Payment Processing
- **Create Payment Voucher**: Document outgoing payments
- **Process Supplier Payments**: Execute payment transactions
- **Record Customer Receipts**: Record incoming customer payments
- **Reconcile Bank Transactions**: Match bank records to system transactions
- **Manage Payment Methods**: Configure payment options
- **Generate Payment Reports**: Create payment analysis reports

### 📈 Reporting & Analysis
- **Generate Trial Balance**: Create trial balance reports
- **View Account Ledger**: Examine detailed transaction history
- **Generate Transaction Reports**: Create various transaction analyses
- **Create Bank Reconciliation Statement**: Document reconciliation results
- **Generate Aging Reports**: Create receivable/payable aging analyses

### 💾 Data Management
- **Import Transactions**: Bulk import transaction data
- **Export Transaction Data**: Export data for analysis
- **Backup Voucher Data**: Create data backups
- **Manage Transaction Templates**: Create and manage voucher templates

### 🔗 Key Relationships
- **Include relationships**: `PostVoucher` includes `ValidateBill` and `Validate Double Entry`, `CreateBill` includes `UploadAttachments`
- **Extend relationships**: `CreateSalesVoucher` extends to `Create Recurring Voucher`, `TransactionReports` extends to `Schedule Report`

### ⚠️ Critical Control Points
- Double-entry validation is mandatory for all voucher posting
- Period validation prevents posting to closed periods
- Bank reconciliation requires proper authorization
- Bill attachments must be uploaded before submission

### 📊 Daily Workflow Integration
1. **Morning**: Process incoming bills and receipts
2. **Mid-day**: Create vouchers and validate entries
3. **Afternoon**: Process payments and reconcile bank transactions
4. **End of day**: Generate reports and backup data