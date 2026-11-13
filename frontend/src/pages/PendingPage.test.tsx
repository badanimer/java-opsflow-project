import { act, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { api } from '../api/client'
import { loadSession } from '../auth/session'
import { FakeServer, openApp, problem, storeToken } from '../test/server'
import { TEXT } from '../text'

const NO_ROLE = { status: 200, body: { id: 7, username: 'alice', roles: [] } }
const WITH_ROLE = { status: 200, body: { id: 7, username: 'alice', roles: ['OPERATOR'] } }

describe('the pending page', () => {
  it('is the only page of a user with no role, whatever page was asked for', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', NO_ROLE)
    openApp('/')

    expect(await screen.findByText(TEXT.pendingTitle)).toBeInTheDocument()
    expect(screen.getByText('alice')).toBeInTheDocument()
    expect(screen.queryByText('You are signed in')).not.toBeInTheDocument()
  })

  it('is skipped by a user who has a role', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', WITH_ROLE)
    openApp('/pending')

    expect(await screen.findByText('You are signed in')).toBeInTheDocument()
  })

  it('keeps the session and opens the pending page when a protected request reports a revoked role', async () => {
    storeToken()
    new FakeServer()
      .on('GET /auth/me', WITH_ROLE)
      .on('GET /orders', problem(403, 'ACCOUNT_NO_ROLE'))
    openApp('/')
    await screen.findByText('You are signed in')

    await act(async () => {
      await expect(api('/orders')).rejects.toMatchObject({ code: 'ACCOUNT_NO_ROLE' })
    })

    expect(await screen.findByText(TEXT.pendingTitle)).toBeInTheDocument()
    expect(loadSession()).not.toBeNull()
  })

  it('says so when a check finds no role yet', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', NO_ROLE)
    const user = openApp('/pending')

    await user.click(await screen.findByRole('button', { name: TEXT.checkAgain }))

    expect(await screen.findByRole('alert')).toHaveTextContent(TEXT.stillNoRole)
    expect(server.callsTo('GET /auth/me')).toHaveLength(2)
  })

  it('opens the application when a check finds that a role was assigned', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', NO_ROLE)
    const user = openApp('/pending')
    await screen.findByText(TEXT.pendingTitle)

    server.on('GET /auth/me', WITH_ROLE)
    await user.click(screen.getByRole('button', { name: TEXT.checkAgain }))

    expect(await screen.findByText('You are signed in')).toBeInTheDocument()
  })

  it('goes back to the login page with the message when the account was disabled in the meantime', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', NO_ROLE)
    const user = openApp('/pending')
    await screen.findByText(TEXT.pendingTitle)

    server.on('GET /auth/me', problem(403, 'ACCOUNT_DISABLED'))
    await user.click(screen.getByRole('button', { name: TEXT.checkAgain }))

    expect(await screen.findByText(TEXT.disabled)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(loadSession()).toBeNull()
  })

  it('needs a login', async () => {
    new FakeServer()
    openApp('/pending')

    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  })
})
