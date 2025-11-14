import api from '@/utils/axios'
import type {
  AuditLogResponse,
  AuditLogQueryParams,
} from '@/types/audit'

export async function getAuditLogs(params: AuditLogQueryParams): Promise<AuditLogResponse> {
  const response = await api.get('/api/v1/admin/audit-logs', {
    params: {
      ...params,
      page: Math.max(0, (params.page ?? 1) - 1),
    },
  })
  return response.data as AuditLogResponse
}

export async function exportAuditLogs(params: AuditLogQueryParams): Promise<Blob> {
  const response = await api.get('/api/v1/admin/audit-logs/export', {
    params: {
      ...params,
      page: undefined,
    },
    responseType: 'blob',
  })
  return response.data as Blob
}



