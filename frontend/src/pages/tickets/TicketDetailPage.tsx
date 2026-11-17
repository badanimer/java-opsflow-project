import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Alert, Button, Descriptions, Result, Skeleton, Table, Typography } from 'antd'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { archiveTicket, getTicket, takeTicket, type HistoryEntry, type Ticket, type TicketRef } from '../../api/tickets'
import { useAuth } from '../../auth/context'
import { can } from '../../auth/permissions'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { formatTime } from '../../lib/days'
import { failureNotice, type Notice } from '../../lib/failure'
import { TEXT } from '../../text'
import { TicketActionDialog, type TicketAction } from './TicketActionDialog'
import { StatusTag } from './ticketParts'
import { SCREEN_PATH, personName } from './ticketState'

type Load = { state: 'loading' } | { state: 'loaded'; ticket: Ticket } | { state: 'notFound' } | { state: 'failed'; error: unknown }

/** A notice under the buttons, and whether it offers Reload ticket. */
type Banner = { notice: Notice; reload?: boolean } | null

/**
 * One ticket (Ticket detail in docs/frontend/page-design.md; Figma section B): the description, the linked
 * items, the order, the follow-up links, the assignment history, the resolution, and the archive or the
 * cancellation, with a message built from the cause when the system cancelled it. The buttons follow the
 * status and the permissions, and an operator's own: Take and Assign in the pool; Resolve, Release,
 * Reassign, and Cancel in progress; Archive when resolved. A ticket that does not exist and one the user
 * may not see give the same page.
 */
