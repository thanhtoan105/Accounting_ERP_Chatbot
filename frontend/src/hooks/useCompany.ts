import { useState, useEffect } from 'react'
import { getCompanySettings, type CompanySettings } from '@/services/company'
import { useRole } from './useRole'

export function useCompany() {
  const { hasAnyRole } = useRole()
  const [company, setCompany] = useState<CompanySettings | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    // Only fetch if user has permission to view company settings
    if (hasAnyRole(['admin', 'chief_accountant'])) {
      getCompanySettings()
        .then((data) => {
          setCompany(data)
          setError(null)
        })
        .catch((err) => {
          setError(err instanceof Error ? err : new Error('Failed to load company settings'))
          setCompany(null)
        })
        .finally(() => {
          setLoading(false)
        })
    } else {
      // User doesn't have permission, skip loading
      setLoading(false)
    }
  }, [hasAnyRole])

  // Calculate current period from fiscal year start
  const getCurrentPeriod = (): string => {
    if (!company?.fiscalYearStart) {
      const now = new Date()
      return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    }

    try {
      const fiscalStart = new Date(company.fiscalYearStart)
      const now = new Date()

      // Calculate which fiscal year we're in
      let fiscalYear = now.getFullYear()
      if (now < new Date(fiscalYear, fiscalStart.getMonth(), fiscalStart.getDate())) {
        fiscalYear -= 1
      }

      // Calculate month in fiscal year (1-12)
      const monthDiff = (now.getMonth() - fiscalStart.getMonth() + 12) % 12
      const fiscalMonth = monthDiff + 1

      return `${fiscalYear}-${String(fiscalMonth).padStart(2, '0')}`
    } catch {
      // Fallback to current year-month
      const now = new Date()
      return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    }
  }

  return {
    company,
    loading,
    error,
    currentPeriod: company ? getCurrentPeriod() : null,
  }
}
