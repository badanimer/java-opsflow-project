import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ListableItem } from '../../api/products'
import { FakeServer, me, openApp, problem, storeToken } from '../../test/server'

/**
 * List item (Figma frames "List item / ..."): the catalog, then the name and the price, and every answer
 * of the server.
 */

const WALLETS: ListableItem = { inventoryId: 2, sku: 'E7B4N8Z6', name: 'Document wallets', imageUrl: null, availableQuantity: 12 }
const TAPE: ListableItem = { inventoryId: 3, sku: 'K8L4R2C3', name: 'Kraft packing tape', imageUrl: null, availableQuantity: 0 }

function page(items: unknown[], nextCursor: string | null = null) {
  return { status: 200, body: { items, nextCursor, prevCursor: null } }
}

async function openListing(roles: string[], catalog: ListableItem[]) {
  storeToken()
  const server = new FakeServer().on('GET /auth/me', me(roles, { id: 20, username: 'morgan' })).on('GET /products/listable', page(catalog))
  const user = openApp('/')
  await user.click(await screen.findByRole('button', { name: 'List item' }))
  const dialog = await screen.findByRole('dialog', { name: 'List item' })
  if (catalog.length > 0) await within(dialog).findByText(catalog[0].name)
  return { server, user, dialog }
}

function catalogRow(dialog: HTMLElement, name: string): HTMLElement {
  return within(dialog).getByText(name).closest('tr') as HTMLElement
}

async function choose(user: Awaited<ReturnType<typeof openListing>>['user'], dialog: HTMLElement, name: string) {
  await user.click(within(catalogRow(dialog, name)).getByRole('button', { name: 'Choose' }))
  return within(dialog).findByLabelText('Price')
}

describe('the catalog', () => {
  it('offers the items that can be listed, and an item with no stock cannot be chosen', async () => {
    const { dialog } = await openListing(['MANAGER'], [WALLETS, TAPE])
    expect(within(catalogRow(dialog, 'Document wallets')).getByText('E7B4N8Z6')).toBeInTheDocument()
    expect(within(catalogRow(dialog, 'Document wallets')).getByRole('button', { name: 'Choose' })).toBeInTheDocument()
    expect(within(catalogRow(dialog, 'Kraft packing tape')).queryByRole('button', { name: 'Choose' })).not.toBeInTheDocument()
    expect(within(catalogRow(dialog, 'Kraft packing tape')).getByText('No stock available.')).toBeInTheDocument()
  })

  it('searches, and says when nothing is found', async () => {
    const { server, user, dialog } = await openListing(['MANAGER'], [WALLETS, TAPE])
    server.on('GET /products/listable?search=zzzz', page([]))
    await user.type(within(dialog).getByRole('searchbox', { name: 'Search by name' }), 'zzzz{Enter}')
    expect(await within(dialog).findByText('No items found')).toBeInTheDocument()
  })

  it('says there is nothing to list, and Go to Stock opens the stock page', async () => {
    const { server, user, dialog } = await openListing(['MANAGER'], [])
    server.on('GET /items', page([]))
    expect(await within(dialog).findByText('No items to list')).toBeInTheDocument()
    expect(within(dialog).getByText('Items that are not on sale appear here. Add items on the Stock page.')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Go to Stock' }))
    expect(await screen.findByRole('heading', { name: 'Stock' })).toBeInTheDocument()
  })

  it('shows a failed load with its reference and tries again', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', me(['MANAGER'], { id: 20 })).on('GET /products/listable', problem(500, 'INTERNAL_ERROR'))
    const user = openApp('/')
    await user.click(await screen.findByRole('button', { name: 'List item' }))
    const dialog = await screen.findByRole('dialog', { name: 'List item' })
    expect(await within(dialog).findByText('Could not load warehouse items')).toBeInTheDocument()
    expect(within(dialog).getByText(/report this reference/)).toBeInTheDocument()
    server.on('GET /products/listable', page([WALLETS]))
    await user.click(within(dialog).getByRole('button', { name: 'Try again' }))
    expect(await within(dialog).findByText('Document wallets')).toBeInTheDocument()
  })
})

