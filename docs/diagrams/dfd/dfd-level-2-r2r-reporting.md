## DFD Level 2 — Record-to-Report (GL, Period Close, Reports)

```mermaid
flowchart LR
  user[Chief Accountant/CFO]
  email[Email Service]

  mdCOA[((COA & Settings))]
  gl[((General Ledger))]
  docs[((Vouchers))]
  rpt[((Report Snapshots))]
  audit[((Audit Log))]

  P1(("Journal Validation & Posting"))
  P2(("Period Close / Open"))
  P3(("Generate Reports (S06, B01, B02, B03, F01)"))
  P4(("Drill-down to Vouchers"))
  P5(("Schedule and Distribute Exports"))

  user --> P1
  P1 <-->|draft vouchers| docs
  P1 --> gl
  P1 --> audit
  P1 --> mdCOA

  user --> P2
  P2 <-->|status & checks| gl
  P2 --> audit

  user --> P3
  P3 <-->|posted GL| gl
  P3 --> rpt
  P3 --> audit

  user --> P4
  P4 <-->|snapshot params| rpt
  P4 --> docs

  user --> P5
  P5 <-->|report files| rpt
  P5 --> email
  P5 --> audit
```
