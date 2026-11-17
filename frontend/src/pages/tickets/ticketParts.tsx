import { Tag } from 'antd'
import type { TicketStatus } from '../../api/tickets'
import { TEXT } from '../../text'

const STATUS_COLOR: Record<TicketStatus, string | undefined> = {
  OPEN: 'gold',
  IN_PROGRESS: 'processing',
  RESOLVED: 'success',
  CLOSED: undefined,
  CANCELLED: 'error',
}

/** The status label of a ticket (Ticket screens in docs/frontend/page-design.md). */
export function StatusTag({ status }: { status: TicketStatus }) {
  return <Tag color={STATUS_COLOR[status]}>{TEXT.tickets.status[status]}</Tag>
}
