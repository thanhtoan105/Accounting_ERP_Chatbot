export interface APAuditTimelineDTO {
  id: number;
  action: string;
  eventType: string;
  timestamp: string; // ISO date string
  userId: number;
  userName: string;
  userRole: string;
  entityId: string;
  entityDisplay: string;
  summary: string;
  status: 'SUCCESS' | 'FAILURE';
  failureReason?: string;
}

export interface APAuditEventDTO {
  id: number;
  action: string;
  eventType: string;
  timestamp: string;
  userId: number;
  userName: string;
  userEmail: string;
  userRole: string;
  ipAddress: string;
  userAgent: string;
  entityType: string;
  entityId: string;
  entityDisplay: string;
  beforeSnapshot?: any;
  afterSnapshot?: any;
  changes?: any;
  metadata?: any;
  diffHash?: string;
  chainHash?: string;
  success: boolean;
  failureReason?: string;
}

export interface AbuseDetectionResultDTO {
  userId: number;
  userName: string;
  userEmail: string;
  patternType: string;
  severity: string;
  eventCount: number;
  firstEventTime: string;
  lastEventTime: string;
  description: string;
  relatedEventIds: string[];
}

export interface APAuditBackup {
  id: number;
  companyId: number;
  backupDate: string;
  archivePath: string;
  hash: string;
  recordCount: number;
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  createdBy: number;
}

export interface AuditFilters {
  userId?: number;
  action?: string;
  outcome?: 'SUCCESS' | 'FAILURE';
  startDate?: string; // YYYY-MM-DD
  endDate?: string; // YYYY-MM-DD
}
