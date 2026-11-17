import { api } from './client'
import { pageParams, type Page, type PageRequest } from './paging'

/** The ticket endpoints and the user picker, in Tickets of docs/api/api-design.md. */

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'CANCELLED'
export type TicketType = 'ORDER_EXCEPTION' | 'INVENTORY_EXCEPTION' | 'OTHER'
export type TicketSource = 'SYSTEM' | 'MANUAL'

/** A person as the API names one. `system` is true only for the SYSTEM identity, shown as System. */
export interface Person {
  id: number | null
  username: string | null
  system: boolean
}

/** A row of a ticket list. */
export interface TicketRow {
  id: number
  ticketNo: string
  type: TicketType
  source: TicketSource
  title: string
  createdBy: Person
  createdAt: string
  status: TicketStatus
  closedAt: string | null
  cancelledAt: string | null
}

/** Another ticket by its number. `id` is null when the caller may not see it: the number is shown without a link. */
export interface TicketRef {
  id: number | null
  ticketNo: string
}

export interface LinkedItem {
  inventoryId: number
  sku: string
  name: string
  deleted: boolean
}

export interface HistoryEntry {
  from: Person | null
  to: Person | null
  by: Person
  reason: string | null
  at: string
}

/** Why the system cancelled a ticket, with the name of the product or item. */
export interface CancelCause {
  kind: 'PRODUCT_DELISTED' | 'ITEM_DELETED'
  name: string | null
}

export interface Ticket {
  id: number
  ticketNo: string
  type: TicketType
  source: TicketSource
  status: TicketStatus
  title: string
  description: string
  orderNo: string | null
  items: LinkedItem[]
  follows: TicketRef | null
  followUps: TicketRef[]
  createdBy: Person
  createdAt: string
  assignee: Person | null
  history: HistoryEntry[]
  resolution: string | null
  resolvedBy: Person | null
  resolvedAt: string | null
  closedBy: Person | null
  closedAt: string | null
  cancelReason: string | null
  cancelCause: CancelCause | null
  cancelledBy: Person | null
  cancelledAt: string | null
}

/**
 * The search of one screen. The ticket number and the order number are used alone: when one is set, the
 * other filters are not sent.
 */
export interface TicketSearch {
  source?: TicketSource
  createdBy?: number
  assigneeId?: number
  /** The tickets linked to this warehouse item. */
  inventoryId?: number
  from?: string
  to?: string
  ticketNo?: string
  orderNo?: string
  order: 'desc' | 'asc'
}

export function listTickets(status: TicketStatus, search: TicketSearch, page: PageRequest): Promise<Page<TicketRow>> {
  const params = new URLSearchParams({ status })
  if (search.ticketNo) {
    params.append('ticketNo', search.ticketNo)
  } else if (search.orderNo) {
    params.append('orderNo', search.orderNo)
  } else {
    if (search.source) params.append('source', search.source)
    if (search.createdBy !== undefined) params.append('createdBy', String(search.createdBy))
    if (search.assigneeId !== undefined) params.append('assigneeId', String(search.assigneeId))
    if (search.inventoryId !== undefined) params.append('inventoryId', String(search.inventoryId))
    if (search.from) params.append('from', search.from)
    if (search.to) params.append('to', search.to)
  }
  if (search.order === 'asc') params.append('order', 'asc')
  pageParams(params, page)
  return api<Page<TicketRow>>(`/tickets?${params.toString()}`)
}

export function getTicket(id: number): Promise<Ticket> {
  return api<Ticket>(`/tickets/${id}`)
}

export interface NewTicket {
  type: TicketType
  title: string
  description: string
  inventoryIds: number[]
  orderNo: string | null
  followsTicketNo: string | null
}

export function createTicket(ticket: NewTicket): Promise<Ticket> {
  return api<Ticket>('/tickets', { method: 'POST', body: ticket })
}

export function takeTicket(id: number): Promise<Ticket> {
  return api<Ticket>(`/tickets/${id}/take`, { method: 'POST' })
}

export function assignTicket(id: number, assigneeId: number, reason: string): Promise<Ticket> {
  return api<Ticket>(`/tickets/${id}/assign`, { method: 'POST', body: { assigneeId, reason } })
}

export function reassignTicket(id: number, assigneeId: number, expectedAssigneeId: number, reason: string): Promise<Ticket> {
  return api<Ticket>(`/tickets/${id}/reassign`, { method: 'POST', body: { assigneeId, expectedAssigneeId, reason } })
}

export function releaseTicket(id: number, expectedAssigneeId: number, reason: string): Promise<Ticket> {
  return api<Ticket>(`/tickets/${id}/release`, { method: 'POST', body: { expectedAssigneeId, reason } })
}

export function resolveTicket(id: number, resolution: string): Promise<Ticket> {
  return api<Ticket>(`/tickets/${id}/resolve`, { method: 'POST', body: { resolution } })
}

export function cancelTicket(id: number, reason: string): Promise<Ticket> {
  return api<Ticket>(`/tickets/${id}/cancel`, { method: 'POST', body: { reason } })
}

export function archiveTicket(id: number): Promise<Ticket> {
  return api<Ticket>(`/tickets/${id}/archive`, { method: 'POST' })
}

export interface ArchiveResult {
  archived: string[]
  skipped: { id: number; ticketNo: string | null; status: TicketStatus | null }[]
}

export function archiveTickets(ids: number[]): Promise<ArchiveResult> {
  return api<ArchiveResult>('/tickets/archive', { method: 'POST', body: { ids } })
}

/** A user of the picker: only the id and the username. */
export interface UserOption {
  id: number
  username: string
}

/** `filter`: every user, for the search filters. `assignee`: the people the caller may give a ticket to. */
export function userOptions(use: 'filter' | 'assignee', search: string, page: PageRequest): Promise<Page<UserOption>> {
  const params = new URLSearchParams({ use })
  const part = search.trim()
  if (part) params.append('search', part)
  pageParams(params, page)
  return api<Page<UserOption>>(`/user-options?${params.toString()}`)
}

/** A ticket number as typed: 8 letters or digits, in upper case. Null when it cannot be one. */
export function normalizeTicketNo(typed: string): string | null {
  const trimmed = typed.trim().toUpperCase()
  return /^[A-Z0-9]{8}$/.test(trimmed) ? trimmed : null
}
