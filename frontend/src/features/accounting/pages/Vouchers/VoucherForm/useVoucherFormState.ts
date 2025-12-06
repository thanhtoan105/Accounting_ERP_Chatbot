import { useCallback, useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import {
  addMinutes,
  endOfMonth,
  format,
  isAfter,
  isBefore,
  isSameDay,
  parseISO,
  startOfMonth,
} from 'date-fns'
import { useParams } from 'react-router-dom'
import { z } from 'zod'
import { toast } from 'sonner'

import type { AccountSummary } from '@/components/account/AccountPicker'
import type { VoucherEntryLine } from '@/components/voucher'
import type { AccountingPeriod } from '@/types/accountingPeriod'
import type {
  VoucherTemplateDTO,
  VoucherCreateRequest,
  VoucherDTO,
  VoucherLedgerLineDTO,
  VoucherDimensionOption,
} from '@/types/voucher'
import type { ChartOfAccount } from '@/types/chartOfAccount'
import useUndoRedo from '@/hooks/useUndoRedo'
import { useAuth } from '@/hooks/useAuth'
import { useRole } from '@/hooks/useRole'
import { useCompany } from '@/hooks/useCompany'
import { getCompanyId } from '@/utils/axios'
import {
  createVoucher,
  validateVoucher,
  updateVoucher,
  getVoucherById,
  applyVoucherTemplate,
  postVoucher,
  unpostVoucher,
  reverseVoucher,
} from '@/services/voucher'
import { getPostableAccounts } from '@/services/chartOfAccounts'

// ─────────────────────────────────────────────────────────────────────────────
// Schema & Types
// ─────────────────────────────────────────────────────────────────────────────

export const formSchema = z.object({
  voucherDate: z.string().min(1, 'Voucher date is required'),
  description: z
    .string()
    .min(1, 'Description is required')
    .max(500, 'Description can be up to 500 characters'),
})

export type VoucherFormValues = z.infer<typeof formSchema>

type DraftLock = {
  ownerId: string
  ownerName?: string
  expiresAt: string
}

type VoucherDraftPayload = {
  version: number
  header: VoucherFormValues
  lines: VoucherEntryLine[]
  updatedAt: string
  lock?: DraftLock | null
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const DRAFT_VERSION = 1
const AUTO_SAVE_DEBOUNCE_MS = 1000
const LOCK_DURATION_MINUTES = 5

// ─────────────────────────────────────────────────────────────────────────────
// Helper Functions
// ─────────────────────────────────────────────────────────────────────────────

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

function createInitialLines(): VoucherEntryLine[] {
  return [
    {
      id: `line-${Date.now()}`,
      debitAccount: null,
      creditAccount: null,
      amount: null,
      description: '',
      customerId: null,
      supplierId: null,
      costCenterId: null,
      customer: null,
      supplier: null,
      costCenter: null,
      source: 'manual',
      status: 'clean',
    },
  ]
}

function buildDraftKey(companyId: number | null, voucherId?: string) {
  const companyPart = companyId ?? 'default'
  const voucherPart = voucherId ?? 'new'
  return `voucherDraft:${companyPart}:${voucherPart}`
}

function readVoucherDraft(key: string): VoucherDraftPayload | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const parsed = JSON.parse(raw) as VoucherDraftPayload
    if (parsed.version !== DRAFT_VERSION) return null
    return parsed
  } catch {
    return null
  }
}

function writeVoucherDraft(key: string, payload: VoucherDraftPayload) {
  if (typeof window === 'undefined') return
  localStorage.setItem(key, JSON.stringify(payload))
}

function removeVoucherDraft(key: string) {
  if (typeof window === 'undefined') return
  localStorage.removeItem(key)
}

