import { api } from './client'
import type { OrderRecord, OrderResult } from './logs'
import { pageParams, type Page, type PageRequest } from './paging'

/** The audit log page: `GET /audit-records` and `GET /audit-order-records` (Logs in docs/api/api-design.md). */

export type AuditArea = 'ACCOUNT' | 'ROLE' | 'TICKET' | 'STOCK' | 'ITEM' | 'PRODUCT'

/** The actions of each area, as the table of actions of the backend files them (Categories in the database design). */
export const AREA_ACTIONS: Record<AuditArea, readonly string[]> = {
  ACCOUNT: ['USER_REGISTERED', 'USER_STATUS_CHANGED', 'USER_DELETED', 'PASSWORD_RESET', 'LOGIN_LOCKED'],
  ROLE: ['ROLE_GRANTED', 'ROLE_REVOKED'],
  TICKET: ['TICKET_CREATED', 'TICKET_TAKEN', 'TICKET_ASSIGNED', 'TICKET_REASSIGNED', 'TICKET_RELEASED', 'TICKET_RESOLVED', 'TICKET_CLOSED', 'TICKET_CANCELLED'],
  STOCK: ['STOCK_RESTOCK', 'STOCK_WRITE_OFF', 'STOCK_ADJUSTED'],
  ITEM: ['ITEM_CREATED', 'ITEM_UPDATED', 'ITEM_DELETED'],
  PRODUCT: ['PRODUCT_LISTED', 'PRODUCT_DELISTED'],
}

export type AuditData = Record<string, unknown>

/** A row of `operation_logs`. `userId` and `by` are null for an action of the system. */
export interface AuditRecord {
  id: number
  at: string
  area: AuditArea
  action: string
  userId: number | null
  by: string | null
  targetType: string
  targetId: number
  before: AuditData | null
  after: AuditData | null
  ipAddress: string | null
  remark: string | null
}

/** The filters of an area. `system` and `userId` are not used together. */
export interface AuditFilters {
  action?: string
  userId?: number
  system?: boolean
  /** The user the rows are about, for the areas ACCOUNT and ROLE. */
  aboutUserId?: number
  /** The ticket the rows are about, for the area TICKET. */
  ticketNo?: string
  from?: string
  to?: string
  order: 'desc' | 'asc'
}

function common(params: URLSearchParams, filters: AuditFilters, page: PageRequest) {
  if (filters.system) params.append('system', 'true')
  else if (filters.userId !== undefined) params.append('userId', String(filters.userId))
  if (filters.from) params.append('from', filters.from)
  if (filters.to) params.append('to', filters.to)
  if (filters.order === 'asc') params.append('order', 'asc')
  pageParams(params, page)
}

export function auditRecords(area: AuditArea, filters: AuditFilters, page: PageRequest): Promise<Page<AuditRecord>> {
  const params = new URLSearchParams({ area })
  if (filters.action) params.append('action', filters.action)
  if (filters.aboutUserId !== undefined && (area === 'ACCOUNT' || area === 'ROLE')) params.append('aboutUserId', String(filters.aboutUserId))
  if (filters.ticketNo && area === 'TICKET') params.append('ticketNo', filters.ticketNo)
  common(params, filters, page)
  return api<Page<AuditRecord>>(`/audit-records?${params.toString()}`)
}

/** The Orders area: the attempts, with the result in place of the action. */
export function auditOrderRecords(filters: AuditFilters & { result?: OrderResult }, page: PageRequest): Promise<Page<OrderRecord>> {
  const params = new URLSearchParams()
  if (filters.result) params.append('result', filters.result)
  common(params, { ...filters, system: false }, page)
  const text = params.toString()
  return api<Page<OrderRecord>>(text ? `/audit-order-records?${text}` : '/audit-order-records')
}
