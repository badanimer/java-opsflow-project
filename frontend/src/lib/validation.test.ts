import { describe, expect, it } from 'vitest'
import { TEXT } from '../text'
import { checkCode, checkEmail, checkNewPassword, checkNewUsername } from './validation'

describe('the checks a page makes before it sends a value', () => {
  it.each(['bob', 'alice', 'AliceSmith', 'opsUser7', '1234', 'a'.repeat(64)])('accepts the username %s', (value) => {
    expect(checkNewUsername(value)).toBeNull()
  })

  it.each(['al', 'alice smith', 'alice@home', ' alice', 'älice', 'Alice.Smith', 'ops_user', 'ops-user', 'a'.repeat(65)])(
    'refuses the username %s',
    (value) => {
      expect(checkNewUsername(value)).toBe(TEXT.usernameInvalid)
    },
  )

  it.each(['abcdefg1', 'correct-horse-1', 'A1 b c d e', 'a23456789012345678901234', 'pässwörd1'])(
    'accepts the password %s',
    (value) => {
      expect(checkNewPassword(value)).toBeNull()
    },
  )

  it.each(['abcdef1', 'abcdefgh', '12345678', '1234567890123456789012345a', '--------'])('refuses the password %s', (value) => {
    expect(checkNewPassword(value)).toBe(TEXT.passwordInvalid)
  })

  it('accepts a 6-digit code and nothing else', () => {
    expect(checkCode('932900')).toBeNull()
    expect(checkCode('000000')).toBeNull()
    for (const value of ['9329', '9329001', '93290a', ' 93290']) expect(checkCode(value)).toBe(TEXT.codeInvalidFormat)
  })

  it('accepts an email address and refuses what is not one', () => {
    expect(checkEmail('alice@example.com')).toBeNull()
    for (const value of ['alice', 'alice@example', 'alice @example.com', '@example.com', 'a'.repeat(250) + '@example.com']) {
      expect(checkEmail(value)).toBe(TEXT.emailInvalid)
    }
  })

  it('asks for a value that is missing', () => {
    expect(checkNewUsername('')).toBe(TEXT.usernameRequiredNew)
    expect(checkEmail(undefined)).toBe(TEXT.emailRequired)
    expect(checkCode('')).toBe(TEXT.codeRequired)
    expect(checkNewPassword(undefined)).toBe(TEXT.passwordRequiredNew)
  })
})
