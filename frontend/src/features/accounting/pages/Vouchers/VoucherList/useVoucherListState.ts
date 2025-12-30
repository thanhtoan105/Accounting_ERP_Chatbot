import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { SortingState } from '@tanstack/react-table'
import { toast } from 'sonner'

import type { VoucherListDTO, VoucherQueryParams, VoucherCountDTO } from '@/types/voucher'
import type { AccountingPeriod } from '@/types/accountingPeriod'
import { getVouchers, getVoucherCounts, deleteVoucher } from '@/services/voucher'

/**
 * Custom hook for VoucherList state management
 *
 * Handles:
 * - Data fetching with pagination
 * - Filter state with localStorage persistence
 * - Sorting with server-side support
 * - Status counts
 * - Delete operations
 */

// localStorage key prefix for voucher list filters
const getStorageKey = (key: string): string => {
  const companyId = localStorage.getItem('activeCompanyId') || 'default'
  return `voucherList_${companyId}_${key}`
}

// Helper to load from localStorage
function loadFromStorage<T>(key: string, defaultValue: T): T {
  try {
    const stored = localStorage.getItem(getStorageKey(key))
    if (stored) {
      return JSON.parse(stored) as T
    }
  } catch {
    // Ignore parse errors
  }
  return defaultValue
}

// Helper to save to localStorage
function saveToStorage<T>(key: string, value: T): void {
  try {
    localStorage.setItem(getStorageKey(key), JSON.stringify(value))
  } catch {
    // Ignore storage errors
  }
}

export type VoucherListStatus = 'all' | 'draft' | 'posted' | 'unposted'

export interface UseVoucherListStateOptions {
  initialPageSize?: number
}

export interface VoucherListState {
  // Data
  vouchers: VoucherListDTO[]
  counts: VoucherCountDTO
  totalElements: number
  totalPages: number

  // Loading states
  loading: boolean
  error: string | null

  // Filters
  status: VoucherListStatus
  dateFrom: string
  dateTo: string
  accountId: number | undefined
  search: string
  selectedPeriod: AccountingPeriod | null

  // Pagination
  page: number
  pageSize: number

  // Sorting
  sorting: SortingState

  // Actions
  setStatus: (status: VoucherListStatus) => void
  setDateFrom: (date: string) => void
  setDateTo: (date: string) => void
  setAccountId: (id: number | undefined) => void
  setSearch: (search: string) => void
  setSelectedPeriod: (period: AccountingPeriod | null) => void
  setPage: (page: number) => void
  setPageSize: (size: number) => void
  setSorting: (sorting: SortingState) => void
  resetFilters: () => void
  refresh: () => Promise<void>
  deleteVoucher: (voucher: VoucherListDTO, reason: string) => Promise<boolean>
}

