import type { Person, Ticket, TicketRow } from '../../api/tickets'

/** The tickets and people of the ticket tests, with the numbers and names the Figma frames use. */

export const OLIVE: Person = { id: 3, username: 'olive', system: false }
export const MARY: Person = { id: 2, username: 'mary', system: false }
export const BOB: Person = { id: 4, username: 'bob', system: false }
export const SYSTEM: Person = { id: 1, username: 'system', system: true }

export function row(id: number, ticketNo: string, extra: Partial<TicketRow> = {}): TicketRow {
  return {
    id,
    ticketNo,
    type: 'INVENTORY_EXCEPTION',
    source: 'MANUAL',
    title: 'Stock count does not match the record',
    createdBy: OLIVE,
    createdAt: '2026-11-15T23:24:00Z',
    status: 'OPEN',
    closedAt: null,
    cancelledAt: null,
    ...extra,
  }
}

export function ticket(extra: Partial<Ticket> = {}): Ticket {
  return {
    id: 12,
    ticketNo: 'K7Q2M9XA',
    type: 'INVENTORY_EXCEPTION',
    source: 'MANUAL',
    status: 'OPEN',
    title: 'Stock count does not match the record',
    description: 'The shelf count for clear packing tape differs from the recorded stock.',
    orderNo: null,
    items: [{ inventoryId: 3, sku: 'C3P9K2D4', name: 'Clear packing tape', deleted: false }],
    follows: null,
    followUps: [],
    createdBy: OLIVE,
    createdAt: '2026-11-15T23:24:00Z',
    assignee: null,
    history: [],
    resolution: null,
    resolvedBy: null,
    resolvedAt: null,
    closedBy: null,
    closedAt: null,
    cancelReason: null,
    cancelCause: null,
    cancelledBy: null,
    cancelledAt: null,
    ...extra,
  }
}

export function page(items: unknown[], nextCursor: string | null = null, prevCursor: string | null = null) {
  return { status: 200, body: { items, nextCursor, prevCursor } }
}
