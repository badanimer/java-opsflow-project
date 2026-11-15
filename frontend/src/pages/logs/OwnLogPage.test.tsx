import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { DeletedItem, ItemRecord, ListingRecord } from '../../api/logs'
import { FakeServer, me, openApp, storeToken } from '../../test/server'

/**
 * The own log of one product or item, and the Deleted items module (Figma frames "Product own log / ...",
 * "Item own log / ...", "Deleted items / ...", "Deleted item with product / ...", "Deleted item without
 * product / ...").
 */

const LISTED: ListingRecord = { id: 7, at: '2026-11-15T04:58:00Z', productId: 5, inventoryId: 3, action: 'LISTED', name: 'A4 paper', price: '12.50', by: 'mary' }
const ADDED: ItemRecord = { id: 4, at: '2026-11-14T04:58:00Z', inventoryId: 3, action: 'CREATED', sku: 'A4Q2M9X1', name: 'A4 paper', quantity: 40, by: 'mary' }
const ROLLS: DeletedItem = { id: 9, sku: 'F4Z2K6M8', name: 'Label rolls', deletedAt: '2026-11-15T04:58:00Z', deletedBy: 'mary', productId: 11 }
const BOXES: DeletedItem = { id: 10, sku: 'D7G2L4R9', name: 'Mailing boxes', deletedAt: '2026-11-14T04:58:00Z', deletedBy: 'olive', productId: null }

function page(items: unknown[]) {
  return { status: 200, body: { items, nextCursor: null, prevCursor: null } }
}

function serve(answers: Record<string, unknown>) {
  storeToken()
  const server = new FakeServer().on('GET /auth/me', me(['VIEWER'], { id: 20, username: 'vera' }))
  for (const [request, answer] of Object.entries(answers)) server.on(request, answer as never)
  return server
}

describe('the own log of a product', () => {
  it('opens from View on a card on its order records, with its listing records as the second tab', async () => {
    const server = serve({
      'GET /products': page([{ id: 5, name: 'A4 paper', price: '12.50', imageUrl: null }]),
      'GET /listing-records?productId=5': page([LISTED]),
      'GET /order-records?productId=5': page([]),
    })
    const user = openApp('/')
    await user.click(await screen.findByRole('button', { name: 'View A4 paper' }))
    expect(await screen.findByRole('heading', { name: 'A4 paper' })).toBeInTheDocument()
    expect(screen.getByText('Order and listing records')).toBeInTheDocument()
    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['Order records', 'Listing records'])
    expect(await screen.findByText('No records yet')).toBeInTheDocument()
    expect(server.callsTo('GET /order-records?productId=5')).toHaveLength(1)
    expect(screen.queryByRole('textbox', { name: 'Order number' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Listing records' }))
    expect(await within(screen.getByRole('region', { name: 'Listing records' })).findByText('$12.50')).toBeInTheDocument()
  })

  it('finds its name from its records after a reload', async () => {
    serve({ 'GET /listing-records?productId=5': page([LISTED]) })
    openApp('/products/5/records')
    expect(await screen.findByRole('heading', { name: 'A4 paper' })).toBeInTheDocument()
  })
})

describe('the own log of an item', () => {
  it('opens from View on a row on its stock movements, with its item records as the second tab', async () => {
    const server = serve({
      'GET /items': page([{ id: 3, sku: 'A4Q2M9X1', name: 'A4 paper', imageUrl: null, availableQuantity: 40, listed: false }]),
      'GET /item-records?inventoryId=3': page([ADDED]),
      'GET /stock-movements?inventoryId=3': page([]),
    })
    const user = openApp('/stock')
    await user.click(await screen.findByRole('button', { name: 'View A4 paper' }))
    expect(await screen.findByText('Stock movements and item records')).toBeInTheDocument()
    await waitFor(() => expect(server.callsTo('GET /stock-movements?inventoryId=3')).toHaveLength(1))
    await user.click(screen.getByRole('tab', { name: 'Item records' }))
    expect(await within(screen.getByRole('region', { name: 'Item records' })).findByText('Added')).toBeInTheDocument()
  })
})

describe('the deleted items', () => {
  it('are listed newest first, found by name or SKU, and View opens four parts', async () => {
    const server = serve({
      'GET /deleted-items': page([ROLLS, BOXES]),
      'GET /deleted-items?search=F4Z2': page([ROLLS]),
      'GET /stock-movements?inventoryId=9': page([]),
    })
    const user = openApp('/stock/records/deleted')
    const list = await screen.findByRole('region', { name: 'Deleted items' })
    expect(await within(list).findByText('Mailing boxes')).toBeInTheDocument()
    await user.type(screen.getByRole('searchbox', { name: 'Search by name or SKU' }), 'F4Z2{Enter}')
    await waitFor(() => expect(within(list).queryByText('Mailing boxes')).not.toBeInTheDocument())
    expect(server.callsTo('GET /deleted-items?search=F4Z2')).toHaveLength(1)

    await user.click(within(list).getByRole('button', { name: 'View Label rolls' }))
    expect(await screen.findByText('Deleted item records · SKU F4Z2K6M8')).toBeInTheDocument()
    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['Stock movements', 'Item records', 'Order records', 'Listing records'])
  })

  it('opens two parts for an item that never had a product, also after a reload', async () => {
    serve({ 'GET /deleted-items?search=D7G2L4R9': page([BOXES]), 'GET /stock-movements?inventoryId=10': page([]) })
    openApp('/stock/records/deleted/D7G2L4R9')
    expect(await screen.findByRole('heading', { name: 'Mailing boxes' })).toBeInTheDocument()
    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['Stock movements', 'Item records'])
  })

  it('say when there is none, and when a search finds none', async () => {
    serve({ 'GET /deleted-items': page([]) })
    openApp('/stock/records/deleted')
    expect(await screen.findByText('No deleted items')).toBeInTheDocument()
    expect(screen.getByText('Deleted items will appear here.')).toBeInTheDocument()
  })
})
