import { loadSession } from '../auth/session'

/**
 * The one place that calls the backend (docs/api/api-design.md).
 *
 * - Every path is under /api/v1. In development the dev server passes /api on to the backend.
 * - A request carries the login token unless it says `auth: false` (login, sign-up, and the email
 *   codes are for people who are not logged in).
 * - An error answer is the Problem Details body of the API. It becomes an {@link ApiError}, and a page
 *   decides what to show from its `code`, never from its `detail`.
 * - When a request that carried a token is answered UNAUTHENTICATED or ACCOUNT_DISABLED, the login is
 *   over. The listener set by the login state is told, and it clears the token and opens the login
 *   page. The same codes from the login endpoint itself are ordinary answers, because that request
 *   carries no token.
 * - ACCOUNT_NO_ROLE keeps the token and tells the login state that the account must wait for a role.
 * - SESSION_REPLACED: the account signed in on another device. The login state shows a dialog that says
 *   so, and the person goes to the login page from there.
 */

const BASE = '/api/v1'

export type ResultCode =
  | 'VALIDATION_FAILED'
  | 'CODE_INVALID'
  | 'UNAUTHENTICATED'
  | 'SESSION_REPLACED'
  | 'FORBIDDEN'
  | 'ACCOUNT_DISABLED'
  | 'ACCOUNT_NO_ROLE'
  | 'NOT_FOUND'
  | 'USERNAME_TAKEN'
  | 'EMAIL_TAKEN'
  | 'LOGIN_LOCKED'
  | 'TOO_MANY_REQUESTS'
  | 'BUSY'
  | 'INTERNAL_ERROR'
  | (string & {})

export interface FieldError {
  field: string
  message: string
}

/** A refusal of the API. */
export class ApiError extends Error {
  readonly status: number
  readonly code: ResultCode
  /** The id of the request, to quote in a report. */
  readonly requestId?: string
  /** The refused fields of a VALIDATION_FAILED. */
  readonly errors: FieldError[]
  /** The number of a refused attempt to order, which the person may name in a ticket. */
  readonly orderNo?: string
  /** The other fields of the error body that belong to the result, such as `items` of ITEM_CHANGED. */
  readonly extras: Record<string, unknown>

  constructor(status: number, code: ResultCode, detail?: string, requestId?: string, errors: FieldError[] = [], orderNo?: string,
    extras: Record<string, unknown> = {}) {
    super(detail ?? code)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.requestId = requestId
    this.errors = errors
    this.orderNo = orderNo
    this.extras = extras
  }
}

/** No answer came: the server is down or the connection is broken. */
export class NetworkError extends Error {
  constructor() {
    super('The server could not be reached')
    this.name = 'NetworkError'
  }
}

export type SessionProblem = 'expired' | 'disabled' | 'noRole' | 'replaced'
let sessionProblemListener: ((reason: SessionProblem) => void) | null = null

/** Called by the login state, once. Passing null removes the listener. */
export function onSessionProblem(listener: ((reason: SessionProblem) => void) | null): void {
  sessionProblemListener = listener
}

interface Options {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  /** false for the endpoints that need no login. The default is true. */
  auth?: boolean
  /** Headers of the request, such as `Idempotency-Key`. */
  headers?: Record<string, string>
}

export async function api<T = void>(path: string, { method = 'GET', body, auth = true, headers: extra }: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...extra }
  // A file goes as multipart: the browser sets that type itself, with the boundary of the parts.
  const multipart = body instanceof FormData
  if (body !== undefined && !multipart) headers['Content-Type'] = 'application/json'
  const startedAt = new Date()
  const token = auth ? loadSession(startedAt)?.token : undefined
  if (token) headers['Authorization'] = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : multipart ? body : JSON.stringify(body) })
  } catch {
    throw new NetworkError()
  }

  if (response.ok) {
    if (response.status === 204) return undefined as T
    return (await response.json()) as T
  }

  const error = await readError(response)
  if (token && token === loadSession(startedAt)?.token) {
    if (error.code === 'UNAUTHENTICATED') sessionProblemListener?.('expired')
    if (error.code === 'ACCOUNT_DISABLED') sessionProblemListener?.('disabled')
    if (error.code === 'ACCOUNT_NO_ROLE') sessionProblemListener?.('noRole')
    if (error.code === 'SESSION_REPLACED') sessionProblemListener?.('replaced')
  }
  throw error
}

/**
 * Reads a stream of Server-Sent Events with the login token, and calls `onEvent` with the name of each
 * event. `fetch` is used and not `EventSource`, because `EventSource` cannot send the `Authorization`
 * header. The promise ends when the server closes the stream or `signal` aborts it. A refusal is handled
 * like the refusal of any request (the login state is told about an ended login) and is thrown.
 */
export async function readEventStream(path: string, onEvent: (name: string) => void, signal: AbortSignal): Promise<void> {
  const startedAt = new Date()
  const token = loadSession(startedAt)?.token
  const headers: Record<string, string> = { Accept: 'text/event-stream' }
  if (token) headers['Authorization'] = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(BASE + path, { headers, signal })
  } catch {
    throw new NetworkError()
  }
  if (!response.ok || !response.body) {
    const error = await readError(response)
    if (token && token === loadSession(startedAt)?.token) {
      if (error.code === 'UNAUTHENTICATED') sessionProblemListener?.('expired')
      if (error.code === 'ACCOUNT_DISABLED') sessionProblemListener?.('disabled')
      if (error.code === 'SESSION_REPLACED') sessionProblemListener?.('replaced')
    }
    throw error
  }

  const reader = response.body.getReader()
  const onAbort = () => void reader.cancel().catch(() => {})
  signal.addEventListener('abort', onAbort)
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = ''
  try {
    for (;;) {
      let chunk: ReadableStreamReadResult<Uint8Array>
      try {
        chunk = await reader.read()
      } catch {
        if (signal.aborted) return
        throw new NetworkError()
      }
      if (chunk.done) return
      buffer += decoder.decode(chunk.value, { stream: true })
      let end: number
      while ((end = buffer.indexOf('\n')) !== -1) {
        const line = buffer.slice(0, end).replace(/\r$/, '')
        buffer = buffer.slice(end + 1)
        // A blank line ends an event; a line that starts with ':' is a comment that keeps the stream open.
        if (line === '') {
          if (eventName) onEvent(eventName)
          eventName = ''
        } else if (line.startsWith('event:')) {
          eventName = line.slice('event:'.length).trim()
        }
      }
    }
  } finally {
    signal.removeEventListener('abort', onAbort)
  }
}

async function readError(response: Response): Promise<ApiError> {
  const requestId = response.headers.get('X-Request-Id') ?? undefined
  try {
    const problem = (await response.json()) as {
      code?: string
      detail?: string
      requestId?: string
      errors?: FieldError[]
      orderNo?: string
      [extra: string]: unknown
    }
    if (typeof problem.code === 'string') {
      return new ApiError(
        response.status,
        problem.code,
        problem.detail,
        problem.requestId ?? requestId,
        Array.isArray(problem.errors) ? problem.errors : [],
        typeof problem.orderNo === 'string' ? problem.orderNo : undefined,
        problem,
      )
    }
  } catch {
    // Not the error body of the API: for example a proxy answered because the backend is down.
  }
  // A gateway error of the dev server or of a proxy means that the backend did not answer.
  if (response.status === 502 || response.status === 503 || response.status === 504) throw new NetworkError()
  return new ApiError(response.status, 'INTERNAL_ERROR', undefined, requestId)
}
