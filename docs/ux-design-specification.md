## UX Design Specification (Frontend - shadcn/ui + Tailwind)

### Design System
- Components: shadcn/ui (Radix primitives) with Tailwind CSS utilities
- Icons: Lucide React
- Layout: `sidebar-06` pattern via `layouts/ProtectedLayout.tsx` with `SidebarProvider`, `AppSidebar`, `SidebarInset`, `SidebarTrigger`

### Navigation
- Sidebar primary nav rendered by `components/app/app-sidebar.tsx`
- Items without submenu navigate directly; items with submenu open `DropdownMenu`
- Breadcrumb in header with `Separator` and `SidebarTrigger` for collapse

### Authentication Screens
- `features/auth/pages/Login.tsx`: `LoginForm` with shadcn `Form`, `Input`, `Button`
- `features/auth/pages/ForgotPassword.tsx`: Shows success/error via `sonner` toast, back link with `ArrowLeft`
- `features/auth/pages/ResetPassword.tsx`: Uses `sonner` toast on success; redirects after delay

### Dashboard
- `features/dashboard/pages/Dashboard.tsx`: Header includes `SidebarTrigger`, `Breadcrumb`; content via `Card`, `CardHeader`, `CardContent`

### Accounting
- `features/accounting/pages/ChartOfAccounts.tsx`: Tree view; uses shadcn layout; account components in `components/account/*`
- `features/accounting/pages/Vouchers/VoucherList.tsx`: List/table (MUI/TanStack Table as needed), toasts for errors
- `features/accounting/pages/Vouchers/VoucherForm.tsx`: Form grid + `VoucherLineItemGrid`

### Feedback & Notifications
- Toasts: `sonner` (`<Toaster richColors />` in `App.tsx`)
- Error presentation: toast with details; optional modal for JSON details

### Accessibility
- Keyboard navigation for sidebar and forms
- WCAG AA color contrast via Tailwind tokens; focus-visible rings on interactive elements

### Internationalization
- User-facing text: Vietnamese

### File & Folder Structure (Feature-first)
See `docs/architecture.md` → Frontend Folder Structure.
