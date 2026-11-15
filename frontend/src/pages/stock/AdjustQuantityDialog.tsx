import { useState } from 'react'
import { Alert, Button, Input, Modal, Segmented, Typography } from 'antd'
import { ApiError } from '../../api/client'
import { changeStock, type Item, type StockChange } from '../../api/items'
import { useAuth } from '../../auth/context'
import { can, type Permission } from '../../auth/permissions'
import { failureNotice, type Notice } from '../../lib/failure'
import { TEXT } from '../../text'

const MAX_INPUT = 999_999
const REASON_MAX = 255

const CHOICES: { change: StockChange; permission: Permission; label: string }[] = [
  { change: 'STOCK_IN', permission: 'STOCK_IN', label: TEXT.adjust.stockIn },
  { change: 'WRITE_OFF', permission: 'STOCK_WRITE_OFF', label: TEXT.adjust.writeOff },
  { change: 'CORRECTION', permission: 'STOCK_CORRECT', label: TEXT.adjust.correction },
]

const SAVED: Record<StockChange, string> = { STOCK_IN: TEXT.adjust.added, WRITE_OFF: TEXT.adjust.writtenOff, CORRECTION: TEXT.adjust.corrected }

/** The quantity typed, as a whole number from `min` to 999,999, or null. */
function wholeNumber(text: string, min: number): number | null {
  const trimmed = text.trim()
  if (!/^\d+$/.test(trimmed)) return null
  const value = Number(trimmed)
  return value >= min && value <= MAX_INPUT ? value : null
}

/** What the dialog shows below the fields: a notice, and whether Reload item or only Close is offered. */
type State = { notice: Notice | null; mode: 'form' | 'reload' | 'finished' }

/**
 * The quantity change of one item (Figma frames "Stock in / ...", "Write-off / ...", "Correction / ...";
 * Manual stock changes in docs/database/database-design.md): stock in, write-off, or a correction to the
 * number that was counted, each offered only with its permission. The reason is required, the stock
 * before and after is shown, and Save is enabled only for a valid change. The dialog is the confirmation.
 * When the stock changed meanwhile, or after a failure whose result is unknown, the person reloads the
 * item and enters the change again: a stock change has no idempotency key, so it is never sent twice by
 * itself.
 */
