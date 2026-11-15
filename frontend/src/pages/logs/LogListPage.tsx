import { Link, useSearchParams } from 'react-router-dom'
import { itemRecords, listingRecords, orderRecords, stockMovements, type RecordFilters, type RecordLoader } from '../../api/logs'
import { OrderNumberSearch } from '../../components/OrderNumberSearch'
import { PageHeader } from '../../components/PageHeader'
import { RecordPicker } from '../../components/RecordPicker'
import { RecordTable, type RecordColumn } from '../../components/RecordTable'
import { TimeFilter } from '../../components/TimeFilter'
import { useRecords } from '../../hooks/useRecords'
import { TEXT } from '../../text'
import { ITEM_COLUMNS, LISTING_COLUMNS, MOVEMENT_COLUMNS, ORDER_COLUMNS } from './recordColumns'
import { filtersOf, isFiltered, readLogState, writeLogState, type LogState } from './recordFilters'

export type LogKind = 'listing' | 'orders' | 'items' | 'movements'

interface KindSetup {
  title: string
  back: { to: string; label: string }
  picker: 'product' | 'item'
  orderNumber: boolean
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  load: RecordLoader<any>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  columns: RecordColumn<any>[]
}

const PRODUCTS = { to: '/', label: TEXT.logs.backToProducts }
const STOCK = { to: '/stock', label: TEXT.logs.backToStock }

const KINDS: Record<LogKind, KindSetup> = {
  listing: { title: TEXT.logs.listingRecords, back: PRODUCTS, picker: 'product', orderNumber: false, load: listingRecords, columns: LISTING_COLUMNS },
  orders: { title: TEXT.logs.orderRecords, back: PRODUCTS, picker: 'product', orderNumber: true, load: orderRecords, columns: ORDER_COLUMNS },
  items: { title: TEXT.logs.itemRecords, back: STOCK, picker: 'item', orderNumber: false, load: itemRecords, columns: ITEM_COLUMNS },
  movements: { title: TEXT.logs.stockMovements, back: STOCK, picker: 'item', orderNumber: true, load: stockMovements, columns: MOVEMENT_COLUMNS },
}

/**
 * All records of one kind, the first way into a log (Logs in docs/frontend/page-design.md; Figma frames
 * "Listing records / ...", "Order records / ...", "Item records / ...", "Stock movements / ..."). The picker
 * narrows them to one product or item and the time filter to days; the order records and the stock
 * movements also find one whole order number, which disables the other filters while it is applied. The
 * filters are kept in the address, so a reload or Back shows the same records.
 */
export function LogListPage({ kind }: { kind: LogKind }) {
  const setup = KINDS[kind]
  const [params, setParams] = useSearchParams()
  const state = readLogState(params)
  const filters: RecordFilters = filtersOf(state)
  const list = useRecords(setup.load, filters, JSON.stringify(filters))
  const byNumber = Boolean(state.orderNo)

  function update(next: LogState) {
    setParams(writeLogState(next), { replace: true })
  }

  return (
    <>
      <Link to={setup.back.to} className="back-link">{setup.back.label}</Link>
      <PageHeader title={setup.title} />
      <section className="panel filter-bar log-filters" aria-label="Filters">
        <div className="log-filters__row">
          <RecordPicker
            kind={setup.picker}
            disabled={byNumber}
            value={state.id !== undefined ? { id: state.id, label: state.label ?? String(state.id) } : null}
            onChange={(picked) => update({ ...state, id: picked?.id, label: picked?.label })}
          />
          <TimeFilter value={state} disabled={byNumber} onChange={(time) => update({ ...state, ...time })} />
        </div>
        {setup.orderNumber && (
          <OrderNumberSearch key={state.orderNo ?? ''} applied={state.orderNo} onApply={(orderNo) => update({ ...state, orderNo })} />
        )}
      </section>
      <RecordTable
        label={setup.title}
        list={list}
        columns={setup.columns}
        filtered={isFiltered(state, true)}
        onClear={() => update({ order: 'desc' })}
        noneFound={byNumber ? { title: TEXT.logs.noneFoundTitle, text: TEXT.logs.orderNumberNone } : undefined}
      />
    </>
  )
}
