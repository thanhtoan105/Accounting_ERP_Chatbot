import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { refresh } from '../services/auth'

const API_BASE = '/api/v1'

let accessToken: string | null = null
let refreshPromise: Promise<string> | null = null

const COMPANY_ID_STORAGE_KEY = 'activeCompanyId'
const ACCESS_TOKEN_STORAGE_KEY = 'accessToken'

// Initialize token from localStorage on module load
function initTokenFromStorage() {
  const stored = localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)
  if (stored) {
    accessToken = stored
  }
}

// Initialize on module load
initTokenFromStorage()

export function setAccessToken(token: string | null) {
  accessToken = token
  if (token) {
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, token)
  } else {
    localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
  }
}

export function getAccessToken(): string | null {
  // If memory token is null but localStorage has it, restore it
  if (!accessToken) {
    const stored = localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)
    if (stored) {
      accessToken = stored
    }
  }
  return accessToken
}

export function setCompanyId(companyId: number | null) {
  if (companyId !== null && companyId !== undefined) {
    // Store even if companyId is 0 (valid ID)
    localStorage.setItem(COMPANY_ID_STORAGE_KEY, String(companyId))
  } else {
    localStorage.removeItem(COMPANY_ID_STORAGE_KEY)
  }
}

export function getCompanyId(): number | null {
  const stored = localStorage.getItem(COMPANY_ID_STORAGE_KEY)
  if (!stored) {
    return null
  }
  const parsed = Number(stored)
  // Return null for NaN, null, or invalid numbers, but allow 0 as valid ID
  return isNaN(parsed) ? null : parsed
}

const axiosInstance = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

axiosInstance.interceptors.request.use(
  (config) => {
    if (accessToken && config.headers) {
      config.headers.Authorization = `Bearer ${accessToken}`
    }
    // Add X-Company-Id header if available (required for company-scoped operations)
    // Add header if we have a valid companyId (not null/undefined)
    const companyId = getCompanyId()
    if (companyId !== null && companyId !== undefined && config.headers) {
      // Send companyId header for any valid number (backend will validate it)
      config.headers['X-Company-Id'] = String(companyId)
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

axiosInstance.interceptors.response.use(
  (response) => {
    return response
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }

    // Only refresh token on 401 (Unauthorized) - indicates expired/invalid token
    // 403 (Forbidden) indicates insufficient permissions, don't refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      if (!refreshPromise) {
        refreshPromise = refresh()
          .then((response) => {
            setAccessToken(response.accessToken)
            // Sync companyId from refreshed user data
            setCompanyId(response.user.companyId ?? null)
            // Update stored user data (for useAuth hook)
            try {
              localStorage.setItem('user', JSON.stringify(response.user))
            } catch {
              // Ignore localStorage errors
            }
            return response.accessToken
          })
          .catch((err) => {
            setAccessToken(null)
            setCompanyId(null)
            // Clear stored user on refresh failure
            try {
              localStorage.removeItem('user')
            } catch {
              // Ignore localStorage errors
            }
            window.location.href = '/login'
            throw err
          })
          .finally(() => {
            refreshPromise = null
          })
      }

      try {
        const newToken = await refreshPromise
        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newToken}`
        }
        return axiosInstance(originalRequest)
      } catch (err) {
        setAccessToken(null)
        window.location.href = '/login'
        return Promise.reject(err)
      }
    }

    return Promise.reject(error)
  },
)

export default axiosInstance

/**
 * Fetch wrapper with automatic token refresh on 401/403 errors
 * Similar to axios interceptor but for fetch API
 */
let refreshPromiseForFetch: Promise<string> | null = null

interface FetchWithAuthOptions extends RequestInit {
  _retry?: boolean
}

export async function fetchWithAuth(
  url: string,
  options: FetchWithAuthOptions = {},
): Promise<Response> {
  // Get current token and company ID
  const token = getAccessToken()
  const companyId = getCompanyId()

  // Prepare headers
  const headers = new Headers(options.headers || {})
  headers.set('Content-Type', 'application/json')

  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  if (companyId !== null && companyId !== undefined) {
    headers.set('X-Company-Id', String(companyId))
  }

  // Make the request
  let response = await fetch(url, {
    ...options,
    headers,
    credentials: 'include',
  })

  // Only refresh token on 401 (Unauthorized) - indicates expired/invalid token
  // 403 (Forbidden) indicates insufficient permissions, don't refresh
  if (response.status === 401 && !options._retry) {
    // Don't read the response body yet - we'll retry and only read if retry fails
    // This prevents exposing the 403 error to the caller if retry succeeds

    // Refresh token if not already refreshing
    if (!refreshPromiseForFetch) {
      refreshPromiseForFetch = refresh()
        .then((response) => {
          setAccessToken(response.accessToken)
          setCompanyId(response.user.companyId ?? null)
          try {
            localStorage.setItem('user', JSON.stringify(response.user))
          } catch {
            // Ignore localStorage errors
          }
          return response.accessToken
        })
        .catch((err) => {
          setAccessToken(null)
          setCompanyId(null)
          try {
            localStorage.removeItem('user')
          } catch {
            // Ignore localStorage errors
          }
          window.location.href = '/login'
          throw err
        })
        .finally(() => {
          refreshPromiseForFetch = null
        })
    }

    try {
      // Wait for token refresh
      const newToken = await refreshPromiseForFetch

      // Retry original request with new token
      const retryHeaders = new Headers(options.headers || {})
      retryHeaders.set('Content-Type', 'application/json')
      retryHeaders.set('Authorization', `Bearer ${newToken}`)

      if (companyId !== null && companyId !== undefined) {
        retryHeaders.set('X-Company-Id', String(companyId))
      }

      // Mark as retry to prevent infinite loops
      response = await fetchWithAuth(url, {
        ...options,
        headers: retryHeaders,
        _retry: true,
      })

      // If retry succeeded, return the successful response (no error shown to user)
      // Only return error response if retry also failed
    } catch (err) {
      // Refresh failed, redirect to login
      setAccessToken(null)
      window.location.href = '/login'
      throw err
    }
  }

  return response
}
