const BASE_PATH = '/api/v1'

export type FieldError = { field: string; message: string }

/** A failure reported by the API in its standard format, or a failure to reach it (status 0). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]

  constructor(status: number, code: string, title: string, fieldErrors: FieldError[] = []) {
    super(title)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

async function send(path: string, init?: RequestInit): Promise<Response> {
  let response: Response
  try {
    response = await fetch(`${BASE_PATH}${path}`, init)
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'The service could not be reached')
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  return response
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body: { code?: string; title?: string; errors?: FieldError[] } = await response.json()
    return new ApiError(
      response.status,
      body.code ?? 'UNKNOWN',
      body.title ?? 'Something went wrong',
      body.errors ?? [],
    )
  } catch {
    return new ApiError(response.status, 'UNKNOWN', 'Something went wrong')
  }
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await send(path)
  return response.json() as Promise<T>
}

/**
 * Fetches a CSRF token before each write. The token changes at login and logout, and when a
 * session expires, so a fresh one avoids a stale token and needs no retry logic.
 */
async function write<T>(method: string, path: string, body?: unknown): Promise<T> {
  const csrf = await apiGet<{ headerName: string; token: string }>('/auth/csrf')
  const response = await send(path, {
    method,
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: method === 'DELETE' ? undefined : JSON.stringify(body ?? {}),
  })
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return write<T>('POST', path, body)
}

export function apiPut<T>(path: string, body: unknown): Promise<T> {
  return write<T>('PUT', path, body)
}

export function apiDelete(path: string): Promise<void> {
  return write<void>('DELETE', path)
}

export type Page<T> = { items: T[]; page: number; size: number; totalItems: number }

/** The message to show a user for any failed request. */
export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong'
}
