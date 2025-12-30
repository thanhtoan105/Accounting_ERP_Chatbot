import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { SortingState } from '@tanstack/react-table'
import { toast } from 'sonner'

import type {
  PurchaseBillListDTO,
  PurchaseBillQueryParams,
  PurchaseBillStatus,
} from '@/types/purchaseBill'
import type { AccountingPeriod } from '@/types/accountingPeriod'
import { getPurchaseBills, deletePurchaseBill } from '@/services/purchaseBill'

const getStorageKey = (key: string): string => {
  const companyId = localStorage.getItem('activeCompanyId') || 'default'
  return `purchaseBillList_${companyId}_${key}`
}

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

function saveToStorage<T>(key: string, value: T): void {
  try {
    localStorage.setItem(getStorageKey(key), JSON.stringify(value))
  } catch {
    // Ignore storage errors
  }
}

export type PurchaseBillListStatus = 'all' | PurchaseBillStatus

export interface PurchaseBillCountDTO {
  draft: number
  posted: number
  pending: number
  paid: number
}

export interface UsePurchaseBillListStateOptions {
  initialPageSize?: number
}

export interface PurchaseBillListState {
  bills: PurchaseBillListDTO[]
  counts: PurchaseBillCountDTO
  totalElements: number
  totalPages: number

  loading: boolean
  error: string | null

  status: PurchaseBillListStatus
  dateFrom: string
  dateTo: string
  supplierId: number | undefined
  search: string
  selectedPeriod: AccountingPeriod | null

  page: number
  pageSize: number
  sorting: SortingState

  setStatus: (status: PurchaseBillListStatus) => void
  setDateFrom: (date: string) => void
  setDateTo: (date: string) => void
  setSupplierId: (id: number | undefined) => void
  setSearch: (search: string) => void
  setSelectedPeriod: (period: AccountingPeriod | null) => void
  setPage: (page: number) => void
  setPageSize: (size: number) => void
  setSorting: (sorting: SortingState) => void
  resetFilters: () => void
  refresh: () => Promise<void>
  deleteBill: (bill: PurchaseBillListDTO, reason: string) => Promise<boolean>
}

export function usePurchaseBillListState(
  options: UsePurchaseBillListStateOptions = {},
): PurchaseBillListState {
  const { initialPageSize = 20 } = options

  const [bills, setBills] = useState<PurchaseBillListDTO[]>([])
  const [counts] = useState<PurchaseBillCountDTO>({
    draft: 0,
    posted: 0,
    pending: 0,
    paid: 0,
  })
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [status, setStatusInternal] = useState<PurchaseBillListStatus>(() =>
    loadFromStorage('status', 'all'),
  )
  const [dateFrom, setDateFromInternal] = useState<string>(() => loadFromStorage('dateFrom', ''))
  const [dateTo, setDateToInternal] = useState<string>(() => loadFromStorage('dateTo', ''))
  const [supplierId, setSupplierIdInternal] = useState<number | undefined>(() =>
    loadFromStorage('supplierId', undefined),
  )
  const [search, setSearchInternal] = useState<string>(() => loadFromStorage('search', ''))
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

  const setStatus = useCallback((value: PurchaseBillListStatus) => {
    setStatusInternal(value)
    saveToStorage('status', value)
    setPageInternal(0)
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

  const setSupplierId = useCallback((value: number | undefined) => {
    setSupplierIdInternal(value)
    saveToStorage('supplierId', value)
    setPageInternal(0)
    saveToStorage('page', 0)
  }, [])

  const setSearch = useCallback((value: string) => {
    searchChangedByUser.current = true
    setSearchInternal(value)
    saveToStorage('search', value)
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
    setSupplierIdInternal(undefined)
    searchChangedByUser.current = false
    setSearchInternal('')
    setDebouncedSearch('')
    setSortingInternal([])
    setPageInternal(0)
    setSelectedPeriodInternal(null)
    saveToStorage('status', 'all')
    saveToStorage('dateFrom', '')
    saveToStorage('dateTo', '')
    saveToStorage('supplierId', undefined)
    saveToStorage('search', '')
    saveToStorage('sorting', [])
    saveToStorage('page', 0)
    saveToStorage('selectedPeriod', null)
  }, [])

  const sortParams = useMemo(() => {
    return sorting.map((sort) => {
      const direction = sort.desc ? 'desc' : 'asc'
      const fieldMap: Record<string, string> = {
        billDate: 'billDate',
        status: 'status',
        billNumber: 'billNumber',
        totalAmount: 'totalAmount',
        supplierName: 'supplierName',
      }
      const field = fieldMap[sort.id] || sort.id
      return `${field},${direction}`
    })
  }, [sorting])

  const loadBills = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const params: PurchaseBillQueryParams = {
        page,
        size: pageSize,
        status: status !== 'all' ? (status as PurchaseBillStatus) : undefined,
        supplier: supplierId,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        search: debouncedSearch.trim() || undefined,
        sort: sortParams.length > 0 ? sortParams : undefined,
      }

      const response = await getPurchaseBills(params)
      setBills(response.data.content)
      setTotalElements(response.data.totalElements)
      setTotalPages(response.data.totalPages)
    } catch (err: any) {
      const message =
        err?.response?.data?.message || err?.message || 'Unable to load purchase bills'
      setError(message)
      toast.error('Failed to load purchase bills', {
        description: message,
        action: {
          label: 'Retry',
          onClick: () => loadBills(),
        },
      })
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, status, supplierId, dateFrom, dateTo, debouncedSearch, sortParams])

  const loadCounts = useCallback(async () => {
    // TODO: Implement getPurchaseBillCounts service when available
  }, [])

  const refresh = useCallback(async () => {
    await Promise.all([loadBills(), loadCounts()])
    toast.success('Purchase bills refreshed')
  }, [loadBills, loadCounts])

  const handleDelete = useCallback(
    async (bill: PurchaseBillListDTO, reason: string): Promise<boolean> => {
      try {
        await deletePurchaseBill(bill.id, reason)
        toast.success('Purchase bill deleted successfully')
        await Promise.all([loadBills(), loadCounts()])
        return true
      } catch (err: any) {
        const message =
          err?.response?.data?.message || err?.message || 'Failed to delete purchase bill'
        toast.error('Failed to delete purchase bill', { description: message })
        return false
      }
    },
    [loadBills, loadCounts],
  )

  useEffect(() => {
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

  useEffect(() => {
    loadBills()
  }, [loadBills])

  useEffect(() => {
    loadCounts()
    const interval = setInterval(loadCounts, 30000)
    return () => clearInterval(interval)
  }, [loadCounts])

  return {
    bills,
    counts,
    totalElements,
    totalPages,
    loading,
    error,
    status,
    dateFrom,
    dateTo,
    supplierId,
    search,
    selectedPeriod,
    page,
    pageSize,
    sorting,
    setStatus,
    setDateFrom,
    setDateTo,
    setSupplierId,
    setSearch,
    setSelectedPeriod,
    setPage,
    setPageSize,
    setSorting,
    resetFilters,
    refresh,
    deleteBill: handleDelete,
  }
}

export default usePurchaseBillListState
