import { api } from './client'
import { pageParams, type Page, type PageRequest } from './paging'

/** The endpoints of products in Warehouse Items, Images, and Products of docs/api/api-design.md. */

/** A product on the product page: nothing else is shown, the stock included. */
export interface ShopProduct {
  id: number
  name: string
  /** Two decimals, as a string, so that no rounding happens on the way. */
  price: string
  imageUrl: string | null
}

/** An item of the catalog of List item: not deleted and not on sale. */
export interface ListableItem {
  inventoryId: number
  sku: string
  name: string
  imageUrl: string | null
  availableQuantity: number
}

export interface Product {
  id: number
  inventoryId: number
  name: string
  price: string
  status: 'LISTED' | 'UNLISTED'
}

function query(path: string, search: string, page: PageRequest): string {
  const params = new URLSearchParams()
  const part = search.trim()
  if (part) params.append('search', part)
  pageParams(params, page)
  const text = params.toString()
  return text ? `${path}?${text}` : path
}

export function listProducts(search: string, page: PageRequest): Promise<Page<ShopProduct>> {
  return api<Page<ShopProduct>>(query('/products', search, page))
}

export function listListable(search: string, page: PageRequest): Promise<Page<ListableItem>> {
  return api<Page<ListableItem>>(query('/products/listable', search, page))
}

/** Lists an item: its first listing creates the product, a later one lists the same product again. */
export function listItem(inventoryId: number, name: string, price: string): Promise<Product> {
  return api<Product>('/products', { method: 'POST', body: { inventoryId, name, price } })
}

export function delistProduct(id: number): Promise<Product> {
  return api<Product>(`/products/${id}/delist`, { method: 'POST' })
}
