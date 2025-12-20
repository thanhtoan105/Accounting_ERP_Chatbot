import { useCallback, useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import {
  addMinutes,
  endOfMonth,
  format,
  formatDistanceToNow,
  isAfter,
  isBefore,
  isSameDay,
  parseISO,
  startOfMonth,
} from 'date-fns'
import {
  AlertCircle,
  CalendarIcon,
  Loader2,
  Save,
  ShieldAlert,
  Sparkles,
  FileText,
  CheckCircle,
  RotateCcw,
  ArrowLeftRight,
} from 'lucide-react'
import { useParams } from 'react-router-dom'
import { z } from 'zod'

import {
  VoucherLineGrid,
  type VoucherEntryLine,
  VoucherTemplateSelector,
  VoucherAttachmentDropzone,
  VoucherHistoryView,
  VoucherAttachmentManagementModal,
} from '@/components/voucher'
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
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { Calendar } from '@/components/ui/calendar'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { toast } from 'sonner'

import type { AccountSummary } from '@/components/account/AccountPicker'
import { PeriodSelector, periodService } from '@/components/period'
import type { AccountingPeriod } from '@/types/accountingPeriod'
import useUndoRedo from '@/hooks/useUndoRedo'
import { useAuth } from '@/hooks/useAuth'
import { useRole } from '@/hooks/useRole'
import { useCompany } from '@/hooks/useCompany'
import { getCompanyId } from '@/utils/axios'
import type {
  VoucherTemplateDTO,
  VoucherCreateRequest,
  VoucherDTO,
  VoucherLedgerLineDTO,
  VoucherDimensionOption,
} from '@/types/voucher'
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
import type { ChartOfAccount } from '@/types/chartOfAccount'

const formSchema = z.object({
  voucherDate: z.string().min(1, 'Voucher date is required'),
  description: z
    .string()
    .max(500, 'Description can be up to 500 characters')
    .optional()
    .or(z.literal('')),
})

type VoucherFormValues = z.infer<typeof formSchema>

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

const DRAFT_VERSION = 1
const AUTO_SAVE_DEBOUNCE_MS = 1000
const LOCK_DURATION_MINUTES = 5

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

export default function VoucherForm() {
  const params = useParams<{ voucherId?: string }>()
  const voucherId = params.voucherId && params.voucherId !== 'new' ? params.voucherId : undefined
  const isEditing = Boolean(voucherId)
  const { user } = useAuth()
  const { hasAnyRole } = useRole()
  const { company } = useCompany()
  const currentUserId = user?.id ? String(user.id) : 'anonymous'
  const companyId = getCompanyId()
  const draftStorageKey = buildDraftKey(companyId, voucherId)
  const [accounts, setAccounts] = useState<AccountSummary[]>([])
  const [lockedAccountIds, setLockedAccountIds] = useState<string[]>([])
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
  const {
    value: lines,
    setValue: setLines,
    reset: resetLines,
    undo,
    redo,
    canUndo,
    canRedo,
  } = useUndoRedo<VoucherEntryLine[]>(initialState.lines)
  const [validationMap, setValidationMap] = useState<Record<number, Record<string, string[]>>>({})
  const [validating, setValidating] = useState(false)
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false)
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
  const [lockCountdown, setLockCountdown] = useState<string | null>(null)
  const [loadingVoucher, setLoadingVoucher] = useState(false)
  const [editingVoucher, setEditingVoucher] = useState<VoucherDTO | null>(null)
  const [autoSaveError, setAutoSaveError] = useState<string | null>(null)
  const [attachmentCount, setAttachmentCount] = useState(0)
  const [attachmentModalOpen, setAttachmentModalOpen] = useState(false)
  const [applyingTemplate, setApplyingTemplate] = useState(false)
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
  const [selectedPeriod, setSelectedPeriod] = useState<AccountingPeriod | null>(null)
  const [periodValidationError, setPeriodValidationError] = useState<string | null>(null)
  const [dateValidationCache, setDateValidationCache] = useState<Map<string, boolean>>(new Map())
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

  useEffect(() => {
    if (selectedDate) {
      setCalendarMonth(selectedDate)
    } else {
      setCalendarMonth(openPeriodRange.openEnd)
    }
  }, [openPeriodRange.openEnd, selectedDate])

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

  function buildRequest(values: VoucherFormValues): VoucherCreateRequest {
    return {
      date: values.voucherDate,
      description: values.description ?? '',
      entryLines: entryLinePayload,
      currency: 'VND',
    }
  }

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

  useEffect(() => {
    if (isEditing || typeof window === 'undefined') return
    if (isLocked) return
    claimLock()
  }, [claimLock, isEditing, isLocked])

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
        resetLines(
          convertLedgerLinesToEntries(
            voucher.lines as VoucherLedgerLineDTO[] | undefined,
            accounts,
          ),
        )
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

  // Real-time validation (debounced, silent)
  const performRealTimeValidation = useCallback(
    async (silent = true) => {
      if (!lines.length || lines.every((line) => !line.debitAccount && !line.creditAccount)) {
        // Skip validation if no lines or all lines are empty
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
        // Silently fail for real-time validation, only show errors for manual validation
        if (!silent) {
          toast.error('Cannot validate voucher', { description: err?.message })
        }
      } finally {
        setValidating(false)
      }
    },
    [lines, form, voucherId],
  )

  // Debounced real-time validation when lines change
  useEffect(() => {
    if (isEditing || loadingAccounts || loadingVoucher) return
    if (lines.length === 0) return

    // Only validate if there's at least one line with accounts filled
    const hasFilledLines = lines.some(
      (line) => line.debitAccount || line.creditAccount || line.amount,
    )
    if (!hasFilledLines) {
      setValidationMap({})
      return
    }

    const timeout = setTimeout(() => {
      performRealTimeValidation(true) // Silent validation
    }, 1000) // 1 second debounce

    return () => clearTimeout(timeout)
  }, [lines, isEditing, loadingAccounts, loadingVoucher, performRealTimeValidation])

  async function handleValidate() {
    await performRealTimeValidation(false) // Manual validation with toast messages
  }

  async function handleSave(values: VoucherFormValues) {
    try {
      setSaving(true)
      const payload = buildRequest(values)
      const response =
        isEditing && voucherId
          ? await updateVoucher(voucherId, payload)
          : await createVoucher(payload)
      toast.success(isEditing ? 'Voucher updated' : 'Draft saved', {
        description: `Mã chứng từ: ${response.voucherNumber}`,
      })
      if (!isEditing) {
        removeVoucherDraft(draftStorageKey)
        setLastSavedAt(null)
      }
      setValidationMap({})
      // Reload voucher if editing to get updated status
      if (isEditing && voucherId) {
        const updated = await getVoucherById(voucherId)
        setEditingVoucher(updated)
      }
    } catch (err: any) {
      toast.error('Cannot save voucher', { description: err?.message })
    } finally {
      setSaving(false)
    }
  }

  async function handlePost() {
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
      // Reload voucher to get updated status
      const updated = await getVoucherById(voucherId)
      setEditingVoucher(updated)
    } catch (err: any) {
      if (err?.validationErrors) {
        // Show bulk validation errors in modal
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
  }

  async function handleUnpost() {
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
      // Reload voucher to get updated status
      const updated = await getVoucherById(voucherId)
      setEditingVoucher(updated)
    } catch (err: any) {
      toast.error('Cannot unpost voucher', {
        description: err?.message || err?.error?.message,
      })
    } finally {
      setUnposting(false)
    }
  }

  async function handleReverse() {
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
      // Reload voucher to get updated status
      const updated = await getVoucherById(voucherId)
      setEditingVoucher(updated)
    } catch (err: any) {
      toast.error('Cannot reverse voucher', {
        description: err?.message || err?.error?.message,
      })
    } finally {
      setReversing(false)
    }
  }

  function applyTemplateLines(template: VoucherTemplateDTO, overrideEntries?: VoucherEntryLine[]) {
    if (!template?.lines && !overrideEntries?.length) return
    const lockedIds = new Set<string>()
    const mapped =
      template.lines?.map<VoucherEntryLine>((line) => {
        const debitAccount = accounts.find((account) => account.id === line.debitAccountId) || null
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
  }

  async function handleTemplateApplied(template: VoucherTemplateDTO) {
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
        const mappedEntries = convertLedgerLinesToEntries(
          voucherLines as unknown as VoucherLedgerLineDTO[],
          accounts,
        )
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
  }

  const onSubmit = (values: VoucherFormValues) => handleSave(values)
  const formDisabled = saving || loadingAccounts || loadingVoucher || isLocked

  // Calculate validation summary
  const validationSummary = useMemo(() => {
    const errorCount = Object.keys(validationMap).length
    const totalErrors = Object.values(validationMap).reduce(
      (sum, fieldErrors) => sum + Object.values(fieldErrors).flat().length,
      0,
    )
    return { errorCount, totalErrors }
  }, [validationMap])

  // Synchronous version for Calendar component (uses cached results)
  const isDateDisabledSync = useCallback(
    (date: Date) => {
      if (selectedDate && isSameDay(date, selectedDate)) return false
      if (isBefore(date, openPeriodRange.openStart)) return true
      if (isAfter(date, openPeriodRange.openEnd)) return true

      // Check cache for period validation
      const dateStr = format(date, 'yyyy-MM-dd')
      const cached = dateValidationCache.get(dateStr)
      if (cached !== undefined) {
        return !cached
      }

      // Default to enabled if not cached yet (will be validated on selection)
      return false
    },
    [openPeriodRange.openEnd, openPeriodRange.openStart, selectedDate, dateValidationCache],
  )

  // Handle period change
  const handlePeriodChange = useCallback(
    (period: AccountingPeriod) => {
      setSelectedPeriod(period)
      // Optionally update the voucher date to be within the selected period
      if (period) {
        const periodStartDate = new Date(period.startDate)
        const periodEndDate = new Date(period.endDate)
        const currentDate = new Date(watchedValues.voucherDate || Date.now())

        // If current date is outside the selected period, set it to the period start date
        if (currentDate < periodStartDate || currentDate > periodEndDate) {
          form.setValue('voucherDate', period.startDate)
        }
      }
    },
    [form, watchedValues.voucherDate],
  )

  return (
    <div className="space-y-6">
      {isLocked && draftLock ? (
        <Alert variant="destructive">
          <AlertTitle>Voucher is locked</AlertTitle>
          <AlertDescription className="flex flex-wrap items-center gap-2 text-sm">
            {draftLock.ownerName || 'Other user'} is editing this draft. Lock will expire in sau{' '}
            <span className="font-semibold">{lockCountdown ?? '—'}</span>.
            <Button
              size="sm"
              variant="outline"
              disabled={Boolean(lockCountdown) && lockCountdown !== '00:00'}
              onClick={claimLock}
            >
              Request edit permission
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold">
              {isEditing ? 'Edit accounting voucher' : 'Create accounting voucher'}
            </h1>
            {editingVoucher?.status ? (
              <Badge variant="outline" className="uppercase">
                {editingVoucher.status}
              </Badge>
            ) : null}
            {editingVoucher?.voucherNumber ? (
              <Badge variant="secondary">{editingVoucher.voucherNumber}</Badge>
            ) : null}
            {editingVoucher?.reversedByVoucherId ? (
              <Badge
                variant="outline"
                className="cursor-pointer hover:bg-orange-100"
                onClick={() => {
                  window.location.href = `/vouchers/${editingVoucher.reversedByVoucherId}`
                }}
              >
                Reversed by
              </Badge>
            ) : null}
            {editingVoucher?.reversalOf ? (
              <Badge
                variant="outline"
                className="cursor-pointer hover:bg-blue-100"
                onClick={() => {
                  window.location.href = `/vouchers/${editingVoucher.reversalOf}`
                }}
              >
                Reversal of
              </Badge>
            ) : null}
            {voucherId && (
              <Badge
                variant="outline"
                className="gap-1 cursor-pointer hover:bg-accent"
                onClick={() => setAttachmentModalOpen(true)}
              >
                <FileText className="size-3" />
                {attachmentCount} đính kèm
              </Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground">
            Quickly enter by keyboard, apply templates and automatically validate before posting.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            type="button"
            onClick={() => setTemplateDialogOpen(true)}
            disabled={loadingAccounts || formDisabled || isEditing}
          >
            <Sparkles className="mr-2 h-4 w-4" />
            Apply template
          </Button>
          <Button
            variant="outline"
            type="button"
            onClick={form.handleSubmit(handleValidate)}
            disabled={formDisabled}
          >
            {saving ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <ShieldAlert className="mr-2 h-4 w-4" />
            )}
            Validate
          </Button>
          <Button type="button" onClick={form.handleSubmit(onSubmit)} disabled={formDisabled}>
            {saving ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Save className="mr-2 h-4 w-4" />
            )}
            Save draft
          </Button>
          {isEditing && editingVoucher && hasAnyRole(['admin', 'chief_accountant', 'cfo']) && (
            <>
              {editingVoucher.status === 'draft' && (
                <Button
                  type="button"
                  onClick={handlePost}
                  disabled={posting || formDisabled}
                  className="bg-green-600 hover:bg-green-700"
                >
                  {posting ? (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  ) : (
                    <CheckCircle className="mr-2 h-4 w-4" />
                  )}
                  Post
                </Button>
              )}
              {editingVoucher.status === 'posted' && (
                <>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setUnpostDialogOpen(true)}
                    disabled={unposting || formDisabled}
                  >
                    {unposting ? (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    ) : (
                      <RotateCcw className="mr-2 h-4 w-4" />
                    )}
                    Unpost
                  </Button>
                  {!editingVoucher.reversedByVoucherId && (
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setReverseDialogOpen(true)}
                      disabled={reversing || formDisabled}
                    >
                      {reversing ? (
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      ) : (
                        <ArrowLeftRight className="mr-2 h-4 w-4" />
                      )}
                      Reverse
                    </Button>
                  )}
                </>
              )}
            </>
          )}
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="text-xs text-muted-foreground">
          {autoSaveStatus === 'saving' && 'Saving draft...'}
          {autoSaveStatus === 'saved' &&
            lastSavedAt &&
            `Draft saved at ${formatDistanceToNow(lastSavedAt, { addSuffix: true })}`}
          {autoSaveStatus === 'error' && (
            <span className="text-destructive flex items-center gap-1">
              <AlertCircle className="h-3 w-3" />
              {autoSaveError || 'Cannot save draft'}
            </span>
          )}
        </div>
        {validationSummary.errorCount > 0 && (
          <Button
            variant="outline"
            size="sm"
            type="button"
            onClick={() => setValidationSummaryOpen(true)}
            className="text-destructive border-destructive"
          >
            <AlertCircle className="mr-2 h-4 w-4" />
            {validationSummary.errorCount} lines with errors ({validationSummary.totalErrors}{' '}
            errors)
          </Button>
        )}
      </div>

      {/* Period Selector */}
      <div className="bg-gray-50 p-4 rounded-lg">
        <div className="flex items-center gap-3">
          <label className="text-sm font-medium">Accounting period</label>
          <div className="flex-1 max-w-md">
            <PeriodSelector
              selectedPeriod={selectedPeriod}
              onPeriodChange={handlePeriodChange}
              showSummary={true}
              placeholder="Select accounting period..."
              disabled={formDisabled}
            />
          </div>
        </div>
      </div>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>General information</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 md:grid-cols-2">
              <FormField
                control={form.control}
                name="voucherDate"
                render={({ field }) => (
                  <FormItem className="flex flex-col">
                    <FormLabel>Voucher date</FormLabel>
                    <Popover>
                      <PopoverTrigger asChild>
                        <FormControl>
                          <Button
                            variant="outline"
                            className="justify-between text-left font-normal"
                            disabled={formDisabled}
                          >
                            {field.value
                              ? format(new Date(field.value), 'dd/MM/yyyy')
                              : 'Select date'}
                            <CalendarIcon className="ml-2 h-4 w-4 opacity-50" />
                          </Button>
                        </FormControl>
                      </PopoverTrigger>
                      <PopoverContent className="w-auto p-0" align="start">
                        <Calendar
                          mode="single"
                          selected={field.value ? new Date(field.value) : undefined}
                          onSelect={async (date) => {
                            if (date) {
                              const dateStr = format(date, 'yyyy-MM-dd')
                              // Validate with period API before allowing selection
                              try {
                                const isValid = await periodService.checkDateInOpenPeriod(dateStr)
                                if (isValid) {
                                  field.onChange(dateStr)
                                  setPeriodValidationError(null)
                                  setDateValidationCache((prev) => new Map(prev).set(dateStr, true))
                                } else {
                                  const period = await periodService.findPeriodByDate(dateStr)
                                  if (period) {
                                    setPeriodValidationError(
                                      `Cannot create voucher in closed period: ${period.periodName}`,
                                    )
                                  } else {
                                    setPeriodValidationError(
                                      `Date ${dateStr} is not in an open period`,
                                    )
                                  }
                                }
                              } catch (error) {
                                console.error('Failed to validate date:', error)
                                field.onChange(dateStr) // Allow selection but show warning
                                setPeriodValidationError(
                                  'Failed to validate period. Please verify the date is in an open period.',
                                )
                              }
                            } else {
                              field.onChange('')
                            }
                          }}
                          month={calendarMonth}
                          onMonthChange={setCalendarMonth}
                          disabled={isDateDisabledSync}
                          initialFocus
                        />
                      </PopoverContent>
                    </Popover>
                    <FormDescription>
                      Only dates between {format(openPeriodRange.openStart, 'dd/MM/yyyy')} –{' '}
                      {format(openPeriodRange.openEnd, 'dd/MM/yyyy')}.
                    </FormDescription>
                    {periodValidationError && (
                      <Alert variant="destructive" className="mt-2">
                        <AlertCircle className="h-4 w-4" />
                        <AlertDescription>{periodValidationError}</AlertDescription>
                      </Alert>
                    )}
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="description"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>General description</FormLabel>
                    <FormControl>
                      <Textarea
                        rows={3}
                        placeholder="Example: Received cash from customer..."
                        {...field}
                        disabled={formDisabled}
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
              <CardTitle>Entry lines</CardTitle>
            </CardHeader>
            <CardContent>
              {loadingAccounts ? (
                <div className="space-y-3">
                  <Skeleton className="h-6 w-48" />
                  <Skeleton className="h-72 w-full" />
                </div>
              ) : (
                <VoucherLineGrid
                  accounts={accounts}
                  lines={lines}
                  onLinesChange={(updated) => {
                    setLines(() => updated)
                    // Don't clear validation map immediately - let debounced validation update it
                  }}
                  validationMap={validationMap}
                  lockedAccountIds={lockedAccountIds}
                  readOnly={formDisabled}
                  loading={validating}
                  onUndo={undo}
                  onRedo={redo}
                  canUndo={canUndo}
                  canRedo={canRedo}
                />
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Attachments</CardTitle>
            </CardHeader>
            <CardContent>
              <VoucherAttachmentDropzone
                voucherId={voucherId || null}
                disabled={formDisabled}
                onUploadSuccess={(attachmentFile) => {
                  setAttachmentCount((prev) => prev + 1)
                  toast.success(`Uploaded: ${attachmentFile.file.name}`)
                  // Reload voucher to get updated attachment count
                  if (voucherId) {
                    getVoucherById(voucherId)
                      .then((voucher) => {
                        setAttachmentCount(voucher.attachmentCount || 0)
                      })
                      .catch(() => {
                        // Ignore errors
                      })
                  }
                }}
                onUploadError={(attachmentFile, error) => {
                  toast.error(`Cannot upload ${attachmentFile.file.name}`, {
                    description: error,
                  })
                }}
              />
            </CardContent>
          </Card>

          {/* Voucher History - only show when editing existing voucher */}
          {isEditing && voucherId && <VoucherHistoryView voucherId={voucherId} />}
        </form>
      </Form>

      <VoucherTemplateSelector
        open={templateDialogOpen}
        onOpenChange={setTemplateDialogOpen}
        onTemplateApplied={handleTemplateApplied}
        isApplying={applyingTemplate}
      />

      {/* Validation Summary Modal */}
      <Dialog open={validationSummaryOpen} onOpenChange={setValidationSummaryOpen}>
        <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <AlertCircle className="h-5 w-5 text-destructive" />
              Validation error summary
            </DialogTitle>
            <DialogDescription>
              There are {validationSummary.errorCount} lines with {validationSummary.totalErrors}{' '}
              errors that need to be reviewed lý
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            {Object.entries(validationMap).length === 0 ? (
              <div className="text-center py-8 text-muted-foreground">No validation errors</div>
            ) : (
              <div className="space-y-3">
                {Object.entries(validationMap)
                  .sort(([a], [b]) => Number(a) - Number(b))
                  .map(([lineNum, fieldErrors]) => (
                    <div
                      key={lineNum}
                      className="rounded-lg border border-destructive/50 bg-destructive/5 p-4"
                    >
                      <div className="font-semibold text-destructive mb-3 flex items-center gap-2">
                        <span>Line {lineNum}:</span>
                        <Badge variant="destructive" className="text-xs">
                          {Object.values(fieldErrors).flat().length} errors
                        </Badge>
                      </div>
                      <div className="space-y-2 ml-4">
                        {Object.entries(fieldErrors).map(([field, errors]) => (
                          <div key={field} className="text-sm">
                            <span className="font-medium text-muted-foreground capitalize">
                              {field === 'debitAccount'
                                ? 'Debit account'
                                : field === 'creditAccount'
                                  ? 'Credit account'
                                  : field === 'amount'
                                    ? 'Amount'
                                    : field === 'dimensions'
                                      ? 'Dimensions'
                                      : field === 'voucherDate'
                                        ? 'Voucher date'
                                        : field === 'description'
                                          ? 'Description'
                                          : field === 'attachmentCount'
                                            ? 'Attachment count'
                                            : field}
                            </span>{' '}
                            <span className="text-destructive">
                              {Array.isArray(errors) ? errors.join(', ') : String(errors)}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button onClick={() => setValidationSummaryOpen(false)}>Đóng</Button>
            <Button
              variant="outline"
              onClick={() => {
                setValidationSummaryOpen(false)
                form.handleSubmit(handleValidate)()
              }}
            >
              <ShieldAlert className="mr-2 h-4 w-4" />
              Kiểm tra lại
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Bulk Validation Error Modal (for posting errors) */}
      <Dialog open={postingErrorModalOpen} onOpenChange={setPostingErrorModalOpen}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <AlertCircle className="h-5 w-5 text-destructive" />
              Lỗi xác thực khi ghi sổ
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            {postingErrors && (
              <div className="space-y-3">
                {postingErrors.lines && typeof postingErrors.lines === 'object' && (
                  <div className="space-y-2">
                    {Object.entries(postingErrors.lines).map(([lineNum, errors]: [string, any]) => (
                      <div
                        key={lineNum}
                        className="rounded-lg border border-destructive/50 bg-destructive/10 p-3"
                      >
                        <div className="font-semibold text-destructive mb-2">
                          Dòng {lineNum === '0' ? 'chung' : lineNum}:
                        </div>
                        {typeof errors === 'object' &&
                          Object.entries(errors).map(([field, fieldErrors]: [string, any]) => (
                            <div key={field} className="ml-4 mb-1">
                              <span className="font-medium">{field}:</span>{' '}
                              {Array.isArray(fieldErrors)
                                ? fieldErrors.join(', ')
                                : String(fieldErrors)}
                            </div>
                          ))}
                      </div>
                    ))}
                  </div>
                )}
                {postingErrors.global && Array.isArray(postingErrors.global) && (
                  <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-3">
                    <div className="font-semibold text-destructive mb-2">Lỗi chung:</div>
                    <ul className="list-disc list-inside ml-2">
                      {postingErrors.global.map((error: string, idx: number) => (
                        <li key={idx}>{error}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button onClick={() => setPostingErrorModalOpen(false)}>Đóng</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Unpost Dialog */}
      <Dialog open={unpostDialogOpen} onOpenChange={setUnpostDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Hủy ghi sổ chứng từ</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <Label htmlFor="unpost-reason">Lý do hủy ghi sổ *</Label>
              <Textarea
                id="unpost-reason"
                value={unpostReason}
                onChange={(e) => setUnpostReason(e.target.value)}
                placeholder="Nhập lý do hủy ghi sổ (bắt buộc cho kiểm toán)"
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setUnpostDialogOpen(false)}>
              Hủy
            </Button>
            <Button onClick={handleUnpost} disabled={!unpostReason.trim() || unposting}>
              {unposting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Xác nhận hủy ghi sổ
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reverse Dialog */}
      <Dialog open={reverseDialogOpen} onOpenChange={setReverseDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Đảo ngược chứng từ</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <Label htmlFor="reverse-description">Mô tả phiếu đảo ngược *</Label>
              <Input
                id="reverse-description"
                value={reverseDescription}
                onChange={(e) => setReverseDescription(e.target.value)}
                placeholder="Mô tả cho phiếu đảo ngược"
              />
            </div>
            <div>
              <Label htmlFor="reverse-reason">Lý do đảo ngược *</Label>
              <Textarea
                id="reverse-reason"
                value={reverseReason}
                onChange={(e) => setReverseReason(e.target.value)}
                placeholder="Nhập lý do đảo ngược (bắt buộc cho kiểm toán)"
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReverseDialogOpen(false)}>
              Hủy
            </Button>
            <Button
              onClick={handleReverse}
              disabled={!reverseDescription.trim() || !reverseReason.trim() || reversing}
            >
              {reversing ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Xác nhận đảo ngược
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Attachment Management Modal */}
      {voucherId && (
        <VoucherAttachmentManagementModal
          voucherId={voucherId}
          open={attachmentModalOpen}
          onOpenChange={setAttachmentModalOpen}
          canDelete={editingVoucher?.status === 'draft' && !formDisabled}
          onAttachmentDeleted={() => {
            // Reload attachment count
            if (voucherId) {
              getVoucherById(voucherId)
                .then((voucher) => {
                  setAttachmentCount(voucher.attachmentCount || 0)
                })
                .catch(() => {
                  // Ignore errors
                })
            }
          }}
        />
      )}
    </div>
  )
}
