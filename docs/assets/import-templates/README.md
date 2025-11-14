# Import Templates Overview

These sanctioned templates cover master data imports for the demo company. Each template ships in both `.xlsx` and `.csv` formats under this directory together with seeded demo rows that align with the initial tenant data.

## Customers

| Header          | Type    | Notes                                                   |
| --------------- | ------- | ------------------------------------------------------- |
| `customer_code` | string? | Optional; auto-generated (`CUST-YYYY-NNNN`) when blank. |
| `name`          | string  | Legal/trading name displayed in ledgers.                |
| `tax_code`      | string? | Vietnam tax number (10 digits).                         |
| `email`         | string? | Validated email format.                                 |
| `phone`         | string? | Include country code where possible.                    |
| `address`       | string? | Free-form address concatenating street/city/province.   |
| `active`        | boolean | Use `TRUE`/`FALSE`; defaults to `TRUE`.                 |

**Demo alignment:** `CUST-001` and `CUST-002` mirror demo customers referenced in AR balances.

## Suppliers

| Header          | Type    | Notes                                                  |
| --------------- | ------- | ------------------------------------------------------ |
| `supplier_code` | string? | Optional; auto-generated (`SUP-YYYY-NNNN`) when blank. |
| `name`          | string  | Legal registration name.                               |
| `tax_code`      | string? | Vietnam tax number (10 digits).                        |
| `email`         | string? | Validated format.                                      |
| `phone`         | string? | Include country code.                                  |
| `address`       | string? | Free-form mailing address.                             |
| `active`        | boolean | `TRUE`/`FALSE`; defaults to `TRUE`.                    |

**Demo alignment:** `SUP-001` and `SUP-002` feed into seeded AP opening balances and reference bank accounts below.

## Bank Accounts

| Header            | Type    | Notes                                            |
| ----------------- | ------- | ------------------------------------------------ |
| `account_code`    | string? | Optional explicit code; generated when blank.    |
| `account_number`  | string  | Stored as string to preserve leading zeros.      |
| `bank_name`       | string  | Full bank name (localized).                      |
| `branch`          | string? | Branch descriptor.                               |
| `account_type`    | enum    | `CASH` or `BANK`.                                |
| `opening_balance` | decimal | Non-negative; used as migration opening balance. |
| `active`          | boolean | `TRUE`/`FALSE`.                                  |

**Demo alignment:** `BANK-001` (Vietcombank) and `BANK-002` (ACB) populate default payment channels referenced by demo transactions.

## Opening Balances

| Header         | Type    | Notes                                                         |
| -------------- | ------- | ------------------------------------------------------------- |
| `journal_code` | string  | Identifies import batch (e.g., `OB-2025-01`).                 |
| `account_code` | string  | Chart of Accounts code (TT200 mapping).                       |
| `account_name` | string  | Descriptive account name.                                     |
| `currency`     | string  | ISO 4217 currency code; defaults to company currency (`VND`). |
| `debit`        | decimal | Debit amount; enforce Dr = Cr across each journal.            |
| `credit`       | decimal | Credit amount.                                                |
| `period_start` | date    | ISO date `YYYY-MM-DD`.                                        |
| `period_end`   | date    | ISO date `YYYY-MM-DD`.                                        |
| `note`         | string? | Optional narration.                                           |

**Demo alignment:** Balances tie to TT200 accounts `1311`, `3311`, `1121`, `4111` ensuring Dr = Cr for the demo tenant. Journal `OB-2025-01` lines reconcile to seeded AR/AP balances and bank cash positions.

## Localization

- Header strings are in English but align with Vietnamese-localized UI labels (see `i18n/accounting.json`).
- Boolean values should be supplied as uppercase `TRUE`/`FALSE` to simplify parsing across CSV and Excel formats.

## File Inventory

- `customers-template.csv` / `customers-template.xlsx`
- `suppliers-template.csv` / `suppliers-template.xlsx`
- `bank-accounts-template.csv` / `bank-accounts-template.xlsx`
- `opening-balances-template.csv` / `opening-balances-template.xlsx`
- `template-metadata.json` summarizing schema for validation bootstrap.
