import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Ticket } from '../../api/tickets'
import { FakeServer, me, openApp, problem, storeToken } from '../../test/server'
import { BOB, MARY, OLIVE, SYSTEM, page, ticket } from './ticketFixtures'

/**
 * The ticket detail and its actions (Figma section B): what each role and each status shows, the cause of
 * a cancellation by the system, the follow-up links, and each action with its refusals.
 */

const SLOW = { timeout: 45_000 }

async function openTicket(shown: Ticket, roles: string[], user = { id: OLIVE.id!, username: 'olive' }) {
  storeToken()
  const server = new FakeServer().on('GET /auth/me', me(roles, user)).on(`GET /tickets/${shown.id}`, { status: 200, body: shown })
  const events = openApp(`/tickets/${shown.id}`)
  await screen.findByRole('heading', { name: shown.ticketNo })
  return { server, user: events }
}

function buttons(): string[] {
  const bar = document.querySelector('.ticket-actions') as HTMLElement
  return within(bar).queryAllByRole('button').map((button) => button.textContent ?? '')
}

describe('what the detail offers', SLOW, () => {
  it('offers a manager Take, Assign, and Cancel on a ticket in the pool', async () => {
    await openTicket(ticket(), ['MANAGER'], { id: MARY.id!, username: 'mary' })
    expect(buttons()).toEqual(['Take', 'Assign', 'Cancel'])
  })

  it('offers an operator Cancel only on a ticket they opened', async () => {
    await openTicket(ticket({ createdBy: MARY }), ['OPERATOR'])
    expect(buttons()).toEqual(['Take'])
  })

  it('offers the assignee Resolve, Release, and Cancel, and nothing else', async () => {
    await openTicket(ticket({ status: 'IN_PROGRESS', assignee: OLIVE }), ['OPERATOR'])
    expect(buttons()).toEqual(['Resolve', 'Release', 'Cancel'])
  })

  it('offers a user who is operator and viewer nothing on a ticket someone else holds', async () => {
    await openTicket(ticket({ status: 'IN_PROGRESS', assignee: BOB }), ['OPERATOR', 'VIEWER'])
    expect(buttons()).toEqual([])
  })

  it('offers a viewer no action at all', async () => {
    await openTicket(ticket(), ['VIEWER'], { id: 9, username: 'vera' })
    expect(buttons()).toEqual([])
  })

  it('shows the cause of a cancellation by the system, and the follow-up links', async () => {
    await openTicket(
      ticket({
        status: 'CANCELLED',
        cancelCause: { kind: 'PRODUCT_DELISTED', name: 'Clear packing tape' },
        cancelReason: 'PRODUCT_DELISTED:5',
        cancelledBy: SYSTEM,
        cancelledAt: '2026-11-16T05:35:00Z',
        follows: { id: null, ticketNo: 'P2X9M3L8' },
        followUps: [{ id: 15, ticketNo: 'Q4M1R7TX' }],
      }),
      ['OPERATOR'],
    )
    expect(screen.getByText('Clear packing tape has been delisted, please open the ticket again')).toBeInTheDocument()
    expect(screen.getByText('Cancelled by System · 16 Nov 2026 16:35')).toBeInTheDocument()
    expect(screen.queryByText('PRODUCT_DELISTED:5')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'P2X9M3L8' })).not.toBeInTheDocument()
    expect(screen.getByText('P2X9M3L8')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Q4M1R7TX' })).toHaveAttribute('href', '/tickets/15')
  })

  it('gives a missing or hidden ticket the same Not found page', async () => {
    storeToken()
    new FakeServer().on('GET /auth/me', me(['OPERATOR'])).on('GET /tickets/99', problem(404, 'NOT_FOUND'))
    openApp('/tickets/99')
    expect(await screen.findByText('This ticket could not be found.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Back to Pool' })).toBeInTheDocument()
  })
})

