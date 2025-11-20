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
  Plus,
  Send,
} from 'lucide-react'
import { useParams, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { toast } from 'sonner'

import {
  PurchaseBillLineGrid,
  type PurchaseBillLine,
} from '@/components/purchase/PurchaseBillLineGrid'
import { SupplierPicker } from '@/components/purchase/SupplierPicker'
import {
  PurchaseBillAttachmentDropzone,
  PurchaseBillAttachmentManagementModal,
  ApprovalDecisionDialog,
  ApprovalWorkflowHistory,
} from '@/components/purchase'
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
import { Separator } from '@/components/ui/separator'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { cn } from '@/lib/utils'

import type { AccountSummary } from '@/components/account/AccountPicker'
import useUndoRedo from '@/hooks/useUndoRedo'
import { useAuth } from '@/hooks/useAuth'
import { getCompanyId } from '@/utils/axios'
import type {
  PurchaseBillDTO,
  PurchaseBillCreateRequest,
  PurchaseBillStatus,
  PurchaseBillValidationResult,
  VatRate,
} from '@/types/purchaseBill'
import {
  createPurchaseBill,
  validatePurchaseBill,
  updatePurchaseBill,
  getPurchaseBillById,
  saveDraft,
  submitForApproval,
} from '@/services/purchaseBill'
import { getPostableAccounts } from '@/services/chartOfAccounts'
import type { ChartOfAccount } from '@/types/chartOfAccount'
import type { Supplier } from '@/types/supplier'
import { vatService } from '@/services/vat'
import type { VATCorrectionDTO } from '@/types/vat'
import { VATCorrectionDialog } from '../VATReports/VATCorrectionDialog'
import { ApproveVATCorrectionDialog } from '../VATReports/ApproveVATCorrectionDialog'

const formSchema = z.object({
  supplierId: z.number({ required_error: 'Supplier is required' }),
  billNumber: z.string().min(1, 'Bill number is required'),
  billDate: z.string({ required_error: 'Bill date is required' }),
  dueDate: z.string({ required_error: 'Due date is required' }),
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

type PurchaseBillFormValues = z.infer<typeof formSchema>

type DraftLock = {
  ownerId: string
  ownerName?: string
  expiresAt: string
}

type PurchaseBillDraftPayload = {
  version: number
  header: PurchaseBillFormValues
  lines: PurchaseBillLine[]
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

function createInitialLines(): PurchaseBillLine[] {
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
      costCenterId: null,
      itemId: null,
      costCenter: null,
      item: null,
      status: 'clean',
    },
  ]
}

function buildDraftKey(companyId: number | null, billId?: string) {
  const companyPart = companyId ?? 'default'
  const billPart = billId ?? 'new'
  return `purchaseBillDraft:${companyPart}:${billPart}`
}

function readPurchaseBillDraft(key: string): PurchaseBillDraftPayload | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const parsed = JSON.parse(raw) as PurchaseBillDraftPayload
    if (parsed.version !== DRAFT_VERSION) return null
    return parsed
  } catch {
    return null
  }
}

function writePurchaseBillDraft(key: string, payload: PurchaseBillDraftPayload) {
  if (typeof window === 'undefined') return
  localStorage.setItem(key, JSON.stringify(payload))
}

function removePurchaseBillDraft(key: string) {
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
  billDate: string,
  paymentTermsDays: number = DEFAULT_PAYMENT_TERMS_DAYS,
): string {
  const date = parseISO(billDate)
  const dueDate = addDays(date, paymentTermsDays)
  return format(dueDate, 'yyyy-MM-dd')
}