export function TicketDetailPage() {
  const { id } = useParams()
  const ticketId = Number(id)
  const navigate = useNavigate()
  const { account, refresh } = useAuth()
  const [load, setLoad] = useState<Load>({ state: 'loading' })
  const [banner, setBanner] = useState<Banner>(null)
  const [dialog, setDialog] = useState<TicketAction | null>(null)
  const [taking, setTaking] = useState(false)
  const [archiving, setArchiving] = useState<'closed' | 'open' | 'running'>('closed')
  const [archiveNotice, setArchiveNotice] = useState<Notice | null>(null)

  const read = useCallback(() => {
    if (!Number.isInteger(ticketId) || ticketId < 1) {
      setLoad({ state: 'notFound' })
      return
    }
    setLoad((current) => (current.state === 'loaded' && current.ticket.id === ticketId ? current : { state: 'loading' }))
    getTicket(ticketId)
      .then((ticket) => setLoad({ state: 'loaded', ticket }))
      .catch((error: unknown) => setLoad(error instanceof ApiError && error.code === 'NOT_FOUND' ? { state: 'notFound' } : { state: 'failed', error }))
  }, [ticketId])

  useEffect(() => {
    read()
  }, [read])

  if (load.state === 'loading') {
    return (
      <section className="panel" aria-busy="true" aria-label={TEXT.tickets.loadingTicket}>
        <Skeleton active />
      </section>
    )
  }
  if (load.state === 'notFound') {
    return (
      <Result
        status="404"
        title={TEXT.tickets.notFoundTitle}
        subTitle={TEXT.tickets.notFoundText}
        extra={<Button type="primary" onClick={() => navigate(SCREEN_PATH.OPEN)}>{TEXT.tickets.backTo(TEXT.nav.pool)}</Button>}
      />
    )
  }
  if (load.state === 'failed') {
    const failure = failureNotice(load.error)
    return (
      <div className="list-failure">
        <Alert type={failure.type} showIcon message={failure.message} role="alert" />
        <Button onClick={read}>{TEXT.tryAgain}</Button>
      </div>
    )
  }

  const ticket = load.ticket
  const me = account?.id ?? -1
  const mine = ticket.assignee?.id === me
  const handlesAny = can(account, 'TICKET_HANDLE_ANY')
  const screen = TEXT.tickets.screens[ticket.status].title
  const buttons: ReactNode[] = []

  if (ticket.status === 'OPEN') {
    if (can(account, 'TICKET_TAKE')) {
      buttons.push(<Button key="take" type="primary" loading={taking} onClick={() => void take()}>{taking ? TEXT.tickets.taking : TEXT.tickets.take}</Button>)
    }
    if (can(account, 'TICKET_ASSIGN')) buttons.push(<Button key="assign" onClick={() => open('assign')}>{TEXT.tickets.assign}</Button>)
    if (can(account, 'TICKET_CANCEL') && (handlesAny || ticket.createdBy.id === me)) {
      buttons.push(<Button key="cancel" danger onClick={() => open('cancel')}>{TEXT.tickets.cancel}</Button>)
    }
  } else if (ticket.status === 'IN_PROGRESS') {
    if (can(account, 'TICKET_RESOLVE') && (mine || handlesAny)) buttons.push(<Button key="resolve" type="primary" onClick={() => open('resolve')}>{TEXT.tickets.resolve}</Button>)
    if (can(account, 'TICKET_RELEASE') && (mine || handlesAny)) buttons.push(<Button key="release" onClick={() => open('release')}>{TEXT.tickets.release}</Button>)
    if (can(account, 'TICKET_ASSIGN')) buttons.push(<Button key="reassign" onClick={() => open('reassign')}>{TEXT.tickets.reassign}</Button>)
    if (can(account, 'TICKET_CANCEL') && (mine || handlesAny)) buttons.push(<Button key="cancel" danger onClick={() => open('cancel')}>{TEXT.tickets.cancel}</Button>)
  } else if (ticket.status === 'RESOLVED' && can(account, 'TICKET_ARCHIVE')) {
    buttons.push(<Button key="archive" type="primary" onClick={() => { setArchiveNotice(null); setArchiving('open') }}>{TEXT.tickets.archiveOne}</Button>)
  }

  function open(action: TicketAction) {
    setBanner(null)
    setDialog(action)
  }

  async function take() {
    setTaking(true)
    setBanner(null)
    try {
      const changed = await takeTicket(ticket.id)
      setLoad({ state: 'loaded', ticket: changed })
      setBanner({ notice: { type: 'success', message: TEXT.tickets.taken } })
    } catch (error) {
      const code = error instanceof ApiError ? error.code : null
      if (code === 'LIMIT_REACHED') setBanner({ notice: { type: 'warning', message: TEXT.tickets.youAreFull } })
      else if (code === 'TICKET_CHANGED') setBanner({ notice: { type: 'warning', message: TEXT.tickets.notInPool }, reload: true })
      else if (code === 'NOT_FOUND') setLoad({ state: 'notFound' })
      else if (code === 'FORBIDDEN') {
        refresh().catch(() => {})
        setBanner({ notice: { type: 'error', message: TEXT.noLongerAllowed } })
      } else setBanner({ notice: failureNotice(error), reload: true })
    } finally {
      setTaking(false)
    }
  }

  async function archive() {
    setArchiving('running')
    setArchiveNotice(null)
    try {
      const changed = await archiveTicket(ticket.id)
      setArchiving('closed')
      setLoad({ state: 'loaded', ticket: changed })
      setBanner({ notice: { type: 'success', message: TEXT.tickets.done.archive } })
    } catch (error) {
      const code = error instanceof ApiError ? error.code : null
      setArchiving('closed')
      if (code === 'TICKET_CHANGED') setBanner({ notice: { type: 'warning', message: TEXT.tickets.changed }, reload: true })
      else if (code === 'NOT_FOUND') setLoad({ state: 'notFound' })
      else setBanner({ notice: failureNotice(error), reload: true })
    }
  }

  function reloadAfterDialog() {
    setDialog(null)
    setBanner(null)
    read()
  }

  const cause = ticket.cancelCause
  return (
    <>
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link to={SCREEN_PATH[ticket.status]}>{TEXT.nav.tickets} / {screen}</Link> / {ticket.ticketNo}
      </nav>
      <Typography.Title level={1} className="page-header__title">{ticket.ticketNo}</Typography.Title>
      <Typography.Paragraph type="secondary">{TEXT.tickets.details}</Typography.Paragraph>
      <div className="ticket-actions">
        <StatusTag status={ticket.status} />
        {buttons}
      </div>
      {banner && (
        <Alert
          type={banner.notice.type}
          showIcon
          message={banner.notice.message}
          role="alert"
          className="page-alert"
          action={banner.reload ? <Button size="small" onClick={() => { setBanner(null); read() }}>{TEXT.tickets.reloadTicket}</Button> : undefined}
        />
      )}
      {cause && (
        <Alert type="warning" showIcon className="page-alert" message={TEXT.tickets.causes[cause.kind](cause.name ?? TEXT.tickets.linkedItems)} />
      )}
      <div className="ticket-detail">
        <section className="panel ticket-detail__main" aria-label={ticket.title}>
          <Typography.Title level={2} className="ticket-detail__title">{ticket.title}</Typography.Title>
          <Typography.Text type="secondary">{TEXT.tickets.types[ticket.type]} · {TEXT.tickets.sources[ticket.source]}</Typography.Text>
          <Typography.Title level={3} className="ticket-detail__heading">{TEXT.tickets.description}</Typography.Title>
          <Typography.Paragraph className="ticket-detail__text">{ticket.description}</Typography.Paragraph>
          {ticket.items.length > 0 && (
            <>
              <Typography.Title level={3} className="ticket-detail__heading">{TEXT.tickets.linkedItems}</Typography.Title>
              <ul className="ticket-items">
                {ticket.items.map((item) => (
                  <li key={item.inventoryId}>
                    {item.name} <Typography.Text type="secondary">{item.sku}</Typography.Text>
                    {item.deleted && <Typography.Text type="danger"> · {TEXT.tickets.deletedItem}</Typography.Text>}
                  </li>
                ))}
              </ul>
            </>
          )}
          {ticket.orderNo && (
            <>
              <Typography.Title level={3} className="ticket-detail__heading">{TEXT.tickets.orderNumber}</Typography.Title>
              <Typography.Text copyable className="order-number">{ticket.orderNo}</Typography.Text>
            </>
          )}
          {ticket.resolution && (
            <>
              <Typography.Title level={3} className="ticket-detail__heading">{TEXT.tickets.resolution}</Typography.Title>
              <Typography.Paragraph className="ticket-detail__text">{ticket.resolution}</Typography.Paragraph>
              {ticket.resolvedAt && <Typography.Text type="secondary">{TEXT.tickets.resolvedBy(personName(ticket.resolvedBy), formatTime(ticket.resolvedAt))}</Typography.Text>}
            </>
          )}
          {ticket.closedAt && (
            <>
              <Typography.Title level={3} className="ticket-detail__heading">{TEXT.tickets.archive}</Typography.Title>
              <Typography.Text type="secondary">{TEXT.tickets.archivedBy(personName(ticket.closedBy), formatTime(ticket.closedAt))}</Typography.Text>
            </>
          )}
          {ticket.cancelledAt && (
            <>
              <Typography.Title level={3} className="ticket-detail__heading">{TEXT.tickets.cancellation}</Typography.Title>
              {!cause && ticket.cancelReason && <Typography.Paragraph className="ticket-detail__text">{ticket.cancelReason}</Typography.Paragraph>}
              <Typography.Text type="secondary">{TEXT.tickets.cancelledBy(personName(ticket.cancelledBy), formatTime(ticket.cancelledAt))}</Typography.Text>
            </>
          )}
        </section>
        <section className="panel ticket-detail__side" aria-label={TEXT.tickets.information}>
          <Typography.Title level={2} className="ticket-detail__title">{TEXT.tickets.information}</Typography.Title>
          <Descriptions column={1} size="small">
            <Descriptions.Item label={TEXT.tickets.type}>{TEXT.tickets.types[ticket.type]}</Descriptions.Item>
            <Descriptions.Item label={TEXT.tickets.source}>{TEXT.tickets.sources[ticket.source]}</Descriptions.Item>
            <Descriptions.Item label={TEXT.tickets.createdBy}>{personName(ticket.createdBy)}</Descriptions.Item>
            <Descriptions.Item label={TEXT.tickets.created}>{formatTime(ticket.createdAt)}</Descriptions.Item>
            <Descriptions.Item label={TEXT.tickets.assignee}>{ticket.assignee ? personName(ticket.assignee) : TEXT.tickets.unassigned}</Descriptions.Item>
            <Descriptions.Item label={TEXT.tickets.follows}>{ticket.follows ? <TicketLink ticket={ticket.follows} /> : TEXT.tickets.none}</Descriptions.Item>
            <Descriptions.Item label={TEXT.tickets.followedBy}>
              {ticket.followUps.length > 0 ? (
                <span className="ticket-links">{ticket.followUps.map((followUp) => <TicketLink key={followUp.ticketNo} ticket={followUp} />)}</span>
              ) : TEXT.tickets.none}
            </Descriptions.Item>
          </Descriptions>
        </section>
      </div>
      <section className="panel" aria-label={TEXT.tickets.history}>
        <Typography.Title level={2} className="ticket-detail__title">{TEXT.tickets.history}</Typography.Title>
        {ticket.history.length === 0 ? (
          <Typography.Text type="secondary">{TEXT.tickets.noHistory}</Typography.Text>
        ) : (
          <Table<HistoryEntry & { key: number }>
            size="small"
            pagination={false}
            scroll={{ x: 700 }}
            dataSource={ticket.history.map((entry, index) => ({ ...entry, key: index }))}
            columns={[
              { key: 'from', title: TEXT.tickets.from, render: (_: unknown, entry) => (entry.from ? personName(entry.from) : TEXT.tickets.unassigned) },
              { key: 'to', title: TEXT.tickets.to, render: (_: unknown, entry) => (entry.to ? personName(entry.to) : TEXT.tickets.unassigned) },
              { key: 'by', title: TEXT.tickets.changedBy, render: (_: unknown, entry) => personName(entry.by) },
              { key: 'reason', title: TEXT.tickets.reason, render: (_: unknown, entry) => entry.reason ?? '—' },
              { key: 'at', title: TEXT.tickets.time, render: (_: unknown, entry) => formatTime(entry.at) },
            ]}
          />
        )}
      </section>

      {dialog && (
        <TicketActionDialog
          action={dialog}
          ticket={ticket}
          onClose={() => setDialog(null)}
          onReload={reloadAfterDialog}
          onGone={() => navigate(SCREEN_PATH[ticket.status])}
          onDone={(changed, message) => {
            setDialog(null)
            setLoad({ state: 'loaded', ticket: changed })
            setBanner({ notice: { type: 'success', message } })
          }}
        />
      )}
      <ConfirmDialog
        open={archiving !== 'closed'}
        title={TEXT.tickets.archiveOneTitle}
        confirmLabel={TEXT.tickets.archiveTicket}
        runningLabel={TEXT.tickets.archiving}
        cancelLabel={TEXT.cancel}
        running={archiving === 'running'}
        notice={archiveNotice}
        onConfirm={() => void archive()}
        onCancel={() => setArchiving('closed')}
      >
        <Typography.Paragraph>{TEXT.tickets.archiveOneText(ticket.ticketNo)}</Typography.Paragraph>
      </ConfirmDialog>
    </>
  )
}

/** A linked ticket: a link when the user may see it, the number alone when not. */
function TicketLink({ ticket }: { ticket: TicketRef }) {
  return ticket.id !== null ? <Link to={`/tickets/${ticket.id}`} className="ticket-number">{ticket.ticketNo}</Link> : <span className="ticket-number">{ticket.ticketNo}</span>
}
