/**
 * Where the login token is kept: the localStorage of the browser, so a refresh keeps the user logged
 * in until the token expires (12 hours after the login). See Frontend Stack And Design Files in
 * docs/frontend/page-design.md for the choice and its cost.
 *
 * Only the token and its expiry are stored. The user and the roles are asked from the server at each
 * start, because they can change at any time.
 */

const KEY = 'opsflow.session'

export interface Session {
  token: string
  /** ISO 8601, as the server sent it */
  expiresAt: string
}

export function saveSession(session: Session): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(session))
  } catch {
    // Storage can be full or blocked. The user is then logged in until the page is closed.
  }
}

export function clearSession(): void {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // Nothing to clear.
  }
}

/** The stored session, or null when there is none, when it is not readable, or when it has expired. */
export function loadSession(now: Date = new Date()): Session | null {
  let raw: string | null
  try {
    raw = localStorage.getItem(KEY)
  } catch {
    return null
  }
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<Session>
    if (typeof parsed.token !== 'string' || typeof parsed.expiresAt !== 'string') throw new Error('shape')
    const expires = Date.parse(parsed.expiresAt)
    if (Number.isNaN(expires) || expires <= now.getTime()) throw new Error('expired')
    return { token: parsed.token, expiresAt: parsed.expiresAt }
  } catch {
    clearSession()
    return null
  }
}
