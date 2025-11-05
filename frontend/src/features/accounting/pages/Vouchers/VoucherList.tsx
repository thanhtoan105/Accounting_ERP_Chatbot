import { useState, useEffect, useCallback } from 'react'

import { Search } from 'lucide-react'
import { getVouchers, getVoucherCounts, deleteVoucher } from '@/services/voucher'
import type { VoucherListDTO, VoucherQueryParams, VoucherCountDTO } from '@/types/voucher'
import { DeleteVoucherDialog } from '@/components/voucher'
import VoucherForm from '@/features/accounting/pages/Vouchers/VoucherForm'
import { getCompanyId } from '@/utils/axios'
import { formatDateDDMMYYYY } from '@/utils/date'
// <CHANGE> Use shadcn/ui primitives for inputs, select, buttons, badges
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Table as ShTable, TableHeader as ShTableHeader, TableRow as ShTableRow, TableHead as ShTableHead, TableBody as ShTableBody, TableCell as ShTableCell } from '@/components/ui/table'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'

// <CHANGE> Fix Radix Select error: avoid empty-string item value
const STATUS_OPTIONS = [
  { value: 'all', label: 'All Statuses' },
  { value: 'draft', label: 'Draft' },
  { value: 'posted', label: 'Posted' },
  { value: 'unposted', label: 'Unposted' },
]

// <CHANGE> Expand page size options to standard set
const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]

