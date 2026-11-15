import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Item } from '../../api/items'
import { FakeServer, me, openApp, problem, storeToken } from '../../test/server'

/**
 * The quantity change of the stock page (Figma frames "Stock in / ...", "Write-off / ...", "Correction /
 * ..."): the three choices by permission, the stock before and after, the checks of the page, and every
 * answer of the server.
 */

const PAPER: Item = { id: 1, sku: 'A4Q2M9X1', name: 'A4 paper', imageUrl: null, availableQuantity: 40, listed: true }

function page(items: Item[]) {
  return { status: 200, body: { items, nextCursor: null, prevCursor: null } }
}

async function openAdjust(roles: string[], permissions?: string[]) {
  storeToken()
  const server = new FakeServer().on('GET /auth/me', me(roles, { id: 20, username: 'olive', permissions })).on('GET /items', page([PAPER]))
  const user = openApp('/stock')
  const cell = await within(await screen.findByRole('region', { name: 'Item list' })).findByText('A4 paper')
  await user.click(within(cell.closest('tr') as HTMLElement).getByRole('button', { name: 'Adjust quantity' }))
  const dialog = await screen.findByRole('dialog', { name: 'Adjust quantity' })
  return { server, user, dialog }
}

type Opened = Awaited<ReturnType<typeof openAdjust>>

async function fill({ user, dialog }: Opened, quantity: string, reason: string) {
  const field = within(dialog).getByRole('textbox', { name: /Quantity|Counted quantity/ })
  await user.clear(field)
  if (quantity) await user.type(field, quantity)
  if (reason) await user.type(within(dialog).getByRole('textbox', { name: 'Reason' }), reason)
}

function saveButton(dialog: HTMLElement) {
  return within(dialog).getByRole('button', { name: 'Save' })
}

/** The Close button of the footer; the X in the corner of a dialog has the same name. */
function closeButton(dialog: HTMLElement): HTMLElement {
  return within(dialog).getAllByRole('button', { name: 'Close' }).find((button) => button.textContent === 'Close')!
}

