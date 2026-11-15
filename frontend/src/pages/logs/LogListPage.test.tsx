import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ListingRecord, OrderRecord, StockMovement } from '../../api/logs'
import { quickRangeDays, todayInSydney } from '../../lib/days'
import { FakeServer, me, openApp, problem, storeToken } from '../../test/server'

/**
 * All records of one kind (Figma frames "Listing records / ...", "Order records / ...", "Stock movements
 * / ..."): the chooser, the columns, the direction, the quick ranges, the picker, the order number used
 * alone, the paging, and the failures.
 */

const NUMBER = 'ORD-3f2b21a6e2414878a36d3c01ce0806bf'
const LISTED: ListingRecord = { id: 7, at: '2026-11-15T04:58:00Z', productId: 5, inventoryId: 3, action: 'LISTED', name: 'A4 paper', price: '12.50', by: 'mary' }
const ORDERED: OrderRecord = { id: 12, at: '2026-11-15T04:58:00Z', productId: 5, productName: 'A4 paper', orderNo: NUMBER, orderedBy: 'olive', quantity: 3, result: 'OUT_OF_STOCK', stockDeducted: 0 }
const SOLD: StockMovement = { id: 20, at: '2026-11-15T04:58:00Z', inventoryId: 3, sku: 'A4Q2M9X1', itemName: 'A4 paper', type: 'SALE', orderNo: NUMBER, reason: null, amount: -3, by: 'olive' }
const STOCKED: StockMovement = { id: 19, at: '2026-11-15T03:00:00Z', inventoryId: 3, sku: 'A4Q2M9X1', itemName: 'A4 paper', type: 'RESTOCK', orderNo: null, reason: 'Delivery', amount: 10, by: 'mary' }

function page(items: unknown[], nextCursor: string | null = null, prevCursor: string | null = null) {
  return { status: 200, body: { items, nextCursor, prevCursor } }
}

async function openList(path: string, answers: Record<string, unknown>, roles = ['VIEWER']) {
  storeToken()
  const server = new FakeServer().on('GET /auth/me', me(roles, { id: 20, username: 'vera' }))
  for (const [request, answer] of Object.entries(answers)) server.on(request, answer as never)
  const user = openApp(path)
  return { server, user }
}

describe('the chooser', () => {
  it('opens the listing records from View records on the product page', async () => {
    const { user } = await openList('/', { 'GET /listing-records': page([LISTED]) })
    await user.click(await screen.findByRole('button', { name: 'View records' }))
    const chooser = await screen.findByRole('dialog', { name: 'View records' })
    expect(within(chooser).getByText('See orders and refused attempts.')).toBeInTheDocument()
    await user.click(within(chooser).getByRole('button', { name: 'Open Listing records' }))
    expect(await screen.findByRole('heading', { name: 'Listing records' })).toBeInTheDocument()
  })

  it('offers Deleted items only with its permission', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', me(['SUPPORT'], { permissions: ['STOCK_VIEW'] })).on('GET /items', page([]))
    const user = openApp('/stock')
    await user.click(await screen.findByRole('button', { name: 'View records' }))
    const chooser = await screen.findByRole('dialog', { name: 'View records' })
    expect(within(chooser).getByText('Stock movements')).toBeInTheDocument()
    expect(within(chooser).queryByText('Deleted items')).not.toBeInTheDocument()
  })
})

