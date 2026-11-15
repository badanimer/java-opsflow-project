import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Item } from '../../api/items'
import { FakeServer, me, openApp, problem, storeToken } from '../../test/server'

/**
 * Adding and editing a warehouse item, with its picture (Figma frames "Add item / ..." and "Edit item /
 * ..."): the checks of the page, the upload, and every answer of the server.
 */

const PAPER: Item = { id: 1, sku: 'A4Q2M9X1', name: 'A4 paper', imageUrl: '/api/v1/images/items/paper.webp', availableQuantity: 40, listed: true }
const GLOVES: Item = { id: 2, sku: 'T7V9M1F2', name: 'Protective gloves', imageUrl: '/api/v1/images/items/0a1b2c3d-0000-0000-0000-000000000000.webp', availableQuantity: 42, listed: false }
const WALLETS: Item = { id: 3, sku: 'E7B4N8Z6', name: 'Document wallets', imageUrl: null, availableQuantity: 12, listed: false }

const PNG_BYTES = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13])
const STORED = { path: 'items/9a1c0000-0000-0000-0000-000000000000.png', url: '/api/v1/images/items/9a1c0000-0000-0000-0000-000000000000.png' }

function page(items: Item[]) {
  return { status: 200, body: { items, nextCursor: null, prevCursor: null } }
}

function png(name = 'tape.png') {
  return new File([PNG_BYTES], name, { type: 'image/png' })
}

async function openStock(roles: string[], items: Item[]) {
  storeToken()
  const server = new FakeServer().on('GET /auth/me', me(roles, { id: 20, username: 'morgan' })).on('GET /items', page(items))
  const user = openApp('/stock')
  await screen.findByRole('searchbox', { name: 'Search by name' })
  if (items.length > 0) await within(screen.getByRole('region', { name: 'Item list' })).findByText(items[0].name)
  return { server, user }
}

async function openAdd(user: Awaited<ReturnType<typeof openStock>>['user']) {
  await user.click(screen.getByRole('button', { name: 'Add item' }))
  return screen.findByRole('dialog', { name: 'Add item' })
}

function fileInput(dialog: HTMLElement): HTMLInputElement {
  return dialog.querySelector('input[type="file"]') as HTMLInputElement
}

function row(name: string): HTMLElement {
  return within(screen.getByRole('region', { name: 'Item list' })).getByText(name).closest('tr') as HTMLElement
}

