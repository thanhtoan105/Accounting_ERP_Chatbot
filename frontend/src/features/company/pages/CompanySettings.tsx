import { useEffect, useMemo, useState, useRef } from 'react'
import { useLocation } from 'react-router-dom'
import { z } from 'zod'
import {
  createCompany,
  getCompanySettings,
  updateCompanySettings,
  type Company,
} from '@/services/company'
import { setCompanyId, getCompanyId } from '@/utils/axios'
import { refresh } from '@/features/auth/services/auth'
import { useAuth } from '@/hooks/useAuth'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PhoneInput } from '@/components/ui/phone-input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Calendar } from '@/components/ui/calendar'
import { CalendarIcon, X } from 'lucide-react'
import { format } from 'date-fns'
import { Alert } from '@/components/ui/alert'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/skeleton'
import { CountryDropdown, type Country } from '@/components/country-dropdown'
import { toast } from 'sonner'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import {
  getAdvancedCompanySettings,
  updateAdvancedCompanySettings,
} from '@/features/company/services/companySettings'
import type {
  CompanySettingsDto,
  UpdateCompanySettingsRequest,
  NumberingConfig,
} from '@/types/companySettings'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { useNavigate } from 'react-router-dom'

// Zod schema for NumberingConfig JSON structure
const numberingConfigSchema = z.object({
  voucher: z
    .object({
      prefix: z.string().max(10, { message: 'Prefix must be max 10 characters' }),
      sequence: z.number().int().min(0, { message: 'Sequence must be non-negative integer' }),
    })
    .optional(),
  bill: z
    .object({
      prefix: z.string().max(10, { message: 'Prefix must be max 10 characters' }),
      sequence: z.number().int().min(0, { message: 'Sequence must be non-negative integer' }),
    })
    .optional(),
  invoice: z
    .object({
      prefix: z.string().max(10, { message: 'Prefix must be max 10 characters' }),
      sequence: z.number().int().min(0, { message: 'Sequence must be non-negative integer' }),
    })
    .optional(),
})

const companySchema = z.object({
  code: z.string().regex(/^[A-Z0-9-]{3,16}$/, { message: 'Code must be 3-16 chars (A-Z, 0-9, -)' }),
  name: z.string().min(1, { message: 'Name is required' }),
  tax_code: z.string().regex(/^\d{10}$/, { message: 'Tax code must be exactly 10 digits' }),
  address: z.string().min(1, { message: 'Address is required' }),
  contact_email: z
    .string()
    .min(1, { message: 'Email is required' })
    .email({ message: 'Invalid email address' }),
  contact_phone: z
    .string()
    .min(6, { message: 'Phone is required' })
    .regex(/^[0-9+()\-\s]{6,32}$/, { message: 'Invalid phone number format' }),
  fiscal_year_start: z
    .string()
    .min(1, { message: 'Fiscal year start is required' })
    .regex(/^\d{4}-\d{2}-\d{2}$/, { message: 'Invalid date format (yyyy-MM-dd)' }),
  logoFile: z.instanceof(File).optional(),
})

type CompanyForm = z.infer<typeof companySchema>

