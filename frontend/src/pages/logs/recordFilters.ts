import type { RecordFilters } from '../../api/logs'
import type { TimeFilterValue } from '../../components/TimeFilter'
import { QUICK_RANGES, type QuickRange } from '../../lib/days'

/**
 * The filters of a log are kept in the address of the page (`?id=5&label=A4%20paper&from=...`), so that a
 * reload or Back shows the same records. These functions read and write them.
 */

export interface LogState extends TimeFilterValue {
  id?: number
  /** The label of the chosen product or item, so that the picker can show it after a reload. */
  label?: string
  orderNo?: string
  tab?: string
}

const DAY = /^\d{4}-\d{2}-\d{2}$/

export function readLogState(params: URLSearchParams): LogState {
  const id = Number(params.get('id'))
  const from = params.get('from') ?? undefined
  const to = params.get('to') ?? undefined
  const quick = params.get('quick') as QuickRange | null
  return {
    id: Number.isInteger(id) && id > 0 ? id : undefined,
    label: params.get('label') ?? undefined,
    from: from && DAY.test(from) ? from : undefined,
    to: to && DAY.test(to) ? to : undefined,
    quick: quick && QUICK_RANGES.includes(quick) ? quick : undefined,
    order: params.get('order') === 'asc' ? 'asc' : 'desc',
    orderNo: params.get('orderNo') ?? undefined,
    tab: params.get('tab') ?? undefined,
  }
}

export function writeLogState(state: LogState): URLSearchParams {
  const params = new URLSearchParams()
  if (state.tab) params.set('tab', state.tab)
  // The other filters stay in the address while an order number is applied, so that clearing the number
  // gives them back; filtersOf leaves them out of the request.
  if (state.orderNo) params.set('orderNo', state.orderNo)
  if (state.id !== undefined) params.set('id', String(state.id))
  if (state.label) params.set('label', state.label)
  if (state.from) params.set('from', state.from)
  if (state.to) params.set('to', state.to)
  if (state.quick) params.set('quick', state.quick)
  if (state.order === 'asc') params.set('order', 'asc')
  return params
}

/** The filters sent to the API: the order number alone, or the rest. */
export function filtersOf(state: LogState, fixedId?: number): RecordFilters {
  if (state.orderNo) return { order: 'desc', orderNo: state.orderNo }
  return { id: fixedId ?? state.id, from: state.from, to: state.to, order: state.order }
}

/** Whether anything narrows the list, so that an empty list means "nothing found". */
export function isFiltered(state: LogState, withId: boolean): boolean {
  return Boolean(state.orderNo || state.from || state.to || (withId && state.id !== undefined))
}
