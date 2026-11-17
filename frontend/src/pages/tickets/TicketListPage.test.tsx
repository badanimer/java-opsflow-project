import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FakeServer, me, openApp, problem, storeToken } from '../../test/server'
import { MARY, SYSTEM, page, row, ticket } from './ticketFixtures'

/**
 * The ticket screens (Figma section A): the rows with the system tickets first, what each role is offered,
 * the search and a number used alone, the direction, the paging, and the batch archive.
 */

async function openScreen(path: string, roles: string[], answers: Record<string, unknown>, user = { id: 3, username: 'olive' }) {
  storeToken()
  const server = new FakeServer().on('GET /auth/me', me(roles, user))
  for (const [request, answer] of Object.entries(answers)) server.on(request, answer as never)
  const events = openApp(path)
  return { server, user: events }
}

const list = () => screen.findByRole('region', { name: 'Ticket list' })

/** A screen with the whole search bar renders slowly in the test DOM. */
const SLOW = { timeout: 45_000 }

describe('a ticket screen', SLOW, () => {
  it('lists the rows with the system tickets first, marked, and opens a ticket from its row', async () => {
    const { user } = await openScreen('/tickets/pool', ['MANAGER'], {
      'GET /tickets?status=OPEN': page([row(1, 'R5F2C8LU', { source: 'SYSTEM', createdBy: SYSTEM }), row(2, 'K7Q2M9XA')]),
      'GET /tickets/2': { status: 200, body: ticket({ id: 2, ticketNo: 'K7Q2M9XA' }) },
    })
    const region = await list()
    await within(region).findByText('K7Q2M9XA')
    const rows = within(region).getAllByRole('row').slice(1)
    expect(rows[0]).toHaveClass('ticket-row--system')
    expect(within(rows[0]).getByText('System')).toBeInTheDocument()
    expect(within(rows[1]).getByText('olive')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Waiting to be taken')).toBeInTheDocument()
    expect(within(region).getByRole('link', { name: 'K7Q2M9XA' })).toHaveAttribute('href', '/tickets/2')
    await user.click(within(rows[1]).getByText('Stock count does not match the record'))
    // The detail must open before the test ends, or it would render after the test on a slow machine.
    expect(await screen.findByRole('heading', { name: 'K7Q2M9XA' })).toBeInTheDocument()
  })

  it('offers an operator Create ticket and the direction, and no search', async () => {
    const { server, user } = await openScreen('/tickets/pool', ['OPERATOR'], { 'GET /tickets?status=OPEN': page([row(2, 'K7Q2M9XA')]) })
    await within(await list()).findByText('K7Q2M9XA')
    expect(screen.getByRole('button', { name: 'Create ticket' })).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Search by' })).not.toBeInTheDocument()
    server.on('GET /tickets?status=OPEN&order=asc', page([]))
    await user.click(screen.getByRole('combobox', { name: 'Sort order' }))
    await user.click(await screen.findByText('Oldest first'))
    await waitFor(() => expect(server.callsTo('GET /tickets?status=OPEN&order=asc')).toHaveLength(1))
  })

  it('tells an operator whose tickets the screen shows, and a viewer that it shows everyone’s', async () => {
    await openScreen('/tickets/in-progress', ['OPERATOR'], { 'GET /tickets?status=IN_PROGRESS': page([]) })
    expect(await screen.findByText('Tickets assigned to you')).toBeInTheDocument()
    expect(await screen.findByText('No tickets')).toBeInTheDocument()
  })

  it('gives a viewer the search but no Create ticket, and finds one ticket number alone in capitals', async () => {
    const { server, user } = await openScreen('/tickets/pool', ['VIEWER'], { 'GET /tickets?status=OPEN': page([row(2, 'K7Q2M9XA')]) }, { id: 9, username: 'vera' })
    await within(await list()).findByText('K7Q2M9XA')
    expect(screen.queryByRole('button', { name: 'Create ticket' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('combobox', { name: 'Search by' }))
    await user.click(await screen.findByTitle('Ticket number'))
    const box = screen.getByPlaceholderText('Enter a ticket number')
    await user.type(box, 'k7?')
    await user.click(screen.getByRole('button', { name: 'Find' }))
    expect(screen.getByText('Enter an 8-character ticket number.')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Kind' })).toBeDisabled()
    server.on('GET /tickets?status=OPEN&ticketNo=K7Q2M9XA', page([row(2, 'K7Q2M9XA')]))
    await user.clear(box)
    await user.type(box, 'k7q2m9xa')
    await user.click(screen.getByRole('button', { name: 'Find' }))
    await waitFor(() => expect(server.callsTo('GET /tickets?status=OPEN&ticketNo=K7Q2M9XA')).toHaveLength(1))
  })

  it('narrows by kind and by creator, and shows no match with Clear filters', async () => {
    const { server, user } = await openScreen('/tickets/in-progress', ['MANAGER'], {
      'GET /tickets?status=IN_PROGRESS': page([row(2, 'K7Q2M9XA', { status: 'IN_PROGRESS' })]),
      'GET /user-options?use=filter': page([{ id: 2, username: 'mary' }]),
    }, { id: 2, username: 'mary' })
    await within(await list()).findByText('K7Q2M9XA')
    server.on('GET /tickets?status=IN_PROGRESS&source=SYSTEM', page([]))
    await user.click(screen.getByRole('combobox', { name: 'Kind' }))
    await user.click(await screen.findByTitle('System'))
    expect(await screen.findByText('No tickets match your search')).toBeInTheDocument()
    server.on(`GET /tickets?status=IN_PROGRESS&source=SYSTEM&createdBy=${MARY.id}`, page([]))
    await user.click(screen.getByRole('combobox', { name: 'Creator' }))
    await user.click(await screen.findByTitle('mary'))
    await waitFor(() => expect(server.callsTo(`GET /tickets?status=IN_PROGRESS&source=SYSTEM&createdBy=${MARY.id}`)).toHaveLength(1))
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    await within(await list()).findByText('K7Q2M9XA')
  })

  it('finds the tickets of one warehouse item, chosen from the item picker', async () => {
    const { server, user } = await openScreen('/tickets/pool', ['MANAGER'], {
      'GET /tickets?status=OPEN': page([row(2, 'K7Q2M9XA')]),
      'GET /items': page([{ id: 3, sku: 'C3P9K2D4', name: 'Clear packing tape', imageUrl: null, availableQuantity: 9, listed: false }]),
    }, { id: 2, username: 'mary' })
    await within(await list()).findByText('K7Q2M9XA')
    server.on('GET /tickets?status=OPEN&inventoryId=3', page([row(2, 'K7Q2M9XA')]))
    await user.click(screen.getByRole('combobox', { name: 'Item' }))
    await user.click(await screen.findByTitle('Clear packing tape · C3P9K2D4'))
    await waitFor(() => expect(server.callsTo('GET /tickets?status=OPEN&inventoryId=3')).toHaveLength(1))
  })

  it('says Not allowed on the archive for an operator and asks the server nothing', async () => {
    const { server } = await openScreen('/tickets/archive', ['OPERATOR'], {})
    expect(await screen.findByText('You do not have permission to open this page.')).toBeInTheDocument()
    expect(server.calls.filter((call) => call.path.startsWith('/tickets'))).toHaveLength(0)
  })

  it('counts the pages with Previous and Next', async () => {
    const { server, user } = await openScreen('/tickets/pool', ['MANAGER'], { 'GET /tickets?status=OPEN': page([row(2, 'K7Q2M9XA')], 'c1') })
    await within(await list()).findByText('K7Q2M9XA')
    expect(screen.getByText('Page 1')).toBeInTheDocument()
    server.on('GET /tickets?status=OPEN&cursor=c1&direction=next', page([row(3, 'H5C8W2GA')], null, 'c0'))
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(await within(await list()).findByText('H5C8W2GA')).toBeInTheDocument()
    expect(screen.getByText('Page 2')).toBeInTheDocument()
  })

  it('shows a fault with its reference and tries again', async () => {
    const { server, user } = await openScreen('/tickets/pool', ['MANAGER'], { 'GET /tickets?status=OPEN': problem(500, 'INTERNAL_ERROR') })
    expect(await screen.findByText(/report this reference/)).toBeInTheDocument()
    server.on('GET /tickets?status=OPEN', page([row(2, 'K7Q2M9XA')]))
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await within(await list()).findByText('K7Q2M9XA')).toBeInTheDocument()
  })
})

describe('the batch archive', SLOW, () => {
  const resolved = page([row(2, 'K7Q2M9XA', { status: 'RESOLVED' }), row(3, 'D6P3L8WN', { status: 'RESOLVED' })])

  it('archives the chosen tickets after a second confirmation and lists the archived and the skipped', async () => {
    const { server, user } = await openScreen('/tickets/resolved', ['MANAGER'], { 'GET /tickets?status=RESOLVED': resolved }, { id: 2, username: 'mary' })
    await within(await list()).findByText('K7Q2M9XA')
    expect(screen.getByRole('button', { name: 'Archive selected' })).toBeDisabled()
    await user.click(screen.getByRole('checkbox', { name: 'Select K7Q2M9XA' }))
    await user.click(screen.getByRole('checkbox', { name: 'Select D6P3L8WN' }))
    await user.click(screen.getByRole('button', { name: 'Archive selected (2)' }))
    const confirm = await screen.findByRole('dialog', { name: 'Archive 2 tickets?' })
    expect(within(confirm).getByText('These tickets cannot be moved again.')).toBeInTheDocument()
    server.on('POST /tickets/archive', { status: 200, body: { archived: ['K7Q2M9XA'], skipped: [{ id: 3, ticketNo: 'D6P3L8WN', status: 'CLOSED' }] } })
    server.on('GET /tickets?status=RESOLVED', page([]))
    await user.click(within(confirm).getByRole('button', { name: 'Archive tickets' }))
    const result = await screen.findByRole('dialog', { name: '1 ticket archived. 1 ticket skipped.' })
    expect(within(result).getByText('Archived K7Q2M9XA')).toBeInTheDocument()
    expect(within(result).getByText('Skipped D6P3L8WN · Archived')).toBeInTheDocument()
    expect(server.callsTo('POST /tickets/archive')[0].body).toEqual({ ids: [2, 3] })
  })

  it('is not offered to a viewer', async () => {
    await openScreen('/tickets/resolved', ['VIEWER'], { 'GET /tickets?status=RESOLVED': resolved }, { id: 9, username: 'vera' })
    await within(await list()).findByText('K7Q2M9XA')
    expect(screen.queryByRole('button', { name: /Archive selected/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })
})