export default function CompanySettings() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()
  const currentCompanyId = useMemo(() => user?.companyId ?? getCompanyId() ?? null, [user])
  const hasCompany = currentCompanyId !== null && currentCompanyId !== undefined
  const [existingCompany, setExistingCompany] = useState<Company | null>(null)
  const [values, setValues] = useState<CompanyForm>({
    code: '',
    name: '',
    tax_code: '',
    address: '',
    contact_email: '',
    contact_phone: '',
    fiscal_year_start: '',
    logoFile: undefined,
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState<string | null>(null)
  const [loadingCompany, setLoadingCompany] = useState(false)
  const [logoPreviewUrl, setLogoPreviewUrl] = useState<string | null>(null)
  const [touched, setTouched] = useState<Record<string, boolean>>({})

  // Advanced settings state
  const [activeTab, setActiveTab] = useState<'basic' | 'advanced'>('basic')
  const [advancedSettings, setAdvancedSettings] = useState<CompanySettingsDto | null>(null)
  const [advancedValues, setAdvancedValues] = useState<UpdateCompanySettingsRequest>({})
  const [advancedErrors, setAdvancedErrors] = useState<Record<string, string>>({})
  const [loadingAdvanced, setLoadingAdvanced] = useState(false)
  const [submittingAdvanced, setSubmittingAdvanced] = useState(false)
  const [isDirty, setIsDirty] = useState(false)

  function withCacheBuster(url: string | null | undefined): string | null {
    if (!url) return null
    // Do NOT modify Supabase signed URLs, otherwise signature becomes invalid (400)
    if (url.includes('/storage/v1/object/sign/')) return url
    const sep = url.includes('?') ? '&' : '?'
    return `${url}${sep}v=${Date.now()}`
  }

  // Ensure X-Company-Id header is available for backend by syncing from user
  useEffect(() => {
    if (user?.companyId !== undefined && user?.companyId !== null) {
      setCompanyId(user.companyId)
    }
  }, [user?.companyId])

  // Load existing company/settings if present to allow editing
  useEffect(() => {
    let mounted = true
    const load = async () => {
      if (!hasCompany) return
      setLoadingCompany(true)
      try {
        // Ensure header is set BEFORE calling API to avoid 403
        const headerCompanyId = user?.companyId ?? getCompanyId()
        if (headerCompanyId !== null && headerCompanyId !== undefined) {
          setCompanyId(headerCompanyId)
        } else {
          // No company in session yet; skip load until available
          return
        }
        // Prefer admin settings endpoint for current company context
        const settings = await getCompanySettings()
        if (mounted && settings) {
          setExistingCompany({
            logoUrl: (settings as { logoUrl?: string | null }).logoUrl || null,
          } as unknown as Company)
          setValues({
            code: settings.code || '', // <CHANGE> load read-only company code for display
            name: settings.name || '',
            tax_code: settings.taxCode || '',
            address: settings.address || '',
            contact_email: settings.contactEmail || '',
            contact_phone: settings.contactPhone || '',
            fiscal_year_start: settings.fiscalYearStart || '',
            logoFile: undefined,
          })
          setLogoPreviewUrl(withCacheBuster(settings.logoUrl))
        }
      } finally {
        if (mounted) setLoadingCompany(false)
      }
    }
    load()
    return () => {
      mounted = false
    }
  }, [hasCompany, currentCompanyId])

  // Load advanced settings when switching to advanced tab
  useEffect(() => {
    if (activeTab === 'advanced' && hasCompany && !advancedSettings) {
      let mounted = true
      const loadAdvanced = async () => {
        setLoadingAdvanced(true)
        try {
          const headerCompanyId = user?.companyId ?? getCompanyId()
          if (headerCompanyId !== null && headerCompanyId !== undefined) {
            setCompanyId(headerCompanyId)
          }
          const settings = await getAdvancedCompanySettings()
          if (mounted) {
            setAdvancedSettings(settings)
            setAdvancedValues({
              legalName: settings.legalName || undefined,
              shortName: settings.shortName || undefined,
              registrationNumber: settings.registrationNumber || undefined,
              defaultFiscalYearStartMonth: settings.defaultFiscalYearStartMonth || undefined,
              timezone: settings.timezone || undefined,
              defaultCurrency: settings.defaultCurrency || undefined,
              currencyFormat: settings.currencyFormat || undefined,
              thousandSeparator: settings.thousandSeparator || undefined,
              decimalSeparator: settings.decimalSeparator || undefined,
              dateFormat: settings.dateFormat || undefined,
              language: settings.language || undefined,
              vatRegistrationNumber: settings.vatRegistrationNumber || undefined,
              vatRatePresets: settings.vatRatePresets || undefined,
              invoiceRoundingMode: settings.invoiceRoundingMode || undefined,
              taxRoundingMode: settings.taxRoundingMode || undefined,
              eInvoiceEnabled: settings.eInvoiceEnabled ?? undefined,
              auditRetentionPeriodDays: settings.auditRetentionPeriodDays || undefined,
              numberingConfig: settings.numberingConfig || undefined,
              bankReconciliationEnabled: settings.bankReconciliationEnabled ?? undefined,
              exportFormatDefault: settings.exportFormatDefault || undefined,
              updatedAt: settings.updatedAt,
            })
            setIsDirty(false)
          }
        } catch (err) {
          if (mounted) {
            toast.error('Failed to load advanced settings')
            console.error(err)
          }
        } finally {
          if (mounted) setLoadingAdvanced(false)
        }
      }
      loadAdvanced()
      return () => {
        mounted = false
      }
    }
  }, [activeTab, hasCompany, advancedSettings, user?.companyId])

  // Unsaved changes guard - browser navigation only
  // Note: useBlocker requires data router (createBrowserRouter), but we use BrowserRouter
  // So we only guard browser navigation (beforeunload) and show a toast for in-app navigation
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (isDirty) {
        e.preventDefault()
        e.returnValue = ''
      }
    }

    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload)
    }
  }, [isDirty])

  // Warn user about unsaved changes when navigating away (in-app)
  // We can't block with BrowserRouter, so we just show a warning
  const handleNavigationAttempt = (targetPath: string | number) => {
    if (isDirty) {
      const confirmed = window.confirm(
        'You have unsaved changes. Are you sure you want to leave? Your changes will be lost.',
      )
      if (!confirmed) {
        return false
      }
      setIsDirty(false)
    }
    return true
  }

  function onChange<K extends keyof CompanyForm>(key: K, value: CompanyForm[K]) {
    setValues((v) => ({ ...v, [key]: value }))
    if (key === 'logoFile') {
      const file = value as File | undefined
      if (file) {
        const url = URL.createObjectURL(file)
        setLogoPreviewUrl(url)
      }
    }
  }

  function onBlur<K extends keyof CompanyForm>(key: K) {
    setTouched((t) => ({ ...t, [key]: true }))
  }

  function clearLogo() {
    setValues((v) => ({ ...v, logoFile: undefined }))
    setLogoPreviewUrl(existingCompany?.logoUrl ?? null)
    setErrors((e) => ({ ...e, logoFile: '' }))
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setErrors({})
    setSuccess(null)

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
      if (hasCompany) {
        const updated = await updateCompanySettings(
          {
            name: values.name,
            taxCode: values.tax_code,
            address: values.address,
            contactEmail: values.contact_email,
            contactPhone: values.contact_phone,
            fiscalYearStart: values.fiscal_year_start,
          },
          values.logoFile,
        )
        // <CHANGE> Use Sonner toast for edit success instead of inline Alert
        toast.success('Settings updated successfully')
        // <CHANGE> Reflect persisted logo URL immediately and clear local file
        if (updated?.logoUrl) {
          setLogoPreviewUrl(withCacheBuster(updated.logoUrl))
        }
        if (values.logoFile) {
          onChange('logoFile', undefined)
        }
      } else {
        const createdCompany = await createCompany({
          code: values.code,
          name: values.name,
          tax_code: values.tax_code,
          address: values.address,
          logoFile: values.logoFile,
        })

        setCompanyId(createdCompany.id)
        try {
          const authResponse = await refresh()
          if (authResponse.user.companyId) {
            setCompanyId(authResponse.user.companyId)
          }
        } catch (refreshErr) {
          console.warn('Failed to refresh auth state:', refreshErr)
        }

        // <CHANGE> If a logo was chosen during creation, upload it via settings endpoint (multipart)
        if (values.logoFile) {
          try {
            const updatedAfterCreate = await updateCompanySettings({}, values.logoFile)
            if (updatedAfterCreate?.logoUrl) {
              setLogoPreviewUrl(withCacheBuster(updatedAfterCreate.logoUrl))
            }
            onChange('logoFile', undefined)
          } catch (uploadErr) {
            console.warn('Logo upload failed after company creation:', uploadErr)
          }
        }

        setSuccess('Company created successfully! Redirecting...')
        setValues({
          code: '',
          name: '',
          tax_code: '',
          address: '',
          contact_email: '',
          contact_phone: '',
          fiscal_year_start: '',
          logoFile: undefined,
        })
        setTimeout(() => navigate('/'), 1500)
      }
    } catch (err) {
      const errorData = err as {
        error?: { details?: { field?: string }; message?: string }
        message?: string
      }
      const rawField = errorData?.error?.details?.field
      const message = errorData?.error?.message || errorData?.message || 'Failed to create company'
      const field = rawField === 'taxCode' ? 'tax_code' : rawField
      if (field) setErrors({ [field]: message })
      else setErrors({ form: message })
    } finally {
      setSubmitting(false)
    }
  }

  // Advanced settings handlers
  function onAdvancedChange<K extends keyof UpdateCompanySettingsRequest>(
    key: K,
    value: UpdateCompanySettingsRequest[K],
  ) {
    setAdvancedValues((v) => ({ ...v, [key]: value }))
    setIsDirty(true)
  }

  function onAdvancedBlur(key: string) {
    if (key === 'numberingConfig' && advancedValues.numberingConfig) {
      try {
        const parsed = JSON.parse(advancedValues.numberingConfig)
        const result = numberingConfigSchema.safeParse(parsed)
        if (!result.success) {
          const errorMessage = result.error.issues.map((issue) => issue.message).join('; ')
          setAdvancedErrors((prev) => ({
            ...prev,
            numberingConfig: `Invalid JSON structure: ${errorMessage}`,
          }))
        } else {
          setAdvancedErrors((prev) => {
            const { numberingConfig, ...rest } = prev
            return rest
          })
        }
      } catch {
        setAdvancedErrors((prev) => ({
          ...prev,
          numberingConfig: 'Invalid JSON format',
        }))
      }
    }
  }

  // Numbering preview helper with Zod validation
  function getNumberingPreview(type: 'voucher' | 'bill' | 'invoice'): string {
    if (!advancedValues.numberingConfig) return '—'
    try {
      const parsed = JSON.parse(advancedValues.numberingConfig)
      const result = numberingConfigSchema.safeParse(parsed)
      if (!result.success) {
        return '—' // Invalid structure
      }
      const config: NumberingConfig = result.data
      const docConfig = config[type]
      if (!docConfig) return '—'
      const year = new Date().getFullYear()
      const seq = String(docConfig.sequence || 0).padStart(6, '0')
      return `${docConfig.prefix || ''}${year}-${seq}`
    } catch {
      return '—'
    }
  }

  async function onSubmitAdvanced(e: React.FormEvent) {
    e.preventDefault()
    setAdvancedErrors({})

    if (!advancedSettings) {
      toast.error('Settings not loaded')
      return
    }

    try {
      setSubmittingAdvanced(true)
      const updated = await updateAdvancedCompanySettings({
        ...advancedValues,
        updatedAt: advancedSettings.updatedAt,
      })
      setAdvancedSettings(updated)
      setAdvancedValues((prev) => ({ ...prev, updatedAt: updated.updatedAt }))
      setIsDirty(false)
      toast.success('Advanced settings updated successfully')
    } catch (err: any) {
      const errorData = err?.response?.data || err
      const error = errorData?.error || {}
      const message = error?.message || errorData?.message || 'Failed to update settings'

      if (err?.response?.status === 409) {
        toast.error('Settings were modified by another user. Please refresh and try again.')
        // Reload settings
        const settings = await getAdvancedCompanySettings()
        setAdvancedSettings(settings)
        setAdvancedValues({
          ...advancedValues,
          updatedAt: settings.updatedAt,
        })
      } else if (err?.response?.status === 400 && error?.details) {
        // Map field-specific validation errors
        const fieldErrors: Record<string, string> = {}
        Object.keys(error.details).forEach((field) => {
          // Map backend field names to frontend field names if needed
          const frontendField = field
          fieldErrors[frontendField] = error.details[field]
        })
        setAdvancedErrors(fieldErrors)

        // Show a general error toast with count of errors
        const errorCount = Object.keys(fieldErrors).length
        toast.error(
          errorCount === 1
            ? 'Please fix the validation error'
            : `Please fix ${errorCount} validation errors`,
        )
      } else {
        toast.error(message)
        setAdvancedErrors({ form: message })
      }
    } finally {
      setSubmittingAdvanced(false)
    }
  }

  return (
    <>
      <Card className="max-w-4xl">
        <CardHeader>
          <CardTitle className="flex items-center gap-3">
            {hasCompany ? 'Company Settings' : 'Create Company'}
            <span className="text-xs font-medium text-muted-foreground">
              {hasCompany ? 'Manage your organization settings' : 'First-time setup'}
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {hasCompany ? (
            <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as 'basic' | 'advanced')}>
              <TabsList>
                <TabsTrigger value="basic">Basic</TabsTrigger>
                <TabsTrigger value="advanced">Advanced</TabsTrigger>
              </TabsList>
              <TabsContent value="basic" className="mt-6">
                {loadingCompany ? (
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="md:col-span-2 space-y-4">
                      <Skeleton className="h-10 w-full" />
                      <Skeleton className="h-10 w-full" />
                      <Skeleton className="h-10 w-full" />
                      <Skeleton className="h-10 w-full" />
                    </div>
                    <div className="space-y-4">
                      <Skeleton className="h-24 w-24 rounded-full" />
                      <Skeleton className="h-10 w-full" />
                    </div>
                  </div>
                ) : (
                  <form onSubmit={onSubmit} className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="md:col-span-2 space-y-6">
                      <div>
                        <h3 className="text-base font-semibold">Company Info</h3>
                        <p className="text-sm text-muted-foreground">
                          Basic details about your organization.
                        </p>
                        <Separator className="my-3" />
                        <div className="grid gap-4">
                          <div className="grid gap-2">
                            <Label htmlFor="code">
                              Code <span className="text-destructive">*</span>
                            </Label>
                            <Input
                              id="code"
                              placeholder="e.g. ACME-01"
                              value={values.code}
                              onChange={(e) =>
                                onChange(
                                  'code',
                                  e.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, ''),
                                )
                              }
                              onBlur={() => onBlur('code')}
                              maxLength={16}
                              pattern="^[A-Z0-9-]{3,16}$"
                              aria-invalid={Boolean(errors.code)}
                              aria-describedby="code-help code-error"
                              required
                              disabled={hasCompany}
                            />
                            <p id="code-help" className="text-sm text-muted-foreground">
                              {hasCompany
                                ? 'Code is set at creation and cannot be changed.'
                                : '3-16 characters; allowed: A-Z, 0-9, dash (-)'}
                            </p>
                            {touched.code && errors.code && (
                              <p id="code-error" className="text-sm text-destructive">
                                {errors.code}
                              </p>
                            )}
                          </div>
                          <div className="grid gap-2">
                            <Label htmlFor="name">
                              Name <span className="text-destructive">*</span>
                            </Label>
                            <Input
                              id="name"
                              placeholder="Company legal name"
                              value={values.name}
                              onChange={(e) => onChange('name', e.target.value)}
                              onBlur={() => onBlur('name')}
                              aria-invalid={Boolean(errors.name)}
                              aria-describedby="name-error"
                            />
                            {touched.name && errors.name && (
                              <p id="name-error" className="text-sm text-destructive">
                                {errors.name}
                              </p>
                            )}
                          </div>
                          <div className="grid gap-2">
                            <Label htmlFor="tax_code">
                              Tax Code <span className="text-destructive">*</span>
                            </Label>
                            <Input
                              id="tax_code"
                              placeholder="10 digits"
                              value={values.tax_code}
                              onChange={(e) =>
                                onChange(
                                  'tax_code',
                                  e.target.value.replace(/[^\d]/g, '').slice(0, 10),
                                )
                              }
                              onBlur={() => onBlur('tax_code')}
                              inputMode="numeric"
                              pattern="^\d{10}$"
                              aria-invalid={Boolean(errors.tax_code)}
                              aria-describedby="tax-help tax-error"
                              required
                            />
                            <p id="tax-help" className="text-sm text-muted-foreground">
                              Exactly 10 digits (numbers only)
                            </p>
                            {touched.tax_code && errors.tax_code && (
                              <p id="tax-error" className="text-sm text-destructive">
                                {errors.tax_code}
                              </p>
                            )}
                          </div>
                          <div className="grid gap-2">
                            <Label htmlFor="address">
                              Address <span className="text-destructive">*</span>
                            </Label>
                            <Input
                              id="address"
                              placeholder="Street, City, Country"
                              value={values.address}
                              onChange={(e) => onChange('address', e.target.value)}
                              onBlur={() => onBlur('address')}
                              aria-invalid={Boolean(errors.address)}
                              aria-describedby="address-error"
                              required
                            />
                            {touched.address && errors.address && (
                              <p id="address-error" className="text-sm text-destructive">
                                {errors.address}
                              </p>
                            )}
                          </div>
                          <div className="grid gap-2">
                            <Label htmlFor="contact_email">
                              {' '}
                              Contact Email <span className="text-destructive">*</span>
                            </Label>
                            <Input
                              id="contact_email"
                              type="email"
                              placeholder="billing@company.com"
                              value={values.contact_email || ''}
                              onChange={(e) => onChange('contact_email', e.target.value)}
                              onBlur={() => onBlur('contact_email')}
                              aria-invalid={Boolean(errors.contact_email)}
                              aria-describedby="contact_email-error"
                              required
                            />
                            {touched.contact_email && errors.contact_email && (
                              <p id="contact_email-error" className="text-sm text-destructive">
                                {errors.contact_email}
                              </p>
                            )}
                          </div>
                          <div className="grid gap-2">
                            <Label htmlFor="contact_phone">
                              Contact Phone <span className="text-destructive">*</span>
                            </Label>
                            <div className="flex">
                              <CountryDropdown
                                placeholder="Country"
                                defaultValue={undefined}
                                inline
                                slim
                                onChange={(country: Country) => {
                                  const dial = country.countryCallingCodes?.[0] || ''
                                  // If current value already starts with +, replace its prefix; else just set dial
                                  const current = values.contact_phone || ''
                                  const next = current.startsWith('+')
                                    ? dial + current.replace(/^\+\d+\s?/, '')
                                    : dial
                                  onChange('contact_phone', next)
                                }}
                              />
                              <div className="flex-1">
                                <PhoneInput
                                  id="contact_phone"
                                  placeholder="Enter your number"
                                  value={values.contact_phone || ''}
                                  onChange={(e) => onChange('contact_phone', e.target.value)}
                                  onBlur={() => onBlur('contact_phone')}
                                  aria-invalid={Boolean(errors.contact_phone)}
                                  aria-describedby="contact_phone-error"
                                  defaultCountry="US"
                                  inline
                                  required
                                />
                              </div>
                            </div>
                            {touched.contact_phone && errors.contact_phone && (
                              <p id="contact_phone-error" className="text-sm text-destructive">
                                {errors.contact_phone}
                              </p>
                            )}
                          </div>
                          <div className="grid gap-2">
                            <Label htmlFor="fiscal_year_start">
                              Fiscal Year Start <span className="text-destructive">*</span>
                            </Label>
                            <Popover>
                              <PopoverTrigger asChild>
                                <Button
                                  id="fiscal_year_start"
                                  type="button"
                                  variant="outline"
                                  className="justify-start font-normal"
                                  aria-invalid={Boolean(errors.fiscal_year_start)}
                                  aria-describedby="fiscal_year_start-error"
                                >
                                  <CalendarIcon className="mr-2 h-4 w-4" />
                                  {values.fiscal_year_start
                                    ? format(
                                        new Date(`${values.fiscal_year_start}T00:00:00`),
                                        'PPP',
                                      )
                                    : 'Pick a date'}
                                </Button>
                              </PopoverTrigger>
                              <PopoverContent align="start" className="p-0">
                                <Calendar
                                  mode="single"
                                  selected={
                                    values.fiscal_year_start
                                      ? new Date(`${values.fiscal_year_start}T00:00:00`)
                                      : undefined
                                  }
                                  onSelect={(date) => {
                                    onBlur('fiscal_year_start')
                                    if (!date) {
                                      onChange('fiscal_year_start', '')
                                      return
                                    }
                                    const localYmd = format(date, 'yyyy-MM-dd')
                                    onChange('fiscal_year_start', localYmd)
                                  }}
                                  captionLayout="dropdown"
                                  fromYear={2000}
                                  toYear={new Date().getFullYear() + 5}
                                  initialFocus
                                />
                              </PopoverContent>
                            </Popover>
                            {touched.fiscal_year_start && errors.fiscal_year_start && (
                              <p id="fiscal_year_start-error" className="text-sm text-destructive">
                                {errors.fiscal_year_start}
                              </p>
                            )}
                          </div>
                        </div>
                      </div>
                      {errors.form && (
                        <Alert className="text-destructive text-sm" role="alert">
                          {errors.form}
                        </Alert>
                      )}
                      {success && (
                        <Alert className="text-green-600 text-sm" role="status">
                          {success}
                        </Alert>
                      )}
                      <div className="flex flex-wrap gap-2">
                        <Button type="submit" disabled={submitting}>
                          {submitting
                            ? hasCompany
                              ? 'Saving…'
                              : 'Submitting…'
                            : hasCompany
                              ? 'Save Changes'
                              : 'Create Company'}
                        </Button>
                        <Button
                          type="button"
                          variant="secondary"
                          disabled={submitting}
                          onClick={() => {
                            if (handleNavigationAttempt(-1)) {
                              navigate(-1)
                            }
                          }}
                        >
                          Cancel
                        </Button>
                      </div>
                    </div>
                    <div className="space-y-6">
                      <div>
                        <h3 className="text-base font-semibold">Branding</h3>
                        <p className="text-sm text-muted-foreground">
                          Upload a square logo for best results.
                        </p>
                        <Separator className="my-3" />
                        <div className="flex items-start gap-4">
                          <div className="relative">
                            <Avatar className="h-20 w-20 overflow-hidden bg-muted">
                              {logoPreviewUrl ? (
                                <img
                                  key={logoPreviewUrl}
                                  src={logoPreviewUrl}
                                  alt="Logo preview"
                                  className="h-full w-full object-cover"
                                  crossOrigin="anonymous"
                                  referrerPolicy="no-referrer"
                                  onError={() => setLogoPreviewUrl(null)}
                                />
                              ) : (
                                <AvatarFallback>
                                  {(values.name || 'C').slice(0, 2).toUpperCase()}
                                </AvatarFallback>
                              )}
                            </Avatar>
                            {logoPreviewUrl && (
                              <Button
                                type="button"
                                variant="outline"
                                size="icon"
                                className="absolute -top-2 -right-2 h-6 w-6 rounded-full"
                                aria-label="Remove logo"
                                onClick={clearLogo}
                              >
                                <X className="h-3 w-3" />
                              </Button>
                            )}
                          </div>
                          <div className="flex-1 grid gap-2">
                            <Input
                              id="logo"
                              type="file"
                              accept="image/png,image/jpeg"
                              onChange={(e) => onChange('logoFile', e.target.files?.[0])}
                              aria-describedby="logo-help logo-error"
                            />
                            {errors.logoFile && (
                              <p className="text-sm text-destructive">{errors.logoFile}</p>
                            )}
                            <p id="logo-help" className="text-xs text-muted-foreground">
                              PNG or JPEG up to 256KB.
                            </p>
                          </div>
                        </div>
                      </div>

                      <div>
                        <h3 className="text-base font-semibold">Preview</h3>
                        <p className="text-sm text-muted-foreground">
                          Live summary of what will be saved.
                        </p>
                        <Separator className="my-3" />
                        <div className="grid gap-2 text-sm">
                          <div className="flex items-center justify-between">
                            <span className="text-muted-foreground">Code</span>
                            <span className="font-medium">{values.code || '—'}</span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-muted-foreground">Name</span>
                            <span className="font-medium">{values.name || '—'}</span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-muted-foreground">Tax code</span>
                            <span className="font-medium">{values.tax_code || '—'}</span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-muted-foreground">Address</span>
                            <span
                              className="font-medium truncate max-w-[14rem]"
                              title={values.address}
                            >
                              {values.address || '—'}
                            </span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-muted-foreground">Email</span>
                            <span className="font-medium">{values.contact_email || '—'}</span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-muted-foreground">Phone</span>
                            <span className="font-medium">{values.contact_phone || '—'}</span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-muted-foreground">Fiscal year start</span>
                            <span className="font-medium">
                              {values.fiscal_year_start
                                ? format(new Date(`${values.fiscal_year_start}T00:00:00`), 'PPP')
                                : '—'}
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </form>
                )}
              </TabsContent>
              <TabsContent value="advanced" className="mt-6">
                {loadingAdvanced ? (
                  <div className="space-y-4">
                    <Skeleton className="h-10 w-full" />
                    <Skeleton className="h-10 w-full" />
                    <Skeleton className="h-10 w-full" />
                  </div>
                ) : (
                  <Tabs defaultValue="general" className="w-full">
                    <TabsList className="grid w-full grid-cols-5">
                      <TabsTrigger value="general">General</TabsTrigger>
                      <TabsTrigger value="localization">Localization</TabsTrigger>
                      <TabsTrigger value="tax">Tax & Compliance</TabsTrigger>
                      <TabsTrigger value="numbering">Numbering</TabsTrigger>
                      <TabsTrigger value="integrations">Integrations</TabsTrigger>
                    </TabsList>
                    <form onSubmit={onSubmitAdvanced}>
                      <TabsContent value="general" className="mt-6 space-y-6">
                        <div>
                          <h3 className="text-base font-semibold mb-3">General Settings</h3>
                          <Separator className="my-3" />
                          <div className="grid gap-4">
                            <div className="grid gap-2">
                              <Label htmlFor="legalName">Legal Name</Label>
                              <Input
                                id="legalName"
                                value={advancedValues.legalName || ''}
                                onChange={(e) => onAdvancedChange('legalName', e.target.value)}
                                onBlur={() => onAdvancedBlur('legalName')}
                                className={advancedErrors.legalName ? 'border-destructive' : ''}
                                aria-describedby={
                                  advancedErrors.legalName ? 'legalName-error' : undefined
                                }
                              />
                              {advancedErrors.legalName && (
                                <p id="legalName-error" className="text-sm text-destructive">
                                  {advancedErrors.legalName}
                                </p>
                              )}
                            </div>
                            <div className="grid gap-2">
                              <Label htmlFor="shortName">Short Name</Label>
                              <Input
                                id="shortName"
                                value={advancedValues.shortName || ''}
                                onChange={(e) => onAdvancedChange('shortName', e.target.value)}
                                onBlur={() => onAdvancedBlur('shortName')}
                              />
                            </div>
                            <div className="grid gap-2">
                              <Label htmlFor="registrationNumber">Registration Number</Label>
                              <Input
                                id="registrationNumber"
                                value={advancedValues.registrationNumber || ''}
                                onChange={(e) =>
                                  onAdvancedChange('registrationNumber', e.target.value)
                                }
                                onBlur={() => onAdvancedBlur('registrationNumber')}
                              />
                            </div>
                            <div className="grid gap-2">
                              <Label htmlFor="defaultFiscalYearStartMonth">
                                Fiscal Year Start Month
                              </Label>
                              <Select
                                value={advancedValues.defaultFiscalYearStartMonth?.toString() || ''}
                                onValueChange={(v) =>
                                  onAdvancedChange(
                                    'defaultFiscalYearStartMonth',
                                    parseInt(v) || undefined,
                                  )
                                }
                              >
                                <SelectTrigger
                                  id="defaultFiscalYearStartMonth"
                                  aria-describedby={
                                    advancedErrors.defaultFiscalYearStartMonth
                                      ? 'defaultFiscalYearStartMonth-error defaultFiscalYearStartMonth-help'
                                      : 'defaultFiscalYearStartMonth-help'
                                  }
                                  className={
                                    advancedErrors.defaultFiscalYearStartMonth
                                      ? 'border-destructive'
                                      : ''
                                  }
                                >
                                  <SelectValue placeholder="Select month" />
                                </SelectTrigger>
                                <SelectContent>
                                  {Array.from({ length: 12 }, (_, i) => i + 1).map((month) => (
                                    <SelectItem key={month} value={month.toString()}>
                                      {new Date(2000, month - 1).toLocaleString('default', {
                                        month: 'long',
                                      })}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                              <p id="defaultFiscalYearStartMonth-help" className="sr-only">
                                Select the month when your fiscal year begins (1-12)
                              </p>
                              {advancedErrors.defaultFiscalYearStartMonth && (
                                <p
                                  id="defaultFiscalYearStartMonth-error"
                                  className="text-sm text-destructive"
                                >
                                  {advancedErrors.defaultFiscalYearStartMonth}
                                </p>
                              )}
                            </div>
                            <div className="grid gap-2">
                              <Label htmlFor="timezone">Timezone</Label>
                              <Input
                                id="timezone"
                                placeholder="e.g., Asia/Ho_Chi_Minh"
                                value={advancedValues.timezone || ''}
                                onChange={(e) => onAdvancedChange('timezone', e.target.value)}
                                onBlur={() => onAdvancedBlur('timezone')}
                                aria-describedby="timezone-help"
                              />
                              <p id="timezone-help" className="sr-only">
                                Enter timezone in IANA format, e.g., Asia/Ho_Chi_Minh
                              </p>
                            </div>
                          </div>
                        </div>
                      </TabsContent>
                      <TabsContent value="localization" className="mt-6 space-y-6">
                        <div>
                          <h3 className="text-base font-semibold mb-3">Localization Settings</h3>
                          <Separator className="my-3" />
                          <div className="grid gap-4">
                            <div className="grid gap-2">
                              <Label htmlFor="defaultCurrency">Default Currency</Label>
                              <Input
                                id="defaultCurrency"
                                placeholder="VND"
                                value={advancedValues.defaultCurrency || ''}
                                onChange={(e) =>
                                  onAdvancedChange(
                                    'defaultCurrency',
                                    e.target.value.toUpperCase().slice(0, 3),
                                  )
                                }
                                onBlur={() => onAdvancedBlur('defaultCurrency')}
                                maxLength={3}
                                disabled
                                className={
                                  advancedErrors.defaultCurrency ? 'border-destructive' : ''
                                }
                                aria-describedby={
                                  advancedErrors.defaultCurrency
                                    ? 'defaultCurrency-error defaultCurrency-help'
                                    : 'defaultCurrency-help'
                                }
                              />
                              <p
                                id="defaultCurrency-help"
                                className="text-xs text-muted-foreground"
                              >
                                Currency is locked to VND in MVP
                              </p>
                              {advancedErrors.defaultCurrency && (
                                <p id="defaultCurrency-error" className="text-sm text-destructive">
                                  {advancedErrors.defaultCurrency}
                                </p>
                              )}
                            </div>
                            <div className="grid gap-2">
                              <Label htmlFor="currencyFormat">Currency Format</Label>
                              <Input
                                id="currencyFormat"
                                placeholder="#,##0.00"
                                value={advancedValues.currencyFormat || ''}
                                onChange={(e) => onAdvancedChange('currencyFormat', e.target.value)}
                                onBlur={() => onAdvancedBlur('currencyFormat')}
                              />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                              <div className="grid gap-2">
                                <Label htmlFor="thousandSeparator">Thousand Separator</Label>
                                <Input
                                  id="thousandSeparator"
                                  placeholder=","
                                  value={advancedValues.thousandSeparator || ''}
                                  onChange={(e) =>
                                    onAdvancedChange(
                                      'thousandSeparator',
                                      e.target.value.slice(0, 1),
                                    )
                                  }
                                  onBlur={() => onAdvancedBlur('thousandSeparator')}
                                  maxLength={1}
                                />
                              </div>
                              <div className="grid gap-2">
                                <Label htmlFor="decimalSeparator">Decimal Separator</Label>
                                <Input
                                  id="decimalSeparator"
                                  placeholder="."
                                  value={advancedValues.decimalSeparator || ''}
                                  onChange={(e) =>
                                    onAdvancedChange('decimalSeparator', e.target.value.slice(0, 1))
                                  }
                                  onBlur={() => onAdvancedBlur('decimalSeparator')}
                                  maxLength={1}
                                />
                              </div>
                            </div>
                            <div className="grid gap-2">
                              <Label htmlFor="dateFormat">Date Format</Label>
                              <Select
                                value={advancedValues.dateFormat || ''}
                                onValueChange={(v) => onAdvancedChange('dateFormat', v)}
                              >
                                <SelectTrigger>
                                  <SelectValue placeholder="Select format" />
                                </SelectTrigger>
                                <SelectContent>
                                  <SelectItem value="ISO">ISO (YYYY-MM-DD)</SelectItem>
                                  <SelectItem value="VN">VN (DD/MM/YYYY)</SelectItem>
                                  <SelectItem value="DD/MM/YYYY">DD/MM/YYYY</SelectItem>
                                </SelectContent>
                              </Select>
                            </div>
                            <div className="grid gap-2">
                              <Label htmlFor="language">Language</Label>
                              <Select
                                value={advancedValues.language || ''}
                                onValueChange={(v) => onAdvancedChange('language', v)}
                              >
                                <SelectTrigger>
                                  <SelectValue placeholder="Select language" />
                                </SelectTrigger>
                                <SelectContent>
                                  <SelectItem value="vi">Vietnamese</SelectItem>
                                  <SelectItem value="en">English</SelectItem>
                                </SelectContent>
                              </Select>
                            </div>
                          </div>
                        </div>
                      </TabsContent>
                      <TabsContent value="tax" className="mt-6 space-y-6">
                        <div>
                          <h3 className="text-base font-semibold mb-3">Tax & Compliance</h3>
                          <Separator className="my-3" />
                          <div className="grid gap-4">
                            <div className="grid gap-2">
                              <Label htmlFor="vatRegistrationNumber">VAT Registration Number</Label>
                              <Input
                                id="vatRegistrationNumber"
                                placeholder="10 digits"
                                value={advancedValues.vatRegistrationNumber || ''}
                                onChange={(e) =>
                                  onAdvancedChange(
                                    'vatRegistrationNumber',
                                    e.target.value.replace(/[^\d]/g, '').slice(0, 10),
                                  )
                                }
                                onBlur={() => onAdvancedBlur('vatRegistrationNumber')}
                                maxLength={10}
                                className={
                                  advancedErrors.vatRegistrationNumber ? 'border-destructive' : ''
                                }
                                aria-describedby={
                                  advancedErrors.vatRegistrationNumber
                                    ? 'vatRegistrationNumber-error vatRegistrationNumber-help'
                                    : 'vatRegistrationNumber-help'
                                }
                              />
                              <p id="vatRegistrationNumber-help" className="sr-only">
                                Enter exactly 10 digits for VAT registration number
                              </p>
                              {advancedErrors.vatRegistrationNumber && (
                                <p
                                  id="vatRegistrationNumber-error"
                                  className="text-sm text-destructive"
                                >
                                  {advancedErrors.vatRegistrationNumber}
                                </p>
                              )}
                            </div>
                            <div className="grid gap-2">
                              <Label htmlFor="vatRatePresets">VAT Rate Presets (JSON)</Label>
                              <Textarea
                                id="vatRatePresets"
                                placeholder="[0, 5, 10]"
                                value={advancedValues.vatRatePresets || ''}
                                onChange={(e) => onAdvancedChange('vatRatePresets', e.target.value)}
                                onBlur={() => onAdvancedBlur('vatRatePresets')}
                                rows={3}
                              />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                              <div className="grid gap-2">
                                <Label htmlFor="invoiceRoundingMode">Invoice Rounding Mode</Label>
                                <Select
                                  value={advancedValues.invoiceRoundingMode || ''}
                                  onValueChange={(v) => onAdvancedChange('invoiceRoundingMode', v)}
                                >
                                  <SelectTrigger>
                                    <SelectValue placeholder="Select mode" />
                                  </SelectTrigger>
                                  <SelectContent>
                                    <SelectItem value="HALF_UP">HALF_UP</SelectItem>
                                    <SelectItem value="HALF_DOWN">HALF_DOWN</SelectItem>
                                    <SelectItem value="UP">UP</SelectItem>
                                    <SelectItem value="DOWN">DOWN</SelectItem>
                                  </SelectContent>
                                </Select>
                              </div>
                              <div className="grid gap-2">
                                <Label htmlFor="taxRoundingMode">Tax Rounding Mode</Label>
                                <Select
                                  value={advancedValues.taxRoundingMode || ''}
                                  onValueChange={(v) => onAdvancedChange('taxRoundingMode', v)}
                                >
                                  <SelectTrigger>
                                    <SelectValue placeholder="Select mode" />
                                  </SelectTrigger>
                                  <SelectContent>
                                    <SelectItem value="HALF_UP">HALF_UP</SelectItem>
                                    <SelectItem value="HALF_DOWN">HALF_DOWN</SelectItem>
                                    <SelectItem value="UP">UP</SelectItem>
                                    <SelectItem value="DOWN">DOWN</SelectItem>
                                  </SelectContent>
                                </Select>
                              </div>
                            </div>
                            <div className="flex items-center gap-2">
                              <Switch
                                id="eInvoiceEnabled"
                                checked={advancedValues.eInvoiceEnabled ?? false}
                                onCheckedChange={(checked) =>
                                  onAdvancedChange('eInvoiceEnabled', checked)
                                }
                              />
                              <Label htmlFor="eInvoiceEnabled">
                                E-Invoice Enabled (Placeholder)
                              </Label>
                            </div>
                            <div className="grid gap-2">
                              <Label htmlFor="auditRetentionPeriodDays">
                                Audit Retention Period (Days)
                              </Label>
                              <Input
                                id="auditRetentionPeriodDays"
                                type="number"
                                min="0"
                                value={advancedValues.auditRetentionPeriodDays || ''}
                                onChange={(e) =>
                                  onAdvancedChange(
                                    'auditRetentionPeriodDays',
                                    parseInt(e.target.value) || undefined,
                                  )
                                }
                                onBlur={() => onAdvancedBlur('auditRetentionPeriodDays')}
                              />
                            </div>
                          </div>
                        </div>
                      </TabsContent>
                      <TabsContent value="numbering" className="mt-6 space-y-6">
                        <div>
                          <h3 className="text-base font-semibold mb-3">Numbering Configuration</h3>
                          <Separator className="my-3" />
                          <div className="grid gap-4">
                            <div className="grid gap-2">
                              <Label htmlFor="numberingConfig">Numbering Config (JSON)</Label>
                              <Textarea
                                id="numberingConfig"
                                placeholder='{"voucher": {"prefix": "VC", "sequence": 1}, "bill": {"prefix": "BL", "sequence": 1}, "invoice": {"prefix": "INV", "sequence": 1}}'
                                value={advancedValues.numberingConfig || ''}
                                onChange={(e) =>
                                  onAdvancedChange('numberingConfig', e.target.value)
                                }
                                onBlur={() => onAdvancedBlur('numberingConfig')}
                                rows={6}
                                className={
                                  advancedErrors.numberingConfig ? 'border-destructive' : ''
                                }
                                aria-describedby={
                                  advancedErrors.numberingConfig
                                    ? 'numberingConfig-error numberingConfig-help'
                                    : 'numberingConfig-help'
                                }
                              />
                              <p id="numberingConfig-help" className="sr-only">
                                Enter JSON configuration for document numbering with prefix and
                                sequence for voucher, bill, and invoice
                              </p>
                              {advancedErrors.numberingConfig && (
                                <p id="numberingConfig-error" className="text-sm text-destructive">
                                  {advancedErrors.numberingConfig}
                                </p>
                              )}
                            </div>
                            <div className="grid gap-2">
                              <Label>Preview Examples</Label>
                              <div className="grid gap-2 p-4 bg-muted rounded-md">
                                <div className="flex items-center justify-between">
                                  <span className="text-sm text-muted-foreground">Voucher:</span>
                                  <span className="font-mono text-sm">
                                    {getNumberingPreview('voucher')}
                                  </span>
                                </div>
                                <div className="flex items-center justify-between">
                                  <span className="text-sm text-muted-foreground">Bill:</span>
                                  <span className="font-mono text-sm">
                                    {getNumberingPreview('bill')}
                                  </span>
                                </div>
                                <div className="flex items-center justify-between">
                                  <span className="text-sm text-muted-foreground">Invoice:</span>
                                  <span className="font-mono text-sm">
                                    {getNumberingPreview('invoice')}
                                  </span>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </TabsContent>
                      <TabsContent value="integrations" className="mt-6 space-y-6">
                        <div>
                          <h3 className="text-base font-semibold mb-3">Integrations</h3>
                          <Separator className="my-3" />
                          <div className="grid gap-4">
                            <div className="flex items-center gap-2">
                              <Switch
                                id="bankReconciliationEnabled"
                                checked={advancedValues.bankReconciliationEnabled ?? false}
                                onCheckedChange={(checked) =>
                                  onAdvancedChange('bankReconciliationEnabled', checked)
                                }
                              />
                              <Label htmlFor="bankReconciliationEnabled">
                                Bank Reconciliation Enabled (Placeholder)
                              </Label>
                            </div>
                            <div className="grid gap-2">
                              <Label htmlFor="exportFormatDefault">Default Export Format</Label>
                              <Select
                                value={advancedValues.exportFormatDefault || ''}
                                onValueChange={(v) => onAdvancedChange('exportFormatDefault', v)}
                              >
                                <SelectTrigger>
                                  <SelectValue placeholder="Select format" />
                                </SelectTrigger>
                                <SelectContent>
                                  <SelectItem value="EXCEL">Excel</SelectItem>
                                  <SelectItem value="CSV">CSV</SelectItem>
                                </SelectContent>
                              </Select>
                            </div>
                          </div>
                        </div>
                      </TabsContent>
                      {advancedErrors.form && (
                        <Alert className="text-destructive text-sm mt-4" role="alert">
                          {advancedErrors.form}
                        </Alert>
                      )}
                      <div className="flex flex-wrap gap-2 mt-6">
                        <Button type="submit" disabled={submittingAdvanced || !isDirty}>
                          {submittingAdvanced ? 'Saving…' : 'Save Changes'}
                        </Button>
                        <Button
                          type="button"
                          variant="secondary"
                          disabled={submittingAdvanced}
                          onClick={() => {
                            if (advancedSettings) {
                              setAdvancedValues({
                                legalName: advancedSettings.legalName || undefined,
                                shortName: advancedSettings.shortName || undefined,
                                registrationNumber:
                                  advancedSettings.registrationNumber || undefined,
                                defaultFiscalYearStartMonth:
                                  advancedSettings.defaultFiscalYearStartMonth || undefined,
                                timezone: advancedSettings.timezone || undefined,
                                defaultCurrency: advancedSettings.defaultCurrency || undefined,
                                currencyFormat: advancedSettings.currencyFormat || undefined,
                                thousandSeparator: advancedSettings.thousandSeparator || undefined,
                                decimalSeparator: advancedSettings.decimalSeparator || undefined,
                                dateFormat: advancedSettings.dateFormat || undefined,
                                language: advancedSettings.language || undefined,
                                vatRegistrationNumber:
                                  advancedSettings.vatRegistrationNumber || undefined,
                                vatRatePresets: advancedSettings.vatRatePresets || undefined,
                                invoiceRoundingMode:
                                  advancedSettings.invoiceRoundingMode || undefined,
                                taxRoundingMode: advancedSettings.taxRoundingMode || undefined,
                                eInvoiceEnabled: advancedSettings.eInvoiceEnabled ?? undefined,
                                auditRetentionPeriodDays:
                                  advancedSettings.auditRetentionPeriodDays || undefined,
                                numberingConfig: advancedSettings.numberingConfig || undefined,
                                bankReconciliationEnabled:
                                  advancedSettings.bankReconciliationEnabled ?? undefined,
                                exportFormatDefault:
                                  advancedSettings.exportFormatDefault || undefined,
                                updatedAt: advancedSettings.updatedAt,
                              })
                              setIsDirty(false)
                            }
                          }}
                        >
                          Reset
                        </Button>
                      </div>
                    </form>
                  </Tabs>
                )}
              </TabsContent>
            </Tabs>
          ) : (
            // No company - show basic form without tabs
            <>
              {loadingCompany ? (
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                  <div className="md:col-span-2 space-y-4">
                    <Skeleton className="h-10 w-full" />
                    <Skeleton className="h-10 w-full" />
                    <Skeleton className="h-10 w-full" />
                    <Skeleton className="h-10 w-full" />
                  </div>
                  <div className="space-y-4">
                    <Skeleton className="h-24 w-24 rounded-full" />
                    <Skeleton className="h-10 w-full" />
                  </div>
                </div>
              ) : (
                <form onSubmit={onSubmit} className="grid grid-cols-1 md:grid-cols-3 gap-6">
                  <div className="md:col-span-2 space-y-6">
                    <div>
                      <h3 className="text-base font-semibold">Company Info</h3>
                      <p className="text-sm text-muted-foreground">
                        Basic details about your organization.
                      </p>
                      <Separator className="my-3" />
                      <div className="grid gap-4">
                        <div className="grid gap-2">
                          <Label htmlFor="code">
                            Code <span className="text-destructive">*</span>
                          </Label>
                          <Input
                            id="code"
                            placeholder="e.g. ACME-01"
                            value={values.code}
                            onChange={(e) =>
                              onChange(
                                'code',
                                e.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, ''),
                              )
                            }
                            onBlur={() => onBlur('code')}
                            maxLength={16}
                            pattern="^[A-Z0-9-]{3,16}$"
                            aria-invalid={Boolean(errors.code)}
                            aria-describedby="code-help code-error"
                            required
                            disabled={hasCompany}
                          />
                          <p id="code-help" className="text-sm text-muted-foreground">
                            {hasCompany
                              ? 'Code is set at creation and cannot be changed.'
                              : '3-16 characters; allowed: A-Z, 0-9, dash (-)'}
                          </p>
                          {touched.code && errors.code && (
                            <p id="code-error" className="text-sm text-destructive">
                              {errors.code}
                            </p>
                          )}
                        </div>
                        <div className="grid gap-2">
                          <Label htmlFor="name">
                            Name <span className="text-destructive">*</span>
                          </Label>
                          <Input
                            id="name"
                            placeholder="Company legal name"
                            value={values.name}
                            onChange={(e) => onChange('name', e.target.value)}
                            onBlur={() => onBlur('name')}
                            aria-invalid={Boolean(errors.name)}
                            aria-describedby="name-error"
                          />
                          {touched.name && errors.name && (
                            <p id="name-error" className="text-sm text-destructive">
                              {errors.name}
                            </p>
                          )}
                        </div>
                        <div className="grid gap-2">
                          <Label htmlFor="tax_code">
                            Tax Code <span className="text-destructive">*</span>
                          </Label>
                          <Input
                            id="tax_code"
                            placeholder="10 digits"
                            value={values.tax_code}
                            onChange={(e) =>
                              onChange(
                                'tax_code',
                                e.target.value.replace(/[^\d]/g, '').slice(0, 10),
                              )
                            }
                            onBlur={() => onBlur('tax_code')}
                            inputMode="numeric"
                            pattern="^\d{10}$"
                            aria-invalid={Boolean(errors.tax_code)}
                            aria-describedby="tax-help tax-error"
                            required
                          />
                          <p id="tax-help" className="text-sm text-muted-foreground">
                            Exactly 10 digits (numbers only)
                          </p>
                          {touched.tax_code && errors.tax_code && (
                            <p id="tax-error" className="text-sm text-destructive">
                              {errors.tax_code}
                            </p>
                          )}
                        </div>
                        <div className="grid gap-2">
                          <Label htmlFor="address">
                            Address <span className="text-destructive">*</span>
                          </Label>
                          <Input
                            id="address"
                            placeholder="Street, City, Country"
                            value={values.address}
                            onChange={(e) => onChange('address', e.target.value)}
                            onBlur={() => onBlur('address')}
                            aria-invalid={Boolean(errors.address)}
                            aria-describedby="address-error"
                            required
                          />
                          {touched.address && errors.address && (
                            <p id="address-error" className="text-sm text-destructive">
                              {errors.address}
                            </p>
                          )}
                        </div>
                        <div className="grid gap-2">
                          <Label htmlFor="contact_email">
                            {' '}
                            Contact Email <span className="text-destructive">*</span>
                          </Label>
                          <Input
                            id="contact_email"
                            type="email"
                            placeholder="billing@company.com"
                            value={values.contact_email || ''}
                            onChange={(e) => onChange('contact_email', e.target.value)}
                            onBlur={() => onBlur('contact_email')}
                            aria-invalid={Boolean(errors.contact_email)}
                            aria-describedby="contact_email-error"
                            required
                          />
                          {touched.contact_email && errors.contact_email && (
                            <p id="contact_email-error" className="text-sm text-destructive">
                              {errors.contact_email}
                            </p>
                          )}
                        </div>
                        <div className="grid gap-2">
                          <Label htmlFor="contact_phone">
                            Contact Phone <span className="text-destructive">*</span>
                          </Label>
                          <div className="flex">
                            <CountryDropdown
                              placeholder="Country"
                              defaultValue={undefined}
                              inline
                              slim
                              onChange={(country: Country) => {
                                const dial = country.countryCallingCodes?.[0] || ''
                                const current = values.contact_phone || ''
                                const next = current.startsWith('+')
                                  ? dial + current.replace(/^\+\d+\s?/, '')
                                  : dial
                                onChange('contact_phone', next)
                              }}
                            />
                            <div className="flex-1">
                              <PhoneInput
                                id="contact_phone"
                                placeholder="Enter your number"
                                value={values.contact_phone || ''}
                                onChange={(e) => onChange('contact_phone', e.target.value)}
                                onBlur={() => onBlur('contact_phone')}
                                aria-invalid={Boolean(errors.contact_phone)}
                                aria-describedby="contact_phone-error"
                                defaultCountry="US"
                                inline
                                required
                              />
                            </div>
                          </div>
                          {touched.contact_phone && errors.contact_phone && (
                            <p id="contact_phone-error" className="text-sm text-destructive">
                              {errors.contact_phone}
                            </p>
                          )}
                        </div>
                        <div className="grid gap-2">
                          <Label htmlFor="fiscal_year_start">
                            Fiscal Year Start <span className="text-destructive">*</span>
                          </Label>
                          <Popover>
                            <PopoverTrigger asChild>
                              <Button
                                id="fiscal_year_start"
                                type="button"
                                variant="outline"
                                className="justify-start font-normal"
                                aria-invalid={Boolean(errors.fiscal_year_start)}
                                aria-describedby="fiscal_year_start-error"
                              >
                                <CalendarIcon className="mr-2 h-4 w-4" />
                                {values.fiscal_year_start
                                  ? format(new Date(`${values.fiscal_year_start}T00:00:00`), 'PPP')
                                  : 'Pick a date'}
                              </Button>
                            </PopoverTrigger>
                            <PopoverContent align="start" className="p-0">
                              <Calendar
                                mode="single"
                                selected={
                                  values.fiscal_year_start
                                    ? new Date(`${values.fiscal_year_start}T00:00:00`)
                                    : undefined
                                }
                                onSelect={(date) => {
                                  onBlur('fiscal_year_start')
                                  if (!date) {
                                    onChange('fiscal_year_start', '')
                                    return
                                  }
                                  const localYmd = format(date, 'yyyy-MM-dd')
                                  onChange('fiscal_year_start', localYmd)
                                }}
                                captionLayout="dropdown"
                                fromYear={2000}
                                toYear={new Date().getFullYear() + 5}
                                initialFocus
                              />
                            </PopoverContent>
                          </Popover>
                          {touched.fiscal_year_start && errors.fiscal_year_start && (
                            <p id="fiscal_year_start-error" className="text-sm text-destructive">
                              {errors.fiscal_year_start}
                            </p>
                          )}
                        </div>
                      </div>
                    </div>
                    {errors.form && (
                      <Alert className="text-destructive text-sm" role="alert">
                        {errors.form}
                      </Alert>
                    )}
                    {success && (
                      <Alert className="text-green-600 text-sm" role="status">
                        {success}
                      </Alert>
                    )}
                    <div className="flex flex-wrap gap-2">
                      <Button type="submit" disabled={submitting}>
                        {submitting
                          ? hasCompany
                            ? 'Saving…'
                            : 'Submitting…'
                          : hasCompany
                            ? 'Save Changes'
                            : 'Create Company'}
                      </Button>
                      <Button
                        type="button"
                        variant="secondary"
                        disabled={submitting}
                        onClick={() => {
                          if (handleNavigationAttempt(-1)) {
                            navigate(-1)
                          }
                        }}
                      >
                        Cancel
                      </Button>
                    </div>
                  </div>
                  <div className="space-y-6">
                    <div>
                      <h3 className="text-base font-semibold">Branding</h3>
                      <p className="text-sm text-muted-foreground">
                        Upload a square logo for best results.
                      </p>
                      <Separator className="my-3" />
                      <div className="flex items-start gap-4">
                        <div className="relative">
                          <Avatar className="h-20 w-20 overflow-hidden bg-muted">
                            {logoPreviewUrl ? (
                              <img
                                key={logoPreviewUrl}
                                src={logoPreviewUrl}
                                alt="Logo preview"
                                className="h-full w-full object-cover"
                                crossOrigin="anonymous"
                                referrerPolicy="no-referrer"
                                onError={() => setLogoPreviewUrl(null)}
                              />
                            ) : (
                              <AvatarFallback>
                                {(values.name || 'C').slice(0, 2).toUpperCase()}
                              </AvatarFallback>
                            )}
                          </Avatar>
                          {logoPreviewUrl && (
                            <Button
                              type="button"
                              variant="outline"
                              size="icon"
                              className="absolute -top-2 -right-2 h-6 w-6 rounded-full"
                              aria-label="Remove logo"
                              onClick={clearLogo}
                            >
                              <X className="h-3 w-3" />
                            </Button>
                          )}
                        </div>
                        <div className="flex-1 grid gap-2">
                          <Input
                            id="logo"
                            type="file"
                            accept="image/png,image/jpeg"
                            onChange={(e) => onChange('logoFile', e.target.files?.[0])}
                            aria-describedby="logo-help logo-error"
                          />
                          {errors.logoFile && (
                            <p className="text-sm text-destructive">{errors.logoFile}</p>
                          )}
                          <p id="logo-help" className="text-xs text-muted-foreground">
                            PNG or JPEG up to 256KB.
                          </p>
                        </div>
                      </div>
                    </div>

                    <div>
                      <h3 className="text-base font-semibold">Preview</h3>
                      <p className="text-sm text-muted-foreground">
                        Live summary of what will be saved.
                      </p>
                      <Separator className="my-3" />
                      <div className="grid gap-2 text-sm">
                        <div className="flex items-center justify-between">
                          <span className="text-muted-foreground">Code</span>
                          <span className="font-medium">{values.code || '—'}</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-muted-foreground">Name</span>
                          <span className="font-medium">{values.name || '—'}</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-muted-foreground">Tax code</span>
                          <span className="font-medium">{values.tax_code || '—'}</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-muted-foreground">Address</span>
                          <span
                            className="font-medium truncate max-w-[14rem]"
                            title={values.address}
                          >
                            {values.address || '—'}
                          </span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-muted-foreground">Email</span>
                          <span className="font-medium">{values.contact_email || '—'}</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-muted-foreground">Phone</span>
                          <span className="font-medium">{values.contact_phone || '—'}</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-muted-foreground">Fiscal year start</span>
                          <span className="font-medium">
                            {values.fiscal_year_start
                              ? format(new Date(`${values.fiscal_year_start}T00:00:00`), 'PPP')
                              : '—'}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                </form>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </>
  )
}
