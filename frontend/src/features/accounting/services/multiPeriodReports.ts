/**
 * Multi-Period Reports API Service
 *
 * API calls for Story 7.4: Multi-Period Comparison & Variance Analysis
 * for Vietnamese statutory reports (B01, B02, B03).
 */

import { useQuery } from '@tanstack/react-query'
import { fetchWithAuth } from '@/utils/axios'
import type {
  MultiPeriodReportDTO,
  PeriodSummaryDTO,
  ComparisonMode,
  MultiPeriodReportType,
} from '../types/multiPeriodReport'

const API_BASE = '/api/reports/statutory'
const PRESETS_BASE = '/api/reports/comparison-presets'

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetchWithAuth(url, options)
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw new Error(error.message || 'Request failed')
  }
  return res.json()
}

export const multiPeriodReportsApi = {
  generateReport: (reportType: MultiPeriodReportType, periodIds: string[]) =>
    fetchJson<MultiPeriodReportDTO>(
      `${API_BASE}/multi-period?reportType=${reportType}&periodIds=${periodIds.join(',')}`,
    ),

  getSuggestedPeriods: (basePeriodId: string, mode: ComparisonMode, maxPeriods = 4) =>
    fetchJson<string[]>(
      `${PRESETS_BASE}/suggested?basePeriodId=${basePeriodId}&mode=${mode}&maxPeriods=${maxPeriods}`,
    ),

  getAvailablePeriods: () => fetchJson<PeriodSummaryDTO[]>(`${PRESETS_BASE}/available-periods`),

  exportReport: async (
    reportType: MultiPeriodReportType,
    periodIds: string[],
    format: 'excel' | 'pdf',
  ): Promise<void> => {
    const params = new URLSearchParams({
      reportType,
      periodIds: periodIds.join(','),
      format,
    })
    const res = await fetchWithAuth(`${API_BASE}/multi-period/export?${params.toString()}`, {
      method: 'GET',
    })
    if (!res.ok) {
      const error = await res.json().catch(() => ({ message: 'Failed to export report' }))
      throw new Error(error.message || 'Failed to export report')
    }

    const blob = await res.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `multi-period-${reportType}-${periodIds.join('-')}.${format === 'excel' ? 'xlsx' : 'pdf'}`
    document.body.appendChild(a)
    a.click()
    window.URL.revokeObjectURL(url)
    document.body.removeChild(a)
  },
}

const MULTI_PERIOD_KEY = ['multi-period-report']
const SUGGESTED_PERIODS_KEY = ['suggested-periods']
const AVAILABLE_PERIODS_KEY = ['available-periods']

export function useMultiPeriodReport(reportType: MultiPeriodReportType, periodIds: string[]) {
  return useQuery({
    queryKey: [...MULTI_PERIOD_KEY, reportType, periodIds],
    queryFn: () => multiPeriodReportsApi.generateReport(reportType, periodIds),
    enabled: periodIds.length > 0,
    staleTime: 5 * 60 * 1000,
  })
}

export function useSuggestedPeriods(basePeriodId: string, mode: ComparisonMode, maxPeriods = 4) {
  return useQuery({
    queryKey: [...SUGGESTED_PERIODS_KEY, basePeriodId, mode, maxPeriods],
    queryFn: () => multiPeriodReportsApi.getSuggestedPeriods(basePeriodId, mode, maxPeriods),
    enabled: !!basePeriodId && mode !== 'CUSTOM',
  })
}

export function useAvailablePeriods() {
  return useQuery({
    queryKey: AVAILABLE_PERIODS_KEY,
    queryFn: () => multiPeriodReportsApi.getAvailablePeriods(),
  })
}
