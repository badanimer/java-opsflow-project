import { useEffect, useState } from 'react'
import { Alert, Empty, Spin, Tabs, Typography } from 'antd'
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom'
import { deletedItems, itemRecords, listingRecords, orderRecords, stockMovements, type RecordLoader } from '../../api/logs'
import { FIRST_PAGE } from '../../api/paging'
import { useAuth } from '../../auth/context'
import { can } from '../../auth/permissions'
import { EmptyText } from '../../components/ListStates'
import { PageHeader } from '../../components/PageHeader'
import { RecordTable, type RecordColumn } from '../../components/RecordTable'
import { TimeFilter } from '../../components/TimeFilter'
import { useRecords } from '../../hooks/useRecords'
import { failureNotice } from '../../lib/failure'
import { TEXT } from '../../text'
import { ITEM_COLUMNS, LISTING_COLUMNS, MOVEMENT_COLUMNS, ORDER_COLUMNS } from './recordColumns'
import { filtersOf, isFiltered, readLogState, writeLogState, type LogState } from './recordFilters'

export type OwnLogKind = 'product' | 'item' | 'deleted'

interface Part {
  key: string
  title: string
  /** The product or the item the part reads. */
  id: number
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  load: RecordLoader<any>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  columns: RecordColumn<any>[]
}

/** Who the log is about: its name, and the ids its parts read. */
interface Subject {
  name: string
  sku?: string
  inventoryId?: number
  productId?: number | null
}

/** The state a View button passes, so that the page needs no request for the name. */
export interface OwnLogArrival {
  name: string
  sku?: string
  inventoryId?: number
  productId?: number | null
}

/**
 * The own log of one product, one item, or one deleted item (Figma frames "Product own log / ...", "Item
 * own log / ...", "Deleted item with product / ...", "Deleted item without product / ..."): its kinds of
 * record as tabs, with the time filter and the direction only. A deleted item has four parts when it ever
 * had a product, and two otherwise. The name comes with the View button; after a reload the page finds it
 * from the records, or, for a deleted item, from the Deleted items list by its SKU.
 */
export function OwnLogPage({ kind }: { kind: OwnLogKind }) {
  const { account } = useAuth()
  const routeParams = useParams()
  const arrival = (useLocation().state ?? null) as OwnLogArrival | null
  const [params, setParams] = useSearchParams()
  const state = readLogState(params)
  const [subject, setSubject] = useState<Subject | null>(arrival ? { ...arrival } : null)
  const [missing, setMissing] = useState<unknown>(null)

  const routeId = Number(routeParams.id)
  const sku = routeParams.sku ?? ''

  useEffect(() => {
    if (subject) return
    let current = true
    findSubject(kind, routeId, sku)
      .then((found) => {
        if (!current) return
        if (found) setSubject(found)
        else setMissing('gone')
      })
      .catch((failure: unknown) => { if (current) setMissing(failure) })
    return () => { current = false }
    // The subject is looked up once for the address of the page.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kind, routeId, sku])

  const parts: Part[] = subject ? partsOf(kind, subject, routeId, can(account, 'PRODUCT_VIEW')) : []
  const active = parts.find((part) => part.key === state.tab) ?? parts[0]
  const back = kind === 'product' ? { to: '/', label: TEXT.logs.backToProducts } : kind === 'item' ? { to: '/stock', label: TEXT.logs.backToStock } : { to: '/stock/records/deleted', label: TEXT.logs.backToDeleted }
  const subtitle = kind === 'product' ? TEXT.logs.productLogSubtitle : kind === 'item' ? TEXT.logs.itemLogSubtitle : TEXT.logs.deletedLogSubtitle(subject?.sku ?? sku)

  function update(next: LogState) {
    setParams(writeLogState(next), { replace: true })
  }

  return (
    <>
      <Link to={back.to} className="back-link">{back.label}</Link>
      <PageHeader title={subject?.name ?? ''} />
      <Typography.Text type="secondary" className="own-log__subtitle">{subtitle}</Typography.Text>
      {!subject ? (
        missing ? (
          missing === 'gone' ? (
            <Empty description={<EmptyText title={TEXT.stock.itemGone} text="" />} />
          ) : (
            <Alert type="error" showIcon message={failureNotice(missing).message} role="alert" />
          )
        ) : (
          <div className="list-loading" aria-busy="true"><Spin /></div>
        )
      ) : (
        <>
          <Tabs
            activeKey={active.key}
            onChange={(tab) => update({ ...state, tab })}
            items={parts.map((part) => ({ key: part.key, label: part.title }))}
          />
          <section className="panel filter-bar log-filters" aria-label="Filters">
            <TimeFilter value={state} onChange={(time) => update({ ...state, ...time })} />
          </section>
          <OwnLogPart key={active.key} part={active} state={state} onClear={() => update({ order: 'desc', tab: state.tab })} />
        </>
      )}
    </>
  )
}

function OwnLogPart({ part, state, onClear }: { part: Part; state: LogState; onClear: () => void }) {
  const filters = filtersOf({ ...state, orderNo: undefined }, part.id)
  const list = useRecords(part.load, filters, JSON.stringify(filters))
  return <RecordTable label={part.title} list={list} columns={part.columns} filtered={isFiltered(state, false)} onClear={onClear} />
}

/**
 * The parts of an own log, the one read most first (the owner's choice, 2026-11-16): what moved comes
 * before what happened to the product or item itself, so View opens on the orders or the stock movements.
 */
function partsOf(kind: OwnLogKind, subject: Subject, routeId: number, mayReadProducts: boolean): Part[] {
  const product = (id: number): Part[] => [
    { key: 'orders', title: TEXT.logs.orderRecords, id, load: orderRecords, columns: ORDER_COLUMNS },
    { key: 'listing', title: TEXT.logs.listingRecords, id, load: listingRecords, columns: LISTING_COLUMNS },
  ]
  const item = (id: number): Part[] => [
    { key: 'movements', title: TEXT.logs.stockMovements, id, load: stockMovements, columns: MOVEMENT_COLUMNS },
    { key: 'items', title: TEXT.logs.itemRecords, id, load: itemRecords, columns: ITEM_COLUMNS },
  ]
  if (kind === 'product') return product(routeId)
  if (kind === 'item') return item(routeId)
  const parts = item(subject.inventoryId ?? 0)
  return subject.productId && mayReadProducts ? [...parts, ...product(subject.productId)] : parts
}

/** The name of the subject after a reload, read from its records or from the Deleted items list. */
async function findSubject(kind: OwnLogKind, id: number, sku: string): Promise<Subject | null> {
  if (kind === 'deleted') {
    const found = await deletedItems(sku, FIRST_PAGE)
    const item = found.items.find((row) => row.sku === sku)
    return item ? { name: item.name, sku: item.sku, inventoryId: item.id, productId: item.productId } : null
  }
  if (!Number.isInteger(id) || id < 1) return null
  if (kind === 'product') {
    const records = await listingRecords({ id, order: 'desc' }, FIRST_PAGE)
    return records.items[0] ? { name: records.items[0].name } : null
  }
  const records = await itemRecords({ id, order: 'desc' }, FIRST_PAGE)
  return records.items[0] ? { name: records.items[0].name, sku: records.items[0].sku } : null
}
