import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Paper,
  Typography,
  TextField,
  InputAdornment,
  MenuItem,
  FormControl,
  Select,
  Chip,
  CircularProgress,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  IconButton,
  Tooltip,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TableSortLabel,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import DeleteIcon from '@mui/icons-material/Delete'
import RefreshIcon from '@mui/icons-material/Refresh'
import ErrorIcon from '@mui/icons-material/Error'
import AddIcon from '@mui/icons-material/Add'
import EditIcon from '@mui/icons-material/Edit'
import CloseIcon from '@mui/icons-material/Close'
import { getVouchers, getVoucherCounts, deleteVoucher } from '../services/voucher'
import type { VoucherListDTO, VoucherQueryParams, VoucherCountDTO } from '../types/voucher'
import DeleteVoucherDialog from '../components/voucher/DeleteVoucherDialog'
import VoucherForm from './VoucherForm'
import { getCompanyId } from '../utils/axios'
import { formatDateDDMMYYYY } from '../utils/date'

const STATUS_OPTIONS = [
  { value: '', label: 'All Statuses' },
  { value: 'draft', label: 'Draft' },
  { value: 'posted', label: 'Posted' },
  { value: 'unposted', label: 'Unposted' },
]

const PAGE_SIZE_OPTIONS = [20, 30, 50]

/**
 * Voucher List page - displays vouchers in a table with filtering, search, sorting, and pagination.
 * Supports localStorage persistence for filters/search/sort state.
 */
