import { api } from './client'

/** Placing an order, in Orders and Stock Changes of docs/api/api-design.md. */

export interface OrderLine {
  productId: number
  sku: string
  name: string
  unitPrice: string
  quantity: number
  subtotal: string
}

export interface PlacedOrder {
  id: number
  orderNo: string
  totalAmount: string
  itemCount: number
  createdAt: string
  lines: OrderLine[]
}

/**
 * Places an order of one product. `name` and `price` are the ones the page showed. `key` is the
 * idempotency key of the dialog: a retry sends the same key, so a repeat never places a second order. A
 * refusal that the server recorded carries its `orderNo` on the ApiError.
 */
export function placeOrder(productId: number, quantity: number, name: string, price: string, key: string): Promise<PlacedOrder> {
  return api<PlacedOrder>('/orders', {
    method: 'POST',
    body: { lines: [{ productId, quantity, name, price }], remark: null },
    headers: { 'Idempotency-Key': key },
  })
}
