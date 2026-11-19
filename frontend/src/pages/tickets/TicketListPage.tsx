import { useState, type ReactNode } from 'react'
import { Alert, Button, Checkbox, Empty, Grid, Input, Modal, Select, Spin, Table, Typography } from 'antd'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { normalizeOrderNo } from '../../api/logs'
import { pageNumberOf } from '../../api/paging'
import { archiveTickets, listTickets, normalizeTicketNo, type ArchiveResult, type TicketRow, type TicketSearch, type TicketSource, type TicketStatus } from '../../api/tickets'
import { useAuth } from '../../auth/context'
import { can } from '../../auth/permissions'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { CursorPager } from '../../components/CursorPager'
import { EmptyText, ListFailure } from '../../components/ListStates'
import { PageHeader } from '../../components/PageHeader'
import { RecordPicker } from '../../components/RecordPicker'
import { TimeFilter } from '../../components/TimeFilter'
import { UserPicker } from '../../components/UserPicker'
import { useRecords } from '../../hooks/useRecords'
import { formatTime } from '../../lib/days'
import { failureNotice, type Notice } from '../../lib/failure'
import { TEXT } from '../../text'
import { StatusTag } from './ticketParts'
import { isSearched, personName, readListState, searchOf, writeListState, type SearchMode, type TicketListState } from './ticketState'

const MAX_ARCHIVE = 100

/**
 * One ticket screen (Ticket screens in docs/frontend/page-design.md; Figma section A "Ticket lists"): the
 * tickets of one status that the user may see, the system tickets first with their own background, newest
 * or oldest first, and paged by key. A user with TICKET_SEARCH gets the search of the screen: the filters,
 * or one ticket number or one order number alone. The pool has Create ticket; the resolved screen lets a
 * user with TICKET_ARCHIVE choose up to 100 tickets and archive them in one batch. The search is kept in
 * the address.
 */
