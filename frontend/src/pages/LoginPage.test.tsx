import { screen, waitFor } from '@testing-library/react'
import type { UserEvent } from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { loadSession } from '../auth/session'
import { FakeServer, TOKEN, openApp, problem, storeToken } from '../test/server'
import { TEXT } from '../text'

async function signIn(user: UserEvent, username = 'alice', password = 'correct-horse-1') {
  await user.type(await screen.findByLabelText('Username'), username)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('the login page', () => {
  it('logs a user with a role in and opens the application', async () => {
    const server = new FakeServer()
      .on('POST /auth/login', { status: 200, body: TOKEN })
      .on('GET /auth/me', { status: 200, body: { id: 7, username: 'alice', roles: ['OPERATOR'] } })
    const user = openApp('/login')

    await signIn(user)

    expect(await screen.findByText('You are signed in')).toBeInTheDocument()
    expect(server.callsTo('POST /auth/login')[0].body).toEqual({ username: 'alice', password: 'correct-horse-1' })
    // The login itself carries no token, and the request that follows carries the new one.
    expect(server.callsTo('POST /auth/login')[0].authorization).toBeNull()
    expect(server.callsTo('GET /auth/me')[0].authorization).toBe(`Bearer ${TOKEN.token}`)
    expect(loadSession()?.token).toBe(TOKEN.token)
  })

  it('sends a user with no role to the pending page', async () => {
    new FakeServer()
      .on('POST /auth/login', { status: 200, body: TOKEN })
      .on('GET /auth/me', { status: 200, body: { id: 7, username: 'alice', roles: [] } })
    const user = openApp('/login')

    await signIn(user)

    expect(await screen.findByText(TEXT.pendingTitle)).toBeInTheDocument()
  })

  it.each([
    ['a wrong username or password', problem(401, 'UNAUTHENTICATED'), TEXT.wrongCredentials],
    ['a locked account', problem(429, 'LOGIN_LOCKED', { lockedUntil: '2026-11-13T03:32:00Z' }), TEXT.locked],
    ['a disabled account', problem(403, 'ACCOUNT_DISABLED'), TEXT.disabled],
    ['a fault of the server', problem(500, 'INTERNAL_ERROR'), TEXT.somethingWrong('ddddc8f3b64942ff')],
    ['a busy server', problem(503, 'BUSY'), TEXT.somethingWrong('ddddc8f3b64942ff')],
    ['no answer from the server', 'network-error' as const, TEXT.cannotReach],
  ])('shows its message for %s, and stays on the page without a token', async (_case, answer, message) => {
    new FakeServer().on('POST /auth/login', answer)
    const user = openApp('/login')

    await signIn(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
    expect(loadSession()).toBeNull()
  })

  it('does not show the time a lock ends, and never the detail of the server', async () => {
    new FakeServer().on('POST /auth/login', problem(429, 'LOGIN_LOCKED', { lockedUntil: '2026-11-13T03:32:00Z' }))
    const user = openApp('/login')

    await signIn(user)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Please try again in a minute.')
    expect(alert).not.toHaveTextContent('03:32')
    expect(alert).not.toHaveTextContent('detail from the server')
  })

  it('asks for the fields that are empty and sends nothing', async () => {
    const server = new FakeServer()
    const user = openApp('/login')

    await user.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText(TEXT.usernameRequired)).toBeInTheDocument()
    expect(screen.getByText(TEXT.passwordRequired)).toBeInTheDocument()
    expect(server.calls).toHaveLength(0)
  })

  it('opens the application when a valid token is already stored', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', { status: 200, body: { id: 7, username: 'alice', roles: ['VIEWER'] } })
    openApp('/login')

    expect(await screen.findByText('You are signed in')).toBeInTheDocument()
  })
})

describe('what ends a login', () => {
  it('opens the login page with a notice when the stored token is refused', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', problem(401, 'UNAUTHENTICATED'))
    openApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent(TEXT.sessionExpired)
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(loadSession()).toBeNull()
  })

  it('opens the login page with the disabled message when the account was disabled after login', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', problem(403, 'ACCOUNT_DISABLED'))
    openApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent(TEXT.disabled)
    expect(loadSession()).toBeNull()
  })

  it('opens the login page without asking the server when there is no token', async () => {
    const server = new FakeServer()
    openApp('/')

    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(server.calls).toHaveLength(0)
  })

  it('says that the server cannot be reached, keeps the token, and tries again on request', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', 'network-error')
    const user = openApp('/')

    expect(await screen.findByText(TEXT.cannotReach)).toBeInTheDocument()
    expect(loadSession()?.token).toBe(TOKEN.token)

    server.on('GET /auth/me', { status: 200, body: { id: 7, username: 'alice', roles: ['ADMIN'] } })
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('You are signed in')).toBeInTheDocument()
  })

  it('signs out: the token is removed and the login page opens', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', { status: 200, body: { id: 7, username: 'alice', roles: ['ADMIN'] } })
    const user = openApp('/')

    await user.click(await screen.findByRole('button', { name: TEXT.signOut }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument())
    expect(loadSession()).toBeNull()
  })
})
