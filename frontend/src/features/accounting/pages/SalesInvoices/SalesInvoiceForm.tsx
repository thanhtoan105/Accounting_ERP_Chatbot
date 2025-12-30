'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { format, addDays, isAfter, isBefore, parseISO, formatDistanceToNow } from 'date-fns'
import {
  AlertCircle,
  CalendarIcon,
  Loader2,
  Save,
  ArrowLeft,
  FileText,
  CheckCircle,
  XCircle,
  RotateCcw,
  Send,
  RefreshCw,
  FileX,
} from 'lucide-react'
import { useParams, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { toast } from 'sonner'

import {
  SalesInvoiceLineGrid,
  type SalesInvoiceLine,
} from '@/components/sales/SalesInvoiceLineGrid'
import { CustomerPicker } from '@/components/sales/CustomerPicker'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { Calendar } from '@/components/ui/calendar'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { cn } from '@/lib/utils'
import { SalesInvoiceApprovalDialog } from '@/components/sales/SalesInvoiceApprovalDialog'
import { SalesInvoiceApprovalHistory } from '@/components/sales/SalesInvoiceApprovalHistory'
import { useRole } from '@/hooks/useRole'

import type { AccountSummary } from '@/components/account/AccountPicker'
import useUndoRedo from '@/hooks/useUndoRedo'
import { useAuth } from '@/hooks/useAuth'
import { getCompanyId } from '@/utils/axios'
import type { SalesInvoiceDTO, SalesInvoiceCreateRequest, VatRate } from '@/types/salesInvoice'
import {
  createSalesInvoice,
  validateSalesInvoice,
  updateSalesInvoice,
  getSalesInvoiceById,
  submitForApproval,
  getPendingApprovals,
} from '@/services/salesInvoice'
import { getPostableAccounts } from '@/services/chartOfAccounts'
import type { ChartOfAccount } from '@/types/chartOfAccount'
import type { Customer } from '@/types/customer'
import type { VATCorrectionDTO } from '@/types/vat'
import { VATCorrectionDialog } from '../VATReports/VATCorrectionDialog'
import { ApproveVATCorrectionDialog } from '../VATReports/ApproveVATCorrectionDialog'

const formSchema = z.object({
  customerId: z.number({ message: 'Customer is required' }),
  invoiceNumber: z.string().min(1, 'Invoice number is required'),
  invoiceDate: z.string({ message: 'Invoice date is required' }),
  dueDate: z.string({ message: 'Due date is required' }),
  reference: z
    .string()
    .min(1, 'Reference is required')
    .max(100, 'Reference must be 100 characters or less'),
  description: z
    .string()
    .max(500, 'Description must be 500 characters or less')
    .optional()
    .or(z.literal('')),
})

type SalesInvoiceFormValues = z.infer<typeof formSchema>

type DraftLock = {
  ownerId: string
  ownerName?: string
  expiresAt: string
}

type SalesInvoiceDraftPayload = {
  version: number
  header: SalesInvoiceFormValues
  lines: SalesInvoiceLine[]
  updatedAt: string
  lock?: DraftLock | null
}

const DRAFT_VERSION = 1
const AUTO_SAVE_DEBOUNCE_MS = 30000 // 30 seconds
const LOCK_DURATION_MINUTES = 5
const DEFAULT_PAYMENT_TERMS_DAYS = 30
const COMPANY_DEFAULT_VAT_RATE: VatRate = 'TEN'
const VAT_SUM_TOLERANCE = 1000
const VAT_RATE_MAP: Record<VatRate, number> = {
  ZERO: 0,
  FIVE: 0.05,
  TEN: 0.1,
  EXEMPT: 0,
}

type VatIssue = {
  severity: 'warning' | 'error'
  message: string
}

function mapAccountsToSummaries(accounts: ChartOfAccount[]): AccountSummary[] {
  return accounts
    .filter((account) => account.postable)
    .map<AccountSummary>((account) => ({
      id: String(account.id),
      code: account.code,
      name: account.name,
      balanceSide: account.normalSide?.toLowerCase().includes('debit')
        ? 'debit'
        : account.normalSide?.toLowerCase().includes('credit')
          ? 'credit'
          : 'both',
      group: account.type,
      isLeaf: account.postable,
    }))
}

function createInitialLines(): SalesInvoiceLine[] {
  return [
    {
      id: `line-${Date.now()}`,
      lineNumber: 1,
      account: null,
      description: '',
      quantity: null,
      unitPrice: null,
      amount: null,
      vatRate: 'ZERO',
      vatAmount: null,
      itemId: null,
      item: null,
      status: 'clean',
    },
  ]
}

function buildDraftKey(companyId: number | null, invoiceId?: string) {
  const companyPart = companyId ?? 'default'
  const invoicePart = invoiceId ?? 'new'
  return `salesInvoiceDraft:${companyPart}:${invoicePart}`
}

function readSalesInvoiceDraft(key: string): SalesInvoiceDraftPayload | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const parsed = JSON.parse(raw) as SalesInvoiceDraftPayload
    if (parsed.version !== DRAFT_VERSION) return null
    return parsed
  } catch {
    return null
  }
}

function writeSalesInvoiceDraft(key: string, payload: SalesInvoiceDraftPayload) {
  if (typeof window === 'undefined') return
  localStorage.setItem(key, JSON.stringify(payload))
}

function removeSalesInvoiceDraft(key: string) {
  if (typeof window === 'undefined') return
  localStorage.removeItem(key)
}

