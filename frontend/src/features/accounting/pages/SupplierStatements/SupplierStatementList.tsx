'use client'

import { useCallback, useEffect, useState } from 'react'
import { FileText, RefreshCw, Download, Plus, Upload, Mail } from 'lucide-react'
import { format } from 'date-fns'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { supplierStatementService } from '@/services/supplierStatement'
import { GenerateStatementDialog } from './GenerateStatementDialog'
import { ImportStatementDialog } from './ImportStatementDialog'
import { ReconciliationResultsDialog } from './ReconciliationResultsDialog'
import { SendStatementDialog } from '@/components/supplier-statements'
import type {
  SupplierStatementHistory,
  ExportFormat,
  ReconciliationResult,
} from '@/types/supplierStatement'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]

export function SupplierStatementList() {
  const [loading, setLoading] = useState(false)
  const [statements, setStatements] = useState<SupplierStatementHistory[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)

  // Filters
  const [searchTerm, setSearchTerm] = useState('')

  // Dialog states
  const [generateDialogOpen, setGenerateDialogOpen] = useState(false)
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [reconciliationDialogOpen, setReconciliationDialogOpen] = useState(false)
  const [sendDialogOpen, setSendDialogOpen] = useState(false)
  const [selectedStatementId, setSelectedStatementId] = useState<string>('')
  const [selectedSupplierName, setSelectedSupplierName] = useState<string>('')
  const [reconciliationResult, setReconciliationResult] = useState<ReconciliationResult | null>(
    null,
  )
  const [reconciliationSupplierId, setReconciliationSupplierId] = useState<number>(0)

  const loadStatements = useCallback(async () => {
    try {
      setLoading(true)
      const response = await supplierStatementService.listStatements({
        page,
        size: pageSize,
      })
      setStatements(response.statements)
      setTotalElements(response.totalItems)
      setTotalPages(response.totalPages)
    } catch (error) {
      toast.error(`Failed to load statements: ${error}`)
    } finally {
      setLoading(false)
    }
  }, [page, pageSize])

  useEffect(() => {
    loadStatements()
  }, [loadStatements])

  const handleRefresh = () => {
    setPage(0)
    loadStatements()
  }

  const handleExport = async (statementId: string, format: ExportFormat) => {
    try {
      const blob = await supplierStatementService.exportStatement(statementId, format)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `statement-${statementId}.${format === 'EXCEL' ? 'xlsx' : 'pdf'}`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success(`Statement exported as ${format}`)
    } catch (error) {
      toast.error(`Failed to export statement: ${error}`)
    }
  }

  const filteredStatements = statements.filter(
    (s) =>
      s.supplierName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.supplierCode.toLowerCase().includes(searchTerm.toLowerCase()),
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            Supplier Statements
          </h1>
          <p className="text-muted-foreground">
            Generate and manage supplier statements and reconciliation
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleRefresh} disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button variant="outline" onClick={() => setImportDialogOpen(true)}>
            <Upload className="mr-2 h-4 w-4" />
            Import Statement
          </Button>
          <Button onClick={() => setGenerateDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            Generate Statement
          </Button>
        </div>
      </div>

      {/* Search */}
      <div className="flex items-center gap-4">
        <Input
          placeholder="Search by supplier name or code..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="max-w-sm"
        />
      </div>

      {/* Table */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Supplier</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Period</TableHead>
              <TableHead>Generated</TableHead>
              <TableHead>Format</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell>
                    <Skeleton className="h-4 w-[200px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[80px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[150px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[120px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[60px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[80px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[100px]" />
                  </TableCell>
                </TableRow>
              ))
            ) : filteredStatements.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="text-center py-10 text-muted-foreground">
                  No statements found. Generate your first statement to get started.
                </TableCell>
              </TableRow>
            ) : (
              filteredStatements.map((statement) => (
                <TableRow key={statement.id}>
                  <TableCell>
                    <div>
                      <div className="font-medium">{statement.supplierName}</div>
                      <div className="text-sm text-muted-foreground">{statement.supplierCode}</div>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{statement.statementType}</Badge>
                  </TableCell>
                  <TableCell className="text-sm">
                    {format(new Date(statement.startDate), 'dd/MM/yyyy')} -{' '}
                    {format(new Date(statement.endDate), 'dd/MM/yyyy')}
                  </TableCell>
                  <TableCell className="text-sm">
                    <div>{format(new Date(statement.generationDate), 'dd/MM/yyyy HH:mm')}</div>
                    <div className="text-muted-foreground">{statement.generatedByName}</div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="secondary">{statement.format}</Badge>
                  </TableCell>
                  <TableCell>
                    {statement.sentDate ? (
                      <Badge variant="default">Sent</Badge>
                    ) : (
                      <Badge variant="outline">Draft</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setSelectedStatementId(statement.id)
                          setSelectedSupplierName(statement.supplierName)
                          setSendDialogOpen(true)
                        }}
                        title="Send to supplier"
                      >
                        <Mail className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleExport(statement.id, statement.format)}
                        title="Download"
                      >
                        <Download className="h-4 w-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between">
        <div className="text-sm text-muted-foreground">
          Showing {filteredStatements.length} of {totalElements} statements
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm">Rows per page:</span>
          <select
            value={pageSize}
            onChange={(e) => {
              setPageSize(Number(e.target.value))
              setPage(0)
            }}
            className="border rounded px-2 py-1 text-sm"
          >
            {PAGE_SIZE_OPTIONS.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
          >
            Previous
          </Button>
          <span className="text-sm">
            Page {page + 1} of {totalPages || 1}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
          >
            Next
          </Button>
        </div>
      </div>

      {/* Dialogs */}
      <GenerateStatementDialog
        open={generateDialogOpen}
        onOpenChange={setGenerateDialogOpen}
        onSuccess={loadStatements}
      />

      <ImportStatementDialog
        open={importDialogOpen}
        onOpenChange={setImportDialogOpen}
        onReconciliationComplete={(result) => {
          setReconciliationResult(result)
          setReconciliationSupplierId(result.supplierId)
          setReconciliationDialogOpen(true)
        }}
      />

      <ReconciliationResultsDialog
        open={reconciliationDialogOpen}
        onOpenChange={setReconciliationDialogOpen}
        result={reconciliationResult}
        supplierId={reconciliationSupplierId}
        onSave={loadStatements}
      />

      <SendStatementDialog
        open={sendDialogOpen}
        onOpenChange={setSendDialogOpen}
        statementId={selectedStatementId}
        supplierName={selectedSupplierName}
        onSuccess={loadStatements}
      />
    </div>
  )
}
