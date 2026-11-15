import { useState } from 'react'
import { Alert, Button, Form, Input, Modal, Typography } from 'antd'
import { ApiError, NetworkError } from '../../api/client'
import { createItem, editItem, imagePathOf, type Item } from '../../api/items'
import { useAuth } from '../../auth/context'
import { ImageField, type DraftImage } from '../../components/ImageField'
import { TEXT } from '../../text'
import { generalFailure, type Notice } from '../../lib/failure'

const NAME_MAX = 200
const QUANTITY_MAX = 999_999

interface Props {
  /** The item to edit, or null to add one. */
  item: Item | null
  /** Saved: the page shows the message and loads the list again. */
  onSaved: (message: string) => void
  /** Closed. `stale` is true when the dialog ended on a refusal, so the list is loaded again. */
  onClose: (stale: boolean) => void
}

type FieldErrors = { name?: string; quantity?: string; image?: string }

/**
 * Add item and Edit item, one dialog (Figma frames "Add item / ..." and "Edit item / ..."). Adding asks
 * for a name, an optional picture, and an optional starting quantity; editing changes the name and the
 * picture of an item that is not on sale, and Save is enabled only when something changed. The page
 * checks every field before it sends anything, and the server checks again. A refusal that ends the
 * dialog (the item went on sale, is gone, or the permission was lost) leaves only Close.
 */
export function ItemDialog({ item, onSaved, onClose }: Props) {
  const { refresh } = useAuth()
  const editing = item !== null
  const initialImage: DraftImage | null =
    item?.imageUrl ? { path: imagePathOf(item.imageUrl) ?? '', url: item.imageUrl, fileName: fileNameOf(item.imageUrl), current: true } : null

  const [name, setName] = useState(item?.name ?? '')
  const [quantity, setQuantity] = useState('')
  const [image, setImage] = useState<DraftImage | null>(initialImage)
  const [uploading, setUploading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [notice, setNotice] = useState<Notice | null>(null)
  const [ended, setEnded] = useState(false)

  const changed = item === null || name.trim() !== item.name || (image?.path ?? null) !== (initialImage?.path ?? null)

  function forbidden() {
    refresh().catch(() => {})
    setNotice({ type: 'error', message: TEXT.noLongerAllowed })
    setEnded(true)
  }

  function check(): { name: string; quantity: number | null } | null {
    const found: FieldErrors = {}
    const trimmed = name.trim()
    if (trimmed === '') found.name = TEXT.stock.nameRequired
    else if (trimmed.length > NAME_MAX) found.name = TEXT.stock.nameTooLong
    let count: number | null = null
    if (!editing && quantity.trim() !== '') {
      const typed = quantity.trim()
      if (!/^\d+$/.test(typed) || Number(typed) > QUANTITY_MAX) found.quantity = TEXT.stock.quantityInvalid
      else count = Number(typed)
    }
    setErrors(found)
    return Object.keys(found).length > 0 ? null : { name: trimmed, quantity: count }
  }

  async function save() {
    setNotice(null)
    const values = check()
    if (!values) return
    setSaving(true)
    try {
      const imagePath = image?.path ?? null
      if (item) {
        await editItem(item.id, values.name, imagePath)
        onSaved(TEXT.stock.itemSaved)
      } else {
        const created = await createItem(values.name, imagePath, values.quantity)
        onSaved(TEXT.stock.itemAdded(created.sku))
      }
    } catch (failure) {
      setSaving(false)
      refused(failure)
    }
  }

  function refused(failure: unknown) {
    if (failure instanceof ApiError) {
      switch (failure.code) {
        case 'ITEM_NAME_TAKEN':
          return setErrors({ name: TEXT.stock.nameTaken })
        case 'INVALID_QUANTITY':
          return setErrors({ quantity: TEXT.stock.quantityInvalid })
        case 'VALIDATION_FAILED': {
          const fields = failure.errors.map((error) => error.field)
          if (fields.includes('imagePath')) {
            setImage(null)
            return setErrors({ image: TEXT.image.chooseAgain })
          }
          return setErrors({ name: name.trim() === '' ? TEXT.stock.nameRequired : TEXT.stock.nameTooLong })
        }
        case 'ITEM_LISTED':
          setNotice({ type: 'warning', message: TEXT.stock.itemOnSale })
          return setEnded(true)
        case 'NOT_FOUND':
          setNotice({ type: 'warning', message: TEXT.stock.itemGone })
          return setEnded(true)
        case 'FORBIDDEN':
          return forbidden()
      }
    }
    setNotice(failure instanceof NetworkError ? { type: 'error', message: TEXT.cannotReach } : generalFailure(failure))
  }

  const busy = saving || uploading
  return (
    <Modal
      open
      title={editing ? TEXT.stock.editItem : TEXT.stock.addItem}
      closable={false}
      maskClosable={false}
      keyboard={!busy}
      onCancel={busy ? undefined : () => onClose(ended)}
      destroyOnClose
      footer={
        ended
          ? [
              <Button key="close" onClick={() => onClose(true)} autoFocus>
                {TEXT.close}
              </Button>,
            ]
          : [
              <Button key="cancel" onClick={() => onClose(false)} disabled={saving}>
                {TEXT.cancel}
              </Button>,
              <Button key="save" type="primary" onClick={() => void save()} disabled={uploading || !changed} loading={saving}>
                {saving ? TEXT.stock.saving : TEXT.stock.save}
              </Button>,
            ]
      }
    >
      <Form layout="vertical" requiredMark={false} disabled={saving || ended} className="dialog-body" component="div">
        {item && <Typography.Text type="secondary">{TEXT.stock.skuLine(item.sku)}</Typography.Text>}
        <Form.Item label={TEXT.stock.nameLabel} validateStatus={errors.name ? 'error' : undefined} help={errors.name} htmlFor="item-name">
          <Input
            id="item-name"
            value={name}
            placeholder={TEXT.stock.namePlaceholder}
            onChange={(event) => setName(event.target.value)}
          />
        </Form.Item>
        <Form.Item label={TEXT.stock.imageLabel}>
          <ImageField
            value={image}
            onChange={(next) => {
              setImage(next)
              setErrors((current) => ({ ...current, image: undefined }))
            }}
            onBusyChange={setUploading}
            onForbidden={forbidden}
            error={errors.image}
            disabled={saving || ended}
          />
        </Form.Item>
        {!editing && (
          <Form.Item
            label={TEXT.stock.quantityLabel}
            validateStatus={errors.quantity ? 'error' : undefined}
            help={errors.quantity ?? TEXT.stock.quantityHint}
            htmlFor="item-quantity"
          >
            <Input id="item-quantity" inputMode="numeric" value={quantity} placeholder="0" onChange={(event) => setQuantity(event.target.value)} />
          </Form.Item>
        )}
        {notice && <Alert type={notice.type} showIcon message={notice.message} role="alert" />}
      </Form>
    </Modal>
  )
}

/** The name the person sees for a saved picture: the last part of its address. */
function fileNameOf(url: string): string {
  return url.slice(url.lastIndexOf('/') + 1)
}
