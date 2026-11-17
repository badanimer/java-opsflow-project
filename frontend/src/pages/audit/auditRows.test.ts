import { describe, expect, it } from 'vitest'
import type { AuditRecord } from '../../api/audit'
import { fieldsOf, summaryOf, targetOf } from './auditRows'

describe('the row of a sign-in lock', () => {
  const record: AuditRecord = {
    id: 1,
    at: '2026-11-18T01:00:00Z',
    area: 'ACCOUNT',
    action: 'LOGIN_LOCKED',
    userId: null,
    by: null,
    targetType: 'USER',
    targetId: 7,
    before: null,
    after: { failedAttempts: 5, lockedUntil: '2026-11-18T01:01:00Z', lockedAddress: '203.0.113.66', username: 'mary' },
    ipAddress: '203.0.113.66',
    remark: 'Locked for one address after failed logins from it',
  }

  it('names the account, says which lock it is, and shows the address that was locked out', () => {
    expect(targetOf(record)).toBe('mary')
    expect(summaryOf(record)).toBe('Locked for one address after failed logins from it')
    expect(fieldsOf(record.after)).toContain('Locked for address: 203.0.113.66')
  })
})
