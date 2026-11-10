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

/** Which sign-in methods are switched on. Google only when its keys are set on the server. */
export async function fetchProviders(): Promise<{ google: boolean }> {
  try {
    return await apiGet<{ google: boolean }>('/auth/providers')
  } catch {
    return { google: false }
  }
}

/** Always succeeds the same way, whether or not the email has an account. */
export function requestPasswordReset(email: string): Promise<void> {
  return apiPost<void>('/auth/password-reset/request', { email })
}

export function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  return apiPost<void>('/auth/password-reset/confirm', { token, newPassword })
}
