import { useState } from 'react'
import { Alert, Button, Input, Modal, Typography } from 'antd'
import { ApiError, NetworkError } from '../../api/client'
import { placeOrder, type PlacedOrder } from '../../api/orders'
import type { ShopProduct } from '../../api/products'
import { useAuth } from '../../auth/context'
import { ItemThumbnail } from '../../components/ItemThumbnail'
import { formatPrice } from '../../lib/format'
import { TEXT } from '../../text'

const MAX_QUANTITY = 9999

/** The quantity typed, as a whole number from 1 to 9,999, or null. */
function orderQuantity(text: string): number | null {
  const trimmed = text.trim()
  if (!/^\d+$/.test(trimmed)) return null
  const value = Number(trimmed)
  return value >= 1 && value <= MAX_QUANTITY ? value : null
}

type Outcome =
  | { kind: 'placed'; order: PlacedOrder }
  /** A refusal. `retry` offers Try again with the same key; `reload` makes Close reload the products. */
  | { kind: 'refused'; title: string; message: string; orderNo?: string; retry?: boolean; reload?: boolean }

/** A new idempotency key: one for each dialog, sent again on every retry of it. */
function newKey(): string {
  return crypto.randomUUID()
}

/**
 * The order dialog (Figma frames "Place order / ..."; The order dialog in docs/database/database-design.md):
 * the product as the page showed it and a quantity from 1, by typing or with minus and plus. The dialog is
 * the confirmation. Every recorded result shows its order number with a way to copy it, and the stock is
 * never shown. A retry after Busy or after no answer sends the same idempotency key, so it never places a
 * second order.
 */
