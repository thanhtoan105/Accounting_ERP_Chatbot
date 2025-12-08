import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchWithAuth } from '@/utils/axios'
import type { Page } from '@/types/common'
import type {
  ReportScheduleDTO,
  CreateReportScheduleRequest,
  UpdateReportScheduleRequest,
  ScheduleRunDTO,
  RunNowRequest,
  RunNowResponse,
} from '../types/reportSchedule'

const API_BASE = '/api/reports/schedules'
const SCHEDULES_KEY = ['report-schedules']

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetchWithAuth(url, options)
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw new Error(error.message || 'Request failed')
  }
  return res.json()
}

export function useSchedules(page = 0, size = 20) {
  return useQuery({
    queryKey: [...SCHEDULES_KEY, 'list', page, size],
    queryFn: () => fetchJson<Page<ReportScheduleDTO>>(`${API_BASE}?page=${page}&size=${size}`),
  })
}

export function useSchedule(id: string) {
  return useQuery({
    queryKey: [...SCHEDULES_KEY, id],
    queryFn: () => fetchJson<ReportScheduleDTO>(`${API_BASE}/${id}`),
    enabled: !!id,
  })
}

export function useCreateSchedule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateReportScheduleRequest) =>
      fetchJson<ReportScheduleDTO>(API_BASE, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SCHEDULES_KEY })
    },
  })
}

export function useUpdateSchedule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateReportScheduleRequest }) =>
      fetchJson<ReportScheduleDTO>(`${API_BASE}/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      }),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: SCHEDULES_KEY })
      queryClient.invalidateQueries({ queryKey: [...SCHEDULES_KEY, id] })
    },
  })
}

export function useCancelSchedule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => fetchJson<void>(`${API_BASE}/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SCHEDULES_KEY })
    },
  })
}

export function useRunNow() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request?: RunNowRequest }) =>
      fetchJson<RunNowResponse>(`${API_BASE}/${id}/run-now`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request || {}),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SCHEDULES_KEY })
      queryClient.invalidateQueries({ queryKey: ['report-center'] })
    },
  })
}

export function useScheduleHistory(scheduleId: string, page = 0, size = 20) {
  return useQuery({
    queryKey: [...SCHEDULES_KEY, scheduleId, 'history', page, size],
    queryFn: () =>
      fetchJson<Page<ScheduleRunDTO>>(
        `${API_BASE}/${scheduleId}/history?page=${page}&size=${size}`,
      ),
    enabled: !!scheduleId,
  })
}

export function useUpcomingRuns() {
  return useQuery({
    queryKey: ['report-center', 'upcoming'],
    queryFn: () => fetchJson<ScheduleRunDTO[]>(`${API_BASE}/report-center/upcoming`),
  })
}

export function useAllRunHistory(page = 0, size = 20) {
  return useQuery({
    queryKey: ['report-center', 'history', page, size],
    queryFn: () =>
      fetchJson<Page<ScheduleRunDTO>>(
        `${API_BASE}/report-center/history?page=${page}&size=${size}`,
      ),
  })
}