export function TicketListPage({ status }: { status: TicketStatus }) {
  const { account } = useAuth()
  const navigate = useNavigate()
  const small = Grid.useBreakpoint().md === false
  const maySearch = can(account, 'TICKET_SEARCH')
  const seesAll = can(account, 'TICKET_VIEW_ALL')
  const mayArchive = status === 'RESOLVED' && can(account, 'TICKET_ARCHIVE')
  const [params, setParams] = useSearchParams()
  const state = readListState(params)
  const search: TicketSearch = searchOf(state, maySearch)
  const list = useRecords((query: TicketSearch, page) => listTickets(status, query, page), search, `${status}:${JSON.stringify(search)}`)
  const screen = TEXT.tickets.screens[status]

  const [selected, setSelected] = useState<Map<number, string>>(new Map())
  const [limitNotice, setLimitNotice] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [archiving, setArchiving] = useState(false)
  const [archiveNotice, setArchiveNotice] = useState<Notice | null>(null)
  const [result, setResult] = useState<ArchiveResult | null>(null)

  function update(next: TicketListState) {
    setSelected(new Map())
    setParams(writeListState(next), { replace: true })
  }

  function toggle(row: TicketRow, on: boolean) {
    const next = new Map(selected)
    if (on) {
      if (next.size >= MAX_ARCHIVE) {
        setLimitNotice(true)
        return
      }
      next.set(row.id, row.ticketNo)
    } else {
      next.delete(row.id)
    }
    setLimitNotice(false)
    setSelected(next)
  }

  async function archive() {
    setArchiving(true)
    setArchiveNotice(null)
    try {
      const answer = await archiveTickets([...selected.keys()])
      setConfirming(false)
      setSelected(new Map())
      setResult(answer)
      list.reload()
    } catch (error) {
      setArchiveNotice(error instanceof ApiError && error.code === 'VALIDATION_FAILED' ? { type: 'error', message: TEXT.tickets.archiveRefused } : failureNotice(error))
    } finally {
      setArchiving(false)
    }
  }

  const { page, loading, error } = list
  let body: ReactNode
  if (error) {
    body = <ListFailure error={error} onRetry={list.reload} onClear={() => update({ mode: 'filters', order: state.order })} />
  } else if (!page) {
    body = (
      <div className="list-loading" aria-busy="true">
        <Spin />
        <Typography.Text type="secondary">{TEXT.tickets.loading}</Typography.Text>
      </div>
    )
  } else if (page.items.length === 0 && !loading) {
    body = isSearched(search) ? (
      <Empty description={<EmptyText title={TEXT.tickets.noneFoundTitle} text={TEXT.tickets.noneFoundText} />}>
        <Button onClick={() => update({ mode: 'filters', order: state.order })}>{TEXT.clearFilters}</Button>
      </Empty>
    ) : (
      <Empty description={<EmptyText title={TEXT.tickets.emptyTitle} text={TEXT.tickets.emptyText} />} />
    )
  } else if (small) {
    body = (
      <Spin spinning={loading}>
        <ul className="record-stack">
          {page.items.map((row) => (
            <li key={row.id} className={row.source === 'SYSTEM' ? 'record-stack__row ticket-row--system' : 'record-stack__row'}>
              {mayArchive && (
                <Checkbox checked={selected.has(row.id)} onChange={(event) => toggle(row, event.target.checked)} aria-label={TEXT.tickets.selectTicket(row.ticketNo)} />
              )}
              <Link to={`/tickets/${row.id}`} className="ticket-number">{row.ticketNo}</Link>
              <span>{row.title}</span>
              <Typography.Text type="secondary">
                {TEXT.tickets.types[row.type]} · {personName(row.person)} · {formatTime(row.time)}
              </Typography.Text>
              <StatusTag status={row.status} />
            </li>
          ))}
        </ul>
      </Spin>
    )
  } else {
    body = (
      <Table<TicketRow>
        loading={loading}
        dataSource={page.items}
        rowKey="id"
        pagination={false}
        tableLayout="auto"
        scroll={{ x: 900 }}
        className="ticket-list"
        rowClassName={(row) => (row.source === 'SYSTEM' ? 'ticket-row--system' : '')}
        onRow={(row) => ({ onClick: (event) => {
          if ((event.target as HTMLElement).closest('a, button, input, label')) return
          navigate(`/tickets/${row.id}`)
        } })}
        columns={[
          ...(mayArchive
            ? [{
                key: 'select',
                width: 48,
                render: (_: unknown, row: TicketRow) => (
                  <Checkbox checked={selected.has(row.id)} onChange={(event) => toggle(row, event.target.checked)} aria-label={TEXT.tickets.selectTicket(row.ticketNo)} />
                ),
              }]
            : []),
          { key: 'ticket', title: TEXT.tickets.ticket, width: 120, render: (_: unknown, row: TicketRow) => <Link to={`/tickets/${row.id}`} className="ticket-number">{row.ticketNo}</Link> },
          { key: 'type', title: TEXT.tickets.type, width: 170, render: (_: unknown, row: TicketRow) => TEXT.tickets.types[row.type] },
          { key: 'title', title: TEXT.tickets.title, className: 'cell-grow', render: (_: unknown, row: TicketRow) => <span className="cell-text" title={row.title}>{row.title}</span> },
          { key: 'person', title: TEXT.tickets.stepColumns[status].person, width: 130, render: (_: unknown, row: TicketRow) => personName(row.person) },
          { key: 'time', title: TEXT.tickets.stepColumns[status].time, width: 170, render: (_: unknown, row: TicketRow) => formatTime(row.time) },
          { key: 'status', title: TEXT.tickets.statusColumn, width: 170, render: (_: unknown, row: TicketRow) => <StatusTag status={row.status} /> },
        ]}
      />
    )
  }

  return (
    <>
      <PageHeader
        title={screen.title}
        actions={
          status === 'OPEN' && can(account, 'TICKET_CREATE') ? (
            <Button type="primary" onClick={() => navigate('/tickets/new')}>{TEXT.tickets.createTicket}</Button>
          ) : mayArchive ? (
            <Button type="primary" disabled={selected.size === 0} onClick={() => { setArchiveNotice(null); setConfirming(true) }}>
              {TEXT.tickets.archiveSelected(selected.size)}
            </Button>
          ) : undefined
        }
      />
      <Typography.Paragraph type="secondary" className="page-subtitle">{seesAll ? screen.all : screen.own}</Typography.Paragraph>
      {maySearch ? (
        <TicketSearchBar status={status} state={state} onChange={update} />
      ) : (
        <section className="panel filter-bar" aria-label="Filters">
          <label className="time-filter__field">
            <span>{TEXT.tickets.sortOrder}</span>
            <Select<'desc' | 'asc'>
              aria-label={TEXT.tickets.sortOrder}
              value={state.order}
              options={[{ value: 'desc', label: TEXT.tickets.newestFirst }, { value: 'asc', label: TEXT.tickets.oldestFirst }]}
              onChange={(order) => update({ ...state, order })}
              className="time-filter__select"
            />
          </label>
        </section>
      )}
      {limitNotice && <Alert type="warning" showIcon message={TEXT.tickets.selectionLimit} role="alert" className="page-alert" />}
      <section className="panel panel--list" aria-label={TEXT.tickets.listLabel}>
        {body}
        {page && !error && !loading && <CursorPager page={page} number={pageNumberOf(list.request)} onPage={list.goTo} />}
      </section>

      <ConfirmDialog
        open={confirming}
        title={TEXT.tickets.archiveManyTitle(selected.size)}
        confirmLabel={TEXT.tickets.archiveTickets}
        runningLabel={TEXT.tickets.archiving}
        cancelLabel={TEXT.cancel}
        running={archiving}
        notice={archiveNotice}
        onConfirm={() => void archive()}
        onCancel={() => setConfirming(false)}
      >
        <Typography.Paragraph>{TEXT.tickets.archiveManyText}</Typography.Paragraph>
      </ConfirmDialog>
      <Modal
        open={result !== null}
        title={result ? TEXT.tickets.archiveResult(result.archived.length, result.skipped.length) : ''}
        onCancel={() => setResult(null)}
        footer={<Button onClick={() => setResult(null)}>{TEXT.close}</Button>}
      >
        {result && (
          <div className="dialog-body">
            {result.archived.length > 0 && <Typography.Text>{TEXT.tickets.archivedList(result.archived.join(' · '))}</Typography.Text>}
            {result.skipped.map((skip) => (
              <Typography.Text key={skip.id} type="secondary">
                {TEXT.tickets.skippedOne(skip.ticketNo, skip.status ? TEXT.tickets.status[skip.status] : null)}
              </Typography.Text>
            ))}
          </div>
        )}
      </Modal>
    </>
  )
}