function createDraftLock(ownerId: string, ownerName?: string): DraftLock {
  return {
    ownerId,
    ownerName,
    expiresAt: new Date(Date.now() + LOCK_DURATION_MINUTES * 60 * 1000).toISOString(),
  }
}

function isLockActive(lock?: DraftLock | null) {
  if (!lock) return false
  return new Date(lock.expiresAt).getTime() > Date.now()
}

function isLockedByOther(lock: DraftLock | null, ownerId: string) {
  if (!lock) return false
  if (!isLockActive(lock)) return false
  return lock.ownerId !== ownerId
}

function calculateVAT(amount: number, rate: VatRate): number {
  const rateValue = rate === 'FIVE' ? 0.05 : rate === 'TEN' ? 0.1 : rate === 'EXEMPT' ? 0 : 0
  return amount * rateValue
}

function calculateDueDate(
  invoiceDate: string,
  paymentTermsDays: number = DEFAULT_PAYMENT_TERMS_DAYS,
): string {
  const date = parseISO(invoiceDate)
  const dueDate = addDays(date, paymentTermsDays)
  return format(dueDate, 'yyyy-MM-dd')
}

export default function SalesInvoiceForm() {
  const params = useParams<{ invoiceId?: string }>()
  const navigate = useNavigate()
  const invoiceId = params.invoiceId && params.invoiceId !== 'new' ? params.invoiceId : undefined
  const isEditing = Boolean(invoiceId)
  const { user } = useAuth()
  const { canApproveVouchers } = useRole()
  const currentUserId = user?.id ? String(user.id) : 'anonymous'
  const companyId = getCompanyId()
  const draftStorageKey = buildDraftKey(companyId, invoiceId)
  const [accounts, setAccounts] = useState<AccountSummary[]>([])
  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null)
  const [initialState] = useState(() => {
    if (typeof window === 'undefined') {
      return {
        values: {
          customerId: 0,
          invoiceNumber: '',
          invoiceDate: format(new Date(), 'yyyy-MM-dd'),
          dueDate: calculateDueDate(format(new Date(), 'yyyy-MM-dd')),
          reference: '',
          description: '',
        },
        lines: createInitialLines(),
        lock: null as DraftLock | null,
        updatedAt: null as string | null,
      }
    }
    const stored = readSalesInvoiceDraft(draftStorageKey)
    return {
      values: stored?.header ?? {
        customerId: 0,
        invoiceNumber: '',
        invoiceDate: format(new Date(), 'yyyy-MM-dd'),
        dueDate: calculateDueDate(format(new Date(), 'yyyy-MM-dd')),
        reference: '',
        description: '',
      },
      lines: stored?.lines?.length ? stored.lines : createInitialLines(),
      lock: stored?.lock ?? null,
      updatedAt: stored?.updatedAt ?? null,
    }
  })
  const {
    value: lines,
    setValue: setLines,
    reset: resetLines,
    undo,
    redo,
    canUndo,
    canRedo,
  } = useUndoRedo<SalesInvoiceLine[]>(initialState.lines)
  const [validationMap, setValidationMap] = useState<Record<number, Record<string, string[]>>>({})
  const [headerErrors, setHeaderErrors] = useState<Record<string, string[]>>({})
  const [validating, setValidating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [loadingAccounts, setLoadingAccounts] = useState(true)
  const [autoSaveStatus, setAutoSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>(
    'idle',
  )
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(
    initialState.updatedAt ? new Date(initialState.updatedAt) : null,
  )
  const [draftLock, setDraftLock] = useState<DraftLock | null>(initialState.lock)
  const [isLocked, setIsLocked] = useState(
    () => !isEditing && isLockedByOther(initialState.lock, currentUserId),
  )
  const [loadingInvoice, setLoadingInvoice] = useState(false)
  const [editingInvoice, setEditingInvoice] = useState<SalesInvoiceDTO | null>(null)
  const [_autoSaveError, setAutoSaveError] = useState<string | null>(null)
  const [activeWorkflowId, setActiveWorkflowId] = useState<string | null>(null)
  const [approvalDialogAction, setApprovalDialogAction] = useState<'approve' | 'reject' | null>(
    null,
  )
  const [submittingForApproval, setSubmittingForApproval] = useState(false)
  const [vatCorrections, _setVatCorrections] = useState<VATCorrectionDTO[]>([])
  const [vatCorrectionsLoading, _setVatCorrectionsLoading] = useState(false)
  const [correctionDialogOpen, setCorrectionDialogOpen] = useState(false)
  const [approveCorrectionDialogOpen, setApproveCorrectionDialogOpen] = useState(false)
  const [selectedCorrection, setSelectedCorrection] = useState<VATCorrectionDTO | null>(null)
  const today = useMemo(() => new Date(), [])

  const form = useForm<SalesInvoiceFormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: initialState.values,
  })
  const watchedValues = useWatch({ control: form.control })
  const selectedDate = useMemo(() => {
    if (!watchedValues?.invoiceDate) return null
    const parsed = new Date(watchedValues.invoiceDate)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }, [watchedValues?.invoiceDate])
  const [calendarMonth, setCalendarMonth] = useState<Date>(selectedDate ?? today)

  useEffect(() => {
    if (selectedDate) {
      setCalendarMonth(selectedDate)
    } else {
      setCalendarMonth(today)
    }
  }, [today, selectedDate])

  // Auto-calculate due date when invoice date changes
  useEffect(() => {
    if (watchedValues?.invoiceDate && !isEditing) {
      const dueDate = calculateDueDate(watchedValues.invoiceDate)
      form.setValue('dueDate', dueDate)
    }
  }, [watchedValues?.invoiceDate, form, isEditing])

  useEffect(() => {
    let mounted = true
    async function loadAccounts() {
      try {
        const response = await getPostableAccounts()
        if (!mounted) return
        setAccounts(mapAccountsToSummaries(response))
      } catch (err: any) {
        toast.error('Failed to load accounts', {
          description: err?.message,
        })
      } finally {
        if (mounted) setLoadingAccounts(false)
      }
    }
    loadAccounts()
    return () => {
      mounted = false
    }
  }, [])

  const linePayload = useMemo(() => {
    return lines.map((line, index) => ({
      lineNumber: index + 1,
      accountId: line.account ? Number(line.account.id) : 0,
      description: line.description,
      quantity: line.quantity ?? undefined,
      unitPrice: line.unitPrice ?? undefined,
      amount: line.amount ?? 0,
      vatRate: line.vatRate,
      vatAmount: line.vatAmount ?? 0,
      itemId: line.itemId ? Number(line.itemId) : undefined,
    }))
  }, [lines])

  // Calculate VAT totals for validation (AC-VAT-003)
  const vatTotals = useMemo(() => {
    const sumOfLineVAT = lines.reduce((sum, line) => sum + (line.vatAmount ?? 0), 0)
    // Header VAT should equal sum of line VAT (in this form, header VAT = sum of line VAT)
    const headerVAT = sumOfLineVAT
    const variance = Math.abs(headerVAT - sumOfLineVAT) // Should be 0, but calculate for validation
    return {
      headerVAT,
      sumOfLineVAT,
      variance,
    }
  }, [lines])

  const vatIssues = useMemo<VatIssue[]>(() => {
    const issues: VatIssue[] = []
    lines.forEach((line, index) => {
      const lineNumber = index + 1
      const baseAmount = typeof line.amount === 'number' ? line.amount : 0
      const vatAmount = typeof line.vatAmount === 'number' ? line.vatAmount : 0
      const rate: VatRate = line.vatRate ?? 'ZERO'
      if (rate !== COMPANY_DEFAULT_VAT_RATE) {
        issues.push({
          severity: 'warning',
          message: `Line ${lineNumber} uses VAT rate ${rate}, which differs from company default ${COMPANY_DEFAULT_VAT_RATE}.`,
        })
      }
      const expectedVat = baseAmount * VAT_RATE_MAP[rate]
      const diff = Math.abs(vatAmount - expectedVat)
      if (diff > VAT_SUM_TOLERANCE) {
        issues.push({
          severity: 'error',
          message: `Line ${lineNumber} VAT differs from expected by ${Intl.NumberFormat(
            'vi-VN',
          ).format(Math.round(diff))}₫ (tolerance 1,000₫).`,
        })
      }
      if (baseAmount > 0) {
        const ratio = (vatAmount / baseAmount) * 100
        if (ratio < -0.01) {
          issues.push({
            severity: 'error',
            message: `Line ${lineNumber} has negative VAT ratio (${ratio.toFixed(2)}%).`,
          })
        } else if (ratio > 100.1) {
          issues.push({
            severity: 'error',
            message: `Line ${lineNumber} exceeds 100% VAT ratio (${ratio.toFixed(2)}%).`,
          })
        }
      } else if (vatAmount > VAT_SUM_TOLERANCE) {
        issues.push({
          severity: 'error',
          message: `Line ${lineNumber} has VAT amount without base amount.`,
        })
      }
    })

    // Add VAT variance validation (AC-VAT-003)
    if (vatTotals.variance >= VAT_SUM_TOLERANCE) {
      issues.push({
        severity: 'error',
        message: `VAT rounding variance: ${Intl.NumberFormat('vi-VN').format(Math.round(vatTotals.variance))} VND. Header VAT (${Intl.NumberFormat('vi-VN').format(Math.round(vatTotals.headerVAT))}₫) differs from sum of line VAT (${Intl.NumberFormat('vi-VN').format(Math.round(vatTotals.sumOfLineVAT))}₫) by ${Intl.NumberFormat('vi-VN').format(Math.round(vatTotals.variance))}₫, exceeding tolerance of 1,000₫. Posting is blocked.`,
      })
    } else if (vatTotals.variance > 0) {
      issues.push({
        severity: 'warning',
        message: `VAT rounding variance: ${Intl.NumberFormat('vi-VN').format(Math.round(vatTotals.variance))} VND. Header VAT (${Intl.NumberFormat('vi-VN').format(Math.round(vatTotals.headerVAT))}₫) differs from sum of line VAT (${Intl.NumberFormat('vi-VN').format(Math.round(vatTotals.sumOfLineVAT))}₫) by ${Intl.NumberFormat('vi-VN').format(Math.round(vatTotals.variance))}₫ (within tolerance).`,
      })
    }

    return issues
  }, [lines, vatTotals])

  const hasBlockingVatIssues = vatIssues.some((issue) => issue.severity === 'error')

  // VAT corrections for sales invoices - feature not yet implemented
  // TODO: Implement AR-specific VAT corrections API and enable this
  const loadVatCorrections = useCallback(async () => {
    // Disabled: VAT corrections API currently only supports purchase bills
    // if (!invoiceId) return
    // try {
    //   setVatCorrectionsLoading(true)
    //   const list = await vatService.listCorrections({ billId: invoiceId })
    //   setVatCorrections(list)
    // } catch (error) {
    //   toast.error(`Failed to load VAT corrections: ${String(error)}`)
    // } finally {
    //   setVatCorrectionsLoading(false)
    // }
  }, [])

  // Disabled: VAT corrections not yet supported for sales invoices
  // useEffect(() => {
  //   if (!invoiceId) return
  //   loadVatCorrections()
  // }, [invoiceId, loadVatCorrections])

  function buildRequest(values: SalesInvoiceFormValues): SalesInvoiceCreateRequest {
    return {
      id: invoiceId,
      customerId: values.customerId,
      invoiceNumber: values.invoiceNumber,
      invoiceDate: values.invoiceDate,
      dueDate: values.dueDate,
      reference: values.reference,
      description: values.description || null,
      status: editingInvoice?.status || 'DRAFT',
      lines: linePayload,
    }
  }

  const saveDraftSnapshot = useCallback(
    (values: SalesInvoiceFormValues, lineItems: SalesInvoiceLine[]) => {
      if (typeof window === 'undefined' || isLocked || isEditing) return
      try {
        setAutoSaveStatus('saving')
        const payload: SalesInvoiceDraftPayload = {
          version: DRAFT_VERSION,
          header: values,
          lines: lineItems,
          updatedAt: new Date().toISOString(),
          lock: createDraftLock(currentUserId, user?.fullName),
        }
        writeSalesInvoiceDraft(draftStorageKey, payload)
        setDraftLock(payload.lock ?? null)
        setLastSavedAt(new Date(payload.updatedAt))
        setAutoSaveStatus('saved')
        setAutoSaveError(null)
      } catch (error: any) {
        console.error('Failed to save draft', error)
        setAutoSaveStatus('error')
        setAutoSaveError(error?.message || 'Failed to save draft')
      }
    },
    [currentUserId, draftStorageKey, isEditing, isLocked, user?.fullName],
  )

  useEffect(() => {
    if (isEditing || typeof window === 'undefined') {
      return
    }
    if (isLocked) return
    const timeout = setTimeout(() => {
      saveDraftSnapshot(watchedValues as SalesInvoiceFormValues, lines)
    }, AUTO_SAVE_DEBOUNCE_MS)
    return () => clearTimeout(timeout)
  }, [isEditing, isLocked, lines, saveDraftSnapshot, watchedValues])

  const claimLock = useCallback(() => {
    if (typeof window === 'undefined' || isEditing) return
    const lock = createDraftLock(currentUserId, user?.fullName)
    const currentValues = watchedValues as SalesInvoiceFormValues
    const payload: SalesInvoiceDraftPayload = {
      version: DRAFT_VERSION,
      header: currentValues,
      lines,
      updatedAt: new Date().toISOString(),
      lock,
    }
    writeSalesInvoiceDraft(draftStorageKey, payload)
    setDraftLock(lock)
    setIsLocked(false)
  }, [currentUserId, draftStorageKey, isEditing, lines, user?.fullName, watchedValues])

  useEffect(() => {
    if (isEditing || typeof window === 'undefined') return
    if (isLocked) return
    claimLock()
  }, [claimLock, isEditing, isLocked])

  useEffect(() => {
    if (!isEditing || !invoiceId || loadingAccounts) return
    let mounted = true
    setLoadingInvoice(true)
    getSalesInvoiceById(invoiceId)
      .then((invoice) => {
        if (!mounted) return
        form.reset({
          customerId: invoice.customerId,
          invoiceNumber: invoice.invoiceNumber,
          invoiceDate: invoice.invoiceDate,
          dueDate: invoice.dueDate,
          reference: invoice.reference,
          description: invoice.description || '',
        })
        setSelectedCustomer({
          id: invoice.customerId,
          code: invoice.customerCode || '',
          name: invoice.customerName || '',
          companyId: invoice.companyId,
          active: true,
          createdAt: invoice.createdAt,
          updatedAt: invoice.updatedAt,
        })
        const convertedLines: SalesInvoiceLine[] = invoice.lines.map((line) => ({
          id: `line-${line.lineNumber}-${Date.now()}`,
          lineNumber: line.lineNumber,
          account: accounts.find((a) => a.id === String(line.accountId)) || null,
          description: line.description,
          quantity: line.quantity ?? null,
          unitPrice: line.unitPrice ?? null,
          amount: line.amount,
          vatRate: line.vatRate || 'ZERO',
          vatAmount: line.vatAmount ?? null,
          itemId: line.itemId?.toString() ?? null,
          item: line.itemId ? { id: String(line.itemId), name: `Item #${line.itemId}` } : null,
          status: 'clean',
        }))
        resetLines(convertedLines.length > 0 ? convertedLines : createInitialLines())
        setEditingInvoice(invoice)
        setValidationMap({})
        setHeaderErrors({})
      })
      .catch((error) => {
        console.error(error)
        toast.error('Failed to load sales invoice')
        navigate('/sales-invoices')
      })
      .finally(() => {
        if (mounted) setLoadingInvoice(false)
      })
    return () => {
      mounted = false
    }
  }, [accounts, invoiceId, form, isEditing, loadingAccounts, navigate, resetLines])

  // Load active approval workflow ID for pending-approval invoices
  useEffect(() => {
    if (!invoiceId || editingInvoice?.status !== 'PENDING_APPROVAL') {
      setActiveWorkflowId(null)
      return
    }

    let cancelled = false

    async function loadActiveWorkflow() {
      try {
        const workflows = await getPendingApprovals()
        if (cancelled) return
        const workflow = workflows.find(
          (w) => w.salesInvoiceId === invoiceId && w.status === 'PENDING',
        )
        setActiveWorkflowId(workflow ? workflow.id : null)
      } catch (error: any) {
        if (!cancelled) {
          toast.error('Failed to load approval workflow', {
            description: error?.message,
          })
        }
      }
    }

    loadActiveWorkflow()

    return () => {
      cancelled = true
    }
  }, [invoiceId, editingInvoice?.status])

  const runServerValidation = useCallback(
    async (silent = true) => {
      if (!invoiceId) {
        if (!silent) {
          toast.info('Save the invoice before running server-side validation.')
        }
        return true
      }

      try {
        setValidating(true)
        const currentValues = form.getValues()
        const payload = buildRequest(currentValues)
        const result = await validateSalesInvoice(invoiceId, payload)
        setValidationMap(result.lineErrors || {})
        setHeaderErrors(result.headerErrors || {})
        if (!silent) {
          if (result.valid) {
            toast.success('All fields are valid')
          } else {
            toast.warning('Some fields need attention')
          }
        }
        return result.valid
      } catch (err: any) {
        if (!silent) {
          toast.error('Failed to validate sales invoice', { description: err?.message })
        }
        return false
      } finally {
        setValidating(false)
      }
    },
    [invoiceId, form],
  )

  async function handleValidate(_values: SalesInvoiceFormValues) {
    await runServerValidation(false)
  }

  async function handleSave(values: SalesInvoiceFormValues) {
    try {
      setSaving(true)
      const payload = buildRequest(values)
      const response =
        isEditing && invoiceId
          ? await updateSalesInvoice(invoiceId, payload)
          : await createSalesInvoice(payload)
      toast.success(isEditing ? 'Sales invoice updated' : 'Sales invoice created', {
        description: `Invoice Number: ${response.invoiceNumber}`,
      })
      if (!isEditing) {
        removeSalesInvoiceDraft(draftStorageKey)
        setLastSavedAt(null)
      }
      setValidationMap({})
      setHeaderErrors({})
      if (isEditing && invoiceId) {
        const updated = await getSalesInvoiceById(invoiceId)
        setEditingInvoice(updated)
      } else {
        navigate(`/sales-invoices/${response.id}`)
      }
    } catch (err: any) {
      toast.error('Failed to save sales invoice', { description: err?.message })
    } finally {
      setSaving(false)
    }
  }

  async function handleSubmitForApproval() {
    if (!invoiceId) return

    if (hasBlockingVatIssues) {
      toast.error('Resolve VAT issues before submitting for approval.')
      return
    }

    setSubmittingForApproval(true)
    try {
      const isValid = await runServerValidation(false)
      if (!isValid) {
        toast.error('Fix validation issues before submitting.')
        return
      }
      await submitForApproval(invoiceId)
      toast.success('Sales invoice submitted for approval', {
        description: 'Chief Accountant/CFO will be notified',
      })
      // Reload invoice to update status
      const updated = await getSalesInvoiceById(invoiceId)
      setEditingInvoice(updated)
    } catch (err: any) {
      toast.error('Failed to submit for approval', { description: err?.message })
    } finally {
      setSubmittingForApproval(false)
    }
  }

  async function handleApprovalSuccess() {
    // Reload invoice after approval/rejection
    if (invoiceId) {
      const updated = await getSalesInvoiceById(invoiceId)
      setEditingInvoice(updated)
    }
  }

  const totalAmount = useMemo(() => {
    return lines.reduce((sum, line) => sum + (line.amount ?? 0), 0)
  }, [lines])

  const totalVAT = useMemo(() => {
    return lines.reduce((sum, line) => sum + (line.vatAmount ?? 0), 0)
  }, [lines])

  const isReadOnly = editingInvoice?.status === 'POSTED' || editingInvoice?.status === 'PAID'
  const canApprove = canApproveVouchers()
  const attachmentCount = editingInvoice?.attachmentCount ?? 0

  if (loadingInvoice || loadingAccounts) {
    return (
      <div className="container mx-auto py-6 space-y-6">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-96 w-full" />
      </div>
    )
  }

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={() => navigate('/sales-invoices')}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-3xl font-bold tracking-tight">
                {isEditing ? 'Edit Sales Invoice' : 'Create Sales Invoice'}
              </h1>
              {attachmentCount > 0 && (
                <Badge variant="outline" className="text-sm">
                  <FileText className="mr-1 h-3 w-3" />
                  {attachmentCount} attachment{attachmentCount !== 1 ? 's' : ''}
                </Badge>
              )}
            </div>
            <p className="text-muted-foreground">
              {isEditing
                ? `Invoice Number: ${editingInvoice?.invoiceNumber || ''}`
                : 'Enter sales invoice details and line items'}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {!isEditing && (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              {autoSaveStatus === 'saving' && (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span>Saving...</span>
                </>
              )}
              {autoSaveStatus === 'saved' && lastSavedAt && (
                <>
                  <CheckCircle className="h-4 w-4 text-green-500" />
                  <span>Saved {formatDistanceToNow(lastSavedAt, { addSuffix: true })}</span>
                </>
              )}
              {autoSaveStatus === 'error' && (
                <>
                  <XCircle className="h-4 w-4 text-destructive" />
                  <span>Save failed</span>
                </>
              )}
            </div>
          )}
          {/* Create Credit Note button for POSTED invoices (AC-VAT-004) */}
          {isEditing && editingInvoice?.status === 'POSTED' && (
            <Button
              variant="outline"
              onClick={() => navigate(`/sales-invoices/${invoiceId}/credit-note`)}
            >
              <FileX className="mr-2 h-4 w-4" />
              Create Credit Note
            </Button>
          )}
          {canUndo && (
            <Button variant="outline" size="sm" onClick={undo}>
              <RotateCcw className="mr-2 h-4 w-4" />
              Undo
            </Button>
          )}
          {canRedo && (
            <Button variant="outline" size="sm" onClick={redo}>
              <RotateCcw className="mr-2 h-4 w-4 rotate-180" />
              Redo
            </Button>
          )}
          {/* Attachments management for sales invoices will be implemented in a future story */}
        </div>
      </div>

      {isLocked && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Draft is locked</AlertTitle>
          <AlertDescription>
            This draft is being edited by {draftLock?.ownerName || 'another user'}. You can claim
            the lock to continue editing.
          </AlertDescription>
        </Alert>
      )}

      <Form {...form}>
        <form onSubmit={form.handleSubmit(handleSave)} className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Invoice Information</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2">
                <FormField
                  control={form.control}
                  name="customerId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Customer <span className="text-destructive">*</span>
                      </FormLabel>
                      <FormControl>
                        <CustomerPicker
                          value={selectedCustomer}
                          onChange={(customer) => {
                            setSelectedCustomer(customer)
                            field.onChange(customer?.id || 0)
                          }}
                          disabled={isReadOnly}
                          error={headerErrors.customerId?.[0]}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="invoiceNumber"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Invoice Number <span className="text-destructive">*</span>
                      </FormLabel>
                      <FormControl>
                        <Input
                          {...field}
                          disabled={isReadOnly}
                          placeholder="Enter invoice number"
                          className={headerErrors.invoiceNumber ? 'border-destructive' : ''}
                        />
                      </FormControl>
                      <FormMessage />
                      {headerErrors.invoiceNumber && (
                        <FormDescription className="text-destructive">
                          {headerErrors.invoiceNumber[0]}
                        </FormDescription>
                      )}
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="invoiceDate"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Invoice Date <span className="text-destructive">*</span>
                      </FormLabel>
                      <FormControl>
                        <Popover>
                          <PopoverTrigger asChild>
                            <Button
                              variant="outline"
                              className={cn(
                                'w-full justify-start text-left font-normal',
                                !field.value && 'text-muted-foreground',
                                headerErrors.invoiceDate && 'border-destructive',
                              )}
                              disabled={isReadOnly}
                            >
                              <CalendarIcon className="mr-2 h-4 w-4" />
                              {field.value ? format(parseISO(field.value), 'PPP') : 'Pick a date'}
                            </Button>
                          </PopoverTrigger>
                          <PopoverContent className="w-auto p-0" align="start">
                            <Calendar
                              mode="single"
                              selected={selectedDate || undefined}
                              onSelect={(date) => {
                                if (date) {
                                  field.onChange(format(date, 'yyyy-MM-dd'))
                                }
                              }}
                              disabled={(date) => isAfter(date, today)}
                              month={calendarMonth}
                              onMonthChange={setCalendarMonth}
                              initialFocus
                            />
                          </PopoverContent>
                        </Popover>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="dueDate"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Due Date <span className="text-destructive">*</span>
                      </FormLabel>
                      <FormControl>
                        <Popover>
                          <PopoverTrigger asChild>
                            <Button
                              variant="outline"
                              className={cn(
                                'w-full justify-start text-left font-normal',
                                !field.value && 'text-muted-foreground',
                                headerErrors.dueDate && 'border-destructive',
                              )}
                              disabled={
                                isReadOnly || (isEditing && editingInvoice?.status !== 'DRAFT')
                              }
                            >
                              <CalendarIcon className="mr-2 h-4 w-4" />
                              {field.value ? format(parseISO(field.value), 'PPP') : 'Pick a date'}
                            </Button>
                          </PopoverTrigger>
                          <PopoverContent className="w-auto p-0" align="start">
                            <Calendar
                              mode="single"
                              selected={field.value ? parseISO(field.value) : undefined}
                              onSelect={(date) => {
                                if (date) {
                                  field.onChange(format(date, 'yyyy-MM-dd'))
                                }
                              }}
                              disabled={(date) =>
                                isBefore(
                                  date,
                                  parseISO(
                                    watchedValues.invoiceDate || format(today, 'yyyy-MM-dd'),
                                  ),
                                )
                              }
                              initialFocus
                            />
                          </PopoverContent>
                        </Popover>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="reference"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Reference <span className="text-destructive">*</span>
                      </FormLabel>
                      <FormControl>
                        <Input
                          {...field}
                          disabled={isReadOnly}
                          placeholder="Enter reference"
                          maxLength={100}
                          className={headerErrors.reference ? 'border-destructive' : ''}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                {editingInvoice && (
                  <FormItem>
                    <FormLabel>Status</FormLabel>
                    <FormControl>
                      <Badge variant="outline">{editingInvoice.status}</Badge>
                    </FormControl>
                  </FormItem>
                )}
              </div>
              <FormField
                control={form.control}
                name="description"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Description</FormLabel>
                    <FormControl>
                      <Textarea
                        {...field}
                        disabled={isReadOnly}
                        placeholder="Enter description (optional)"
                        maxLength={500}
                        rows={3}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Line Items</CardTitle>
            </CardHeader>
            <CardContent>
              <SalesInvoiceLineGrid
                accounts={accounts}
                lines={lines}
                onLinesChange={setLines}
                validationMap={validationMap}
                readOnly={isReadOnly}
                loading={loadingAccounts}
                onCalculateVAT={calculateVAT}
                defaultVatRate={COMPANY_DEFAULT_VAT_RATE}
              />
            </CardContent>
          </Card>

          {/* VAT Corrections section - disabled for sales invoices (feature only available for purchase bills)
          TODO: Implement AR-specific VAT corrections API and enable this */}
          {/* eslint-disable-next-line no-constant-binary-expression */}
          {false && isEditing && invoiceId && (
            <Card>
              <CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                <div>
                  <CardTitle>VAT Corrections</CardTitle>
                  <p className="text-sm text-muted-foreground">
                    Track manual VAT adjustments for this invoice. Pending corrections require CFO /
                    Chief Accountant approval.
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    onClick={loadVatCorrections}
                    disabled={vatCorrectionsLoading}
                  >
                    <RefreshCw
                      className={`mr-2 h-4 w-4 ${vatCorrectionsLoading ? 'animate-spin' : ''}`}
                    />
                    Refresh
                  </Button>
                  <Button variant="default" onClick={() => setCorrectionDialogOpen(true)}>
                    New Correction
                  </Button>
                </div>
              </CardHeader>
              <CardContent>
                <div className="rounded-md border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Line</TableHead>
                        <TableHead>Reason</TableHead>
                        <TableHead className="text-right">Old VAT</TableHead>
                        <TableHead className="text-right">New VAT</TableHead>
                        <TableHead className="text-right">Diff</TableHead>
                        <TableHead>Corrected By</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead className="text-right">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {vatCorrectionsLoading ? (
                        <TableRow>
                          <TableCell colSpan={8}>
                            <Skeleton className="h-6 w-full" />
                          </TableCell>
                        </TableRow>
                      ) : vatCorrections.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={8} className="text-center py-6 text-muted-foreground">
                            No VAT corrections submitted yet.
                          </TableCell>
                        </TableRow>
                      ) : (
                        vatCorrections.map((correction) => (
                          <TableRow key={correction.id}>
                            <TableCell className="font-mono text-xs">
                              {correction.purchaseBillLineId ?? 'Invoice total'}
                            </TableCell>
                            <TableCell
                              className="max-w-xs truncate text-sm"
                              title={correction.reason}
                            >
                              {correction.reason}
                            </TableCell>
                            <TableCell className="text-right font-mono text-sm">
                              {correction.oldVatAmount}
                            </TableCell>
                            <TableCell className="text-right font-mono text-sm">
                              {correction.newVatAmount}
                            </TableCell>
                            <TableCell className="text-right font-mono text-sm">
                              {correction.difference}
                            </TableCell>
                            <TableCell className="text-sm">
                              <div>User #{correction.correctedById}</div>
                              <div className="text-xs text-muted-foreground">
                                {format(new Date(correction.correctedAt), 'dd/MM/yyyy HH:mm')}
                              </div>
                            </TableCell>
                            <TableCell>
                              <Badge
                                variant={
                                  correction.status === 'APPROVED'
                                    ? 'default'
                                    : correction.status === 'REJECTED'
                                      ? 'destructive'
                                      : 'outline'
                                }
                              >
                                {correction.status}
                              </Badge>
                            </TableCell>
                            <TableCell className="text-right">
                              {correction.status === 'PENDING' && (
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => {
                                    setSelectedCorrection(correction)
                                    setApproveCorrectionDialogOpen(true)
                                  }}
                                >
                                  Approve
                                </Button>
                              )}
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </div>
              </CardContent>
            </Card>
          )}

          {/* Attachments upload for sales invoices will be implemented in a future story */}

          <Card>
            <CardHeader>
              <CardTitle>Summary</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                {/* VAT Totals Summary (AC-VAT-003) */}
                <div className="rounded-md border bg-muted/50 p-4">
                  <div className="text-sm font-medium mb-2">VAT Totals Validation</div>
                  <div className="grid grid-cols-3 gap-4 text-sm">
                    <div>
                      <div className="text-muted-foreground">Header VAT</div>
                      <div className="font-mono font-semibold">
                        {new Intl.NumberFormat('vi-VN', {
                          style: 'currency',
                          currency: 'VND',
                          minimumFractionDigits: 0,
                          maximumFractionDigits: 0,
                        }).format(vatTotals.headerVAT)}
                      </div>
                    </div>
                    <div>
                      <div className="text-muted-foreground">Sum of Line VAT</div>
                      <div className="font-mono font-semibold">
                        {new Intl.NumberFormat('vi-VN', {
                          style: 'currency',
                          currency: 'VND',
                          minimumFractionDigits: 0,
                          maximumFractionDigits: 0,
                        }).format(vatTotals.sumOfLineVAT)}
                      </div>
                    </div>
                    <div>
                      <div className="text-muted-foreground">Variance</div>
                      <div
                        className={cn(
                          'font-mono font-semibold',
                          vatTotals.variance >= VAT_SUM_TOLERANCE
                            ? 'text-destructive'
                            : vatTotals.variance > 0
                              ? 'text-amber-600'
                              : 'text-green-600',
                        )}
                      >
                        {new Intl.NumberFormat('vi-VN', {
                          style: 'currency',
                          currency: 'VND',
                          minimumFractionDigits: 0,
                          maximumFractionDigits: 0,
                        }).format(vatTotals.variance)}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Invoice Totals */}
                <div className="flex justify-end gap-8">
                  <div className="text-right">
                    <div className="text-sm text-muted-foreground">Total Amount</div>
                    <div className="text-lg font-semibold">
                      {new Intl.NumberFormat('vi-VN', {
                        style: 'currency',
                        currency: 'VND',
                        minimumFractionDigits: 0,
                        maximumFractionDigits: 0,
                      }).format(totalAmount)}
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-sm text-muted-foreground">Total VAT</div>
                    <div className="text-lg font-semibold">
                      {new Intl.NumberFormat('vi-VN', {
                        style: 'currency',
                        currency: 'VND',
                        minimumFractionDigits: 0,
                        maximumFractionDigits: 0,
                      }).format(totalVAT)}
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-sm text-muted-foreground">Grand Total</div>
                    <div className="text-xl font-bold">
                      {new Intl.NumberFormat('vi-VN', {
                        style: 'currency',
                        currency: 'VND',
                        minimumFractionDigits: 0,
                        maximumFractionDigits: 0,
                      }).format(totalAmount + totalVAT)}
                    </div>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          {vatIssues.length > 0 && (
            <Alert variant={hasBlockingVatIssues ? 'destructive' : 'default'}>
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>VAT {hasBlockingVatIssues ? 'Issues' : 'Warnings'}</AlertTitle>
              <AlertDescription>
                <ul className="list-disc list-inside">
                  {vatIssues.map((issue, idx) => (
                    <li
                      key={`vat-issue-${idx}`}
                      className={issue.severity === 'error' ? 'text-red-600' : undefined}
                    >
                      {issue.message}
                    </li>
                  ))}
                </ul>
              </AlertDescription>
            </Alert>
          )}

          {Object.keys(headerErrors).length > 0 && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>Validation Errors</AlertTitle>
              <AlertDescription>
                <ul className="list-disc list-inside">
                  {Object.entries(headerErrors).map(([field, errors]) => (
                    <li key={field}>
                      <strong>{field}:</strong> {errors.join(', ')}
                    </li>
                  ))}
                </ul>
              </AlertDescription>
            </Alert>
          )}

          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => navigate('/sales-invoices')}
              disabled={saving}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => handleValidate(form.getValues())}
              disabled={saving || validating}
            >
              {validating ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Validating...
                </>
              ) : (
                'Validate'
              )}
            </Button>
            <Button type="submit" disabled={saving || isReadOnly}>
              {saving ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save className="mr-2 h-4 w-4" />
                  {isEditing ? 'Update' : 'Save'}
                </>
              )}
            </Button>

            {/* Submit for Approval button - only show for DRAFT invoices */}
            {isEditing && editingInvoice?.status === 'DRAFT' && (
              <Button
                type="button"
                variant="default"
                onClick={handleSubmitForApproval}
                disabled={submittingForApproval || hasBlockingVatIssues}
                title={
                  hasBlockingVatIssues
                    ? 'Resolve VAT issues before submitting for approval'
                    : undefined
                }
              >
                {submittingForApproval ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Submitting...
                  </>
                ) : (
                  <>
                    <Send className="mr-2 h-4 w-4" />
                    Submit for Approval
                  </>
                )}
              </Button>
            )}

            {/* Approve/Reject buttons - only show for PENDING_APPROVAL invoices and approver roles */}
            {isEditing &&
              editingInvoice?.status === 'PENDING_APPROVAL' &&
              canApprove &&
              activeWorkflowId && (
                <>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setApprovalDialogAction('reject')}
                    className="border-red-200 text-red-600 hover:bg-red-50"
                  >
                    <XCircle className="mr-2 h-4 w-4" />
                    Reject
                  </Button>
                  <Button
                    type="button"
                    variant="default"
                    onClick={() => setApprovalDialogAction('approve')}
                    className="bg-green-600 hover:bg-green-700"
                  >
                    <CheckCircle className="mr-2 h-4 w-4" />
                    Approve
                  </Button>
                </>
              )}
          </div>
        </form>
      </Form>

      {/* VAT Correction Dialogs - disabled for sales invoices (feature only available for purchase bills)
      TODO: Implement AR-specific VAT corrections API and enable this */}
      {/* eslint-disable-next-line no-constant-binary-expression */}
      {false && invoiceId && (
        <>
          <VATCorrectionDialog
            open={correctionDialogOpen}
            onOpenChange={setCorrectionDialogOpen}
            defaultBillId={invoiceId}
            onCreated={() => {
              loadVatCorrections()
            }}
          />
          <ApproveVATCorrectionDialog
            open={approveCorrectionDialogOpen}
            onOpenChange={(open) => {
              if (!open) {
                setSelectedCorrection(null)
              }
              setApproveCorrectionDialogOpen(open)
            }}
            correction={selectedCorrection}
            onApproved={() => {
              setSelectedCorrection(null)
              loadVatCorrections()
            }}
          />
        </>
      )}

      {/* Approval Workflow History - show for all editing invoices */}
      {isEditing && invoiceId && (
        <div className="mt-6">
          <SalesInvoiceApprovalHistory invoiceId={invoiceId} />
        </div>
      )}

      {/* Approval Decision Dialog */}
      {invoiceId && editingInvoice && approvalDialogAction && activeWorkflowId && (
        <SalesInvoiceApprovalDialog
          workflowId={activeWorkflowId}
          invoiceNumber={editingInvoice.invoiceNumber || ''}
          action={approvalDialogAction}
          open={approvalDialogAction !== null}
          onOpenChange={(open) => !open && setApprovalDialogAction(null)}
          onSuccess={handleApprovalSuccess}
        />
      )}

      {/* Attachment Management Modal for sales invoices will be implemented in a future story */}
    </div>
  )
}
