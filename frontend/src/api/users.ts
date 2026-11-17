import { api } from './client'
import type { Page, PageRequest } from './paging'
import { pageParams } from './paging'

/** The endpoints of Users in docs/api/api-design.md. */

/** `DELETED` is a soft-deleted user, listed only when the status button Deleted is chosen. */
export type UserStatus = 'ACTIVE' | 'DISABLED' | 'DELETED'

export interface ListedUser {
  id: number
  username: string
  status: UserStatus
  /** The role codes, in the order of the codes. Empty for a user who waits for a role. */
  roles: string[]
  /** The tickets assigned to the user that are in progress now: a disabling or a deletion returns them to the pool. */
  ticketsInProgress?: number
}

/** The value of the role filter for the button No role. */
export const NO_ROLE = 'NONE'

export interface UserFilter {
  /** A part of the username. Blank is no search. */
  search: string
  /** The chosen role buttons: role codes, and {@link NO_ROLE}. Empty is no button. */
  roles: string[]
  /** The chosen status buttons: ACTIVE, DISABLED, DELETED. Empty lists the users who are not deleted. */
  statuses: string[]
}

export function listUsers(filter: UserFilter, page: PageRequest): Promise<Page<ListedUser>> {
  const params = new URLSearchParams()
  const search = filter.search.trim()
  if (search) params.append('search', search)
  for (const role of filter.roles) params.append('roles', role)
  for (const status of filter.statuses) params.append('status', status)
  pageParams(params, page)
  const query = params.toString()
  return api<Page<ListedUser>>(query ? `/users?${query}` : '/users')
}

/**
 * Replaces the roles with the whole new list. `expectedRoles` is the list the person saw. `ticketsReturned`
 * is the number of tickets in progress that went back to the pool, because the user can no longer take
 * tickets.
 */
export function replaceRoles(id: number, roles: string[], expectedRoles: string[]): Promise<{ id: number; username: string; roles: string[]; ticketsReturned?: number }> {
  return api(`/users/${id}/roles`, { method: 'PUT', body: { roles, expectedRoles } })
}

/** How many tickets in progress a change to these roles would return to the pool. It changes nothing. */
export function roleChangeEffect(id: number, roles: string[]): Promise<{ ticketsReturned: number }> {
  const params = new URLSearchParams()
  for (const role of roles) params.append('roles', role)
  const query = params.toString()
  return api(query ? `/users/${id}/role-change?${query}` : `/users/${id}/role-change`)
}

export function disableUser(id: number): Promise<{ id: number; username: string; status: UserStatus; ticketsReturned?: number }> {
  return api(`/users/${id}/disable`, { method: 'POST' })
}

export function enableUser(id: number): Promise<{ id: number; username: string; status: UserStatus }> {
  return api(`/users/${id}/enable`, { method: 'POST' })
}

/** The deletion is soft and cannot be undone: the user disappears from every list. */
export function deleteUser(id: number): Promise<void> {
  return api(`/users/${id}`, { method: 'DELETE' })
}
