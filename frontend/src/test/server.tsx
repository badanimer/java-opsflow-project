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
}

type Answer = { status: number; body?: unknown } | 'network-error'
type Handler = Answer | ((call: Call) => Answer)

export class FakeServer {
  readonly calls: Call[] = []
  private readonly handlers = new Map<string, Handler>()

  constructor() {
    vi.stubGlobal('fetch', (input: RequestInfo | URL, init?: RequestInit) => this.handle(String(input), init))
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
      body: init?.body ? JSON.parse(String(init.body)) : undefined,
      authorization: headers.get('Authorization'),
    }
    this.calls.push(call)
    const handler = this.handlers.get(`${call.method} ${call.path}`)
    if (!handler) throw new Error(`The test set no answer for ${call.method} ${call.path}`)
    const answer = typeof handler === 'function' ? handler(call) : handler
    if (answer === 'network-error') throw new TypeError('Failed to fetch')
    if (answer.status === 204) return new Response(null, { status: 204 })
    return new Response(JSON.stringify(answer.body ?? {}), {
      status: answer.status,
      headers: { 'Content-Type': answer.status < 400 ? 'application/json' : 'application/problem+json' },
    })
  }
}

/** An error answer in the format of the API. */
export function problem(status: number, code: string, extra: Record<string, unknown> = {}): Answer {
  return { status, body: { status, code, title: code, detail: 'detail from the server', requestId: 'ddddc8f3b64942ff', ...extra } }
}

export const TOKEN = { token: 'token-of-the-test', expiresAt: '2999-01-01T00:00:00Z' }

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