export function AdjustQuantityDialog({
  item,
  onSaved,
  onClose,
  reloadItem,
}: {
  item: Item
  onSaved: (message: string) => void
  /** `stale` asks the page to reload its list. */
  onClose: (stale: boolean) => void
  /** Reads the item again with the list of the page; null when it is no longer there. */
  reloadItem: () => Promise<Item | null>
}) {
  const { account, refresh } = useAuth()
  const choices = CHOICES.filter((choice) => can(account, choice.permission))
  const [change, setChange] = useState<StockChange>(choices[0]?.change ?? 'STOCK_IN')
  const [stock, setStock] = useState(item.availableQuantity)
  const [quantityText, setQuantityText] = useState('')
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)
  const [state, setState] = useState<State>({ notice: null, mode: 'form' })
  const [quantityRefusal, setQuantityRefusal] = useState<string | null>(null)
  const [reasonRefusal, setReasonRefusal] = useState<string | null>(null)

  const correcting = change === 'CORRECTION'
  const quantity = wholeNumber(quantityText, correcting ? 0 : 1)
  const quantityError =
    quantityRefusal ??
    (quantityText.trim() === ''
      ? null
      : quantity === null
        ? correcting ? TEXT.adjust.countInvalid : TEXT.adjust.changeInvalid
        : change === 'WRITE_OFF' && quantity > stock
          ? TEXT.adjust.aboveStock
          : correcting && quantity === stock
            ? TEXT.adjust.noChange
            : null)
  const trimmedReason = reason.trim()
  const reasonError = reasonRefusal ?? (reason.length > REASON_MAX || trimmedReason.length > REASON_MAX ? TEXT.adjust.reasonInvalid : null)
  const after =
    quantity === null || quantityError ? null : change === 'STOCK_IN' ? stock + quantity : change === 'WRITE_OFF' ? stock - quantity : quantity
  const ready = after !== null && trimmedReason.length > 0 && reasonError === null && state.mode === 'form' && !saving

  function choose(next: StockChange) {
    setChange(next)
    setQuantityText('')
    setQuantityRefusal(null)
    setState({ notice: null, mode: 'form' })
  }

  async function save() {
    if (!ready || quantity === null) return
    setSaving(true)
    setState({ notice: null, mode: 'form' })
    try {
      await changeStock(item.id, change, quantity, trimmedReason, stock)
      onSaved(SAVED[change])
    } catch (error) {
      setSaving(false)
      refused(error)
    }
  }

  function refused(error: unknown) {
    const code = error instanceof ApiError ? error.code : null
    if (code === 'INVALID_QUANTITY') {
      setQuantityRefusal(change === 'STOCK_IN' ? TEXT.adjust.tooMuch : correcting ? TEXT.adjust.countInvalid : TEXT.adjust.changeInvalid)
    } else if (code === 'VALIDATION_FAILED' && error instanceof ApiError) {
      if (error.errors.some((field) => field.field === 'reason')) setReasonRefusal(TEXT.adjust.reasonInvalid)
      else setQuantityRefusal(correcting ? TEXT.adjust.noChange : TEXT.adjust.changeInvalid)
    } else if (code === 'OUT_OF_STOCK') {
      setState({ notice: { type: 'warning', message: TEXT.adjust.stockDecreased }, mode: 'reload' })
    } else if (code === 'STOCK_CHANGED') {
      setState({ notice: { type: 'warning', message: TEXT.adjust.stockChanged }, mode: 'reload' })
    } else if (code === 'NOT_FOUND') {
      setState({ notice: { type: 'warning', message: TEXT.stock.itemGone }, mode: 'finished' })
    } else if (code === 'FORBIDDEN') {
      refresh().catch(() => {})
      setState({ notice: { type: 'error', message: TEXT.noLongerAllowed }, mode: 'finished' })
    } else {
      // The change may or may not have been made: the person reloads the item before trying again.
      setState({ notice: failureNotice(error), mode: 'reload' })
    }
  }

  async function reload() {
    setSaving(true)
    try {
      const fresh = await reloadItem()
      if (!fresh) {
        setState({ notice: { type: 'warning', message: TEXT.stock.itemGone }, mode: 'finished' })
        return
      }
      setStock(fresh.availableQuantity)
      setQuantityText('')
      setQuantityRefusal(null)
      setState({ notice: { type: 'info', message: correcting ? TEXT.adjust.refreshedCount : TEXT.adjust.refreshedQuantity }, mode: 'form' })
    } catch (error) {
      setState({ notice: failureNotice(error), mode: 'reload' })
    } finally {
      setSaving(false)
    }
  }

  const close = () => onClose(state.mode !== 'form' || state.notice?.type === 'info')
  const locked = saving || state.mode === 'finished'

  return (
    <Modal
      open
      title={TEXT.adjust.title}
      onCancel={saving ? undefined : close}
      closable={!saving}
      maskClosable={false}
      keyboard={!saving}
      destroyOnClose
      footer={
        state.mode === 'finished' ? (
          <Button onClick={close}>{TEXT.close}</Button>
        ) : (
          <>
            <Button onClick={close} disabled={saving}>{TEXT.cancel}</Button>
            {state.mode === 'reload' ? (
              <Button type="primary" loading={saving} onClick={() => void reload()}>{TEXT.adjust.reload}</Button>
            ) : (
              <Button type="primary" loading={saving} disabled={!ready} onClick={() => void save()}>
                {saving ? TEXT.adjust.saving : TEXT.adjust.save}
              </Button>
            )}
          </>
        )
      }
    >
      <div className="dialog-body">
        <Typography.Text type="secondary">{item.name}</Typography.Text>
        <Segmented
          block
          aria-label={TEXT.adjust.title}
          value={change}
          disabled={locked}
          options={choices.map((choice) => ({ value: choice.change, label: choice.label }))}
          onChange={(value) => choose(value as StockChange)}
        />
        <div className="stock-before-after" aria-live="polite">
          <div>
            <Typography.Text type="secondary">{TEXT.adjust.before}</Typography.Text>
            <div className="stock-before-after__value" data-testid="stock-before">{stock}</div>
          </div>
          <div>
            <Typography.Text type="secondary">{TEXT.adjust.after}</Typography.Text>
            <div className="stock-before-after__value" data-testid="stock-after">{after ?? '—'}</div>
          </div>
        </div>
        <div className="adjust-field">
          <label htmlFor="adjust-quantity">{correcting ? TEXT.adjust.counted : TEXT.adjust.quantity}</label>
          <Input
            id="adjust-quantity"
            inputMode="numeric"
            placeholder={TEXT.adjust.quantityPlaceholder}
            value={quantityText}
            status={quantityError ? 'error' : undefined}
            disabled={locked || state.mode === 'reload'}
            onChange={(event) => { setQuantityRefusal(null); setQuantityText(event.target.value) }}
          />
          {quantityError && <div className="field-error field-error--inline" role="alert">{quantityError}</div>}
        </div>
        <div className="adjust-field">
          <label htmlFor="adjust-reason">{TEXT.adjust.reason}</label>
          <Input.TextArea
            id="adjust-reason"
            placeholder={TEXT.adjust.reasonPlaceholder}
            rows={3}
            value={reason}
            status={reasonError ? 'error' : undefined}
            disabled={locked}
            onChange={(event) => { setReasonRefusal(null); setReason(event.target.value) }}
          />
          {reasonError && <div className="field-error field-error--inline" role="alert">{reasonError}</div>}
        </div>
        {state.notice && <Alert type={state.notice.type} showIcon message={state.notice.message} role="alert" />}
      </div>
    </Modal>
  )
}