describe('adding an item', () => {
  it('is offered to a manager only', async () => {
    await openStock(['VIEWER'], [WALLETS])
    expect(screen.queryByRole('button', { name: 'Add item' })).not.toBeInTheDocument()
  })

  it('checks the name in the page and sends nothing until it is right', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    const dialog = await openAdd(user)
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(within(dialog).getByText('Enter a name.')).toBeInTheDocument()

    await user.click(within(dialog).getByLabelText('Name'))
    await user.paste('x'.repeat(201))
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(within(dialog).getByText('Enter a name with 200 characters or fewer.')).toBeInTheDocument()
    expect(server.callsTo('POST /items')).toHaveLength(0)
  })

  for (const typed of ['1.5', '1000000', 'ten']) {
    it(`refuses the starting quantity ${typed} in the page`, async () => {
      const { server, user } = await openStock(['MANAGER'], [WALLETS])
      const dialog = await openAdd(user)
      await user.type(within(dialog).getByLabelText('Name'), 'Tape')
      await user.type(within(dialog).getByLabelText('Starting quantity (optional)'), typed)
      await user.click(within(dialog).getByRole('button', { name: 'Save' }))
      expect(within(dialog).getByText('Enter a whole number from 0 to 999,999.')).toBeInTheDocument()
      expect(server.callsTo('POST /items')).toHaveLength(0)
    })
  }

  it('adds the item with a trimmed name and its quantity, and names the generated SKU', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('POST /items', { status: 201, body: { id: 9, sku: 'C6M9Q2L7', name: 'Carton sealing tape', imageUrl: null, availableQuantity: 18, listed: false } })
    const dialog = await openAdd(user)
    expect(within(dialog).getByText('Leave blank to start at 0.')).toBeInTheDocument()
    await user.type(within(dialog).getByLabelText('Name'), '  Carton sealing tape ')
    await user.type(within(dialog).getByLabelText('Starting quantity (optional)'), '18')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Item added. SKU: C6M9Q2L7.')).toBeInTheDocument()
    expect(server.callsTo('POST /items')[0].body).toEqual({ name: 'Carton sealing tape', imagePath: null, startingQuantity: 18 })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('leaves out a blank starting quantity', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('POST /items', { status: 201, body: { ...WALLETS, id: 9, sku: 'C6M9Q2L7', name: 'Ruler' } })
    const dialog = await openAdd(user)
    await user.type(within(dialog).getByLabelText('Name'), 'Ruler')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    await screen.findByText('Item added. SKU: C6M9Q2L7.')
    expect(server.callsTo('POST /items')[0].body).toEqual({ name: 'Ruler', imagePath: null })
  })

  it('uploads a picture first and saves its path with the item', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('POST /item-images', { status: 201, body: STORED })
    server.on('POST /items', { status: 201, body: { ...WALLETS, id: 9, sku: 'C6M9Q2L7', name: 'Tape', imageUrl: STORED.url } })
    const dialog = await openAdd(user)
    await user.type(within(dialog).getByLabelText('Name'), 'Tape')
    await user.upload(fileInput(dialog), png('carton-sealing-tape.png'))

    expect(await within(dialog).findByText('Image uploaded')).toBeInTheDocument()
    expect(within(dialog).getByText('carton-sealing-tape.png')).toBeInTheDocument()
    expect(server.callsTo('POST /item-images')[0].body).toBeInstanceOf(FormData)

    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    await screen.findByText('Item added. SKU: C6M9Q2L7.')
    expect(server.callsTo('POST /items')[0].body).toEqual({ name: 'Tape', imagePath: STORED.path })
  })

  it('refuses a picture over 2 MB or of another type before sending it, and keeps what was typed', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    const dialog = await openAdd(user)
    await user.type(within(dialog).getByLabelText('Name'), 'Tape')

    const large = new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' })
    await user.upload(fileInput(dialog), large)
    expect(await within(dialog).findByText('Choose an image that is 2 MB or smaller.')).toBeInTheDocument()

    const renamed = new File(['just some text'], 'photo.png', { type: 'image/png' })
    await user.upload(fileInput(dialog), renamed)
    expect(await within(dialog).findByText('Choose a JPEG, PNG or WebP image.')).toBeInTheDocument()

    expect(server.callsTo('POST /item-images')).toHaveLength(0)
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Tape')
  })

  it('shows the refusals of the upload, and a failed upload keeps the picture there was', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    const dialog = await openAdd(user)
    server.on('POST /item-images', problem(400, 'IMAGE_TYPE_NOT_ALLOWED'))
    await user.upload(fileInput(dialog), png())
    expect(await within(dialog).findByText('Choose a JPEG, PNG or WebP image.')).toBeInTheDocument()

    server.on('POST /item-images', problem(413, 'IMAGE_TOO_LARGE'))
    await user.upload(fileInput(dialog), png())
    expect(await within(dialog).findByText('Choose an image that is 2 MB or smaller.')).toBeInTheDocument()

    server.on('POST /item-images', { status: 201, body: STORED })
    await user.upload(fileInput(dialog), png('first.png'))
    await within(dialog).findByText('first.png')

    server.on('POST /item-images', 'network-error')
    await user.upload(fileInput(dialog), png('second.png'))
    expect(await within(dialog).findByText('Cannot reach the server. Check your connection and try again.')).toBeInTheDocument()
    expect(within(dialog).getByText('first.png')).toBeInTheDocument()

    server.on('POST /item-images', problem(500, 'INTERNAL_ERROR'))
    await user.upload(fileInput(dialog), png('third.png'))
    expect(await within(dialog).findByText(/report this reference/)).toBeInTheDocument()
  })

  it('removes the picture from the draft only', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('POST /item-images', { status: 201, body: STORED })
    server.on('POST /items', { status: 201, body: { ...WALLETS, id: 9, sku: 'C6M9Q2L7', name: 'Tape' } })
    const dialog = await openAdd(user)
    await user.type(within(dialog).getByLabelText('Name'), 'Tape')
    await user.upload(fileInput(dialog), png())
    await within(dialog).findByText('Image uploaded')
    await user.click(within(dialog).getByRole('button', { name: 'Remove image' }))
    expect(within(dialog).getByText('Image removed.')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    await screen.findByText('Item added. SKU: C6M9Q2L7.')
    expect(server.callsTo('POST /items')[0].body).toEqual({ name: 'Tape', imagePath: null })
  })

  it('shows a taken name under the name and keeps the picture', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('POST /item-images', { status: 201, body: STORED })
    server.on('POST /items', problem(409, 'ITEM_NAME_TAKEN'))
    const dialog = await openAdd(user)
    await user.type(within(dialog).getByLabelText('Name'), 'document WALLETS')
    await user.upload(fileInput(dialog), png())
    await within(dialog).findByText('Image uploaded')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(await within(dialog).findByText('An item with this name already exists.')).toBeInTheDocument()
    expect(within(dialog).getByText('Image uploaded')).toBeInTheDocument()
  })

  it('asks for the picture again when the server no longer accepts its path', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('POST /item-images', { status: 201, body: STORED })
    server.on('POST /items', problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'imagePath', message: 'must be a path returned by the image upload' }] }))
    const dialog = await openAdd(user)
    await user.type(within(dialog).getByLabelText('Name'), 'Tape')
    await user.upload(fileInput(dialog), png())
    await within(dialog).findByText('Image uploaded')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(await within(dialog).findByText('Choose the image again.')).toBeInTheDocument()
    expect(within(dialog).queryByText('Image uploaded')).not.toBeInTheDocument()
  })

  it('keeps the draft when the save got no answer', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('POST /items', 'network-error')
    const dialog = await openAdd(user)
    await user.type(within(dialog).getByLabelText('Name'), 'Tape')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(await within(dialog).findByText('Cannot reach the server. Check your connection and try again.')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Tape')
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeEnabled()
  })

  it('ends with Close when the permission was taken away', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('POST /items', problem(403, 'FORBIDDEN'))
    const dialog = await openAdd(user)
    await user.type(within(dialog).getByLabelText('Name'), 'Tape')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(await within(dialog).findByText('You no longer have permission to make this change.')).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Close' })).toBeInTheDocument()
  })
})