function createDraftLock(ownerId: string, ownerName?: string): DraftLock {
  return {
    ownerId,
    ownerName,
    expiresAt: addMinutes(new Date(), LOCK_DURATION_MINUTES).toISOString(),
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

function formatCountdown(ms: number) {
  if (ms <= 0) return '00:00'
  const totalSeconds = Math.floor(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function getAccountById(accounts: AccountSummary[], id?: string | number | null) {
  if (!id) return null
  return accounts.find((account) => account.id === String(id)) || null
}

function buildDimensionOption(
  id?: string | number | null,
  prefix?: string,
): VoucherDimensionOption | null {
  if (!id && id !== 0) return null
  const idStr = String(id)
  return {
    id: idStr,
    name: prefix ? `${prefix} #${idStr}` : idStr,
  }
}

function convertLedgerLinesToEntries(
  ledgerLines: VoucherLedgerLineDTO[] | undefined,
  accounts: AccountSummary[],
): VoucherEntryLine[] {
  if (!ledgerLines?.length) {
    return createInitialLines()
  }

  const sorted = [...ledgerLines].sort((a, b) => (a.lineNumber ?? 0) - (b.lineNumber ?? 0))
  const entries: VoucherEntryLine[] = []

  for (let i = 0; i < sorted.length; ) {
    const first = sorted[i]
    const second = sorted[i + 1]
    const debitLine =
      first?.debit && first.debit > 0
        ? first
        : second && second.debit && second.debit > 0
          ? second
          : first
    const creditLine =
      first?.credit && first.credit > 0
        ? first
        : second && second.credit && second.credit > 0
          ? second
          : (second ?? first)

    entries.push({
      id: `loaded-${i}`,
      debitAccount: getAccountById(accounts, debitLine?.accountId),
      creditAccount: getAccountById(accounts, creditLine?.accountId),
      amount: Number(debitLine?.debit ?? creditLine?.credit ?? 0),
      description: debitLine?.description || creditLine?.description || '',
      customerId: (debitLine?.customerId ?? creditLine?.customerId)?.toString() ?? null,
      supplierId: (debitLine?.vendorId ?? creditLine?.vendorId)?.toString() ?? null,
      costCenterId: (debitLine?.costCenterId ?? creditLine?.costCenterId)?.toString() ?? null,
      customer: buildDimensionOption(debitLine?.customerId ?? creditLine?.customerId, 'KH'),
      supplier: buildDimensionOption(debitLine?.vendorId ?? creditLine?.vendorId, 'NCC'),
      costCenter: buildDimensionOption(debitLine?.costCenterId ?? creditLine?.costCenterId, 'TTCP'),
      source: 'manual',
      status: 'clean',
    })

    i += 2
  }

  return entries.length ? entries : createInitialLines()
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Hook
// ─────────────────────────────────────────────────────────────────────────────

export function useVoucherFormState() {
  const params = useParams<{ voucherId?: string }>()
  const voucherId = params.voucherId && params.voucherId !== 'new' ? params.voucherId : undefined
  const isEditing = Boolean(voucherId)
  const { user } = useAuth()
  const { hasAnyRole } = useRole()
  const { company } = useCompany()
  const currentUserId = user?.id ? String(user.id) : 'anonymous'
  const companyId = getCompanyId()
  const draftStorageKey = buildDraftKey(companyId, voucherId)

  // ─── Account State ─────────────────────────────────────────────────────────
  const [accounts, setAccounts] = useState<AccountSummary[]>([])
  const [lockedAccountIds, setLockedAccountIds] = useState<string[]>([])
  const [loadingAccounts, setLoadingAccounts] = useState(true)

  // ─── Initial State from Draft ──────────────────────────────────────────────
  const [initialState] = useState(() => {
    if (typeof window === 'undefined') {
      return {
        values: {
          voucherDate: format(new Date(), 'yyyy-MM-dd'),
          description: '',
        },
        lines: createInitialLines(),
        lock: null as DraftLock | null,
        updatedAt: null as string | null,
      }
    }
    const stored = readVoucherDraft(draftStorageKey)
    return {
      values: stored?.header ?? {
        voucherDate: format(new Date(), 'yyyy-MM-dd'),
        description: '',
      },
      lines: stored?.lines?.length ? stored.lines : createInitialLines(),
      lock: stored?.lock ?? null,
      updatedAt: stored?.updatedAt ?? null,
    }
  })

  // ─── Lines State with Undo/Redo ────────────────────────────────────────────
  const {
    value: lines,
    setValue: setLines,
    reset: resetLines,
    undo,
    redo,
    canUndo,
    canRedo,
  } = useUndoRedo<VoucherEntryLine[]>(initialState.lines)

  // ─── Validation State ──────────────────────────────────────────────────────
  const [validationMap, setValidationMap] = useState<Record<number, Record<string, string[]>>>({})
  const [validating, setValidating] = useState(false)

  // ─── UI State ──────────────────────────────────────────────────────────────
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [autoSaveStatus, setAutoSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>(
    'idle',
  )
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(
    initialState.updatedAt ? new Date(initialState.updatedAt) : null,
  )
  const [autoSaveError, setAutoSaveError] = useState<string | null>(null)

  // ─── Draft Lock State ──────────────────────────────────────────────────────
  const [draftLock, setDraftLock] = useState<DraftLock | null>(initialState.lock)
  const [isLocked, setIsLocked] = useState(
    () => !isEditing && isLockedByOther(initialState.lock, currentUserId),
  )
  const [lockCountdown, setLockCountdown] = useState<string | null>(null)

  // ─── Voucher Data State ────────────────────────────────────────────────────
  const [loadingVoucher, setLoadingVoucher] = useState(false)
  const [editingVoucher, setEditingVoucher] = useState<VoucherDTO | null>(null)
  const [attachmentCount, setAttachmentCount] = useState(0)
  const [attachmentModalOpen, setAttachmentModalOpen] = useState(false)

  // ─── Template State ────────────────────────────────────────────────────────
  const [applyingTemplate, setApplyingTemplate] = useState(false)

  // ─── Posting State ─────────────────────────────────────────────────────────
  const [posting, setPosting] = useState(false)
  const [unposting, setUnposting] = useState(false)
  const [reversing, setReversing] = useState(false)
  const [postingErrorModalOpen, setPostingErrorModalOpen] = useState(false)
  const [postingErrors, setPostingErrors] = useState<Record<string, any> | null>(null)
  const [unpostDialogOpen, setUnpostDialogOpen] = useState(false)
  const [reverseDialogOpen, setReverseDialogOpen] = useState(false)
  const [unpostReason, setUnpostReason] = useState('')
  const [reverseDescription, setReverseDescription] = useState('')
  const [reverseReason, setReverseReason] = useState('')
  const [validationSummaryOpen, setValidationSummaryOpen] = useState(false)

  // ─── Period State ──────────────────────────────────────────────────────────
  const [selectedPeriod, setSelectedPeriod] = useState<AccountingPeriod | null>(null)
  const [periodValidationError, setPeriodValidationError] = useState<string | null>(null)
  const [dateValidationCache, setDateValidationCache] = useState<Map<string, boolean>>(new Map())

  // ─── Computed Values ───────────────────────────────────────────────────────
  const today = useMemo(() => new Date(), [])

  const openPeriodRange = useMemo(() => {
    const fiscalStartBase = company?.fiscalYearStart
      ? parseISO(company.fiscalYearStart)
      : startOfMonth(new Date(today.getFullYear(), 0, 1))
    const fiscalMonth = fiscalStartBase.getMonth()
    const fiscalDay = fiscalStartBase.getDate()
    let startYear = today.getFullYear()
    const candidate = new Date(startYear, fiscalMonth, fiscalDay)
    if (candidate > today) {
      startYear -= 1
    }
    const openStart = startOfMonth(new Date(startYear, fiscalMonth, fiscalDay))
    const openEnd = endOfMonth(today)
    return { openStart, openEnd }
  }, [company?.fiscalYearStart, today])

  // ─── Form Setup ────────────────────────────────────────────────────────────
  const form = useForm<VoucherFormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: initialState.values,
  })

  const watchedValues = useWatch({ control: form.control })

  const selectedDate = useMemo(() => {
    if (!watchedValues?.voucherDate) return null
    const parsed = new Date(watchedValues.voucherDate)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }, [watchedValues?.voucherDate])

  const [calendarMonth, setCalendarMonth] = useState<Date>(selectedDate ?? openPeriodRange.openEnd)

  // ─── Entry Line Payload ────────────────────────────────────────────────────
  const entryLinePayload = useMemo(() => {
    return lines
      .map((line, index) => ({
        lineNumber: index + 1,
        debitAccountId: line.debitAccount?.id || '',
        creditAccountId: line.creditAccount?.id || '',
        amount: line.amount || 0,
        description: line.description,
        customerId: line.customerId ?? line.customer?.id ?? undefined,
        supplierId: line.supplierId ?? line.supplier?.id ?? undefined,
        costCenterId: line.costCenterId ?? line.costCenter?.id ?? undefined,
      }))
      .filter((line) => line.debitAccountId && line.creditAccountId)
  }, [lines])

  // ─── Totals Calculation (for Summary) ──────────────────────────────────────
  const totals = useMemo(() => {
    let totalDebit = 0
    let totalCredit = 0
    let lineCount = 0

    for (const line of lines) {
      if (line.amount && line.amount > 0) {
        if (line.debitAccount) totalDebit += line.amount
        if (line.creditAccount) totalCredit += line.amount
        lineCount++
      }
    }

    const isBalanced = Math.abs(totalDebit - totalCredit) < 0.01
    const difference = totalDebit - totalCredit

    return { totalDebit, totalCredit, lineCount, isBalanced, difference }
  }, [lines])

  // ─── Validation Summary ────────────────────────────────────────────────────
  const validationSummary = useMemo(() => {
    const errorCount = Object.keys(validationMap).length
    const totalErrors = Object.values(validationMap).reduce(
      (sum, fieldErrors) => sum + Object.values(fieldErrors).flat().length,
      0,
    )
    return { errorCount, totalErrors }
  }, [validationMap])

  // ─── Form Disabled State ───────────────────────────────────────────────────
  const formDisabled = saving || loadingAccounts || loadingVoucher || isLocked

  // ─── Can Post/Unpost/Reverse ───────────────────────────────────────────────
  const canPost =
    isEditing &&
    editingVoucher?.status === 'draft' &&
    hasAnyRole(['admin', 'chief_accountant', 'cfo'])
  const canUnpost =
    isEditing &&
    editingVoucher?.status === 'posted' &&
    hasAnyRole(['admin', 'chief_accountant', 'cfo'])
  const canReverse =
    isEditing &&
    editingVoucher?.status === 'posted' &&
    !editingVoucher?.reversedByVoucherId &&
    hasAnyRole(['admin', 'chief_accountant', 'cfo'])

  // ─── Build Request ─────────────────────────────────────────────────────────
  function buildRequest(values: VoucherFormValues): VoucherCreateRequest {
    return {
      date: values.voucherDate,
      description: values.description || '',
      entryLines: entryLinePayload,
      currency: 'VND',
    }
  }

  // ─── Draft Management ──────────────────────────────────────────────────────
  const saveDraftSnapshot = useCallback(
    (values: VoucherFormValues, lineItems: VoucherEntryLine[]) => {
      if (typeof window === 'undefined' || isLocked || isEditing) return
      try {
        setAutoSaveStatus('saving')
        const payload: VoucherDraftPayload = {
          version: DRAFT_VERSION,
          header: values,
          lines: lineItems,
          updatedAt: new Date().toISOString(),
          lock: createDraftLock(currentUserId, user?.fullName),
        }
        writeVoucherDraft(draftStorageKey, payload)
        setDraftLock(payload.lock ?? null)
        setLastSavedAt(new Date(payload.updatedAt))
        setAutoSaveStatus('saved')
        setAutoSaveError(null)
      } catch (error: any) {
        console.error('Failed to save draft', error)
        setAutoSaveStatus('error')
        setAutoSaveError(error?.message || 'Cannot save draft')
      }
    },
    [currentUserId, draftStorageKey, isEditing, isLocked, user?.fullName],
  )

  const claimLock = useCallback(() => {
    if (typeof window === 'undefined' || isEditing) return
    const lock = createDraftLock(currentUserId, user?.fullName)
    const currentValues = watchedValues as VoucherFormValues
    const payload: VoucherDraftPayload = {
      version: DRAFT_VERSION,
      header: currentValues,
      lines,
      updatedAt: new Date().toISOString(),
      lock,
    }
    writeVoucherDraft(draftStorageKey, payload)
    setDraftLock(lock)
    setIsLocked(false)
  }, [currentUserId, draftStorageKey, isEditing, lines, user?.fullName, watchedValues])

  // ─── Validation ────────────────────────────────────────────────────────────
  const performRealTimeValidation = useCallback(
    async (silent = true) => {
      if (!lines.length || lines.every((line) => !line.debitAccount && !line.creditAccount)) {
        return
      }

      try {
        setValidating(true)
        const currentValues = form.getValues()
        const payload = buildRequest(currentValues)
        const result = await validateVoucher(payload, voucherId)
        setValidationMap(result.errors || {})
        if (!silent) {
          if (result.valid) {
            toast.success('All voucher lines are valid')
          } else {
            toast.warning('Some lines need to be reviewed')
          }
        }
      } catch (err: any) {
        if (!silent) {
          toast.error('Cannot validate voucher', { description: err?.message })
        }
      } finally {
        setValidating(false)
      }
    },
    [lines, form, voucherId],
  )

  // ─── Save Handler ──────────────────────────────────────────────────────────
  const handleSave = useCallback(
    async (values: VoucherFormValues) => {
      try {
        setSaving(true)
        const payload = buildRequest(values)
        const response =
          isEditing && voucherId
            ? await updateVoucher(voucherId, payload)
            : await createVoucher(payload)
        toast.success(isEditing ? 'Voucher updated' : 'Draft saved', {
          description: `Voucher number: ${response.voucherNumber}`,
        })
        if (!isEditing) {
          removeVoucherDraft(draftStorageKey)
          setLastSavedAt(null)
        }
        setValidationMap({})
        if (isEditing && voucherId) {
          const updated = await getVoucherById(voucherId)
          setEditingVoucher(updated)
        }
      } catch (err: any) {
        toast.error('Cannot save voucher', { description: err?.message })
      } finally {
        setSaving(false)
      }
    },
    [isEditing, voucherId, draftStorageKey, entryLinePayload],
  )

  // ─── Post Handler ──────────────────────────────────────────────────────────
  const handlePost = useCallback(async () => {
    if (!voucherId || !editingVoucher) return
    if (editingVoucher.status !== 'draft') {
      toast.error('Can only post voucher in draft status')
      return
    }
    try {
      setPosting(true)
      const response = await postVoucher(voucherId)
      toast.success('Voucher posted successfully', {
        description: `Created ${response.journalEntries.length} journal entries`,
      })
      const updated = await getVoucherById(voucherId)
      setEditingVoucher(updated)
    } catch (err: any) {
      if (err?.validationErrors) {
        setPostingErrors(err.validationErrors)
        setPostingErrorModalOpen(true)
      } else {
        toast.error('Cannot post voucher', {
          description: err?.message || err?.error?.message,
        })
      }
    } finally {
      setPosting(false)
    }
  }, [voucherId, editingVoucher])

  // ─── Unpost Handler ────────────────────────────────────────────────────────
  const handleUnpost = useCallback(async () => {
    if (!voucherId || !editingVoucher) return
    if (!unpostReason.trim()) {
      toast.error('Please enter the reason for unposting')
      return
    }
    try {
      setUnposting(true)
      await unpostVoucher(voucherId, unpostReason.trim())
      toast.success('Voucher unposted successfully')
      setUnpostDialogOpen(false)
      setUnpostReason('')
      const updated = await getVoucherById(voucherId)
      setEditingVoucher(updated)
    } catch (err: any) {
      toast.error('Cannot unpost voucher', {
        description: err?.message || err?.error?.message,
      })
    } finally {
      setUnposting(false)
    }
  }, [voucherId, editingVoucher, unpostReason])

  // ─── Reverse Handler ───────────────────────────────────────────────────────
  const handleReverse = useCallback(async () => {
    if (!voucherId || !editingVoucher) return
    if (!reverseDescription.trim() || !reverseReason.trim()) {
      toast.error('Please enter the full description and reason for reversing')
      return
    }
    try {
      setReversing(true)
      const response = await reverseVoucher(
        voucherId,
        reverseDescription.trim(),
        reverseReason.trim(),
      )
      toast.success('Voucher reversed successfully', {
        description: `Reversed voucher: ${response.reversal.voucherNumber}`,
      })
      setReverseDialogOpen(false)
      setReverseDescription('')
      setReverseReason('')
      const updated = await getVoucherById(voucherId)
      setEditingVoucher(updated)
    } catch (err: any) {
      toast.error('Cannot reverse voucher', {
        description: err?.message || err?.error?.message,
      })
    } finally {
      setReversing(false)
    }
  }, [voucherId, editingVoucher, reverseDescription, reverseReason])

  // ─── Template Handler ──────────────────────────────────────────────────────
  const applyTemplateLines = useCallback(
    (template: VoucherTemplateDTO, overrideEntries?: VoucherEntryLine[]) => {
      if (!template?.lines && !overrideEntries?.length) return
      const lockedIds = new Set<string>()
      const mapped =
        template.lines?.map<VoucherEntryLine>((line) => {
          const debitAccount =
            accounts.find((account) => account.id === line.debitAccountId) || null
          const creditAccount =
            accounts.find((account) => account.id === line.creditAccountId) || null
          if (line.lockAccounts) {
            if (line.debitAccountId) lockedIds.add(String(line.debitAccountId))
            if (line.creditAccountId) lockedIds.add(String(line.creditAccountId))
          }
          return {
            id: `tpl-${template.id}-${line.lineNumber}-${Date.now()}`,
            debitAccount,
            creditAccount,
            description: line.defaultDescription || '',
            amount: null,
            customerId: null,
            supplierId: null,
            costCenterId: null,
            customer: null,
            supplier: null,
            costCenter: null,
            source: 'template',
            status: 'dirty',
          }
        }) ?? []
      const nextLines =
        overrideEntries && overrideEntries.length
          ? overrideEntries
          : mapped.length > 0
            ? mapped
            : createInitialLines()
      resetLines(nextLines)
      setLockedAccountIds(Array.from(lockedIds))
      setValidationMap({})
    },
    [accounts, resetLines],
  )

  const handleTemplateApplied = useCallback(
    async (template: VoucherTemplateDTO) => {
      if (isEditing) {
        toast.info('Can only apply template when creating a new voucher.')
        return
      }
      if (!accounts.length) {
        toast.error('Account categories not loaded, please try again.')
        return
      }
      const voucherDate = form.getValues('voucherDate')
      if (!voucherDate) {
        toast.error('Please select the voucher date before applying the template.')
        return
      }
      setApplyingTemplate(true)
      try {
        const payload = await applyVoucherTemplate({
          templateId: template.id,
          voucherDate,
          description: form.getValues('description') || template.description || '',
        })
        const appliedTemplate = payload?.data?.template ?? template
        const voucherLines = payload?.data?.voucher?.lines ?? []
        if (voucherLines.length > 0) {
          const mappedEntries = convertLedgerLinesToEntries(voucherLines, accounts)
          applyTemplateLines(appliedTemplate, mappedEntries)
        } else {
          applyTemplateLines(appliedTemplate)
        }
        toast.success(`Template ${appliedTemplate.name} applied`)
        setTemplateDialogOpen(false)
      } catch (error: any) {
        console.error('Failed to apply template via API', error)
        toast.error('Cannot apply template', {
          description: error?.message || error?.code || 'Please try again later.',
        })
        applyTemplateLines(template)
        setTemplateDialogOpen(false)
      } finally {
        setApplyingTemplate(false)
      }
    },
    [accounts, form, isEditing, applyTemplateLines],
  )

  // ─── Date Validation ───────────────────────────────────────────────────────
  const isDateDisabledSync = useCallback(
    (date: Date) => {
      if (selectedDate && isSameDay(date, selectedDate)) return false
      if (isBefore(date, openPeriodRange.openStart)) return true
      if (isAfter(date, openPeriodRange.openEnd)) return true
      const dateStr = format(date, 'yyyy-MM-dd')
      const cached = dateValidationCache.get(dateStr)
      if (cached !== undefined) {
        return !cached
      }
      return false
    },
    [openPeriodRange.openEnd, openPeriodRange.openStart, selectedDate, dateValidationCache],
  )

  // ─── Effects ───────────────────────────────────────────────────────────────

  // Calendar month sync
  useEffect(() => {
    if (selectedDate) {
      setCalendarMonth(selectedDate)
    } else {
      setCalendarMonth(openPeriodRange.openEnd)
    }
  }, [openPeriodRange.openEnd, selectedDate])

  // Load accounts
  useEffect(() => {
    let mounted = true
    async function loadAccounts() {
      try {
        const response = await getPostableAccounts()
        if (!mounted) return
        setAccounts(mapAccountsToSummaries(response))
      } catch (err: any) {
        toast.error('Cannot load account categories', {
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

  // Auto-save draft
  useEffect(() => {
    if (isEditing || typeof window === 'undefined') {
      return
    }
    if (isLocked) return
    const timeout = setTimeout(() => {
      saveDraftSnapshot(watchedValues as VoucherFormValues, lines)
    }, AUTO_SAVE_DEBOUNCE_MS)
    return () => clearTimeout(timeout)
  }, [isEditing, isLocked, lines, saveDraftSnapshot, watchedValues])

  // Claim lock on mount
  useEffect(() => {
    if (isEditing || typeof window === 'undefined') return
    if (isLocked) return
    claimLock()
  }, [claimLock, isEditing, isLocked])

  // Lock countdown
  useEffect(() => {
    if (!draftLock || !isLocked) {
      setLockCountdown(null)
      return
    }
    const tick = () => {
      const remaining = new Date(draftLock.expiresAt).getTime() - Date.now()
      setLockCountdown(formatCountdown(remaining))
      if (remaining <= 0) {
        setIsLocked(false)
        setDraftLock(null)
        claimLock()
      }
    }
    tick()
    const interval = setInterval(tick, 1000)
    return () => clearInterval(interval)
  }, [claimLock, draftLock, isLocked])

  // Load voucher for editing
  useEffect(() => {
    if (!isEditing || !voucherId || loadingAccounts) return
    let mounted = true
    setLoadingVoucher(true)
    getVoucherById(voucherId)
      .then((voucher) => {
        if (!mounted) return
        form.reset({
          voucherDate: voucher.voucherDate,
          description: voucher.description ?? '',
        })
        resetLines(convertLedgerLinesToEntries(voucher.lines, accounts))
        setEditingVoucher(voucher)
        setValidationMap({})
        setLockedAccountIds([])
        setAttachmentCount(voucher.attachmentCount || 0)
      })
      .catch((error) => {
        console.error(error)
        toast.error('Cannot load voucher to edit')
      })
      .finally(() => {
        if (mounted) setLoadingVoucher(false)
      })
    return () => {
      mounted = false
    }
  }, [accounts, form, isEditing, loadingAccounts, resetLines, voucherId])

  // Debounced real-time validation
  useEffect(() => {
    if (isEditing || loadingAccounts || loadingVoucher) return
    if (lines.length === 0) return
    const hasFilledLines = lines.some(
      (line) => line.debitAccount || line.creditAccount || line.amount,
    )
    if (!hasFilledLines) {
      setValidationMap({})
      return
    }
    const timeout = setTimeout(() => {
      performRealTimeValidation(true)
    }, 1000)
    return () => clearTimeout(timeout)
  }, [lines, isEditing, loadingAccounts, loadingVoucher, performRealTimeValidation])

  // ─── Return ────────────────────────────────────────────────────────────────
  return {
    // Form
    form,
    watchedValues,
    selectedDate,
    calendarMonth,
    setCalendarMonth,
    formDisabled,

    // Voucher Info
    voucherId,
    isEditing,
    editingVoucher,
    loadingVoucher,

    // Lines
    lines,
    setLines,
    undo,
    redo,
    canUndo,
    canRedo,

    // Accounts
    accounts,
    lockedAccountIds,
    loadingAccounts,

    // Totals
    totals,

    // Validation
    validationMap,
    validating,
    validationSummary,
    validationSummaryOpen,
    setValidationSummaryOpen,
    performRealTimeValidation,

    // Period
    selectedPeriod,
    setSelectedPeriod,
    periodValidationError,
    setPeriodValidationError,
    openPeriodRange,
    isDateDisabledSync,
    dateValidationCache,
    setDateValidationCache,

    // Draft
    autoSaveStatus,
    lastSavedAt,
    autoSaveError,
    isLocked,
    draftLock,
    lockCountdown,
    claimLock,

    // Template
    templateDialogOpen,
    setTemplateDialogOpen,
    applyingTemplate,
    handleTemplateApplied,

    // Attachments
    attachmentCount,
    setAttachmentCount,
    attachmentModalOpen,
    setAttachmentModalOpen,

    // Saving
    saving,
    handleSave,

    // Posting
    posting,
    unposting,
    reversing,
    canPost,
    canUnpost,
    canReverse,
    handlePost,
    handleUnpost,
    handleReverse,
    postingErrorModalOpen,
    setPostingErrorModalOpen,
    postingErrors,
    unpostDialogOpen,
    setUnpostDialogOpen,
    reverseDialogOpen,
    setReverseDialogOpen,
    unpostReason,
    setUnpostReason,
    reverseDescription,
    setReverseDescription,
    reverseReason,
    setReverseReason,
  }
}
