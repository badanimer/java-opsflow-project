import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { App } from '../App'

/**
 * The backend of the tests: answers that a test sets for a method and a path, and a record of every
 * request the page made. No test talks to a real server.
 */

export interface Call {
  method: string
  path: string
  body: unknown
  authorization: string | null
  idempotencyKey: string | null
}

type Answer = { status: number; body?: unknown } | { status: 200; stream: ReadableStream<Uint8Array> } | 'network-error'

/**
 * A stream of Server-Sent Events that a test controls: `send` puts an event on it, `close` ends it.
 * Every FakeServer answers `GET /auth/session-events` with one that stays open and sends nothing, so a
 * test that is not about it sees no difference; `sessionEvents` is that stream.
 */
export class EventStream {
  private controller: ReadableStreamDefaultController<Uint8Array> | null = null
  private readonly encoder = new TextEncoder()
  opened = 0

  answer(): Answer {
    this.opened++
    const stream = new ReadableStream<Uint8Array>({
      start: (controller) => {
        this.controller = controller
        controller.enqueue(this.encoder.encode(':open\n\n'))
      },
    })
    return { status: 200, stream }
  }

  send(event: string): void {
    this.controller?.enqueue(this.encoder.encode(`event:${event}\ndata:{}\n\n`))
  }

  close(): void {
    this.controller?.close()
    this.controller = null
  }
}
type Handler = Answer | ((call: Call) => Answer)

export class FakeServer {
  readonly calls: Call[] = []
  private readonly handlers = new Map<string, Handler>()

  readonly sessionEvents = new EventStream()

  constructor() {
    vi.stubGlobal('fetch', (input: RequestInfo | URL, init?: RequestInit) => this.handle(String(input), init))
    this.on('GET /auth/session-events', () => this.sessionEvents.answer())
    // The home page is the product page: a test that is not about it sees an empty one.
    this.on('GET /products', { status: 200, body: { items: [], nextCursor: null, prevCursor: null } })
  }

  /** Sets the answer for a request such as 'POST /auth/login'. The path is the one under /api/v1. */
  on(request: string, handler: Handler): this {
    this.handlers.set(request, handler)
    return this
  }

  /** The requests made so far for a method and a path. */
  callsTo(request: string): Call[] {
    return this.calls.filter((call) => `${call.method} ${call.path}` === request)
  }

  private async handle(url: string, init?: RequestInit): Promise<Response> {
    const headers = new Headers(init?.headers)
    const call: Call = {
      method: init?.method ?? 'GET',
      path: url.replace('/api/v1', ''),
      // A multipart upload is kept as the FormData it is; every other body is JSON.
      body: init?.body instanceof FormData ? init.body : init?.body ? JSON.parse(String(init.body)) : undefined,
      authorization: headers.get('Authorization'),
      idempotencyKey: headers.get('Idempotency-Key'),
    }
    this.calls.push(call)
    const handler = this.handlers.get(`${call.method} ${call.path}`)
    if (!handler) throw new Error(`The test set no answer for ${call.method} ${call.path}`)
    const answer = typeof handler === 'function' ? handler(call) : handler
    if (answer === 'network-error') throw new TypeError('Failed to fetch')
    if ('stream' in answer) return new Response(answer.stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
    if (answer.status === 204) return new Response(null, { status: 204 })
    return new Response(JSON.stringify((answer as { body?: unknown }).body ?? {}), {
      status: answer.status,
      headers: { 'Content-Type': answer.status < 400 ? 'application/json' : 'application/problem+json' },
    })
  }
}

/** An error answer in the format of the API. */
export function problem(status: number, code: string, extra: Record<string, unknown> = {}): Answer {
  return { status, body: { status, code, title: code, detail: 'detail from the server', requestId: 'ddddc8f3b64942ff', ...extra } }
}

/**
 * The permissions each role gives on the server, copied from the enum `Role` of the backend for the
 * tests only. The application itself never has this table: it reads `permissions` from `GET /auth/me`.
 */
const SHARED = ['PRODUCT_VIEW', 'STOCK_VIEW', 'DELETED_ITEMS_VIEW', 'TICKET_VIEW']
const STAFF = [...SHARED, 'PRODUCT_LIST', 'PRODUCT_DELIST', 'ITEM_MANAGE', 'ITEM_DELETE', 'STOCK_IN', 'STOCK_WRITE_OFF', 'STOCK_CORRECT', 'ORDER_PLACE',
  'TICKET_VIEW_ALL', 'TICKET_SEARCH', 'TICKET_CREATE', 'TICKET_TAKE', 'TICKET_ASSIGN', 'TICKET_RELEASE', 'TICKET_RESOLVE', 'TICKET_CANCEL', 'TICKET_ARCHIVE',
  'AUDIT_VIEW', 'USER_VIEW', 'USER_MANAGE', 'ROLE_ASSIGN']
export const ROLE_PERMISSIONS: Record<string, string[]> = {
  ADMIN: [...STAFF, 'USER_MANAGE_PROTECTED', 'ROLE_ASSIGN_PROTECTED'],
  MANAGER: STAFF,
  OPERATOR: [...SHARED, 'STOCK_IN', 'STOCK_WRITE_OFF', 'STOCK_CORRECT', 'ORDER_PLACE', 'TICKET_CREATE', 'TICKET_TAKE', 'TICKET_RELEASE', 'TICKET_RESOLVE', 'TICKET_CANCEL'],
  VIEWER: [...SHARED, 'TICKET_VIEW_ALL', 'TICKET_SEARCH'],
}

/** The answer of `GET /auth/me` for a user with these roles, with the permissions of all of them. */
export function me(roles: string[], user: { id?: number; username?: string; permissions?: string[] } = {}): Answer {
  const permissions = user.permissions ?? [...new Set(roles.flatMap((role) => ROLE_PERMISSIONS[role] ?? []))]
  return { status: 200, body: { id: user.id ?? 7, username: user.username ?? 'alice', roles, permissions } }
}

export const TOKEN ={ token: 'token-of-the-test', expiresAt: '2999-01-01T00:00:00Z' }

/** Stores a token as a login would, so that the application starts logged in. */
export function storeToken(): void {
  localStorage.setItem('opsflow.session', JSON.stringify(TOKEN))
}

/** Opens the whole application at a path. */
export function openApp(path: string) {
  const user = userEvent.setup()
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
  return user
}
