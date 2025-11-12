import type { CompanySettingsDto, UpdateCompanySettingsRequest } from '@/types/companySettings'
import { default as axiosInstance } from '@/utils/axios'

/**
 * Get current company advanced settings.
 * Returns settings for the company in context.
 */
export async function getAdvancedCompanySettings(): Promise<CompanySettingsDto> {
  const res = await axiosInstance.get<{ data: CompanySettingsDto }>('/company-settings')
  return res.data.data
}

/**
 * Update current company advanced settings.
 * Supports partial updates and optimistic locking via updatedAt.
 * Returns 409 Conflict if updatedAt doesn't match (stale update).
 */
export async function updateAdvancedCompanySettings(
  request: UpdateCompanySettingsRequest,
): Promise<CompanySettingsDto> {
  const res = await axiosInstance.put<{ data: CompanySettingsDto }>(
    '/company-settings',
    request,
  )
  return res.data.data
}

