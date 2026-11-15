import { screen } from '@testing-library/react'
import type { UserEvent } from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { FakeServer, openApp, problem } from '../../test/server'
import { TEXT } from '../../text'

async function fill(user: UserEvent, values: Partial<Record<'Email' | 'Verification code' | 'New password', string>>) {
  for (const [label, value] of Object.entries(values)) {
    const input = await screen.findByLabelText(label)
    await user.clear(input)
    if (value) {
      await user.click(input)
      await user.paste(value)
    }
  }
}

const VALID = { Email: 'alice@example.com', 'Verification code': '932900', 'New password': 'new-horse-2' }

describe('the Forgot password page', () => {
  it('requests a code and says the same sentence whether or not the address has an account', async () => {
    const server = new FakeServer().on('POST /auth/email-codes', { status: 204 })
    const user = openApp('/forgot-password')
    await fill(user, { Email: 'nobody@example.com' })

    await user.click(screen.getByRole('button', { name: TEXT.sendCode }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(TEXT.resetCodeSent)
    // The sentence does not repeat the address as a fact about an account.
    expect(alert).not.toHaveTextContent('nobody@example.com')
    expect(server.callsTo('POST /auth/email-codes')[0].body).toEqual({ email: 'nobody@example.com', purpose: 'RESET_PASSWORD' })
    expect(screen.getByRole('button', { name: /Resend in \d+s/ })).toBeDisabled()
  })

  it('resets the password and opens the login page with the notice', async () => {
    const server = new FakeServer().on('POST /auth/password-reset', { status: 204 })
    const user = openApp('/forgot-password')
    await fill(user, VALID)

    await user.click(screen.getByRole('button', { name: 'Reset password' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(TEXT.passwordReset)
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(server.callsTo('POST /auth/password-reset')[0].body).toEqual({
      email: 'alice@example.com',
      code: '932900',
      newPassword: 'new-horse-2',
    })
  })

  it.each([
    ['a password that breaks the rule', { 'New password': 'short1' }, TEXT.passwordInvalid],
    ['a code that is not 6 digits', { 'Verification code': '93' }, TEXT.codeInvalidFormat],
    ['an address that is not one', { Email: 'alice' }, TEXT.emailInvalid],
  ])('shows the rule under the field for %s and sends nothing', async (_case, wrong, message) => {
    const server = new FakeServer()
    const user = openApp('/forgot-password')
    await fill(user, { ...VALID, ...wrong })

    await user.click(screen.getByRole('button', { name: 'Reset password' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(server.calls).toHaveLength(0)
  })

  it.each([
    ['a wrong or expired code', problem(400, 'CODE_INVALID'), TEXT.codeWrong],
    [
      'a new password equal to the current one',
      problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'newPassword', message: 'must differ from the current password' }] }),
      TEXT.samePassword,
    ],
  ])('shows the answer of the server under the field for %s and stays on the page', async (_case, answer, message) => {
    new FakeServer().on('POST /auth/password-reset', answer)
    const user = openApp('/forgot-password')
    await fill(user, VALID)

    await user.click(screen.getByRole('button', { name: 'Reset password' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(screen.getByLabelText('Verification code')).toHaveValue('932900')
    expect(screen.getByRole('button', { name: 'Reset password' })).toBeEnabled()
  })

  it('says "try again in a minute" when a limit refuses the request for a code', async () => {
    new FakeServer().on('POST /auth/email-codes', problem(429, 'TOO_MANY_REQUESTS'))
    const user = openApp('/forgot-password')
    await fill(user, { Email: 'alice@example.com' })

    await user.click(screen.getByRole('button', { name: TEXT.sendCode }))

    expect(await screen.findByRole('alert')).toHaveTextContent(TEXT.tooManyRequests)
  })
})
