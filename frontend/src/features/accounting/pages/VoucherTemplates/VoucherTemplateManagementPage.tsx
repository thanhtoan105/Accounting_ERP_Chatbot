'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
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
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
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
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
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
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Copy,
  Edit,
  Loader2,
  MoreVertical,
  Plus,
  RefreshCw,
  Search,
  Trash2,
} from 'lucide-react'
import { toast } from 'sonner'

const STATUS_FILTERS = [
  { label: 'All Status', value: 'all' },
  { label: 'Active', value: 'active' },
  { label: 'Inactive', value: 'inactive' },
]

const PAGE_SIZE_OPTIONS = [8, 12, 16, 24, 48]

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

export default function VoucherTemplateManagementPage() {
  const [templates, setTemplates] = useState<VoucherTemplateSummaryDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(12)

  const [formOpen, setFormOpen] = useState(false)
  const [formMode, setFormMode] = useState<TemplateFormMode>('create')
  const [formState, setFormState] = useState<TemplateFormState>(initialFormState)
  const [formLoading, setFormLoading] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [editingTemplateId, setEditingTemplateId] = useState<string | null>(null)

  const [deleteState, setDeleteState] = useState<DeleteState>({ open: false, template: null })
  const [deleteLoading, setDeleteLoading] = useState(false)
  const [togglingIds, setTogglingIds] = useState<Set<string>>(new Set())

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search)
    }, 300)
    return () => clearTimeout(timer)
  }, [search])

  const filteredTemplates = useMemo(() => {
    return templates.filter((template) => {
      const matchesSearch =
        !debouncedSearch.trim() ||
        template.name.toLowerCase().includes(debouncedSearch.toLowerCase()) ||
        template.description?.toLowerCase().includes(debouncedSearch.toLowerCase())
      const matchesStatus =
        statusFilter === 'all' ||
        (statusFilter === 'active' ? template.isActive : !template.isActive)
      return matchesSearch && matchesStatus
    })
  }, [templates, debouncedSearch, statusFilter])

  const totalFiltered = filteredTemplates.length
  const pagedTemplates = useMemo(
    () => filteredTemplates.slice((page - 1) * pageSize, page * pageSize),
    [filteredTemplates, page, pageSize],
  )
  const totalPages = Math.max(1, Math.ceil(totalFiltered / pageSize))

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

  useEffect(() => {
    setPage(1)
  }, [debouncedSearch, statusFilter, pageSize])

  const handleRefresh = () => {
    loadTemplates()
  }

  const openCreateSheet = () => {
    setFormMode('create')
    setFormState(initialFormState)
    setFormError(null)
    setEditingTemplateId(null)
    setFormOpen(true)
  }

  const openEditSheet = async (templateId: string, mode: TemplateFormMode = 'edit') => {
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
    setTogglingIds((prev) => new Set(prev).add(template.id))
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
      setTogglingIds((prev) => {
        const next = new Set(prev)
        next.delete(template.id)
        return next
      })
    }
  }

  const handleDuplicate = (template: VoucherTemplateSummaryDTO) => {
    openEditSheet(template.id, 'duplicate')
  }

  const handleEdit = (template: VoucherTemplateSummaryDTO) => {
    openEditSheet(template.id, 'edit')
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

  const renderCardGrid = () => {
    if (loading && templates.length === 0) {
      return (
        <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Skeleton key={i} className="h-40 w-full rounded-xl" />
          ))}
        </div>
      )
    }

    if (error) {
      return (
        <div className="text-center py-12">
          <p className="text-sm text-destructive mb-3">{error}</p>
          <Button variant="outline" size="sm" onClick={loadTemplates}>
            Try again
          </Button>
        </div>
      )
    }

    if (!pagedTemplates.length) {
      return (
        <div className="text-center py-8 text-muted-foreground">
          <p className="text-lg font-medium">No templates found</p>
          <p className="text-sm">
            {debouncedSearch
              ? 'Try adjusting your search criteria.'
              : 'Get started by creating your first voucher template.'}
          </p>
        </div>
      )
    }

    return (
      <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 voucher-stagger">
        {pagedTemplates.map((template) => (
          <Card
            key={template.id}
            className="voucher-card-interactive voucher-row-animate cursor-pointer group relative py-4"
            onClick={() => handleEdit(template)}
          >
            <CardContent className="p-4">
              <div className="flex items-start justify-between">
                <div className="space-y-1 flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-base font-semibold truncate">{template.name}</span>
                    <Badge
                      className={
                        template.isActive
                          ? 'rounded-full border-none bg-green-600/10 text-green-600 dark:bg-green-400/10 dark:text-green-400'
                          : 'bg-destructive/10 text-destructive rounded-full border-none'
                      }
                    >
                      <span
                        className={`size-1.5 rounded-full mr-1 ${
                          template.isActive
                            ? 'bg-green-600 dark:bg-green-400'
                            : 'bg-destructive'
                        }`}
                        aria-hidden="true"
                      />
                      {template.isActive ? 'Active' : 'Inactive'}
                    </Badge>
                  </div>
                  <p
                    className="text-xs text-muted-foreground line-clamp-2"
                    title={template.description || 'No description'}
                  >
                    {template.description || 'No description'}
                  </p>
                </div>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-8 w-8 p-0 opacity-0 group-hover:opacity-100 transition-opacity"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <MoreVertical className="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem
                      onClick={(e) => {
                        e.stopPropagation()
                        handleEdit(template)
                      }}
                    >
                      <Edit className="mr-2 h-4 w-4" />
                      Edit
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      onClick={(e) => {
                        e.stopPropagation()
                        handleDuplicate(template)
                      }}
                    >
                      <Copy className="mr-2 h-4 w-4" />
                      Duplicate
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      variant="destructive"
                      onClick={(e) => {
                        e.stopPropagation()
                        setDeleteState({ open: true, template })
                      }}
                    >
                      <Trash2 className="mr-2 h-4 w-4" />
                      Delete
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>

              <div className="mt-3 space-y-1.5">
                {template.firstLineDebitAccount && (
                  <div className="flex items-center gap-2">
                    <Badge variant="outline" className="text-xs font-mono">
                      Dr: {template.firstLineDebitAccount.code}
                    </Badge>
                    <span className="text-xs text-muted-foreground truncate">
                      {template.firstLineDebitAccount.name}
                    </span>
                  </div>
                )}
                {template.firstLineCreditAccount && (
                  <div className="flex items-center gap-2">
                    <Badge variant="outline" className="text-xs font-mono">
                      Cr: {template.firstLineCreditAccount.code}
                    </Badge>
                    <span className="text-xs text-muted-foreground truncate">
                      {template.firstLineCreditAccount.name}
                    </span>
                  </div>
                )}
                {!template.firstLineDebitAccount && !template.firstLineCreditAccount && (
                  <p className="text-xs text-muted-foreground italic">No accounts configured</p>
                )}
              </div>

              <div
                className="mt-4 flex items-center justify-between border-t pt-3"
                onClick={(e) => e.stopPropagation()}
              >
                <Label
                  htmlFor={`toggle-${template.id}`}
                  className="text-xs text-muted-foreground cursor-pointer"
                >
                  {template.isActive ? 'Active' : 'Inactive'}
                </Label>
                <Switch
                  id={`toggle-${template.id}`}
                  checked={template.isActive}
                  disabled={togglingIds.has(template.id)}
                  onCheckedChange={() => handleToggleStatus(template)}
                />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    )
  }

  return (
    <RoleGuard requiredRoles={['admin', 'chief_accountant', 'cfo']}>
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Voucher Templates</h1>
            <p className="text-sm text-muted-foreground">
              Manage voucher template library to apply quickly for accounting vouchers.
            </p>
          </div>
          <Button onClick={openCreateSheet}>
            <Plus className="mr-2 h-4 w-4" />
            Create Template
          </Button>
        </div>

        <div className="flex items-center gap-2">
          <div className="relative flex-1 max-w-sm">
            <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search by name or description..."
              className="pl-8"
            />
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <Select
              value={statusFilter}
              onValueChange={(value) => setStatusFilter(value as typeof statusFilter)}
            >
              <SelectTrigger aria-label="Filter status" className="w-28">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>
              <SelectContent side="bottom" align="end">
                {STATUS_FILTERS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              variant="outline"
              onClick={handleRefresh}
              disabled={loading}
              className="h-9 w-9 p-0"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            </Button>
          </div>
        </div>

        {renderCardGrid()}

        {!loading && !error && pagedTemplates.length > 0 && (
          <div className="flex items-center justify-between border-t px-4 py-4">
            <div className="text-sm text-muted-foreground">
              Total: <strong className="text-foreground">{totalFiltered}</strong> templates
            </div>
            <div className="flex items-center gap-6">
              <div className="hidden items-center gap-2 lg:flex">
                <Label htmlFor="cards-per-page" className="text-sm font-medium">
                  Cards per page
                </Label>
                <Select
                  value={`${pageSize}`}
                  onValueChange={(value) => {
                    setPageSize(Number(value))
                    setPage(1)
                  }}
                  disabled={loading}
                >
                  <SelectTrigger size="sm" className="w-20" id="cards-per-page">
                    <SelectValue placeholder={pageSize} />
                  </SelectTrigger>
                  <SelectContent side="top">
                    {PAGE_SIZE_OPTIONS.map((size) => (
                      <SelectItem key={size} value={`${size}`}>
                        {size}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex items-center justify-center text-sm font-medium">
                Page {page} / {totalPages}
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  className="hidden h-8 w-8 p-0 lg:flex"
                  onClick={() => setPage(1)}
                  disabled={page === 1 || loading}
                >
                  <span className="sr-only">First page</span>
                  <ChevronsLeft className="size-4" />
                </Button>
                <Button
                  variant="outline"
                  className="h-8 w-8"
                  size="icon"
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={page === 1 || loading}
                >
                  <span className="sr-only">Previous page</span>
                  <ChevronLeft className="size-4" />
                </Button>
                <Button
                  variant="outline"
                  className="h-8 w-8"
                  size="icon"
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={page >= totalPages || loading}
                >
                  <span className="sr-only">Next page</span>
                  <ChevronRight className="size-4" />
                </Button>
                <Button
                  variant="outline"
                  className="hidden h-8 w-8 lg:flex"
                  size="icon"
                  onClick={() => setPage(totalPages)}
                  disabled={page >= totalPages || loading}
                >
                  <span className="sr-only">Last page</span>
                  <ChevronsRight className="size-4" />
                </Button>
              </div>
            </div>
          </div>
        )}
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
              Are you sure you want to delete template{' '}
              <span className="font-semibold">{deleteState.template?.name}</span>? This action
              cannot be undone.
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
