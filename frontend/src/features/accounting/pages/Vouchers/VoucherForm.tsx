import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
// <CHANGE> Replace MUI with shadcn/ui and lucide-react
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { useToast } from '@/hooks/use-toast'
import { Save, ArrowLeft, Paperclip } from 'lucide-react'
import VoucherLineItemGrid from '@/components/voucher/VoucherLineItemGrid'
import { createVoucher, updateVoucher, getVoucherById, validateVoucher } from '@/services/voucher'
import type { VoucherCreateRequest, VoucherLineDTO, VoucherValidationResult } from '@/types/voucher'
import { getCompanyId } from '@/utils/axios'

interface HistoryState { lines: VoucherLineDTO[]; date: string; description: string; periodId?: number | null }
interface VoucherFormProps { onSave?: () => void; onCancel?: () => void; voucherId?: string }

export default function VoucherForm({ onSave, onCancel, voucherId }: VoucherFormProps = {}) {
  const { id: urlId } = useParams<{ id?: string }>()
  const navigate = useNavigate()
  const id = voucherId || urlId
  const isEditMode = !!id
  const isDialogMode = !!onSave || !!onCancel

  const [date, setDate] = useState<string>(() => new Date().toISOString().split('T')[0])
  const [description, setDescription] = useState<string>('')
  const [periodId, setPeriodId] = useState<number | null>(null)
  const [lines, setLines] = useState<VoucherLineDTO[]>([{ accountId: 0, debit: 0, credit: 0, description: '' }])

  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [validationErrors, setValidationErrors] = useState<Record<number, Record<string, string>>>({})

  const [lastSaved, setLastSaved] = useState<Date | null>(null)
  const [draftSaving, setDraftSaving] = useState(false)
  const autoSaveIntervalRef = useRef<NodeJS.Timeout | null>(null)
  const lastSaveRef = useRef<Date | null>(null)

  const [history, setHistory] = useState<HistoryState[]>([])
  const [historyIndex, setHistoryIndex] = useState(-1)
  const historyRef = useRef<{ state: HistoryState; index: number }[]>([])
  const historyIndexRef = useRef(-1)

  useEffect(() => {
    if (isEditMode && id) {
      const loadVoucher = async () => {
        try {
          setLoading(true)
          const voucher = await getVoucherById(id)
          setDate(voucher.voucherDate.split('T')[0])
          setDescription(voucher.description)
          setPeriodId(voucher.periodId)
          setLines(voucher.lines || [])
          if (voucher.status !== 'draft') setError('Only draft vouchers can be edited')
        } catch (err) {
          setError(err instanceof Error ? err.message : 'Failed to load voucher')
        } finally {
          setLoading(false)
        }
      }
      loadVoucher()
    }
  }, [isEditMode, id])

  useEffect(() => {
    const companyId = getCompanyId()
    const storageKey = `voucherDraft_${companyId}_${id || 'new'}`
    const saved = localStorage.getItem(storageKey)
    if (saved) {
      try {
        const draft = JSON.parse(saved)
        const savedTime = new Date(draft.timestamp)
        const now = new Date()
        if (now.getTime() - savedTime.getTime() < 5 * 60 * 1000) {
          setDate(draft.date || date)
          setDescription(draft.description || '')
          setPeriodId(draft.periodId || null)
          if (draft.lines && draft.lines.length > 0) setLines(draft.lines)
          setLastSaved(savedTime)
        }
      } catch (e) {
        // <CHANGE> add a statement to avoid no-empty: warn and ignore malformed draft
        console.warn('Failed to parse voucher draft from localStorage', e)
      }
    }
  }, [id, date])

  const saveDraftToLocalStorage = useCallback(() => {
    const companyId = getCompanyId()
    const storageKey = `voucherDraft_${companyId}_${id || 'new'}`
    const draft = { date, description, periodId, lines, timestamp: new Date().toISOString() }
    localStorage.setItem(storageKey, JSON.stringify(draft))
  }, [id, date, description, periodId, lines])

  const autoSaveDraft = useCallback(async () => {
    if (saving || loading) return
    try {
      setDraftSaving(true)
      const request: VoucherCreateRequest = {
        date,
        description,
        periodId,
        lines: lines.filter((line) => line.accountId && (line.debit > 0 || line.credit > 0)),
      }
      if (isEditMode && id) await updateVoucher(id, request)
      else {
        const result = await createVoucher(request)
        if (result.id && !isEditMode) navigate(`/vouchers/${result.id}/edit`, { replace: true })
      }
      setLastSaved(new Date())
      lastSaveRef.current = new Date()
      saveDraftToLocalStorage()
      setSuccessMessage('Draft saved automatically')
    } catch (err) {
      // ignore
    } finally {
      setDraftSaving(false)
    }
  }, [date, description, periodId, lines, saving, loading, isEditMode, id, navigate, saveDraftToLocalStorage])

  useEffect(() => {
    autoSaveIntervalRef.current = setInterval(() => { autoSaveDraft() }, 30000)
    return () => { if (autoSaveIntervalRef.current) clearInterval(autoSaveIntervalRef.current) }
  }, [autoSaveDraft])

  const handleBlur = useCallback(() => {
    const timeoutId = setTimeout(() => { autoSaveDraft() }, 1000)
    return () => clearTimeout(timeoutId)
  }, [autoSaveDraft])

  useEffect(() => {
    const handleBeforeUnload = () => { saveDraftToLocalStorage() }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [saveDraftToLocalStorage])

  const validateForm = useCallback(async () => {
    try {
      const request: VoucherCreateRequest = { date, description, periodId, lines }
      const result: VoucherValidationResult = await validateVoucher(request, id)
      setValidationErrors(result.errors || {})
      return result.valid
    } catch {
      return false
    }
  }, [date, description, periodId, lines, id])

  const handleSave = useCallback(async () => {
    try {
      setSaving(true)
      setError(null)
      const isValid = await validateForm()
      if (!isValid) { setError('Please fix validation errors before saving'); return }
      const request: VoucherCreateRequest = {
        date,
        description,
        periodId,
        lines: lines.filter((line) => line.accountId && (line.debit > 0 || line.credit > 0)),
      }
      let result
      if (isEditMode && id) result = await updateVoucher(id, request)
      else {
        result = await createVoucher(request)
        if (result.id && !isDialogMode) navigate(`/vouchers/${result.id}/edit`, { replace: true })
      }
      setLastSaved(new Date())
      saveDraftToLocalStorage()
      setSuccessMessage(isEditMode ? 'Voucher updated successfully' : 'Voucher created successfully')
      const companyId = getCompanyId(); const storageKey = `voucherDraft_${companyId}_${id || 'new'}`; localStorage.removeItem(storageKey)
      if (onSave) onSave()
    } catch (err: any) {
      setError(err?.message || 'Failed to save voucher')
    } finally { setSaving(false) }
  }, [date, description, periodId, lines, isEditMode, id, validateForm, navigate, saveDraftToLocalStorage, isDialogMode, onSave])

  const handleAutoAddLine = useCallback(() => { const newLine: VoucherLineDTO = { accountId: 0, debit: 0, credit: 0, description: '' }; setLines([...lines, newLine]) }, [lines])
  const handleLinesChange = useCallback((newLines: VoucherLineDTO[]) => {
    setLines(newLines)
    const newState: HistoryState = { lines: newLines, date, description, periodId }
    historyRef.current = historyRef.current.slice(0, historyIndexRef.current + 1)
    historyRef.current.push(newState)
    historyIndexRef.current = historyRef.current.length - 1
    if (historyRef.current.length > 50) { historyRef.current.shift(); historyIndexRef.current = historyRef.current.length - 1 }
  }, [date, description, periodId])
  const handleUndo = useCallback(() => { if (historyIndexRef.current > 0) { historyIndexRef.current--; const prev = historyRef.current[historyIndexRef.current]; setLines(prev.lines); setDate(prev.date); setDescription(prev.description); setPeriodId(prev.periodId) } }, [])
  const handleRedo = useCallback(() => { if (historyIndexRef.current < historyRef.current.length - 1) { historyIndexRef.current++; const next = historyRef.current[historyIndexRef.current]; setLines(next.lines); setDate(next.date); setDescription(next.description); setPeriodId(next.periodId) } }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.key === 's') { e.preventDefault(); handleSave() }
      if (e.ctrlKey && e.key === 'z' && !e.shiftKey) { e.preventDefault(); handleUndo() }
      if ((e.ctrlKey && e.shiftKey && e.key === 'z') || (e.ctrlKey && e.key === 'y')) { e.preventDefault(); handleRedo() }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [handleSave, handleUndo, handleRedo])

  if (loading) return (<div className="p-4 text-center text-sm text-muted-foreground">Loading…</div>)

  const handleCancel = () => { if (onCancel) onCancel(); else navigate('/vouchers') }

  return (
    <div className={isDialogMode ? '' : 'p-3'}>
      {!isDialogMode && (
        <div className="mb-3 flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={() => navigate('/vouchers')} aria-label="Back">
            <ArrowLeft className="size-4" />
          </Button>
          <h2 className="text-xl font-semibold">{isEditMode ? 'Edit Voucher' : 'Create New Voucher'}</h2>
          {lastSaved && (<Badge variant="secondary">Draft saved {lastSaved.toLocaleTimeString()}</Badge>)}
          {draftSaving && (<Badge variant="outline">Saving draft…</Badge>)}
        </div>
      )}

      {isDialogMode && (
        <div className="mb-2 flex items-center gap-2">
          <h3 className="text-lg font-medium">{isEditMode ? 'Edit Voucher' : 'Create New Voucher'}</h3>
          {lastSaved && (<Badge variant="secondary">Draft saved {lastSaved.toLocaleTimeString()}</Badge>)}
          {draftSaving && (<Badge variant="outline">Saving draft…</Badge>)}
        </div>
      )}

      {error && (
        <Alert variant="destructive" className="mb-2" onClick={() => setError(null)}>
          <AlertTitle>Error</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      {/* Toast for success */}
      {successMessage && (
        <Alert className="mb-2" onClick={() => setSuccessMessage(null)}>
          <AlertTitle>Success</AlertTitle>
          <AlertDescription>{successMessage}</AlertDescription>
        </Alert>
      )}

      <div className="rounded-md border p-3">
        <div className="mb-3 flex flex-wrap gap-3">
          <div className="min-w-[200px]">
            <label className="mb-1 block text-sm font-medium">Voucher Date</label>
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} onBlur={handleBlur} required />
          </div>
          <div className="flex-1 min-w-[260px]">
            <label className="mb-1 block text-sm font-medium">Description</label>
            <Input value={description} onChange={(e) => setDescription(e.target.value)} onBlur={handleBlur} required />
          </div>
          <div className="min-w-[120px]">
            <label className="mb-1 block text-sm font-medium">Currency</label>
            <Input value="VND" readOnly />
            <div className="text-xs text-muted-foreground">Read-only</div>
          </div>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <span>
                  <Button variant="outline" size="icon" disabled>
                    <Paperclip className="size-4" />
                  </Button>
                </span>
              </TooltipTrigger>
              <TooltipContent>Attachments (Coming in Story 3.7)</TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>

        <VoucherLineItemGrid lines={lines} onChange={handleLinesChange} errors={validationErrors} onAutoAddLine={handleAutoAddLine} disabled={saving} />

        <div className="mt-3 flex justify-end gap-2">
          <Button variant="outline" onClick={handleCancel}>Cancel</Button>
          <Button onClick={handleSave} disabled={saving}>
            <Save className="mr-2 size-4" /> {saving ? 'Saving...' : isEditMode ? 'Update Draft' : 'Save Draft'}
          </Button>
        </div>
      </div>
    </div>
  )
}

