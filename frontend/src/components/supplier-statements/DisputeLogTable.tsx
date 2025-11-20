'use client'

import { useState, useCallback, useEffect } from 'react'
import { AlertCircle, CheckCircle2, XCircle, Clock, RefreshCw } from 'lucide-react'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
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
import { supplierStatementService } from '@/services/supplierStatement'
import type {
  SupplierStatementDispute,
  DisputeStatus,
  DisputeFilterParams,
  UpdateDisputeRequest,
} from '@/types/supplierStatement'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]

interface DisputeLogTableProps {
  supplierId?: number
}

export function DisputeLogTable({ supplierId }: DisputeLogTableProps) {
  const [loading, setLoading] = useState(false)
  const [disputes, setDisputes] = useState<SupplierStatementDispute[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [searchTerm, setSearchTerm] = useState('')

  // Dialog state
  const [selectedDispute, setSelectedDispute] = useState<SupplierStatementDispute | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [resolutionNotes, setResolutionNotes] = useState('')
  const [updating, setUpdating] = useState(false)

  const loadDisputes = useCallback(async () => {
    try {
      setLoading(true)
      const params: DisputeFilterParams = {
        page,
        size: pageSize,
        supplier: supplierId,
        status: statusFilter || undefined,
      }
      const response = await supplierStatementService.listDisputes(params)
      setDisputes(response.disputes)
      setTotalElements(response.totalItems)
      setTotalPages(response.totalPages)
    } catch (error) {
      toast.error(`Failed to load disputes: ${error}`)
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, supplierId, statusFilter])

  useEffect(() => {
    loadDisputes()
  }, [loadDisputes])

  const handleRefresh = () => {
    setPage(0)
    loadDisputes()
  }

  const handleResolve = (dispute: SupplierStatementDispute) => {
    setSelectedDispute(dispute)
    setResolutionNotes('')
    setDialogOpen(true)
  }

  const handleUpdateStatus = async (status: DisputeStatus) => {
    if (!selectedDispute) return

    try {
      setUpdating(true)
      const request: UpdateDisputeRequest = {
        status,
        resolutionNotes: resolutionNotes.trim() || undefined,
      }
      await supplierStatementService.updateDispute(selectedDispute.id, request)
      toast.success(`Dispute ${status.toLowerCase()} successfully`)
      setDialogOpen(false)
      loadDisputes()
    } catch (error: any) {
      toast.error('Failed to update dispute', {
        description: error?.message || 'Unknown error',
      })
    } finally {
      setUpdating(false)
    }
  }

  const getStatusIcon = (status: DisputeStatus) => {
    switch (status) {
      case 'OPEN':
        return <AlertCircle className="h-4 w-4 text-orange-500" />
      case 'IN_PROGRESS':
        return <Clock className="h-4 w-4 text-blue-500" />
      case 'RESOLVED':
        return <CheckCircle2 className="h-4 w-4 text-green-500" />
      case 'REJECTED':
        return <XCircle className="h-4 w-4 text-red-500" />
      default:
        return null
    }
  }

  const getStatusVariant = (status: DisputeStatus): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (status) {
      case 'OPEN':
        return 'outline'
      case 'IN_PROGRESS':
        return 'secondary'
      case 'RESOLVED':
        return 'default'
      case 'REJECTED':
        return 'destructive'
      default:
        return 'outline'
    }
  }

  const filteredDisputes = disputes.filter(
    (d) =>
      !searchTerm ||
      d.billNumber?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      d.disputeReason.toLowerCase().includes(searchTerm.toLowerCase())
  )

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex items-center gap-4">
        <Input
          placeholder="Search by bill number or reason..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="max-w-sm"
        />
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="All Statuses" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">All Statuses</SelectItem>
            <SelectItem value="OPEN">Open</SelectItem>
            <SelectItem value="IN_PROGRESS">In Progress</SelectItem>
            <SelectItem value="RESOLVED">Resolved</SelectItem>
            <SelectItem value="REJECTED">Rejected</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" onClick={handleRefresh} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Table */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Bill Number</TableHead>
              <TableHead>Dispute Reason</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead>Created By</TableHead>
              <TableHead>Created Date</TableHead>
              <TableHead>Resolved By</TableHead>
              <TableHead>Resolved Date</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell><Skeleton className="h-4 w-[100px]" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-[200px]" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-[80px]" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-[100px]" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-[120px]" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-[100px]" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-[120px]" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-[100px]" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-[80px]" /></TableCell>
                </TableRow>
              ))
            ) : filteredDisputes.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9} className="text-center py-10 text-muted-foreground">
                  No disputes found
                </TableCell>
              </TableRow>
            ) : (
              filteredDisputes.map((dispute) => (
                <TableRow key={dispute.id}>
                  <TableCell className="font-medium">{dispute.billNumber || '-'}</TableCell>
                  <TableCell className="max-w-[300px] truncate">{dispute.disputeReason}</TableCell>
                  <TableCell>
                    <Badge variant={getStatusVariant(dispute.status)} className="flex items-center gap-1 w-fit">
                      {getStatusIcon(dispute.status)}
                      {dispute.status}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {dispute.disputedAmount
                      ? `${dispute.disputedAmount.toLocaleString('vi-VN')}₫`
                      : '-'}
                  </TableCell>
                  <TableCell>{dispute.createdByName}</TableCell>
                  <TableCell>{format(new Date(dispute.createdAt), 'dd/MM/yyyy HH:mm')}</TableCell>
                  <TableCell>{dispute.resolvedByName || '-'}</TableCell>
                  <TableCell>
                    {dispute.resolvedAt ? format(new Date(dispute.resolvedAt), 'dd/MM/yyyy HH:mm') : '-'}
                  </TableCell>
                  <TableCell className="text-right">
                    {dispute.status !== 'RESOLVED' && dispute.status !== 'REJECTED' && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleResolve(dispute)}
                      >
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
      <div className="flex items-center justify-between">
        <div className="text-sm text-muted-foreground">
          Showing {filteredDisputes.length} of {totalElements} disputes
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

      {/* Resolve Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Resolve Dispute</DialogTitle>
            <DialogDescription>
              Update dispute status and add resolution notes
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Resolution Notes</Label>
              <Textarea
                placeholder="Enter resolution notes..."
                value={resolutionNotes}
                onChange={(e) => setResolutionNotes(e.target.value)}
                rows={4}
                disabled={updating}
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDialogOpen(false)}
              disabled={updating}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => handleUpdateStatus('REJECTED')}
              disabled={updating}
            >
              Reject
            </Button>
            <Button
              onClick={() => handleUpdateStatus('RESOLVED')}
              disabled={updating}
            >
              {updating ? 'Updating...' : 'Resolve'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

