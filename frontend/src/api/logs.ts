import { api } from './client'
import { pageParams, type Page, type PageRequest } from './paging'

/** The two logs, the deleted items, and the product picker, in Logs of docs/api/api-design.md. */

export interface ListingRecord {
  id: number
  at: string
  productId: number
  inventoryId: number
  action: 'LISTED' | 'DELISTED'
  name: string
  price: string
  by: string
}

export type OrderResult = 'COMPLETED' | 'PRODUCT_DELISTED' | 'PRODUCT_CHANGED' | 'OUT_OF_STOCK' | 'INVALID_QUANTITY' | 'BUSY'

export interface OrderRecord {
  id: number
  at: string
  productId: number
  productName: string
  orderNo: string
  orderedBy: string
  quantity: number
  result: OrderResult
  stockDeducted: number
}

export interface ItemRecord {
  id: number
  at: string
  inventoryId: number
  action: 'CREATED' | 'DELETED'
  sku: string
  name: string
  quantity: number
  by: string
}

export type MovementType = 'RESTOCK' | 'WRITE_OFF' | 'ADJUST_IN' | 'ADJUST_OUT' | 'SALE'

export interface StockMovement {
  id: number
  at: string
  inventoryId: number
  sku: string
  itemName: string
  type: MovementType
  orderNo: string | null
  reason: string | null
  /** Signed: positive into the stock, negative out of it. */
  amount: number
  by: string
}

export interface DeletedItem {
  id: number
  sku: string
  name: string
  deletedAt: string
  deletedBy: string
  /** The product of the item if it ever had one. */
  productId: number | null
}

export interface ProductOption {
  id: number
  name: string
  sku: string
}

/**
 * The filters of a list of records. `id` is the chosen product or item. `orderNo` is used alone: when it
 * is set, the other filters are not sent.
 */
export interface RecordFilters {
  id?: number
  from?: string
  to?: string
  order: 'desc' | 'asc'
  orderNo?: string
}

export type RecordLoader<T> = (filters: RecordFilters, page: PageRequest) => Promise<Page<T>>

function recordQuery(path: string, idName: string, filters: RecordFilters, page: PageRequest): string {
  const params = new URLSearchParams()
  if (filters.orderNo) {
    params.append('orderNo', filters.orderNo)
  } else {
    if (filters.id !== undefined) params.append(idName, String(filters.id))
    if (filters.from) params.append('from', filters.from)
    if (filters.to) params.append('to', filters.to)
    if (filters.order === 'asc') params.append('order', 'asc')
  }
  pageParams(params, page)
  const text = params.toString()
  return text ? `${path}?${text}` : path
}

export const listingRecords: RecordLoader<ListingRecord> = (filters, page) =>
  api<Page<ListingRecord>>(recordQuery('/listing-records', 'productId', filters, page))

export const orderRecords: RecordLoader<OrderRecord> = (filters, page) =>
  api<Page<OrderRecord>>(recordQuery('/order-records', 'productId', filters, page))

export const itemRecords: RecordLoader<ItemRecord> = (filters, page) =>
  api<Page<ItemRecord>>(recordQuery('/item-records', 'inventoryId', filters, page))

export const stockMovements: RecordLoader<StockMovement> = (filters, page) =>
  api<Page<StockMovement>>(recordQuery('/stock-movements', 'inventoryId', filters, page))

function searchQuery(path: string, search: string, page: PageRequest): string {
  const params = new URLSearchParams()
  const part = search.trim()
  if (part) params.append('search', part)
  pageParams(params, page)
  const text = params.toString()
  return text ? `${path}?${text}` : path
}

export function deletedItems(search: string, page: PageRequest): Promise<Page<DeletedItem>> {
  return api<Page<DeletedItem>>(searchQuery('/deleted-items', search, page))
}

export function productOptions(search: string, page: PageRequest): Promise<Page<ProductOption>> {
  return api<Page<ProductOption>>(searchQuery('/product-options', search, page))
}

/**
 * One whole order number as the person typed it, written as the numbers are made: `ORD-` and 32 letters
 * or digits in lower case. Null when it is not an order number.
 */
export function normalizeOrderNo(typed: string): string | null {
  const trimmed = typed.trim()
  return /^ord-[0-9a-z]{32}$/i.test(trimmed) ? `ORD-${trimmed.slice(4).toLowerCase()}` : null
}
