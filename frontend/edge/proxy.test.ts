// @vitest-environment node
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CLIENT_ADDRESS_HEADER, proxy, SECRET_HEADER } from './proxy'

const env = { BACKEND_URL: 'https://backend.example.test', PROXY_SECRET: 'secret-value' }

function stubBackend(response = new Response('{}', { status: 200 })) {
  const fetchMock = vi.fn<(input: string, init: RequestInit) => Promise<Response>>(async () => response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function sentHeaders(fetchMock: ReturnType<typeof stubBackend>) {
  return new Headers(fetchMock.mock.calls[0][1].headers)
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('proxy', () => {
  it('forwards the path and query to the backend with the secret and the visitor address', async () => {
    const fetchMock = stubBackend()
    const request = new Request('https://app.example.test/api/v1/profiles?page=2', {
      headers: { 'CF-Connecting-IP': '198.51.100.7', Cookie: 'SESSION=abc', Host: 'app.example.test' },
    })

    await proxy(request, env)

    expect(fetchMock.mock.calls[0][0]).toBe('https://backend.example.test/api/v1/profiles?page=2')
    const headers = sentHeaders(fetchMock)
    expect(headers.get(SECRET_HEADER)).toBe('secret-value')
    expect(headers.get(CLIENT_ADDRESS_HEADER)).toBe('198.51.100.7')
    expect(headers.get('Cookie')).toBe('SESSION=abc')
    expect(headers.get('Host')).toBeNull()
    expect(fetchMock.mock.calls[0][1].redirect).toBe('manual')
  })

  it('replaces a secret or visitor address sent by the browser', async () => {
    const fetchMock = stubBackend()
    const request = new Request('https://app.example.test/api/v1/auth/csrf', {
      headers: { [SECRET_HEADER]: 'guess', [CLIENT_ADDRESS_HEADER]: '203.0.113.9' },
    })

    await proxy(request, env)

    const headers = sentHeaders(fetchMock)
    expect(headers.get(SECRET_HEADER)).toBe('secret-value')
    // No CF-Connecting-IP, so no visitor address at all rather than the browser's claim.
    expect(headers.get(CLIENT_ADDRESS_HEADER)).toBeNull()
  })

  it('forwards a request body unchanged', async () => {
    const fetchMock = stubBackend()
    const request = new Request('https://app.example.test/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"email":"a@example.test"}',
    })

    await proxy(request, env)

    const init = fetchMock.mock.calls[0][1]
    expect(init.method).toBe('POST')
    expect(new TextDecoder().decode(init.body as ArrayBuffer)).toBe('{"email":"a@example.test"}')
  })

  it('returns the backend response with every Set-Cookie header and redirect intact', async () => {
    const headers = new Headers({ Location: 'https://accounts.google.com/o/oauth2/v2/auth' })
    headers.append('Set-Cookie', 'SESSION=one; Path=/; Secure; HttpOnly; SameSite=Lax')
    headers.append('Set-Cookie', 'OTHER=two; Path=/')
    stubBackend(new Response(null, { status: 302, headers }))

    const response = await proxy(new Request('https://app.example.test/oauth2/authorization/google'), env)

    expect(response.status).toBe(302)
    expect(response.headers.get('Location')).toBe('https://accounts.google.com/o/oauth2/v2/auth')
    expect(response.headers.getSetCookie()).toHaveLength(2)
  })

  it('answers 503 without calling anything when the proxy is not configured', async () => {
    const fetchMock = stubBackend()

    const response = await proxy(new Request('https://app.example.test/api/v1/auth/me'), { BACKEND_URL: env.BACKEND_URL })

    expect(response.status).toBe(503)
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
