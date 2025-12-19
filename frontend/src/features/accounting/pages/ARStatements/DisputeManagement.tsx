'use client'

import { useCallback, useEffect, useState } from 'react'
import { AlertTriangle, RefreshCw, CheckCircle2 } from 'lucide-react'
import { format } from 'date-fns'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { arStatementService } from '@/services/arStatement'
import { getCustomers } from '@/features/customers/services/customer'
import type { ARStatementDispute, DisputeStatus } from '@/types/arStatement'
import type { Customer } from '@/types/customer'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]

export function DisputeManagement() {
  const [loading, setLoading] = useState(false)
  const [disputes, setDisputes] = useState<ARStatementDispute[]>([])
  const [customers, setCustomers] = useState<Customer[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)

  // Filters
  const [selectedCustomerId, setSelectedCustomerId] = useState<number | null>(null)
  const [status, setStatus] = useState<DisputeStatus | 'all'>('all')
  const [dateFrom, setDateFrom] = useState<string>('')
  const [dateTo, setDateTo] = useState<string>('')

  // Dialog states
  const [resolveDialogOpen, setResolveDialogOpen] = useState(false)
  const [selectedDispute, setSelectedDispute] = useState<ARStatementDispute | null>(null)
  const [resolutionNotes, setResolutionNotes] = useState('')
  const [resolving, setResolving] = useState(false)

  useEffect(() => {
    loadCustomers()
  }, [])

  const loadCustomers = async () => {
    try {
      const response = await getCustomers({ page: 1, size: 1000 })
      setCustomers(response.data || [])
    } catch (err: any) {
      toast.error('Failed to load customers')
    }
  }

  const loadDisputes = useCallback(async () => {
    try {
      setLoading(true)
      const response = await arStatementService.getDisputes({
        page,
        size: pageSize,
        customerId: selectedCustomerId || undefined,
        status: status !== 'all' ? status : undefined,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
      })
      setDisputes(response.disputes)
      setTotalElements(response.totalItems)
      setTotalPages(response.totalPages)
    } catch (err: any) {
      toast.error('Failed to load disputes', {
        description: err?.message || 'Unknown error',
      })
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, selectedCustomerId, status, dateFrom, dateTo])

  useEffect(() => {
    loadDisputes()
  }, [loadDisputes])

  const handleRefresh = () => {
    loadDisputes()
    toast.success('Disputes refreshed')
  }

  const handleResolve = async () => {
    if (!selectedDispute || !resolutionNotes.trim()) {
      toast.error('Resolution notes are required')
      return
    }

    try {
      setResolving(true)
      await arStatementService.resolveDispute(selectedDispute.id, {
        resolutionNotes: resolutionNotes.trim(),
      })
      toast.success('Dispute resolved successfully')
      setResolveDialogOpen(false)
      setSelectedDispute(null)
      setResolutionNotes('')
      loadDisputes()
    } catch (err: any) {
      toast.error('Failed to resolve dispute', {
        description: err?.message || 'Unknown error',
      })
    } finally {
      setResolving(false)
    }
  }

  const getStatusBadgeVariant = (
    status: DisputeStatus,
  ): 'destructive' | 'default' | 'secondary' | 'outline' => {
    switch (status) {
      case 'OPEN':
        return 'destructive'
      case 'IN_PROGRESS':
        return 'secondary'
      case 'RESOLVED':
        return 'default'
      case 'REJECTED':
        return 'outline'
      default:
        return 'outline'
    }
  }

  const getVarianceBadgeVariant = (varianceType?: string): 'destructive' | 'secondary' => {
    return varianceType === 'SIGNIFICANT' ? 'destructive' : 'secondary'
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <AlertTriangle className="h-6 w-6 text-primary" />
            Dispute Management
          </h1>
          <p className="text-muted-foreground">
            View and resolve customer statement reconciliation disputes
          </p>
        </div>
        <Button variant="outline" onClick={handleRefresh} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <Select
          value={selectedCustomerId?.toString() || 'all'}
          onValueChange={(value) => setSelectedCustomerId(value === 'all' ? null : Number(value))}
        >
          <SelectTrigger className="w-[200px]">
            <SelectValue placeholder="All Customers" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Customers</SelectItem>
            {customers.map((customer) => (
              <SelectItem key={customer.id} value={customer.id.toString()}>
                {customer.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={status} onValueChange={(value) => setStatus(value as DisputeStatus | 'all')}>
          <SelectTrigger className="w-[180px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Status</SelectItem>
            <SelectItem value="OPEN">Open</SelectItem>
            <SelectItem value="IN_PROGRESS">In Progress</SelectItem>
            <SelectItem value="RESOLVED">Resolved</SelectItem>
            <SelectItem value="REJECTED">Rejected</SelectItem>
          </SelectContent>
        </Select>
        <Input
          type="date"
          placeholder="Date From"
          value={dateFrom}
          onChange={(e) => setDateFrom(e.target.value)}
          className="w-[150px]"
        />
        <Input
          type="date"
          placeholder="Date To"
          value={dateTo}
          onChange={(e) => setDateTo(e.target.value)}
          className="w-[150px]"
        />
      </div>

      {/* Table */}
      <div className="rounded-md border" data-testid="disputes-grid">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Invoice #</TableHead>
              <TableHead className="text-right">System Amount</TableHead>
              <TableHead className="text-right">Customer Amount</TableHead>
              <TableHead className="text-right">Variance</TableHead>
              <TableHead>Variance Type</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Created</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell>
                    <Skeleton className="h-4 w-[120px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[100px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[100px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[80px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[100px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[80px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[120px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[80px]" />
                  </TableCell>
                </TableRow>
              ))
            ) : disputes.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} className="text-center py-10 text-muted-foreground">
                  No disputes found
                </TableCell>
              </TableRow>
            ) : (
              disputes.map((dispute) => (
                <TableRow key={dispute.id} data-testid="dispute-row">
                  <TableCell className="font-medium" data-testid="dispute-invoice-number">
                    {dispute.invoiceNumber || '-'}
                  </TableCell>
                  <TableCell className="text-right">
                    {dispute.systemAmount
                      ? dispute.systemAmount.toLocaleString('vi-VN') + '₫'
                      : '-'}
                  </TableCell>
                  <TableCell className="text-right">
                    {dispute.customerAmount
                      ? dispute.customerAmount.toLocaleString('vi-VN') + '₫'
                      : '-'}
                  </TableCell>
                  <TableCell className="text-right">
                    {dispute.variance ? dispute.variance.toLocaleString('vi-VN') + '₫' : '-'}
                  </TableCell>
                  <TableCell>
                    {dispute.varianceType && (
                      <Badge variant={getVarianceBadgeVariant(dispute.varianceType)}>
                        {dispute.varianceType}
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant={getStatusBadgeVariant(dispute.status)}
                      data-testid="dispute-status"
                    >
                      {dispute.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-sm">
                    {format(new Date(dispute.createdAt), 'dd/MM/yyyy')}
                  </TableCell>
                  <TableCell className="text-right">
                    {dispute.status !== 'RESOLVED' && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setSelectedDispute(dispute)
                          setResolveDialogOpen(true)
                        }}
                      >
                        <CheckCircle2 className="h-4 w-4 mr-1" />
                        Resolve
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {!loading && disputes.length > 0 && (
        <div className="flex items-center justify-between">
          <div className="text-sm text-muted-foreground">
            Showing {page * pageSize + 1} to {Math.min((page + 1) * pageSize, totalElements)} of{' '}
            {totalElements} disputes
          </div>
          <div className="flex items-center gap-2">
            <Select value={String(pageSize)} onValueChange={(value) => setPageSize(Number(value))}>
              <SelectTrigger className="w-[100px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PAGE_SIZE_OPTIONS.map((size) => (
                  <SelectItem key={size} value={String(size)}>
                    {size} per page
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage(Math.max(0, page - 1))}
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
              onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
              disabled={page >= totalPages - 1}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Resolve Dialog */}
      <Dialog open={resolveDialogOpen} onOpenChange={setResolveDialogOpen}>
        <DialogContent data-testid="dispute-detail-dialog">
          <DialogHeader>
            <DialogTitle>Resolve Dispute</DialogTitle>
            <DialogDescription>
              Add resolution notes for invoice {selectedDispute?.invoiceNumber}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            {selectedDispute && (
              <div className="rounded-md bg-muted p-3 text-sm space-y-1">
                <p>
                  <span className="font-medium">System Amount:</span>{' '}
                  {selectedDispute.systemAmount?.toLocaleString('vi-VN')}₫
                </p>
                <p>
                  <span className="font-medium">Customer Amount:</span>{' '}
                  {selectedDispute.customerAmount?.toLocaleString('vi-VN')}₫
                </p>
                <p>
                  <span className="font-medium">Variance:</span>{' '}
                  <span data-testid="dispute-variance">
                    {selectedDispute.variance?.toLocaleString('vi-VN')}₫
                  </span>{' '}
                  (<span data-testid="dispute-variance-type">{selectedDispute.varianceType}</span>)
                </p>
                {selectedDispute.notes && (
                  <p>
                    <span className="font-medium">Notes:</span> {selectedDispute.notes}
                  </p>
                )}
              </div>
            )}
            <div className="space-y-2">
              <Label htmlFor="resolutionNotes">Resolution Notes *</Label>
              <Textarea
                id="resolutionNotes"
                placeholder="Enter resolution details..."
                value={resolutionNotes}
                onChange={(e) => setResolutionNotes(e.target.value)}
                rows={4}
                disabled={resolving}
                data-testid="resolution-notes-input"
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setResolveDialogOpen(false)}
              disabled={resolving}
            >
              Cancel
            </Button>
            <Button
              onClick={handleResolve}
              disabled={resolving || !resolutionNotes.trim()}
              data-testid="resolve-dispute-button"
            >
              <CheckCircle2 className={`mr-2 h-4 w-4 ${resolving ? 'animate-spin' : ''}`} />
              {resolving ? 'Resolving...' : 'Resolve'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
