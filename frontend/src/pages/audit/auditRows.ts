import type { AuditData, AuditRecord } from '../../api/audit'
import { formatPrice } from '../../lib/format'
import { TEXT } from '../../text'

/**
 * How a row of the audit page reads (Audit log page in docs/frontend/page-design.md; Figma section D). Every
 * row carries its labels (Labels in audit rows in the database design), so the page shows the names of the
 * time and never an id: the target is the ticket number, the username, the role, or the name and SKU; the
 * summary says what changed; the expanded view lists the fields before and after.
 */

const ROLE_NAMES: Record<string, string> = { ADMIN: 'Administrator', MANAGER: 'Manager', OPERATOR: 'Operator', VIEWER: 'Viewer' }
const TICKET_STATUS: Record<string, string> = TEXT.tickets.status

function text(data: AuditData | null, key: string): string | undefined {
  const value = data?.[key]
  return typeof value === 'string' && value !== '' ? value : undefined
}

function number(data: AuditData | null, key: string): number | undefined {
  const value = data?.[key]
  return typeof value === 'number' ? value : undefined
}

function list(data: AuditData | null, key: string): string[] {
  const value = data?.[key]
  return Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === 'string') : []
}

function label(data: AuditData | null, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const found = text(data, key)
    if (found) return found
  }
  return undefined
}

function status(value: string | undefined): string {
  if (!value) return '—'
  return TICKET_STATUS[value] ?? TEXT.audit.statuses[value] ?? value
}

function signed(amount: number): string {
  return amount > 0 ? `+${amount}` : `−${Math.abs(amount)}`
}

/** What the row is about, by its label. */
export function targetOf(record: AuditRecord): string {
  const { before, after } = record
  const ticketNo = label(after, 'ticketNo') ?? label(before, 'ticketNo')
  if (ticketNo) return ticketNo
  const name = label(after, 'name') ?? label(before, 'name')
  const sku = label(after, 'sku') ?? label(before, 'sku')
  if (name) return sku ? `${name} (${sku})` : name
  const username = label(after, 'username') ?? label(before, 'username')
  if (username) return username
  return '—'
}

/** One line that says what changed, as the Figma rows do. */
export function summaryOf(record: AuditRecord): string {
  const { before, after, action } = record
  const cancelled = list(after, 'cancelledTicketNos')
  const withCancelled = (head: string) => (cancelled.length > 0 ? `${head} · ${TEXT.audit.cancelledTickets(cancelled.join(', '))}` : head)
  const returned = list(after, 'returnedTicketNos')
  const withReturned = (head: string) => (returned.length > 0 ? `${head} · ${TEXT.audit.returnedTickets(returned.join(', '))}` : head)
  switch (action) {
    case 'USER_STATUS_CHANGED':
      return withReturned(TEXT.audit.statusChange(status(text(before, 'status')), status(text(after, 'status'))))
    case 'USER_DELETED':
      return withReturned(record.remark ?? '')
    case 'ROLE_GRANTED':
      return TEXT.audit.granted(ROLE_NAMES[text(after, 'roleCode') ?? ''] ?? text(after, 'roleCode') ?? '')
    case 'ROLE_REVOKED':
      return withReturned(TEXT.audit.revoked(ROLE_NAMES[text(before, 'roleCode') ?? ''] ?? text(before, 'roleCode') ?? ''))
    case 'TICKET_ASSIGNED':
      return TEXT.audit.assignedTo(text(after, 'assigneeUsername') ?? '')
    case 'TICKET_REASSIGNED':
      return `${text(before, 'assigneeUsername') ?? '—'} → ${text(after, 'assigneeUsername') ?? '—'}`
    case 'TICKET_CREATED':
      return TEXT.tickets.types[(text(after, 'type') ?? 'OTHER') as keyof typeof TEXT.tickets.types] ?? ''
    case 'TICKET_RELEASED':
    case 'TICKET_RESOLVED':
    case 'TICKET_CLOSED':
    case 'TICKET_CANCELLED':
      return TEXT.audit.statusChange(status(text(before, 'status')), status(text(after, 'status')))
    case 'STOCK_RESTOCK':
    case 'STOCK_WRITE_OFF':
    case 'STOCK_ADJUSTED': {
      const from = number(before, 'availableQuantity')
      const to = number(after, 'availableQuantity')
      return from !== undefined && to !== undefined ? `${from} → ${to} (${signed(to - from)})` : ''
    }
    case 'ITEM_CREATED':
      return TEXT.audit.startingStock(number(after, 'quantity') ?? 0)
    case 'ITEM_UPDATED':
      return [before && 'name' in before ? TEXT.audit.nameUpdated : null, before && 'imageUrl' in before ? TEXT.audit.imageUpdated : null]
        .filter(Boolean)
        .join(' · ')
    case 'ITEM_DELETED':
      return withCancelled(TEXT.audit.stockAtDeletion(number(before, 'availableQuantity') ?? 0))
    case 'PRODUCT_LISTED':
      return TEXT.audit.listedAt(formatPrice(text(after, 'price') ?? '0.00'))
    case 'PRODUCT_DELISTED':
      return withCancelled(TEXT.audit.statusChange(status('LISTED'), status('UNLISTED')))
    default:
      return record.remark ?? ''
  }
}

/** User ids beside their usernames, and the address of an image. The usernames are shown instead. */
const HIDDEN = new Set(['createdBy', 'resolvedBy', 'closedBy', 'cancelledBy', 'imageUrl'])

/** The fields that are only ids or keys for programs, never shown. */
function hidden(key: string): boolean {
  return HIDDEN.has(key) || key.endsWith('Id') || key.endsWith('Ids')
}

/** The fields of one side of the row, as "Label: value", with the ids left out. */
export function fieldsOf(data: AuditData | null): string[] {
  if (!data) return []
  return Object.entries(data)
    .filter(([key, value]) => !hidden(key) && value !== null && value !== undefined && !(Array.isArray(value) && value.length === 0))
    .map(([key, value]) => `${TEXT.audit.fields[key] ?? key}: ${show(key, value)}`)
}

function show(key: string, value: unknown): string {
  if (typeof value === 'boolean') return value ? TEXT.audit.yes : TEXT.audit.no
  if (Array.isArray(value)) return value.join(', ')
  if (key === 'status' && typeof value === 'string') return status(value)
  if (key === 'roleCode' && typeof value === 'string') return `${ROLE_NAMES[value] ?? value} (${value})`
  if (key === 'type' && typeof value === 'string') return TEXT.tickets.types[value as keyof typeof TEXT.tickets.types] ?? value
  if (key === 'price' && typeof value === 'string') return formatPrice(value)
  return String(value)
}
