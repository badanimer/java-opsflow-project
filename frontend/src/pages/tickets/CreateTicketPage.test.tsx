import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FakeServer, me, openApp, problem, storeToken } from '../../test/server'
import { page, ticket } from './ticketFixtures'

/**
 * Create ticket (Figma section C): the items from the searched list, the checks of each field in the page,
 * the request that is sent, the ticket that was opened, and each refusal, which keeps what was typed.
 */

const SLOW = { timeout: 45_000 }
const TAPE = { id: 3, sku: 'C3P9K2D4', name: 'Clear packing tape', imageUrl: null, availableQuantity: 9, listed: false }
const KRAFT = { id: 5, sku: 'K8L4R2C3', name: 'Kraft packing tape', imageUrl: null, availableQuantity: 4, listed: false }

async function openForm(roles = ['OPERATOR']) {
  storeToken()
  const server = new FakeServer().on('GET /auth/me', me(roles, { id: 3, username: 'olive' })).on('GET /items', page([TAPE, KRAFT]))
  const user = openApp('/tickets/new')
  await screen.findByRole('heading', { name: 'Create ticket' })
  return { server, user }
}

async function chooseType(user: Awaited<ReturnType<typeof openForm>>['user'], label: string) {
  await user.click(screen.getByRole('combobox', { name: 'Type (required)' }))
  await user.click(await screen.findByTitle(label))
}

async function chooseItem(user: Awaited<ReturnType<typeof openForm>>['user'], label: string) {
  await user.click(screen.getByRole('combobox', { name: 'Warehouse item' }))
  await user.click(await screen.findByTitle(label))
}

