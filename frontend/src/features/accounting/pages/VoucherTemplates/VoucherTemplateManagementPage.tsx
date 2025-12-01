'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ScrollArea } from '@/components/ui/scroll-area'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Switch } from '@/components/ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import {
  activateVoucherTemplate,
  createVoucherTemplate,
  deactivateVoucherTemplate,
  deleteVoucherTemplate,
  getVoucherTemplateById,
  getVoucherTemplates,
  updateVoucherTemplate,
} from '@/services/voucher'
import type {
  VoucherTemplateDTO,
  VoucherTemplatePayload,
  VoucherTemplateSummaryDTO,
} from '@/types/voucher'
import { RoleGuard } from '@/components'
import AccountCombobox from '@/components/account/AccountCombobox'
import {
  Copy,
  Loader2,
  Lock,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Trash2,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react'
import { toast } from 'sonner'

const STATUS_FILTERS = [
  { label: 'All Status', value: 'all' },
  { label: 'Active', value: 'active' },
  { label: 'Inactive', value: 'inactive' },
]

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]

type TemplateFormMode = 'create' | 'edit' | 'duplicate'

type TemplateLineForm = {
  id: string
  debitAccountId: number | null
  creditAccountId: number | null
  defaultDescription: string
  requiresCustomer: boolean
  requiresSupplier: boolean
  requiresCostCenter: boolean
  lockAccounts: boolean
}

type TemplateFormState = {
  name: string
  description: string
  isActive: boolean
  lines: TemplateLineForm[]
}

const createEmptyLine = (): TemplateLineForm => ({
  id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
  debitAccountId: null,
  creditAccountId: null,
  defaultDescription: '',
  requiresCustomer: false,
  requiresSupplier: false,
  requiresCostCenter: false,
  lockAccounts: false,
})

const initialFormState: TemplateFormState = {
  name: '',
  description: '',
  isActive: true,
  lines: [createEmptyLine()],
}

function mapTemplateToForm(template: VoucherTemplateDTO): TemplateFormState {
  return {
    name: template.name,
    description: template.description || '',
    isActive: template.isActive,
    lines: template.lines?.map((line) => ({
      id: `${template.id}-${line.lineNumber}-${Math.random().toString(36).slice(2, 6)}`,
      debitAccountId: line.debitAccountId ? Number(line.debitAccountId) : null,
      creditAccountId: line.creditAccountId ? Number(line.creditAccountId) : null,
      defaultDescription: line.defaultDescription || '',
      requiresCustomer: Boolean(line.requiresCustomer),
      requiresSupplier: Boolean(line.requiresSupplier),
      requiresCostCenter: Boolean(line.requiresCostCenter),
      lockAccounts: Boolean(line.lockAccounts),
    })) ?? [createEmptyLine()],
  }
}

function buildTemplatePayload(state: TemplateFormState): VoucherTemplatePayload {
  return {
    name: state.name.trim(),
    description: state.description?.trim() || undefined,
    isActive: state.isActive,
    lines: state.lines.map((line, index) => ({
      debitAccountId: line.debitAccountId ? String(line.debitAccountId) : undefined,
      creditAccountId: line.creditAccountId ? String(line.creditAccountId) : undefined,
      defaultDescription: line.defaultDescription?.trim() || undefined,
      requiresCustomer: line.requiresCustomer || undefined,
      requiresSupplier: line.requiresSupplier || undefined,
      requiresCostCenter: line.requiresCostCenter || undefined,
      lockAccounts: line.lockAccounts || undefined,
      lineNumber: index + 1,
    })),
  }
}

type DeleteState = {
  open: boolean
  template: VoucherTemplateSummaryDTO | null
}

function TemplateStatusBadge({ isActive }: { isActive: boolean }) {
  if (isActive) {
    return (
      <Badge variant="default" className="gap-1">
        <ShieldCheck className="h-3.5 w-3.5" />
        Active
      </Badge>
    )
  }
  return (
    <Badge variant="outline" className="gap-1 text-muted-foreground">
      Inactive
    </Badge>
  )
}