describe('the actions', SLOW, () => {
  it('takes a ticket and shows it in progress', async () => {
    const { server, user } = await openTicket(ticket(), ['OPERATOR'])
    server.on('POST /tickets/12/take', { status: 200, body: ticket({ status: 'IN_PROGRESS', assignee: OLIVE }) })
    await user.click(screen.getByRole('button', { name: 'Take' }))
    expect(await screen.findByText('Ticket taken. It is now in In progress.')).toBeInTheDocument()
    expect(buttons()).toEqual(['Resolve', 'Release', 'Cancel'])
  })

  it('says a lost take is no longer in the pool, and reloads the ticket', async () => {
    const { server, user } = await openTicket(ticket(), ['OPERATOR'])
    server.on('POST /tickets/12/take', problem(409, 'TICKET_CHANGED'))
    await user.click(screen.getByRole('button', { name: 'Take' }))
    expect(await screen.findByText('This ticket is no longer in the pool.')).toBeInTheDocument()
    server.on('GET /tickets/12', problem(404, 'NOT_FOUND'))
    await user.click(screen.getByRole('button', { name: 'Reload ticket' }))
    expect(await screen.findByText('This ticket could not be found.')).toBeInTheDocument()
  })

  it('says when the taker already holds 99 tickets', async () => {
    const { server, user } = await openTicket(ticket(), ['OPERATOR'])
    server.on('POST /tickets/12/take', problem(409, 'LIMIT_REACHED'))
    await user.click(screen.getByRole('button', { name: 'Take' }))
    expect(await screen.findByText('You already have 99 tickets in progress.')).toBeInTheDocument()
  })

  it('assigns to a user from the picker with a reason, and names an assignee who can no longer take it', async () => {
    const { server, user } = await openTicket(ticket(), ['MANAGER'], { id: MARY.id!, username: 'mary' })
    server.on('GET /user-options?use=assignee', page([{ id: 4, username: 'bob' }]))
    await user.click(screen.getByRole('button', { name: 'Assign' }))
    const dialog = await screen.findByRole('dialog', { name: 'Assign ticket K7Q2M9XA' })
    const assign = within(dialog).getByRole('button', { name: 'Assign' })
    expect(assign).toBeDisabled()
    await user.click(within(dialog).getByRole('combobox', { name: 'Assignee' }))
    await user.click(await screen.findByTitle('bob'))
    await user.type(within(dialog).getByLabelText('Reason (required)'), '  Delivery received  ')
    server.on('POST /tickets/12/assign', problem(409, 'NOT_ELIGIBLE'))
    await user.click(assign)
    expect(await within(dialog).findByText('bob is no longer eligible. Choose another assignee.')).toBeInTheDocument()
    expect(server.callsTo('POST /tickets/12/assign')[0].body).toEqual({ assigneeId: 4, reason: 'Delivery received' })
  })

  it('reassigns with the assignee the person saw, and asks for a reload when the ticket changed', async () => {
    const { server, user } = await openTicket(ticket({ status: 'IN_PROGRESS', assignee: OLIVE }), ['MANAGER'], { id: MARY.id!, username: 'mary' })
    server.on('GET /user-options?use=assignee', page([{ id: 3, username: 'olive' }, { id: 4, username: 'bob' }]))
    await user.click(screen.getByRole('button', { name: 'Reassign' }))
    const dialog = await screen.findByRole('dialog', { name: 'Reassign ticket K7Q2M9XA' })
    expect(within(dialog).getByText('Current assignee: olive')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('combobox', { name: 'New assignee' }))
    expect(await screen.findByTitle('bob')).toBeInTheDocument()
    expect(screen.queryByTitle('olive')).not.toBeInTheDocument()
    await user.click(screen.getByTitle('bob'))
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'Move to deliveries')
    server.on('POST /tickets/12/reassign', problem(409, 'TICKET_CHANGED', { ticketStatus: 'IN_PROGRESS' }))
    await user.click(within(dialog).getByRole('button', { name: 'Reassign' }))
    expect(await within(dialog).findByText('This ticket has changed. Reload the ticket to see its current state.')).toBeInTheDocument()
    expect(server.callsTo('POST /tickets/12/reassign')[0].body).toEqual({ assigneeId: 4, expectedAssigneeId: 3, reason: 'Move to deliveries' })
    expect(within(dialog).getByRole('button', { name: 'Reload ticket' })).toBeInTheDocument()
  })

  it('releases with a reason, and sends a ticket that left the operator back to the list', async () => {
    const { server, user } = await openTicket(ticket({ status: 'IN_PROGRESS', assignee: OLIVE }), ['OPERATOR'])
    await user.click(screen.getByRole('button', { name: 'Release' }))
    const dialog = await screen.findByRole('dialog', { name: 'Release ticket K7Q2M9XA' })
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'x'.repeat(256))
    expect(within(dialog).getByText('Use 255 characters or fewer.')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Release' })).toBeDisabled()
    await user.clear(within(dialog).getByLabelText('Reason (required)'))
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'wrong shelf')
    server.on('POST /tickets/12/release', problem(404, 'NOT_FOUND'))
    server.on('GET /tickets?status=IN_PROGRESS', page([]))
    await user.click(within(dialog).getByRole('button', { name: 'Release' }))
    expect(await within(dialog).findByText('You can no longer release this ticket.')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Back to list' }))
    expect(await screen.findByRole('heading', { name: 'In progress' })).toBeInTheDocument()
  })

  it('resolves with a counted resolution and shows who resolved it', async () => {
    const { server, user } = await openTicket(ticket({ status: 'IN_PROGRESS', assignee: OLIVE }), ['OPERATOR'])
    await user.click(screen.getByRole('button', { name: 'Resolve' }))
    const dialog = await screen.findByRole('dialog', { name: 'Resolve ticket K7Q2M9XA' })
    expect(within(dialog).getByText('0 / 2000')).toBeInTheDocument()
    await user.type(within(dialog).getByLabelText('Resolution'), 'Counted again')
    expect(within(dialog).getByText('13 / 2000')).toBeInTheDocument()
    server.on('POST /tickets/12/resolve', {
      status: 200,
      body: ticket({ status: 'RESOLVED', assignee: OLIVE, resolution: 'Counted again', resolvedBy: OLIVE, resolvedAt: '2026-11-16T01:00:00Z' }),
    })
    await user.click(within(dialog).getByRole('button', { name: 'Resolve' }))
    expect(await screen.findByText('Ticket resolved.')).toBeInTheDocument()
    expect(screen.getByText('Resolved by olive · 16 Nov 2026 12:00')).toBeInTheDocument()
    expect(buttons()).toEqual([])
  })

  it('cancels with a reason after saying that a cancelled ticket cannot be reopened', async () => {
    const { server, user } = await openTicket(ticket(), ['OPERATOR'])
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    const dialog = await screen.findByRole('dialog', { name: 'Cancel ticket K7Q2M9XA?' })
    expect(within(dialog).getByText('A cancelled ticket cannot be reopened.')).toBeInTheDocument()
    await user.type(within(dialog).getByLabelText('Reason (required)'), 'duplicate')
    server.on('POST /tickets/12/cancel', { status: 200, body: ticket({ status: 'CANCELLED', cancelReason: 'duplicate', cancelledBy: OLIVE, cancelledAt: '2026-11-16T01:00:00Z' }) })
    await user.click(within(dialog).getByRole('button', { name: 'Cancel ticket' }))
    expect(await screen.findByText('Ticket cancelled.')).toBeInTheDocument()
    expect(screen.getByText('duplicate')).toBeInTheDocument()
  })

  it('archives a resolved ticket after a second confirmation', async () => {
    const resolved = ticket({ status: 'RESOLVED', assignee: OLIVE, resolution: 'done', resolvedBy: OLIVE, resolvedAt: '2026-11-16T01:00:00Z' })
    const { server, user } = await openTicket(resolved, ['MANAGER'], { id: MARY.id!, username: 'mary' })
    await user.click(screen.getByRole('button', { name: 'Archive' }))
    const dialog = await screen.findByRole('dialog', { name: 'Archive 1 ticket?' })
    expect(within(dialog).getByText('K7Q2M9XA will be archived and cannot be moved again.')).toBeInTheDocument()
    server.on('POST /tickets/12/archive', { status: 200, body: { ...resolved, status: 'CLOSED', closedBy: MARY, closedAt: '2026-11-16T02:00:00Z' } })
    await user.click(within(dialog).getByRole('button', { name: 'Archive ticket' }))
    expect(await screen.findByText('Ticket archived.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('Archived by mary · 16 Nov 2026 13:00')).toBeInTheDocument())
  })
})