export default function VoucherList() {
  const navigate = useNavigate()
  const theme = useTheme()
  const fullScreen = useMediaQuery(theme.breakpoints.down('md'))
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
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [dateFrom, setDateFrom] = useState<string>('')
  const [dateTo, setDateTo] = useState<string>('')

  // Sorting - support multiple columns
  type SortConfig = { field: string; direction: 'asc' | 'desc' }
  const [sorts, setSorts] = useState<SortConfig[]>([{ field: 'voucherDate', direction: 'desc' }])

  // Counts for badges
  const [counts, setCounts] = useState<VoucherCountDTO>({ draft: 0, posted: 0, unposted: 0 })

  // Delete dialog
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [voucherToDelete, setVoucherToDelete] = useState<VoucherListDTO | null>(null)

  // Load persisted state from localStorage
  useEffect(() => {
    const companyId = getCompanyId()
    const storageKey = `voucherFilters_${companyId}`
    const saved = localStorage.getItem(storageKey)
    if (saved) {
      try {
        const parsed = JSON.parse(saved)
        setSearchTerm(parsed.searchTerm || '')
        setStatusFilter(parsed.statusFilter || '')
        // Restore sorts from localStorage or default
        if (parsed.sorts && Array.isArray(parsed.sorts)) {
          setSorts(parsed.sorts)
        } else if (parsed.sortField) {
          // Backward compatibility: migrate old single sort to array
          setSorts([
            { field: parsed.sortField || 'voucherDate', direction: parsed.sortDirection || 'desc' },
          ])
        }
        setSize(parsed.size || 20)
        if (parsed.dateFrom) setDateFrom(parsed.dateFrom)
        if (parsed.dateTo) setDateTo(parsed.dateTo)
      } catch (e) {
        // Invalid saved data, use defaults
      }
    }
  }, [])

  // Persist state to localStorage
  useEffect(() => {
    const companyId = getCompanyId()
    const storageKey = `voucherFilters_${companyId}`
    const state = {
      searchTerm,
      statusFilter,
      sorts,
      size,
      dateFrom: dateFrom,
      dateTo: dateTo,
    }
    localStorage.setItem(storageKey, JSON.stringify(state))
  }, [searchTerm, statusFilter, sorts, size, dateFrom, dateTo])

  // Debounce search
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm)
      setPage(0) // Reset to first page on search
    }, 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  // Load vouchers
  const loadVouchers = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      setErrorDetails(null)

      const params: VoucherQueryParams = {
        page,
        size,
        search: debouncedSearch.trim() || undefined,
        status: (statusFilter as any) || undefined,
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

  // Load counts
  const loadCounts = useCallback(async () => {
    try {
      const response = await getVoucherCounts()
      setCounts(response)
    } catch (err) {
      // Silently fail for counts - not critical
    }
  }, [])

  useEffect(() => {
    loadVouchers()
  }, [loadVouchers])

  useEffect(() => {
    loadCounts()
    // Refresh counts every 30 seconds
    const interval = setInterval(loadCounts, 30000)
    return () => clearInterval(interval)
  }, [loadCounts])

  const handleSort = (field: string, event?: React.MouseEvent) => {
    const isShiftClick = event?.shiftKey

    if (isShiftClick) {
      // Add secondary sort
      const existingIndex = sorts.findIndex((s) => s.field === field)
      if (existingIndex >= 0) {
        // Toggle direction if already in sorts
        const newSorts = [...sorts]
        newSorts[existingIndex] = {
          field,
          direction: newSorts[existingIndex].direction === 'asc' ? 'desc' : 'asc',
        }
        setSorts(newSorts)
      } else {
        // Add new secondary sort
        setSorts([...sorts, { field, direction: 'asc' }])
      }
    } else {
      // Primary sort (replace all sorts)
      const existingIndex = sorts.findIndex((s) => s.field === field)
      if (existingIndex === 0) {
        // Toggle direction if already primary
        setSorts([{ field, direction: sorts[0].direction === 'asc' ? 'desc' : 'asc' }])
      } else {
        // Set as primary (first sort)
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

  const getSortDirection = (field: string): 'asc' | 'desc' | false => {
    const sort = sorts.find((s) => s.field === field)
    return sort ? sort.direction : false
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
      // Reload vouchers and counts
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

  const formatAmount = (amount: number, currency: string = 'VND') => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: currency,
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount)
  }

  const formatDate = (dateString: string) => {
    return formatDateDDMMYYYY(dateString)
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">Vouchers</Typography>
        <Box display="flex" gap={1} alignItems="center">
          <Chip label={`Draft: ${counts.draft}`} color="warning" size="small" />
          <Chip label={`Posted: ${counts.posted}`} color="success" size="small" />
          <Chip label={`Unposted: ${counts.unposted}`} color="info" size="small" />
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setCreateDialogOpen(true)}
          >
            Create Voucher
          </Button>
        </Box>
      </Box>

      {/* Filters */}
      <Paper sx={{ p: 2, mb: 3 }}>
        {sorts.length > 1 && (
          <Box mb={1}>
            <Typography variant="caption" color="text.secondary">
              Sort order:{' '}
              {sorts.map((s, i) => (
                <Chip
                  key={i}
                  label={`${s.field} ${s.direction}`}
                  size="small"
                  onDelete={() => removeSort(i)}
                  sx={{ mr: 0.5 }}
                />
              ))}{' '}
              (Shift+click to add secondary sort)
            </Typography>
          </Box>
        )}
        <Box display="flex" gap={2} flexWrap="wrap" alignItems="flex-end">
          <TextField
            label="Search"
            placeholder="Search by voucher number or description..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
            }}
            sx={{ minWidth: 250 }}
          />
          <FormControl sx={{ minWidth: 150 }}>
            <Select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value)
                setPage(0)
              }}
              displayEmpty
            >
              {STATUS_OPTIONS.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>
                  {opt.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <TextField
            label="Date From"
            type="date"
            value={dateFrom}
            onChange={(e) => {
              setDateFrom(e.target.value)
              setPage(0)
            }}
            size="small"
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            label="Date To"
            type="date"
            value={dateTo}
            onChange={(e) => {
              setDateTo(e.target.value)
              setPage(0)
            }}
            size="small"
            InputLabelProps={{ shrink: true }}
          />
          <Button
            variant="outlined"
            onClick={() => {
              setSearchTerm('')
              setStatusFilter('')
              setDateFrom('')
              setDateTo('')
              setPage(0)
            }}
          >
            Clear
          </Button>
        </Box>
      </Paper>

      {/* Error State */}
      {error && (
        <Alert
          severity="error"
          action={
            <Box>
              <Button size="small" onClick={handleRetry} startIcon={<RefreshIcon />}>
                Retry
              </Button>
              <Button
                size="small"
                onClick={() => setErrorModalOpen(true)}
                startIcon={<ErrorIcon />}
              >
                Details
              </Button>
            </Box>
          }
          sx={{ mb: 2 }}
        >
          {error}
        </Alert>
      )}

      {/* Loading State */}
      {loading && vouchers.length === 0 && (
        <Box display="flex" justifyContent="center" p={4}>
          <CircularProgress />
        </Box>
      )}

      {/* Empty State */}
      {!loading && !error && vouchers.length === 0 && (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="h6" gutterBottom>
            No vouchers found
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {debouncedSearch || statusFilter || dateFrom || dateTo
              ? 'Try adjusting your filters or search criteria.'
              : 'Get started by creating your first voucher.'}
          </Typography>
        </Paper>
      )}

      {/* Vouchers Table */}
      {!loading && vouchers.length > 0 && (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>
                  <TableSortLabel
                    active={!!getSortDirection('voucherNumber')}
                    direction={getSortDirection('voucherNumber') || 'asc'}
                    onClick={(e) => handleSort('voucherNumber', e)}
                  >
                    Voucher #{' '}
                    {getSortPriority('voucherNumber') && `(${getSortPriority('voucherNumber')})`}
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={!!getSortDirection('voucherDate')}
                    direction={getSortDirection('voucherDate') || 'asc'}
                    onClick={(e) => handleSort('voucherDate', e)}
                  >
                    Date {getSortPriority('voucherDate') && `(${getSortPriority('voucherDate')})`}
                  </TableSortLabel>
                </TableCell>
                <TableCell>Type / Description</TableCell>
                <TableCell>
                  <TableSortLabel
                    active={!!getSortDirection('totalDebit')}
                    direction={getSortDirection('totalDebit') || 'asc'}
                    onClick={(e) => handleSort('totalDebit', e)}
                  >
                    Amount {getSortPriority('totalDebit') && `(${getSortPriority('totalDebit')})`}
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={!!getSortDirection('status')}
                    direction={getSortDirection('status') || 'asc'}
                    onClick={(e) => handleSort('status', e)}
                  >
                    Status {getSortPriority('status') && `(${getSortPriority('status')})`}
                  </TableSortLabel>
                </TableCell>
                <TableCell>Entered By</TableCell>
                <TableCell>Posted By</TableCell>
                <TableCell>AR/AP Entity (Coming in Epic 4/5)</TableCell>
                <TableCell>Reversal</TableCell>
                <TableCell>Attachments</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {vouchers.map((voucher) => (
                <TableRow key={voucher.id} hover>
                  <TableCell>{voucher.voucherNumber}</TableCell>
                  <TableCell>{formatDate(voucher.voucherDate)}</TableCell>
                  <TableCell>{voucher.type || 'N/A'}</TableCell>
                  <TableCell>
                    {voucher.totalDebit && voucher.totalCredit
                      ? `${formatAmount(voucher.totalDebit, voucher.currency)} / ${formatAmount(voucher.totalCredit, voucher.currency)}`
                      : formatAmount(
                          voucher.totalDebit || voucher.totalCredit || 0,
                          voucher.currency,
                        )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={voucher.status}
                      size="small"
                      color={
                        voucher.status === 'posted'
                          ? 'success'
                          : voucher.status === 'draft'
                            ? 'warning'
                            : 'info'
                      }
                    />
                  </TableCell>
                  <TableCell>{voucher.enteredByName || 'N/A'}</TableCell>
                  <TableCell>{voucher.postedByName || '-'}</TableCell>
                  <TableCell>N/A</TableCell>
                  <TableCell>
                    {voucher.hasReversal && (
                      <Chip label="Reversal" size="small" color="secondary" />
                    )}
                  </TableCell>
                  <TableCell>{voucher.attachmentCount || 0}</TableCell>
                  <TableCell align="right">
                    <Box display="flex" gap={0.5} justifyContent="flex-end">
                      {voucher.status === 'draft' && (
                        <>
                          <Tooltip title="Edit">
                            <IconButton
                              size="small"
                              color="primary"
                              onClick={() => {
                                setVoucherToEdit(voucher)
                                setEditDialogOpen(true)
                              }}
                            >
                              <EditIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Delete">
                            <IconButton
                              size="small"
                              color="error"
                              onClick={() => handleDeleteClick(voucher)}
                            >
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </>
                      )}
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <TablePagination
            component="div"
            count={total}
            page={page}
            onPageChange={(_, newPage) => setPage(newPage)}
            rowsPerPage={size}
            onRowsPerPageChange={(e) => {
              setSize(parseInt(e.target.value, 10))
              setPage(0)
            }}
            rowsPerPageOptions={PAGE_SIZE_OPTIONS}
          />
        </TableContainer>
      )}

      {/* Delete Dialog */}
      <DeleteVoucherDialog
        open={deleteDialogOpen}
        voucher={voucherToDelete}
        onClose={() => {
          setDeleteDialogOpen(false)
          setVoucherToDelete(null)
        }}
        onConfirm={handleDeleteConfirm}
      />

      {/* Error Details Modal */}
      <Dialog
        open={errorModalOpen}
        onClose={() => setErrorModalOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Error Details</DialogTitle>
        <DialogContent>
          <Typography
            variant="body2"
            component="pre"
            sx={{ whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}
          >
            {JSON.stringify(errorDetails, null, 2)}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setErrorModalOpen(false)}>Close</Button>
          <Button
            onClick={() => {
              navigator.clipboard.writeText(JSON.stringify(errorDetails, null, 2))
            }}
          >
            Copy
          </Button>
        </DialogActions>
      </Dialog>

      {/* Create Voucher Full-Screen Dialog */}
      <Dialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
        fullScreen
        maxWidth={false}
        PaperProps={{
          sx: {
            m: 0,
            height: '100vh',
            maxHeight: '100vh',
          },
        }}
      >
        <DialogTitle
          sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', pb: 1 }}
        >
          Create Voucher
          <IconButton
            edge="end"
            color="inherit"
            onClick={() => setCreateDialogOpen(false)}
            aria-label="close"
          >
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent sx={{ p: 0, height: 'calc(100vh - 64px)', overflow: 'auto' }}>
          <Box sx={{ p: 3 }}>
            <VoucherForm
              onSave={() => {
                setCreateDialogOpen(false)
                loadVouchers()
                loadCounts()
              }}
              onCancel={() => setCreateDialogOpen(false)}
            />
          </Box>
        </DialogContent>
      </Dialog>

      {/* Edit Voucher Full-Screen Dialog */}
      <Dialog
        open={editDialogOpen}
        onClose={() => {
          setEditDialogOpen(false)
          setVoucherToEdit(null)
        }}
        fullScreen
        maxWidth={false}
        PaperProps={{
          sx: {
            m: 0,
            height: '100vh',
            maxHeight: '100vh',
          },
        }}
      >
        <DialogTitle
          sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', pb: 1 }}
        >
          Edit Voucher
          <IconButton
            edge="end"
            color="inherit"
            onClick={() => {
              setEditDialogOpen(false)
              setVoucherToEdit(null)
            }}
            aria-label="close"
          >
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent sx={{ p: 0, height: 'calc(100vh - 64px)', overflow: 'auto' }}>
          <Box sx={{ p: 3 }}>
            <VoucherForm
              voucherId={voucherToEdit?.id}
              onSave={() => {
                setEditDialogOpen(false)
                setVoucherToEdit(null)
                loadVouchers()
                loadCounts()
              }}
              onCancel={() => {
                setEditDialogOpen(false)
                setVoucherToEdit(null)
              }}
            />
          </Box>
        </DialogContent>
      </Dialog>
    </Box>
  )
}