export default function PurchaseBillForm() {
  const params = useParams<{ billId?: string }>()
  const navigate = useNavigate()
  const billId = params.billId && params.billId !== 'new' ? params.billId : undefined
  const isEditing = Boolean(billId)
  const { user } = useAuth()
  const currentUserId = user?.id ? String(user.id) : 'anonymous'
  const companyId = getCompanyId()
  const draftStorageKey = buildDraftKey(companyId, billId)
  const [accounts, setAccounts] = useState<AccountSummary[]>([])
  const [selectedSupplier, setSelectedSupplier] = useState<Supplier | null>(null)
  const [initialState] = useState(() => {
    if (typeof window === 'undefined') {
      return {
        values: {
          supplierId: 0,
          billNumber: '',
          billDate: format(new Date(), 'yyyy-MM-dd'),
          dueDate: calculateDueDate(format(new Date(), 'yyyy-MM-dd')),
          reference: '',
          description: '',
        },
        lines: createInitialLines(),
        lock: null as DraftLock | null,
        updatedAt: null as string | null,
      }
    }
    const stored = readPurchaseBillDraft(draftStorageKey)
    return {
      values: stored?.header ?? {
        supplierId: 0,
        billNumber: '',
        billDate: format(new Date(), 'yyyy-MM-dd'),
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
  } = useUndoRedo<PurchaseBillLine[]>(initialState.lines)
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
  const [loadingBill, setLoadingBill] = useState(false)
  const [editingBill, setEditingBill] = useState<PurchaseBillDTO | null>(null)
  const [autoSaveError, setAutoSaveError] = useState<string | null>(null)
  const [attachmentCount, setAttachmentCount] = useState(0)
  const [attachmentModalOpen, setAttachmentModalOpen] = useState(false)
  const [approvalDialogAction, setApprovalDialogAction] = useState<'approve' | 'reject' | null>(
    null,
  )
  const [submittingForApproval, setSubmittingForApproval] = useState(false)
  const [vatCorrections, setVatCorrections] = useState<VATCorrectionDTO[]>([])
  const [vatCorrectionsLoading, setVatCorrectionsLoading] = useState(false)
  const [correctionDialogOpen, setCorrectionDialogOpen] = useState(false)
  const [approveCorrectionDialogOpen, setApproveCorrectionDialogOpen] = useState(false)
  const [selectedCorrection, setSelectedCorrection] = useState<VATCorrectionDTO | null>(null)
  const today = useMemo(() => new Date(), [])

  const form = useForm<PurchaseBillFormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: initialState.values,
  })
  const watchedValues = useWatch({ control: form.control })
  const selectedDate = useMemo(() => {
    if (!watchedValues?.billDate) return null
    const parsed = new Date(watchedValues.billDate)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }, [watchedValues?.billDate])
  const [calendarMonth, setCalendarMonth] = useState<Date>(selectedDate ?? today)

  useEffect(() => {
    if (selectedDate) {
      setCalendarMonth(selectedDate)
    } else {
      setCalendarMonth(today)
    }
  }, [today, selectedDate])

  // Auto-calculate due date when bill date changes
  useEffect(() => {
    if (watchedValues?.billDate && !isEditing) {
      const dueDate = calculateDueDate(watchedValues.billDate)
      form.setValue('dueDate', dueDate)
    }
  }, [watchedValues?.billDate, form, isEditing])

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

  useEffect(() => {
    if (!billId) return
    loadVatCorrections()
  }, [billId, loadVatCorrections])

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
      costCenterId: line.costCenterId ? Number(line.costCenterId) : undefined,
      itemId: line.itemId ? Number(line.itemId) : undefined,
    }))
  }, [lines])

  const vatIssues = useMemo<VatIssue[]>(() => {
    const issues: VatIssue[] = []
    lines.forEach((line, index) => {
      const lineNumber = index + 1
      const baseAmount = typeof line.amount === 'number' ? line.amount : 0
      const vatAmount = typeof line.vatAmount === 'number' ? line.vatAmount : 0
      const rate = line.vatRate ?? 'ZERO'
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
    return issues
  }, [lines])

  const hasBlockingVatIssues = vatIssues.some((issue) => issue.severity === 'error')

  const loadVatCorrections = useCallback(async () => {
    if (!billId) return
    try {
      setVatCorrectionsLoading(true)
      const list = await vatService.listCorrections({ billId })
      setVatCorrections(list)
    } catch (error) {
      toast.error(`Failed to load VAT corrections: ${String(error)}`)
    } finally {
      setVatCorrectionsLoading(false)
    }
  }, [billId])

  function buildRequest(values: PurchaseBillFormValues): PurchaseBillCreateRequest {
    return {
      id: billId,
      supplierId: values.supplierId,
      billNumber: values.billNumber,
      billDate: values.billDate,
      dueDate: values.dueDate,
      reference: values.reference,
      description: values.description || null,
      status: editingBill?.status || 'DRAFT',
      lines: linePayload,
    }
  }

  const saveDraftSnapshot = useCallback(
    (values: PurchaseBillFormValues, lineItems: PurchaseBillLine[]) => {
      if (typeof window === 'undefined' || isLocked || isEditing) return
      try {
        setAutoSaveStatus('saving')
        const payload: PurchaseBillDraftPayload = {
          version: DRAFT_VERSION,
          header: values,
          lines: lineItems,
          updatedAt: new Date().toISOString(),
          lock: createDraftLock(currentUserId, user?.fullName),
        }
        writePurchaseBillDraft(draftStorageKey, payload)
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
      saveDraftSnapshot(watchedValues as PurchaseBillFormValues, lines)
    }, AUTO_SAVE_DEBOUNCE_MS)
    return () => clearTimeout(timeout)
  }, [isEditing, isLocked, lines, saveDraftSnapshot, watchedValues])

  const claimLock = useCallback(() => {
    if (typeof window === 'undefined' || isEditing) return
    const lock = createDraftLock(currentUserId, user?.fullName)
    const currentValues = watchedValues as PurchaseBillFormValues
    const payload: PurchaseBillDraftPayload = {
      version: DRAFT_VERSION,
      header: currentValues,
      lines,
      updatedAt: new Date().toISOString(),
      lock,
    }
    writePurchaseBillDraft(draftStorageKey, payload)
    setDraftLock(lock)
    setIsLocked(false)
  }, [currentUserId, draftStorageKey, isEditing, lines, user?.fullName, watchedValues])

  useEffect(() => {
    if (isEditing || typeof window === 'undefined') return
    if (isLocked) return
    claimLock()
  }, [claimLock, isEditing, isLocked])

  useEffect(() => {
    if (!isEditing || !billId || loadingAccounts) return
    let mounted = true
    setLoadingBill(true)
    getPurchaseBillById(billId)
      .then((bill) => {
        if (!mounted) return
        form.reset({
          supplierId: bill.supplierId,
          billNumber: bill.billNumber,
          billDate: bill.billDate,
          dueDate: bill.dueDate,
          reference: bill.reference,
          description: bill.description || '',
        })
        setSelectedSupplier({
          id: bill.supplierId,
          code: bill.supplierCode || '',
          name: bill.supplierName || '',
          companyId: bill.companyId,
          active: true,
          createdAt: bill.createdAt,
          updatedAt: bill.updatedAt,
        })
        const convertedLines: PurchaseBillLine[] = bill.lines.map((line) => ({
          id: `line-${line.lineNumber}-${Date.now()}`,
          lineNumber: line.lineNumber,
          account: accounts.find((a) => a.id === String(line.accountId)) || null,
          description: line.description,
          quantity: line.quantity ?? null,
          unitPrice: line.unitPrice ?? null,
          amount: line.amount,
          vatRate: line.vatRate || 'ZERO',
          vatAmount: line.vatAmount ?? null,
          costCenterId: line.costCenterId?.toString() ?? null,
          itemId: line.itemId?.toString() ?? null,
          costCenter: line.costCenterId
            ? { id: String(line.costCenterId), name: `Cost Center #${line.costCenterId}` }
            : null,
          item: line.itemId ? { id: String(line.itemId), name: `Item #${line.itemId}` } : null,
          status: 'clean',
        }))
        resetLines(convertedLines.length > 0 ? convertedLines : createInitialLines())
        setEditingBill(bill)
        setAttachmentCount(bill.attachmentCount || 0)
        setValidationMap({})
        setHeaderErrors({})
      })
      .catch((error) => {
        console.error(error)
        toast.error('Failed to load purchase bill')
        navigate('/purchase-bills')
      })
      .finally(() => {
        if (mounted) setLoadingBill(false)
      })
    return () => {
      mounted = false
    }
  }, [accounts, billId, form, isEditing, loadingAccounts, navigate, resetLines])

  const runServerValidation = useCallback(
    async (silent = true) => {
      if (!billId) {
        if (!silent) {
          toast.info('Save the bill before running server-side validation.')
        }
        return true
      }

      try {
        setValidating(true)
        const currentValues = form.getValues()
        const payload = buildRequest(currentValues)
        const result = await validatePurchaseBill(billId, payload)
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
          toast.error('Failed to validate purchase bill', { description: err?.message })
        }
        return false
      } finally {
        setValidating(false)
      }
    },
    [billId, form],
  )

  async function handleValidate(values: PurchaseBillFormValues) {
    await runServerValidation(false)
  }

  async function handleSave(values: PurchaseBillFormValues) {
    try {
      setSaving(true)
      const payload = buildRequest(values)
      const response =
        isEditing && billId
          ? await updatePurchaseBill(billId, payload)
          : await createPurchaseBill(payload)
      toast.success(isEditing ? 'Purchase bill updated' : 'Purchase bill created', {
        description: `Bill Number: ${response.billNumber}`,
      })
      if (!isEditing) {
        removePurchaseBillDraft(draftStorageKey)
        setLastSavedAt(null)
      }
      setValidationMap({})
      setHeaderErrors({})
      if (isEditing && billId) {
        const updated = await getPurchaseBillById(billId)
        setEditingBill(updated)
      } else {
        navigate(`/purchase-bills/${response.id}`)
      }
    } catch (err: any) {
      toast.error('Failed to save purchase bill', { description: err?.message })
    } finally {
      setSaving(false)
    }
  }

  async function handleSubmitForApproval() {
    if (!billId) return

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
      await submitForApproval(billId)
      toast.success('Purchase bill submitted for approval', {
        description: 'Chief Accountant/CFO will be notified',
      })
      // Reload bill to update status
      const updated = await getPurchaseBillById(billId)
      setEditingBill(updated)
    } catch (err: any) {
      toast.error('Failed to submit for approval', { description: err?.message })
    } finally {
      setSubmittingForApproval(false)
    }
  }

  async function handleApprovalSuccess() {
    // Reload bill after approval/rejection
    if (billId) {
      const updated = await getPurchaseBillById(billId)
      setEditingBill(updated)
    }
  }

  const totalAmount = useMemo(() => {
    return lines.reduce((sum, line) => sum + (line.amount ?? 0), 0)
  }, [lines])

  const totalVAT = useMemo(() => {
    return lines.reduce((sum, line) => sum + (line.vatAmount ?? 0), 0)
  }, [lines])

  const isReadOnly = editingBill?.status === 'POSTED' || editingBill?.status === 'PAID'

  if (loadingBill || loadingAccounts) {
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
          <Button variant="ghost" size="icon" onClick={() => navigate('/purchase-bills')}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-3xl font-bold tracking-tight">
                {isEditing ? 'Edit Purchase Bill' : 'Create Purchase Bill'}
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
                ? `Bill Number: ${editingBill?.billNumber || ''}`
                : 'Enter purchase bill details and line items'}
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
          {isEditing && billId && (
            <Button variant="outline" size="sm" onClick={() => setAttachmentModalOpen(true)}>
              <FileText className="mr-2 h-4 w-4" />
              Attachments {attachmentCount > 0 && `(${attachmentCount})`}
            </Button>
          )}
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
              <CardTitle>Bill Information</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2">
                <FormField
                  control={form.control}
                  name="supplierId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Supplier <span className="text-destructive">*</span>
                      </FormLabel>
                      <FormControl>
                        <SupplierPicker
                          value={selectedSupplier}
                          onChange={(supplier) => {
                            setSelectedSupplier(supplier)
                            field.onChange(supplier?.id || 0)
                          }}
                          disabled={isReadOnly}
                          error={headerErrors.supplierId?.[0]}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="billNumber"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Bill Number <span className="text-destructive">*</span>
                      </FormLabel>
                      <FormControl>
                        <Input
                          {...field}
                          disabled={isReadOnly}
                          placeholder="Enter bill number"
                          className={headerErrors.billNumber ? 'border-destructive' : ''}
                        />
                      </FormControl>
                      <FormMessage />
                      {headerErrors.billNumber && (
                        <FormDescription className="text-destructive">
                          {headerErrors.billNumber[0]}
                        </FormDescription>
                      )}
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="billDate"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Bill Date <span className="text-destructive">*</span>
                      </FormLabel>
                      <FormControl>
                        <Popover>
                          <PopoverTrigger asChild>
                            <Button
                              variant="outline"
                              className={cn(
                                'w-full justify-start text-left font-normal',
                                !field.value && 'text-muted-foreground',
                                headerErrors.billDate && 'border-destructive',
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
                                isReadOnly || (isEditing && editingBill?.status !== 'DRAFT')
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
                                  parseISO(watchedValues.billDate || format(today, 'yyyy-MM-dd')),
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
                {editingBill && (
                  <FormItem>
                    <FormLabel>Status</FormLabel>
                    <FormControl>
                      <Badge variant="outline">{editingBill.status}</Badge>
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
              <PurchaseBillLineGrid
                accounts={accounts}
                lines={lines}
                onLinesChange={setLines}
                validationMap={validationMap}
                readOnly={isReadOnly}
                loading={loadingAccounts}
                onCalculateVAT={calculateVAT}
              />
            </CardContent>
          </Card>

          {isEditing && billId && (
            <Card>
              <CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                <div>
                  <CardTitle>VAT Corrections</CardTitle>
                  <p className="text-sm text-muted-foreground">
                    Track manual VAT adjustments for this bill. Pending corrections require CFO /
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
                              {correction.purchaseBillLineId ?? 'Bill total'}
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

          {/* Attachments - only show if billId exists (after first save) */}
          {billId && (
            <Card>
              <CardHeader>
                <CardTitle>Attachments</CardTitle>
              </CardHeader>
              <CardContent>
                <PurchaseBillAttachmentDropzone
                  billId={billId}
                  disabled={isReadOnly}
                  maxSize={20 * 1024 * 1024} // 20MB
                  maxFiles={10}
                  existingCount={attachmentCount}
                  onUploadSuccess={() => {
                    setAttachmentCount((prev) => prev + 1)
                    toast.success('Attachment uploaded successfully')
                  }}
                  onUploadError={(file, error) => {
                    toast.error('Upload failed', { description: error })
                  }}
                />
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader>
              <CardTitle>Summary</CardTitle>
            </CardHeader>
            <CardContent>
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
              onClick={() => navigate('/purchase-bills')}
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

            {/* Submit for Approval button - only show for DRAFT bills */}
            {isEditing && editingBill?.status === 'DRAFT' && (
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

            {/* Approve/Reject buttons - only show for PENDING_APPROVAL bills */}
            {isEditing && editingBill?.status === 'PENDING_APPROVAL' && (
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

      {billId && (
        <>
          <VATCorrectionDialog
            open={correctionDialogOpen}
            onOpenChange={setCorrectionDialogOpen}
            defaultBillId={billId}
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

      {/* Approval Workflow History - show for all editing bills */}
      {isEditing && billId && (
        <div className="mt-6">
          <ApprovalWorkflowHistory billId={billId} />
        </div>
      )}

      {/* Approval Decision Dialog */}
      {billId && editingBill && (
        <ApprovalDecisionDialog
          billId={billId}
          billNumber={editingBill.billNumber || ''}
          action={approvalDialogAction}
          open={approvalDialogAction !== null}
          onOpenChange={(open) => !open && setApprovalDialogAction(null)}
          onSuccess={handleApprovalSuccess}
        />
      )}

      {/* Attachment Management Modal */}
      {isEditing && billId && (
        <PurchaseBillAttachmentManagementModal
          billId={billId}
          open={attachmentModalOpen}
          onOpenChange={setAttachmentModalOpen}
          canDelete={!isReadOnly && editingBill?.status === 'DRAFT'}
          onAttachmentDeleted={() => {
            setAttachmentCount((prev) => Math.max(0, prev - 1))
            // Reload bill to get updated attachment count
            if (billId) {
              getPurchaseBillById(billId)
                .then((bill) => {
                  setAttachmentCount(bill.attachmentCount || 0)
                })
                .catch(() => {
                  // Silently fail
                })
            }
          }}
        />
      )}
    </div>
  )
}
