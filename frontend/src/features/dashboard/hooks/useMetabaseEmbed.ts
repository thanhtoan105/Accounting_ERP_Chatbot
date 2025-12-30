import { useQuery } from '@tanstack/react-query'
import { getMetabaseDashboardEmbedUrl } from '../api/metabase'

export const metabaseKeys = {
  all: ['metabase'] as const,
  dashboard: (dashboardId: number) => [...metabaseKeys.all, 'dashboard', dashboardId] as const,
}

export function useMetabaseDashboardEmbed(dashboardId: number) {
  return useQuery({
    queryKey: metabaseKeys.dashboard(dashboardId),
    queryFn: () => getMetabaseDashboardEmbedUrl(dashboardId),
    staleTime: 5 * 60 * 1000,
    refetchInterval: 5 * 60 * 1000,
  })
}
