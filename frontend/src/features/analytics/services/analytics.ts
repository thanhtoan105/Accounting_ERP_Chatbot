import axios from '@/utils/axios'
import { getAccessToken, getCompanyId } from '@/utils/axios'
import type {
  MetabaseTokenResponse,
  MetabaseUrlResponse,
  MetabaseEmbedConfig,
  MetabaseSsoToken,
  DashboardInfo,
  WidgetPermissions,
  WidgetPermissionsByType,
  FreshnessStatus,
  RefreshJobResponse,
  RefreshJobStatus,
  AnalyticsEvent,
  WidgetAccessResponse,
  DashboardConfigResponse,
} from '../types'

const API_BASE = '/analytics'

export const getEmbeddingToken = async (dashboardId: number): Promise<MetabaseTokenResponse> => {
  const response = await axios.get<MetabaseTokenResponse>(
    `${API_BASE}/metabase/token/${dashboardId}`,
  )
  return response.data
}

export const getEmbeddedDashboardUrl = async (
  dashboardId: number,
): Promise<MetabaseUrlResponse> => {
  const response = await axios.get<MetabaseUrlResponse>(`${API_BASE}/metabase/url/${dashboardId}`)
  return response.data
}

export const getEmbedConfig = async (dashboardKey: string): Promise<MetabaseEmbedConfig> => {
  const response = await axios.get<MetabaseEmbedConfig>(
    `${API_BASE}/metabase/embed/dashboard/${dashboardKey}`,
  )
  return response.data
}

export const getSsoToken = async (): Promise<MetabaseSsoToken> => {
  const response = await axios.get<MetabaseSsoToken>(`${API_BASE}/metabase/sso/token`)
  return response.data
}

export const listDashboards = async (): Promise<DashboardInfo[]> => {
  const response = await axios.get<DashboardInfo[]>(`${API_BASE}/metabase/dashboards`)
  return response.data
}

export const getWidgetPermissions = async (): Promise<WidgetPermissions> => {
  const response = await axios.get<WidgetPermissions>(`${API_BASE}/metabase/widgets`)
  return response.data
}

export const getWidgetPermissionsByType = async (): Promise<WidgetPermissionsByType> => {
  const response = await axios.get<WidgetPermissionsByType>(`${API_BASE}/widgets/permissions`)
  return response.data
}

export const checkWidgetAccess = async (widgetKey: string): Promise<WidgetAccessResponse> => {
  const response = await axios.get<WidgetAccessResponse>(
    `${API_BASE}/metabase/widgets/${widgetKey}/access`,
  )
  return response.data
}

export const logAnalyticsEvent = async (event: AnalyticsEvent): Promise<void> => {
  await axios.post(`${API_BASE}/metabase/events`, event)
}

export const getDashboardFreshness = async (): Promise<FreshnessStatus> => {
  const response = await axios.get<FreshnessStatus>(`/dashboard/freshness`)
  return response.data
}

export const triggerManualRefresh = async (): Promise<RefreshJobResponse> => {
  const response = await axios.post<RefreshJobResponse>(`/dashboard/refresh`)
  return response.data
}

export const getRefreshJobStatus = async (jobId: string): Promise<RefreshJobStatus> => {
  const response = await axios.get<RefreshJobStatus>(`/dashboard/etl/status/${jobId}`)
  return response.data
}

export const createSsoTokenFetcher = () => {
  return async (): Promise<string> => {
    const response = await getSsoToken()
    return response.jwt
  }
}

export const createAuthConfig = (authProviderUri: string) => {
  return {
    authType: 'jwt' as const,
    authProviderUri,
    getRequestOptions: () => {
      const token = getAccessToken()
      const companyId = getCompanyId()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      }
      if (token) {
        headers['Authorization'] = `Bearer ${token}`
      }
      if (companyId !== null && companyId !== undefined) {
        headers['X-Company-Id'] = String(companyId)
      }
      return { headers }
    },
  }
}

export const getDashboardForRole = async (): Promise<DashboardConfigResponse> => {
  const response = await axios.get<DashboardConfigResponse>(
    `${API_BASE}/metabase/dashboard-for-role`,
  )
  return response.data
}

export interface PeriodFinancialSummary {
  periodId: number
  totalRevenue: number
  totalExpense: number
  netIncome: number
  arBalance: number
  apBalance: number
  cashBalance: number
  voucherCount: number
  periodStart: string
  periodEnd: string
}

export const getPeriodFinancialSummary = async (
  periodId: string,
): Promise<PeriodFinancialSummary | null> => {
  try {
    const response = await axios.get<PeriodFinancialSummary>(
      `/dashboard/period-summary/${periodId}`,
    )
    return response.data
  } catch {
    return null
  }
}
