import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { problem } from '../../test/server'
import { setScreenWidth } from '../../test/screen'
import { ADAM, ALEX, ALICE, BOB, CHLOE, EVA, actionsIn, findInList, list, openUsers, page, row } from '../../test/users'

/**
 * The list of the user page: what each viewer sees, the filters, the paging, and the states of a list
 * (User page in docs/frontend/page-design.md, and the Figma frames Users / ...).
 */

const ROLE_BUTTONS = ['Administrator', 'Manager', 'Operator', 'Viewer', 'No role']

function roleButtons(): string[] {
  const group = screen.getByRole('group', { name: 'Roles' })
  return within(group).getAllByRole('button').map((button) => button.textContent ?? '')
}

describe('what each viewer sees', () => {
  it('shows an administrator their own row marked You with no controls, and every control on the others', async () => {
    await openUsers(['ADMIN'], [ADAM, ALEX, ALICE, BOB, CHLOE, EVA])
    expect(within(row('alexadmin')).getByText('You')).toBeInTheDocument()
    expect(actionsIn('alexadmin')).toEqual([])
    expect(actionsIn('adam')).toEqual(['Change roles', 'Disable', 'Delete'])
    expect(actionsIn('eva')).toEqual(['Change roles', 'Disable', 'Delete'])
    expect(actionsIn('bob')).toEqual(['Change roles', 'Enable', 'Delete'])
    expect(roleButtons()).toEqual(ROLE_BUTTONS)
  })

  it('offers a manager only the role buttons of the roles they manage', async () => {
    await openUsers(['MANAGER'], [ALICE, BOB, CHLOE], { id: 20, username: 'morgan' })
    expect(roleButtons()).toEqual(['Operator', 'Viewer', 'No role'])
    expect(actionsIn('alice')).toEqual(['Change roles', 'Disable', 'Delete'])
  })

  it('hides the controls of a protected user from a viewer who may manage only unprotected users', async () => {
    // The server would not list eva for such a viewer; the page must not offer anything even if it did.
    await openUsers(['MANAGER'], [ALICE, EVA], { id: 20, username: 'morgan' })
    expect(actionsIn('eva')).toEqual([])
  })

  it('leaves out the whole Actions column for a viewer who may change nothing', async () => {
    await openUsers(['SUPPORT'], [ALICE, BOB], { id: 30, username: 'audsupport', permissions: ['PRODUCT_VIEW', 'USER_VIEW'] })
    expect(screen.queryByRole('columnheader', { name: 'Actions' })).not.toBeInTheDocument()
    expect(actionsIn('alice')).toEqual([])
  })

  it('offers only Change roles to a viewer who may assign roles but not manage accounts', async () => {
    await openUsers(['SUPPORT'], [ALICE, BOB], { id: 30, permissions: ['PRODUCT_VIEW', 'USER_VIEW', 'ROLE_ASSIGN'] })
    expect(actionsIn('alice')).toEqual(['Change roles'])
    expect(actionsIn('bob')).toEqual(['Change roles'])
  })

  it('shows the protected role buttons to a viewer who sees protected users through USER_MANAGE_PROTECTED alone', async () => {
    await openUsers(['SUPPORT'], [ADAM], { id: 30, permissions: ['PRODUCT_VIEW', 'USER_VIEW', 'USER_MANAGE', 'USER_MANAGE_PROTECTED'] })
    expect(roleButtons()).toEqual(ROLE_BUTTONS)
    expect(actionsIn('adam')).toEqual(['Disable', 'Delete'])
  })

  it('shows the status and the roles of each user, and No role for a user waiting for one', async () => {
    await openUsers(['ADMIN'], [ALICE, BOB, CHLOE])
    const bob = row('bob')
    expect(within(bob).getByText('Disabled')).toBeInTheDocument()
    const chloe = row('chloe')
    expect(within(chloe).getByText('No role')).toBeInTheDocument()
    const alice = row('alice')
    expect(within(alice).getByText('Operator')).toBeInTheDocument()
    expect(within(alice).getByText('Viewer')).toBeInTheDocument()
  })
})

