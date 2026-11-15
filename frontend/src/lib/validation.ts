import { TEXT } from '../text'

/**
 * The checks a page makes before it sends a value. They are the rules of the backend
 * (Username rule and Password rule in docs/database/database-design.md), repeated here so that a slip
 * is shown at once and costs no request. The server checks the same rules again.
 *
 * Each function returns the message to show, or null when the value is fine.
 */

const USERNAME = /^[A-Za-z0-9]{3,64}$/
const CODE = /^\d{6}$/
// The same idea as the server: something, an @, a domain with a dot in it, and no spaces.
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const EMAIL_MAX = 254
const PASSWORD_MIN = 8
const PASSWORD_MAX = 24

export function checkNewUsername(value: string | undefined): string | null {
  if (!value) return TEXT.usernameRequiredNew
  return USERNAME.test(value) ? null : TEXT.usernameInvalid
}

export function checkEmail(value: string | undefined): string | null {
  if (!value) return TEXT.emailRequired
  return value.length <= EMAIL_MAX && EMAIL.test(value) ? null : TEXT.emailInvalid
}

export function checkCode(value: string | undefined): string | null {
  if (!value) return TEXT.codeRequired
  return CODE.test(value) ? null : TEXT.codeInvalidFormat
}

/** 8 to 24 characters with at least one letter and at least one digit. */
export function checkNewPassword(value: string | undefined): string | null {
  if (!value) return TEXT.passwordRequiredNew
  const length = value.length >= PASSWORD_MIN && value.length <= PASSWORD_MAX
  const letter = /\p{L}/u.test(value)
  const digit = /\p{Nd}/u.test(value)
  return length && letter && digit ? null : TEXT.passwordInvalid
}

/** Turns one of the checks above into a rule of an Ant Design form field. */
export function rule(check: (value: string | undefined) => string | null) {
  return {
    validator: (_: unknown, value: string | undefined) => {
      const message = check(value)
      return message ? Promise.reject(new Error(message)) : Promise.resolve()
    },
  }
}
