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
 */

const BASE = '/api/v1'

export type ResultCode =
  | 'VALIDATION_FAILED'
  | 'CODE_INVALID'
  | 'UNAUTHENTICATED'
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

  constructor(status: number, code: ResultCode, detail?: string, requestId?: string, errors: FieldError[] = []) {
    super(detail ?? code)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.requestId = requestId
    this.errors = errors
  }
}

/** No answer came: the server is down or the connection is broken. */
export class NetworkError extends Error {
  constructor() {
    super('The server could not be reached')
    this.name = 'NetworkError'
  }
}

export type SessionProblem = 'expired' | 'disabled' | 'noRole'
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
}

export async function api<T = void>(path: string, { method = 'GET', body, auth = true }: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const startedAt = new Date()
  const token = auth ? loadSession(startedAt)?.token : undefined
  if (token) headers['Authorization'] = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
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
  }
  throw error
}

async function readError(response: Response): Promise<ApiError> {
  const requestId = response.headers.get('X-Request-Id') ?? undefined
  try {
    const problem = (await response.json()) as {
      code?: string
      detail?: string
      requestId?: string
      errors?: FieldError[]
    }
    if (typeof problem.code === 'string') {
      return new ApiError(
        response.status,
        problem.code,
        problem.detail,
        problem.requestId ?? requestId,
        Array.isArray(problem.errors) ? problem.errors : [],
      )
    }
  } catch {
    // Not the error body of the API: for example a proxy answered because the backend is down.
  }
  // A gateway error of the dev server or of a proxy means that the backend did not answer.
  if (response.status === 502 || response.status === 503 || response.status === 504) throw new NetworkError()
  return new ApiError(response.status, 'INTERNAL_ERROR', undefined, requestId)
}
