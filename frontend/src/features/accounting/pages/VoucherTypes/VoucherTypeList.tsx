import { useState, useEffect, useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Plus,
  Search,
  RefreshCw,
  MoreVertical,
  Edit,
  Trash2,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
} from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { Switch } from '@/components/ui/switch'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  getVoucherTypes,
  deleteVoucherType,
  deactivateVoucherType,
  activateVoucherType,
} from '@/services/voucherType'
import type { VoucherType, VoucherTypeQueryParams } from '@/types/voucherType'
import VoucherTypeDialog from './VoucherTypeDialog'
import DeleteVoucherTypeDialog from '@/components/voucher-type/DeleteVoucherTypeDialog'

export default function VoucherTypeList() {
  const { t } = useTranslation()
  const [voucherTypes, setVoucherTypes] = useState<VoucherType[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [debouncedSearch, setDebouncedSearch] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<string>('all')

  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(12)
  const [_totalElements, setTotalElements] = useState(0)

  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [selectedVoucherType, setSelectedVoucherType] = useState<VoucherType | null>(null)
  const [togglingIds, setTogglingIds] = useState<Set<number>>(new Set())

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const loadVoucherTypes = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const params: VoucherTypeQueryParams = {}
      if (debouncedSearch.trim()) {
        params.search = debouncedSearch.trim()
      }
      const response = await getVoucherTypes(params)
      setVoucherTypes(response.data)
      setTotalElements(typeof response.total === 'number' ? response.total : response.data.length)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load voucher types'
      setError(errorMessage)
      toast.error('Failed to load voucher types', { description: errorMessage })
    } finally {
      setLoading(false)
    }
  }, [debouncedSearch])

  useEffect(() => {
    setPage(1)
  }, [debouncedSearch, pageSize, statusFilter])

  useEffect(() => {
    loadVoucherTypes()
  }, [loadVoucherTypes])

  const handleDeleteClick = (voucherType: VoucherType) => {
    setSelectedVoucherType(voucherType)
    setDeleteDialogOpen(true)
  }

  const handleDeleteConfirm = async () => {
    if (!selectedVoucherType) return
    try {
      await deleteVoucherType(selectedVoucherType.id)
      toast.success('Voucher type deleted successfully')
      setDeleteDialogOpen(false)
      setSelectedVoucherType(null)
      await loadVoucherTypes()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to delete voucher type'
      toast.error('Failed to delete voucher type', { description: errorMessage })
    }
  }

  const handleToggleStatus = async (voucherType: VoucherType) => {
    setTogglingIds((prev) => new Set(prev).add(voucherType.id))
    try {
      if (voucherType.status === 'ACTIVE') {
        await deactivateVoucherType(voucherType.id)
        toast.success('Voucher type deactivated successfully')
      } else {
        await activateVoucherType(voucherType.id)
        toast.success('Voucher type activated successfully')
      }
      await loadVoucherTypes()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to update voucher type'
      toast.error('Failed to update voucher type', { description: errorMessage })
    } finally {
      setTogglingIds((prev) => {
        const next = new Set(prev)
        next.delete(voucherType.id)
        return next
      })
    }
  }

  const handleEditClick = (voucherType: VoucherType) => {
    setSelectedVoucherType(voucherType)
    setEditDialogOpen(true)
  }

  const handleRefresh = () => {
    loadVoucherTypes()
  }

  const filteredVoucherTypes = useMemo(() => {
    return voucherTypes.filter((vt) => {
      if (statusFilter === 'all') return true
      return vt.status === statusFilter
    })
  }, [voucherTypes, statusFilter])

  const totalFiltered = filteredVoucherTypes.length
  const pagedVoucherTypes = useMemo(
    () => filteredVoucherTypes.slice((page - 1) * pageSize, page * pageSize),
    [filteredVoucherTypes, page, pageSize],
  )
  const totalPages = Math.max(1, Math.ceil(totalFiltered / pageSize))

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">{t('vouchers.voucherTypes')}</h1>
        <Button onClick={() => setCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          {t('vouchers.addVoucherType')}
        </Button>
      </div>

      <div className="flex items-center gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={t('vouchers.searchByCodeOrName')}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-8"
          />
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <Select
            value={statusFilter}
            onValueChange={(value) => {
              setStatusFilter(value)
              setPage(1)
            }}
          >
            <SelectTrigger aria-label="Filter status" className="w-28">
              <SelectValue placeholder="All statuses" />
            </SelectTrigger>
            <SelectContent side="bottom" align="end">
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="ACTIVE">Active</SelectItem>
              <SelectItem value="INACTIVE">Inactive</SelectItem>
            </SelectContent>
          </Select>
          <Button
            variant="outline"
            onClick={handleRefresh}
            disabled={loading}
            className="h-9 w-9 p-0"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {loading && voucherTypes.length === 0 ? (
        <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Skeleton key={i} className="h-32 w-full rounded-xl" />
          ))}
        </div>
      ) : voucherTypes.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground">
          <p className="text-lg font-medium">No voucher types found</p>
          <p className="text-sm">
            {debouncedSearch
              ? 'Try adjusting your search criteria.'
              : 'Get started by creating your first voucher type.'}
          </p>
        </div>
      ) : (
        <>
          <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 voucher-stagger">
            {pagedVoucherTypes.map((vt) => (
              <Card
                key={vt.id}
                className="voucher-card-interactive voucher-row-animate cursor-pointer group relative py-4"
              >
                <CardContent className="p-4">
                  <div className="flex items-start justify-between">
                    <div className="space-y-1 flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-xl font-bold text-primary font-mono tracking-wide">
                          {vt.typeCode}
                        </span>
                        <Badge
                          className={
                            vt.status === 'ACTIVE'
                              ? 'rounded-full border-none bg-green-600/10 text-green-600 dark:bg-green-400/10 dark:text-green-400'
                              : 'bg-destructive/10 text-destructive rounded-full border-none'
                          }
                        >
                          <span
                            className={`size-1.5 rounded-full mr-1 ${
                              vt.status === 'ACTIVE'
                                ? 'bg-green-600 dark:bg-green-400'
                                : 'bg-destructive'
                            }`}
                            aria-hidden="true"
                          />
                          {vt.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                        </Badge>
                      </div>
                      <p className="text-sm text-muted-foreground truncate" title={vt.typeName}>
                        {vt.typeName}
                      </p>
                    </div>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-8 w-8 p-0 opacity-0 group-hover:opacity-100 transition-opacity"
                        >
                          <MoreVertical className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => handleEditClick(vt)}>
                          <Edit className="mr-2 h-4 w-4" />
                          Edit
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          variant="destructive"
                          onClick={() => handleDeleteClick(vt)}
                        >
                          <Trash2 className="mr-2 h-4 w-4" />
                          Delete
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                  <div className="mt-4 flex items-center justify-between border-t pt-3">
                    <Label
                      htmlFor={`toggle-${vt.id}`}
                      className="text-xs text-muted-foreground cursor-pointer"
                    >
                      {vt.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                    </Label>
                    <Switch
                      id={`toggle-${vt.id}`}
                      checked={vt.status === 'ACTIVE'}
                      disabled={togglingIds.has(vt.id)}
                      onCheckedChange={() => handleToggleStatus(vt)}
                    />
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          <div className="flex items-center justify-between border-t px-4 py-4">
            <div className="text-sm text-muted-foreground">
              Total: <strong className="text-foreground">{totalFiltered}</strong> records
            </div>
            <div className="flex items-center gap-6">
              <div className="hidden items-center gap-2 lg:flex">
                <Label htmlFor="rows-per-page" className="text-sm font-medium">
                  Cards per page
                </Label>
                <Select
                  value={`${pageSize}`}
                  onValueChange={(value) => {
                    setPageSize(Number(value))
                    setPage(1)
                  }}
                  disabled={loading}
                >
                  <SelectTrigger size="sm" className="w-20" id="rows-per-page">
                    <SelectValue placeholder={pageSize} />
                  </SelectTrigger>
                  <SelectContent side="top">
                    {[8, 12, 16, 24, 48].map((size) => (
                      <SelectItem key={size} value={`${size}`}>
                        {size}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex items-center justify-center text-sm font-medium">
                Page {page} / {totalPages}
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  className="hidden h-8 w-8 p-0 lg:flex"
                  onClick={() => setPage(1)}
                  disabled={page === 1 || loading}
                >
                  <span className="sr-only">First page</span>
                  <ChevronsLeft className="size-4" />
                </Button>
                <Button
                  variant="outline"
                  className="h-8 w-8"
                  size="icon"
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={page === 1 || loading}
                >
                  <span className="sr-only">Previous page</span>
                  <ChevronLeft className="size-4" />
                </Button>
                <Button
                  variant="outline"
                  className="h-8 w-8"
                  size="icon"
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={page >= totalPages || loading}
                >
                  <span className="sr-only">Next page</span>
                  <ChevronRight className="size-4" />
                </Button>
                <Button
                  variant="outline"
                  className="hidden h-8 w-8 lg:flex"
                  size="icon"
                  onClick={() => setPage(totalPages)}
                  disabled={page >= totalPages || loading}
                >
                  <span className="sr-only">Last page</span>
                  <ChevronsRight className="size-4" />
                </Button>
              </div>
            </div>
          </div>
        </>
      )}

      <VoucherTypeDialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
        onSuccess={() => {
          setCreateDialogOpen(false)
          loadVoucherTypes()
        }}
      />

      <VoucherTypeDialog
        open={editDialogOpen}
        onClose={() => {
          setEditDialogOpen(false)
          setSelectedVoucherType(null)
        }}
        onSuccess={() => {
          setEditDialogOpen(false)
          setSelectedVoucherType(null)
          loadVoucherTypes()
        }}
        voucherTypeId={selectedVoucherType?.id}
      />

      <DeleteVoucherTypeDialog
        open={deleteDialogOpen}
        voucherType={selectedVoucherType}
        onClose={() => {
          setDeleteDialogOpen(false)
          setSelectedVoucherType(null)
        }}
        onConfirm={handleDeleteConfirm}
      />
    </div>
  )
}