export function OrderDialog({ product, onClose }: { product: ShopProduct; onClose: (reload: boolean) => void }) {
  const { refresh } = useAuth()
  const [key] = useState(newKey)
  const [quantityText, setQuantityText] = useState('1')
  const [touched, setTouched] = useState(false)
  const [placing, setPlacing] = useState(false)
  const [outcome, setOutcome] = useState<Outcome | null>(null)
  const [copied, setCopied] = useState(false)

  const quantity = orderQuantity(quantityText)
  const showInvalid = touched && quantity === null

  function step(by: number) {
    const current = quantity ?? 1
    setTouched(true)
    setQuantityText(String(Math.min(MAX_QUANTITY, Math.max(1, current + by))))
  }

  async function send(amount: number) {
    setPlacing(true)
    setOutcome(null)
    try {
      const order = await placeOrder(product.id, amount, product.name, product.price, key)
      setOutcome({ kind: 'placed', order })
    } catch (error) {
      setOutcome(refusalOf(error))
    } finally {
      setPlacing(false)
    }
  }

  function refusalOf(error: unknown): Outcome {
    if (error instanceof NetworkError) return { kind: 'refused', title: TEXT.order.unknownTitle, message: TEXT.cannotReach, retry: true }
    if (!(error instanceof ApiError)) return { kind: 'refused', title: TEXT.order.faultTitle, message: TEXT.somethingWrong() }
    const refused = (message: string, extra: Partial<Extract<Outcome, { kind: 'refused' }>> = {}): Outcome => ({
      kind: 'refused',
      title: TEXT.order.refusedTitle,
      message,
      orderNo: error.orderNo,
      ...extra,
    })
    switch (error.code) {
      case 'PRODUCT_DELISTED':
        return refused(TEXT.order.delisted, { reload: true })
      case 'PRODUCT_CHANGED':
        return refused(TEXT.order.changed, { reload: true })
      case 'OUT_OF_STOCK':
        return refused(TEXT.order.outOfStock)
      case 'INVALID_QUANTITY':
        return refused(TEXT.order.quantityInvalid)
      case 'BUSY':
        return refused(TEXT.order.busy, { retry: true })
      case 'NOT_FOUND':
        return refused(TEXT.order.productGone, { reload: true })
      case 'FORBIDDEN':
        refresh().catch(() => {})
        return refused(TEXT.noLongerAllowed)
      case 'VALIDATION_FAILED':
        return refused(TEXT.order.requestRefused)
      default:
        return { kind: 'refused', title: TEXT.order.faultTitle, message: TEXT.somethingWrong(error.requestId) }
    }
  }

  function submit() {
    setTouched(true)
    if (quantity === null) return
    void send(quantity)
  }

  async function copy(orderNo: string) {
    try {
      await navigator.clipboard.writeText(orderNo)
      setCopied(true)
    } catch {
      // The browser refused the clipboard; the number stays on the screen to copy by hand.
    }
  }

  const reloadOnClose = outcome?.kind === 'placed' || (outcome?.kind === 'refused' && outcome.reload === true)
  const close = () => onClose(reloadOnClose)
  const title = outcome === null ? TEXT.order.title : outcome.kind === 'placed' ? TEXT.order.placedTitle : outcome.title
  const orderNo = outcome?.kind === 'placed' ? outcome.order.orderNo : outcome?.orderNo

  return (
    <Modal
      open
      title={title}
      onCancel={placing ? undefined : close}
      closable={!placing}
      maskClosable={false}
      keyboard={!placing}
      destroyOnClose
      footer={
        outcome === null ? (
          <>
            <Button onClick={close} disabled={placing}>{TEXT.cancel}</Button>
            <Button type="primary" onClick={submit} loading={placing}>
              {placing ? TEXT.order.placing : TEXT.order.place}
            </Button>
          </>
        ) : (
          <>
            <Button onClick={close} disabled={placing}>{TEXT.close}</Button>
            {outcome.kind === 'refused' && outcome.retry && quantity !== null && (
              <Button type="primary" loading={placing} onClick={() => void send(quantity)}>
                {TEXT.tryAgain}
              </Button>
            )}
          </>
        )
      }
    >
      <div className="dialog-body">
        {outcome === null && (
          <>
            <div className="chosen-item">
              <ItemThumbnail url={product.imageUrl} size={56} />
              <div>
                <Typography.Text strong>{product.name}</Typography.Text>
                <div>{formatPrice(product.price)}</div>
              </div>
            </div>
            <label className="order-quantity" htmlFor="order-quantity">
              {TEXT.order.quantity}
            </label>
            <div className="quantity-stepper">
              <Button aria-label={TEXT.order.decrease} onClick={() => step(-1)} disabled={placing || (quantity ?? 1) <= 1}>
                −
              </Button>
              <Input
                id="order-quantity"
                inputMode="numeric"
                value={quantityText}
                status={showInvalid ? 'error' : undefined}
                disabled={placing}
                onChange={(event) => { setTouched(true); setQuantityText(event.target.value) }}
                onPressEnter={submit}
              />
              <Button aria-label={TEXT.order.increase} onClick={() => step(1)} disabled={placing || (quantity ?? 0) >= MAX_QUANTITY}>
                +
              </Button>
            </div>
            {showInvalid && <div className="field-error" role="alert">{TEXT.order.quantityInvalid}</div>}
          </>
        )}

        {orderNo && (
          <div className="order-number">
            <div>
              <Typography.Text type="secondary">{TEXT.order.orderNumber}</Typography.Text>
              <div className="order-number__value">{orderNo}</div>
            </div>
            <Button type="link" onClick={() => void copy(orderNo)}>
              {copied ? TEXT.order.copied : TEXT.order.copy}
            </Button>
          </div>
        )}

        {outcome?.kind === 'placed' && (
          <div className="order-summary">
            <div>
              <Typography.Text type="secondary">{TEXT.order.quantity}</Typography.Text>
              <div className="order-summary__value">{outcome.order.lines[0]?.quantity}</div>
            </div>
            <div>
              <Typography.Text type="secondary">{TEXT.order.total}</Typography.Text>
              <div className="order-summary__value">{formatPrice(outcome.order.totalAmount)}</div>
            </div>
          </div>
        )}

        {outcome?.kind === 'refused' && <Alert type="error" showIcon message={outcome.message} role="alert" />}
      </div>
    </Modal>
  )
}
