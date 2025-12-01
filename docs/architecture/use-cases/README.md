# Use Case Diagrams - Vietnamese Accounting System

## Overview

This directory contains UML Use Case diagrams for the Vietnamese Accounting System (TT200-aligned ERP).

**Total**: 9 Excalidraw diagrams organized in 4 tiers.

## Diagram Navigation

### Tier 1: System Context
| Diagram | Description | Status |
|---------|-------------|--------|
| [System Overview](./00-overview/usecase-overview-system-context.excalidraw) | 30,000-foot view with 14 subsystems and 4 actors | ✅ Complete |

### Tier 2: Workflow Diagrams
| Diagram | Description | Status |
|---------|-------------|--------|
| Procure-to-Pay (P2P) | Purchase bill → Payment workflow | ⏳ Pending |
| Order-to-Cash (O2C) | Sales invoice → Receipt workflow | ⏳ Pending |
| Period Closing & Reporting | Month-end closing process | ⏳ Pending |

### Tier 3: Module Diagrams
| Diagram | Description | Status |
|---------|-------------|--------|
| General Ledger & Vouchers | Complete voucher lifecycle | ⏳ Pending |
| Master Data & Configuration | CRUD for COA, Customers, Suppliers | ⏳ Pending |
| AR & AP Operations | Comprehensive transaction operations | ⏳ Pending |
| Reporting, Analytics & Compliance | Financial reports, BI, VAT | ⏳ Pending |

### Tier 4: Administration
| Diagram | Description | Status |
|---------|-------------|--------|
| System Administration & Security | User management, audit, security | ⏳ Pending |

---

## Actors

| Actor | Level | Description |
|-------|-------|-------------|
| **ADMIN** | 4 | System administrator, full access |
| **CHIEF_ACCOUNTANT** | 3 | Senior accountant, approvals, period closing |
| **CFO** | 2 | Financial controller, reporting, read-mostly |
| **ACCOUNTANT** | 1 | Data entry, daily operations |

**Hierarchy**: Higher level inherits lower level permissions.

---

## Color Scheme

| Module | Color | Hex |
|--------|-------|-----|
| Authentication & Security | Blue | `#2563eb` |
| Master Data | Green | `#16a34a` |
| General Ledger | Purple | `#9333ea` |
| AR & AP | Orange | `#ea580c` |
| Cash & Bank | Teal | `#0d9488` |
| Period Management | Red | `#dc2626` |
| VAT & Compliance | Yellow | `#eab308` |
| Reporting & Analytics | Indigo | `#4f46e5` |
| System Administration | Gray | `#6b7280` |
| Audit | Brown | `#92400e` |
| File Management | Pink | `#ec4899` |
| Import/Export | Cyan | `#06b6d4` |
| AI Chatbot | Lime | `#84cc16` |

---

## How to View

1. Open `.excalidraw` files in [Excalidraw](https://excalidraw.com/)
2. Or use VS Code extension: **Excalidraw** by pomdtr

---

## Implementation Plan

Full implementation details: [vivid-finding-moler.md](../vivid-finding-moler.md)

---

## Changelog

- **2025-11-27**: Created System Overview diagram (Diagram 1)
