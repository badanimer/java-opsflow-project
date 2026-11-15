import { createContext, useContext } from 'react'
import type { Account } from '../api/auth'
import type { Session } from './session'

/**
 * - `loading`: the stored token is being checked against the server.
 * - `signedOut`: there is no usable token.
 * - `signedIn`: the server knows the user. `account.roles` may be empty: the user then waits for a role.
 * - `unreachable`: there is a token, but the account could not be loaded (no answer, or a fault of the
 *   server), so nothing is known yet.
 */
export type AuthStatus = 'loading' | 'signedOut' | 'signedIn' | 'unreachable'

export interface AuthValue {
  status: AuthStatus
  account: Account | null
  /** Why the account could not be loaded, while the status is `unreachable`: no answer, or a fault. */
  startupError: unknown
  /** Stores the token of a login or a sign-up and loads the account behind it. */
  signIn: (session: Session) => Promise<Account>
  signOut: () => void
  /** Asks the server for the account again, for example to see whether a role was assigned. */
  refresh: () => Promise<Account>
  /** Tries again after `unreachable`. */
  retry: () => void
}

export const AuthContext = createContext<AuthValue | null>(null)

export function useAuth(): AuthValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
