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
import { AlertCircle, CalendarIcon, Loader2, Save, ShieldAlert, Sparkles, FileText } from 'lucide-react'
import { useParams } from 'react-router-dom'
import { z } from 'zod'

import {
  VoucherLineGrid,
  type VoucherEntryLine,
  VoucherTemplateSelector,
  VoucherAttachmentDropzone,
  type AttachmentFile,
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
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { Calendar } from '@/components/ui/calendar'
import { toast } from 'sonner'

import type { AccountSummary } from '@/components/account/AccountPicker'
import useUndoRedo from '@/hooks/useUndoRedo'
import { useAuth } from '@/hooks/useAuth'
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
} from '@/services/voucher'
import { getPostableAccounts } from '@/services/chartOfAccounts'
import type { ChartOfAccount } from '@/types/chartOfAccount'

const formSchema = z.object({
  voucherDate: z.string({ required_error: 'Ngày chứng từ bắt buộc' }),
  description: z
    .string()
    .max(500, 'Mô tả tối đa 500 ký tự')
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
      balanceSide:
        account.normalSide?.toLowerCase().includes('debit')
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

  const sorted = [...ledgerLines].sort(
    (a, b) => (a.lineNumber ?? 0) - (b.lineNumber ?? 0),
  )
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
          : second ?? first

    entries.push({
      id: `loaded-${i}`,
      debitAccount: getAccountById(accounts, debitLine?.accountId),
      creditAccount: getAccountById(accounts, creditLine?.accountId),
      amount: Number(debitLine?.debit ?? creditLine?.credit ?? 0),
      description: debitLine?.description || creditLine?.description || '',
      customerId:
        (debitLine?.customerId ?? creditLine?.customerId)?.toString() ?? null,
      supplierId:
        (debitLine?.vendorId ?? creditLine?.vendorId)?.toString() ?? null,
      costCenterId:
        (debitLine?.costCenterId ?? creditLine?.costCenterId)?.toString() ?? null,
      customer: buildDimensionOption(
        debitLine?.customerId ?? creditLine?.customerId,
        'KH',
      ),
      supplier: buildDimensionOption(
        debitLine?.vendorId ?? creditLine?.vendorId,
        'NCC',
      ),
      costCenter: buildDimensionOption(
        debitLine?.costCenterId ?? creditLine?.costCenterId,
        'TTCP',
      ),
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
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [loadingAccounts, setLoadingAccounts] = useState(true)
  const [autoSaveStatus, setAutoSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(
    initialState.updatedAt ? new Date(initialState.updatedAt) : null,
  )
  const [draftLock, setDraftLock] = useState<DraftLock | null>(initialState.lock)
  const [isLocked, setIsLocked] = useState(() =>
    !isEditing && isLockedByOther(initialState.lock, currentUserId),
  )
  const [lockCountdown, setLockCountdown] = useState<string | null>(null)
  const [loadingVoucher, setLoadingVoucher] = useState(false)
  const [editingVoucher, setEditingVoucher] = useState<VoucherDTO | null>(null)
  const [autoSaveError, setAutoSaveError] = useState<string | null>(null)
  const [attachmentCount, setAttachmentCount] = useState(0)
  const [applyingTemplate, setApplyingTemplate] = useState(false)
  const today = useMemo(() => new Date(), [])
  const openPeriodRange = useMemo(() => {
    const fiscalStartBase = company?.fiscalYearStart ? parseISO(company.fiscalYearStart) : startOfMonth(new Date(today.getFullYear(), 0, 1))
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
        toast.error('Không thể tải danh mục tài khoản', {
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
      voucherDate: values.voucherDate,
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
        setAutoSaveError(error?.message || 'Không thể lưu nháp')
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
        resetLines(convertLedgerLinesToEntries(voucher.lines, accounts))
        setEditingVoucher(voucher)
        setValidationMap({})
        setLockedAccountIds([])
        setAttachmentCount(voucher.attachmentCount || 0)
      })
      .catch((error) => {
        console.error(error)
        toast.error('Không thể tải chứng từ để chỉnh sửa')
      })
      .finally(() => {
        if (mounted) setLoadingVoucher(false)
      })
    return () => {
      mounted = false
    }
  }, [accounts, form, isEditing, loadingAccounts, resetLines, voucherId])

  async function handleValidate(values: VoucherFormValues) {
    try {
      setSaving(true)
      const payload = buildRequest(values)
      const result = await validateVoucher(payload, voucherId)
      setValidationMap(result.errors || {})
      if (result.valid) {
        toast.success('Tất cả dòng chứng từ hợp lệ')
      } else {
        toast.warning('Một số dòng cần kiểm tra lại')
      }
    } catch (err: any) {
      toast.error('Không thể xác thực chứng từ', { description: err?.message })
    } finally {
      setSaving(false)
    }
  }

  async function handleSave(values: VoucherFormValues) {
    try {
      setSaving(true)
      const payload = buildRequest(values)
      const response = isEditing && voucherId ? await updateVoucher(voucherId, payload) : await createVoucher(payload)
      toast.success(isEditing ? 'Đã cập nhật chứng từ' : 'Đã lưu nháp chứng từ', {
        description: `Mã chứng từ: ${response.voucherNumber}`,
      })
      if (!isEditing) {
        removeVoucherDraft(draftStorageKey)
        setLastSavedAt(null)
      }
      setValidationMap({})
    } catch (err: any) {
      toast.error('Không thể lưu chứng từ', { description: err?.message })
    } finally {
      setSaving(false)
    }
  }

  function applyTemplateLines(template: VoucherTemplateDTO, overrideEntries?: VoucherEntryLine[]) {
    if (!template?.lines && !overrideEntries?.length) return
    const lockedIds = new Set<string>()
    const mapped =
      template.lines?.map<VoucherEntryLine>((line) => {
      const debitAccount = accounts.find((account) => account.id === line.debitAccountId) || null
      const creditAccount = accounts.find((account) => account.id === line.creditAccountId) || null
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
      toast.info('Chỉ có thể áp dụng mẫu khi tạo phiếu mới.')
      return
    }
    if (!accounts.length) {
      toast.error('Chưa tải xong danh mục tài khoản, vui lòng thử lại.')
      return
    }
    const voucherDate = form.getValues('voucherDate')
    if (!voucherDate) {
      toast.error('Vui lòng chọn ngày chứng từ trước khi áp dụng mẫu.')
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
      toast.success(`Đã áp dụng mẫu ${appliedTemplate.name}`)
      setTemplateDialogOpen(false)
    } catch (error: any) {
      console.error('Failed to apply template via API', error)
      toast.error('Không thể áp dụng mẫu từ máy chủ', {
        description: error?.message || error?.code || 'Vui lòng thử lại sau.',
      })
      applyTemplateLines(template)
      setTemplateDialogOpen(false)
    } finally {
      setApplyingTemplate(false)
    }
  }

  const onSubmit = (values: VoucherFormValues) => handleSave(values)
  const formDisabled = saving || loadingAccounts || loadingVoucher || isLocked
  const isDateDisabled = useCallback(
    (date: Date) => {
      if (selectedDate && isSameDay(date, selectedDate)) return false
      if (isBefore(date, openPeriodRange.openStart)) return true
      if (isAfter(date, openPeriodRange.openEnd)) return true
      return false
    },
    [openPeriodRange.openEnd, openPeriodRange.openStart, selectedDate],
  )

  return (
    <div className="space-y-6">
      {isLocked && draftLock ? (
        <Alert variant="destructive">
          <AlertTitle>Phiếu đang bị khóa</AlertTitle>
          <AlertDescription className="flex flex-wrap items-center gap-2 text-sm">
            {draftLock.ownerName || 'Người dùng khác'} đang chỉnh sửa bản nháp này. Khóa sẽ hết hạn sau{' '}
            <span className="font-semibold">{lockCountdown ?? '—'}</span>.
            <Button
              size="sm"
              variant="outline"
              disabled={Boolean(lockCountdown) && lockCountdown !== '00:00'}
              onClick={claimLock}
            >
              Yêu cầu quyền chỉnh sửa
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold">
              {isEditing ? 'Chỉnh sửa phiếu kế toán' : 'Tạo phiếu kế toán'}
            </h1>
            {editingVoucher?.status ? (
              <Badge variant="outline" className="uppercase">
                {editingVoucher.status}
              </Badge>
            ) : null}
            {editingVoucher?.voucherNumber ? (
              <Badge variant="secondary">{editingVoucher.voucherNumber}</Badge>
            ) : null}
            {attachmentCount > 0 && (
              <Badge variant="outline" className="gap-1">
                <FileText className="size-3" />
                {attachmentCount} đính kèm
              </Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground">
            Nhập nhanh bằng bàn phím, áp dụng mẫu có sẵn và tự động kiểm tra trước khi ghi sổ.
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
            Áp dụng mẫu
          </Button>
          <Button
            variant="outline"
            type="button"
            onClick={form.handleSubmit(handleValidate)}
            disabled={formDisabled}
          >
            {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <ShieldAlert className="mr-2 h-4 w-4" />}
            Kiểm tra
          </Button>
          <Button type="button" onClick={form.handleSubmit(onSubmit)} disabled={formDisabled}>
            {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
            Lưu nháp
          </Button>
        </div>
      </div>
      <div className="text-xs text-muted-foreground">
        {autoSaveStatus === 'saving' && 'Đang lưu nháp...'}
        {autoSaveStatus === 'saved' && lastSavedAt && `Đã lưu nháp lúc ${formatDistanceToNow(lastSavedAt, { addSuffix: true })}`}
        {autoSaveStatus === 'error' && (
          <span className="text-destructive flex items-center gap-1">
            <AlertCircle className="h-3 w-3" />
            {autoSaveError || 'Không thể lưu nháp'}
          </span>
        )}
      </div>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Thông tin chung</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 md:grid-cols-2">
              <FormField
                control={form.control}
                name="voucherDate"
                render={({ field }) => (
                  <FormItem className="flex flex-col">
                    <FormLabel>Ngày chứng từ</FormLabel>
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
                              : 'Chọn ngày'}
                            <CalendarIcon className="ml-2 h-4 w-4 opacity-50" />
                          </Button>
                        </FormControl>
                      </PopoverTrigger>
                      <PopoverContent className="w-auto p-0" align="start">
                        <Calendar
                          mode="single"
                          selected={field.value ? new Date(field.value) : undefined}
                          onSelect={(date) => field.onChange(date ? format(date, 'yyyy-MM-dd') : '')}
                          month={calendarMonth}
                          onMonthChange={setCalendarMonth}
                          disabled={isDateDisabled}
                          initialFocus
                        />
                      </PopoverContent>
                    </Popover>
                    <FormDescription>
                      Chỉ được chọn ngày trong khoảng{' '}
                      {format(openPeriodRange.openStart, 'dd/MM/yyyy')} –{' '}
                      {format(openPeriodRange.openEnd, 'dd/MM/yyyy')}.
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="description"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Mô tả chung</FormLabel>
                    <FormControl>
                      <Textarea
                        rows={3}
                        placeholder="VD: Thu tiền mặt của khách hàng..."
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
              <CardTitle>Dòng định khoản</CardTitle>
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
                    setValidationMap({})
                  }}
                  validationMap={validationMap}
                  lockedAccountIds={lockedAccountIds}
                  readOnly={formDisabled}
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
              <CardTitle>Đính kèm</CardTitle>
            </CardHeader>
            <CardContent>
              <VoucherAttachmentDropzone
                voucherId={voucherId || null}
                disabled={formDisabled}
                onUploadSuccess={(attachmentFile) => {
                  setAttachmentCount((prev) => prev + 1)
                  toast.success(`Đã tải lên: ${attachmentFile.file.name}`)
                }}
                onUploadError={(attachmentFile, error) => {
                  toast.error(`Không thể tải lên ${attachmentFile.file.name}`, {
                    description: error,
                  })
                }}
              />
            </CardContent>
          </Card>
        </form>
      </Form>

      <VoucherTemplateSelector
        open={templateDialogOpen}
        onOpenChange={setTemplateDialogOpen}
        onTemplateApplied={handleTemplateApplied}
        isApplying={applyingTemplate}
      />
    </div>
  )
}

