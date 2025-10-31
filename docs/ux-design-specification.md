# accounting UX Design Specification

_Created on 2025-10-30 by thanhtoan_
_Generated using BMad Method - Create UX Design Workflow v1.0_

---

## Executive Summary

This UX Design Specification defines the user experience for **accounting**, a desktop-first accounting ERP for Vietnam that enforces TT200 compliance automatically while keeping users in complete control. The design emphasizes **empowerment and calm focus** through KPI-first dashboards, inline workflows, and smart defaults that reduce stress around audit-readiness.

**Key Design Decisions:**
- **Design System:** MUI + MUI X Data Grid for accessibility and dense table support
- **Visual Foundation:** Trust Blue theme (professional, compliance-first) with clear semantic color coding
- **Design Direction:** Hybrid approach combining dense dashboard layouts with inline workflows (minimal modals)
- **Core Experience:** Fast voucher entry with automatic TT200 validation, confident period closing, and 100% compliant financial reporting
- **Accessibility:** WCAG 2.1 Level AA compliance with full keyboard navigation and screen reader support

---

## 1. Design System Foundation

### 1.1 Design System Choice

System: Material UI (MUI) + MUI X Data Grid
Version: Latest stable (to be pinned at implementation)
Rationale: Accessibility (WCAG), Vietnamese i18n, robust data grid for dense accounting tables, strong theming for brand alignment, large ecosystem.
Provides: Buttons, inputs, dialogs, menus, tables/data grid, form validation helpers, icons, responsive system, theming/tokens.
Customization needs: Finance-styled data density presets, TT200 report tables, compliance status badges, KPI summary cards.

---

## 2. Core User Experience

### 2.1 Defining Experience

Defining statement: A desktop accounting system that enforces TT200 compliance automatically while keeping you in complete control—so you're never stressed about audit-readiness again.

Primary action: Recording vouchers and closing month-end (most repetitive daily task).

Completely effortless: Document entry with automatic TT200 compliance checks; immediate guidance so users don’t need to memorize regulations.

Most critical action: Generating compliant financial reports; accuracy must be 100% due to legal consequences.

### 2.2 Novel UX Patterns

{{novel_ux_patterns}}

---

## 3. Visual Foundation

### 3.1 Color System

Interactive color theme exploration created.

Chosen Theme: Trust Blue (Professional, Compliance‑first)

Semantic color mapping
- Primary: #2B6CB0 (main actions, highlights)
- Secondary: #38B2AC (supporting actions)
- Accent: #805AD5 (charts, emphasis)
- Success: #22C55E (compliance OK)
- Warning: #F59E0B (needs review)
- Error: #EF4444 (exception)
- Neutral grayscale: dark surfaces with #0B1220/#0F172A backgrounds; text #E6EEFC; borders rgba(255,255,255,0.1)

Rationale: Trust‑oriented blue palette aligns with enterprise finance, clear compliance statuses, and low‑stress readability.

**Interactive Visualizations:**

- Color Theme Explorer: [ux-color-themes.html](./ux-color-themes.html)

---

## 4. Design Direction

### 4.1 Chosen Design Approach

Chosen Hybrid Approach: #1 Dense Dashboard + #5 Inline Workflow

Rationale:
- Dashboard (#1) provides KPI-first situational awareness in <3 seconds
- Voucher workflow (#5) minimizes modal interruptions and keeps users in flow
- Combination supports both power users (dense tables) and newcomers (inline guidance)
- Desktop-first (1920px+) allows all information visible without excessive scrolling


**Interactive Mockups:**

- Design Direction Showcase: [ux-design-directions.html](./ux-design-directions.html)

---

## 5. User Journey Flows

### 5.1 Critical User Paths

Selected for detailed design (phase 1):

1) Dashboard (Entry)
- Goal: Instant situational awareness and navigation to work
- Primary KPI row: Month‑End Close %, Open Exceptions, AP Paid %, AR Overdue %
- Primary actions: New Voucher, Import Excel, Go to Reports

2) Voucher Creation → Posting (GL)
- Goal: Create, validate TT200, and post with confidence
- Primary actions: Add lines (keyboard‑first), inline validation/fixes, save draft, post with confirmation

