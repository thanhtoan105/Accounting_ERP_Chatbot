export interface AuditActor {
  id: number | null
  email: string | null
  role: string | null
}

export interface AuditLogItem {
  id: number
  occurredAt: string | null
  action: string | null
  eventType: string | null
  success: boolean | null
  failureReason: string | null
  entityType: string | null
  entityId: string | null
  entityDisplay: string | null
  actor: AuditActor | null
  changes: Record<string, unknown>
  metadata: Record<string, unknown>
  ipAddress: string | null
  userAgent: string | null
  traceId: string | null
}

export interface AuditLogMeta {
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface AuditLogResponse {
  data: AuditLogItem[]
  meta: AuditLogMeta
}

export interface AuditLogQueryParams {
  entityType?: string
  entityId?: string
  action?: string
  eventType?: string
  userEmail?: string
  actorRole?: string
  success?: boolean | null
  from?: string
  to?: string
  page?: number
  size?: number
}



