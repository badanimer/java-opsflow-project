import { api } from './client'

/** The endpoints of Authentication in docs/api/api-design.md. */

export interface TokenResponse {
  token: string
  expiresAt: string
}

export interface Account {
  id: number
  username: string
  /** The role codes. Empty while the account waits for an administrator to assign one. */
  roles: string[]
  /**
   * What the user may do, as the permission names of the server (for example `ORDER_PLACE`): the
   * permissions of all the roles together. Show a control by its permission, never by a role, so that
   * a new role needs no change here. The server checks every request again.
   */
  permissions: string[]
}

export interface SignUpResponse extends TokenResponse {
  id: number
  username: string
}

export type EmailCodePurpose = 'REGISTER' | 'RESET_PASSWORD'

export function login(username: string, password: string): Promise<TokenResponse> {
  return api<TokenResponse>('/auth/login', { method: 'POST', body: { username, password }, auth: false })
}

export function fetchAccount(): Promise<Account> {
  return api<Account>('/auth/me')
}

export function requestEmailCode(email: string, purpose: EmailCodePurpose): Promise<void> {
  return api('/auth/email-codes', { method: 'POST', body: { email, purpose }, auth: false })
}

/** The answer logs the new user in. */
export function signUp(username: string, email: string, password: string, code: string): Promise<SignUpResponse> {
  return api<SignUpResponse>('/auth/sign-up', { method: 'POST', body: { username, email, password, code }, auth: false })
}

export function resetPassword(email: string, code: string, newPassword: string): Promise<void> {
  return api('/auth/password-reset', { method: 'POST', body: { email, code, newPassword }, auth: false })
}
