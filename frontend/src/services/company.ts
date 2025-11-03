export type Company = {
  id: number
  code: string
  name: string
  tax_code: string
  address: string
  logoUrl?: string | null
}

const API_BASE = '/api/v1'

async function handleJsonResponse<T>(res: Response): Promise<T> {
  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined
  if (!res.ok) {
    throw data || { error: { code: 'UNKNOWN', message: 'Request failed' } }
  }
  return data as T
}

export async function listCompanies(): Promise<Company[]> {
  // Use axios to get automatic Authorization and X-Company-Id headers
  const { default: axiosInstance } = await import('../utils/axios')
  const response = await axiosInstance.get('/companies')

  // Handle different response formats
  if (Array.isArray(response.data)) {
    return response.data as Company[]
  }
  if (response.data?.data && Array.isArray(response.data.data)) {
    return response.data.data as Company[]
  }
  return []
}

export async function createCompany(
  input: Omit<Company, 'id' | 'logoUrl'> & { logoFile?: File | null },
): Promise<Company> {
  // Import getCompanyId to add X-Company-Id header if available
  const { getCompanyId } = await import('../utils/axios')
  const companyId = getCompanyId()

  const body: Record<string, unknown> = {
    code: input.code,
    name: input.name,
    // Backend expects camelCase (taxCode). Keep UI field as tax_code and map here.
    taxCode: input.tax_code,
    address: input.address,
    logoUrl: null,
  }

  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  // Add X-Company-Id header if available (though for creation, user might not have company yet)
  if (companyId !== null && companyId !== undefined) {
    headers['X-Company-Id'] = String(companyId)
  }

  const res = await fetch(`${API_BASE}/companies`, {
    method: 'POST',
    headers,
    credentials: 'include',
    body: JSON.stringify(body),
  })
  const anyPayload = await handleJsonResponse<Company | { data: Company }>(res)
  // Accept either raw entity or { data: entity }
  if (anyPayload && typeof anyPayload === 'object' && 'data' in anyPayload) {
    return (anyPayload as { data: Company }).data
  }
  return anyPayload as Company
}

export async function setActiveCompany(companyId: number): Promise<void> {
  // This assumes backend infers session from cookie and header
  await fetch(`${API_BASE}/_context/company`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Company-Id': String(companyId) },
    credentials: 'include',
    body: JSON.stringify({ companyId }),
  })
}
