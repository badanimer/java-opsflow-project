import { useState } from 'react'
import { Alert, Button, Input, Modal, Typography } from 'antd'
import { ApiError } from '../../api/client'
import { assignTicket, cancelTicket, reassignTicket, releaseTicket, resolveTicket, type Ticket } from '../../api/tickets'
import { useAuth } from '../../auth/context'
import { UserPicker, type PickedUser } from '../../components/UserPicker'
import { failureNotice, type Notice } from '../../lib/failure'
import { TEXT } from '../../text'

export type TicketAction = 'assign' | 'reassign' | 'release' | 'resolve' | 'cancel'

const REASON_MAX = 255
const RESOLUTION_MAX = 2000

/**
 * What the dialog shows after a refusal: a notice, and what the person can do next. `form` keeps the
 * dialog usable; `reload` offers Reload ticket; `gone` offers Back to list, because the ticket left what
 * the person may see; `finished` offers only Close.
 */
type After = 'form' | 'reload' | 'gone' | 'finished'

/**
 * The dialog of an action on a ticket (Figma frames "Assign / ...", "Reassign / ...", "Release / ...",
 * "Resolve / ...", "Cancel / ..."): the chosen assignee for an assignment, the required reason, or the
 * resolution with its counter. The dialog is the confirmation. The texts are trimmed and checked before
 * anything is sent; a refusal keeps what was typed and says what to do next.
 */
