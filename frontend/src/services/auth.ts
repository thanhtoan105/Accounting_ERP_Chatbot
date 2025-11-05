export type User = {
  id: number
  email: string
  fullName: string
  role: string
  status?: string
  companyId?: number | null
  createdAt?: string
  updatedAt?: string
}

export type AuthResponse = {
  accessToken: string
  user: User
}

const API_BASE = '/api/v1'

async function handleJsonResponse<T>(res: Response): Promise<T> {
  const text = await res.text()
  let data: any = undefined

  try {
    data = text ? JSON.parse(text) : undefined
  } catch (e) {
    // If parsing fails, create a structured error
    if (!res.ok) {
      throw {
        error: {
          code: res.status === 401 ? 'UNAUTHORIZED' : res.status === 403 ? 'FORBIDDEN' : 'UNKNOWN',
          message: `Request failed with status ${res.status}`,
        },
      }
    }
  }

  if (!res.ok) {
    // Ensure error structure is consistent
    const errorData = data || {
      error: {
        code:
          res.status === 401
            ? 'UNAUTHORIZED'
            : res.status === 403
              ? 'FORBIDDEN'
              : res.status === 400
                ? 'BAD_REQUEST'
                : 'UNKNOWN',
        message:
          data?.error?.message || data?.message || `Request failed with status ${res.status}`,
      },
    }
    throw errorData
  }
  return data as T
}

export async function login(input: {
  email: string
  password: string
  rememberMe?: boolean
}): Promise<AuthResponse> {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(input),
  })
  const payload = await handleJsonResponse<{ data: AuthResponse }>(res)
  return payload.data
}

export async function refresh(): Promise<AuthResponse> {
  const res = await fetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  })
  const payload = await handleJsonResponse<{ data: AuthResponse }>(res)
  return payload.data
}

export async function logout(): Promise<void> {
  await fetch(`${API_BASE}/auth/logout`, {
    method: 'POST',
    credentials: 'include',
  })
}

export async function forgotPassword(email: string): Promise<void> {
  const res = await fetch(`${API_BASE}/auth/forgot-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email }),
  })
  await handleJsonResponse<{ data: { message: string } }>(res)
}

export async function resetPassword(token: string, password: string): Promise<void> {
  const res = await fetch(`${API_BASE}/auth/reset-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ token, password }),
  })

  // Use handleJsonResponse to properly handle errors
  await handleJsonResponse<{ data: { message: string } }>(res)
}
