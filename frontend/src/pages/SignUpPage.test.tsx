import { act, screen, waitFor } from '@testing-library/react'
import type { UserEvent } from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { loadSession } from '../auth/session'
import { FakeServer, TOKEN, openApp, problem } from '../test/server'
import { TEXT } from '../text'

const CREATED = { status: 201, body: { id: 9, username: 'alice', ...TOKEN } }

async function fill(user: UserEvent, values: Partial<Record<'Username' | 'Email' | 'Verification code' | 'Password', string>>) {
  for (const [label, value] of Object.entries(values)) {
    const input = await screen.findByLabelText(label)
    await user.clear(input)
    if (value) {
      await user.click(input)
      await user.paste(value)
    }
  }
}

const VALID = { Username: 'alice', Email: 'alice@example.com', 'Verification code': '932900', Password: 'correct-horse-1' }

describe('the sign-up page', () => {
  it('requests a code for the address, says so, and makes the button wait', async () => {
    const server = new FakeServer().on('POST /auth/email-codes', { status: 204 })
    const user = openApp('/sign-up')
    await fill(user, { Email: 'alice@example.com' })

    await user.click(screen.getByRole('button', { name: TEXT.sendCode }))

    expect(await screen.findByRole('alert')).toHaveTextContent(TEXT.signUpCodeSent('alice@example.com'))
    expect(server.callsTo('POST /auth/email-codes')[0].body).toEqual({ email: 'alice@example.com', purpose: 'REGISTER' })
    expect(screen.getByRole('button', { name: /Resend in \d+s/ })).toBeDisabled()
  })

  it('lets a new code be requested after 60 seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    new FakeServer().on('POST /auth/email-codes', { status: 204 })
    const user = openApp('/sign-up')
    await fill(user, { Email: 'alice@example.com' })
    await user.click(screen.getByRole('button', { name: TEXT.sendCode }))
    expect(await screen.findByRole('button', { name: TEXT.resendIn(60) })).toBeDisabled()

    act(() => {
      vi.advanceTimersByTime(59_000)
    })
    expect(screen.getByRole('button', { name: TEXT.resendIn(1) })).toBeDisabled()
    act(() => {
      vi.advanceTimersByTime(1_000)
    })

    expect(screen.getByRole('button', { name: TEXT.sendCode })).toBeEnabled()
  })

  it('does not request a code for an address that is not one', async () => {
    const server = new FakeServer()
    const user = openApp('/sign-up')
    await fill(user, { Email: 'alice@example' })

    await user.click(screen.getByRole('button', { name: TEXT.sendCode }))

    expect(await screen.findByText(TEXT.emailInvalid)).toBeInTheDocument()
    expect(server.calls).toHaveLength(0)
  })

  it('says that the email is already registered, with the ways on, and lets the request be made again', async () => {
    new FakeServer().on('POST /auth/email-codes', problem(409, 'EMAIL_TAKEN'))
    const user = openApp('/sign-up')
    await fill(user, { Email: 'alice@example.com' })

    await user.click(screen.getByRole('button', { name: TEXT.sendCode }))

    expect(await screen.findByText(/This email is already registered\./)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Forgot password' })).toHaveAttribute('href', '/forgot-password')
    expect(screen.getByRole('button', { name: TEXT.sendCode })).toBeEnabled()
  })

  it('says "try again in a minute" when a limit refuses the request for a code', async () => {
    new FakeServer().on('POST /auth/email-codes', problem(429, 'TOO_MANY_REQUESTS'))
    const user = openApp('/sign-up')
    await fill(user, { Email: 'alice@example.com' })

    await user.click(screen.getByRole('button', { name: TEXT.sendCode }))

    expect(await screen.findByRole('alert')).toHaveTextContent(TEXT.tooManyRequests)
  })

  it('creates the account, keeps the token of the sign-up, and opens the pending page', async () => {
    const server = new FakeServer()
      .on('POST /auth/sign-up', CREATED)
      .on('GET /auth/me', { status: 200, body: { id: 9, username: 'alice', roles: [] } })
    const user = openApp('/sign-up')
    await fill(user, VALID)

    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText(TEXT.pendingTitle)).toBeInTheDocument()
    expect(server.callsTo('POST /auth/sign-up')[0].body).toEqual({
      username: 'alice',
      email: 'alice@example.com',
      password: 'correct-horse-1',
      code: '932900',
    })
    expect(loadSession()?.token).toBe(TOKEN.token)
    // No second login: the page never calls the login endpoint.
    expect(server.callsTo('POST /auth/login')).toHaveLength(0)
  })

  it.each([
    ['an invalid username', { Username: 'alice smith' }, TEXT.usernameInvalid],
    ['an invalid email', { Email: 'alice@example' }, TEXT.emailInvalid],
    ['a code that is not 6 digits', { 'Verification code': '9329' }, TEXT.codeInvalidFormat],
    ['a password that is too short', { Password: 'short1' }, TEXT.passwordInvalid],
    ['a password with no digit', { Password: 'onlyletters' }, TEXT.passwordInvalid],
    ['a password with no letter', { Password: '1234567890' }, TEXT.passwordInvalid],
  ])('shows the rule under the field for %s and sends nothing', async (_case, wrong, message) => {
    const server = new FakeServer()
    const user = openApp('/sign-up')
    await fill(user, { ...VALID, ...wrong })

    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(server.calls).toHaveLength(0)
  })

  it('asks for every field that is empty and sends nothing', async () => {
    const server = new FakeServer()
    const user = openApp('/sign-up')

    await user.click(await screen.findByRole('button', { name: 'Create account' }))

    for (const message of [TEXT.usernameRequiredNew, TEXT.emailRequired, TEXT.codeRequired, TEXT.passwordRequiredNew]) {
      expect(await screen.findByText(message)).toBeInTheDocument()
    }
    expect(server.calls).toHaveLength(0)
  })

  it.each([
    ['a wrong or expired code', problem(400, 'CODE_INVALID'), TEXT.codeWrong],
    ['a username that is taken', problem(409, 'USERNAME_TAKEN'), TEXT.usernameTaken],
    ['a password the server refuses', problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'password', message: 'x' }] }), TEXT.passwordInvalid],
  ])('shows the answer of the server under the field for %s and keeps what was typed', async (_case, answer, message) => {
    new FakeServer().on('POST /auth/sign-up', answer)
    const user = openApp('/sign-up')
    await fill(user, VALID)

    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    // The code is still in its field, so a taken username only needs another name.
    expect(screen.getByLabelText('Verification code')).toHaveValue('932900')
    expect(screen.getByLabelText('Username')).toHaveValue('alice')
    expect(loadSession()).toBeNull()
  })

  it('sends the same code again with another username after USERNAME_TAKEN', async () => {
    const server = new FakeServer().on('POST /auth/sign-up', (call) =>
      (call.body as { username: string }).username === 'alice' ? problem(409, 'USERNAME_TAKEN') : CREATED,
    )
    server.on('GET /auth/me', { status: 200, body: { id: 9, username: 'alice2', roles: [] } })
    const user = openApp('/sign-up')
    await fill(user, VALID)
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText(TEXT.usernameTaken)).toBeInTheDocument()

    await fill(user, { Username: 'alice2' })
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText(TEXT.pendingTitle)).toBeInTheDocument()
    expect(server.callsTo('POST /auth/sign-up').map((call) => (call.body as { code: string }).code)).toEqual(['932900', '932900'])
  })

  it('shows a fault of the server with its reference, and no answer as a broken connection', async () => {
    const server = new FakeServer().on('POST /auth/sign-up', problem(500, 'INTERNAL_ERROR'))
    const user = openApp('/sign-up')
    await fill(user, VALID)

    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('ddddc8f3b64942ff')

    server.on('POST /auth/sign-up', 'network-error')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(TEXT.cannotReach))
  })
})