export default function VoucherTemplateManagementPage() {
  const [templates, setTemplates] = useState<VoucherTemplateSummaryDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [refreshing, setRefreshing] = useState(false)

  const [formOpen, setFormOpen] = useState(false)
  const [formMode, setFormMode] = useState<TemplateFormMode>('create')
  const [formState, setFormState] = useState<TemplateFormState>(initialFormState)
  const [formLoading, setFormLoading] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [editingTemplateId, setEditingTemplateId] = useState<string | null>(null)

  const [deleteState, setDeleteState] = useState<DeleteState>({ open: false, template: null })
  const [deleteLoading, setDeleteLoading] = useState(false)
  const [rowActionId, setRowActionId] = useState<string | null>(null)

  const filteredTemplates = useMemo(() => {
    return templates.filter((template) => {
      const matchesSearch =
        !search.trim() ||
        template.name.toLowerCase().includes(search.toLowerCase()) ||
        template.description?.toLowerCase().includes(search.toLowerCase())
      const matchesStatus =
        statusFilter === 'all' ||
        (statusFilter === 'active' ? template.isActive : !template.isActive)
      return matchesSearch && matchesStatus
    })
  }, [templates, search, statusFilter])

  const paginatedTemplates = useMemo(() => {
    const start = page * pageSize
    return filteredTemplates.slice(start, start + pageSize)
  }, [filteredTemplates, page, pageSize])

  const totalPages = Math.max(1, Math.ceil(filteredTemplates.length / pageSize))

  const loadTemplates = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await getVoucherTemplates()
      setTemplates(response)
    } catch (err: any) {
      const message = err?.message || 'Cannot load voucher template list'
      setError(message)
      toast.error('Load voucher template failed', { description: message })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadTemplates()
  }, [loadTemplates])

  const handleRefresh = async () => {
    setRefreshing(true)
    await loadTemplates()
    setRefreshing(false)
    toast.success('Refreshed voucher template list')
  }

  const openCreateDialog = () => {
    setFormMode('create')
    setFormState(initialFormState)
    setFormError(null)
    setEditingTemplateId(null)
    setFormOpen(true)
  }

  const openEditDialog = async (templateId: string, mode: TemplateFormMode = 'edit') => {
    setFormMode(mode)
    setFormError(null)
    setFormOpen(true)
    setFormLoading(true)
    try {
      const detail = await getVoucherTemplateById(templateId)
      const state = mapTemplateToForm(detail)
      const adjustedState =
        mode === 'duplicate'
          ? {
              ...state,
              name: `${state.name} (Copy)`,
              isActive: false,
            }
          : state
      setFormState(adjustedState)
      setEditingTemplateId(mode === 'edit' ? templateId : null)
    } catch (err: any) {
      const message = err?.message || 'Cannot load template information'
      setFormError(message)
      toast.error('Cannot open template form', { description: message })
      setFormOpen(false)
    } finally {
      setFormLoading(false)
    }
  }

  const validateForm = () => {
    if (!formState.name.trim()) {
      setFormError('Template name is required.')
      return false
    }
    if (!formState.lines.length) {
      setFormError('At least one line is required.')
      return false
    }
    if (formState.lines.some((line) => !line.debitAccountId && !line.creditAccountId)) {
      setFormError('Each line must select at least one account (debit or credit).')
      return false
    }
    setFormError(null)
    return true
  }

  const handleFormSubmit = async () => {
    if (!validateForm()) return
    setFormLoading(true)
    try {
      const payload = buildTemplatePayload(formState)
      const isEdit = formMode === 'edit' && editingTemplateId
      if (isEdit) {
        await updateVoucherTemplate(editingTemplateId, payload)
        toast.success('Updated voucher template')
      } else {
        await createVoucherTemplate(payload)
        toast.success('Created voucher template')
      }
      setFormOpen(false)
      setFormState(initialFormState)
      setEditingTemplateId(null)
      await loadTemplates()
    } catch (err: any) {
      const message = err?.message || 'Cannot save template'
      setFormError(message)
      toast.error('Save template failed', { description: message })
    } finally {
      setFormLoading(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteState.template) return
    setDeleteLoading(true)
    try {
      await deleteVoucherTemplate(deleteState.template.id)
      toast.success('Deleted voucher template')
      setDeleteState({ open: false, template: null })
      await loadTemplates()
    } catch (err: any) {
      const message = err?.message || 'Cannot delete template'
      toast.error('Delete template failed', { description: message })
    } finally {
      setDeleteLoading(false)
    }
  }

  const handleToggleStatus = async (template: VoucherTemplateSummaryDTO) => {
    setRowActionId(template.id)
    try {
      if (template.isActive) {
        await deactivateVoucherTemplate(template.id)
        toast.success(`Deactivated template ${template.name}`)
      } else {
        await activateVoucherTemplate(template.id)
        toast.success(`Activated template ${template.name}`)
      }
      await loadTemplates()
    } catch (err: any) {
      const message = err?.message || 'Cannot update status'
      toast.error('Update status failed', { description: message })
    } finally {
      setRowActionId(null)
    }
  }

  const handleDuplicate = (template: VoucherTemplateSummaryDTO) => {
    openEditDialog(template.id, 'duplicate')
  }

  const handleEdit = (template: VoucherTemplateSummaryDTO) => {
    openEditDialog(template.id, 'edit')
  }

  const handlePageChange = (direction: 'prev' | 'next') => {
    setPage((prev) => {
      if (direction === 'prev') {
        return Math.max(0, prev - 1)
      }
      return Math.min(totalPages - 1, prev + 1)
    })
  }

  useEffect(() => {
    setPage(0)
  }, [search, statusFilter, pageSize])

  const renderTableBody = () => {
    if (loading) {
      return (
        <TableRow>
          <TableCell colSpan={6} className="text-center py-12 text-muted-foreground">
            <Loader2 className="mx-auto mb-3 h-5 w-5 animate-spin" />
            Loading voucher template list...
          </TableCell>
        </TableRow>
      )
    }

    if (error) {
      return (
        <TableRow>
          <TableCell colSpan={6} className="py-10 text-center">
            <p className="text-sm text-destructive">{error}</p>
            <Button variant="outline" size="sm" className="mt-3" onClick={loadTemplates}>
              Try again
            </Button>
          </TableCell>
        </TableRow>
      )
    }

    if (!paginatedTemplates.length) {
      return (
        <TableRow>
          <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
            No template matches the current filter.
          </TableCell>
        </TableRow>
      )
    }

    return paginatedTemplates.map((template, index) => (
      <TableRow key={template.id}>
        <TableCell className="font-medium">{page * pageSize + index + 1}</TableCell>
        <TableCell>
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <span className="font-semibold text-sm">{template.name}</span>
              <TemplateStatusBadge isActive={template.isActive} />
            </div>
            <p className="text-xs text-muted-foreground line-clamp-2">
              {template.description || 'No description'}
            </p>
          </div>
        </TableCell>
        <TableCell>
          {template.firstLineDebitAccount ? (
            <Badge variant="outline" className="text-xs">
              Debit {template.firstLineDebitAccount.code}
            </Badge>
          ) : (
            '—'
          )}
        </TableCell>
        <TableCell>
          {template.firstLineCreditAccount ? (
            <Badge variant="outline" className="text-xs">
              Credit {template.firstLineCreditAccount.code}
            </Badge>
          ) : (
            '—'
          )}
        </TableCell>
        <TableCell>{template.createdBy || '—'}</TableCell>
        <TableCell className="w-[1%] whitespace-nowrap">
          <div className="flex items-center gap-1">
            <Button
              variant="ghost"
              size="icon"
              aria-label="Edit"
              onClick={() => handleEdit(template)}
            >
              <Pencil className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Duplicate"
              onClick={() => handleDuplicate(template)}
            >
              <Copy className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label={template.isActive ? 'Deactivate' : 'Activate'}
              onClick={() => handleToggleStatus(template)}
              disabled={rowActionId === template.id}
            >
              {rowActionId === template.id ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : template.isActive ? (
                <Lock className="h-4 w-4" />
              ) : (
                <ShieldCheck className="h-4 w-4" />
              )}
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Delete"
              onClick={() => setDeleteState({ open: true, template })}
            >
              <Trash2 className="h-4 w-4 text-destructive" />
            </Button>
          </div>
        </TableCell>
      </TableRow>
    ))
  }

  const updateLine = (lineId: string, patch: Partial<TemplateLineForm>) => {
    setFormState((prev) => ({
      ...prev,
      lines: prev.lines.map((line) => (line.id === lineId ? { ...line, ...patch } : line)),
    }))
  }

  const addLine = () => {
    setFormState((prev) => ({
      ...prev,
      lines: [...prev.lines, createEmptyLine()],
    }))
  }

  const removeLine = (lineId: string) => {
    setFormState((prev) => {
      if (prev.lines.length === 1) return prev
      return { ...prev, lines: prev.lines.filter((line) => line.id !== lineId) }
    })
  }

  return (
    <RoleGuard requiredRoles={['admin', 'chief_accountant', 'cfo']}>
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold">Voucher Templates</h1>
            <p className="text-sm text-muted-foreground">
              Manage voucher template library to apply quickly for accounting vouchers.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="outline" onClick={handleRefresh} disabled={refreshing}>
              {refreshing ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Refreshing...
                </>
              ) : (
                <>
                  <RefreshCw className="mr-2 h-4 w-4" />
                  Refresh
                </>
              )}
            </Button>
            <Button onClick={openCreateDialog}>
              <Plus className="mr-2 h-4 w-4" />
              Create new template
            </Button>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[240px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search by name or description..."
              className="pl-9"
            />
          </div>
          <Select
            value={statusFilter}
            onValueChange={(value) => setStatusFilter(value as typeof statusFilter)}
          >
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              {STATUS_FILTERS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[60px] text-center">#</TableHead>
                <TableHead>Template Name</TableHead>
                <TableHead className="w-[160px]">Debit Account (Line 1)</TableHead>
                <TableHead className="w-[160px]">Credit Account (Line 1)</TableHead>
                <TableHead className="w-[160px]">Created By</TableHead>
                <TableHead className="w-[140px] text-center">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>{renderTableBody()}</TableBody>
          </Table>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground">
          <div>
            Display {paginatedTemplates.length ? page * pageSize + 1 : 0}-
            {page * pageSize + paginatedTemplates.length} / {filteredTemplates.length} templates
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-2">
              <span>Page size</span>
              <Select
                value={String(pageSize)}
                onValueChange={(value) => setPageSize(Number(value))}
              >
                <SelectTrigger className="w-[90px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PAGE_SIZE_OPTIONS.map((option) => (
                    <SelectItem key={option} value={String(option)}>
                      {option}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-center gap-1">
              <Button
                variant="outline"
                size="icon"
                disabled={page === 0}
                onClick={() => handlePageChange('prev')}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span>
                Page {page + 1}/{totalPages}
              </span>
              <Button
                variant="outline"
                size="icon"
                disabled={page + 1 >= totalPages}
                onClick={() => handlePageChange('next')}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
      </div>

      <Sheet open={formOpen} onOpenChange={setFormOpen}>
        <SheetContent side="right" className="w-full sm:max-w-2xl !p-0 overflow-hidden">
          <SheetHeader className="px-6 pt-6 pb-4 flex-shrink-0">
            <SheetTitle>
              {formMode === 'edit'
                ? 'Edit voucher template'
                : formMode === 'duplicate'
                  ? 'Duplicate voucher template'
                  : 'Create voucher template'}
            </SheetTitle>
            <SheetDescription>
              {formMode === 'edit'
                ? 'Update template information and default line accounts.'
                : formMode === 'duplicate'
                  ? 'Copy existing template, you can edit before saving.'
                  : 'Declare template line accounts to apply quickly for accounting vouchers.'}
            </SheetDescription>
          </SheetHeader>
          <ScrollArea className="flex-1 min-h-0 px-6">
            <div className="flex flex-col gap-4 pb-4">
              {formLoading && (
                <div className="rounded-md border border-dashed px-4 py-3 text-sm text-muted-foreground flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading data...
                </div>
              )}
              {formError ? (
                <div className="rounded-md border border-destructive/50 bg-destructive/5 px-4 py-2 text-sm text-destructive">
                  {formError}
                </div>
              ) : null}
              <div className="grid gap-4">
                <div className="space-y-2">
                  <Label htmlFor="template-name">Template Name *</Label>
                  <Input
                    id="template-name"
                    value={formState.name}
                    onChange={(event) =>
                      setFormState((prev) => ({ ...prev, name: event.target.value }))
                    }
                    placeholder="Example: Cash received from customer"
                  />
                </div>
                <div className="flex items-center justify-between rounded-md border px-3 py-2">
                  <span className="text-sm font-medium">Active</span>
                  <Switch
                    checked={formState.isActive}
                    onCheckedChange={(checked) =>
                      setFormState((prev) => ({ ...prev, isActive: checked }))
                    }
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="template-description">Description</Label>
                <Textarea
                  id="template-description"
                  rows={2}
                  value={formState.description}
                  onChange={(event) =>
                    setFormState((prev) => ({ ...prev, description: event.target.value }))
                  }
                  placeholder="Description of template purpose..."
                />
              </div>
              <Separator />
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-semibold text-sm">Template line accounts</p>
                  <p className="text-xs text-muted-foreground">
                    Each line must have at least one account (debit or credit) and required
                    conditions.
                  </p>
                </div>
                <Button type="button" variant="outline" size="sm" onClick={addLine}>
                  <Plus className="mr-1.5 h-4 w-4" />
                  Add line
                </Button>
              </div>
              <div className="space-y-4">
                {formState.lines.map((line, index) => (
                  <div
                    key={line.id}
                    className="rounded-lg border p-4 space-y-3 bg-muted/40 relative"
                  >
                    <div className="flex items-center justify-between">
                      <Badge variant="outline">Line {index + 1}</Badge>
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={formState.lines.length === 1}
                        onClick={() => removeLine(line.id)}
                        className="text-destructive"
                      >
                        Delete
                      </Button>
                    </div>
                    <div className="grid gap-3">
                      <div className="space-y-1.5">
                        <Label>Debit Account</Label>
                        <AccountCombobox
                          value={line.debitAccountId}
                          onValueChange={(value) => updateLine(line.id, { debitAccountId: value })}
                          disabled={formLoading}
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label>Credit Account</Label>
                        <AccountCombobox
                          value={line.creditAccountId}
                          onValueChange={(value) => updateLine(line.id, { creditAccountId: value })}
                          disabled={formLoading}
                        />
                      </div>
                    </div>
                    <div className="space-y-1.5">
                      <Label>Default description</Label>
                      <Input
                        value={line.defaultDescription}
                        onChange={(event) =>
                          updateLine(line.id, { defaultDescription: event.target.value })
                        }
                        placeholder="Example: Cash received from customer..."
                      />
                    </div>
                    <div className="grid gap-2 grid-cols-2">
                      <Label className="flex items-center gap-2 text-sm font-medium">
                        <Checkbox
                          checked={line.requiresCustomer}
                          onCheckedChange={(checked) =>
                            updateLine(line.id, { requiresCustomer: Boolean(checked) })
                          }
                        />
                        Requires customer
                      </Label>
                      <Label className="flex items-center gap-2 text-sm font-medium">
                        <Checkbox
                          checked={line.requiresSupplier}
                          onCheckedChange={(checked) =>
                            updateLine(line.id, { requiresSupplier: Boolean(checked) })
                          }
                        />
                        Requires supplier
                      </Label>
                      <Label className="flex items-center gap-2 text-sm font-medium">
                        <Checkbox
                          checked={line.requiresCostCenter}
                          onCheckedChange={(checked) =>
                            updateLine(line.id, { requiresCostCenter: Boolean(checked) })
                          }
                        />
                        Requires cost center
                      </Label>
                      <Label className="flex items-center gap-2 text-sm font-medium">
                        <Checkbox
                          checked={line.lockAccounts}
                          onCheckedChange={(checked) =>
                            updateLine(line.id, { lockAccounts: Boolean(checked) })
                          }
                        />
                        Lock accounts
                      </Label>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </ScrollArea>
          <SheetFooter className="px-6 py-4 border-t flex-shrink-0 flex-row gap-2 sm:justify-end">
            <Button variant="outline" onClick={() => setFormOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleFormSubmit} disabled={formLoading}>
              {formLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                'Save Template'
              )}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>

      <Dialog
        open={deleteState.open}
        onOpenChange={(open) =>
          setDeleteState({ open, template: open ? deleteState.template : null })
        }
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Voucher Template</DialogTitle>
            <DialogDescription>
              Bạn chắc chắn muốn xoá mẫu{' '}
              <span className="font-semibold">{deleteState.template?.name}</span>? Hành động này
              không thể hoàn tác.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDeleteState({ open: false, template: null })}
            >
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleDelete} disabled={deleteLoading}>
              {deleteLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Deleting...
                </>
              ) : (
                'Delete Template'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </RoleGuard>
  )
}
