export interface MetabaseTokenResponse {
  token: string
  dashboardId: string
}

export interface MetabaseUrlResponse {
  url: string
  dashboardId: string
}

export interface MetabaseEmbedConfig {
  metabaseInstanceUrl: string
  authEnabled: boolean
  authProviderUri: string
  authType: string
  tokenExpiryMinutes: number
  refreshBeforeExpiryMinutes: number
}

export interface MetabaseSsoToken {
  jwt: string
  expiresInMinutes: number
}

export interface DashboardInfo {
  key: string
  name: string
  description: string
  allowedRoles: string[]
  metabaseDashboardId: number
}

export interface WidgetPermissions {
  scope: WidgetScope
  allowedWidgets: string[]
  canRefresh: boolean
  canViewETLStatus: boolean
}

export type WidgetScope = 'ALL' | 'SUMMARY' | 'AR_ONLY' | 'AP_ONLY' | 'CASH_ONLY'

export type WidgetType =
  | 'REVENUE_EXPENSE'
  | 'AR_BALANCES'
  | 'AP_BALANCES'
  | 'CASH_POSITION'
  | 'TOP_DEBTORS'
  | 'TOP_CREDITORS'
  | 'PERIOD_SUMMARY'

export interface WidgetPermissionsByType {
  accessibleWidgets: WidgetType[]
  canRefresh: boolean
  canExport: boolean
  isFullAccess: boolean
}

export interface FreshnessStatus {
  level: FreshnessLevel
  lastRefresh: string | null
  message: string
  consecutiveFailures: number
  periodLocked?: boolean
  currentPeriodId?: string
  dataAsOfPeriodId?: string
  dataAsOfPeriodLocked?: boolean
  canManualRefresh?: boolean
  // Computed fields for backwards compatibility with frontend components
  lastRefreshTime?: string
  freshnessStatus?: FreshnessLevel
  nextScheduledRefresh?: string
}

export type FreshnessLevel = 'GREEN' | 'YELLOW' | 'RED'

export interface RefreshJobResponse {
  jobId: string
}

export interface RefreshJobStatus {
  jobId: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  startTime?: string
  endTime?: string
  error?: string
}

export interface AnalyticsEvent {
  eventType: 'VIEW_LOADED' | 'VIEW_ERROR' | 'EXPORT_TRIGGERED' | 'WIDGET_ACCESSED'
  resourceType: 'DASHBOARD' | 'WIDGET' | 'ETL_JOB' | 'JWT' | 'EXPORT'
  resourceId?: string
}

export interface WidgetAccessResponse {
  allowed: boolean
  widgetKey: string
  message?: string
}

export type DrillDownAction =
  | { type: 'VOUCHER_BY_DATE'; date: string; periodId?: string }
  | { type: 'CUSTOMER_DETAIL'; customerId: string }
  | { type: 'SUPPLIER_DETAIL'; supplierId: string }
  | { type: 'CUSTOMER_TRANSACTIONS'; customerId: string }
  | { type: 'SUPPLIER_TRANSACTIONS'; supplierId: string }
  | { type: 'VOUCHERS_BY_ACCOUNT'; accountCode: string; date?: string }
  | { type: 'AR_AGING_BUCKET'; bucket: string; customerId?: string }
  | { type: 'AP_AGING_BUCKET'; bucket: string; supplierId?: string }

export interface MetabaseClickEvent {
  type: 'metabase:drill-through' | 'metabase:click'
  data: {
    column?: string
    value?: unknown
    dimensions?: Record<string, unknown>
    cardId?: number
    cardName?: string
  }
}

export interface MetabasePostMessageEvent {
  origin: string
  data: MetabaseClickEvent | unknown
}

export interface DashboardConfigResponse {
  dashboardId: number
  accessibleWidgets: WidgetType[]
  isFullAccess: boolean
  dashboardKey: string
}
