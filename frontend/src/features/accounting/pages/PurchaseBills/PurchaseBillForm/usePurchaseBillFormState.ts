import { useCallback, useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { format, addDays, parseISO } from 'date-fns'
import { useParams, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { toast } from 'sonner'

import { type PurchaseBillLine } from '@/components/purchase/PurchaseBillLineGrid'
import type { AccountSummary } from '@/components/account/AccountPicker'
import useUndoRedo from '@/hooks/useUndoRedo'
import { useAuth } from '@/hooks/useAuth'
import { getCompanyId } from '@/utils/axios'
import type { PurchaseBillDTO, PurchaseBillCreateRequest, VatRate } from '@/types/purchaseBill'
import {
  createPurchaseBill,
  validatePurchaseBill,
  updatePurchaseBill,
  getPurchaseBillById,
  submitForApproval,
} from '@/services/purchaseBill'
import { getPostableAccounts } from '@/services/chartOfAccounts'
import type { ChartOfAccount } from '@/types/chartOfAccount'
import type { Supplier } from '@/types/supplier'
import { vatService } from '@/services/vat'
import type { VATCorrectionDTO } from '@/types/vat'

export const formSchema = z.object({
  supplierId: z.number({ message: 'Supplier is required' }),
  billNumber: z.string().min(1, 'Bill number is required'),
  billDate: z.string({ message: 'Bill date is required' }),
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

export type PurchaseBillFormValues = z.infer<typeof formSchema>

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

export type VatIssue = {
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
      itemId: null,
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

export function calculateVAT(amount: number, rate: VatRate): number {
  const rateValue = rate === 'FIVE' ? 0.05 : rate === 'TEN' ? 0.1 : rate === 'EXEMPT' ? 0 : 0
  return amount * rateValue
}

export function calculateDueDate(
  billDate: string,
  paymentTermsDays: number = DEFAULT_PAYMENT_TERMS_DAYS,
): string {
  const date = parseISO(billDate)
  const dueDate = addDays(date, paymentTermsDays)
  return format(dueDate, 'yyyy-MM-dd')
}

export function usePurchaseBillFormState() {
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

  // Approval states
  const [submittingForApproval, setSubmittingForApproval] = useState(false)

  // VAT Corrections states
  const [vatCorrections, setVatCorrections] = useState<VATCorrectionDTO[]>([])
  const [vatCorrectionsLoading, setVatCorrectionsLoading] = useState(false)

  const form = useForm<PurchaseBillFormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: initialState.values,
  })

  const watchedValues = useWatch({ control: form.control })

  // Auto-calculate due date when bill date changes
  useEffect(() => {
    if (watchedValues?.billDate && !isEditing) {
      const dueDate = calculateDueDate(watchedValues.billDate)
      form.setValue('dueDate', dueDate)
    }
  }, [watchedValues?.billDate, form, isEditing])

  // Load accounts
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

  // Load VAT corrections
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

  useEffect(() => {
    if (!billId) return
    loadVatCorrections()
  }, [billId, loadVatCorrections])

  // Derived state: linePayload
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

  // Derived state: vatIssues
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

  // Helper to build request payload
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

  // Draft saving logic
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

  // Load existing bill
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
          itemId: line.itemId?.toString() ?? null,
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
    [billId, form, editingBill, lines, linePayload],
  )

  async function handleValidate() {
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

  const totalAmount = useMemo(() => {
    return lines.reduce((sum, line) => sum + (line.amount ?? 0), 0)
  }, [lines])

  const totalVAT = useMemo(() => {
    return lines.reduce((sum, line) => sum + (line.vatAmount ?? 0), 0)
  }, [lines])

  return {
    // Form and State
    form,
    billId,
    isEditing,
    isLoading: loadingBill || loadingAccounts,
    isReadOnly: editingBill?.status === 'POSTED' || editingBill?.status === 'PAID',
    isLocked,
    draftLock,
    autoSaveStatus,
    lastSavedAt,
    autoSaveError,

    // Data
    accounts,
    lines,
    selectedSupplier,
    editingBill,
    vatCorrections,
    vatCorrectionsLoading,
    attachmentCount,
    setAttachmentCount,

    // Calculated Values
    totalAmount,
    totalVAT,
    vatIssues,
    hasBlockingVatIssues,

    // Validation
    headerErrors,
    validationMap,
    validating,
    saving,
    submittingForApproval,

    // Actions
    setLines,
    setSelectedSupplier,
    handleSave,
    handleValidate,
    handleSubmitForApproval,
    loadVatCorrections,
    setEditingBill, // Exposed to update local state after approval/rejection

    // Undo/Redo
    undo,
    redo,
    canUndo,
    canRedo,
  }
}
