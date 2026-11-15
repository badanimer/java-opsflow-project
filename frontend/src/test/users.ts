import { screen, within } from '@testing-library/react'
import type { ListedUser } from '../api/users'
import { FakeServer, me, openApp, storeToken } from './server'

/** The users of the user page tests. alexadmin (id 1) is the administrator who looks at the page. */
export const ALEX: ListedUser = { id: 1, username: 'alexadmin', status: 'ACTIVE', roles: ['ADMIN'] }
export const ADAM: ListedUser = { id: 2, username: 'adam', status: 'ACTIVE', roles: ['ADMIN'] }
export const ALICE: ListedUser = { id: 3, username: 'alice', status: 'ACTIVE', roles: ['OPERATOR', 'VIEWER'] }
export const BOB: ListedUser = { id: 4, username: 'bob', status: 'DISABLED', roles: ['OPERATOR'] }
export const CHLOE: ListedUser = { id: 5, username: 'chloe', status: 'ACTIVE', roles: [] }
export const EVA: ListedUser = { id: 6, username: 'eva', status: 'ACTIVE', roles: ['MANAGER'] }

export function page(items: ListedUser[], nextCursor: string | null = null, prevCursor: string | null = null) {
  return { status: 200, body: { items, nextCursor, prevCursor } }
}

/** The part of the page that holds the list, so that the username in the top bar is not found. */
export function list(): HTMLElement {
  return screen.getByRole('region', { name: 'User list' })
}

/** Waits until the list shows a user. */
export async function findInList(username: string): Promise<HTMLElement> {
  const region = await screen.findByRole('region', { name: 'User list' })
  return within(region).findByText(username)
}

/** Opens the user page as a viewer with these roles (or these permissions), with the first page given. */
export async function openUsers(
  roles: string[],
  firstPage: ListedUser[],
  viewer: { id?: number; username?: string; permissions?: string[] } = {},
) {
  storeToken()
  const server = new FakeServer()
    .on('GET /auth/me', me(roles, { id: viewer.id ?? 1, username: viewer.username ?? 'alexadmin', permissions: viewer.permissions }))
    .on('GET /users', page(firstPage))
  const user = openApp('/users')
  await screen.findByRole('searchbox', { name: 'Search by username' })
  if (firstPage.length > 0) await findInList(firstPage[0].username)
  return { server, user }
}

/** The row of a user: a table row, or an item of the stacked list on a small screen. */
export function row(username: string): HTMLElement {
  const cell = within(list()).getByText(username)
  const item = cell.closest('tr') ?? cell.closest('li')
  if (!item) throw new Error(`No row for ${username}`)
  return item as HTMLElement
}

/** The names of the buttons in the row of a user. */
export function actionsIn(username: string): string[] {
  return within(row(username))
    .queryAllByRole('button')
    .map((button) => button.textContent ?? '')
}
