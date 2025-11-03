import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Box, Button, Stack, TextField, Typography } from '@mui/material'
import { createCompany } from '../../services/company'
import { setCompanyId, getCompanyId } from '../../utils/axios'
import { refresh } from '../../services/auth'
import { useAuth } from '../../hooks/useAuth'

const companySchema = z.object({
  code: z.string().regex(/^[A-Z0-9-]{3,16}$/),
  name: z.string().min(1),
  tax_code: z.string().regex(/^\d{10}$/),
  address: z.string().min(1),
  logoFile: z.instanceof(File).optional(),
})

type CompanyForm = z.infer<typeof companySchema>

export function CompanySettings() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [values, setValues] = useState<CompanyForm>({
    code: '',
    name: '',
    tax_code: '',
    address: '',
    logoFile: undefined,
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState<string | null>(null)

  // Check if user already has a company - if yes, redirect to dashboard
  useEffect(() => {
    const userCompanyId = user?.companyId ?? null
    const storedCompanyId = getCompanyId()
    const hasCompany =
      (userCompanyId !== null && userCompanyId !== undefined) ||
      (storedCompanyId !== null && storedCompanyId !== undefined)

    if (hasCompany) {
      // User already has a company, redirect to dashboard
      // Company Settings is only for creating the first company
      navigate('/', { replace: true })
    }
  }, [user, navigate])

  function onChange<K extends keyof CompanyForm>(key: K, value: CompanyForm[K]) {
    setValues((v) => ({ ...v, [key]: value }))
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setErrors({})
    setSuccess(null)
    // Validate logo file if present: PNG/JPEG and <= 256KB
    if (values.logoFile) {
      const f = values.logoFile
      const isValidType = f.type === 'image/png' || f.type === 'image/jpeg'
      const isValidSize = f.size <= 256 * 1024
      if (!isValidType || !isValidSize) {
        setErrors({ logoFile: !isValidType ? 'Logo must be PNG/JPEG' : 'Logo must be ≤ 256KB' })
        return
      }
    }
    const parsed = companySchema.safeParse(values)
    if (!parsed.success) {
      const map: Record<string, string> = {}
      for (const issue of parsed.error.issues) {
        const key = issue.path.join('.')
        map[key] = issue.message
      }
      setErrors(map)
      return
    }
    try {
      setSubmitting(true)
      const createdCompany = await createCompany({
        code: values.code,
        name: values.name,
        tax_code: values.tax_code,
        address: values.address,
        logoFile: values.logoFile,
      })

      // Update localStorage with new company ID
      setCompanyId(createdCompany.id)

      // Refresh auth state to get updated user with companyId
      try {
        const authResponse = await refresh()
        // Update companyId from refreshed user data (in case backend updated it)
        if (authResponse.user.companyId) {
          setCompanyId(authResponse.user.companyId)
        }
      } catch (refreshErr) {
        // If refresh fails, still proceed with companyId we got from creation
        console.warn('Failed to refresh auth state:', refreshErr)
      }

      setSuccess('Company created successfully! Redirecting...')
      setValues({ code: '', name: '', tax_code: '', address: '', logoFile: undefined })

      // Navigate to dashboard after brief delay
      setTimeout(() => {
        navigate('/')
      }, 1500)
    } catch (err) {
      // Map backend error shape if available (supports camelCase from backend)
      const errorData = err as {
        error?: { details?: { field?: string }; message?: string }
        message?: string
      }
      const rawField = errorData?.error?.details?.field
      const message = errorData?.error?.message || errorData?.message || 'Failed to create company'
      const field = rawField === 'taxCode' ? 'tax_code' : rawField
      if (field) {
        setErrors({ [field]: message })
      } else {
        setErrors({ form: message })
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Box component="form" onSubmit={onSubmit} sx={{ maxWidth: 560 }}>
      <Typography variant="h5" gutterBottom>
        Company Settings
      </Typography>
      <Stack spacing={2}>
        <TextField
          label="Code"
          value={values.code}
          onChange={(e) => onChange('code', e.target.value.toUpperCase())}
          error={Boolean(errors.code)}
          helperText={errors.code || '3-16 chars, A-Z 0-9 -'}
          inputProps={{ maxLength: 16 }}
          required
        />
        <TextField
          label="Name"
          value={values.name}
          onChange={(e) => onChange('name', e.target.value)}
          error={Boolean(errors.name)}
          helperText={errors.name}
          required
        />
        <TextField
          label="Tax Code"
          value={values.tax_code}
          onChange={(e) => onChange('tax_code', e.target.value.replace(/[^\d]/g, '').slice(0, 10))}
          error={Boolean(errors.tax_code)}
          helperText={errors.tax_code || 'Exactly 10 digits'}
          inputProps={{ inputMode: 'numeric' }}
          required
        />
        <TextField
          label="Address"
          value={values.address}
          onChange={(e) => onChange('address', e.target.value)}
          error={Boolean(errors.address)}
          helperText={errors.address}
          required
        />
        <input
          type="file"
          accept="image/png,image/jpeg"
          onChange={(e) => onChange('logoFile', e.target.files?.[0])}
        />
        {errors.form && (
          <Typography color="error" variant="body2">
            {errors.form}
          </Typography>
        )}
        {success && (
          <Typography color="success.main" variant="body2">
            {success}
          </Typography>
        )}
        <Button type="submit" variant="contained" disabled={submitting}>
          {submitting ? 'Submitting…' : 'Create Company'}
        </Button>
      </Stack>
    </Box>
  )
}

export default CompanySettings
