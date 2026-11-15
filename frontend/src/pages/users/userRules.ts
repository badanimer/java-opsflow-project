import type { Account } from '../../api/auth'
import { ApiError, NetworkError } from '../../api/client'
import { NO_ROLE, type ListedUser } from '../../api/users'
import { can } from '../../auth/permissions'
import { isProtectedRole, ROLES } from '../../lib/roles'
import { TEXT } from '../../text'
import { generalFailure, type Notice } from '../../lib/failure'

/**
 * What a viewer may do on the user page, from the permissions of `GET /auth/me` (Authority over roles
 * and accounts in docs/database/database-design.md). A control the viewer may not use is not shown. The
 * server decides again on every request, under its locks.
 */

/** Whether the viewer is shown the users who hold a protected role: the same rule as the server's list. */
export function seesProtected(account: Account | null): boolean {
  return can(account, 'USER_MANAGE_PROTECTED') || can(account, 'ROLE_ASSIGN_PROTECTED')
}

/** The role buttons of the filter: the roles the viewer can see holders of, and No role. */
export function filterOptions(account: Account | null): { value: string; label: string }[] {
  const roles = ROLES.filter((role) => !role.protected || seesProtected(account))
  return [...roles.map((role) => ({ value: role.code, label: role.label })), { value: NO_ROLE, label: TEXT.users.noRole }]
}

/** The roles the viewer may give or take away. */
export function assignableRoles(account: Account | null) {
  return ROLES.filter((role) => !role.protected || can(account, 'ROLE_ASSIGN_PROTECTED'))
}

export interface RowActions {
  changeRoles: boolean
  /** Disable on an active user, Enable on a disabled one. */
  changeStatus: boolean
  delete: boolean
}

export function rowActions(account: Account | null, user: ListedUser): RowActions {
  const self = account?.id === user.id
  const protectedTarget = user.roles.some(isProtectedRole)
  const manage = !self && can(account, 'USER_MANAGE') && (!protectedTarget || can(account, 'USER_MANAGE_PROTECTED'))
  return {
    changeRoles: !self && can(account, 'ROLE_ASSIGN') && (!protectedTarget || can(account, 'ROLE_ASSIGN_PROTECTED')),
    changeStatus: manage,
    delete: manage,
  }
}

/** Whether the Actions column is there at all: not for a viewer who may change nothing. */
export function hasActionColumn(account: Account | null): boolean {
  return can(account, 'ROLE_ASSIGN') || can(account, 'USER_MANAGE')
}

/**
 * Where a refusal of a change is shown.
 * - `page`: the user is not as the viewer saw it, is gone, or the viewer lost the right. The dialog
 *   closes, the list is loaded again, and the notice is shown above it.
 * - `dialog`: the dialog stays open with the notice, and the person can change the choice or cancel.
 */
export type Outcome = { where: 'page' | 'dialog'; notice: Notice; reloadAccount?: boolean }

export function changeFailure(error: unknown): Outcome {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'USER_CHANGED':
        return { where: 'page', notice: { type: 'warning', message: TEXT.users.userChanged } }
      case 'NOT_FOUND':
        return { where: 'page', notice: { type: 'warning', message: TEXT.users.userGone } }
      case 'FORBIDDEN':
        return { where: 'page', notice: { type: 'error', message: TEXT.users.noLongerAllowed }, reloadAccount: true }
      case 'LAST_ADMIN':
        return { where: 'dialog', notice: { type: 'error', message: TEXT.users.lastAdmin } }
      case 'VALIDATION_FAILED':
        return { where: 'dialog', notice: { type: 'error', message: TEXT.users.rolesRefused } }
    }
  }
  if (error instanceof NetworkError) return { where: 'dialog', notice: { type: 'error', message: TEXT.cannotReach } }
  return { where: 'dialog', notice: generalFailure(error) }
}
