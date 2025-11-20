import { AxiosResponse } from 'axios';
import axios from '@/lib/axios'; // Assuming configured axios instance
import { APAuditTimelineDTO, APAuditEventDTO, AbuseDetectionResultDTO, APAuditBackup, AuditFilters } from '@/types/apAudit';
import { Page } from '@/types/common'; // Assuming Page type exists

const BASE_URL = '/api/v1/ap-audit';

export const apAuditService = {
  getTimeline: (filters: AuditFilters, page: number, size: number): Promise<Page<APAuditTimelineDTO>> => {
    return axios.get(`${BASE_URL}/timeline`, {
      params: { ...filters, page, size }
    }).then(res => res.data);
  },

  getEventDetails: (eventId: number): Promise<APAuditEventDTO> => {
    return axios.get(`${BASE_URL}/events/${eventId}`).then(res => res.data);
  },

  exportTimeline: (filters: AuditFilters): Promise<Blob> => {
    return axios.get(`${BASE_URL}/timeline/export`, {
      params: filters,
      responseType: 'blob'
    }).then(res => res.data);
  },

  getAbuseDetections: (userId?: number, timeWindowMinutes = 60): Promise<AbuseDetectionResultDTO[]> => {
    return axios.get(`${BASE_URL}/abuse`, {
      params: { userId, timeWindowMinutes }
    }).then(res => res.data);
  },

  getUnauthorizedAttempts: (filters: AuditFilters, page: number, size: number): Promise<Page<APAuditTimelineDTO>> => {
    return axios.get(`${BASE_URL}/unauthorized-attempts`, {
      params: { ...filters, page, size }
    }).then(res => res.data);
  },

  listBackups: (status?: string, startDate?: string, endDate?: string, page = 0, size = 20): Promise<Page<APAuditBackup>> => {
    return axios.get(`${BASE_URL}/backups`, {
      params: { status, startDate, endDate, page, size }
    }).then(res => res.data);
  },

  createBackup: (): Promise<APAuditBackup> => {
    return axios.post(`${BASE_URL}/backups`).then(res => res.data);
  },

  downloadBackup: (backupId: number): Promise<Blob> => {
    return axios.get(`${BASE_URL}/backups/${backupId}/download`, {
      responseType: 'blob'
    }).then(res => res.data);
  },

  purgeAuditLogs: (criteria: { userId?: number; beforeDate: string }): Promise<{ purgedCount: number; status: string }> => {
    return axios.post(`${BASE_URL}/purge`, criteria).then(res => res.data);
  }
};