describe('a stock-in', () => {
  it('shows the stock before and after, sends the trimmed reason, and closes with a notice', async () => {
    const opened = await openAdjust(['OPERATOR'])
    const { server, user, dialog } = opened
    server.on('POST /items/1/stock-in', { status: 200, body: { ...PAPER, availableQuantity: 48 } })
    expect(saveButton(dialog)).toBeDisabled()
    await fill(opened, '8', '  Delivery received  ')
    expect(within(dialog).getByTestId('stock-before')).toHaveTextContent('40')
    expect(within(dialog).getByTestId('stock-after')).toHaveTextContent('48')

    const loads = server.callsTo('GET /items').length
    await user.click(saveButton(dialog))
    expect(await screen.findByText('Stock added.')).toBeInTheDocument()
    expect(server.callsTo('POST /items/1/stock-in')[0].body).toEqual({ quantity: 8, reason: 'Delivery received' })
    await waitFor(() => expect(server.callsTo('GET /items').length).toBe(loads + 1))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('keeps Save disabled without a reason and refuses a quantity out of its range in the page', async () => {
    const opened = await openAdjust(['OPERATOR'])
    const { dialog } = opened
    await fill(opened, '8', '   ')
    expect(saveButton(dialog)).toBeDisabled()
    for (const typed of ['0', '1.5', '1000000']) {
      await fill(opened, typed, '')
      expect(within(dialog).getByText('Enter a whole number from 1 to 999,999.')).toBeInTheDocument()
      expect(within(dialog).getByTestId('stock-after')).toHaveTextContent('—')
    }
  })

  it('shows a stock the system cannot hold under the quantity', async () => {
    const opened = await openAdjust(['OPERATOR'])
    opened.server.on('POST /items/1/stock-in', problem(400, 'INVALID_QUANTITY'))
    await fill(opened, '8', 'Delivery')
    await opened.user.click(saveButton(opened.dialog))
    expect(await within(opened.dialog).findByText('This change exceeds the maximum stock quantity.')).toBeInTheDocument()
  })
})

describe('a write-off and a correction', () => {
  it('refuses a write-off larger than the stock shown', async () => {
    const opened = await openAdjust(['OPERATOR'])
    await opened.user.click(within(opened.dialog).getByText('Write-off'))
    await fill(opened, '50', 'r')
    expect(within(opened.dialog).getByText('Enter a quantity no greater than the stock shown.')).toBeInTheDocument()
    expect(saveButton(opened.dialog)).toBeDisabled()
  })

  it('refuses a correction to the stock shown', async () => {
    const opened = await openAdjust(['OPERATOR'])
    await opened.user.click(within(opened.dialog).getByText('Correction'))
    await fill(opened, '40', 'r')
    expect(within(opened.dialog).getByText('Enter a count different from the current stock.')).toBeInTheDocument()
    expect(saveButton(opened.dialog)).toBeDisabled()
  })

  it('sends a correction with the stock the person saw', async () => {
    const opened = await openAdjust(['OPERATOR'])
    opened.server.on('POST /items/1/correction', { status: 200, body: { ...PAPER, availableQuantity: 32 } })
    await opened.user.click(within(opened.dialog).getByText('Correction'))
    await fill(opened, '32', 'Count')
    expect(within(opened.dialog).getByTestId('stock-after')).toHaveTextContent('32')
    await opened.user.click(saveButton(opened.dialog))
    expect(await screen.findByText('Stock corrected.')).toBeInTheDocument()
    expect(opened.server.callsTo('POST /items/1/correction')[0].body).toEqual({ quantity: 32, reason: 'Count', expectedQuantity: 40 })
  })

  it('reloads the item when the stock changed, and asks for the count again', async () => {
    const opened = await openAdjust(['OPERATOR'])
    const { server, user, dialog } = opened
    server.on('POST /items/1/correction', problem(409, 'STOCK_CHANGED'))
    await user.click(within(dialog).getByText('Correction'))
    await fill(opened, '32', 'Count')
    await user.click(saveButton(dialog))
    expect(await within(dialog).findByText('Stock changed. Reload the item and count again.')).toBeInTheDocument()

    server.on('GET /items', page([{ ...PAPER, availableQuantity: 36 }]))
    await user.click(within(dialog).getByRole('button', { name: 'Reload item' }))
    expect(await within(dialog).findByText('Stock was refreshed. Enter the new count.')).toBeInTheDocument()
    expect(within(dialog).getByTestId('stock-before')).toHaveTextContent('36')
    expect(within(dialog).getByRole('textbox', { name: 'Counted quantity' })).toHaveValue('')
  })

  it('reloads the item when a write-off found less stock than shown', async () => {
    const opened = await openAdjust(['OPERATOR'])
    opened.server.on('POST /items/1/write-off', problem(409, 'OUT_OF_STOCK'))
    await opened.user.click(within(opened.dialog).getByText('Write-off'))
    await fill(opened, '35', 'Damaged')
    await opened.user.click(saveButton(opened.dialog))
    expect(await within(opened.dialog).findByText('Stock has decreased since you opened this item. Reload it and enter a new quantity.')).toBeInTheDocument()
    expect(within(opened.dialog).getByRole('button', { name: 'Reload item' })).toBeInTheDocument()
    expect(within(opened.dialog).queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
  })
})

describe('the other answers', () => {
  it('ends with Close when the item is gone', async () => {
    const opened = await openAdjust(['OPERATOR'])
    opened.server.on('POST /items/1/stock-in', problem(404, 'NOT_FOUND'))
    await fill(opened, '8', 'Delivery')
    await opened.user.click(saveButton(opened.dialog))
    expect(await within(opened.dialog).findByText('This item is no longer available.')).toBeInTheDocument()
    expect(closeButton(opened.dialog)).toBeInTheDocument()
    expect(within(opened.dialog).queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
  })

  it('offers Reload item, and never sends again by itself, when no answer came', async () => {
    const opened = await openAdjust(['OPERATOR'])
    opened.server.on('POST /items/1/stock-in', 'network-error')
    await fill(opened, '8', 'Delivery')
    await opened.user.click(saveButton(opened.dialog))
    expect(await within(opened.dialog).findByText('Cannot reach the server. Check your connection and try again.')).toBeInTheDocument()
    expect(within(opened.dialog).getByRole('button', { name: 'Reload item' })).toBeInTheDocument()
    expect(opened.server.callsTo('POST /items/1/stock-in')).toHaveLength(1)
  })

  it('offers only the changes the permissions allow', async () => {
    const { dialog } = await openAdjust(['SUPPORT'], ['STOCK_VIEW', 'STOCK_CORRECT'])
    expect(within(dialog).getByText('Correction')).toBeInTheDocument()
    expect(within(dialog).queryByText('Stock in')).not.toBeInTheDocument()
    expect(within(dialog).queryByText('Write-off')).not.toBeInTheDocument()
    expect(within(dialog).getByRole('textbox', { name: 'Counted quantity' })).toBeInTheDocument()
  })
})
