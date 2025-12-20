import { useId, useMemo, useRef, useState } from 'react'
import { z } from 'zod'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
import { toast } from 'sonner'
import { Download, Loader2, RefreshCw, Upload } from 'lucide-react'
import { cn } from '@/lib/utils'
import { importApi } from '@/features/accounting/services/importApi'

const importTypes = [
  { value: 'customers', label: 'Customers' },
  { value: 'suppliers', label: 'Suppliers' },
  { value: 'bank-accounts', label: 'Bank Accounts' },
  { value: 'opening-balances', label: 'Opening Balances' },
] as const

const ErrorRowSchema = z.object({
  rowNumber: z.number(),
  field: z.string(),
  message: z.string(),
})

const ImportResultSchema = z.object({
  successCount: z.number(),
  skippedCount: z.number().optional().default(0),
  errorCount: z.number(),
  errors: z.array(ErrorRowSchema).default([]),
  errorReportId: z.string().trim().min(1).nullable().optional(),
})

type ImportType = (typeof importTypes)[number]['value']
type ImportResult = z.infer<typeof ImportResultSchema>

export default function ImportWizard() {
  const [type, setType] = useState<ImportType>('customers')
  const [file, setFile] = useState<File | null>(null)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const inputRef = useRef<HTMLInputElement | null>(null)
  const uploadInputId = useId()
  const [errorResult, setErrorResult] = useState<ImportResult | null>(null)

  const { refetch: refetchTemplate } = useQuery({
    queryKey: ['template-metadata', type],
    queryFn: async () => {
      // Placeholder to align with pattern; backend generates on demand.
      return { type }
    },
  })

  const uploadMutation = useMutation<ImportResult, any>({
    mutationFn: async () => {
      if (!file) {
        throw new Error('Please choose a file to upload.')
      }
      const payload = await importApi.upload(type, file)
      return ImportResultSchema.parse(payload)
    },
    onSuccess: (res) => {
      toast[res.errorCount > 0 ? 'error' : 'success'](
        res.errorCount > 0
          ? `Imported with ${res.errorCount} error(s).`
          : `Imported successfully: ${res.successCount} row(s).`,
      )
    },
    onError: (err: any) => {
      const payload = err?.response?.data
      const parsed = payload ? ImportResultSchema.safeParse(payload) : null
      if (parsed?.success) {
        setErrorResult(parsed.data)
      }
      const msg = payload?.message || err?.message || 'Import failed.'
      toast.error(msg)
    },
  })

  const result = errorResult ?? uploadMutation.data ?? null

  const filteredErrors = useMemo(() => {
    if (!result?.errors) return []
    const term = search.trim().toLowerCase()
    if (!term) return result.errors
    return result.errors.filter((e) => {
      return (
        String(e.rowNumber).includes(term) ||
        e.field.toLowerCase().includes(term) ||
        e.message.toLowerCase().includes(term)
      )
    })
  }, [result, search])

  const total = filteredErrors.length
  const from = (page - 1) * pageSize
  const to = Math.min(from + pageSize, total)
  const pageItems = filteredErrors.slice(from, to)
  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  const onDownloadTemplate = async () => {
    try {
      const blob = await importApi.downloadTemplate(type, 'xlsx')
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${type}-template.xlsx`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e: any) {
      toast.error('Failed to download template.')
    }
  }

  const onDownloadErrorReport = async () => {
    const id = result?.errorReportId
    if (!id) {
      toast.info('No error report available.')
      return
    }
    try {
      const blob = await importApi.downloadErrorReport(id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `import-error-report-${id}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e: any) {
      toast.error('Failed to download error report.')
    }
  }

  const onUpload = async () => {
    if (uploadMutation.isPending) {
      return
    }
    setPage(1)
    setErrorResult(null)
    try {
      await uploadMutation.mutateAsync()
    } catch (_err) {
      // handled via onError
    }
  }

  const onRefresh = () => {
    setSearch('')
    setPage(1)
    if (inputRef.current) inputRef.current.value = ''
    setErrorResult(null)
    refetchTemplate()
    toast.success('Refreshed.')
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Import Wizard</CardTitle>
          <div className="flex items-center gap-2">
            <Button variant="outline" onClick={onDownloadTemplate}>
              <Download className="mr-2 h-4 w-4" />
              Download Template
            </Button>
            <Button variant="outline" onClick={onRefresh}>
              <RefreshCw className="mr-2 h-4 w-4" />
              Refresh
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <Label>Import Type</Label>
              <Select value={type} onValueChange={(v) => setType(v as ImportType)}>
                <SelectTrigger>
                  <SelectValue placeholder="Select type" />
                </SelectTrigger>
                <SelectContent>
                  {importTypes.map((t) => (
                    <SelectItem key={t.value} value={t.value}>
                      {t.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="md:col-span-2">
              <Label htmlFor={uploadInputId}>Upload File</Label>
              <div className="flex items-center gap-2">
                <Input
                  id={uploadInputId}
                  ref={inputRef}
                  type="file"
                  accept=".csv,.xls,.xlsx"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                />
                <Button onClick={onUpload} disabled={!file || uploadMutation.isPending}>
                  {uploadMutation.isPending ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Uploading...
                    </>
                  ) : (
                    <>
                      <Upload className="mr-2 h-4 w-4" />
                      Upload
                    </>
                  )}
                </Button>
              </div>
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between gap-2">
              <Input
                placeholder="Search errors (row, field, message)..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="max-w-md"
              />
              <div className="text-sm text-muted-foreground">
                Showing {to - from} of {total} error(s)
                {result ? ` • ${result.successCount} success` : ''}
              </div>
            </div>

            <div className={cn('rounded-md border', result?.errors?.length ? '' : 'opacity-60')}>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[120px]">Row</TableHead>
                    <TableHead className="w-[220px]">Field</TableHead>
                    <TableHead>Message</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {pageItems.length === 0 ? (
                    <TableRow>
                      <TableCell
                        colSpan={3}
                        className="h-24 text-center text-sm text-muted-foreground"
                      >
                        {result ? 'No errors found.' : 'Upload a file to see results.'}
                      </TableCell>
                    </TableRow>
                  ) : (
                    pageItems.map((e, idx) => (
                      <TableRow key={`${e.rowNumber}-${e.field}-${idx}`}>
                        <TableCell>{e.rowNumber}</TableCell>
                        <TableCell>{e.field}</TableCell>
                        <TableCell>{e.message}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </div>

            <div className="flex flex-col md:flex-row items-center justify-between gap-3 pt-2">
              <div className="flex items-center gap-2">
                <Label htmlFor="page-size">Rows per page</Label>
                <Select
                  value={String(pageSize)}
                  onValueChange={(v) => {
                    setPageSize(parseInt(v, 10))
                    setPage(1)
                  }}
                >
                  <SelectTrigger id="page-size" className="w-[100px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {[10, 20, 30, 50, 100].map((n) => (
                      <SelectItem key={n} value={String(n)}>
                        {n}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  disabled={page <= 1}
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                >
                  Prev
                </Button>
                <div className="text-sm text-muted-foreground">
                  Page {page} of {totalPages}
                </div>
                <Button
                  variant="outline"
                  disabled={page >= totalPages}
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                >
                  Next
                </Button>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="secondary"
                  onClick={onDownloadErrorReport}
                  disabled={!result?.errorReportId}
                >
                  <Download className="mr-2 h-4 w-4" />
                  Download Error Report
                </Button>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