3) Period Close → Reports (B01/B02/B03)
- Goal: Close month securely and generate compliant statements
- Primary actions: Pre‑flight checks, confirm close, generate B01/B02/B03, drill‑down

---

## 6. Component Library

### 6.1 Component Strategy

From Design System (MUI/MUI X)
- Buttons, IconButtons, TextField, Select, Dialog, Drawer, Menu, Tabs, Stepper
- Snackbar/Alert (toasts), Tooltip, Breadcrumbs, Pagination
- Data Grid (MUI X) with keyboard nav, column pinning, filtering, export

Custom Components Needed
- KPI Summary Card (compact KPI with trend/Delta, compliance tint)
- Compliance Status Badge (success/warning/error with tooltips and links)
- Period Close Preflight Panel (checks, blockers, quick links to resolve)
- Voucher Line Enhancements for Data Grid:
  - Inline validation chips and TT200 hints per account/line
  - Dr/Cr exclusive toggle, auto-balance indicator, row error states
- Account Selection Combobox (scales to 1000+ TT accounts)
  - Fast typeahead with unaccented search (e.g., "nha" → "nhà")
  - Group by account class (1xx/2xx/3xx), show code + name + postable flag
  - Keyboard-first; async paging; recent selections
- Audit Trail Sidebar
  - Slide-in drawer attached to right; shows event timeline for current entity
  - Filters (type/user/date), diff view (before/after), copyable IDs
- Numeric Input Formatter (for money amounts)
  - Auto-format as user types: 1000000 → 1,000,000 VND
  - Accepts numeric input; formats with thousand separators (Vietnamese locale)
  - Shows currency suffix (VND) after formatted number
  - Validates positive-only amounts; preserves decimals for partial amounts
  - Keyboard-friendly (backspace, delete work naturally on formatted display)
- Draft Auto-save Indicator
  - Displays "Draft saved at 10:32 AM" (or equivalent Vietnamese: "Đã lưu bản nháp lúc 10:32 AM")
  - Subtle status in form header or footer; auto-hides after 3 seconds, shows on manual save
  - Visual state: success icon + timestamp; non-intrusive toast-style appearance
  - Updates in real-time after successful auto-save; shows last save time if user returns to draft

Customization Notes
- Dark theme defaults per Trust Blue palette; density tokens for tables/forms
- Accessible focus outlines; ARIA labels for badges and alerts

---

## 7. UX Pattern Decisions

### 7.1 Consistency Rules

