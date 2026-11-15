import { api } from './client'
import type { Page, PageRequest } from './paging'
import { pageParams } from './paging'

/** The endpoints of Users in docs/api/api-design.md. */

export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface ListedUser {
  id: number
  username: string
  status: UserStatus
  /** The role codes, in the order of the codes. Empty for a user who waits for a role. */
  roles: string[]
}

/** The value of the role filter for the button No role. */
export const NO_ROLE = 'NONE'

export interface UserFilter {
  /** A part of the username. Blank is no search. */
  search: string
  /** The chosen role buttons: role codes, and {@link NO_ROLE}. Empty is no button. */
  roles: string[]
}

export function listUsers(filter: UserFilter, page: PageRequest): Promise<Page<ListedUser>> {
  const params = new URLSearchParams()
  const search = filter.search.trim()
  if (search) params.append('search', search)
  for (const role of filter.roles) params.append('roles', role)
  pageParams(params, page)
  const query = params.toString()
  return api<Page<ListedUser>>(query ? `/users?${query}` : '/users')
}

/** Replaces the roles with the whole new list. `expectedRoles` is the list the person saw. */
export function replaceRoles(id: number, roles: string[], expectedRoles: string[]): Promise<{ id: number; username: string; roles: string[] }> {
  return api(`/users/${id}/roles`, { method: 'PUT', body: { roles, expectedRoles } })
}

export function disableUser(id: number): Promise<{ id: number; username: string; status: UserStatus }> {
  return api(`/users/${id}/disable`, { method: 'POST' })
}

export function enableUser(id: number): Promise<{ id: number; username: string; status: UserStatus }> {
  return api(`/users/${id}/enable`, { method: 'POST' })
}

/** The deletion is soft and cannot be undone: the user disappears from every list. */
export function deleteUser(id: number): Promise<void> {
  return api(`/users/${id}`, { method: 'DELETE' })
}
