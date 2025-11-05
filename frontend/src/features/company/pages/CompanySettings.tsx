import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { createCompany, getCompanySettings, updateCompanySettings, type Company } from '@/services/company'
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
          setExistingCompany({ logoUrl: (settings as { logoUrl?: string | null }).logoUrl || null } as unknown as Company)
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
        setValues({ code: '', name: '', tax_code: '', address: '', contact_email: '', contact_phone: '', fiscal_year_start: '', logoFile: undefined })
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

  return (
    <Card className="max-w-4xl">
      <CardHeader>
        <CardTitle className="flex items-center gap-3">
          {hasCompany ? 'Edit Company' : 'Create Company'}
          <span className="text-xs font-medium text-muted-foreground">
            {hasCompany ? 'Updating existing organization' : 'First-time setup'}
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent>
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
                <p className="text-sm text-muted-foreground">Basic details about your organization.</p>
                <Separator className="my-3" />
                <div className="grid gap-4">
                  <div className="grid gap-2">
                    <Label htmlFor="code">Code <span className="text-destructive">*</span></Label>
                    <Input
                      id="code"
                      placeholder="e.g. ACME-01"
                      value={values.code}
                      onChange={(e) => onChange('code', e.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, ''))}
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
                      <p id="code-error" className="text-sm text-destructive">{errors.code}</p>
                    )}
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="name">Name <span className="text-destructive">*</span></Label>
                    <Input
                      id="name"
                      placeholder="Company legal name"
                      value={values.name}
                      onChange={(e) => onChange('name', e.target.value)}
                      onBlur={() => onBlur('name')}
                      aria-invalid={Boolean(errors.name)}
                      aria-describedby="name-error"
                    />
                    {touched.name && errors.name && <p id="name-error" className="text-sm text-destructive">{errors.name}</p>}
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="tax_code">Tax Code <span className="text-destructive">*</span></Label>
                    <Input
                      id="tax_code"
                      placeholder="10 digits"
                      value={values.tax_code}
                      onChange={(e) => onChange('tax_code', e.target.value.replace(/[^\d]/g, '').slice(0, 10))}
                      onBlur={() => onBlur('tax_code')}
                      inputMode="numeric"
                      pattern="^\d{10}$"
                      aria-invalid={Boolean(errors.tax_code)}
                      aria-describedby="tax-help tax-error"
                      required
                    />
                    <p id="tax-help" className="text-sm text-muted-foreground">Exactly 10 digits (numbers only)</p>
                    {touched.tax_code && errors.tax_code && (
                      <p id="tax-error" className="text-sm text-destructive">{errors.tax_code}</p>
                    )}
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="address">Address <span className="text-destructive">*</span></Label>
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
                    {touched.address && errors.address && <p id="address-error" className="text-sm text-destructive">{errors.address}</p>}
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="contact_email"> Contact Email <span className="text-destructive">*</span></Label>
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
                      <p id="contact_email-error" className="text-sm text-destructive">{errors.contact_email}</p>
                    )}
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="contact_phone">Contact Phone <span className="text-destructive">*</span></Label>
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
                          const next = current.startsWith('+') ? dial + current.replace(/^\+\d+\s?/, '') : dial
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
                      <p id="contact_phone-error" className="text-sm text-destructive">{errors.contact_phone}</p>
                    )}
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="fiscal_year_start">Fiscal Year Start <span className="text-destructive">*</span></Label>
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
                          selected={values.fiscal_year_start ? new Date(`${values.fiscal_year_start}T00:00:00`) : undefined}
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
                      <p id="fiscal_year_start-error" className="text-sm text-destructive">{errors.fiscal_year_start}</p>
                    )}
                  </div>
                </div>
              </div>
              {errors.form && <Alert className="text-destructive text-sm" role="alert">{errors.form}</Alert>}
              {success && <Alert className="text-green-600 text-sm" role="status">{success}</Alert>}
              <div className="flex flex-wrap gap-2">
                <Button type="submit" disabled={submitting}>
                  {submitting ? (hasCompany ? 'Saving…' : 'Submitting…') : (hasCompany ? 'Save Changes' : 'Create Company')}
                </Button>
                <Button type="button" variant="secondary" disabled={submitting} onClick={() => navigate(-1)}>
                  Cancel
                </Button>
              </div>
            </div>
            <div className="space-y-6">
              <div>
                <h3 className="text-base font-semibold">Branding</h3>
                <p className="text-sm text-muted-foreground">Upload a square logo for best results.</p>
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
                      <AvatarFallback>{(values.name || 'C').slice(0, 2).toUpperCase()}</AvatarFallback>
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
                    {errors.logoFile && <p className="text-sm text-destructive">{errors.logoFile}</p>}
                    <p id="logo-help" className="text-xs text-muted-foreground">PNG or JPEG up to 256KB.</p>
                  </div>
                </div>
              </div>

              <div>
                <h3 className="text-base font-semibold">Preview</h3>
                <p className="text-sm text-muted-foreground">Live summary of what will be saved.</p>
                <Separator className="my-3" />
                <div className="grid gap-2 text-sm">
                  <div className="flex items-center justify-between"><span className="text-muted-foreground">Code</span><span className="font-medium">{values.code || '—'}</span></div>
                  <div className="flex items-center justify-between"><span className="text-muted-foreground">Name</span><span className="font-medium">{values.name || '—'}</span></div>
                  <div className="flex items-center justify-between"><span className="text-muted-foreground">Tax code</span><span className="font-medium">{values.tax_code || '—'}</span></div>
                  <div className="flex items-center justify-between"><span className="text-muted-foreground">Address</span><span className="font-medium truncate max-w-[14rem]" title={values.address}>{values.address || '—'}</span></div>
                  <div className="flex items-center justify-between"><span className="text-muted-foreground">Email</span><span className="font-medium">{values.contact_email || '—'}</span></div>
                  <div className="flex items-center justify-between"><span className="text-muted-foreground">Phone</span><span className="font-medium">{values.contact_phone || '—'}</span></div>
                  <div className="flex items-center justify-between"><span className="text-muted-foreground">Fiscal year start</span><span className="font-medium">{values.fiscal_year_start ? format(new Date(`${values.fiscal_year_start}T00:00:00`), 'PPP') : '—'}</span></div>
                </div>
              </div>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  )
}

