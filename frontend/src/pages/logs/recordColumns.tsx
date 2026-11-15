import { Tag } from 'antd'
import type { ItemRecord, ListingRecord, OrderRecord, OrderResult, StockMovement } from '../../api/logs'
import { RecordOrderNumber } from '../../components/RecordOrderNumber'
import { CellText, type RecordColumn } from '../../components/RecordTable'
import { formatTime } from '../../lib/days'
import { formatPrice } from '../../lib/format'
import { TEXT } from '../../text'

/** The columns of each kind of record (What the records show in docs/database/database-design.md). */

const RESULT_COLOR: Record<OrderResult, string | undefined> = {
  COMPLETED: 'success',
  PRODUCT_DELISTED: undefined,
  PRODUCT_CHANGED: 'processing',
  OUT_OF_STOCK: 'error',
  INVALID_QUANTITY: 'error',
  BUSY: 'processing',
}

/** An amount with its sign: `+10` into the stock, `−3` out of it. */
function signed(amount: number) {
  return <span className={amount > 0 ? 'amount amount--in' : 'amount amount--out'}>{amount > 0 ? `+${amount}` : `−${Math.abs(amount)}`}</span>
}

const time = <T extends { at: string }>(): RecordColumn<T> => ({ key: 'at', title: TEXT.logs.time, width: 170, render: (row) => formatTime(row.at) })

export const LISTING_COLUMNS: RecordColumn<ListingRecord>[] = [
  time(),
  { key: 'event', title: TEXT.logs.event, width: 110, render: (row) => <Tag color={row.action === 'LISTED' ? 'success' : undefined}>{row.action === 'LISTED' ? TEXT.logs.listed : TEXT.logs.delisted}</Tag> },
  { key: 'name', title: TEXT.logs.nameAtTime, grow: true, render: (row) => <CellText text={row.name} /> },
  { key: 'price', title: TEXT.logs.priceAtTime, width: 140, render: (row) => formatPrice(row.price) },
  { key: 'by', title: TEXT.logs.by, width: 120, render: (row) => row.by },
]

export const ORDER_COLUMNS: RecordColumn<OrderRecord>[] = [
  time(),
  { key: 'product', title: TEXT.logs.product, grow: true, render: (row) => <CellText text={row.productName} /> },
  { key: 'orderNo', title: TEXT.logs.orderNumber, width: 260, render: (row) => <RecordOrderNumber orderNo={row.orderNo} /> },
  { key: 'by', title: TEXT.logs.orderedBy, width: 110, render: (row) => row.orderedBy },
  { key: 'quantity', title: TEXT.logs.quantity, width: 90, render: (row) => row.quantity },
  { key: 'result', title: TEXT.logs.result, width: 130, render: (row) => <Tag color={RESULT_COLOR[row.result]}>{TEXT.logs.results[row.result]}</Tag> },
  { key: 'deducted', title: TEXT.logs.stockDeducted, width: 120, render: (row) => row.stockDeducted },
]

export const ITEM_COLUMNS: RecordColumn<ItemRecord>[] = [
  time(),
  { key: 'event', title: TEXT.logs.event, width: 110, render: (row) => <Tag color={row.action === 'CREATED' ? 'success' : undefined}>{row.action === 'CREATED' ? TEXT.logs.added : TEXT.logs.deleted}</Tag> },
  { key: 'name', title: TEXT.logs.nameAtTime, grow: true, render: (row) => <CellText text={row.name} /> },
  { key: 'sku', title: TEXT.logs.sku, width: 120, render: (row) => row.sku },
  { key: 'quantity', title: TEXT.logs.quantity, width: 100, render: (row) => row.quantity },
  { key: 'by', title: TEXT.logs.by, width: 120, render: (row) => row.by },
]

export const MOVEMENT_COLUMNS: RecordColumn<StockMovement>[] = [
  time(),
  { key: 'item', title: TEXT.logs.item, grow: true, render: (row) => <CellText text={row.itemName} /> },
  { key: 'type', title: TEXT.logs.type, width: 110, render: (row) => TEXT.logs.types[row.type] },
  { key: 'orderNo', title: TEXT.logs.orderNumber, width: 260, render: (row) => (row.orderNo ? <RecordOrderNumber orderNo={row.orderNo} /> : '—') },
  { key: 'reason', title: TEXT.logs.reason, grow: true, render: (row) => (row.reason ? <CellText text={row.reason} /> : '—') },
  { key: 'amount', title: TEXT.logs.amount, width: 90, render: (row) => signed(row.amount) },
  { key: 'by', title: TEXT.logs.by, width: 110, render: (row) => row.by },
]
