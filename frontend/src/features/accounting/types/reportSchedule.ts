export interface ReportScheduleDTO {
  id: string
  name: string
  reportType: string
  cronExpression: string
  periodRule: string
  parameters?: Record<string, unknown>
  exportFormats: string[]
  recipients: string[]
  recipientCount: number
  ownerId: number
  ownerName: string
  isActive: boolean
  lastRunAt: string | null
  nextRunAt: string | null
  createdAt: string
}

export interface CreateReportScheduleRequest {
  name: string
  reportType: string
  cronExpression: string
  periodRule: string
  periodId?: string
  exportFormats: string[]
  recipients: string[]
}

export interface UpdateReportScheduleRequest {
  name?: string
  cronExpression?: string
  periodRule?: string
  periodId?: string
  exportFormats?: string[]
  recipients?: string[]
}

export interface ScheduleRunDTO {
  id: string
  scheduleId: string
  scheduleName: string
  periodId: string
  periodLabel: string
  status:
    | 'PENDING'
    | 'RUNNING'
    | 'SUCCESS'
    | 'PARTIAL_SUCCESS'
    | 'FAILED'
    | 'CANCELLED'
    | 'SKIPPED_DUPLICATE'
  triggerType: 'SCHEDULED' | 'MANUAL' | 'RETRY'
  triggeredById: number | null
  triggeredByName: string | null
  snapshotId: string | null
  rerunOfRunId: string | null
  attempt: number
  queuedAt: string
  startedAt: string | null
  finishedAt: string | null
  durationMs: number | null
  errorCode: string | null
  errorMessage: string | null
  slaDeadline: string | null
  exceededSla: boolean
}

export interface RunNowRequest {
  overridePeriodId?: string
  overrideFormats?: string[]
}

export interface RunNowResponse {
  runId: string
  scheduleId: string
  status: string
  periodId: string
  periodLabel: string
  queuedAt: string
}