describe('the listing records', () => {
  it('show the time in Sydney, the event, the name and price at that moment, and who did it', async () => {
    await openList('/products/records/listing', { 'GET /listing-records': page([LISTED]) })
    const list = await screen.findByRole('region', { name: 'Listing records' })
    expect(await within(list).findByText('15 Nov 2026 15:58')).toBeInTheDocument()
    expect(within(list).getByText('Listed')).toBeInTheDocument()
    expect(within(list).getByText('$12.50')).toBeInTheDocument()
    expect(within(list).getByText('mary')).toBeInTheDocument()
  })

  it('read oldest first, and fill both days from a quick range', async () => {
    const { server, user } = await openList('/products/records/listing', { 'GET /listing-records': page([LISTED]) })
    await within(await screen.findByRole('region', { name: 'Listing records' })).findByText('mary')
    server.on('GET /listing-records?order=asc', page([LISTED]))
    await user.click(screen.getByRole('combobox', { name: 'Sort order' }))
    await user.click(await screen.findByText('Oldest first'))
    await waitFor(() => expect(server.callsTo('GET /listing-records?order=asc')).toHaveLength(1))

    const { from, to } = quickRangeDays('days3', todayInSydney())
    const ranged = `GET /listing-records?from=${from}&to=${to}&order=asc`
    server.on(ranged, page([]))
    await user.click(screen.getByRole('combobox', { name: 'Quick range' }))
    await user.click(await screen.findByText('Last 3 days'))
    expect(await screen.findByText('No records found')).toBeInTheDocument()
    expect(server.callsTo(ranged)).toHaveLength(1)
  })

  it('narrow to one product chosen in the picker', async () => {
    const { server, user } = await openList('/products/records/listing', {
      'GET /listing-records': page([LISTED]),
      'GET /product-options': page([{ id: 5, name: 'A4 paper', sku: 'A4Q2M9X1' }, { id: 6, name: 'Tape', sku: 'K8L4R2C3' }]),
      'GET /listing-records?productId=6': page([]),
    })
    await within(await screen.findByRole('region', { name: 'Listing records' })).findByText('mary')
    await user.click(screen.getByRole('combobox', { name: 'Product' }))
    await user.click(await screen.findByTitle('Tape'))
    await waitFor(() => expect(server.callsTo('GET /listing-records?productId=6')).toHaveLength(1))
  })

  it('page with Next and Previous', async () => {
    const { server, user } = await openList('/products/records/listing', { 'GET /listing-records': page([LISTED], 'c1') })
    server.on('GET /listing-records?cursor=c1&direction=next', page([{ ...LISTED, id: 8, by: 'olive' }], null, 'p1'))
    await user.click(await screen.findByRole('button', { name: 'Next' }))
    expect(await screen.findByText('olive')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeInTheDocument()
  })

  it('show a refused filter with Clear filters, and a lost permission', async () => {
    const { server } = await openList('/products/records/listing?from=2026-11-10', { 'GET /listing-records?from=2026-11-10': problem(400, 'VALIDATION_FAILED') })
    expect(await screen.findByText('We could not apply these filters. Clear the filters and try again.')).toBeInTheDocument()
    server.on('GET /listing-records', problem(403, 'FORBIDDEN'))
    await screen.findByRole('button', { name: 'Clear filters' }).then((button) => button.click())
    expect(await screen.findByText('You do not have permission to view these records.')).toBeInTheDocument()
  })
})

describe('the order number', () => {
  it('refuses a number that is not whole, and finds one typed in any case alone', async () => {
    const { server, user } = await openList('/products/records/orders', { 'GET /order-records': page([ORDERED]) })
    const list = await screen.findByRole('region', { name: 'Order records' })
    expect(await within(list).findByText('Out of stock')).toBeInTheDocument()

    const box = screen.getByRole('textbox', { name: 'Order number' })
    await user.type(box, 'ORD-3f2b')
    await user.click(screen.getByRole('button', { name: 'Find order' }))
    expect(screen.getByText('Enter the full order number, including ORD-.')).toBeInTheDocument()

    const found = `GET /order-records?orderNo=${NUMBER}`
    server.on(found, page([ORDERED]))
    await user.clear(box)
    await user.type(box, NUMBER.toUpperCase().replace('ORD-', 'ord-'))
    await user.click(screen.getByRole('button', { name: 'Find order' }))
    await waitFor(() => expect(server.callsTo(found)).toHaveLength(1))
    expect(screen.getByRole('combobox', { name: 'Product' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Clear order number' }))
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Product' })).toBeEnabled())
  })

  it('says to check the number when nothing is found', async () => {
    await openList(`/products/records/orders?orderNo=${NUMBER}`, { [`GET /order-records?orderNo=${NUMBER}`]: page([]) })
    expect(await screen.findByText('Check the order number and try again.')).toBeInTheDocument()
  })
})

describe('the stock movements', () => {
  it('show the type, the order number of a sale, the reason of a manual change, and a signed amount', async () => {
    await openList('/stock/records/movements', { 'GET /stock-movements': page([SOLD, STOCKED]) })
    const list = await screen.findByRole('region', { name: 'Stock movements' })
    expect(await within(list).findByText('Order')).toBeInTheDocument()
    expect(within(list).getByText('−3')).toBeInTheDocument()
    expect(within(list).getByText('+10')).toBeInTheDocument()
    expect(within(list).getByText('Stock in')).toBeInTheDocument()
    expect(within(list).getByText('Delivery')).toBeInTheDocument()
    expect(within(list).getByRole('button', { name: `Copy ${NUMBER}` })).toBeInTheDocument()
  })
})