/**
 * The search of a screen for ADMIN, MANAGER, and VIEWER (Ticket search in the database design): the kind,
 * the creator, the assignee, one warehouse item, and the creation days, which combine; or one ticket number or one order
 * number alone, which disables the filters. Each screen searches only its own status.
 */
function TicketSearchBar({ status, state, onChange }: { status: TicketStatus; state: TicketListState; onChange: (next: TicketListState) => void }) {
  const byNumber = state.mode !== 'filters'
  const [text, setText] = useState(state.mode === 'ticketNo' ? state.ticketNo ?? '' : state.orderNo ?? '')
  const [invalid, setInvalid] = useState(false)

  function changeMode(mode: SearchMode) {
    setText('')
    setInvalid(false)
    onChange({ mode, order: state.order })
  }

  function find() {
    const number = state.mode === 'ticketNo' ? normalizeTicketNo(text) : normalizeOrderNo(text)
    if (!number) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    setText(number)
    onChange(state.mode === 'ticketNo' ? { mode: 'ticketNo', ticketNo: number, order: state.order } : { mode: 'orderNo', orderNo: number, order: state.order })
  }

  return (
    <section className="panel filter-bar ticket-search" aria-label="Filters">
      <div className="ticket-search__row">
        <label className="time-filter__field">
          <span>{TEXT.tickets.searchBy}</span>
          <Select<SearchMode>
            aria-label={TEXT.tickets.searchBy}
            value={state.mode}
            options={(['filters', 'ticketNo', 'orderNo'] as const).map((mode) => ({ value: mode, label: TEXT.tickets.searchModes[mode] }))}
            onChange={changeMode}
            className="time-filter__select"
          />
        </label>
        {byNumber && (
          <div className="time-filter__field ticket-search__number">
            <label htmlFor="ticket-number-search">{state.mode === 'ticketNo' ? TEXT.tickets.ticketNumber : TEXT.tickets.orderNumber}</label>
            <div className="order-number-search__row">
              <Input
                id="ticket-number-search"
                placeholder={state.mode === 'ticketNo' ? TEXT.tickets.ticketNumberPlaceholder : TEXT.tickets.orderNumberPlaceholder}
                value={text}
                maxLength={64}
                status={invalid ? 'error' : undefined}
                onChange={(event) => { setInvalid(false); setText(event.target.value) }}
                onPressEnter={find}
              />
              <Button type="primary" disabled={text.trim() === ''} onClick={find}>{TEXT.tickets.find}</Button>
            </div>
            {invalid && (
              <span className="field-error field-error--inline" role="alert">
                {state.mode === 'ticketNo' ? TEXT.tickets.ticketNumberInvalid : TEXT.tickets.orderNumberInvalid}
              </span>
            )}
          </div>
        )}
      </div>
      <div className="ticket-search__row">
        <label className="time-filter__field">
          <span>{TEXT.tickets.kind}</span>
          <Select<TicketSource | 'ALL'>
            aria-label={TEXT.tickets.kind}
            value={state.source ?? 'ALL'}
            disabled={byNumber}
            options={[
              { value: 'ALL', label: TEXT.tickets.allKinds },
              { value: 'SYSTEM', label: TEXT.tickets.sources.SYSTEM },
              { value: 'MANUAL', label: TEXT.tickets.sources.MANUAL },
            ]}
            onChange={(source) => onChange({ ...state, source: source === 'ALL' ? undefined : source })}
            className="time-filter__select"
          />
        </label>
        <label className="time-filter__field">
          <span>{TEXT.tickets.creator}</span>
          <UserPicker
            use="filter"
            label={TEXT.tickets.creator}
            placeholder={TEXT.tickets.anyCreator}
            disabled={byNumber}
            value={state.creator !== undefined ? { id: state.creator, username: state.creatorName ?? String(state.creator) } : null}
            onChange={(picked) => onChange({ ...state, creator: picked?.id ?? undefined, creatorName: picked?.username })}
          />
        </label>
        {status !== 'OPEN' && (
          <label className="time-filter__field">
            <span>{TEXT.tickets.assignee}</span>
            <UserPicker
              use="filter"
              label={TEXT.tickets.assignee}
              placeholder={TEXT.tickets.anyAssignee}
              disabled={byNumber}
              value={state.assignee !== undefined ? { id: state.assignee, username: state.assigneeName ?? String(state.assignee) } : null}
              onChange={(picked) => onChange({ ...state, assignee: picked?.id ?? undefined, assigneeName: picked?.username })}
            />
          </label>
        )}
        <RecordPicker
          kind="item"
          disabled={byNumber}
          value={state.item !== undefined ? { id: state.item, label: state.itemName ?? String(state.item) } : null}
          onChange={(picked) => onChange({ ...state, item: picked?.id, itemName: picked?.label })}
        />
      </div>
      <TimeFilter value={state} disabled={byNumber} onChange={(time) => onChange({ ...state, ...time })} />
    </section>
  )
}
