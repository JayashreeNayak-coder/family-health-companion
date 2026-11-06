import { vi } from 'vitest'

export type Reply = { status: number; body?: unknown } | Error

export function problem(status: number, code: string, title: string, errors?: unknown[]): Reply {
  return { status, body: { type: 'about:blank', status, code, title, errors } }
}

export function page(items: unknown[]): Reply {
  return { status: 200, body: { items, page: 0, size: 100, totalItems: items.length } }
}

/**
 * Stubs fetch by "METHOD path". Each route answers with its replies in order, and repeats the
 * last one. The CSRF route is always answered, like the real API. The signed-in home page's two
 * lists default to empty.
 */
export function stubApi(routes: Record<string, Reply | Reply[]>) {
  const remaining = new Map(
    Object.entries({
      'GET /families?size=100': page([]),
      'GET /profiles?size=100': page([]),
      ...routes,
    }).map(([route, replies]) => [route, [replies].flat()]),
  )
  const stub = vi.fn(async (url: string, init?: RequestInit) => {
    const route = `${init?.method ?? 'GET'} ${url.replace('/api/v1', '')}`
    if (route === 'GET /auth/csrf') {
      return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf-1' }))
    }
    const replies = remaining.get(route) ?? defaultReplies(route)
    if (!replies) {
      throw new Error(`Unexpected request: ${route}`)
    }
    const reply = replies.length > 1 ? replies.shift()! : replies[0]
    if (reply instanceof Error) {
      throw reply
    }
    return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
      status: reply.status,
    })
  })
  vi.stubGlobal('fetch', stub)
  return stub
}

/** Record lists also load their attachments, and record forms the documents to cite. Both default to none. */
function defaultReplies(route: string): Reply[] | undefined {
  if (/^GET \/profiles\/[^/]+\/attachments\?recordType=[A-Z_]+$/.test(route)) {
    return [{ status: 200, body: [] }]
  }
  if (/^GET \/profiles\/[^/]+\/documents\?size=100$/.test(route)) {
    return [page([])]
  }
  return undefined
}

export function requestBody(stub: ReturnType<typeof stubApi>, method: string, path: string) {
  const call = stub.mock.calls.find(
    ([url, init]) => url === `/api/v1${path}` && (init?.method ?? 'GET') === method,
  )
  return JSON.parse(call![1]!.body as string)
}
