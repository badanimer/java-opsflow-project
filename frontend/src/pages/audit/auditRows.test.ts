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

describe('the rows of a take and an assignment', () => {
  const base: AuditRecord = {
    id: 2,
    at: '2026-11-19T03:58:38Z',
    area: 'TICKET',
    action: 'TICKET_TAKEN',
    userId: 3,
    by: 'olive',
    targetType: 'TICKET',
    targetId: 12,
    before: { status: 'OPEN' },
    after: { status: 'IN_PROGRESS', assigneeId: 3, assigneeUsername: 'olive', ticketNo: 'K7Q2M9XA' },
    ipAddress: null,
    remark: 'taken',
  }

  it('tells a take from an assignment', () => {
    expect(targetOf(base)).toBe('K7Q2M9XA')
    expect(summaryOf(base)).toBe('Taken from the pool')
    expect(summaryOf({ ...base, action: 'TICKET_ASSIGNED', userId: 2, by: 'mary', remark: 'assigned' })).toBe('Assigned to olive')
  })
})