export function TicketActionDialog({
  action,
  ticket,
  onDone,
  onClose,
  onReload,
  onGone,
}: {
  action: TicketAction
  ticket: Ticket
  onDone: (changed: Ticket, message: string) => void
  onClose: () => void
  onReload: () => void
  onGone: () => void
}) {
  const { refresh } = useAuth()
  const [assignee, setAssignee] = useState<PickedUser | null>(null)
  const [text, setText] = useState('')
  const [running, setRunning] = useState(false)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [after, setAfter] = useState<After>('form')
  const [refusedText, setRefusedText] = useState(false)

  const resolving = action === 'resolve'
  const limit = resolving ? RESOLUTION_MAX : REASON_MAX
  const trimmed = text.trim()
  const tooLong = trimmed.length > limit
  const textError = tooLong ? (resolving ? TEXT.tickets.resolutionTooLong : TEXT.tickets.reasonTooLong) : refusedText ? (resolving ? TEXT.tickets.refusedResolution : TEXT.tickets.refusedReason) : null
  const needsAssignee = action === 'assign' || action === 'reassign'
  const ready = after === 'form' && !running && trimmed.length > 0 && !tooLong && (!needsAssignee || assignee?.id != null)
  const currentAssignee = ticket.assignee?.id ?? null

  const titles: Record<TicketAction, string> = {
    assign: TEXT.tickets.assignTitle(ticket.ticketNo),
    reassign: TEXT.tickets.reassignTitle(ticket.ticketNo),
    release: TEXT.tickets.releaseTitle(ticket.ticketNo),
    resolve: TEXT.tickets.resolveTitle(ticket.ticketNo),
    cancel: TEXT.tickets.cancelTitle(ticket.ticketNo),
  }
  const confirmLabels: Record<TicketAction, string> = {
    assign: TEXT.tickets.assign,
    reassign: TEXT.tickets.reassign,
    release: TEXT.tickets.release,
    resolve: TEXT.tickets.resolve,
    cancel: TEXT.tickets.cancelTicket,
  }

  function send(): Promise<Ticket> {
    switch (action) {
      case 'assign':
        return assignTicket(ticket.id, assignee!.id!, trimmed)
      case 'reassign':
        return reassignTicket(ticket.id, assignee!.id!, currentAssignee!, trimmed)
      case 'release':
        return releaseTicket(ticket.id, currentAssignee!, trimmed)
      case 'resolve':
        return resolveTicket(ticket.id, trimmed)
      case 'cancel':
        return cancelTicket(ticket.id, trimmed)
    }
  }

  async function confirm() {
    if (!ready) return
    setRunning(true)
    setNotice(null)
    try {
      const changed = await send()
      onDone(changed, TEXT.tickets.done[action])
    } catch (error) {
      setRunning(false)
      refused(error)
    }
  }

  function refused(error: unknown) {
    const code = error instanceof ApiError ? error.code : null
    const name = assignee?.username ?? ''
    if (code === 'VALIDATION_FAILED') {
      setRefusedText(true)
    } else if (code === 'NOT_ELIGIBLE') {
      setNotice({ type: 'warning', message: TEXT.tickets.notEligible(name) })
      setAssignee(null)
    } else if (code === 'LIMIT_REACHED') {
      setNotice({ type: 'warning', message: TEXT.tickets.assigneeFull(name) })
      setAssignee(null)
    } else if (code === 'TICKET_CHANGED') {
      setNotice({ type: 'warning', message: TEXT.tickets.changed })
      setAfter('reload')
    } else if ((code === 'NOT_FOUND' || code === 'NOT_ALLOWED') && (action === 'release' || action === 'resolve' || action === 'cancel')) {
      setNotice({ type: 'warning', message: TEXT.tickets.noLonger[action] })
      setAfter(code === 'NOT_FOUND' ? 'gone' : 'finished')
    } else if (code === 'NOT_FOUND') {
      setNotice({ type: 'warning', message: TEXT.tickets.notFoundText })
      setAfter('gone')
    } else if (code === 'FORBIDDEN') {
      refresh().catch(() => {})
      setNotice({ type: 'error', message: TEXT.noLongerAllowed })
      setAfter('finished')
    } else {
      // The change may have been made: the person reloads the ticket before trying again.
      setNotice(failureNotice(error))
      setAfter('reload')
    }
  }

  const footer =
    after === 'finished' ? (
      <Button onClick={onClose}>{TEXT.close}</Button>
    ) : after === 'gone' ? (
      <Button type="primary" onClick={onGone}>{TEXT.tickets.backToList}</Button>
    ) : after === 'reload' ? (
      <>
        <Button onClick={onClose}>{TEXT.close}</Button>
        <Button type="primary" onClick={onReload}>{TEXT.tickets.reloadTicket}</Button>
      </>
    ) : (
      <>
        <Button onClick={onClose} disabled={running}>{action === 'cancel' ? TEXT.tickets.back : TEXT.cancel}</Button>
        <Button type="primary" danger={action === 'cancel'} loading={running} disabled={!ready} onClick={() => void confirm()}>
          {running ? TEXT.tickets.running[action] : confirmLabels[action]}
        </Button>
      </>
    )

  const locked = running || after !== 'form'
  return (
    <Modal open title={titles[action]} onCancel={running ? undefined : onClose} closable={!running} maskClosable={false} keyboard={!running} destroyOnClose footer={footer}>
      <div className="dialog-body">
        <Typography.Text type="secondary">{ticket.title}</Typography.Text>
        {action === 'cancel' && <Typography.Paragraph>{TEXT.tickets.cancelWarning}</Typography.Paragraph>}
        {action === 'reassign' && ticket.assignee && <Typography.Text>{TEXT.tickets.currentAssignee(ticket.assignee.username ?? '')}</Typography.Text>}
        {needsAssignee && (
          <div className="adjust-field">
            <label>{action === 'reassign' ? TEXT.tickets.newAssignee : TEXT.tickets.assignee}</label>
            <UserPicker
              use="assignee"
              label={action === 'reassign' ? TEXT.tickets.newAssignee : TEXT.tickets.assignee}
              placeholder={TEXT.tickets.selectUser}
              value={assignee}
              exclude={action === 'reassign' ? currentAssignee : null}
              disabled={locked}
              onChange={(picked) => { setNotice(null); setAssignee(picked) }}
            />
          </div>
        )}
        <div className="adjust-field">
          <label htmlFor="ticket-action-text">{resolving ? TEXT.tickets.resolution : TEXT.tickets.reasonRequired}</label>
          <Input.TextArea
            id="ticket-action-text"
            rows={resolving ? 5 : 3}
            placeholder={resolving ? TEXT.tickets.resolutionPlaceholder : TEXT.tickets.reasonPlaceholder}
            value={text}
            status={textError ? 'error' : undefined}
            disabled={locked}
            onChange={(event) => { setRefusedText(false); setText(event.target.value) }}
          />
          {resolving && <Typography.Text type="secondary" className="field-counter">{TEXT.tickets.counter(trimmed.length, RESOLUTION_MAX)}</Typography.Text>}
          {textError && <div className="field-error field-error--inline" role="alert">{textError}</div>}
        </div>
        {notice && <Alert type={notice.type} showIcon message={notice.message} role="alert" />}
      </div>
    </Modal>
  )
}