describe('Create ticket', SLOW, () => {
  it('shows the usage rule and checks every field in the page before sending anything', async () => {
    const { server, user } = await openForm()
    expect(screen.getByText('Open a ticket for a problem you cannot or may not solve alone.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Create ticket' }))
    expect(screen.getByText('Choose a ticket type.')).toBeInTheDocument()
    expect(screen.getByText('Enter a title.')).toBeInTheDocument()
    expect(screen.getByText('Enter a description.')).toBeInTheDocument()

    await chooseType(user, 'Inventory exception')
    await user.type(screen.getByLabelText('Title (required)'), 'Count is off')
    await user.type(screen.getByLabelText('Description (required)'), 'Shelf shows 3')
    expect(screen.getByText('13 / 4000')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Follow-up ticket number (optional)'), 'p2')
    await user.click(screen.getByRole('button', { name: 'Create ticket' }))
    expect(screen.getByText('Choose at least one item for an inventory exception.')).toBeInTheDocument()
    expect(screen.getByText('Enter an 8-character ticket number.')).toBeInTheDocument()

    await chooseType(user, 'Order exception')
    expect(screen.getByLabelText('Order number (required)')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Create ticket' }))
    expect(screen.getByText('Enter an order number for an order exception.')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Order number (required)'), 'ORD-3f2b')
    await user.click(screen.getByRole('button', { name: 'Create ticket' }))
    expect(screen.getByText('Enter ORD- followed by 32 hexadecimal characters.')).toBeInTheDocument()
    expect(server.callsTo('POST /tickets')).toHaveLength(0)
  })

  it('opens a ticket on chosen items, sends the trimmed texts and the number in any case, and offers the new ticket', async () => {
    const { server, user } = await openForm()
    await chooseItem(user, 'Clear packing tape · C3P9K2D4')
    await chooseItem(user, 'Kraft packing tape · K8L4R2C3')
    expect(screen.getByText('Clear packing tape · C3P9K2D4', { selector: '.ant-tag' })).toBeInTheDocument()
    await chooseType(user, 'Inventory exception')
    await user.type(screen.getByLabelText('Title (required)'), '  Count is off  ')
    await user.type(screen.getByLabelText('Description (required)'), 'Shelf shows 3')
    await user.type(screen.getByLabelText('Order number (optional)'), 'ORD-3F2B07C8A6E91D0452827BC4E386BF15')
    server.on('POST /tickets', { status: 201, body: ticket({ id: 12, ticketNo: 'K7Q2M9XA' }) })
    await user.click(screen.getByRole('button', { name: 'Create ticket' }))
    expect(await screen.findByText('Ticket K7Q2M9XA was opened.')).toBeInTheDocument()
    expect(server.callsTo('POST /tickets')[0].body).toEqual({
      type: 'INVENTORY_EXCEPTION',
      title: 'Count is off',
      description: 'Shelf shows 3',
      inventoryIds: [3, 5],
      orderNo: 'ORD-3F2B07C8A6E91D0452827BC4E386BF15',
      followsTicketNo: null,
    })
    server.on('GET /tickets/12', { status: 200, body: ticket() })
    await user.click(screen.getByRole('button', { name: 'View ticket' }))
    expect(await screen.findByRole('heading', { name: 'K7Q2M9XA' })).toBeInTheDocument()
  })

  it('names the items that no longer exist, keeps what was typed, and reloads the item list', async () => {
    const { server, user } = await openForm()
    await chooseItem(user, 'Clear packing tape · C3P9K2D4')
    await chooseType(user, 'Other')
    await user.type(screen.getByLabelText('Title (required)'), 'Count is off')
    await user.type(screen.getByLabelText('Description (required)'), 'Shelf shows 3')
    server.on('POST /tickets', problem(409, 'ITEM_CHANGED', { items: [{ inventoryId: 3, name: 'Clear packing tape' }] }))
    await user.click(screen.getByRole('button', { name: 'Create ticket' }))
    expect(await screen.findByText('Clear packing tape no longer exists.')).toBeInTheDocument()
    expect(screen.getByLabelText('Title (required)')).toHaveValue('Count is off')
    await user.click(screen.getByRole('button', { name: 'Reload item list' }))
    expect(screen.getByText('Item list refreshed. Review the remaining items before creating the ticket.')).toBeInTheDocument()
    expect(screen.queryByText('Clear packing tape · C3P9K2D4', { selector: '.ant-tag' })).not.toBeInTheDocument()
  })

  it('shows each refusal of the server under its field or above the buttons', async () => {
    const { server, user } = await openForm()
    await chooseType(user, 'Other')
    await user.type(screen.getByLabelText('Title (required)'), 'Count is off')
    await user.type(screen.getByLabelText('Description (required)'), 'Shelf shows 3')
    await user.type(screen.getByLabelText('Follow-up ticket number (optional)'), 'p2x9m3l8')
    expect(screen.getByLabelText('Follow-up ticket number (optional)')).toHaveValue('P2X9M3L8')
    const submit = () => user.click(screen.getByRole('button', { name: 'Create ticket' }))

    server.on('POST /tickets', problem(404, 'FOLLOW_NOT_FOUND'))
    await submit()
    expect(await screen.findByText('No ticket has that number.')).toBeInTheDocument()
    server.on('POST /tickets', problem(409, 'FOLLOW_NOT_ALLOWED'))
    await submit()
    expect(await screen.findByText('Only a resolved or archived ticket can be followed.')).toBeInTheDocument()
    server.on('POST /tickets', problem(409, 'LIMIT_REACHED'))
    await submit()
    expect(await screen.findByText('You already have 99 open tickets.')).toBeInTheDocument()
    server.on('POST /tickets', problem(503, 'BUSY'))
    await submit()
    expect(await screen.findByText(/report this reference/)).toBeInTheDocument()
    await waitFor(() => expect(server.callsTo('POST /tickets')).toHaveLength(4))
    expect(server.callsTo('POST /tickets')[0].body).toMatchObject({ followsTicketNo: 'P2X9M3L8' })
  })

  it('is not open to a viewer', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', me(['VIEWER']))
    openApp('/tickets/new')
    const notice = await screen.findByText('You do not have permission to open this page.')
    expect(within(notice.closest('div')!.parentElement!).queryByRole('button', { name: 'Create ticket' })).not.toBeInTheDocument()
  })
})
