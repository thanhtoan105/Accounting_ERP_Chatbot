import { useState, useEffect } from 'react'
import { login as loginService, logout as logoutService, refresh } from '../services/auth'
import type { User } from '../services/auth'
import { setAccessToken, getAccessToken, setCompanyId } from '../utils/axios'

export type AuthState = {
  user: User | null
  isAuthenticated: boolean
  loading: boolean
}

const USER_STORAGE_KEY = 'user'

// Helper to store/retrieve user from localStorage
function storeUser(user: User | null) {
  if (user) {
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user))
  } else {
    localStorage.removeItem(USER_STORAGE_KEY)
  }
}

function getStoredUser(): User | null {
  const stored = localStorage.getItem(USER_STORAGE_KEY)
  if (stored) {
    try {
      return JSON.parse(stored) as User
    } catch {
      return null
    }
  }
  return null
}

export function useAuth() {
  // Initialize with stored user immediately to avoid flash of 403
  const storedUser = getStoredUser()
  const token = getAccessToken()
  const hasStoredData = storedUser && token
  
  const [state, setState] = useState<AuthState>({
    user: storedUser, // Use stored user immediately
    isAuthenticated: hasStoredData,
    loading: hasStoredData, // Only loading if we have stored data (need to refresh)
  })

  // Initialize auth state from localStorage and refresh if needed
  useEffect(() => {
    const initializeAuth = async () => {
      const currentToken = getAccessToken()
      const currentStoredUser = getStoredUser()

      if (currentToken) {
        // If we have a token, try to refresh to get latest user data
        // This also validates the token and gets fresh user info
        try {
          const response = await refresh()
          setAccessToken(response.accessToken)
          setCompanyId(response.user.companyId ?? null)
          storeUser(response.user)
          setState({
            user: response.user,
            isAuthenticated: true,
            loading: false,
          })
        } catch {
          // Token is invalid or expired, clear everything
          setAccessToken(null)
          setCompanyId(null)
          storeUser(null)
          setState({
            user: null,
            isAuthenticated: false,
            loading: false,
          })
        }
      } else if (currentStoredUser) {
        // No token but we have stored user - clear it (invalid state)
        storeUser(null)
        setState({
          user: null,
          isAuthenticated: false,
          loading: false,
        })
      } else {
        // No token, no stored user - not authenticated
        setState({
          user: null,
          isAuthenticated: false,
          loading: false,
        })
      }
    }

    // Always initialize to refresh token and validate
    initializeAuth()
  }, [])

  const login = async (email: string, password: string, rememberMe?: boolean) => {
    const response = await loginService({ email, password, rememberMe })
    setAccessToken(response.accessToken)
    // Sync companyId to localStorage
    setCompanyId(response.user.companyId ?? null)
    // Store user in localStorage
    storeUser(response.user)
    setState({
      user: response.user,
      isAuthenticated: true,
      loading: false,
    })
    return response
  }

  const logout = async () => {
    try {
      await logoutService()
    } catch (err) {
      // Log error but continue with local cleanup
      console.warn('Logout API call failed:', err)
    } finally {
      // Always clear local state even if API call fails
      setAccessToken(null)
      // Clear companyId from localStorage on logout
      setCompanyId(null)
      // Clear user from localStorage on logout
      storeUser(null)
      setState({
        user: null,
        isAuthenticated: false,
        loading: false,
      })
    }
  }

  return {
    ...state,
    login,
    logout,
  }
}
