import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { me, problem } from '../../test/server'
import { ADAM, ALEX, ALICE, BOB, CHLOE, EVA, findInList, list, openUsers, page, row } from '../../test/users'

/**
 * The four changes to a user on the user page, the second confirmation of the ones that are hard to take
 * back (Second Confirmation in docs/frontend/page-design.md), and what the page shows for each answer of
 * the server (Users in docs/api/api-design.md).
 */

function dialog(name: RegExp | string): HTMLElement {
  return screen.getByRole('dialog', { name })
}

async function openRolesOf(username: string, user: Awaited<ReturnType<typeof openUsers>>['user']) {
  await user.click(within(row(username)).getByRole('button', { name: 'Change roles' }))
  return screen.findByRole('dialog', { name: `Change roles for ${username}` })
}

function checkbox(within_: HTMLElement, label: string): HTMLElement {
  return within(within_).getByRole('checkbox', { name: label })
}

describe('changing the roles', () => {
  it('warns before a change that sends the tickets in progress of the user back to the pool, and says how many went', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, ALICE])
    server.on('GET /users/3/role-change?roles=VIEWER', { status: 200, body: { ticketsReturned: 2 } })
    server.on('PUT /users/3/roles', { status: 200, body: { id: 3, username: 'alice', roles: ['VIEWER'], ticketsReturned: 2 } })
    const roles = await openRolesOf('alice', user)
    await user.click(checkbox(roles, 'Operator'))
    await user.click(within(roles).getByRole('button', { name: 'Save' }))

    const confirm = await screen.findByRole('dialog', { name: 'Change the roles of alice?' })
    expect(within(confirm).getByText('alice has 2 tickets in progress and will no longer be able to take tickets. They will go back to the pool.')).toBeInTheDocument()
    expect(server.callsTo('PUT /users/3/roles')).toHaveLength(0)
    server.on('GET /users', page([ALEX, { ...ALICE, roles: ['VIEWER'] }]))
    await user.click(within(confirm).getByRole('button', { name: 'Change roles' }))
    expect(await screen.findByText('Roles updated. 2 tickets went back to the pool.')).toBeInTheDocument()
  })

  it('enables Save only when the set changed, and asks again before an ordinary change too, naming it', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, ALICE])
    server.on('PUT /users/3/roles', { status: 200, body: { id: 3, username: 'alice', roles: ['OPERATOR'] } })
    const roles = await openRolesOf('alice', user)
    expect(within(roles).getByRole('button', { name: 'Save' })).toBeDisabled()

    await user.click(checkbox(roles, 'Viewer'))
    server.on('GET /users', page([ALEX, { ...ALICE, roles: ['OPERATOR'] }]))
    await user.click(within(roles).getByRole('button', { name: 'Save' }))
    const confirm = await screen.findByRole('dialog', { name: 'Change the roles of alice?' })
    expect(within(confirm).getByText('Removes: Viewer')).toBeInTheDocument()
    expect(server.callsTo('PUT /users/3/roles')).toHaveLength(0)
    await user.click(within(confirm).getByRole('button', { name: 'Change roles' }))

    expect(await screen.findByText('Roles updated.')).toBeInTheDocument()
    expect(server.callsTo('PUT /users/3/roles')[0].body).toEqual({ roles: ['OPERATOR'], expectedRoles: ['OPERATOR', 'VIEWER'] })
    await waitFor(() => expect(within(row('alice')).queryByText('Viewer')).not.toBeInTheDocument())
  })

  it('asks again before giving Manager, and Cancel goes back with the choice kept', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ADAM, ALEX])
    server.on('PUT /users/2/roles', { status: 200, body: { id: 2, username: 'adam', roles: ['ADMIN', 'MANAGER'] } })
    const roles = await openRolesOf('adam', user)
    await user.click(checkbox(roles, 'Manager'))
    await user.click(within(roles).getByRole('button', { name: 'Save' }))

    const confirm = await screen.findByRole('dialog', { name: 'Change the roles of adam?' })
    expect(within(confirm).getByText('Adds: Manager')).toBeInTheDocument()
    expect(server.callsTo('PUT /users/2/roles')).toHaveLength(0)

    await user.click(within(confirm).getByRole('button', { name: 'Cancel' }))
    const back = await screen.findByRole('dialog', { name: 'Change roles for adam' })
    expect(checkbox(back, 'Manager')).toBeChecked()

    await user.click(within(back).getByRole('button', { name: 'Save' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Change the roles of adam?' })).getByRole('button', { name: 'Change roles' }))
    expect(await screen.findByText('Roles updated.')).toBeInTheDocument()
    expect(server.callsTo('PUT /users/2/roles')[0].body).toEqual({ roles: ['ADMIN', 'MANAGER'], expectedRoles: ['ADMIN'] })
  })

  it('asks again before leaving a user with no role', async () => {
    const { server, user } = await openUsers(['MANAGER'], [ALICE], { id: 20, username: 'morgan' })
    server.on('PUT /users/3/roles', { status: 200, body: { id: 3, username: 'alice', roles: [] } })
    const roles = await openRolesOf('alice', user)
    await user.click(checkbox(roles, 'Operator'))
    await user.click(checkbox(roles, 'Viewer'))
    await user.click(within(roles).getByRole('button', { name: 'Save' }))

    const confirm = await screen.findByRole('dialog', { name: 'Remove every role from alice?' })
    expect(within(confirm).getByText('alice will not be able to use OpsFlow until a role is given again.')).toBeInTheDocument()
    await user.click(within(confirm).getByRole('button', { name: 'Remove roles' }))
    expect(await screen.findByText('Roles updated.')).toBeInTheDocument()
    expect(server.callsTo('PUT /users/3/roles')[0].body).toEqual({ roles: [], expectedRoles: ['OPERATOR', 'VIEWER'] })
  })

  it('names the protected role in the one confirmation when the only role, Manager, is removed', async () => {
    const { user } = await openUsers(['ADMIN'], [ALEX, EVA])
    const roles = await openRolesOf('eva', user)
    await user.click(checkbox(roles, 'Manager'))
    await user.click(within(roles).getByRole('button', { name: 'Save' }))
    const confirm = await screen.findByRole('dialog', { name: 'Remove every role from eva?' })
    expect(within(confirm).getByText('Removes: Manager')).toBeInTheDocument()
  })

  it('offers a manager no protected role', async () => {
    const { user } = await openUsers(['MANAGER'], [ALICE], { id: 20 })
    const roles = await openRolesOf('alice', user)
    expect(within(roles).queryByRole('checkbox', { name: 'Administrator' })).not.toBeInTheDocument()
    expect(within(roles).queryByRole('checkbox', { name: 'Manager' })).not.toBeInTheDocument()
    expect(checkbox(roles, 'Operator')).toBeChecked()
  })

  it('lets a disabled user only lose roles, and says why', async () => {
    const { user } = await openUsers(['ADMIN'], [ALEX, BOB])
    const roles = await openRolesOf('bob', user)
    expect(within(roles).getByText('You can only remove roles from a disabled user.')).toBeInTheDocument()
    expect(checkbox(roles, 'Viewer')).toBeDisabled()
    expect(checkbox(roles, 'Manager')).toBeDisabled()
    expect(checkbox(roles, 'Operator')).toBeEnabled()
  })

  it('takes back an added role when the user was disabled meanwhile', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, ALICE])
    server.on('PUT /users/3/roles', problem(409, 'USER_NOT_ACTIVE'))
    const roles = await openRolesOf('alice', user)
    await user.click(checkbox(roles, 'Manager'))
    await user.click(within(roles).getByRole('button', { name: 'Save' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Change the roles of alice?' })).getByRole('button', { name: 'Change roles' }))

    const back = await screen.findByRole('dialog', { name: 'Change roles for alice' })
    expect(await within(back).findByText('Enable this user before adding a role.')).toBeInTheDocument()
    expect(checkbox(back, 'Manager')).not.toBeChecked()
    expect(checkbox(back, 'Manager')).toBeDisabled()
    expect(server.callsTo('GET /users').length).toBeGreaterThan(1)
  })

  it('keeps the dialog open with the choice when the change would leave no administrator', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ADAM, ALEX])
    server.on('PUT /users/2/roles', problem(409, 'LAST_ADMIN'))
    const roles = await openRolesOf('adam', user)
    await user.click(checkbox(roles, 'Operator'))
    await user.click(checkbox(roles, 'Administrator'))
    await user.click(within(roles).getByRole('button', { name: 'Save' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Change the roles of adam?' })).getByRole('button', { name: 'Change roles' }))

    const back = await screen.findByRole('dialog', { name: 'Change roles for adam' })
    expect(await within(back).findByText('At least one active administrator must remain.')).toBeInTheDocument()
    expect(checkbox(back, 'Operator')).toBeChecked()
  })

  it('closes the dialog and shows the list again when someone else changed the user', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, ALICE])
    server.on('PUT /users/3/roles', problem(409, 'USER_CHANGED'))
    const roles = await openRolesOf('alice', user)
    await user.click(checkbox(roles, 'Viewer'))
    server.on('GET /users', page([ALEX, { ...ALICE, roles: ['VIEWER'] }]))
    await user.click(within(roles).getByRole('button', { name: 'Save' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Change the roles of alice?' })).getByRole('button', { name: 'Change roles' }))

    expect(await screen.findByText('This user has changed. We refreshed the list.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(within(row('alice')).queryByText('Operator')).not.toBeInTheDocument())
  })

  it('shows a fault with its reference in the dialog', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, ALICE])
    server.on('PUT /users/3/roles', problem(500, 'INTERNAL_ERROR'))
    const roles = await openRolesOf('alice', user)
    await user.click(checkbox(roles, 'Viewer'))
    await user.click(within(roles).getByRole('button', { name: 'Save' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Change the roles of alice?' })).getByRole('button', { name: 'Change roles' }))
    expect(await within(dialog('Change roles for alice')).findByText(/report this reference: ddddc8f3b64942ff/)).toBeInTheDocument()
  })

  it('reloads the permissions after FORBIDDEN, and says Not allowed when the user page is no longer open to the viewer', async () => {
    const { server, user } = await openUsers(['MANAGER'], [ALICE], { id: 20 })
    server.on('PUT /users/3/roles', problem(403, 'FORBIDDEN'))
    const roles = await openRolesOf('alice', user)
    await user.click(checkbox(roles, 'Viewer'))
    server.on('GET /auth/me', me(['OPERATOR'], { id: 20 }))
    await user.click(within(roles).getByRole('button', { name: 'Save' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Change the roles of alice?' })).getByRole('button', { name: 'Change roles' }))
    expect(await screen.findByText('You do not have permission to open this page.')).toBeInTheDocument()
  })
})

describe('disabling, enabling, and deleting', () => {
  it('says before a disabling or a deletion how many tickets in progress go back to the pool, and after it how many went', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, { ...ALICE, ticketsInProgress: 2 }])
    await user.click(within(row('alice')).getByRole('button', { name: 'Delete' }))
    const remove = await screen.findByRole('dialog', { name: 'Delete alice?' })
    expect(within(remove).getByText('alice has 2 tickets in progress. They will go back to the pool.')).toBeInTheDocument()
    await user.click(within(remove).getByRole('button', { name: 'Cancel' }))

    await user.click(within(row('alice')).getByRole('button', { name: 'Disable' }))
    const confirm = await screen.findByRole('dialog', { name: 'Disable alice?' })
    expect(within(confirm).getByText('alice has 2 tickets in progress. They will go back to the pool.')).toBeInTheDocument()
    server.on('POST /users/3/disable', { status: 200, body: { id: 3, username: 'alice', status: 'DISABLED', ticketsReturned: 2 } })
    await user.click(within(confirm).getByRole('button', { name: 'Disable' }))
    expect(await screen.findByText('User disabled. 2 tickets went back to the pool.')).toBeInTheDocument()
  })

  it('asks before disabling, and shows the user disabled afterwards', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, ALICE])
    server.on('POST /users/3/disable', { status: 200, body: { id: 3, username: 'alice', status: 'DISABLED' } })
    await user.click(within(row('alice')).getByRole('button', { name: 'Disable' }))
    const confirm = await screen.findByRole('dialog', { name: 'Disable alice?' })
    expect(within(confirm).getByText('This user will be signed out when they next use OpsFlow. Their roles will be kept.')).toBeInTheDocument()
    expect(server.callsTo('POST /users/3/disable')).toHaveLength(0)

    server.on('GET /users', page([ALEX, { ...ALICE, status: 'DISABLED' }]))
    await user.click(within(confirm).getByRole('button', { name: 'Disable' }))
    expect(await screen.findByText('User disabled.')).toBeInTheDocument()
    await waitFor(() => expect(within(row('alice')).getByRole('button', { name: 'Enable' })).toBeInTheDocument())
  })

  it('does nothing when the confirmation is cancelled', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, ALICE])
    await user.click(within(row('alice')).getByRole('button', { name: 'Delete' }))
    const confirm = await screen.findByRole('dialog', { name: 'Delete alice?' })
    await user.click(within(confirm).getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(server.callsTo('DELETE /users/3')).toHaveLength(0)
  })

  it('says that a deletion cannot be undone, and the user disappears from the list', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, ALICE])
    server.on('DELETE /users/3', { status: 204 })
    await user.click(within(row('alice')).getByRole('button', { name: 'Delete' }))
    const confirm = await screen.findByRole('dialog', { name: 'Delete alice?' })
    expect(within(confirm).getByText('This user will disappear from the list. This action cannot be undone.')).toBeInTheDocument()

    server.on('GET /users', page([ALEX]))
    await user.click(within(confirm).getByRole('button', { name: 'Delete' }))
    expect(await screen.findByText('User deleted.')).toBeInTheDocument()
    await waitFor(() => expect(within(list()).queryByText('alice')).not.toBeInTheDocument())
  })

  it('goes back to the first page when a deletion empties the page shown', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX], {})
    server.on('GET /users', page([ADAM, ALEX], 'c1'))
    server.on('GET /users?roles=NONE', page([ALEX], 'c1'))
    server.on('GET /users?roles=NONE&cursor=c1&direction=next', page([CHLOE], null, 'p1'))
    server.on('DELETE /users/5', { status: 204 })
    await user.click(screen.getByRole('button', { name: 'No role' }))
    await user.click(await screen.findByRole('button', { name: 'Next' }))
    await findInList('chloe')

    server.on('GET /users?roles=NONE&cursor=c1&direction=next', page([]))
    server.on('GET /users?roles=NONE', page([ALEX]))
    await user.click(within(row('chloe')).getByRole('button', { name: 'Delete' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Delete chloe?' })).getByRole('button', { name: 'Delete' }))
    expect(await findInList('alexadmin')).toBeInTheDocument()
  })

  it('enables a user at once, without a confirmation', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, BOB])
    server.on('POST /users/4/enable', { status: 200, body: { id: 4, username: 'bob', status: 'ACTIVE' } })
    server.on('GET /users', page([ALEX, { ...BOB, status: 'ACTIVE' }]))
    await user.click(within(row('bob')).getByRole('button', { name: 'Enable' }))
    expect(await screen.findByText('User enabled.')).toBeInTheDocument()
    expect(server.callsTo('POST /users/4/enable')).toHaveLength(1)
    await waitFor(() => expect(within(row('bob')).getByRole('button', { name: 'Disable' })).toBeInTheDocument())
  })

  it('keeps the confirmation open when disabling would leave no administrator', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ADAM, ALEX])
    server.on('POST /users/2/disable', problem(409, 'LAST_ADMIN'))
    await user.click(within(row('adam')).getByRole('button', { name: 'Disable' }))
    const confirm = await screen.findByRole('dialog', { name: 'Disable adam?' })
    await user.click(within(confirm).getByRole('button', { name: 'Disable' }))
    expect(await within(confirm).findByText('At least one active administrator must remain.')).toBeInTheDocument()
  })

  it('closes the confirmation and refreshes the list when the user was deleted meanwhile', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, ALICE])
    server.on('POST /users/3/disable', problem(404, 'NOT_FOUND'))
    await user.click(within(row('alice')).getByRole('button', { name: 'Disable' }))
    server.on('GET /users', page([ALEX]))
    await user.click(within(await screen.findByRole('dialog', { name: 'Disable alice?' })).getByRole('button', { name: 'Disable' }))
    expect(await screen.findByText('This user is no longer available. We refreshed the list.')).toBeInTheDocument()
    await waitFor(() => expect(within(list()).queryByText('alice')).not.toBeInTheDocument())
  })

  it('says that the server cannot be reached when an enabling got no answer', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, BOB])
    server.on('POST /users/4/enable', 'network-error')
    await user.click(within(row('bob')).getByRole('button', { name: 'Enable' }))
    expect(await screen.findByText('Cannot reach the server. Check your connection and try again.')).toBeInTheDocument()
  })

  it('shows that someone else changed the status meanwhile', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALEX, BOB])
    server.on('POST /users/4/enable', problem(409, 'USER_CHANGED', { userStatus: 'ACTIVE' }))
    server.on('GET /users', page([ALEX, { ...BOB, status: 'ACTIVE' }]))
    await user.click(within(row('bob')).getByRole('button', { name: 'Enable' }))
    expect(await screen.findByText('This user has changed. We refreshed the list.')).toBeInTheDocument()
  })
})
