import type { Person, TicketSearch, TicketSource, TicketStatus } from '../../api/tickets'
import type { TimeFilterValue } from '../../components/TimeFilter'
import { QUICK_RANGES, type QuickRange } from '../../lib/days'
import { TEXT } from '../../text'

/** The address of each screen, and the screen of each status (Ticket screens in docs/frontend/page-design.md). */
export const SCREEN_PATH: Record<TicketStatus, string> = {
  OPEN: '/tickets/pool',
  IN_PROGRESS: '/tickets/in-progress',
  RESOLVED: '/tickets/resolved',
  CLOSED: '/tickets/archive',
  CANCELLED: '/tickets/cancelled',
}

export type SearchMode = 'filters' | 'ticketNo' | 'orderNo'

/**
 * The search of a ticket screen, kept in the address (`?source=MANUAL&creator=7&creatorName=olive&...`),
 * so that a reload or Back shows the same tickets. The names of the chosen users are kept beside their
 * ids, so that the pickers can show them after a reload.
 */
export interface TicketListState extends TimeFilterValue {
  mode: SearchMode
  source?: TicketSource
  creator?: number
  creatorName?: string
  assignee?: number
  assigneeName?: string
  item?: number
  itemName?: string
  ticketNo?: string
  orderNo?: string
}

const DAY = /^\d{4}-\d{2}-\d{2}$/

function positive(text: string | null): number | undefined {
  const value = Number(text)
  return text && Number.isInteger(value) && value > 0 ? value : undefined
}

export function readListState(params: URLSearchParams): TicketListState {
  const source = params.get('source')
  const quick = params.get('quick') as QuickRange | null
  const from = params.get('from') ?? undefined
  const to = params.get('to') ?? undefined
  const ticketNo = params.get('ticketNo') ?? undefined
  const orderNo = params.get('orderNo') ?? undefined
  return {
    mode: ticketNo ? 'ticketNo' : orderNo ? 'orderNo' : params.get('mode') === 'ticketNo' ? 'ticketNo' : params.get('mode') === 'orderNo' ? 'orderNo' : 'filters',
    source: source === 'SYSTEM' || source === 'MANUAL' ? source : undefined,
    creator: positive(params.get('creator')),
    creatorName: params.get('creatorName') ?? undefined,
    assignee: positive(params.get('assignee')),
    assigneeName: params.get('assigneeName') ?? undefined,
    item: positive(params.get('item')),
    itemName: params.get('itemName') ?? undefined,
    from: from && DAY.test(from) ? from : undefined,
    to: to && DAY.test(to) ? to : undefined,
    quick: quick && QUICK_RANGES.includes(quick) ? quick : undefined,
    order: params.get('order') === 'asc' ? 'asc' : 'desc',
    ticketNo,
    orderNo,
  }
}

export function writeListState(state: TicketListState): URLSearchParams {
  const params = new URLSearchParams()
  if (state.mode !== 'filters') params.set('mode', state.mode)
  if (state.ticketNo) params.set('ticketNo', state.ticketNo)
  if (state.orderNo) params.set('orderNo', state.orderNo)
  if (state.source) params.set('source', state.source)
  if (state.creator !== undefined) {
    params.set('creator', String(state.creator))
    if (state.creatorName) params.set('creatorName', state.creatorName)
  }
  if (state.assignee !== undefined) {
    params.set('assignee', String(state.assignee))
    if (state.assigneeName) params.set('assigneeName', state.assigneeName)
  }
  if (state.item !== undefined) {
    params.set('item', String(state.item))
    if (state.itemName) params.set('itemName', state.itemName)
  }
  if (state.from) params.set('from', state.from)
  if (state.to) params.set('to', state.to)
  if (state.quick) params.set('quick', state.quick)
  if (state.order === 'asc') params.set('order', 'asc')
  return params
}

/** What is sent: a number alone in its mode, or the filters; and only the direction for a user who may not search. */
export function searchOf(state: TicketListState, maySearch: boolean): TicketSearch {
  if (!maySearch) return { order: state.order }
  if (state.mode === 'ticketNo') return { ticketNo: state.ticketNo, order: state.order }
  if (state.mode === 'orderNo') return { orderNo: state.orderNo, order: state.order }
  return { source: state.source, createdBy: state.creator, assigneeId: state.assignee, inventoryId: state.item, from: state.from, to: state.to, order: state.order }
}

/** Whether anything narrows the list, so that an empty list means "no match". */
export function isSearched(search: TicketSearch): boolean {
  return Boolean(search.ticketNo || search.orderNo || search.source || search.createdBy || search.assigneeId || search.inventoryId || search.from || search.to)
}

/** A person as the page names them: the username, or System for the SYSTEM identity. */
export function personName(person: Person | null | undefined): string {
  if (!person) return '—'
  return person.system ? TEXT.tickets.system : person.username ?? '—'
}
