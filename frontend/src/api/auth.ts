import { apiGet, apiPost, ApiError } from './client'

export type Account = { id: string; email: string; displayName: string }

export type Registration = { email: string; password: string; displayName: string }

export type Credentials = { email: string; password: string }

/** The signed-in account, or null when signed out. Other failures are thrown. */
export async function fetchCurrentAccount(): Promise<Account | null> {
  try {
    return await apiGet<Account>('/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

export function register(registration: Registration): Promise<Account> {
  return apiPost<Account>('/auth/register', registration)
}

export function login(credentials: Credentials): Promise<Account> {
  return apiPost<Account>('/auth/login', credentials)
}

export function logout(): Promise<void> {
  return apiPost<void>('/auth/logout')
}
