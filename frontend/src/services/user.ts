import type { User } from './auth'
import { fetchWithAuth } from '../utils/axios'
import type { Role } from '../utils/roles'

const API_BASE = '/api/v1'

async function handleJsonResponse<T>(res: Response): Promise<T> {
  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined
  if (!res.ok) {
    // Don't throw for 401/403 - these are handled by fetchWithAuth and retried automatically
    // Only throw if the retry also failed (status is still 401/403 after retry)
    if (res.status === 401 || res.status === 403) {
      throw {
        code: res.status === 401 ? 'UNAUTHORIZED' : 'FORBIDDEN',
        message: data?.error?.message || data?.message || 'Authentication failed',
        status: res.status,
      }
    }
    throw data?.error || { code: 'UNKNOWN', message: 'Request failed' }
  }
  return data as T
}

export interface PaginatedUsersResponse {
  data: User[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface GetAllUsersParams {
  role?: string
  status?: string
  search?: string
  page?: number
  size?: number
}

export async function getAllUsers(params?: GetAllUsersParams): Promise<PaginatedUsersResponse> {
  const queryParams = new URLSearchParams()
  if (params?.role) queryParams.append('role', params.role)
  if (params?.status) queryParams.append('status', params.status)
  if (params?.search) queryParams.append('search', params.search)
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size !== undefined) queryParams.append('size', String(params.size))

  const url = `${API_BASE}/users${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  return await handleJsonResponse<PaginatedUsersResponse>(res)
}

// Legacy method for backward compatibility
export async function getUsers(): Promise<User[]> {
  const response = await getAllUsers({ size: 1000 })
  return response.data
}

export async function getUserById(id: number): Promise<User> {
  const res = await fetchWithAuth(`${API_BASE}/users/${id}`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<{ data: User }>(res)
  return payload.data
}

export interface CreateUserRequest {
  email: string
  password: string
  fullName: string
  role?: string
}

export async function createUser(user: CreateUserRequest): Promise<User> {
  const res = await fetchWithAuth(`${API_BASE}/users`, {
    method: 'POST',
    body: JSON.stringify(user),
  })
  const payload = await handleJsonResponse<{ data: User }>(res)
  return payload.data
}

export interface UpdateUserRequest {
  fullName?: string
  role?: string
  status?: string
}

export async function updateUser(userId: number, user: UpdateUserRequest): Promise<User> {
  const res = await fetchWithAuth(`${API_BASE}/users/${userId}`, {
    method: 'PUT',
    body: JSON.stringify(user),
  })
  const payload = await handleJsonResponse<{ data: User }>(res)
  return payload.data
}

export async function deactivateUser(userId: number): Promise<User> {
  const res = await fetchWithAuth(`${API_BASE}/users/${userId}/deactivate`, {
    method: 'PUT',
  })
  const payload = await handleJsonResponse<{ data: User }>(res)
  return payload.data
}

export async function activateUser(userId: number): Promise<User> {
  const res = await fetchWithAuth(`${API_BASE}/users/${userId}/activate`, {
    method: 'PUT',
  })
  const payload = await handleJsonResponse<{ data: User }>(res)
  return payload.data
}

export async function resetPasswordByAdmin(userId: number): Promise<{ message: string }> {
  const res = await fetchWithAuth(`${API_BASE}/users/${userId}/reset-password`, {
    method: 'POST',
  })
  const payload = await handleJsonResponse<{ message: string }>(res)
  return payload
}

export async function getCurrentUserProfile(): Promise<User> {
  const res = await fetchWithAuth(`${API_BASE}/users/me`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<{ data: User }>(res)
  return payload.data
}

export interface UpdateProfileRequest {
  fullName: string
}

export async function updateProfile(profile: UpdateProfileRequest): Promise<User> {
  const res = await fetchWithAuth(`${API_BASE}/users/me`, {
    method: 'PUT',
    body: JSON.stringify(profile),
  })
  const payload = await handleJsonResponse<{ data: User }>(res)
  return payload.data
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export async function changePassword(
  passwordData: ChangePasswordRequest,
): Promise<{ message: string }> {
  const res = await fetchWithAuth(`${API_BASE}/users/me/change-password`, {
    method: 'POST',
    body: JSON.stringify(passwordData),
  })
  const payload = await handleJsonResponse<{ message: string }>(res)
  return payload
}

export async function updateUserRole(userId: number, role: Role): Promise<User> {
  const res = await fetchWithAuth(`${API_BASE}/users/${userId}/role`, {
    method: 'PUT',
    body: JSON.stringify({ role }),
  })
  const payload = await handleJsonResponse<{ data: User }>(res)
  return payload.data
}
