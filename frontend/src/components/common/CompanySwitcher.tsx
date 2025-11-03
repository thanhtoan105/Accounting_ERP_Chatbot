import { useEffect, useState } from 'react'
import { FormControl, InputLabel, MenuItem, Select } from '@mui/material'
import type { Company } from '../../services/company'
import { listCompanies, setActiveCompany } from '../../services/company'
import { setCompanyId, getCompanyId } from '../../utils/axios'

export function CompanySwitcher() {
  const [companies, setCompanies] = useState<Company[]>([])
  const [activeId, setActiveId] = useState<number | ''>('')

  useEffect(() => {
    // Only try to list companies if user has a companyId
    // Users without company shouldn't fetch companies list
    // Use a delay to ensure localStorage is synced after login redirect
    const checkAndLoadCompanies = (retries = 3) => {
      const savedCompanyId = getCompanyId()
      if (!savedCompanyId || savedCompanyId <= 0) {
        // User has no company - don't try to fetch companies
        setCompanies([])
        return
      }

      listCompanies()
        .then((list) => {
          setCompanies(list)
          if (savedCompanyId && list.some((c) => c.id === savedCompanyId)) {
            setActiveId(savedCompanyId)
          }
        })
        .catch((err) => {
          // If error is about missing company context and we have retries left, try again
          const isMissingContext =
            err?.message?.includes('company context') ||
            err?.error?.message?.includes('company context') ||
            err?.error?.code === 'FORBIDDEN'

          if (isMissingContext && retries > 0) {
            // Retry after a longer delay - localStorage might not be synced yet
            setTimeout(() => checkAndLoadCompanies(retries - 1), 200)
          } else {
            // Silently fail - user might not have companies yet or missing X-Company-Id header
            console.warn('Failed to list companies:', err)
            setCompanies([])
          }
        })
    }

    // Delay to ensure localStorage is synced after login redirect
    // Increased delay to account for navigation timing
    const timeoutId = setTimeout(() => checkAndLoadCompanies(), 300)
    return () => clearTimeout(timeoutId)
  }, [])

  async function handleChange(id: number) {
    setActiveId(id)
    setCompanyId(id)
    await setActiveCompany(id)
  }

  return (
    <FormControl size="small" fullWidth>
      <InputLabel id="company-switcher-label">Company</InputLabel>
      <Select
        labelId="company-switcher-label"
        label="Company"
        value={activeId}
        onChange={(e) => handleChange(Number(e.target.value))}
        displayEmpty
      >
        {companies.map((c) => (
          <MenuItem key={c.id} value={c.id}>
            {c.code} — {c.name}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  )
}

export default CompanySwitcher
