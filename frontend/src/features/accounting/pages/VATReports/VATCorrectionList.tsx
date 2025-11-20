'use client'

import { useCallback, useMemo, useState } from 'react'
import { format } from 'date-fns'
import { toast } from 'sonner'
import { FileWarning, Filter, RefreshCw, Plus, CheckCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { DatePickerWithRange } from '@/components/ui/date-picker'
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
import { vatService } from '@/services/vat'
import type { VATCorrectionDTO } from '@/types/vat'
import { VATCorrectionDialog } from './VATCorrectionDialog'
import { ApproveVATCorrectionDialog } from './ApproveVATCorrectionDialog'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]
const STATUS_OPTIONS: VATCorrectionDTO['status'][] = ['PENDING', 'APPROVED', 'REJECTED']

export function VATCorrectionList() {
  const [corrections, setCorrections] = useState<VATCorrectionDTO[]>([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)

  const [billId, setBillId] = useState('')
  const [status, setStatus] = useState<VATCorrectionDTO['status'] | 'ALL'>('ALL')
  const [correctedById, setCorrectedById] = useState('')
  const [dateRange, setDateRange] = useState<{ from?: Date; to?: Date }>({})

  const [dialogOpen, setDialogOpen] = useState(false)
  const [approveDialogOpen, setApproveDialogOpen] = useState(false)
  const [selectedCorrection, setSelectedCorrection] = useState<VATCorrectionDTO | null>(null)

  const loadCorrections = useCallback(async () => {
    if (!billId) {
      toast.error('Enter a purchase bill ID to search corrections')
      return
    }
    try {
      setLoading(true)
      const params: {
        billId: string
        status?: string
        startDate?: string
        endDate?: string
        correctedById?: number
      } = { billId }
      if (status !== 'ALL') {
        params.status = status
      }
      if (dateRange.from) {
        params.startDate = format(dateRange.from, 'yyyy-MM-dd')
      }
      if (dateRange.to) {
        params.endDate = format(dateRange.to, 'yyyy-MM-dd')
      }
      if (correctedById) {
        if (Number.isNaN(Number(correctedById))) {
          toast.error('Corrected by ID must be numeric')
          return
        }
        params.correctedById = Number(correctedById)
      }
      const list = await vatService.listCorrections(params)
      setCorrections(list)
      setPage(0)
    } catch (error) {
      toast.error(`Failed to load corrections: ${String(error)}`)
    } finally {
      setLoading(false)
    }
  }, [billId, status, dateRange.from, dateRange.to, correctedById])

  const openApproveDialog = (correction: VATCorrectionDTO) => {
    setSelectedCorrection(correction)
    setApproveDialogOpen(true)
  }

  const filtered = useMemo(() => {
    const start = page * pageSize
    const end = start + pageSize
    return corrections.slice(start, end)
  }, [corrections, page, pageSize])

  const totalItems = corrections.length
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize))

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <FileWarning className="h-6 w-6 text-primary" />
            VAT Corrections
          </h1>
          <p className="text-muted-foreground">
            Review, create, and approve manual VAT corrections for posted bills.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={loadCorrections} disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button onClick={() => setDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            New Correction
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <div>
          <Labelled>
            <span>Purchase Bill ID *</span>
            <Input
              value={billId}
              onChange={(e) => setBillId(e.target.value)}
              placeholder="Bill UUID"
            />
          </Labelled>
        </div>
        <div>
          <Labelled>
            <span>Status</span>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as VATCorrectionDTO['status'] | 'ALL')}
              className="w-full rounded-md border bg-background px-3 py-2 text-sm"
            >
              <option value="ALL">All statuses</option>
              {STATUS_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option.charAt(0) + option.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
          </Labelled>
        </div>
        <div>
          <Labelled>
            <span>Corrected By (ID)</span>
            <Input
              value={correctedById}
              onChange={(e) => setCorrectedById(e.target.value)}
              placeholder="User ID"
            />
          </Labelled>
        </div>
        <div>
          <Labelled>
            <span>Corrected Date</span>
          </Labelled>
          <DatePickerWithRange value={dateRange} onChange={setDateRange} />
        </div>
        <div className="md:col-span-2 lg:col-span-4 flex items-center gap-2">
          <Button
            variant="secondary"
            onClick={loadCorrections}
            disabled={loading || !billId}
            className="flex items-center gap-1"
          >
            <Filter className="h-4 w-4" />
            Apply Filters
          </Button>
          <Button
            variant="ghost"
            onClick={() => {
              setStatus('ALL')
              setCorrectedById('')
              setDateRange({})
              setCorrections([])
            }}
          >
            Clear
          </Button>
          <span className="text-xs text-muted-foreground">
            Bill ID required (company scope enforced server-side)
          </span>
        </div>
      </div>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Bill</TableHead>
              <TableHead>Line</TableHead>
              <TableHead className="text-right">Old VAT</TableHead>
              <TableHead className="text-right">New VAT</TableHead>
              <TableHead className="text-right">Diff</TableHead>
              <TableHead>Reason</TableHead>
              <TableHead>Corrected By</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              Array.from({ length: 6 }).map((_, idx) => (
                <TableRow key={idx}>
                  <TableCell colSpan={9}>
                    <div className="flex items-center gap-2">
                      <Skeleton className="h-4 w-full" />
                    </div>
                  </TableCell>
                </TableRow>
              ))
            ) : filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9} className="py-10 text-center text-muted-foreground">
                  {billId
                    ? 'No corrections found for the selected filters.'
                    : 'Enter a bill ID and apply filters to load corrections.'}
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((correction) => (
                <TableRow key={correction.id}>
                  <TableCell className="font-mono text-xs">{correction.purchaseBillId}</TableCell>
                  <TableCell className="font-mono text-xs">
                    {correction.purchaseBillLineId ?? '—'}
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
                  <TableCell className="max-w-xs truncate text-sm" title={correction.reason}>
                    {correction.reason}
                  </TableCell>
                  <TableCell className="text-sm">
                    <div>{correction.correctedById}</div>
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
                        className="flex items-center gap-1"
                        onClick={() => openApproveDialog(correction)}
                      >
                        <CheckCircle className="h-4 w-4" />
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

      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="text-sm text-muted-foreground">
          Showing {filtered.length} of {totalItems} corrections
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-muted-foreground">Rows per page:</span>
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value))
                setPage(0)
              }}
              className="rounded border bg-background px-2 py-1 text-sm"
            >
              {PAGE_SIZE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
          <div className="flex items-center gap-2 text-sm">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
            >
              Previous
            </Button>
            <span className="text-muted-foreground">
              Page {totalItems === 0 ? 0 : page + 1} of {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1 || totalItems === 0}
            >
              Next
            </Button>
          </div>
        </div>
      </div>

      <VATCorrectionDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        defaultBillId={billId || undefined}
        onCreated={() => loadCorrections()}
      />
      <ApproveVATCorrectionDialog
        open={approveDialogOpen}
        onOpenChange={setApproveDialogOpen}
        correction={selectedCorrection}
        onApproved={() => {
          setApproveDialogOpen(false)
          loadCorrections()
        }}
      />
    </div>
  )
}

function Labelled({ children }: { children: React.ReactNode }) {
  return <div className="space-y-2 text-sm text-muted-foreground">{children}</div>
}
