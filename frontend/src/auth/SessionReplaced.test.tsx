import { act, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FakeServer, me, openApp, problem, storeToken, TOKEN } from '../test/server'

/**
 * One signed-in device per account (One signed-in device in docs/database/database-design.md): a page
 * whose account signed in elsewhere learns it from the event stream, from any request, or when the
 * person comes back to the tab, and shows a dialog whose one button opens the login page.
 */

const REPLACED_NOTICE = 'You were signed out because your account signed in on another device. If this was not you, reset your password.'

async function signedOutDialog() {
  return screen.findByRole('dialog', { name: 'You have been signed out' })
}

describe('a page whose account signed in on another device', () => {
  it('shows the dialog at once when the stream says so, and Sign in again opens the login page with the reason', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', me(['VIEWER']))
    const user = openApp('/')
    await screen.findByText('Products are coming soon')
    await waitFor(() => expect(server.sessionEvents.opened).toBe(1))

    act(() => server.sessionEvents.send('session-replaced'))

    const dialog = await signedOutDialog()
    expect(within(dialog).getByText(/Your account signed in on another device/)).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: /Cancel|Close/ })).not.toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Sign in again' }))
    expect(await screen.findByText(REPLACED_NOTICE)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(localStorage.getItem('opsflow.session')).toBeNull()
  })

  it('shows the dialog when any request is answered SESSION_REPLACED', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', me(['ADMIN'])).on('GET /users', problem(401, 'SESSION_REPLACED'))
    openApp('/users')
    expect(await signedOutDialog()).toBeInTheDocument()
  })

  it('shows the dialog on opening the application with a token of an ended login', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', problem(401, 'SESSION_REPLACED'))
    openApp('/')
    expect(await signedOutDialog()).toBeInTheDocument()
  })

  it('asks for the account when the person comes back to the tab, and shows the dialog when the login ended', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', me(['VIEWER']))
    openApp('/')
    await screen.findByText('Products are coming soon')
    const before = server.callsTo('GET /auth/me').length

    server.on('GET /auth/me', problem(401, 'SESSION_REPLACED'))
    // The first check waits a few seconds after the page opened, so the clock of the check is moved on.
    const realNow = Date.now
    Date.now = () => realNow() + 10_000
    try {
      act(() => {
        window.dispatchEvent(new Event('focus'))
      })
      expect(await signedOutDialog()).toBeInTheDocument()
    } finally {
      Date.now = realNow
    }
    expect(server.callsTo('GET /auth/me').length).toBe(before + 1)
  })

  it('shows no dialog when another tab of this browser signed in again, and loads the new account', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', me(['VIEWER'], { username: 'alice' }))
    openApp('/')
    await screen.findByText('Products are coming soon')
    await waitFor(() => expect(server.sessionEvents.opened).toBe(1))

    // Another tab signed in as bob: the stored token is now bob's, and the server ends alice's login.
    localStorage.setItem('opsflow.session', JSON.stringify({ ...TOKEN, token: 'token-of-another-tab' }))
    server.on('GET /auth/me', me(['VIEWER'], { username: 'bob' }))
    act(() => server.sessionEvents.send('session-replaced'))

    expect(await screen.findByText('bob')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'You have been signed out' })).not.toBeInTheDocument()
  })

  it('opens the stream again when the server closes it', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', me(['VIEWER']))
    openApp('/')
    await waitFor(() => expect(server.sessionEvents.opened).toBe(1))

    act(() => server.sessionEvents.close())

    await waitFor(() => expect(server.sessionEvents.opened).toBe(2), { timeout: 4000 })
  })

  it('keeps the stream open on the pending page too', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', me([]))
    openApp('/pending')
    await screen.findByText('Waiting for an administrator to assign permissions')
    await waitFor(() => expect(server.sessionEvents.opened).toBe(1))
    act(() => server.sessionEvents.send('session-replaced'))
    expect(await signedOutDialog()).toBeInTheDocument()
  })
})
