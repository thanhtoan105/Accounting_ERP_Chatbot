## DFD Level 1 — Main Processes and Data Stores

```mermaid
flowchart TB
  %% External entities
  user[User]
  bank[Bank]
  customer[Customer]
  supplier[Supplier]
  email[Email Service]

  %% Data stores
  md[((Master Data))]
  gl[((General Ledger))]
  docs[((Vouchers and Docs))]
  rpt[((Reports Snapshots))]
  audit[((Audit Log))]

  %% Processes
  P1(("Order-to-Cash (AR)"))
  P2(("Procure-to-Pay (AP)"))
  P3(("GL and Vouchers"))
  P4(("Cash and Treasury"))
  P5(("Reporting (TT200)"))
  P6(("Admin and Security"))
  P7(("BI Dashboard and Analytics"))
  P8(("AI Assistance"))

  %% Flows (generalized)
  user --> P1
  user --> P2
  user --> P3
  user --> P4
  user --> P5
  user --> P6
  user --> P7
  user --> P8

  P1 <-->|customers| md
  P2 <-->|suppliers| md
  P3 <-->|COA, company settings| md
  P4 <-->|bank and cash accounts| md

  P1 --> docs
  P2 --> docs
  P3 --> docs
  P4 --> docs

  P3 --> gl
  P4 --> gl

  P5 <-->|posted GL| gl
  P5 --> rpt
  P7 <-->|widgets| rpt

  P6 --> audit
  P1 --> audit
  P2 --> audit
  P3 --> audit
  P4 --> audit
  P5 --> audit
  P7 --> audit
  P8 --> audit

  %% External flows
  P4 <-->|statements| bank
  P1 -->|invoices and statements| customer
  P2 -->|supplier statements| supplier
  P6 -->|emails| email
```