**Button hierarchy**
- Primary: Trust Blue (#2B6CB0), high contrast; main actions (Post, Save, Close Period, Generate Report)
- Secondary: Teal (#38B2AC); supporting actions (Cancel, Edit Draft, Export)
- Tertiary: Ghost/outline; low priority (Reset, Clear Filters)
- Destructive: Red (#EF4444) with confirmation; Delete, Unpost (if allowed)

**Feedback patterns**
- Success: Non-blocking Snackbar (bottom-right, auto-dismiss 4s); e.g., "Voucher VC2025-123 posted successfully_id"
- Error: Prominent Alert banner (top of content area) with field anchors; inline validation chips in forms; non-dismissible until fixed
- Warning: Inline Alert (not blocking); e.g., "3 drafts remain before period close"
- Info: Subtle Tooltip on hover; Toast for actionable info (e.g., "New notification: Bill requires approval")
- Loading: Skeleton for table/data; Circular Progress for actions; Linear Progress for multi-step (period close)

**Form patterns**
- Label position: Above fields (clear hierarchy)
- Required field indicator: Asterisk (*) + "Bắt buộc" tooltip
- Validation timing: onChange (inline), onSubmit (summary)
- Error display: Inline below field + chip badge if TT200 violation; summary Alert if form-level errors
- Help text: Tooltip icon next to label; expandable "TT200 Reference" link

**Modal patterns**
Size variants: Small (confirmations, ~400px), Medium (forms, ~600px), Large (reports preview, ~900px), Full-screen (period close wizard)
- Dismiss: Click outside (if safe), Escape (always), explicit Close (always)
- Focus management: Auto-focus first input; trap focus; restore on close
- Stacking: Only one modal at a time; Drawer (Audit Trail) can overlay

**Navigation patterns**
- Active state: Left sidebar — bold text + left border accent (Trust Blue); Top nav — underline
- Breadcrumbs: Show for reports and nested views (Dashboard > Reports > Balance Sheet B01-DN)
- Back button: Browser back (preserve state); explicit "Back" in wizards
- Deep linking: Supported for vouchers, reports, periods (shareable URLs)

**Empty state patterns**
- First use: Welcome card with sample data link + "Create your first voucher" CTA
- No results: "Không tìm thấy" + clear filters button + create action
- Cleared content: Show undo option for 5s if reversible

**Confirmation patterns**
- Delete: Always confirm with impact summary ("This will delete voucher VC2025-123 and its audit trail")
- Leave unsaved: Warn with Autosave hint; option to "Save draft and leave"
 broad Irreversible actions: Post, Close Period → mandatory confirmation with impact summary and "I understand" checkbox

**Notification patterns**
- Placement: Bell icon (top-right) with badge count; dropdown list (max 10, "View all" link)
- Duration: Auto-dismiss toasts (4s); persistent in bell until read
- Stacking: Max 3 toasts visible; others queue
- Priority: Critical (red badge, sound), Important (orange), Info (blue)

**Search patterns**
- Trigger: Global search bar (always visible, top-center); inline search in tables (filter icon)
- Results display: Instant for master data (accounts, customers); "Enter" for vouchers/reports
- Filters: Sidebar drawer (tablet) or inline row (desktop); save presets
- No results: Suggest similar terms, clear filters, create new

**Date/time patterns**
- Format: Vietnamese locale (dd/MM/yyyy); relative for recent ("Hôm nay", "Hôm qua")
- Timezone: User local (Vietnam, UTC+7); show timezone hint in admin settings
- Pickers: Calendar popup (MUI DatePicker); keyboard entry supported

**Keyboard shortcuts (Desktop power users)**
- **Ctrl+N:** New Voucher (opens voucher creation form)
- **Ctrl+S:** Save Draft (saves current form/voucher; shows auto-save indicator)
- **Ctrl+P:** Post Voucher (with confirmation dialog for irreversible actions)
- **Escape:** Cancel / Close Modal (closes current dialog/modal; cancels inline edit)
- **Tab:** Navigate between fields (standard form navigation)
- **Arrow Up/Down:** Move between rows in Data Grid (when grid is focused)
- **F2:** Inline edit row (enters edit mode for selected row in grid)
- **Ctrl+F:** Find / Filter (opens global search or focuses filter in current view)
- **Ctrl+K:** Global search (quick access to search bar from anywhere)
- **Enter:** Submit form / Confirm action (when in confirmation dialog)
- **Shift+Tab:** Navigate backward between fields

**Shortcut discoverability:**
- Show keyboard shortcut hints in tooltips on hover for buttons (e.g., "Save Draft (Ctrl+S)")
- Help menu item "Keyboard Shortcuts" lists all available shortcuts
- Shortcuts work globally when not in input fields; disabled in text inputs to prevent conflicts

---

## 8. Responsive Design & Accessibility

### 8.1 Responsive Strategy

Breakpoints
- Mobile: < 768px (single column; bottom nav or hamburger; simplified tables)
- Tablet: 768px - 1199px (2-column layout where applicable; sidebar collapses to icon; filters in drawer)
- Desktop: ≥ 1200px (full dense layout; persistent sidebar; all features visible)

Adaptation patterns
- Navigation: Desktop = persistent left sidebar; Tablet = collapsible sidebar; Mobile = bottom tabs or drawer
- Sidebar: Desktop = always visible; Tablet = auto-collapse when narrow; Mobile = drawer overlay
- Cards/Lists: Placement Desktop = grid (2-4 columns); Tablet = 2 columns; Mobile = single column stack
- Tables: Desktop = full table with horizontal scroll if needed; Tablet = card view or horizontal scroll; Mobile = card list with key fields
- Modals: Desktop = centered dialog; Tablet/Mobile = full-screen overlay
- Forms: Desktop = multi-column where appropriate; Tablet/Mobile = single column stack
- KPI cards: Desktop = 4 columns; Tablet = 2 columns; Mobile = single column stack

Touch targets
- Minimum 44x44px for all interactive elements on mobile/tablet
- Spacing between touch targets ≥ 8px

### 8.2 Accessibility Strategy

WCAG compliance target: Level AA (recommended for public/enterprise sites)

Key requirements
- Color contrast: Text vs background ≥ 4.5:1 (normal), ≥ 3:1 (large); UI components ≥ 3:1
- Keyboard navigation: All interactive elements accessible via Tab/Shift+Tab; Enter/Space to activate; Arrow keys in grids
- Focus indicators: Visible outline (2px solid Trust Blue) on all focusable elements; no keyboard traps
- ARIA labels: Meaningful labels for screen readers (e.g., "Post voucher VC2025-123"; "Month-end close progress: 92%")
- Alt text: Descriptive alt text for all meaningful images/icons; decorative images marked aria-hidden="true"
- Form labels: Proper `<label>` associations; required fields announced; error messages linked via aria-describedby
- Error identification: Clear, descriptive error messages; announce errors to screen readers immediately
- Touch target size: Minimum 44x44px on mobile/tablet (meets accessibility guidelines)

Testing strategy
- Automated: Lighthouse (Chrome DevTools), axe DevTools, WAVE LU Extension
- Manual: Keyboard-only navigation testing (Tab, Enter, Escape, Arrow keys)
- Screen reader: NVDA (Windows) / VoiceOver (Mac) testing for critical flows (voucher entry, period close)
- Color blindness: Test with color blindness simulators; ensure compliance status not color-only (icons + text)

---

## 9. Implementation Guidance

### 9.1 Completion Summary

**What we created together:**

- **Design System:** MUI + MUI X Data Grid with 8 custom components (KPI cards, compliance badges, account combobox, audit trail sidebar, period close panel, voucher line enhancements, numeric input formatter, draft auto-save indicator)
- **Visual Foundation:** Trust Blue color theme with semantic mapping (primary #2B6CB0, success/warning/error tints) and Vietnamese typography defaults
- **Design Direction:** Hybrid #1 Dense Dashboard + #5 Inline Workflow — KPI-first awareness with minimal modal interruptions
- **User Journeys:** 3 critical flows designed with clear navigation paths (Dashboard, Voucher Creation → Posting, Period Close → Reports)
- **UX Patterns:** 10 consistency rule categories established (buttons, feedback, forms, modals, navigation, empty states, confirmations, notifications, search, date/time)
- **Responsive Strategy:** 3 breakpoints (mobile < 768px, tablet 768-1199px, desktop ≥ 1200px) with adaptation patterns for all device sizes
- **Accessibility:** WCAG 2.1 Level AA compliance requirements defined with automated and manual testing strategy

**Your Deliverables:**
- UX Design Document: `docs/ux-design-specification.md`
- Interactive Color Themes: `docs/ux-color-themes.html`
- Design Direction Mockups: `docs/ux-design-directions.html`

**What happens next:**
- Designers can create high-fidelity mockups from this foundation
- Developers can implement with clear UX guidance and rationale
- All design decisions are documented with reasoning for future reference

You've made thoughtful choices through visual collaboration that will create a great user experience. Ready for design refinement and implementation!

---

## Appendix

### Related Documents

- Product Requirements: `/home/duong/code/accounting/docs/PRD.md`
- Product Brief: `/home/duong/code/accounting/docs/product-brief-accounting-2025-10-30.md`
- Brainstorming: `/home/duong/code/accounting/docs/brainstorming.md`

### Core Interactive Deliverables

This UX Design Specification was created through visual collaboration:

- **Color Theme Visualizer**: /home/duong/code/accounting/docs/ux-color-themes.html
  - Interactive HTML showing all color theme options explored
  - Live UI component examples in each theme
  - Side-by-side comparison and semantic color usage

- **Design Direction Mockups**: /home/duong/code/accounting/docs/ux-design-directions.html
  - Interactive HTML with 6-8 complete design approaches
  - Full-screen mockups of key screens
  - Design philosophy and rationale for each direction

### Optional Enhancement Deliverables

_This section will be populated if additional UX artifacts are generated through follow-up workflows._

<!-- Additional deliverables added here by other workflows -->

### Next Steps & Follow-Up Workflows

This UX Design Specification can serve as input to:

- **Wireframe Generation Workflow** - Create detailed wireframes from user flows
- **Figma Design Workflow** - Generate Figma files via MCP integration
- **Interactive Prototype Workflow** - Build clickable HTML prototypes
- **Component Showcase Workflow** - Create interactive component library
- **AI Frontend Prompt Workflow** - Generate prompts for v0, Lovable, Bolt, etc.
- **Solution Architecture Workflow** - Define technical architecture with UX context

### Version History

| Date     | Version | Changes                         | Author        |
| -------- | ------- | ------------------------------- | ------------- |
| 2025-10-30 | 1.0     | Initial UX Design Specification | thanhtoan |

---

_This UX Design Specification was created through collaborative design facilitation, not template generation. All decisions were made with user input and are documented with rationale._

---

## Checkpoint: project_and_users_confirmed

Project: Accounting ERP for Vietnam (TT200-compliant)
Target Users: Chief Accountants/CFOs (primary), Accounting students/juniors (secondary)

## Checkpoint: core_experience_and_platform

Core Experience
- Primary action: Recording vouchers and closing month-end
- Effortless: Auto TT200-compliant document entry with immediate guidance
- Critical: 100% accurate statutory financial reports

Platform
- Desktop-first web app (1920px+) for high information density
- Responsive tablet support for viewing outside the office
- Mobile app not needed at this stage

## Checkpoint: desired_emotional_response

- Empowered and in control — users make critical financial decisions and need confidence
- Calm and focused — reduced cognitive load and stress for compliance-critical work

## Checkpoint: inspiration_analysis

Apps referenced
- MISA AMIS — accounting suite with rich dashboards and statutory workflows ([link](https://amis.misa.vn/amis-ke-toan/))
- QuickBooks — SMB accounting with strong usability and reporting ([link](https://quickbooks.intuit.com/))

Observed UX aspects to learn
- KPI-first hierarchy: top cards highlight the one metric that matters (e.g., "Month‑End Close Status: 92% Complete"), with drill-down charts below.
- Color coding for compliance: green/blue = compliant, red = exception; enables rapid visual scanning without reading every datum.
- Mixed visualization types: pie for proportions (account distributions), line for trends, tables for detail; mixed intentionally, not cluttered.
- Smart defaults: prebuilt statutory reports (Balance Sheet, P&L per TT200) so users don’t design from scratch.

## Checkpoint: project_vision

Vision
- Compliance-first Vietnamese accounting ERP that accelerates voucher entry and guarantees TT200-aligned reporting with real-time insight.

Users
- Primary: Chief Accountants, CFOs in VN SMEs.  Secondary: accounting students/juniors.

Core Experience
- Record vouchers fast with immediate TT200 validation; close periods confidently; generate 100% compliant financial statements.

Desired Feeling
- Empowered and in control; calm and focused.

Platform
- Desktop-first web (1920px+), tablet-responsive viewing; no mobile app initially.

Inspiration & Patterns to Apply
- KPI-first dashboards, compliance color coding (green/blue=ok, red=exception), mixed charts+tables for clarity, smart statutory defaults.

UX Complexity Assessment
- High: multiple roles, many primary journeys (voucher entry, AR/AP, closing, reporting), dense data tables, compliance constraints, responsive needs.

Facilitation Mode
- UX_INTERMEDIATE — balance design concepts with concise explanations; confirm understanding at key points.

## Checkpoint: defining_experience

- A desktop accounting system that enforces TT200 compliance automatically while keeping you in complete control—so you're never stressed about audit-readiness again.

## Checkpoint: core_experience_principles

- Speed: Instant validations and saved drafts; sub-1s field checks; keyboard-first data grid interactions.
- Guidance: Inline TT200 tips and fix suggestions; non-blocking toasts; clear recovery paths.
- Flexibility: Dense “power mode” tables plus simplified “guided” forms; configurable columns and presets.
- Feedback: Subtle success toasts; prominent error banners with field anchors; compliance status badges on reports.
- Confirmation before important action: Confirm on destructive/irreversible ops (delete, post, close period) with concise impact summary.

## Checkpoint: visual_foundation

- Generated 4 theme directions with palettes, component examples, and KPIs:
  1. Trust Blue (Professional, Compliance-first)
  2. Professional Teal (Efficient, Modern)
  3. Calm Green (Focused, Low‑Stress)
  4. Bold Purple (High Contrast, Expressive)
- File: ./ux-color-themes.html
 - Selected: Theme 1 · Trust Blue, with semantic mapping above

## Checkpoint: design_direction_decision

- Showcase generated: ./ux-design-directions.html (6 directions)
- Chosen Direction: Hybrid — #1 Dense Dashboard layout + #5 Inline Workflow
- Layout Decisions:
  - Navigation pattern: Sidebar with KPI-first top row
  - Content structure: Dense tables with inline expansion for advanced sections
  - Organization: Table-first lists; cards only for KPIs
- Hierarchy Decisions:
  - Visual density: Dense for data tables; balanced for forms
  - Header emphasis: Clear KPI cards; subdued section headers
  - Content focus: Data-first (tables, metrics), minimal imagery
- Interaction Decisions:
  - Primary action: Inline (reduce modals); stepper only for guided posting when needed
  - Information disclosure: Progressive (inline hints, expandable sections)
  - User control: Guided but flexible, keyboard-first
- Visual Style Decisions:
  - Weight: Balanced-to-dense; subtle elevation
  - Border style: Subtle on dark surfaces
- Rationale:
  - KPI-first situational awareness + minimal context switching; inline edits speed voucher workflows and reduce modal fatigue.
  - Fits desktop power users while maintaining clarity for compliance tasks.
- User Notes: Prefer confirm-before-important-action on post/close/delete

## Checkpoint: user_journey_flows

Journey: Dashboard (Entry)
- Step 1: View KPI row (Close %, Exceptions, AP/AR statuses)
- Step 2: Scan alerts (exceptions highlighted red/orange)
- Step 3: Take action (New Voucher / Review Exceptions / Open Reports)
- Success: User lands on next screen with context preserved

Journey: Voucher Creation → Posting (GL)
- Step 1: Enter voucher header (date, period, description)
- Step 2: Add lines in table (accounts, Dr/Cr, amounts) with inline TT200 hints
- Step 3: Fix validation chips; save draft
- Step 4: Post with confirmation (impact summary)
- Success: Status = Posted; audit entry created

Journey: Period Close → Reports
- Step 1: Run pre‑flight checks (no drafts; balanced)
- Step 2: Confirm close (irreversible summary)
- Step 3: Generate B01/B02/B03; mark compliance badges
- Step 4: Drill‑down from report numbers to vouchers
- Success: Period locked; reports exported


