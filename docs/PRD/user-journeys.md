# User Journeys

**Journey 1: Procure-to-Pay (AP Clerk & Chief Accountant)**

1.  **Persona:** AP Clerk (Maker) & Chief Accountant (Checker)
2.  **Goal:** Process a vendor bill and pay it accurately and on time, with proper controls.
3.  **Steps:**
    - An **AP Clerk** receives a bill from a new vendor. They log in, navigate to "Suppliers", and create a new supplier record.
    - The Clerk navigates to "Purchase Bills" and creates a new bill, entering the vendor, invoice number, and date. For each line item (e.g., raw materials), they select an expense account. **The system validates the account against TT200 and checks for mandatory dimensions (e.g., requires `cost_center_id` for account 621).**
    - The Clerk selects the applicable **VAT rate (e.g., 10%)**, as required by FR18, for record-keeping.
    - The bill's total exceeds the approval threshold. Upon submission, it enters a "Pending Approval" state.
    - The **Chief Accountant** receives an **in-app notification (via the bell icon)**. They log in, review the bill details, and click "Approve". The bill is now "Posted" and reflected in the AP Aging report.
    - On the due date, the **AP Clerk** creates a new payment voucher. **The voucher is explicitly linked to the approved bill (via `bill_id`) for traceability.**
    - The system automatically generates the correct GL entry (Dr. AP, Cr. Bank), and the bill's status changes to "Paid".
    - The Clerk views the AP Aging report and confirms the bill is no longer outstanding.

**Journey 2: Order-to-Cash (AR Clerk & CFO)**

1.  **Persona:** AR Clerk (Maker) & CFO (Viewer)
2.  **Goal:** Invoice an existing customer, ensure timely payment, and provide visibility to management.
3.  **Steps:**
    - An **AR Clerk** logs in and navigates to "Sales Invoices" to create an invoice for an **existing customer**.
    - They select the customer, add line items, and set the due date. The invoice total is below the threshold for mandatory approval.
    - Upon submission, **the invoice is auto-posted** as per the rules defined in FR26. The system generates the correct GL entry (Dr. AR, Cr. Revenue).
    - **The system automatically schedules a reminder.** Three days before the due date, it sends an alert to the AR Clerk's notification center about the upcoming payment.
    - The **CFO** logs in to the BI Dashboard, views the AR Aging report, and sees the outstanding invoice.
    - After the customer pays, the **AR Clerk** creates a "Cash Receipt", applying the payment to the invoice. The status updates to "Paid", and the GL is updated.
    - The **CFO** later refreshes the dashboard and sees the updated AR and cash balances.

**Journey 3: Financial Closing & Reporting (Chief Accountant)**

1.  **Persona:** Chief Accountant
2.  **Goal:** Close the monthly accounting period securely and generate accurate financial statements.
3.  **Steps:**
    - On the first day of the new month, the **Chief Accountant** logs in to close the previous month.
    - They run the "Trial Balance" to ensure all accounts are in balance.
    - They navigate to "Accounting Periods" and initiate the "Close Period" action. The system runs its pre-flight checks.
    - After confirmation, the period is locked. The Chief Accountant then navigates to the **Audit Trail module to verify that the period close event was logged correctly with their user ID and a timestamp**, satisfying FR5.
    - They proceed to the "Reports" module. They generate the "Income Statement", which **uses the system's automatic mapping from GL accounts to the official B02-DN line items as required by TT200**.
    - They export the report to PDF.
    - A manager asks about a specific expense. The Chief Accountant uses the **RAG Chatbot**: "What transactions make up the 'Software Subscription' expense for last month?"
    - The chatbot replies with a list of vouchers, **displaying a confidence score of 95% and providing a source citation for each item (e.g., "Voucher #VC2025-123, posted by accountant@demo.com")**, fulfilling FR41.

---
