import { useState } from 'react'
import { Button, Input, Typography } from 'antd'
import { normalizeOrderNo } from '../api/logs'
import { TEXT } from '../text'

/**
 * The box for one whole order number on the order records and the stock movements (Logs in
 * docs/frontend/page-design.md). It is used alone: while a number is applied, the page disables the other
 * filters, and Clear order number gives them back. A number that is not `ORD-` and 32 letters or digits is
 * refused in the page; one that is found nowhere gives an empty list.
 */
export function OrderNumberSearch({ applied, onApply }: { applied: string | undefined; onApply: (orderNo: string | undefined) => void }) {
  const [text, setText] = useState(applied ?? '')
  const [invalid, setInvalid] = useState(false)

  function find() {
    const number = normalizeOrderNo(text)
    if (!number) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    setText(number)
    onApply(number)
  }

  function clear() {
    setText('')
    setInvalid(false)
    onApply(undefined)
  }

  return (
    <div className="order-number-search">
      <label className="time-filter__field" htmlFor="order-number-search">
        <span>{TEXT.logs.orderNumber}</span>
      </label>
      <div className="order-number-search__row">
        <Input
          id="order-number-search"
          placeholder={TEXT.logs.orderNumberPlaceholder}
          value={text}
          status={invalid ? 'error' : undefined}
          maxLength={64}
          onChange={(event) => { setInvalid(false); setText(event.target.value) }}
          onPressEnter={find}
          className="order-number-search__input"
        />
        <Button type="primary" disabled={text.trim() === ''} onClick={find}>{TEXT.logs.findOrder}</Button>
        {(applied || text) && <Button type="link" onClick={clear}>{TEXT.logs.clearOrderNumber}</Button>}
      </div>
      {invalid ? (
        <span className="field-error field-error--inline" role="alert">{TEXT.logs.orderNumberInvalid}</span>
      ) : (
        <Typography.Text type="secondary">{TEXT.logs.orderNumberAlone}</Typography.Text>
      )}
    </div>
  )
}
