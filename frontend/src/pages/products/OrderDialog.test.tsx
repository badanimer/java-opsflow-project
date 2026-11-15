import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ShopProduct } from '../../api/products'
import { FakeServer, me, openApp, problem, storeToken } from '../../test/server'

/**
 * The order dialog (Figma frames "Place order / ..."): the quantity and its checks, the order with its
 * idempotency key, every result with its order number, and a retry that sends the same key.
 */

const PAPER: ShopProduct = { id: 5, name: 'A4 paper', price: '12.50', imageUrl: null }
const NUMBER = 'ORD-3f2b21a6e2414878a36d3c01ce0806bf'

function placed(quantity: number) {
  const total = (12.5 * quantity).toFixed(2)
  return {
    status: 201,
    body: { id: 41, orderNo: NUMBER, totalAmount: total, itemCount: 1, createdAt: '2026-11-16T01:00:00Z',
      lines: [{ productId: 5, sku: 'A4Q2M9X1', name: 'A4 paper', unitPrice: '12.50', quantity, subtotal: total }] },
  }
}

async function openOrder(roles: string[] = ['OPERATOR']) {
  storeToken()
  const server = new FakeServer()
    .on('GET /auth/me', me(roles, { id: 20, username: 'olive' }))
    .on('GET /products', { status: 200, body: { items: [PAPER], nextCursor: null, prevCursor: null } })
  const user = openApp('/')
  const name = await within(await screen.findByRole('region', { name: 'Product list' })).findByText('A4 paper')
  await user.click(within(name.closest('li') as HTMLElement).getByRole('button', { name: 'Order' }))
  const dialog = await screen.findByRole('dialog', { name: 'Place order' })
  return { server, user, dialog }
}

function quantityField(dialog: HTMLElement) {
  return within(dialog).getByRole('textbox', { name: 'Quantity' })
}

/** The Close button of the footer; the X in the corner of a dialog has the same name. */
function closeButton(dialog: HTMLElement): HTMLElement {
  return within(dialog).getAllByRole('button', { name: 'Close' }).find((button) => button.textContent === 'Close')!
}

describe('the quantity', () => {
  it('starts at 1, steps with minus and plus, and stops at 1 and at 9,999', async () => {
    const { user, dialog } = await openOrder()
    expect(quantityField(dialog)).toHaveValue('1')
    expect(within(dialog).getByRole('button', { name: 'Decrease quantity' })).toBeDisabled()
    await user.click(within(dialog).getByRole('button', { name: 'Increase quantity' }))
    expect(quantityField(dialog)).toHaveValue('2')
    await user.clear(quantityField(dialog))
    await user.type(quantityField(dialog), '9999')
    expect(within(dialog).getByRole('button', { name: 'Increase quantity' })).toBeDisabled()
  })

  it('refuses a quantity that is not a whole number from 1 to 9,999 and sends nothing', async () => {
    const { server, user, dialog } = await openOrder()
    for (const typed of ['0', '10000', '1.5', '']) {
      await user.clear(quantityField(dialog))
      if (typed) await user.type(quantityField(dialog), typed)
      await user.click(within(dialog).getByRole('button', { name: 'Place order' }))
      expect(within(dialog).getByText('Enter a whole number from 1 to 9,999.')).toBeInTheDocument()
    }
    expect(server.callsTo('POST /orders')).toHaveLength(0)
  })
})

