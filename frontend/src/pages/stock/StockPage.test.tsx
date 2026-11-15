import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Item } from '../../api/items'
import { FakeServer, me, openApp, problem, storeToken } from '../../test/server'
import { setScreenWidth } from '../../test/screen'

/**
 * The stock page, Phase 4 part (Stock page in docs/frontend/page-design.md, Figma page "Stock and
 * Products"): the rows, the search, the paging, the states of the list, and deleting an item.
 */

const PAPER: Item = { id: 1, sku: 'A4Q2M9X1', name: 'A4 paper', imageUrl: '/api/v1/images/items/paper.webp', availableQuantity: 40, listed: true }
const WALLETS: Item = { id: 2, sku: 'E7B4N8Z6', name: 'Document wallets', imageUrl: null, availableQuantity: 12, listed: false }
const TAPE: Item = { id: 3, sku: 'K8L4R2C3', name: 'Kraft packing tape', imageUrl: null, availableQuantity: 0, listed: false }

function page(items: Item[], nextCursor: string | null = null, prevCursor: string | null = null) {
  return { status: 200, body: { items, nextCursor, prevCursor } }
}

async function openStock(roles: string[], items: Item[]) {
  storeToken()
  const server = new FakeServer().on('GET /auth/me', me(roles, { id: 20, username: 'morgan' })).on('GET /items', page(items))
  const user = openApp('/stock')
  await screen.findByRole('searchbox', { name: 'Search by name' })
  if (items.length > 0) await within(await list()).findByText(items[0].name)
  return { server, user }
}

async function list() {
  return screen.findByRole('region', { name: 'Item list' })
}

function row(name: string): HTMLElement {
  const cell = within(screen.getByRole('region', { name: 'Item list' })).getByText(name)
  return (cell.closest('tr') ?? cell.closest('li')) as HTMLElement
}

describe('the rows', () => {
  it('shows each item with its picture, SKU, stock, and whether it is on sale, and a manager the reason Edit is missing', async () => {
    await openStock(['MANAGER'], [PAPER, WALLETS])
    const paper = row('A4 paper')
    expect(within(paper).getByText('A4Q2M9X1')).toBeInTheDocument()
    expect(within(paper).getByText('40')).toBeInTheDocument()
    expect(within(paper).getByText('On sale')).toBeInTheDocument()
    expect(within(paper).getByText('Delist on Products to edit.')).toBeInTheDocument()
    expect(paper.querySelector('img')).toHaveAttribute('src', '/api/v1/images/items/paper.webp')
    expect(within(paper).getByRole('button', { name: 'Delete' })).toBeInTheDocument()
    expect(within(row('Document wallets')).getByText('Not on sale')).toBeInTheDocument()
  })

  it('shows a viewer and an operator the items with no controls and no Actions column', async () => {
    await openStock(['OPERATOR', 'VIEWER'], [PAPER])
    expect(screen.queryByRole('columnheader', { name: 'Actions' })).not.toBeInTheDocument()
    expect(within(row('A4 paper')).queryByRole('button')).not.toBeInTheDocument()
    expect(within(row('A4 paper')).queryByText('Delist on Products to edit.')).not.toBeInTheDocument()
  })

  it('shows the placeholder for an item with no picture, and for a picture that fails to load', async () => {
    await openStock(['VIEWER'], [PAPER, WALLETS])
    const image = row('A4 paper').querySelector('img')!
    fireEvent.error(image)
    await waitFor(() => expect(row('A4 paper').querySelector('img')).toBeNull())
    expect(row('Document wallets').querySelector('.item-thumbnail--empty')).not.toBeNull()
    expect(row('A4 paper').querySelector('.item-thumbnail--empty')).not.toBeNull()
  })

  it('stacks the items on a small screen', async () => {
    setScreenWidth(390)
    await openStock(['MANAGER'], [PAPER])
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(within(row('A4 paper')).getByText('SKU A4Q2M9X1 · Stock 40')).toBeInTheDocument()
    expect(within(row('A4 paper')).getByRole('button', { name: 'Delete' })).toBeInTheDocument()
  })
})

