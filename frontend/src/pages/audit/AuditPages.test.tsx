import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { AuditRecord } from '../../api/audit'
import type { OrderRecord } from '../../api/logs'
import { FakeServer, me, openApp, storeToken } from '../../test/server'

/**
 * The audit log page (Figma section D): the menu of seven areas, a row read by its labels with the fields
 * before and after, the action of the area, the user or System, the Orders area, and who may open it.
 */

const SLOW = { timeout: 45_000 }

function page(items: unknown[]) {
  return { status: 200, body: { items, nextCursor: null, prevCursor: null } }
}

const DELETED: AuditRecord = {
  id: 30,
  at: '2026-11-16T05:52:00Z',
  area: 'ITEM',
  action: 'ITEM_DELETED',
  userId: 2,
  by: 'mary',
  targetType: 'ITEM',
  targetId: 3,
  before: { name: 'Pens', availableQuantity: 9 },
  after: { deleted: true, cancelledTicketIds: [12], cancelledTicketNos: ['K7Q2M9XA'], sku: 'F1X2J7L6', name: 'Pens' },
  ipAddress: '10.0.0.5',
  remark: null,
}

const CANCELLED: AuditRecord = {
  id: 31,
  at: '2026-11-16T05:52:00Z',
  area: 'TICKET',
  action: 'TICKET_CANCELLED',
  userId: null,
  by: null,
  targetType: 'TICKET',
  targetId: 12,
  before: { status: 'IN_PROGRESS' },
  after: { status: 'CANCELLED', cancelledBy: 1, cancelledByUsername: 'system', ticketNo: 'K7Q2M9XA' },
  ipAddress: null,
  remark: 'ITEM_DELETED:3',
}

const ATTEMPT: OrderRecord = { id: 12, at: '2026-11-16T01:00:00Z', productId: 5, productName: 'A4 paper', orderNo: 'ORD-3f2b07c8a6e91d0452827bc4e386bf15', orderedBy: 'olive', quantity: 3, result: 'COMPLETED', stockDeducted: 3 }

async function openAudit(path: string, answers: Record<string, unknown>, roles = ['MANAGER']) {
  storeToken()
  const server = new FakeServer().on('GET /auth/me', me(roles, { id: 2, username: 'mary' }))
  for (const [request, answer] of Object.entries(answers)) server.on(request, answer as never)
  const user = openApp(path)
  return { server, user }
}