describe('the name and the price', () => {
  it('starts with the name of the item and lists with the typed price, with no second confirmation', async () => {
    const { server, user, dialog } = await openListing(['MANAGER'], [WALLETS])
    server.on('POST /products', { status: 201, body: { id: 9, inventoryId: 2, name: 'Wallets, A4', price: '8.00', status: 'LISTED' } })
    const price = await choose(user, dialog, 'Document wallets')
    expect(within(dialog).getByText('SKU E7B4N8Z6')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Product name')).toHaveValue('Document wallets')
    expect(within(dialog).getByRole('button', { name: 'List' })).toBeDisabled()

    await user.clear(within(dialog).getByLabelText('Product name'))
    await user.type(within(dialog).getByLabelText('Product name'), ' Wallets, A4 ')
    await user.type(price, '8.00')
    await user.click(within(dialog).getByRole('button', { name: 'List' }))

    expect(await screen.findByText('Item listed.')).toBeInTheDocument()
    expect(server.callsTo('POST /products')[0].body).toEqual({ inventoryId: 2, name: 'Wallets, A4', price: '8.00' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  for (const typed of ['0', '2.345', 'abc', '12345678901']) {
    it(`refuses the price ${typed} in the page`, async () => {
      const { server, user, dialog } = await openListing(['MANAGER'], [WALLETS])
      const price = await choose(user, dialog, 'Document wallets')
      await user.type(price, typed)
      await user.click(within(dialog).getByRole('button', { name: 'List' }))
      expect(within(dialog).getByText('Enter a price from 0.01 to 9,999,999,999.99 with up to two decimal places.')).toBeInTheDocument()
      expect(server.callsTo('POST /products')).toHaveLength(0)
    })
  }

  it('requires a name', async () => {
    const { user, dialog } = await openListing(['MANAGER'], [WALLETS])
    const price = await choose(user, dialog, 'Document wallets')
    await user.clear(within(dialog).getByLabelText('Product name'))
    await user.type(price, '1')
    await user.click(within(dialog).getByRole('button', { name: 'List' }))
    expect(within(dialog).getByText('Enter a name.')).toBeInTheDocument()
  })

  it('says when the item has no stock or is already on sale, and Back returns to a fresh catalog', async () => {
    const { server, user, dialog } = await openListing(['MANAGER'], [WALLETS])
    server.on('POST /products', problem(409, 'NO_STOCK'))
    let price = await choose(user, dialog, 'Document wallets')
    await user.type(price, '8')
    await user.click(within(dialog).getByRole('button', { name: 'List' }))
    expect(await within(dialog).findByText('This item has no stock. Choose another item.')).toBeInTheDocument()

    server.on('POST /products', problem(409, 'ALREADY_LISTED'))
    await user.click(within(dialog).getByRole('button', { name: 'List' }))
    expect(await within(dialog).findByText('This item is already on sale. Choose another item.')).toBeInTheDocument()

    const before = server.callsTo('GET /products/listable').length
    await user.click(within(dialog).getByRole('button', { name: 'Back' }))
    await waitFor(() => expect(server.callsTo('GET /products/listable').length).toBe(before + 1))
    price = await choose(user, dialog, 'Document wallets')
    expect(price).toHaveValue('')
  })

  it('shows the item gone, a price the server refused, and no answer, and keeps the draft', async () => {
    const { server, user, dialog } = await openListing(['MANAGER'], [WALLETS])
    const price = await choose(user, dialog, 'Document wallets')
    await user.type(price, '8')

    server.on('POST /products', problem(404, 'NOT_FOUND'))
    await user.click(within(dialog).getByRole('button', { name: 'List' }))
    expect(await within(dialog).findByText('This item is no longer available.')).toBeInTheDocument()

    server.on('POST /products', problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'price', message: 'must be above 0' }] }))
    await user.click(within(dialog).getByRole('button', { name: 'List' }))
    expect(await within(dialog).findByText('Enter a price from 0.01 to 9,999,999,999.99 with up to two decimal places.')).toBeInTheDocument()

    server.on('POST /products', 'network-error')
    await user.click(within(dialog).getByRole('button', { name: 'List' }))
    expect(await within(dialog).findByText('Cannot reach the server. Check your connection and try again.')).toBeInTheDocument()
    expect(price).toHaveValue('8')
  })

  it('ends with Close when the permission was taken away', async () => {
    const { server, user, dialog } = await openListing(['MANAGER'], [WALLETS])
    server.on('POST /products', problem(403, 'FORBIDDEN'))
    const price = await choose(user, dialog, 'Document wallets')
    await user.type(price, '8')
    await user.click(within(dialog).getByRole('button', { name: 'List' }))
    expect(await within(dialog).findByText('You no longer have permission to make this change.')).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'List' })).not.toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Close' })).toBeInTheDocument()
  })
})
