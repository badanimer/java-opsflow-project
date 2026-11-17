import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FakeServer, me, openApp, problem, storeToken } from '../test/server'
import { setScreenWidth } from '../test/screen'

/**
 * The frame of the application: the navigation holds only the pages the user's permissions open
 * (What Each Role Sees in docs/frontend/page-design.md), a page the permissions do not open says Not
 * allowed, and an unknown address says Page not found.
 */

const ALL = ['Products', 'Stock', 'Tickets', 'Pool', 'In progress', 'Resolved', 'Archive', 'Cancelled', 'Users', 'Audit log']

async function navigationOf(roles: string[]): Promise<string[]> {
  storeToken()
  new FakeServer().on('GET /auth/me', me(roles))
  openApp('/')
  const nav = await screen.findByRole('menu', { name: 'Main navigation' })
  return ALL.filter((label) => within(nav).queryByText(label) !== null)
}

describe('the navigation', () => {
  it('shows an administrator every page', async () => {
    expect(await navigationOf(['ADMIN'])).toEqual(ALL)
  })

  it('shows a manager every page', async () => {
    expect(await navigationOf(['MANAGER'])).toEqual(ALL)
  })

  it('shows an operator no archive, no users, and no audit log', async () => {
    expect(await navigationOf(['OPERATOR'])).toEqual(['Products', 'Stock', 'Tickets', 'Pool', 'In progress', 'Resolved', 'Cancelled'])
  })

  it('shows a viewer the archive, but no users and no audit log', async () => {
    expect(await navigationOf(['VIEWER'])).toEqual(['Products', 'Stock', 'Tickets', 'Pool', 'In progress', 'Resolved', 'Archive', 'Cancelled'])
  })

  it('shows a user with two roles what either of them opens', async () => {
    expect(await navigationOf(['OPERATOR', 'VIEWER'])).toEqual(['Products', 'Stock', 'Tickets', 'Pool', 'In progress', 'Resolved', 'Archive', 'Cancelled'])
  })

  it('decides by the permissions, not by the role names', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', me(['SUPPORT'], { permissions: ['PRODUCT_VIEW', 'USER_VIEW'] }))
    openApp('/')
    const nav = await screen.findByRole('menu', { name: 'Main navigation' })
    expect(ALL.filter((label) => within(nav).queryByText(label) !== null)).toEqual(['Products', 'Users'])
  })

  it('offers every page of the map, the ticket screens and the audit log included, and opens them', async () => {
    storeToken()
    new FakeServer()
      .on('GET /auth/me', me(['ADMIN']))
      .on('GET /tickets?status=OPEN', { status: 200, body: { items: [], nextCursor: null, prevCursor: null } })
    const user = openApp('/')
    const nav = await screen.findByRole('menu', { name: 'Main navigation' })
    for (const name of ['Stock', 'Pool', 'Archive', 'Users', 'Audit log']) {
      expect(within(nav).getByText(name).closest('li')).not.toHaveAttribute('aria-disabled', 'true')
    }
    await user.click(within(nav).getByText('Pool'))
    expect(await screen.findByRole('heading', { name: 'Pool' })).toBeInTheDocument()
    await user.click(within(nav).getByText('Audit log'))
    expect(await screen.findByRole('heading', { name: 'Audit log' })).toBeInTheDocument()
  })

  it('opens the product page as the home page', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', me(['VIEWER']))
    openApp('/')
    expect(await screen.findByText('No products on sale')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Products' })).toBeInTheDocument()
  })
})

describe('pages that cannot be opened', () => {
  it('says Not allowed when an operator types the address of the user page, and asks the server nothing', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', me(['OPERATOR']))
    const user = openApp('/users')
    expect(await screen.findByText('You do not have permission to open this page.')).toBeInTheDocument()
    expect(server.callsTo('GET /users')).toHaveLength(0)
    await user.click(screen.getByRole('button', { name: 'Go to Products' }))
    expect(await screen.findByText('No products on sale')).toBeInTheDocument()
  })

  it('says Page not found for an unknown address', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', me(['VIEWER']))
    openApp('/no-such-page')
    expect(await screen.findByText('We could not find this page.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Go to Products' })).toBeInTheDocument()
  })

  it('sends a visitor with no login from an unknown address to the login page', async () => {
    new FakeServer()
    openApp('/no-such-page')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})

describe('loading the account', () => {
  it('shows the reference of a fault of the server and tries again on request', async () => {
    storeToken()
    let fail = true
    new FakeServer().on('GET /auth/me', () => (fail ? problem(500, 'INTERNAL_ERROR') : me(['VIEWER'])))
    const user = openApp('/')
    expect(await screen.findByText(/report this reference: ddddc8f3b64942ff/)).toBeInTheDocument()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    fail = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('No products on sale')).toBeInTheDocument()
  })
})

describe('a small screen', () => {
  it('puts the navigation in a drawer that the Menu button opens', async () => {
    setScreenWidth(390)
    storeToken()
    new FakeServer().on('GET /auth/me', me(['OPERATOR']))
    const user = openApp('/')
    await screen.findByText('No products on sale')
    expect(screen.queryByRole('menu', { name: 'Main navigation' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Menu/ }))
    const nav = await screen.findByRole('menu', { name: 'Main navigation' })
    expect(within(nav).queryByText('Users')).not.toBeInTheDocument()
    expect(within(nav).getByText('Pool')).toBeInTheDocument()
  })
})
