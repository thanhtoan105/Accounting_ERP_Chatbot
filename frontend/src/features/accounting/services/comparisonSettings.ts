/**
 * Comparison Settings API Service
 *
 * API calls for managing multi-period comparison settings.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchWithAuth } from '@/utils/axios'
import type { ComparisonSettingsDTO } from '../types/multiPeriodReport'

const API_BASE = '/api/settings/comparison'
const SETTINGS_KEY = ['comparison-settings']

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetchWithAuth(url, options)
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw new Error(error.message || 'Request failed')
  }
  return res.json()
}

export const comparisonSettingsApi = {
  getSettings: () => fetchJson<ComparisonSettingsDTO>(API_BASE),

  updateSettings: (settings: Partial<ComparisonSettingsDTO>) =>
    fetchJson<ComparisonSettingsDTO>(API_BASE, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(settings),
    }),
}

export function useComparisonSettings() {
  return useQuery({
    queryKey: SETTINGS_KEY,
    queryFn: () => comparisonSettingsApi.getSettings(),
  })
}

export function useUpdateComparisonSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (settings: Partial<ComparisonSettingsDTO>) =>
      comparisonSettingsApi.updateSettings(settings),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: SETTINGS_KEY })
    },
  })
}
