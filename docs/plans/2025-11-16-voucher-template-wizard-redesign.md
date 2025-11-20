# Voucher Template Wizard Redesign

**Date:** 2025-11-16
**Status:** Validated Design
**Component:** VoucherTemplateManagementPage

## Problem Statement

The current "Create Template" dialog has two main usability issues:
1. **Overwhelming interface** - Too much information displayed at once confuses users
2. **Tedious line addition** - Adding multiple template lines is a cumbersome process

## Solution: Multi-Step Wizard with Inline Table Editing

Replace the single large dialog with a three-step wizard that breaks the task into focused stages and provides inline table editing for efficient line management.

---

## Overall Wizard Structure

### Component Architecture

- Dialog component containing a Stepper component
- Three distinct steps: Template Details → Template Lines → Review & Save
- Each step focuses on one specific task

### Navigation Pattern

**Top of Dialog:**
- Stepper indicator showing all three steps
- Current step highlighted
- Completed steps show checkmark
- Future steps grayed out

**Bottom of Dialog:**
- "Back" button (disabled on Step 1)
- "Next" button (becomes "Save Template" on Step 3)
- "Cancel" button (with confirmation if changes exist)

### State Management

Single state object for the entire template:

```typescript
const [template, setTemplate] = useState({
  name: '',
  description: '',
  isActive: true,
  lines: []
});
```

Additional state for current step tracking (1, 2, or 3).

**Navigation Model:** Linear but flexible
- "Next" button advances to next step
- Users can click step headers to return to previous steps
- No validation blocking between steps
- Changes held in state until final save

---

## Step 1: Template Details

### Purpose
Collect basic template information without overwhelming the user.

### Fields

1. **Template Name** (required)
   - Text input
   - Placeholder: "e.g., Sales Invoice, Purchase Order"
   - Inline validation on blur (red border + error if empty)

2. **Description** (optional)
   - Textarea
   - Placeholder: "Describe when to use this template..."

3. **Active Status**
   - Checkbox (checked by default)
   - Label: "Active"
   - Helper text: "Only active templates appear in voucher creation"

### Layout
- Clean vertical form
- Fields centered, taking ~60% of dialog width
- Generous spacing between fields (mb-6)
- Card or bordered container for form area
- Breathing room - not filling entire dialog height

### Validation
- Show inline hints on blur
- Don't block navigation to next step
- User must fix before final save

---

## Step 2: Template Lines (Inline Table Editing)

### Purpose
Enable fast, efficient addition of multiple template lines with minimal friction.

### Table Structure

Columns:
1. **#** - Auto-numbered (1, 2, 3...)
2. **Debit Account** - Account code + name (e.g., "111 - Cash")
3. **Credit Account** - Account code + name (e.g., "511 - Revenue")
4. **Required Fields** - Badges showing "Amount • Description • Dimensions"
5. **Actions** - Delete button (trash icon)

### Adding New Rows

- Prominent "+ Add Line" button at bottom (or top if empty)
- Click adds blank row immediately
- New row auto-enters edit mode
- Focus automatically moves to Debit Account field

### Inline Editing Behavior

Each row can be clicked to enter edit mode:

**Debit/Credit Account Cells:**
- Click opens AccountPicker dropdown inline
- Same component as current implementation
- Saves selection on close

**Required Fields Cell:**
- Click opens small popover
- Three checkboxes:
  - Amount Required
  - Description Required
  - Dimensions Required
- Saves on close

**Visual Feedback:**
- Row shows subtle highlight when in edit mode
- Click outside row or press Enter to save
- Changes immediately reflected in table

### Empty State

When no lines exist:
- Table/grid icon
- "No template lines yet"
- "Add your first line to define the voucher structure"
- Prominent "+ Add First Line" button

---

## Step 3: Review & Save

### Purpose
Provide complete summary before saving, with easy access to edit any section.

### Template Details Summary

Card/section showing:
- Template Name (large, bold)
- Description (if provided)
- Status badge: "Active" (green) or "Inactive" (gray)
- "Edit" button → jumps to Step 1

### Template Lines Summary

Read-only table showing:
- Title: "Template Lines (X lines)"
- Same columns as Step 2 (without edit functionality)
- Clean, condensed view
- "Edit Lines" button → jumps to Step 2

### Validation Summary

Alert/banner at top showing status:

**If Invalid:**
- ⚠️ "Template name is required" (link: "Go to Step 1")
- ⚠️ "At least one template line is required" (link: "Go to Step 2")

**If Valid:**
- ✅ "Template is valid and ready to save" (green success message)

### Save Action

- "Next" button changes to "Save Template"
- Only enabled if validation passes
- On click:
  - Save template via API
  - Close dialog
  - Show success toast
  - Refresh template list

---

## Technical Implementation Notes

### Components to Create/Modify

1. **VoucherTemplateWizardDialog.tsx** (new)
   - Main wizard container
   - Step management
   - State handling

2. **TemplateDetailsStep.tsx** (new)
   - Step 1 form

3. **TemplateLinesStep.tsx** (new)
   - Step 2 inline table
   - Uses existing AccountPicker component

4. **TemplateReviewStep.tsx** (new)
   - Step 3 summary view

5. **VoucherTemplateManagementPage.tsx** (modify)
   - Replace dialog trigger to use new wizard

### shadcn Components Needed

- Dialog
- Form
- Input
- Textarea
- Checkbox
- Button
- Table
- Badge
- Card
- Popover
- Alert

### State Flow

```
User clicks "Create Template"
  ↓
Open wizard dialog (Step 1)
  ↓
Fill template details
  ↓
Click "Next" → Step 2
  ↓
Add lines via inline editing
  ↓
Click "Next" → Step 3
  ↓
Review summary
  ↓
Click "Save Template"
  ↓
API call → Success
  ↓
Close dialog, refresh list, show toast
```

---

## Success Criteria

- Users can complete template creation without feeling overwhelmed
- Adding 5-10 template lines feels quick and natural
- Users can navigate back to edit any section easily
- Validation errors are clear and actionable
- Mobile/tablet experience is improved (smaller, focused steps)

## Future Enhancements (Not in Initial Implementation)

- Save draft functionality
- Template line reordering (drag and drop)
- Clone/duplicate existing templates
- Bulk import of template lines