describe('the audit log page', SLOW, () => {
  it('opens on the seven areas and goes into one', async () => {
    const { user } = await openAudit('/audit', { 'GET /audit-records?area=ITEM': page([DELETED]) })
    expect(await screen.findByText('Choose an area to view its records.')).toBeInTheDocument()
    for (const area of ['Accounts', 'Roles and permissions', 'Tickets', 'Stock', 'Warehouse items', 'Products', 'Orders']) {
      expect(screen.getByText(`${area} ›`)).toBeInTheDocument()
    }
    await user.click(screen.getByText('Warehouse items ›'))
    expect(await screen.findByRole('heading', { name: 'Warehouse items' })).toBeInTheDocument()
  })

  it('reads a row by its labels, names the cancelled tickets, and shows the fields before and after without ids', async () => {
    const { user } = await openAudit('/audit/items', { 'GET /audit-records?area=ITEM': page([DELETED]) })
    const region = await screen.findByRole('region', { name: 'Audit log' })
    expect(await within(region).findByText('Item deleted')).toBeInTheDocument()
    expect(within(region).getByText('Pens (F1X2J7L6)')).toBeInTheDocument()
    expect(within(region).getByText('Stock at deletion: 9 · Cancelled tickets: K7Q2M9XA')).toBeInTheDocument()
    await user.click(within(region).getByRole('button', { name: 'View' }))
    expect(await within(region).findByText('Available quantity: 9')).toBeInTheDocument()
    expect(within(region).getByText('Cancelled tickets: K7Q2M9XA')).toBeInTheDocument()
    expect(within(region).getByText('Deleted: Yes')).toBeInTheDocument()
    expect(within(region).getByText('IP address: 10.0.0.5')).toBeInTheDocument()
    expect(within(region).queryByText(/cancelledTicketIds|12\b/)).not.toBeInTheDocument()
    await user.click(within(region).getByRole('button', { name: 'Hide' }))
    await waitFor(() => expect(within(region).getByText('Available quantity: 9')).not.toBeVisible())
  })

  it('narrows to an action of the area and to the actions of the system', async () => {
    const { server, user } = await openAudit('/audit/tickets', {
      'GET /audit-records?area=TICKET': page([CANCELLED]),
      'GET /user-options?use=filter': page([{ id: 3, username: 'olive' }]),
    })
    const region = await screen.findByRole('region', { name: 'Audit log' })
    expect(await within(region).findByText('System')).toBeInTheDocument()
    expect(within(region).getByText('In progress → Cancelled')).toBeInTheDocument()
    server.on('GET /audit-records?area=TICKET&action=TICKET_CANCELLED', page([CANCELLED]))
    await user.click(screen.getByRole('combobox', { name: 'Action' }))
    await user.click(await screen.findByTitle('Ticket cancelled'))
    await waitFor(() => expect(server.callsTo('GET /audit-records?area=TICKET&action=TICKET_CANCELLED')).toHaveLength(1))
    server.on('GET /audit-records?area=TICKET&action=TICKET_CANCELLED&system=true', page([CANCELLED]))
    await user.click(screen.getByRole('combobox', { name: 'User' }))
    await user.click(await screen.findByTitle('System'))
    await waitFor(() => expect(server.callsTo('GET /audit-records?area=TICKET&action=TICKET_CANCELLED&system=true')).toHaveLength(1))
  })

  it('narrows the Accounts area to the rows about one user, and names the tickets a disabling returned', async () => {
    const disabled: AuditRecord = {
      id: 40, at: '2026-11-16T05:52:00Z', area: 'ACCOUNT', action: 'USER_STATUS_CHANGED', userId: 1, by: 'adam', targetType: 'USER', targetId: 3,
      before: { status: 'ACTIVE' }, after: { status: 'DISABLED', username: 'olive', returnedTicketNos: ['K7Q2M9XA'] }, ipAddress: null, remark: 'Disabled on the user page',
    }
    const { server, user } = await openAudit('/audit/accounts', {
      'GET /audit-records?area=ACCOUNT': page([disabled]),
      'GET /user-options?use=filter': page([{ id: 3, username: 'olive' }]),
    })
    const region = await screen.findByRole('region', { name: 'Audit log' })
    expect(await within(region).findByText('Active → Disabled · Tickets returned to the pool: K7Q2M9XA')).toBeInTheDocument()
    server.on('GET /audit-records?area=ACCOUNT&aboutUserId=3', page([disabled]))
    await user.click(screen.getByRole('combobox', { name: 'About user' }))
    await user.click(await screen.findByTitle('olive'))
    await waitFor(() => expect(server.callsTo('GET /audit-records?area=ACCOUNT&aboutUserId=3')).toHaveLength(1))
  })

  it('reads the Orders area from the attempts, with the result and no System', async () => {
    const { server, user } = await openAudit('/audit/orders', {
      'GET /audit-order-records': page([ATTEMPT]),
      'GET /user-options?use=filter': page([{ id: 3, username: 'olive' }]),
    })
    const region = await screen.findByRole('region', { name: 'Audit log' })
    expect(await within(region).findByText('A4 paper · 3 ordered · 3 deducted')).toBeInTheDocument()
    expect(within(region).getByText('Placed')).toBeInTheDocument()
    server.on('GET /audit-order-records?result=OUT_OF_STOCK', page([]))
    await user.click(screen.getByRole('combobox', { name: 'Result' }))
    await user.click(await screen.findByTitle('Out of stock'))
    expect(await screen.findByText('No records match these filters')).toBeInTheDocument()
    await user.click(screen.getByRole('combobox', { name: 'User' }))
    expect(await screen.findByTitle('olive')).toBeInTheDocument()
    expect(screen.queryByTitle('System')).not.toBeInTheDocument()
  })

  it('is not open to a viewer, who has no entry for it', async () => {
    const { server } = await openAudit('/audit', {}, ['VIEWER'])
    expect(await screen.findByText('You do not have permission to open this page.')).toBeInTheDocument()
    const nav = screen.getByRole('menu', { name: 'Main navigation' })
    expect(within(nav).queryByText('Audit log')).not.toBeInTheDocument()
    expect(server.calls.filter((call) => call.path.startsWith('/audit'))).toHaveLength(0)
  })
})
