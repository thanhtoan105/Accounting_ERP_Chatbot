import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Alert,
  CircularProgress,
  Chip,
  IconButton,
  Tooltip,
  Snackbar,
} from '@mui/material'
import SaveIcon from '@mui/icons-material/Save'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import AttachFileIcon from '@mui/icons-material/AttachFile'
import VoucherLineItemGrid from '../components/voucher/VoucherLineItemGrid'
import { createVoucher, updateVoucher, getVoucherById, validateVoucher } from '../services/voucher'
import type {
  VoucherCreateRequest,
  VoucherLineDTO,
  VoucherValidationResult,
} from '../types/voucher'
import { getCompanyId } from '../utils/axios'

interface HistoryState {
  lines: VoucherLineDTO[]
  date: string
  description: string
  periodId?: number | null
}

interface VoucherFormProps {
  onSave?: () => void
  onCancel?: () => void
  voucherId?: string
}

/**
 * Voucher Form page for creating and editing vouchers.
 * Supports draft auto-save, validation, undo/redo, and keyboard navigation.
 * Can be used as a standalone page or embedded in a dialog.
 */
export default function VoucherForm({ onSave, onCancel, voucherId }: VoucherFormProps = {}) {
  const { id: urlId } = useParams<{ id?: string }>()
  const navigate = useNavigate()
  const id = voucherId || urlId
  const isEditMode = !!id
  const isDialogMode = !!onSave || !!onCancel

  // Form state
  const [date, setDate] = useState<string>(() => {
    // Default to today
    return new Date().toISOString().split('T')[0]
  })
  const [description, setDescription] = useState<string>('')
  const [periodId, setPeriodId] = useState<number | null>(null)
  const [lines, setLines] = useState<VoucherLineDTO[]>([
    {
      accountId: 0, // Placeholder - will require selection
      debit: 0,
      credit: 0,
      description: '',
    },
  ])

  // UI state
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [validationErrors, setValidationErrors] = useState<Record<number, Record<string, string>>>(
    {},
  )

  // Draft auto-save
  const [lastSaved, setLastSaved] = useState<Date | null>(null)
  const [draftSaving, setDraftSaving] = useState(false)
  const autoSaveIntervalRef = useRef<NodeJS.Timeout | null>(null)
  const lastSaveRef = useRef<Date | null>(null)

  // Undo/redo history
  const [history, setHistory] = useState<HistoryState[]>([])
  const [historyIndex, setHistoryIndex] = useState(-1)
  const historyRef = useRef<{ state: HistoryState; index: number }[]>([])
  const historyIndexRef = useRef(-1)

  // Load voucher for edit mode
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
          if (voucher.status !== 'draft') {
            setError('Only draft vouchers can be edited')
          }
        } catch (err) {
          setError(err instanceof Error ? err.message : 'Failed to load voucher')
        } finally {
          setLoading(false)
        }
      }
      loadVoucher()
    }
  }, [isEditMode, id])

  // Load draft from localStorage on mount
  useEffect(() => {
    const companyId = getCompanyId()
    const storageKey = `voucherDraft_${companyId}_${id || 'new'}`
    const saved = localStorage.getItem(storageKey)
    if (saved) {
      try {
        const draft = JSON.parse(saved)
        const savedTime = new Date(draft.timestamp)
        const now = new Date()
        // Only restore if saved within last 5 minutes
        if (now.getTime() - savedTime.getTime() < 5 * 60 * 1000) {
          setDate(draft.date || date)
          setDescription(draft.description || '')
          setPeriodId(draft.periodId || null)
          if (draft.lines && draft.lines.length > 0) {
            setLines(draft.lines)
          }
          setLastSaved(savedTime)
        }
      } catch (err) {
        console.error('Failed to load draft from localStorage', err)
      }
    }
  }, [id, date])

  // Save draft to localStorage
  const saveDraftToLocalStorage = useCallback(() => {
    const companyId = getCompanyId()
    const storageKey = `voucherDraft_${companyId}_${id || 'new'}`
    const draft = {
      date,
      description,
      periodId,
      lines,
      timestamp: new Date().toISOString(),
    }
    localStorage.setItem(storageKey, JSON.stringify(draft))
  }, [id, date, description, periodId, lines])

  // Auto-save draft
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

      if (isEditMode && id) {
        await updateVoucher(id, request)
      } else {
        const result = await createVoucher(request)
        // Update URL to edit mode if we got an ID back
        if (result.id && !isEditMode) {
          navigate(`/vouchers/${result.id}/edit`, { replace: true })
        }
      }

      setLastSaved(new Date())
      lastSaveRef.current = new Date()
      saveDraftToLocalStorage()
      setSuccessMessage('Draft saved automatically')
    } catch (err) {
      console.error('Auto-save failed', err)
      // Don't show error to user for auto-save failures
    } finally {
      setDraftSaving(false)
    }
  }, [
    date,
    description,
    periodId,
    lines,
    saving,
    loading,
    isEditMode,
    id,
    navigate,
    saveDraftToLocalStorage,
  ])

  // Setup auto-save interval (30 seconds)
  useEffect(() => {
    autoSaveIntervalRef.current = setInterval(() => {
      autoSaveDraft()
    }, 30000) // 30 seconds

    return () => {
      if (autoSaveIntervalRef.current) {
        clearInterval(autoSaveIntervalRef.current)
      }
    }
  }, [autoSaveDraft])

  // Save draft on blur
  const handleBlur = useCallback(() => {
    // Debounce auto-save on blur
    const timeoutId = setTimeout(() => {
      autoSaveDraft()
    }, 1000)
    return () => clearTimeout(timeoutId)
  }, [autoSaveDraft])

  // Save draft on browser close
  useEffect(() => {
    const handleBeforeUnload = () => {
      saveDraftToLocalStorage()
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [saveDraftToLocalStorage])

  // Validate voucher
  const validateForm = useCallback(async () => {
    try {
      const request: VoucherCreateRequest = {
        date,
        description,
        periodId,
        lines,
      }
      const result = await validateVoucher(request, id)
      setValidationErrors(result.errors || {})
      return result.valid
    } catch (err) {
      console.error('Validation failed', err)
      return false
    }
  }, [date, description, periodId, lines, id])

  // Save voucher (manual save)
  const handleSave = useCallback(async () => {
    try {
      setSaving(true)
      setError(null)

      // Validate first
      const isValid = await validateForm()
      if (!isValid) {
        setError('Please fix validation errors before saving')
        return
      }

      const request: VoucherCreateRequest = {
        date,
        description,
        periodId,
        lines: lines.filter((line) => line.accountId && (line.debit > 0 || line.credit > 0)),
      }

      let result
      if (isEditMode && id) {
        result = await updateVoucher(id, request)
      } else {
        result = await createVoucher(request)
        // Navigate to edit mode if we created a new voucher (only if not in dialog mode)
        if (result.id && !isDialogMode) {
          navigate(`/vouchers/${result.id}/edit`, { replace: true })
        }
      }

      setLastSaved(new Date())
      saveDraftToLocalStorage()
      setSuccessMessage(
        isEditMode ? 'Voucher updated successfully' : 'Voucher created successfully',
      )

      // Clear localStorage draft after successful save
      const companyId = getCompanyId()
      const storageKey = `voucherDraft_${companyId}_${id || 'new'}`
      localStorage.removeItem(storageKey)

      // Call onSave callback if provided (dialog mode)
      if (onSave) {
        onSave()
      }
    } catch (err: any) {
      setError(err?.message || 'Failed to save voucher')
    } finally {
      setSaving(false)
    }
  }, [
    date,
    description,
    periodId,
    lines,
    isEditMode,
    id,
    validateForm,
    navigate,
    saveDraftToLocalStorage,
  ])

  // Add new line
  const handleAutoAddLine = useCallback(() => {
    const newLine: VoucherLineDTO = {
      accountId: 0,
      debit: 0,
      credit: 0,
      description: '',
    }
    setLines([...lines, newLine])
  }, [lines])

  // Handle lines change
  const handleLinesChange = useCallback(
    (newLines: VoucherLineDTO[]) => {
      setLines(newLines)
      // Add to history for undo/redo
      const newState: HistoryState = {
        lines: newLines,
        date,
        description,
        periodId,
      }
      historyRef.current = historyRef.current.slice(0, historyIndexRef.current + 1)
      historyRef.current.push(newState)
      historyIndexRef.current = historyRef.current.length - 1
      // Limit history to last 50 states
      if (historyRef.current.length > 50) {
        historyRef.current.shift()
        historyIndexRef.current = historyRef.current.length - 1
      }
    },
    [date, description, periodId],
  )

  // Undo
  const handleUndo = useCallback(() => {
    if (historyIndexRef.current > 0) {
      historyIndexRef.current--
      const prevState = historyRef.current[historyIndexRef.current]
      setLines(prevState.lines)
      setDate(prevState.date)
      setDescription(prevState.description)
      setPeriodId(prevState.periodId)
    }
  }, [])

  // Redo
  const handleRedo = useCallback(() => {
    if (historyIndexRef.current < historyRef.current.length - 1) {
      historyIndexRef.current++
      const nextState = historyRef.current[historyIndexRef.current]
      setLines(nextState.lines)
      setDate(nextState.date)
      setDescription(nextState.description)
      setPeriodId(nextState.periodId)
    }
  }, [])

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Ctrl+S: Save
      if (e.ctrlKey && e.key === 's') {
        e.preventDefault()
        handleSave()
      }
      // Ctrl+Z: Undo
      if (e.ctrlKey && e.key === 'z' && !e.shiftKey) {
        e.preventDefault()
        handleUndo()
      }
      // Ctrl+Shift+Z or Ctrl+Y: Redo
      if ((e.ctrlKey && e.shiftKey && e.key === 'z') || (e.ctrlKey && e.key === 'y')) {
        e.preventDefault()
        handleRedo()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [handleSave, handleUndo, handleRedo])

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    )
  }

  // Handle cancel
  const handleCancel = () => {
    if (onCancel) {
      onCancel()
    } else {
      navigate('/vouchers')
    }
  }

  return (
    <Box sx={{ p: isDialogMode ? 0 : 3 }}>
      {/* Header */}
      {!isDialogMode && (
        <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', gap: 2 }}>
          <IconButton onClick={() => navigate('/vouchers')} size="small">
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h5">{isEditMode ? 'Edit Voucher' : 'Create New Voucher'}</Typography>
          {lastSaved && (
            <Chip
              label={`Draft saved ${lastSaved.toLocaleTimeString()}`}
              size="small"
              color="success"
              variant="outlined"
            />
          )}
          {draftSaving && (
            <Chip label="Saving draft..." size="small" icon={<CircularProgress size={16} />} />
          )}
        </Box>
      )}

      {/* Dialog Mode Header */}
      {isDialogMode && (
        <Box sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 2 }}>
          <Typography variant="h6">{isEditMode ? 'Edit Voucher' : 'Create New Voucher'}</Typography>
          {lastSaved && (
            <Chip
              label={`Draft saved ${lastSaved.toLocaleTimeString()}`}
              size="small"
              color="success"
              variant="outlined"
            />
          )}
          {draftSaving && (
            <Chip label="Saving draft..." size="small" icon={<CircularProgress size={16} />} />
          )}
        </Box>
      )}

      {/* Error Alert */}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Success Message */}
      <Snackbar
        open={!!successMessage}
        autoHideDuration={3000}
        onClose={() => setSuccessMessage(null)}
        message={successMessage}
      />

      {/* Form */}
      <Paper sx={{ p: 3 }}>
        {/* Header Fields */}
        <Box sx={{ mb: 3, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          <TextField
            label="Voucher Date"
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            onBlur={handleBlur}
            InputLabelProps={{ shrink: true }}
            required
            sx={{ minWidth: 200 }}
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            onBlur={handleBlur}
            required
            fullWidth
            inputProps={{ maxLength: 500 }}
          />
          <TextField
            label="Currency"
            value="VND"
            InputProps={{ readOnly: true }}
            sx={{ minWidth: 120 }}
            helperText="Read-only"
          />
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Tooltip title="Attachments (Coming in Story 3.7)">
              <span>
                <IconButton disabled>
                  <AttachFileIcon />
                </IconButton>
              </span>
            </Tooltip>
          </Box>
        </Box>

        {/* Line Items Grid */}
        <VoucherLineItemGrid
          lines={lines}
          onChange={handleLinesChange}
          errors={validationErrors}
          onAutoAddLine={handleAutoAddLine}
          disabled={saving}
        />

        {/* Actions */}
        <Box sx={{ mt: 3, display: 'flex', gap: 2, justifyContent: 'flex-end' }}>
          <Button variant="outlined" onClick={handleCancel}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleSave}
            disabled={saving}
            startIcon={saving ? <CircularProgress size={20} /> : <SaveIcon />}
          >
            {saving ? 'Saving...' : isEditMode ? 'Update Draft' : 'Save Draft'}
          </Button>
        </Box>
      </Paper>
    </Box>
  )
}
