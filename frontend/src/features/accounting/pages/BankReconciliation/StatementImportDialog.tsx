'use client'

import { useState, useRef, useEffect, useCallback } from 'react'
import { Upload, FileText, Download, AlertCircle, X, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Progress } from '@/components/ui/progress'
import { cn } from '@/lib/utils'

import {
  analyzeStatementFile,
  importStatement,
  downloadImportErrors,
  downloadBlob,
  type ColumnMappingSuggestion,
  type StatementImportRequest,
  type StatementImportResult,
  type ImportError,
} from '../../services/bankReconciliation'

// ============================================================================
// Types
// ============================================================================

type WizardStep = 'upload' | 'mapping' | 'preview' | 'result'

interface StatementImportDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  reconciliationId: string
  bankAccountName: string
  onSuccess?: () => void
}

// ============================================================================
// Step Indicator Component
// ============================================================================

interface StepIndicatorProps {
  currentStep: WizardStep
  steps: { key: WizardStep; label: string }[]
}

function StepIndicator({ currentStep, steps }: StepIndicatorProps) {
  const stepOrder: WizardStep[] = ['upload', 'mapping', 'preview', 'result']
  const currentIndex = stepOrder.indexOf(currentStep)

  return (
    <div className="flex items-center justify-between mb-6">
      {steps.map((step, index) => {
        const stepIndex = stepOrder.indexOf(step.key)
        const isCompleted = stepIndex < currentIndex
        const isActive = step.key === currentStep

        return (
          <div key={step.key} className="flex items-center">
            <div className="flex items-center">
              <div
                className={cn(
                  'w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium transition-colors',
                  isCompleted && 'bg-green-500 text-white',
                  isActive && 'bg-primary text-primary-foreground',
                  !isCompleted && !isActive && 'bg-muted text-muted-foreground',
                )}
              >
                {isCompleted ? <CheckCircle2 className="h-5 w-5" /> : index + 1}
              </div>
              <span
                className={cn(
                  'ml-2 text-sm hidden sm:inline',
                  isActive && 'font-semibold',
                  !isActive && 'text-muted-foreground',
                )}
              >
                {step.label}
              </span>
            </div>
            {index < steps.length - 1 && (
              <div
                className={cn(
                  'w-12 h-0.5 mx-2',
                  stepIndex < currentIndex ? 'bg-green-500' : 'bg-muted',
                )}
              />
            )}
          </div>
        )
      })}
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function StatementImportDialog({
  open,
  onOpenChange,
  reconciliationId,
  bankAccountName,
  onSuccess,
}: StatementImportDialogProps) {
  const { t } = useTranslation()

  // Wizard state
  const [step, setStep] = useState<WizardStep>('upload')

  // File upload state
  const [file, setFile] = useState<File | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Analysis state
  const [analyzing, setAnalyzing] = useState(false)
  const [columnSuggestions, setColumnSuggestions] = useState<ColumnMappingSuggestion | null>(null)

  // Column mapping state
  const [dateColumn, setDateColumn] = useState<string>('')
  const [descriptionColumn, setDescriptionColumn] = useState<string>('')
  const [referenceColumn, setReferenceColumn] = useState<string>('')
  const [debitColumn, setDebitColumn] = useState<string>('')
  const [creditColumn, setCreditColumn] = useState<string>('')
  const [balanceColumn, setBalanceColumn] = useState<string>('')
  const [dateFormat, setDateFormat] = useState<string>('yyyy-MM-dd')
  const [skipHeaderRows, setSkipHeaderRows] = useState<number>(1)
  const [saveFormatProfile, setSaveFormatProfile] = useState<boolean>(false)
  const [formatProfileName, setFormatProfileName] = useState<string>('')

  // Preview state (parsed from first few rows)
  const [previewData, setPreviewData] = useState<string[][]>([])

  // Import state
  const [importing, setImporting] = useState(false)
  const [importResult, setImportResult] = useState<StatementImportResult | null>(null)

  // Steps configuration
  const steps = [
    { key: 'upload' as WizardStep, label: t('bankReconciliation.import.step1') },
    { key: 'mapping' as WizardStep, label: t('bankReconciliation.import.step2') },
    { key: 'preview' as WizardStep, label: t('bankReconciliation.import.step3') },
    { key: 'result' as WizardStep, label: t('bankReconciliation.import.step4') },
  ]

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setStep('upload')
      setFile(null)
      setColumnSuggestions(null)
      setDateColumn('')
      setDescriptionColumn('')
      setReferenceColumn('')
      setDebitColumn('')
      setCreditColumn('')
      setBalanceColumn('')
      setDateFormat('yyyy-MM-dd')
      setSkipHeaderRows(1)
      setSaveFormatProfile(false)
      setFormatProfileName('')
      setPreviewData([])
      setImportResult(null)
    }
  }, [open])

  // Apply suggestions when received
  useEffect(() => {
    if (columnSuggestions) {
      if (columnSuggestions.suggestedDateColumn)
        setDateColumn(columnSuggestions.suggestedDateColumn)
      if (columnSuggestions.suggestedDescriptionColumn)
        setDescriptionColumn(columnSuggestions.suggestedDescriptionColumn)
      if (columnSuggestions.suggestedReferenceColumn)
        setReferenceColumn(columnSuggestions.suggestedReferenceColumn)
      if (columnSuggestions.suggestedDebitColumn)
        setDebitColumn(columnSuggestions.suggestedDebitColumn)
      if (columnSuggestions.suggestedCreditColumn)
        setCreditColumn(columnSuggestions.suggestedCreditColumn)
      if (columnSuggestions.suggestedBalanceColumn)
        setBalanceColumn(columnSuggestions.suggestedBalanceColumn)
      if (columnSuggestions.suggestedDateFormat)
        setDateFormat(columnSuggestions.suggestedDateFormat)

      // If saved profile exists, use it
      if (columnSuggestions.savedProfile) {
        const profile = columnSuggestions.savedProfile
        if (profile.dateColumn) setDateColumn(profile.dateColumn)
        if (profile.descriptionColumn) setDescriptionColumn(profile.descriptionColumn)
        if (profile.referenceColumn) setReferenceColumn(profile.referenceColumn)
        if (profile.debitColumn) setDebitColumn(profile.debitColumn)
        if (profile.creditColumn) setCreditColumn(profile.creditColumn)
        if (profile.balanceColumn) setBalanceColumn(profile.balanceColumn)
        if (profile.dateFormat) setDateFormat(profile.dateFormat)
        if (profile.skipHeaderRows) setSkipHeaderRows(profile.skipHeaderRows)
      }
    }
  }, [columnSuggestions])

  // ============================================================================
  // File Handling
  // ============================================================================

  const validateFile = useCallback(
    (selectedFile: File): boolean => {
      // Check file type
      const validExtensions = ['.csv', '.xlsx', '.xls']
      const fileExtension = selectedFile.name
        .substring(selectedFile.name.lastIndexOf('.'))
        .toLowerCase()
      if (!validExtensions.includes(fileExtension)) {
        toast.error(t('bankReconciliation.import.supportedFormats'))
        return false
      }

      // Check file size (10MB max)
      const maxSize = 10 * 1024 * 1024
      if (selectedFile.size > maxSize) {
        toast.error(t('bankReconciliation.import.maxFileSize'))
        return false
      }

      return true
    },
    [t],
  )

  const handleFileSelect = useCallback(
    (selectedFile: File) => {
      if (validateFile(selectedFile)) {
        setFile(selectedFile)
        setColumnSuggestions(null)
        setImportResult(null)
      }
    },
    [validateFile],
  )

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0]
    if (selectedFile) {
      handleFileSelect(selectedFile)
    }
  }

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(true)
  }

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    const droppedFile = e.dataTransfer.files[0]
    if (droppedFile) {
      handleFileSelect(droppedFile)
    }
  }

  const handleRemoveFile = () => {
    setFile(null)
    setColumnSuggestions(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  // ============================================================================
  // Step Navigation
  // ============================================================================

  const handleAnalyzeAndProceed = async () => {
    if (!file) return

    try {
      setAnalyzing(true)
      const suggestions = await analyzeStatementFile(reconciliationId, file)
      setColumnSuggestions(suggestions)

      // Parse preview data from file
      await parsePreviewData(file)

      setStep('mapping')
    } catch (err: any) {
      toast.error(t('common.error'), {
        description: err?.error?.message || err?.message || 'Failed to analyze file',
      })
    } finally {
      setAnalyzing(false)
    }
  }

  const parsePreviewData = async (f: File) => {
    try {
      const text = await f.text()
      const lines = text.split('\n').filter((line) => line.trim())
      const preview = lines.slice(0, 6).map((line) => {
        // Simple CSV parsing (handles basic cases)
        return line.split(',').map((cell) => cell.trim().replace(/^"|"$/g, ''))
      })
      setPreviewData(preview)
    } catch {
      // For Excel files, preview will be empty (handled by backend)
      setPreviewData([])
    }
  }

  const handleProceedToPreview = () => {
    // Validate required fields
    if (!dateColumn) {
      toast.error(t('bankReconciliation.import.dateColumn') + ' is required')
      return
    }
    if (!debitColumn && !creditColumn) {
      toast.error('At least Debit or Credit column is required')
      return
    }
    setStep('preview')
  }

  const handleImport = async () => {
    if (!file) return

    try {
      setImporting(true)

      const config: StatementImportRequest = {
        dateColumn,
        descriptionColumn: descriptionColumn || undefined,
        referenceColumn: referenceColumn || undefined,
        debitColumn: debitColumn || undefined,
        creditColumn: creditColumn || undefined,
        balanceColumn: balanceColumn || undefined,
        dateFormat,
        skipHeaderRows,
        saveFormatProfile,
        formatProfileName: saveFormatProfile ? formatProfileName : undefined,
      }

      const result = await importStatement(reconciliationId, file, config)
      setImportResult(result)
      setStep('result')

      if (result.success && result.errorRows === 0) {
        toast.success(t('common.success'), {
          description: `Imported ${result.importedRows} rows successfully`,
        })
        onSuccess?.()
      } else if (result.success && result.errorRows > 0) {
        toast.warning('Import completed with errors', {
          description: `${result.importedRows} imported, ${result.errorRows} errors`,
        })
        onSuccess?.()
      }
    } catch (err: any) {
      toast.error(t('common.error'), {
        description: err?.error?.message || err?.message || 'Import failed',
      })
    } finally {
      setImporting(false)
    }
  }

  const handleDownloadErrors = async () => {
    if (!importResult?.errorReportId) return

    try {
      const blob = await downloadImportErrors(reconciliationId, importResult.errorReportId)
      downloadBlob(blob, `import-errors-${reconciliationId}.csv`)
    } catch (err: any) {
      toast.error('Failed to download error report')
    }
  }

  const handleClose = () => {
    if (!importing && !analyzing) {
      onOpenChange(false)
    }
  }

  // ============================================================================
  // Render
  // ============================================================================

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Upload className="h-5 w-5" />
            {t('bankReconciliation.import.title')}
          </DialogTitle>
          <DialogDescription>
            {t('bankReconciliation.import.title')} for {bankAccountName}
          </DialogDescription>
        </DialogHeader>

        <StepIndicator currentStep={step} steps={steps} />

        {/* Step 1: File Upload */}
        {step === 'upload' && (
          <div className="space-y-4">
            {/* Drag and Drop Zone */}
            <div
              className={cn(
                'border-2 border-dashed rounded-lg p-8 text-center transition-colors',
                isDragging && 'border-primary bg-primary/5',
                !isDragging && 'border-muted-foreground/25 hover:border-muted-foreground/50',
              )}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
            >
              <Upload className="h-10 w-10 mx-auto text-muted-foreground mb-4" />
              <div className="space-y-2">
                <p className="text-sm font-medium">
                  <label
                    htmlFor="file-upload"
                    className="text-primary cursor-pointer hover:underline"
                  >
                    {t('bankReconciliation.import.selectFile')}
                  </label>{' '}
                  {t('bankReconciliation.import.dragAndDrop')}
                </p>
                <p className="text-xs text-muted-foreground">
                  {t('bankReconciliation.import.supportedFormats')}
                </p>
                <p className="text-xs text-muted-foreground">
                  {t('bankReconciliation.import.maxFileSize')}
                </p>
              </div>
              <Input
                id="file-upload"
                ref={fileInputRef}
                type="file"
                accept=".csv,.xlsx,.xls"
                onChange={handleInputChange}
                className="hidden"
                data-testid="import-file-input"
              />
            </div>

            {/* Selected File Display */}
            {file && (
              <div className="flex items-center justify-between p-3 bg-muted rounded-lg">
                <div className="flex items-center gap-2">
                  <FileText className="h-5 w-5 text-muted-foreground" />
                  <div>
                    <p className="text-sm font-medium">{file.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {(file.size / 1024).toFixed(1)} KB
                    </p>
                  </div>
                </div>
                <Button variant="ghost" size="icon" onClick={handleRemoveFile}>
                  <X className="h-4 w-4" />
                </Button>
              </div>
            )}

            {/* Saved Profile Notice */}
            {columnSuggestions?.hasSavedProfile && (
              <Alert>
                <CheckCircle2 className="h-4 w-4" />
                <AlertTitle>{t('bankReconciliation.import.useSavedProfile')}</AlertTitle>
                <AlertDescription>
                  A saved format profile was found for this bank account and will be applied.
                </AlertDescription>
              </Alert>
            )}
          </div>
        )}

        {/* Step 2: Column Mapping */}
        {step === 'mapping' && columnSuggestions && (
          <div className="space-y-4">
            <Alert>
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>
                Map the columns from your file to the system fields. Required fields are marked with
                *.
              </AlertDescription>
            </Alert>

            <div className="grid grid-cols-2 gap-4">
              {/* Date Column (Required) */}
              <div className="space-y-2">
                <Label>
                  {t('bankReconciliation.import.dateColumn')}{' '}
                  <span className="text-red-500">*</span>
                </Label>
                <Select value={dateColumn} onValueChange={setDateColumn}>
                  <SelectTrigger data-testid="date-column-select">
                    <SelectValue placeholder="Select column..." />
                  </SelectTrigger>
                  <SelectContent>
                    {columnSuggestions.headers.map((header) => (
                      <SelectItem key={header} value={header}>
                        {header}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Description Column */}
              <div className="space-y-2">
                <Label>{t('bankReconciliation.import.descriptionColumn')}</Label>
                <Select value={descriptionColumn} onValueChange={setDescriptionColumn}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select column..." />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="">-- Not Mapped --</SelectItem>
                    {columnSuggestions.headers.map((header) => (
                      <SelectItem key={header} value={header}>
                        {header}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Reference Column */}
              <div className="space-y-2">
                <Label>{t('bankReconciliation.import.referenceColumn')}</Label>
                <Select value={referenceColumn} onValueChange={setReferenceColumn}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select column..." />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="">-- Not Mapped --</SelectItem>
                    {columnSuggestions.headers.map((header) => (
                      <SelectItem key={header} value={header}>
                        {header}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Debit Column */}
              <div className="space-y-2">
                <Label>{t('bankReconciliation.import.debitColumn')}</Label>
                <Select value={debitColumn} onValueChange={setDebitColumn}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select column..." />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="">-- Not Mapped --</SelectItem>
                    {columnSuggestions.headers.map((header) => (
                      <SelectItem key={header} value={header}>
                        {header}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Credit Column */}
              <div className="space-y-2">
                <Label>{t('bankReconciliation.import.creditColumn')}</Label>
                <Select value={creditColumn} onValueChange={setCreditColumn}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select column..." />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="">-- Not Mapped --</SelectItem>
                    {columnSuggestions.headers.map((header) => (
                      <SelectItem key={header} value={header}>
                        {header}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Balance Column */}
              <div className="space-y-2">
                <Label>{t('bankReconciliation.import.balanceColumn')}</Label>
                <Select value={balanceColumn} onValueChange={setBalanceColumn}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select column..." />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="">-- Not Mapped --</SelectItem>
                    {columnSuggestions.headers.map((header) => (
                      <SelectItem key={header} value={header}>
                        {header}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Date Format */}
              <div className="space-y-2">
                <Label>{t('bankReconciliation.import.dateFormat')}</Label>
                <Select value={dateFormat} onValueChange={setDateFormat}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="yyyy-MM-dd">yyyy-MM-dd (2024-01-15)</SelectItem>
                    <SelectItem value="dd/MM/yyyy">dd/MM/yyyy (15/01/2024)</SelectItem>
                    <SelectItem value="MM/dd/yyyy">MM/dd/yyyy (01/15/2024)</SelectItem>
                    <SelectItem value="dd-MM-yyyy">dd-MM-yyyy (15-01-2024)</SelectItem>
                    <SelectItem value="yyyy/MM/dd">yyyy/MM/dd (2024/01/15)</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {/* Skip Header Rows */}
              <div className="space-y-2">
                <Label>{t('bankReconciliation.import.skipHeaderRows')}</Label>
                <Input
                  type="number"
                  min="0"
                  max="10"
                  value={skipHeaderRows}
                  onChange={(e) => setSkipHeaderRows(parseInt(e.target.value) || 0)}
                />
              </div>
            </div>

            {/* Save Format Profile */}
            <div className="space-y-2 pt-4 border-t">
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="save-profile"
                  checked={saveFormatProfile}
                  onCheckedChange={(checked) => setSaveFormatProfile(checked === true)}
                />
                <Label htmlFor="save-profile" className="cursor-pointer">
                  {t('bankReconciliation.import.saveFormatProfile')}
                </Label>
              </div>
              {saveFormatProfile && (
                <Input
                  placeholder={t('bankReconciliation.import.formatProfileName')}
                  value={formatProfileName}
                  onChange={(e) => setFormatProfileName(e.target.value)}
                  className="mt-2"
                />
              )}
            </div>

            {/* Preview Table */}
            {previewData.length > 0 && (
              <div className="space-y-2 pt-4 border-t">
                <Label>{t('bankReconciliation.import.previewData')} (First 5 rows)</Label>
                <div className="border rounded-lg overflow-x-auto max-h-[200px]">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        {previewData[0]?.map((header, idx) => (
                          <TableHead key={idx} className="whitespace-nowrap">
                            {header}
                          </TableHead>
                        ))}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {previewData.slice(1, 6).map((row, rowIdx) => (
                        <TableRow key={rowIdx}>
                          {row.map((cell, cellIdx) => (
                            <TableCell key={cellIdx} className="whitespace-nowrap">
                              {cell}
                            </TableCell>
                          ))}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Step 3: Preview & Confirm */}
        {step === 'preview' && (
          <div className="space-y-4">
            <Alert>
              <CheckCircle2 className="h-4 w-4" />
              <AlertTitle>Ready to Import</AlertTitle>
              <AlertDescription>
                Review your column mapping below. Click Import to process the file.
              </AlertDescription>
            </Alert>

            {/* Duplicate Warning */}
            {importResult?.duplicateDetected && (
              <Alert variant="destructive">
                <AlertCircle className="h-4 w-4" />
                <AlertTitle>{t('bankReconciliation.import.duplicateWarning')}</AlertTitle>
                <AlertDescription>
                  {t('bankReconciliation.import.duplicateMessage')}
                </AlertDescription>
              </Alert>
            )}

            {/* Mapping Summary */}
            <div className="border rounded-lg p-4 space-y-2">
              <h4 className="font-semibold">Column Mapping Summary</h4>
              <div className="grid grid-cols-2 gap-2 text-sm">
                <div>
                  <span className="text-muted-foreground">Date Column:</span>{' '}
                  <Badge variant="secondary">{dateColumn}</Badge>
                </div>
                {descriptionColumn && (
                  <div>
                    <span className="text-muted-foreground">Description:</span>{' '}
                    <Badge variant="secondary">{descriptionColumn}</Badge>
                  </div>
                )}
                {referenceColumn && (
                  <div>
                    <span className="text-muted-foreground">Reference:</span>{' '}
                    <Badge variant="secondary">{referenceColumn}</Badge>
                  </div>
                )}
                {debitColumn && (
                  <div>
                    <span className="text-muted-foreground">Debit:</span>{' '}
                    <Badge variant="secondary">{debitColumn}</Badge>
                  </div>
                )}
                {creditColumn && (
                  <div>
                    <span className="text-muted-foreground">Credit:</span>{' '}
                    <Badge variant="secondary">{creditColumn}</Badge>
                  </div>
                )}
                {balanceColumn && (
                  <div>
                    <span className="text-muted-foreground">Balance:</span>{' '}
                    <Badge variant="secondary">{balanceColumn}</Badge>
                  </div>
                )}
                <div>
                  <span className="text-muted-foreground">Date Format:</span>{' '}
                  <Badge variant="secondary">{dateFormat}</Badge>
                </div>
                <div>
                  <span className="text-muted-foreground">Skip Rows:</span>{' '}
                  <Badge variant="secondary">{skipHeaderRows}</Badge>
                </div>
              </div>
            </div>

            {/* File Info */}
            <div className="flex items-center gap-2 p-3 bg-muted rounded-lg">
              <FileText className="h-5 w-5 text-muted-foreground" />
              <div>
                <p className="text-sm font-medium">{file?.name}</p>
                <p className="text-xs text-muted-foreground">
                  {file && (file.size / 1024).toFixed(1)} KB
                </p>
              </div>
            </div>

            {importing && (
              <div className="space-y-2">
                <Progress value={undefined} className="h-2" />
                <p className="text-sm text-center text-muted-foreground">Importing...</p>
              </div>
            )}
          </div>
        )}

        {/* Step 4: Result */}
        {step === 'result' && importResult && (
          <div className="space-y-4">
            {/* Success/Warning Alert */}
            {importResult.success && importResult.errorRows === 0 ? (
              <Alert className="bg-green-50 border-green-200">
                <CheckCircle2 className="h-4 w-4 text-green-600" />
                <AlertTitle className="text-green-800">Import Successful</AlertTitle>
                <AlertDescription className="text-green-700">
                  Successfully imported {importResult.importedRows} of {importResult.totalRows}{' '}
                  rows.
                </AlertDescription>
              </Alert>
            ) : importResult.success ? (
              <Alert variant="default" className="bg-yellow-50 border-yellow-200">
                <AlertCircle className="h-4 w-4 text-yellow-600" />
                <AlertTitle className="text-yellow-800">Import Completed with Errors</AlertTitle>
                <AlertDescription className="text-yellow-700">
                  Imported {importResult.importedRows} rows, {importResult.errorRows} errors.
                </AlertDescription>
              </Alert>
            ) : (
              <Alert variant="destructive">
                <AlertCircle className="h-4 w-4" />
                <AlertTitle>Import Failed</AlertTitle>
                <AlertDescription>
                  Failed to import statement. Please check the error report.
                </AlertDescription>
              </Alert>
            )}

            {/* Statistics */}
            <div className="grid grid-cols-3 gap-4">
              <div className="border rounded-lg p-4 text-center">
                <p className="text-2xl font-bold text-green-600">{importResult.importedRows}</p>
                <p className="text-sm text-muted-foreground">Imported</p>
              </div>
              <div className="border rounded-lg p-4 text-center">
                <p className="text-2xl font-bold text-red-600">{importResult.errorRows}</p>
                <p className="text-sm text-muted-foreground">Errors</p>
              </div>
              <div className="border rounded-lg p-4 text-center">
                <p className="text-2xl font-bold">{importResult.totalRows}</p>
                <p className="text-sm text-muted-foreground">Total Rows</p>
              </div>
            </div>

            {/* Error Details */}
            {importResult.errors && importResult.errors.length > 0 && (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label>{t('bankReconciliation.import.importErrors')}</Label>
                  {importResult.errorReportId && (
                    <Button variant="outline" size="sm" onClick={handleDownloadErrors}>
                      <Download className="mr-2 h-4 w-4" />
                      {t('bankReconciliation.import.downloadErrorReport')}
                    </Button>
                  )}
                </div>
                <div className="border rounded-lg max-h-[200px] overflow-y-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('bankReconciliation.import.rowNumber')}</TableHead>
                        <TableHead>{t('bankReconciliation.import.field')}</TableHead>
                        <TableHead>{t('bankReconciliation.import.value')}</TableHead>
                        <TableHead>{t('bankReconciliation.import.errorMessage')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {importResult.errors.slice(0, 10).map((error, idx) => (
                        <TableRow key={idx}>
                          <TableCell>{error.rowNumber}</TableCell>
                          <TableCell>{error.field}</TableCell>
                          <TableCell className="max-w-[100px] truncate">{error.value}</TableCell>
                          <TableCell className="text-red-600">{error.errorMessage}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
                {importResult.errors.length > 10 && (
                  <p className="text-sm text-muted-foreground text-center">
                    Showing first 10 of {importResult.errors.length} errors. Download the full
                    report for details.
                  </p>
                )}
              </div>
            )}
          </div>
        )}

        <DialogFooter className="gap-2">
          {/* Back Button */}
          {step !== 'upload' && step !== 'result' && (
            <Button
              variant="outline"
              onClick={() => {
                if (step === 'mapping') setStep('upload')
                else if (step === 'preview') setStep('mapping')
              }}
              disabled={importing || analyzing}
            >
              {t('common.back')}
            </Button>
          )}

          {/* Cancel/Close Button */}
          <Button variant="outline" onClick={handleClose} disabled={importing || analyzing}>
            {step === 'result' ? t('common.close') : t('common.cancel')}
          </Button>

          {/* Action Buttons */}
          {step === 'upload' && (
            <Button
              onClick={handleAnalyzeAndProceed}
              disabled={!file || analyzing}
              data-testid="analyze-file-button"
            >
              {analyzing ? (
                <>
                  <Upload className="mr-2 h-4 w-4 animate-spin" />
                  Analyzing...
                </>
              ) : (
                <>
                  {t('common.next')}: {t('bankReconciliation.import.step2')}
                </>
              )}
            </Button>
          )}

          {step === 'mapping' && (
            <Button onClick={handleProceedToPreview} data-testid="proceed-to-preview-button">
              {t('common.next')}: {t('bankReconciliation.import.step3')}
            </Button>
          )}

          {step === 'preview' && (
            <Button onClick={handleImport} disabled={importing} data-testid="import-button">
              {importing ? (
                <>
                  <Upload className="mr-2 h-4 w-4 animate-spin" />
                  Importing...
                </>
              ) : (
                <>
                  <Upload className="mr-2 h-4 w-4" />
                  {t('common.import')}
                </>
              )}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
