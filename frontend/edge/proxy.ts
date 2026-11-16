/**
 * Cloudflare Pages Functions proxy (DECISIONS.md, D-033). The browser sees one origin, as with nginx:
 * the app's own paths for the API and Google sign-in are forwarded to the backend on Render, so the
 * session cookie stays first-party and SameSite=Lax, and no CORS is needed.
 *
 * The backend refuses requests without PROXY_SECRET, so its own public address can't be used to skip
 * this proxy. The visitor's address goes in X-Client-IP, because the backend otherwise sees only
 * Cloudflare's address and would rate limit every visitor together.
 */

export interface ProxyEnv {
  /** The backend's origin, such as https://family-health-backend.onrender.com. */
  BACKEND_URL?: string
  /** The same value as the backend's PROXY_SECRET. Set as an encrypted variable in Pages. */
  PROXY_SECRET?: string
}

export interface PagesContext {
  request: Request
  env: ProxyEnv
}

export const SECRET_HEADER = 'X-Proxy-Secret'
export const CLIENT_ADDRESS_HEADER = 'X-Client-IP'

export async function proxy(request: Request, env: ProxyEnv): Promise<Response> {
  if (!env.BACKEND_URL || !env.PROXY_SECRET) {
    return new Response(
      JSON.stringify({ type: 'about:blank', title: 'Service Unavailable', status: 503, code: 'PROXY_NOT_CONFIGURED' }),
      { status: 503, headers: { 'Content-Type': 'application/problem+json' } },
    )
  }
  const incoming = new URL(request.url)
  const target = new URL(incoming.pathname + incoming.search, env.BACKEND_URL)

  const headers = new Headers(request.headers)
  // The backend's address decides the Host. Anything a visitor sent in these headers is replaced.
  headers.delete('Host')
  headers.set(SECRET_HEADER, env.PROXY_SECRET)
  const visitor = request.headers.get('CF-Connecting-IP')
  if (visitor) {
    headers.set(CLIENT_ADDRESS_HEADER, visitor)
  } else {
    headers.delete(CLIENT_ADDRESS_HEADER)
  }
  headers.set('X-Forwarded-Proto', 'https')

  const hasBody = request.method !== 'GET' && request.method !== 'HEAD'
  return fetch(target.toString(), {
    method: request.method,
    headers,
    // Read whole: uploads are at most about 11 MB, and a buffer works the same in every runtime.
    body: hasBody ? await request.arrayBuffer() : undefined,
    // Sign-in redirects go back to the browser unchanged, never followed here.
    redirect: 'manual',
  })
}

/** The handler each route file exports as `onRequest`. */
export const handle = (context: PagesContext): Promise<Response> => proxy(context.request, context.env)