describe('the filters', () => {
  it('sends the search and the chosen role buttons together, and starts again from the first page', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALICE, BOB])
    server.on('GET /users?search=al', page([ALEX, ALICE]))
    server.on('GET /users?search=al&roles=OPERATOR', page([ALICE]))
    server.on('GET /users?search=al&roles=OPERATOR&roles=NONE', page([ALICE]))

    await user.type(screen.getByRole('searchbox', { name: 'Search by username' }), 'al{Enter}')
    expect(await findInList('alexadmin')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Operator' }))
    await user.click(screen.getByRole('button', { name: 'No role' }))

    expect(screen.getByRole('button', { name: 'Operator' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'No role' })).toHaveAttribute('aria-pressed', 'true')
    expect(server.callsTo('GET /users?search=al&roles=OPERATOR&roles=NONE')).toHaveLength(1)
    expect(within(list()).queryByText('alexadmin')).not.toBeInTheDocument()
  })

  it('says that nothing was found, and Clear filters shows the whole list again', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALICE, BOB])
    server.on('GET /users?search=zzz', page([]))
    await user.type(screen.getByRole('searchbox', { name: 'Search by username' }), 'zzz{Enter}')
    expect(await screen.findByText('No users found')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(await findInList('alice')).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Search by username' })).toHaveValue('')
  })

  it('takes at most 64 characters in the search box', async () => {
    await openUsers(['ADMIN'], [ALICE])
    expect(screen.getByRole('searchbox', { name: 'Search by username' })).toHaveAttribute('maxlength', '64')
  })

  it('says so when the server refused the filters, and Clear filters starts again', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALICE])
    server.on('GET /users?roles=OPERATOR', problem(400, 'VALIDATION_FAILED'))
    await user.click(screen.getByRole('button', { name: 'Operator' }))
    expect(await screen.findByText('We could not apply these filters. Clear the filters and try again.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(await findInList('alice')).toBeInTheDocument()
  })
})

describe('the paging', () => {
  it('shows Next on the first page, both in the middle, and Previous on the last, with the filters kept', async () => {
    const { server, user } = await openUsers(['ADMIN'], [])
    server.on('GET /users', page([ADAM, ALEX], 'c1'))
    server.on('GET /users?search=a', page([ADAM, ALEX], 'c1'))
    server.on('GET /users?search=a&cursor=c1&direction=next', page([ALICE], 'c2', 'p2'))
    server.on('GET /users?search=a&cursor=c2&direction=next', page([CHLOE], null, 'p3'))
    server.on('GET /users?search=a&cursor=p3&direction=prev', page([ALICE], 'c2', 'p2'))

    await user.type(screen.getByRole('searchbox', { name: 'Search by username' }), 'a{Enter}')
    await findInList('adam')
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Next' }))
    await findInList('alice')
    expect(screen.getByRole('button', { name: 'Previous' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Next' }))
    await findInList('chloe')
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Previous' }))
    expect(await findInList('alice')).toBeInTheDocument()
  })
})

describe('the states of the list', () => {
  it('says when there is nobody to show', async () => {
    await openUsers(['MANAGER'], [], { id: 20 })
    expect(await screen.findByText('No users to show')).toBeInTheDocument()
  })

  it('says that the server cannot be reached, and Try again loads the list', async () => {
    const { server, user } = await openUsers(['ADMIN'], [])
    server.on('GET /users?roles=OPERATOR', 'network-error')
    await user.click(screen.getByRole('button', { name: 'Operator' }))
    expect(await screen.findByText('Cannot reach the server. Check your connection and try again.')).toBeInTheDocument()
    server.on('GET /users?roles=OPERATOR', page([ALICE]))
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await findInList('alice')).toBeInTheDocument()
  })

  it('shows the reference of a fault of the server', async () => {
    const { server, user } = await openUsers(['ADMIN'], [ALICE])
    server.on('GET /users?roles=VIEWER', problem(500, 'INTERNAL_ERROR'))
    await user.click(screen.getByRole('button', { name: 'Viewer' }))
    expect(await screen.findByText(/report this reference: ddddc8f3b64942ff/)).toBeInTheDocument()
  })
})

describe('a small screen', () => {
  it('stacks the users with their controls', async () => {
    setScreenWidth(390)
    await openUsers(['ADMIN'], [ALEX, ALICE])
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    const alice = row('alice')
    expect(within(alice).getByRole('button', { name: 'Change roles' })).toBeInTheDocument()
    const alex = row('alexadmin')
    expect(within(alex).queryAllByRole('button')).toHaveLength(0)
  })
})