describe('placing', () => {
  it('sends the product as shown with a key, shows the number and the total, and copies the number', async () => {
    const { server, user, dialog } = await openOrder()
    server.on('POST /orders', placed(2))
    await user.click(within(dialog).getByRole('button', { name: 'Increase quantity' }))
    await user.click(within(dialog).getByRole('button', { name: 'Place order' }))

    const result = await screen.findByRole('dialog', { name: 'Order placed' })
    expect(within(result).getByText(NUMBER)).toBeInTheDocument()
    expect(within(result).getByText('$25.00')).toBeInTheDocument()
    const call = server.callsTo('POST /orders')[0]
    expect(call.body).toEqual({ lines: [{ productId: 5, quantity: 2, name: 'A4 paper', price: '12.50' }], remark: null })
    expect(call.idempotencyKey).toMatch(/^[0-9a-f-]{36}$/)

    await user.click(within(result).getByRole('button', { name: 'Copy order number' }))
    expect(await within(result).findByRole('button', { name: 'Copied' })).toBeInTheDocument()
    await expect(navigator.clipboard.readText()).resolves.toBe(NUMBER)

    const loads = server.callsTo('GET /products').length
    await user.click(closeButton(result))
    await waitFor(() => expect(server.callsTo('GET /products').length).toBe(loads + 1))
  })

  it('shows a recorded refusal with its number, and a delisted product reloads the page on Close', async () => {
    const { server, user, dialog } = await openOrder()
    server.on('POST /orders', problem(409, 'PRODUCT_DELISTED', { orderNo: NUMBER }))
    await user.click(within(dialog).getByRole('button', { name: 'Place order' }))
    const result = await screen.findByRole('dialog', { name: 'Order not placed' })
    expect(within(result).getByText('This product has been delisted.')).toBeInTheDocument()
    expect(within(result).getByText(NUMBER)).toBeInTheDocument()
    expect(within(result).queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()

    const loads = server.callsTo('GET /products').length
    await user.click(closeButton(result))
    await waitFor(() => expect(server.callsTo('GET /products').length).toBe(loads + 1))
  })

  it('says there is not enough stock, and never how much is left', async () => {
    const { server, user, dialog } = await openOrder()
    server.on('POST /orders', problem(409, 'OUT_OF_STOCK', { orderNo: NUMBER }))
    await user.click(within(dialog).getByRole('button', { name: 'Place order' }))
    const result = await screen.findByRole('dialog', { name: 'Order not placed' })
    expect(within(result).getByText('There is not enough stock to place this order.')).toBeInTheDocument()
    expect(within(result).getByText(NUMBER)).toBeInTheDocument()
  })

  it('tries again with the same key after Busy, and places the order once', async () => {
    const { server, user, dialog } = await openOrder()
    server.on('POST /orders', problem(503, 'BUSY', { orderNo: 'ORD-00000000000000000000000000000001' }))
    await user.click(within(dialog).getByRole('button', { name: 'Place order' }))
    const busy = await screen.findByRole('dialog', { name: 'Order not placed' })
    expect(within(busy).getByText('The system is busy. Please try again.')).toBeInTheDocument()

    server.on('POST /orders', placed(1))
    await user.click(within(busy).getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('dialog', { name: 'Order placed' })).toBeInTheDocument()
    const [first, second] = server.callsTo('POST /orders')
    expect(second.idempotencyKey).toBe(first.idempotencyKey)
  })

  it('says the status is unknown when no answer came, and tries again with the same key', async () => {
    const { server, user, dialog } = await openOrder()
    server.on('POST /orders', 'network-error')
    await user.click(within(dialog).getByRole('button', { name: 'Place order' }))
    const unknown = await screen.findByRole('dialog', { name: 'Order status unknown' })
    expect(within(unknown).getByText('Cannot reach the server. Check your connection and try again.')).toBeInTheDocument()

    server.on('POST /orders', { ...placed(1), status: 200 })
    await user.click(within(unknown).getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('dialog', { name: 'Order placed' })).toBeInTheDocument()
    const [first, second] = server.callsTo('POST /orders')
    expect(second.idempotencyKey).toBe(first.idempotencyKey)
  })

  it('shows a fault with its reference and a lost permission, each with Close only', async () => {
    const { server, user, dialog } = await openOrder()
    server.on('POST /orders', problem(500, 'INTERNAL_ERROR'))
    await user.click(within(dialog).getByRole('button', { name: 'Place order' }))
    const fault = await screen.findByRole('dialog', { name: 'Could not confirm order' })
    expect(within(fault).getByText(/report this reference: ddddc8f3b64942ff/)).toBeInTheDocument()
    expect(within(fault).queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })
})

describe('who may order', () => {
  it('gives the Order button to an operator and a manager, and not to a viewer', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', me(['VIEWER'])).on('GET /products', { status: 200, body: { items: [PAPER], nextCursor: null, prevCursor: null } })
    openApp('/')
    await within(await screen.findByRole('region', { name: 'Product list' })).findByText('A4 paper')
    expect(screen.queryByRole('button', { name: 'Order' })).not.toBeInTheDocument()
  })
})