describe('editing an item', () => {
  it('is offered on an item that is not on sale only', async () => {
    await openStock(['MANAGER'], [PAPER, GLOVES])
    expect(within(row('A4 paper')).queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(within(row('Protective gloves')).getByRole('button', { name: 'Edit' })).toBeInTheDocument()
  })

  it('shows the SKU and the current picture, enables Save only after a change, and keeps the picture it does not change', async () => {
    const { server, user } = await openStock(['MANAGER'], [PAPER, GLOVES])
    server.on('PUT /items/2', { status: 200, body: { ...GLOVES, name: 'Protective work gloves' } })
    await user.click(within(row('Protective gloves')).getByRole('button', { name: 'Edit' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit item' })
    expect(within(dialog).getByText('SKU T7V9M1F2')).toBeInTheDocument()
    expect(within(dialog).getByText('Current image')).toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Starting quantity (optional)')).not.toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled()

    await user.type(within(dialog).getByLabelText('Name'), ' ')
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled()
    await user.clear(within(dialog).getByLabelText('Name'))
    await user.type(within(dialog).getByLabelText('Name'), 'Protective work gloves')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Item saved.')).toBeInTheDocument()
    expect(server.callsTo('PUT /items/2')[0].body).toEqual({ name: 'Protective work gloves', imagePath: 'items/0a1b2c3d-0000-0000-0000-000000000000.webp' })
  })

  it('removes the picture by sending no path', async () => {
    const { server, user } = await openStock(['MANAGER'], [GLOVES])
    server.on('PUT /items/2', { status: 200, body: { ...GLOVES, imageUrl: null } })
    await user.click(within(row('Protective gloves')).getByRole('button', { name: 'Edit' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit item' })
    await user.click(within(dialog).getByRole('button', { name: 'Remove image' }))
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    await screen.findByText('Item saved.')
    expect(server.callsTo('PUT /items/2')[0].body).toEqual({ name: 'Protective gloves', imagePath: null })
  })

  it('ends with Close when the item went on sale, and Close reloads the list', async () => {
    const { server, user } = await openStock(['MANAGER'], [GLOVES])
    server.on('PUT /items/2', problem(409, 'ITEM_LISTED'))
    await user.click(within(row('Protective gloves')).getByRole('button', { name: 'Edit' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit item' })
    await user.type(within(dialog).getByLabelText('Name'), ' 2')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(await within(dialog).findByText('This item is on sale. Delist it on the Products page to edit.')).toBeInTheDocument()
    const before = server.callsTo('GET /items').length
    await user.click(within(dialog).getByRole('button', { name: 'Close' }))
    await waitFor(() => expect(server.callsTo('GET /items').length).toBe(before + 1))
  })

  it('ends with Close when the item is gone', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('PUT /items/3', problem(404, 'NOT_FOUND'))
    await user.click(within(row('Document wallets')).getByRole('button', { name: 'Edit' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit item' })
    await user.type(within(dialog).getByLabelText('Name'), ' 2')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(await within(dialog).findByText('This item is no longer available.')).toBeInTheDocument()
  })

  it('adds a picture to an item that has none', async () => {
    const { server, user } = await openStock(['MANAGER'], [WALLETS])
    server.on('POST /item-images', { status: 201, body: STORED })
    server.on('PUT /items/3', { status: 200, body: { ...WALLETS, imageUrl: STORED.url } })
    await user.click(within(row('Document wallets')).getByRole('button', { name: 'Edit' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit item' })
    expect(within(dialog).getByText('Choose image')).toBeInTheDocument()
    await user.upload(fileInput(dialog), png('wallets.png'))
    await within(dialog).findByText('Image uploaded')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    await screen.findByText('Item saved.')
    expect(server.callsTo('PUT /items/3')[0].body).toEqual({ name: 'Document wallets', imagePath: STORED.path })
  })
})
