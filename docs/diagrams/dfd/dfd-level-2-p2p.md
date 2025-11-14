## DFD Level 2 — Procure-to-Pay (AP)

```mermaid
flowchart LR
  user[AP Clerk/Chief]
  supplier[Supplier]
  bank[Bank]
  email[Email Service]

  mdSupp[((Suppliers))]
  mdCOA[((COA))]
  bills[((Purchase Bills))]
  payments[((Payments))]
  gl[((General Ledger))]
  audit[((Audit Log))]
  rpt[((Reports))]

  P1(("Create/Approve Bill"))
  P2(("Post Bill to GL"))
  P3(("Supplier Statement and Notice"))
  P4(("Record Payment and Allocate"))
  P5(("AP Aging and Statement"))

  user --> P1
  P1 -->|validate (supplier, COA)| mdSupp
  P1 --> mdCOA
  P1 --> bills
  P1 --> audit

  P2 <-->|ready-to-post| bills
  P2 --> gl
  P2 --> audit

  P3 <-->|bill/aging| bills
  P3 --> supplier
  P3 --> email
  P3 --> audit

  user --> P4
  P4 <-->|bank statement| bank
  P4 --> payments
  P4 --> gl
  P4 --> audit

  P5 <-->|posted GL| gl
  P5 --> rpt
  P5 --> audit
```
