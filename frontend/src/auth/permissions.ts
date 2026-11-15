import type { Account } from '../api/auth'

/**
 * The permission names the frontend reads from `permissions` of `GET /auth/me`. They are the names of
 * the enum `Permission` of the backend. A control is shown by its permission and never by a role, so a
 * new role needs no change in the frontend (docs/iterations/api-v1.4.md). The server checks every
 * request again: hiding a control is only for the person, not a protection.
 */
export type Permission =
  | 'PRODUCT_VIEW'
  | 'STOCK_VIEW'
  | 'TICKET_VIEW'
  | 'TICKET_VIEW_ALL'
  | 'AUDIT_VIEW'
  | 'USER_VIEW'
  | 'USER_MANAGE'
  | 'USER_MANAGE_PROTECTED'
  | 'ROLE_ASSIGN'
  | 'ROLE_ASSIGN_PROTECTED'

/** Whether the account holds the permission, through any one of its roles. */
export function can(account: Account | null, permission: Permission): boolean {
  return account?.permissions.includes(permission) ?? false
}