describe('the search and the paging', () => {
  it('searches by name, says when nothing is found, and Clear filters shows every item again', async () => {
    const { server, user } = await openStock(['VIEWER'], [PAPER, WALLETS])
    server.on('GET /items?search=tape', page([TAPE]))
    server.on('GET /items?search=zzzz', page([]))

    await user.type(screen.getByRole('searchbox', { name: 'Search by name' }), 'tape{Enter}')
    expect(await within(await list()).findByText('Kraft packing tape')).toBeInTheDocument()

    await user.clear(screen.getByRole('searchbox', { name: 'Search by name' }))
    await user.type(screen.getByRole('searchbox', { name: 'Search by name' }), 'zzzz{Enter}')
    expect(await screen.findByText('No items found')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(await within(await list()).findByText('A4 paper')).toBeInTheDocument()
  })

  it('pages with Previous and Next', async () => {
    const { server, user } = await openStock(['VIEWER'], [])
    server.on('GET /items', page([PAPER], 'c1'))
    server.on('GET /items?search=a', page([PAPER], 'c1'))
    server.on('GET /items?search=a&cursor=c1&direction=next', page([TAPE], null, 'p1'))
    await user.type(screen.getByRole('searchbox', { name: 'Search by name' }), 'a{Enter}')
    await within(await list()).findByText('A4 paper')
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(await within(await list()).findByText('Kraft packing tape')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeInTheDocument()
  })
})

describe('the states of the list', () => {
  it('tells a manager to add the first item, and others that items will appear', async () => {
    await openStock(['MANAGER'], [])
    expect(await screen.findByText('Add your first warehouse item.')).toBeInTheDocument()
  })

  it('tells a viewer that items will appear', async () => {
    await openStock(['VIEWER'], [])
    expect(await screen.findByText('Warehouse items will appear here.')).toBeInTheDocument()
  })

  it('says that the server cannot be reached, and Try again loads the list', async () => {
    const { server, user } = await openStock(['VIEWER'], [])
    server.on('GET /items?search=x', 'network-error')
    await user.type(screen.getByRole('searchbox', { name: 'Search by name' }), 'x{Enter}')
    expect(await screen.findByText('Cannot reach the server. Check your connection and try again.')).toBeInTheDocument()
    server.on('GET /items?search=x', page([WALLETS]))
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await within(await list()).findByText('Document wallets')).toBeInTheDocument()
  })

  it('shows a fault with its reference, and a refused search with Clear filters', async () => {
    const { server, user } = await openStock(['VIEWER'], [PAPER])
    server.on('GET /items?search=boom', problem(500, 'INTERNAL_ERROR'))
    await user.type(screen.getByRole('searchbox', { name: 'Search by name' }), 'boom{Enter}')
    expect(await screen.findByText(/report this reference: ddddc8f3b64942ff/)).toBeInTheDocument()

    server.on('GET /items?search=bad', problem(400, 'VALIDATION_FAILED'))
    await user.clear(screen.getByRole('searchbox', { name: 'Search by name' }))
    await user.type(screen.getByRole('searchbox', { name: 'Search by name' }), 'bad{Enter}')
    expect(await screen.findByText('Search could not be applied')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(await within(await list()).findByText('A4 paper')).toBeInTheDocument()
  })

  it('says Not allowed when the permission was taken away while the page was open', async () => {
    const { server, user } = await openStock(['VIEWER'], [PAPER])
    server.on('GET /items?search=x', problem(403, 'FORBIDDEN'))
    server.on('GET /auth/me', me(['SUPPORT'], { id: 20, permissions: ['PRODUCT_VIEW'] }))
    await user.type(screen.getByRole('searchbox', { name: 'Search by name' }), 'x{Enter}')
    expect(await screen.findByText('You no longer have permission to make this change.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('You do not have permission to open this page.')).toBeInTheDocument()
  })

  it('is not offered to a user without STOCK_VIEW', async () => {
    storeToken()
    const server = new FakeServer().on('GET /auth/me', me(['SUPPORT'], { permissions: ['PRODUCT_VIEW'] }))
    openApp('/stock')
    expect(await screen.findByText('You do not have permission to open this page.')).toBeInTheDocument()
    expect(server.callsTo('GET /items')).toHaveLength(0)
  })
})

describe('deleting an item', () => {
  it('asks first, says that an item on sale is delisted, and the row disappears', async () => {
    const { server, user } = await openStock(['MANAGER'], [PAPER, WALLETS])
    server.on('DELETE /items/1', { status: 204 })
    await user.click(within(row('A4 paper')).getByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete A4 paper?' })
    expect(within(dialog).getByText(/its product will be delisted/)).toBeInTheDocument()
    expect(server.callsTo('DELETE /items/1')).toHaveLength(0)

    server.on('GET /items', page([WALLETS]))
    await user.click(within(dialog).getByRole('button', { name: 'Delete item' }))
    expect(await screen.findByText('Item deleted.')).toBeInTheDocument()
    await waitFor(() => expect(within(screen.getByRole('region', { name: 'Item list' })).queryByText('A4 paper')).not.toBeInTheDocument())
  })

  it('does not mention delisting for an item that is not on sale, and Cancel deletes nothing', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    await user.click(within(row('Document wallets')).getByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete Document wallets?' })
    expect(within(dialog).getByText('This item will leave Stock. Its stock quantity will stay the same. This cannot be undone.')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(server.callsTo('DELETE /items/2')).toHaveLength(0)
  })

  it('says when the item is gone, offers only Close, and Close refreshes the list', async () => {
    const { server, user } = await openStock(['MANAGER'], [PAPER, WALLETS])
    server.on('DELETE /items/2', problem(404, 'NOT_FOUND'))
    await user.click(within(row('Document wallets')).getByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete Document wallets?' })
    await user.click(within(dialog).getByRole('button', { name: 'Delete item' }))
    expect(await within(dialog).findByText('This item is no longer available.')).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Delete item' })).not.toBeInTheDocument()

    const before = server.callsTo('GET /items').length
    server.on('GET /items', page([PAPER]))
    await user.click(within(dialog).getByRole('button', { name: 'Close' }))
    await waitFor(() => expect(server.callsTo('GET /items').length).toBe(before + 1))
  })

  it('keeps the dialog for another try after a fault or no answer', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('DELETE /items/2', problem(500, 'INTERNAL_ERROR'))
    await user.click(within(row('Document wallets')).getByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete Document wallets?' })
    await user.click(within(dialog).getByRole('button', { name: 'Delete item' }))
    expect(await within(dialog).findByText(/report this reference/)).toBeInTheDocument()

    server.on('DELETE /items/2', 'network-error')
    await user.click(within(dialog).getByRole('button', { name: 'Delete item' }))
    expect(await within(dialog).findByText('Cannot reach the server. Check your connection and try again.')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Delete item' })).toBeEnabled()
  })

  it('reloads the permissions when the deletion is forbidden', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('DELETE /items/2', problem(403, 'FORBIDDEN'))
    const before = server.callsTo('GET /auth/me').length
    await user.click(within(row('Document wallets')).getByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete Document wallets?' })
    await act(async () => {
      await user.click(within(dialog).getByRole('button', { name: 'Delete item' }))
    })
    expect(await within(dialog).findByText('You no longer have permission to make this change.')).toBeInTheDocument()
    await waitFor(() => expect(server.callsTo('GET /auth/me').length).toBeGreaterThan(before))
  })
})
