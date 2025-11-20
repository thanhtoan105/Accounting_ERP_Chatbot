# Epic to Architecture Mapping

| Epic                      | Backend Location                               | Frontend Location                                                 | Database Tables                                          | Key Technologies                        |
| ------------------------- | ---------------------------------------------- | ----------------------------------------------------------------- | -------------------------------------------------------- | --------------------------------------- |
| Epic 1: Foundation & Auth | `controller/auth/`, `security/`                | `features/auth/pages/Login.tsx`, `features/auth/services/auth.ts` | `users`, `roles`, `companies`                            | Spring Security 6, JWT, Shadcn UI       |
| Epic 2: Master Data       | `controller/customer/`, `controller/supplier/` | `pages/Customers.tsx`, `pages/Suppliers.tsx`                      | `customers`, `suppliers`, `chart_of_accounts`            | Spring Data JPA, TanStack Table         |
| Epic 3: Voucher Engine    | `controller/voucher/`, `service/gl/`           | `features/accounting/pages/Vouchers/VoucherForm.tsx`              | `vouchers`, `voucher_lines`, `journal_entries`           | Spring Data JPA, TanStack Table         |
| Epic 4: AP Module         | `controller/purchase/`                         | `pages/PurchaseBills.tsx`                                         | `purchase_bills`, `ap_payments`                          | Spring Data JPA, Maker-Checker          |
| Epic 5: AR Module         | `controller/sales/`                            | `pages/SalesInvoices.tsx`                                         | `sales_invoices`, `ar_receipts`                          | Spring Data JPA, Approval workflow      |
| Epic 6: Cash & Bank       | `controller/cash/`                             | `pages/CashBook.tsx`                                              | `cash_receipts`, `cash_payments`, `bank_reconciliations` | Spring Data JPA, Supabase Storage       |
| Epic 7: Reporting         | `controller/report/`, `service/report/`        | `pages/Reports.tsx`                                               | Query from `journal_entries`, `vouchers`                 | Spring Data JPA, Redis cache, PDF/Excel |
| Epic 8: BI Dashboard      | `controller/dashboard/`, `service/analytics/`  | `pages/Dashboard.tsx`                                             | Materialized views, Redis cache                          | Redis, React Query, Chart.js            |
| Epic 9: AI RAG            | `controller/chatbot/`, `service/rag/`          | `components/Chatbot.tsx`                                          | External: Pinecone                                       | Pinecone SDK, OpenAI embeddings         |
| Epic 9.0: RAG MVP         | `controller/chatbot/`, `service/rag/`, n8n webhook | `components/ChatbotMVP.tsx`, chatbot panel entry point             | `vouchers`, `voucher_lines`, Pinecone                    | Spring Boot webhook client, n8n, Pinecone SDK |
| Epic 10: Admin            | `controller/admin/`                            | `pages/Admin.tsx`                                                 | `audit_logs`, `system_settings`                          | Audit trail, file import/export         |

---
