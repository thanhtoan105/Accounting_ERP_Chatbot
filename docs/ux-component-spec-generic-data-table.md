# Generic Data Table (shadcn/ui) — Component Spec

Purpose: One reusable data table pattern for all list screens (Vouchers, Sales Invoices, Supplier Payments, Customers, Suppliers, COA, etc.). Optimized for server-driven data with consistent UX: search, refresh, record count, page size, pagination.

## 1) Core UX Requirements
- Search (debounced 300ms). Placeholder localized per context. Clears with one click.
- Refresh button. Re-fetches with current query, sort, filters, pagination. Shows spinner state.
- Record summary. “Hiển thị X–Y trên Z bản ghi” (VI). Always visible above table and near paginator.
- Page size selector. Options: 10, 20, 30, 50, 100. Persist last choice per route (localStorage).
- Pagination. Server-side ready (page, pageSize, total). First/Prev/Next/Last, numeric pages (condensed for large totals).
- Sorting. Single-column by header click; Shift+Click for multi-column (optional). Visual indicator ↑/↓.
- Column visibility. “Columns” menu to show/hide with checkboxes; persist per route.
- Filters. Slot for contextual filters (date range, status, account, etc.). Clear all resets to defaults.
- Row selection (optional). Checkbox column; “Select all on page”; bulk actions slot.
- Row actions. “...” menu per row; accessible via keyboard.
- Empty state. Friendly message + “Clear filters” + “Create New” CTA if applicable.
- Error state. Inline banner with retry; details in expandable area for debugging.
- Loading state. Skeleton table rows.
- i18n. Vietnamese default for labels, number/date formats.
- A11y. WCAG AA, focus-visible rings, aria-sort on headers, aria-live for result count updates.

## 2) Visual Anatomy (shadcn primitives)
- Toolbar: `Input` (search) + `Button` (refresh) + `DropdownMenu` (“Columns”) + optional filter controls slot.
- Table: `Table`, `TableHeader`, `TableRow`, `TableHead`, `TableBody`, `TableCell`.
- Footer: record summary + page size `Select` + `Pagination` (prev/next with icons).
- Feedback: `Skeleton`, `Alert`, `Badge` for statuses, `Toast` on errors from actions.

## 3) Props (Type-level contract)
```ts
type ColumnDef<T> = {
  id: string;
  header: string | React.ReactNode;
  accessor?: (row: T) => React.ReactNode | string | number;
  sortable?: boolean;
  align?: 'left' | 'right' | 'center';
  width?: number | string;
  visible?: boolean; // initial
};

type PageMeta = {
  page: number;       // 1-based
  pageSize: number;   // 10|20|30|50|100
  total: number;      // server total
};

type Sort = { id: string; desc: boolean };

export type GenericTableProps<T> = {
  columns: ColumnDef<T>[];
  rows: T[];
  page: PageMeta;
  sort?: Sort[];
  isLoading?: boolean;
  error?: { message: string; details?: string } | null;
  searchable?: boolean;            // default true
  initialQuery?: string;
  filtersSlot?: React.ReactNode;   // custom filters
  enableRowSelection?: boolean;
  rowActions?: (row: T) => React.ReactNode; // “...” menu
  onQueryChange?: (q: string) => void;
  onRefresh?: () => void;
  onPageChange?: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  onSortChange?: (sort: Sort[]) => void;
  onToggleColumn?: (id: string, visible: boolean) => void;
  onSelectRows?: (ids: string[]) => void;
};
```

Notes:
- Component is headless regarding data; caller owns fetching. All callbacks are pure (no side effects).
- Persist `pageSize`, `visibleColumns`, and `sort` per route key in `localStorage` (opt-in flag).

## 4) Behaviors
- Search: Debounce 300ms; pressing Enter triggers immediately. Clearing input resets page to 1.
- Refresh: Triggers `onRefresh`. While refreshing, button shows spinner and is disabled.
- Sorting: Header click toggles asc → desc → none. If `sortable=false`, no toggle.
- Column visibility: Checked items update `visible` state; render only visible columns.
- Pagination: Changing page size resets page to 1; maintain query/sort/filters.
- Row keyboard:
  - ArrowUp/Down moves highlight (roving tabindex). Enter opens row actions menu.
  - Space toggles selection when selection enabled.
- Accessibility:
  - Headers with sorting expose `aria-sort="ascending|descending|none"`.
  - Record summary updates `aria-live="polite"`.
  - Each row has role="row"; cells role="gridcell"; header role="columnheader".

## 5) Layout & Spacing
- Toolbar: 12px gap between controls; search grows on wide screens. Use container max-width = 1280px.
- Table: min-row-height 44px; header sticky optional (prop). Numeric columns right-aligned.
- Footer: left — record summary; right — page size + pagination. 12px gap.

## 6) States
- Loading: show 5–10 skeleton rows matching column count.
- Empty: “Không có dữ liệu phù hợp” + actions: Clear filters, Create New (if provided via slot).
- Error: `Alert` with message; “Retry” button calls `onRefresh`.

## 7) Performance
- Virtualization not required initially; revisit for >1000 rows/page.
- Avoid re-rendering all rows on query change by memoizing cells.
- Use `aria-busy` on table while loading.

## 8) Internationalization (VI defaults)
- Search placeholder: “Tìm kiếm...”
- Refresh: “Làm mới”
- Columns: “Cột hiển thị”
- Record summary: “Hiển thị {from}–{to} trên {total} bản ghi”
- Page size: “Kích thước trang”
- Empty state: “Không có dữ liệu phù hợp”

## 9) Example Wire (pseudo-JSX)
```tsx
<GenericTable
  columns={[
    { id: 'code', header: 'Số chứng từ', sortable: true },
    { id: 'date', header: 'Ngày', sortable: true, align: 'right' },
    { id: 'customer', header: 'Khách hàng' },
    { id: 'amount', header: 'Số tiền', sortable: true, align: 'right' },
    { id: 'status', header: 'Trạng thái' },
  ]}
  rows={data}
  page={{ page, pageSize, total }}
  sort={sort}
  onQueryChange={setQuery}
  onRefresh={refetch}
  onPageChange={setPage}
  onPageSizeChange={setPageSize}
  onSortChange={setSort}
  filtersSlot={<DateRangeFilter value={range} onChange={setRange} />}
  rowActions={(row) => <RowMenu row={row} />}
/>
```

## 10) Acceptance Checklist
- [ ] Search (debounced) updates dataset and resets to page 1
- [ ] Refresh preserves all state and shows spinner
- [ ] Record summary and totals accurate
- [ ] Page size selector works; options: 10/20/30/50/100
- [ ] Pagination works with server meta
- [ ] Sorting toggles and announces via `aria-sort`
- [ ] Columns menu persists visibility per route
- [ ] Row selection + bulk actions (when enabled)
- [ ] Empty/error/loading states implemented
- [ ] Keyboard access and focus rings verified

## 11) Implementation Notes
- Use project brand tokens from `frontend/src/index.css` (—primary, —background, etc.).
- Prefer `@/components/ui/table`, `pagination`, `select`, `input`, `button`, `dropdown-menu`, `checkbox`, `skeleton`, `alert`.
- Encapsulate state sync (query, page, pageSize, sort, visibleColumns) inside the table wrapper but expose controlled props for advanced screens.