export function useVoucherListState(options: UseVoucherListStateOptions = {}): VoucherListState {
  const { initialPageSize = 20 } = options

  // Data state
  const [vouchers, setVouchers] = useState<VoucherListDTO[]>([])
  const [counts, setCounts] = useState<VoucherCountDTO>({ draft: 0, posted: 0, unposted: 0 })
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Filter state with localStorage persistence
  const [status, setStatusInternal] = useState<VoucherListStatus>(() =>
    loadFromStorage('status', 'all'),
  )
  const [dateFrom, setDateFromInternal] = useState<string>(() => loadFromStorage('dateFrom', ''))
  const [dateTo, setDateToInternal] = useState<string>(() => loadFromStorage('dateTo', ''))
  const [accountId, setAccountIdInternal] = useState<number | undefined>(() =>
    loadFromStorage('accountId', undefined),
  )
  const [search, setSearchInternal] = useState<string>(() => loadFromStorage('search', ''))
  // Debounced search value used for API calls
  const [debouncedSearch, setDebouncedSearch] = useState<string>(() =>
    loadFromStorage('search', ''),
  )
  const searchChangedByUser = useRef(false)
  const [selectedPeriod, setSelectedPeriodInternal] = useState<AccountingPeriod | null>(() =>
    loadFromStorage('selectedPeriod', null),
  )
  const [sorting, setSortingInternal] = useState<SortingState>(() =>
    loadFromStorage('sorting', [] as SortingState),
  )
  const [page, setPageInternal] = useState(() => loadFromStorage('page', 0))
  const [pageSize, setPageSizeInternal] = useState(() =>
    loadFromStorage('pageSize', initialPageSize),
  )

  // Wrapped setters that persist to localStorage
  const setStatus = useCallback((value: VoucherListStatus) => {
    setStatusInternal(value)
    saveToStorage('status', value)
    setPageInternal(0) // Reset to first page on filter change
    saveToStorage('page', 0)
  }, [])

  const setDateFrom = useCallback((value: string) => {
    setDateFromInternal(value)
    saveToStorage('dateFrom', value)
    setPageInternal(0)
    saveToStorage('page', 0)
  }, [])

  const setDateTo = useCallback((value: string) => {
    setDateToInternal(value)
    saveToStorage('dateTo', value)
    setPageInternal(0)
    saveToStorage('page', 0)
  }, [])

  const setAccountId = useCallback((value: number | undefined) => {
    setAccountIdInternal(value)
    saveToStorage('accountId', value)
    setPageInternal(0)
    saveToStorage('page', 0)
  }, [])

  const setSearch = useCallback((value: string) => {
    searchChangedByUser.current = true
    setSearchInternal(value)
    saveToStorage('search', value)
    // Don't reset page here - will be done when debounced value updates
  }, [])

  const setSelectedPeriod = useCallback((value: AccountingPeriod | null) => {
    setSelectedPeriodInternal(value)
    saveToStorage('selectedPeriod', value)
    setPageInternal(0)
    saveToStorage('page', 0)
  }, [])

  const setPage = useCallback((value: number) => {
    setPageInternal(value)
    saveToStorage('page', value)
  }, [])

  const setPageSize = useCallback((value: number) => {
    setPageSizeInternal(value)
    saveToStorage('pageSize', value)
    setPageInternal(0)
    saveToStorage('page', 0)
  }, [])

  const setSorting = useCallback((value: SortingState) => {
    setSortingInternal(value)
    saveToStorage('sorting', value)
  }, [])

  const resetFilters = useCallback(() => {
    setStatusInternal('all')
    setDateFromInternal('')
    setDateToInternal('')
    setAccountIdInternal(undefined)
    searchChangedByUser.current = false
    setSearchInternal('')
    setDebouncedSearch('')
    setSortingInternal([])
    setPageInternal(0)
    saveToStorage('status', 'all')
    saveToStorage('dateFrom', '')
    saveToStorage('dateTo', '')
    saveToStorage('accountId', undefined)
    saveToStorage('search', '')
    saveToStorage('sorting', [])
    saveToStorage('page', 0)
  }, [])

  // Convert sorting state to API sort parameters - use string to avoid array reference changes
  const sortParamsString = useMemo(() => {
    return sorting
      .map((sort) => {
        const direction = sort.desc ? 'desc' : 'asc'
        const fieldMap: Record<string, string> = {
          voucherDate: 'voucherDate',
          status: 'status',
          voucherNumber: 'voucherNumber',
          totalDebit: 'totalDebit',
          totalCredit: 'totalCredit',
        }
        const field = fieldMap[sort.id] || sort.id
        return `${field},${direction}`
      })
      .join('|')
  }, [sorting])

  // Load vouchers
  const loadVouchers = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const sortParams = sortParamsString ? sortParamsString.split('|') : undefined
      const params: VoucherQueryParams = {
        page,
        size: pageSize,
        status: status !== 'all' ? (status as 'draft' | 'posted' | 'unposted') : undefined,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        accountId,
        search: debouncedSearch.trim() || undefined,
        sort: sortParams,
      }

      const response = await getVouchers(params)
      setVouchers(response.data.content)
      setTotalElements(response.data.totalElements)
      setTotalPages(response.data.totalPages)
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Unable to load vouchers'
      setError(message)
      toast.error('Failed to load vouchers', {
        description: message,
        action: {
          label: 'Retry',
          onClick: () => loadVouchers(),
        },
      })
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, status, dateFrom, dateTo, accountId, debouncedSearch, sortParamsString])

  // Load counts
  const loadCounts = useCallback(async () => {
    try {
      const countsData = await getVoucherCounts()
      setCounts(countsData)
    } catch (err) {
      // Silently fail for counts - not critical
      console.warn('Failed to load voucher counts:', err)
    }
  }, [])

  // Refresh both
  const refresh = useCallback(async () => {
    await Promise.all([loadVouchers(), loadCounts()])
    toast.success('Vouchers refreshed')
  }, [loadVouchers, loadCounts])

  // Delete voucher
  const handleDelete = useCallback(
    async (voucher: VoucherListDTO, reason: string): Promise<boolean> => {
      try {
        await deleteVoucher(voucher.id, reason)
        toast.success('Voucher deleted successfully')
        await Promise.all([loadVouchers(), loadCounts()])
        return true
      } catch (err: any) {
        const message = err?.response?.data?.message || err?.message || 'Failed to delete voucher'
        toast.error('Failed to delete voucher', { description: message })
        return false
      }
    },
    [loadVouchers, loadCounts],
  )

  // Debounce search - wait 500ms after user stops typing
  useEffect(() => {
    // Skip if this is not a user-initiated change
    if (!searchChangedByUser.current) {
      return
    }
    const timer = setTimeout(() => {
      setDebouncedSearch(search)
      setPageInternal(0)
      saveToStorage('page', 0)
    }, 500)
    return () => clearTimeout(timer)
  }, [search])

  // Initial load and refresh on filter changes
  useEffect(() => {
    loadVouchers()
  }, [loadVouchers])

  // Load counts on mount and periodically
  useEffect(() => {
    loadCounts()
    const interval = setInterval(loadCounts, 30000) // Poll every 30 seconds
    return () => clearInterval(interval)
  }, [loadCounts])

  return {
    // Data
    vouchers,
    counts,
    totalElements,
    totalPages,

    // Loading states
    loading,
    error,

    // Filters
    status,
    dateFrom,
    dateTo,
    accountId,
    search,
    selectedPeriod,

    // Pagination
    page,
    pageSize,

    // Sorting
    sorting,

    // Actions
    setStatus,
    setDateFrom,
    setDateTo,
    setAccountId,
    setSearch,
    setSelectedPeriod,
    setPage,
    setPageSize,
    setSorting,
    resetFilters,
    refresh,
    deleteVoucher: handleDelete,
  }
}

export default useVoucherListState
