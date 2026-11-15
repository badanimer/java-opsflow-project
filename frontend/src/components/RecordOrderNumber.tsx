import { useState } from 'react'
import { Button, Typography } from 'antd'
import { TEXT } from '../text'

/** An order number in a list of records, with a link that copies it (Figma component "Record order number / Copy"). */
export function RecordOrderNumber({ orderNo }: { orderNo: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <span className="record-order-number">
      <Typography.Text ellipsis={{ tooltip: orderNo }} className="record-order-number__text">{orderNo}</Typography.Text>
      <Button
        type="link"
        size="small"
        aria-label={`${TEXT.logs.copy} ${orderNo}`}
        onClick={() => {
          navigator.clipboard.writeText(orderNo).then(() => setCopied(true), () => {})
        }}
      >
        {copied ? TEXT.order.copied : TEXT.logs.copy}
      </Button>
    </span>
  )
}
