import axiosInstance from '@/utils/axios'

export interface ARAgingBucket {
    current: number
    days1To30: number
    days31To60: number
    days61To90: number
    daysOver90: number
    total: number
}

export interface ARAgingReport {
    customerId: number
    customerName: string
    customerCode: string
    buckets: ARAgingBucket
    totalOutstanding: number
    hasOverdue: boolean
    invoiceCount?: number
}

export interface ARDashboardMetrics {
    totalOverdue: number
    overdueCount: number
    topOverdueCustomers: Array<{
        customerId: number
        customerName: string
        overdueAmount: number
    }>
}

export interface ARReminderConfig {
    id?: string
    companyId?: number
    preDueDays: number
    dueDateEnabled: boolean
    postDueCadenceDays: number
}

export const arAgingApi = {
    async getAgingReport(params?: {
        customerId?: number
        asOfDate?: string
        status?: string
        bucket?: string
        page?: number
        size?: number
        sortBy?: string
        sortDir?: string
    }) {
        // Filter out undefined values to avoid sending them in query string
        const cleanParams: Record<string, any> = {}
        if (params) {
            Object.entries(params).forEach(([key, value]) => {
                if (value !== undefined && value !== null && value !== '') {
                    cleanParams[key] = value
                }
            })
        }
        const res = await axiosInstance.get('/ar-aging', { params: cleanParams })
        return res.data
    },

    async refreshCache(asOfDate?: string) {
        const res = await axiosInstance.post('/ar-aging/refresh', null, {
            params: { asOfDate },
        })
        return res.data
    },

    async getDrillDownDetail(
        customerId: number,
        agingBucketKey: string,
        params?: {
            asOfDate?: string
            page?: number
            size?: number
        }
    ) {
        const res = await axiosInstance.get(`/ar-aging/${customerId}/detail`, {
            params: { agingBucketKey, ...params },
        })
        return res.data
    },

    async getInvoiceDetail(invoiceId: string) {
        const res = await axiosInstance.get(`/ar-aging/invoice/${invoiceId}`)
        return res.data
    },

    async exportReport(format: 'EXCEL' | 'PDF', params?: {
        customerId?: number
        asOfDate?: string
    }) {
        const res = await axiosInstance.get('/ar-aging/export', {
            params: { format, ...params },
            responseType: 'blob',
        })
        return res.data as Blob
    },

    async getDashboardMetrics(): Promise<ARDashboardMetrics> {
        const res = await axiosInstance.get('/ar-aging/dashboard-metrics')
        return res.data
    },

    async getReminderConfig(): Promise<ARReminderConfig> {
        const res = await axiosInstance.get('/ar-aging/reminder-config')
        return res.data
    },

    async updateReminderConfig(config: ARReminderConfig): Promise<ARReminderConfig> {
        const res = await axiosInstance.put('/ar-aging/reminder-config', config)
        return res.data
    },

    async triggerReminders(params?: {
        customerIds?: number[]
        invoiceIds?: string[]
    }) {
        const res = await axiosInstance.post('/ar-aging/trigger-reminders', null, { params })
        return res.data
    },
}