export default function VoucherList() {
  const [vouchers, setVouchers] = useState<VoucherListDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [errorDetails, setErrorDetails] = useState<any>(null)
  const [errorModalOpen, setErrorModalOpen] = useState(false)
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [voucherToEdit, setVoucherToEdit] = useState<VoucherListDTO | null>(null)

  // Pagination
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [total, setTotal] = useState(0)

  // Filters
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [debouncedSearch, setDebouncedSearch] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [dateFrom, setDateFrom] = useState<string>('')
  const [dateTo, setDateTo] = useState<string>('')

  // Sorting
  type SortConfig = { field: string; direction: 'asc' | 'desc' }
  const [sorts, setSorts] = useState<SortConfig[]>([{ field: 'voucherDate', direction: 'desc' }])

  // Counts
  const [counts, setCounts] = useState<VoucherCountDTO>({ draft: 0, posted: 0, unposted: 0 })

  // Delete dialog
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [voucherToDelete, setVoucherToDelete] = useState<VoucherListDTO | null>(null)

  useEffect(() => {
    const companyId = getCompanyId()
    const storageKey = `voucherFilters_${companyId}`
    const saved = localStorage.getItem(storageKey)
    if (saved) {
      try {
        const parsed = JSON.parse(saved)
        setSearchTerm(parsed.searchTerm || '')
        setStatusFilter(parsed.statusFilter || '')
        if (parsed.sorts && Array.isArray(parsed.sorts)) {
          setSorts(parsed.sorts)
        } else if (parsed.sortField) {
          setSorts([
            { field: parsed.sortField || 'voucherDate', direction: parsed.sortDirection || 'desc' },
          ])
        }
        setSize(parsed.size || 20)
        if (parsed.dateFrom) setDateFrom(parsed.dateFrom)
        if (parsed.dateTo) setDateTo(parsed.dateTo)
      } catch (e) {
        // <CHANGE> add a statement to avoid no-empty: warn and ignore malformed saved filters
        console.warn('Failed to parse saved voucher filters', e)
      }
    }
  }, [])

  useEffect(() => {
    const companyId = getCompanyId()
    const storageKey = `voucherFilters_${companyId}`
    const state = { searchTerm, statusFilter, sorts, size, dateFrom, dateTo }
    localStorage.setItem(storageKey, JSON.stringify(state))
  }, [searchTerm, statusFilter, sorts, size, dateFrom, dateTo])

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm)
      setPage(0)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const loadVouchers = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      setErrorDetails(null)

      const params: VoucherQueryParams = {
        page,
        size,
        search: debouncedSearch.trim() || undefined,
        status: (statusFilter === 'all' ? undefined : (statusFilter as any)) || undefined,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        sort: sorts.map((s) => `${s.field},${s.direction}`),
      }

      const response = await getVouchers(params)
      setVouchers(response.data)
      setTotal(response.total)
    } catch (err: any) {
      setError(err?.message || 'Failed to load vouchers')
      setErrorDetails(err)
      setVouchers([])
    } finally {
      setLoading(false)
    }
  }, [page, size, debouncedSearch, statusFilter, dateFrom, dateTo, sorts])

  const loadCounts = useCallback(async () => {
    try {
      const response = await getVoucherCounts()
      setCounts(response)
    } catch (e) {
      // <CHANGE> add a statement to avoid no-empty: non-fatal failure to load counts
      console.warn('Failed to load voucher counts', e)
    }
  }, [])

  useEffect(() => {
    loadVouchers()
  }, [loadVouchers])

  useEffect(() => {
    loadCounts()
    const interval = setInterval(loadCounts, 30000)
    return () => clearInterval(interval)
  }, [loadCounts])

  const handleSort = (field: string, event?: React.MouseEvent) => {
    const isShiftClick = event?.shiftKey
    if (isShiftClick) {
      const existingIndex = sorts.findIndex((s) => s.field === field)
      if (existingIndex >= 0) {
        const newSorts = [...sorts]
        newSorts[existingIndex] = {
          field,
          direction: newSorts[existingIndex].direction === 'asc' ? 'desc' : 'asc',
        }
        setSorts(newSorts)
      } else {
        setSorts([...sorts, { field, direction: 'asc' }])
      }
    } else {
      const existingIndex = sorts.findIndex((s) => s.field === field)
      if (existingIndex === 0) {
        setSorts([{ field, direction: sorts[0].direction === 'asc' ? 'desc' : 'asc' }])
      } else {
        setSorts([{ field, direction: 'asc' }])
      }
    }
    setPage(0)
  }

  const removeSort = (index: number) => {
    const newSorts = sorts.filter((_, i) => i !== index)
    setSorts(newSorts.length > 0 ? newSorts : [{ field: 'voucherDate', direction: 'desc' }])
    setPage(0)
  }


  const getSortPriority = (field: string): number | null => {
    const index = sorts.findIndex((s) => s.field === field)
    return index >= 0 ? index + 1 : null
  }

  const handleDeleteClick = (voucher: VoucherListDTO) => {
    if (voucher.status !== 'draft') {
      alert('Only draft vouchers can be deleted')
      return
    }
    setVoucherToDelete(voucher)
    setDeleteDialogOpen(true)
  }

  const handleDeleteConfirm = async (reason: string) => {
    if (!voucherToDelete) return
    try {
      await deleteVoucher(voucherToDelete.id, reason)
      await Promise.all([loadVouchers(), loadCounts()])
      setDeleteDialogOpen(false)
      setVoucherToDelete(null)
    } catch (err: any) {
      throw err
    }
  }

  const handleRetry = () => {
    setError(null)
    setErrorDetails(null)
    loadVouchers()
  }

  const formatAmount = (amount: number, currency: string = 'VND') =>
    new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: currency,
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount)

  const formatDate = (dateString: string) => formatDateDDMMYYYY(dateString)

  return (
    <div>
      {/* Header */}
      <div className="mb-3 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Vouchers</h1>
        <div className="flex items-center gap-2">
          {/* <CHANGE> shadcn badges for counts */}
          <Badge variant="secondary">Draft: {counts.draft}</Badge>
          <Badge variant="outline">Posted: {counts.posted}</Badge>
          <Badge variant="outline">Unposted: {counts.unposted}</Badge>
          <Button variant="secondary" onClick={() => { loadVouchers(); loadCounts(); }}>Refresh</Button>
          <Button onClick={() => setCreateDialogOpen(true)}>Create Voucher</Button>
        </div>
      </div>

      {/* Filters */}
      <div className="rounded-md border p-4 mb-3">
        {sorts.length > 1 && (
          <div className="mb-2 text-xs text-muted-foreground">
            Sort order:{' '}
            {sorts.map((s, i) => (
              <Badge key={i} variant="outline" className="mr-1 cursor-pointer" onClick={() => removeSort(i)}>
                {s.field} {s.direction}
              </Badge>
            ))}{' '}
            (Shift+click to add secondary sort)
          </div>
        )}
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[250px]">
            <div className="relative">
              <Search className="absolute left-2 top-1/2 -translate-y-1/2 text-muted-foreground" size={16} />
              <Input
                placeholder="Search by voucher number or description..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-8"
              />
            </div>
          </div>
          <div className="min-w-[150px]">
            <Select value={statusFilter} onValueChange={(val) => { setStatusFilter(val); setPage(0) }}>
              <SelectTrigger>
                <SelectValue placeholder="All Statuses" />
              </SelectTrigger>
              <SelectContent>
                {STATUS_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div>
            <Input type="date" value={dateFrom} onChange={(e) => { setDateFrom(e.target.value); setPage(0) }} />
          </div>
          <div>
            <Input type="date" value={dateTo} onChange={(e) => { setDateTo(e.target.value); setPage(0) }} />
          </div>
          <Button variant="outline" onClick={() => { setSearchTerm(''); setStatusFilter('all'); setDateFrom(''); setDateTo(''); setPage(0) }}>Clear</Button>
        </div>
      </div>

      {error && (
        <Alert variant="destructive" className="mb-2">
          <AlertTitle>Error</AlertTitle>
          <AlertDescription className="flex items-center justify-between">
            <span>{error}</span>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={handleRetry}>Retry</Button>
              <Button size="sm" variant="outline" onClick={() => setErrorModalOpen(true)}>Details</Button>
            </div>
          </AlertDescription>
        </Alert>
      )}

      {loading && vouchers.length === 0 && (
        <div className="p-4 text-center text-sm text-muted-foreground">Loading…</div>
      )}

      {!loading && !error && vouchers.length === 0 && (
        <div className="rounded-md border p-6 text-center">
          <div className="mb-1 text-base font-medium">No vouchers found</div>
          <div className="text-sm text-muted-foreground">
            {debouncedSearch || (statusFilter && statusFilter !== 'all') || dateFrom || dateTo
              ? 'Try adjusting your filters or search criteria.'
              : 'Get started by creating your first voucher.'}
          </div>
        </div>
      )}

      {!loading && vouchers.length > 0 && (
        <div className="rounded-md border">
          <ShTable>
            <ShTableHeader>
              <ShTableRow>
                <ShTableHead className="cursor-pointer" onClick={(e) => handleSort('voucherNumber', e as any)}>
                  Voucher # {getSortPriority('voucherNumber') && `(${getSortPriority('voucherNumber')})`}
                </ShTableHead>
                <ShTableHead className="cursor-pointer" onClick={(e) => handleSort('voucherDate', e as any)}>
                  Date {getSortPriority('voucherDate') && `(${getSortPriority('voucherDate')})`}
                </ShTableHead>
                <ShTableHead>Type / Description</ShTableHead>
                <ShTableHead className="cursor-pointer" onClick={(e) => handleSort('totalDebit', e as any)}>
                  Amount {getSortPriority('totalDebit') && `(${getSortPriority('totalDebit')})`}
                </ShTableHead>
                <ShTableHead className="cursor-pointer" onClick={(e) => handleSort('status', e as any)}>
                  Status {getSortPriority('status') && `(${getSortPriority('status')})`}
                </ShTableHead>
                <ShTableHead>Entered By</ShTableHead>
                <ShTableHead>Posted By</ShTableHead>
                <ShTableHead>AR/AP Entity (Coming in Epic 4/5)</ShTableHead>
                <ShTableHead>Reversal</ShTableHead>
                <ShTableHead>Attachments</ShTableHead>
                <ShTableHead className="text-right">Actions</ShTableHead>
              </ShTableRow>
            </ShTableHeader>
            <ShTableBody>
              {vouchers.map((voucher) => (
                <ShTableRow key={voucher.id}>
                  <ShTableCell>{voucher.voucherNumber}</ShTableCell>
                  <ShTableCell>{formatDate(voucher.voucherDate)}</ShTableCell>
                  <ShTableCell>{voucher.type || 'N/A'}</ShTableCell>
                  <ShTableCell>
                    {voucher.totalDebit && voucher.totalCredit
                      ? `${formatAmount(voucher.totalDebit, voucher.currency)} / ${formatAmount(voucher.totalCredit, voucher.currency)}`
                      : formatAmount(
                        voucher.totalDebit || voucher.totalCredit || 0,
                        voucher.currency,
                      )}
                  </ShTableCell>
                  <ShTableCell>
                    <Badge variant={voucher.status === 'posted' ? 'secondary' : 'outline'}>
                      {voucher.status}
                    </Badge>
                  </ShTableCell>
                  <ShTableCell>{voucher.enteredByName || 'N/A'}</ShTableCell>
                  <ShTableCell>{voucher.postedByName || '-'}</ShTableCell>
                  <ShTableCell>N/A</ShTableCell>
                  <ShTableCell>{voucher.hasReversal && (<Badge variant="secondary">Reversal</Badge>)}</ShTableCell>
                  <ShTableCell>{voucher.attachmentCount || 0}</ShTableCell>
                  <ShTableCell className="text-right">
                    {voucher.status === 'draft' && (
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="outline" onClick={() => { setVoucherToEdit(voucher); setEditDialogOpen(true) }}>Edit</Button>
                        <Button size="sm" variant="destructive" onClick={() => handleDeleteClick(voucher)}>Delete</Button>
                      </div>
                    )}
                  </ShTableCell>
                </ShTableRow>
              ))}
            </ShTableBody>
          </ShTable>
          <div className="flex items-center justify-between p-3 text-sm">
            <div>
              Showing {Math.min((page * size) + 1, total)}-
              {Math.min((page + 1) * size, total)} of {total}
            </div>
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground">Rows per page</span>
              <Select value={String(size)} onValueChange={(val) => { setSize(parseInt(val, 10)); setPage(0) }}>
                <SelectTrigger className="w-[100px]"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {PAGE_SIZE_OPTIONS.map((opt) => (
                    <SelectItem key={opt} value={String(opt)}>{opt}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <div className="flex items-center gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>Prev</Button>
                <Button variant="outline" size="sm" disabled={(page + 1) * size >= total} onClick={() => setPage((p) => p + 1)}>Next</Button>
              </div>
            </div>
          </div>
        </div>
      )}

      <DeleteVoucherDialog open={deleteDialogOpen} voucher={voucherToDelete} onClose={() => { setDeleteDialogOpen(false); setVoucherToDelete(null) }} onConfirm={handleDeleteConfirm} />

      <Dialog open={errorModalOpen} onOpenChange={setErrorModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Error Details</DialogTitle>
          </DialogHeader>
          <pre className="max-h-[60vh] overflow-auto whitespace-pre-wrap text-sm">{JSON.stringify(errorDetails, null, 2)}</pre>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setErrorModalOpen(false)}>Close</Button>
            <Button onClick={() => { navigator.clipboard.writeText(JSON.stringify(errorDetails, null, 2)) }}>Copy</Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <DialogContent className="max-w-[90vw]">
          <DialogHeader>
            <DialogTitle>Create Voucher</DialogTitle>
          </DialogHeader>
          <div className="max-h-[70vh] overflow-auto p-0">
            <div className="p-3">
              <VoucherForm onSave={() => { setCreateDialogOpen(false); loadVouchers(); loadCounts() }} onCancel={() => setCreateDialogOpen(false)} />
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={editDialogOpen} onOpenChange={(open) => { setEditDialogOpen(open); if (!open) setVoucherToEdit(null) }}>
        <DialogContent className="max-w-[90vw]">
          <DialogHeader>
            <DialogTitle>Edit Voucher</DialogTitle>
          </DialogHeader>
          <div className="max-h-[70vh] overflow-auto p-0">
            <div className="p-3">
              <VoucherForm voucherId={voucherToEdit?.id} onSave={() => { setEditDialogOpen(false); setVoucherToEdit(null); loadVouchers(); loadCounts() }} onCancel={() => { setEditDialogOpen(false); setVoucherToEdit(null) }} />
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}

