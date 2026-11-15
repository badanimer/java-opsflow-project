import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ShopProduct } from '../../api/products'
import { FakeServer, me, openApp, problem, storeToken } from '../../test/server'
import { setScreenWidth } from '../../test/screen'

/**
 * The product page (Product page in docs/frontend/page-design.md, Figma page "Stock and Products"): the
 * cards, the search, the paging, the states of the list, and delisting.
 */

const PAPER: ShopProduct = { id: 5, name: 'A4 paper', price: '12.50', imageUrl: '/api/v1/images/items/paper.webp' }
const TAPE: ShopProduct = { id: 6, name: 'Clear packing tape', price: '3.50', imageUrl: null }

function page(items: ShopProduct[], nextCursor: string | null = null, prevCursor: string | null = null) {
  return { status: 200, body: { items, nextCursor, prevCursor } }
}

async function openProducts(roles: string[], items: ShopProduct[]) {
  storeToken()
  const server = new FakeServer().on('GET /auth/me', me(roles, { id: 20, username: 'morgan' })).on('GET /products', page(items))
  const user = openApp('/')
  await screen.findByRole('searchbox', { name: 'Search by name' })
  if (items.length > 0) await within(await screen.findByRole('region', { name: 'Product list' })).findByText(items[0].name)
  return { server, user }
}

function card(name: string): HTMLElement {
  return within(screen.getByRole('region', { name: 'Product list' })).getByText(name).closest('li') as HTMLElement
}

describe('the cards', () => {
  it('show the picture, the name, and the price in Australian dollars, and nothing else', async () => {
    await openProducts(['VIEWER'], [PAPER, TAPE])
    const paper = card('A4 paper')
    expect(within(paper).getByText('$12.50')).toBeInTheDocument()
    expect(paper.querySelector('img')).toHaveAttribute('src', '/api/v1/images/items/paper.webp')
    expect(card('Clear packing tape').querySelector('.item-thumbnail--empty')).not.toBeNull()
    expect(within(paper).queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'List item' })).not.toBeInTheDocument()
  })

  it('give a manager List item and Delist', async () => {
    await openProducts(['MANAGER'], [PAPER])
    expect(screen.getByRole('button', { name: 'List item' })).toBeInTheDocument()
    expect(within(card('A4 paper')).getByRole('button', { name: 'Delist' })).toBeInTheDocument()
  })

  it('stay cards on a small screen', async () => {
    setScreenWidth(390)
    await openProducts(['OPERATOR'], [PAPER, TAPE])
    expect(within(card('Clear packing tape')).getByText('$3.50')).toBeInTheDocument()
  })
})

describe('the search, the paging, and the states', () => {
  it('searches by name and says when nothing is found', async () => {
    const { server, user } = await openProducts(['VIEWER'], [PAPER, TAPE])
    server.on('GET /products?search=tape', page([TAPE]))
    server.on('GET /products?search=zzzz', page([]))
    await user.type(screen.getByRole('searchbox', { name: 'Search by name' }), 'tape{Enter}')
    await waitFor(() => expect(within(screen.getByRole('region', { name: 'Product list' })).queryByText('A4 paper')).not.toBeInTheDocument())
    await user.clear(screen.getByRole('searchbox', { name: 'Search by name' }))
    await user.type(screen.getByRole('searchbox', { name: 'Search by name' }), 'zzzz{Enter}')
    expect(await screen.findByText('No products found')).toBeInTheDocument()
  })

  it('pages with Next and Previous', async () => {
    const { server, user } = await openProducts(['VIEWER'], [])
    server.on('GET /products?search=a', page([PAPER], 'c1'))
    server.on('GET /products?search=a&cursor=c1&direction=next', page([TAPE], null, 'p1'))
    await user.type(screen.getByRole('searchbox', { name: 'Search by name' }), 'a{Enter}')
    await user.click(await screen.findByRole('button', { name: 'Next' }))
    expect(await within(screen.getByRole('region', { name: 'Product list' })).findByText('Clear packing tape')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeInTheDocument()
  })

  it('tells a manager how to list the first product, and others that products will appear', async () => {
    await openProducts(['MANAGER'], [])
    expect(await screen.findByText('List an item from Stock to get started.')).toBeInTheDocument()
  })

  it('tells a viewer that listed products will appear', async () => {
    await openProducts(['VIEWER'], [])
    expect(await screen.findByText('Listed products will appear here.')).toBeInTheDocument()
  })

  it('shows a fault with its reference and tries again', async () => {
    const { server, user } = await openProducts(['VIEWER'], [])
    server.on('GET /products?search=x', problem(500, 'INTERNAL_ERROR'))
    await user.type(screen.getByRole('searchbox', { name: 'Search by name' }), 'x{Enter}')
    expect(await screen.findByText(/report this reference/)).toBeInTheDocument()
    server.on('GET /products?search=x', page([TAPE]))
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await within(screen.getByRole('region', { name: 'Product list' })).findByText('Clear packing tape')).toBeInTheDocument()
  })
})

describe('delisting', () => {
  it('asks first, and the product leaves the page', async () => {
    const { server, user } = await openProducts(['MANAGER'], [PAPER, TAPE])
    server.on('POST /products/6/delist', { status: 200, body: { id: 6, inventoryId: 3, name: 'Clear packing tape', price: '3.50', status: 'UNLISTED' } })
    await user.click(within(card('Clear packing tape')).getByRole('button', { name: 'Delist' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delist Clear packing tape?' })
    expect(within(dialog).getByText('This product will be removed from sale. Orders already placed will stay as they are.')).toBeInTheDocument()
    expect(server.callsTo('POST /products/6/delist')).toHaveLength(0)

    server.on('GET /products', page([PAPER]))
    await user.click(within(dialog).getByRole('button', { name: 'Delist product' }))
    expect(await screen.findByText('Product delisted.')).toBeInTheDocument()
    await waitFor(() => expect(within(screen.getByRole('region', { name: 'Product list' })).queryByText('Clear packing tape')).not.toBeInTheDocument())
  })

  it('ends with Close when someone delisted it meanwhile or it is gone', async () => {
    const { server, user } = await openProducts(['MANAGER'], [PAPER, TAPE])
    server.on('POST /products/6/delist', problem(409, 'ALREADY_DELISTED'))
    await user.click(within(card('Clear packing tape')).getByRole('button', { name: 'Delist' }))
    let dialog = await screen.findByRole('dialog', { name: 'Delist Clear packing tape?' })
    await user.click(within(dialog).getByRole('button', { name: 'Delist product' }))
    expect(await within(dialog).findByText('This product has already been delisted.')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Close' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    server.on('POST /products/5/delist', problem(404, 'NOT_FOUND'))
    await user.click(within(card('A4 paper')).getByRole('button', { name: 'Delist' }))
    dialog = await screen.findByRole('dialog', { name: 'Delist A4 paper?' })
    await user.click(within(dialog).getByRole('button', { name: 'Delist product' }))
    expect(await within(dialog).findByText('This product is no longer available.')).toBeInTheDocument()
  })

  it('keeps the dialog for another try after no answer', async () => {
    const { server, user } = await openProducts(['MANAGER'], [TAPE])
    server.on('POST /products/6/delist', 'network-error')
    await user.click(within(card('Clear packing tape')).getByRole('button', { name: 'Delist' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delist Clear packing tape?' })
    await user.click(within(dialog).getByRole('button', { name: 'Delist product' }))
    expect(await within(dialog).findByText('Cannot reach the server. Check your connection and try again.')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Delist product' })).toBeEnabled()
  })
})
