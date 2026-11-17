import { useState, type ReactNode } from 'react'
import { Button, Card, Empty, Grid, Select, Spin, Table, Typography } from 'antd'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { AREA_ACTIONS, auditOrderRecords, auditRecords, type AuditArea, type AuditFilters, type AuditRecord } from '../../api/audit'
import type { OrderRecord, OrderResult } from '../../api/logs'
import { pageNumberOf } from '../../api/paging'
import { CursorPager } from '../../components/CursorPager'
import { EmptyText, ListFailure } from '../../components/ListStates'
import { PageHeader } from '../../components/PageHeader'
import { TimeFilter, type TimeFilterValue } from '../../components/TimeFilter'
import { UserPicker, type PickedUser } from '../../components/UserPicker'
import { useRecords, type RecordList } from '../../hooks/useRecords'
import { formatTime, QUICK_RANGES, type QuickRange } from '../../lib/days'
import { TEXT } from '../../text'
import { fieldsOf, summaryOf, targetOf } from './auditRows'

type AreaKey = AuditArea | 'ORDER'

/** The address of each area, in the order of the menu. */
const AREAS: { key: AreaKey; slug: string }[] = [
  { key: 'ACCOUNT', slug: 'accounts' },
  { key: 'ROLE', slug: 'roles' },
  { key: 'TICKET', slug: 'tickets' },
  { key: 'STOCK', slug: 'stock' },
  { key: 'ITEM', slug: 'items' },
  { key: 'PRODUCT', slug: 'products' },
  { key: 'ORDER', slug: 'orders' },
]

const RESULTS: OrderResult[] = ['COMPLETED', 'PRODUCT_DELISTED', 'PRODUCT_CHANGED', 'OUT_OF_STOCK', 'INVALID_QUANTITY', 'BUSY']

/**
 * The audit log page for ADMIN and MANAGER (Audit log page in docs/frontend/page-design.md; Figma section
 * D): a menu of seven areas.
 */
export function AuditMenuPage() {
  return (
    <>
      <PageHeader title={TEXT.audit.title} />
      <Typography.Paragraph type="secondary">{TEXT.audit.chooseArea}</Typography.Paragraph>
      <div className="audit-menu">
        {AREAS.map((area) => (
          <Link key={area.key} to={`/audit/${area.slug}`} className="audit-menu__link">
            <Card hoverable size="small">
              <Typography.Text strong>{TEXT.audit.areas[area.key].title} ›</Typography.Text>
              <br />
              <Typography.Text type="secondary">{TEXT.audit.areas[area.key].text}</Typography.Text>
            </Card>
          </Link>
        ))}
      </div>
    </>
  )
}

interface AuditState extends TimeFilterValue {
  action?: string
  userId?: number
  userName?: string
  system?: boolean
  about?: number
  aboutName?: string
}

const DAY = /^\d{4}-\d{2}-\d{2}$/

function readState(params: URLSearchParams): AuditState {
  const user = Number(params.get('user'))
  const about = Number(params.get('about'))
  const quick = params.get('quick') as QuickRange | null
  const from = params.get('from') ?? undefined
  const to = params.get('to') ?? undefined
  return {
    action: params.get('action') ?? undefined,
    userId: Number.isInteger(user) && user > 0 ? user : undefined,
    userName: params.get('userName') ?? undefined,
    system: params.get('system') === 'true',
    about: Number.isInteger(about) && about > 0 ? about : undefined,
    aboutName: params.get('aboutName') ?? undefined,
    from: from && DAY.test(from) ? from : undefined,
    to: to && DAY.test(to) ? to : undefined,
    quick: quick && QUICK_RANGES.includes(quick) ? quick : undefined,
    order: params.get('order') === 'asc' ? 'asc' : 'desc',
  }
}

function writeState(state: AuditState): URLSearchParams {
  const params = new URLSearchParams()
  if (state.action) params.set('action', state.action)
  if (state.system) params.set('system', 'true')
  else if (state.userId !== undefined) {
    params.set('user', String(state.userId))
    if (state.userName) params.set('userName', state.userName)
  }
  if (state.about !== undefined) {
    params.set('about', String(state.about))
    if (state.aboutName) params.set('aboutName', state.aboutName)
  }
  if (state.from) params.set('from', state.from)
  if (state.to) params.set('to', state.to)
  if (state.quick) params.set('quick', state.quick)
  if (state.order === 'asc') params.set('order', 'asc')
  return params
}

/**
 * One area of the audit page: its records, newest first, with three independent filters (the action of
 * the area, or the result for Orders; the user or System; the time) kept in the address. The areas whose
 * rows are about a user, Accounts and Roles and permissions, can also be narrowed to one such user. A row reads by
 * its labels, and View shows the fields before and after.
 */
export function AuditAreaPage() {
  const { area: slug } = useParams()
  const navigate = useNavigate()
  const area = AREAS.find((candidate) => candidate.slug === slug)
  const [params, setParams] = useSearchParams()
  const state = readState(params)
  if (!area) {
    return (
      <Empty description={<EmptyText title={TEXT.notFoundTitle} text={TEXT.notFoundDescription} />}>
        <Button onClick={() => navigate('/audit')}>{TEXT.audit.allAreas}</Button>
      </Empty>
    )
  }
  const update = (next: AuditState) => setParams(writeState(next), { replace: true })
  const orders = area.key === 'ORDER'
  const aboutUsers = area.key === 'ACCOUNT' || area.key === 'ROLE'
  const filtered = Boolean(state.action || state.userId || state.system || (aboutUsers && state.about) || state.from || state.to)
  const actionOptions = orders
    ? RESULTS.map((result) => ({ value: result, label: TEXT.logs.results[result] }))
    : AREA_ACTIONS[area.key as AuditArea].map((action) => ({ value: action, label: TEXT.audit.actions[action] ?? action }))

  return (
    <>
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link to="/audit">{TEXT.audit.title}</Link> / {TEXT.audit.areas[area.key].title}
      </nav>
      <PageHeader title={TEXT.audit.areas[area.key].title} actions={<Button onClick={() => navigate('/audit')}>{TEXT.audit.allAreas}</Button>} />
      <section className="panel filter-bar log-filters" aria-label="Filters">
        <div className="log-filters__row">
          <label className="time-filter__field">
            <span>{orders ? TEXT.audit.result : TEXT.audit.action}</span>
            <Select<string>
              aria-label={orders ? TEXT.audit.result : TEXT.audit.action}
              allowClear
              placeholder={orders ? TEXT.audit.allResults : TEXT.audit.allActions}
              value={state.action}
              options={actionOptions}
              onChange={(action) => update({ ...state, action: action ?? undefined })}
              className="time-filter__select audit-action"
            />
          </label>
          <label className="time-filter__field">
            <span>{TEXT.audit.user}</span>
            <UserPicker
              use="filter"
              label={TEXT.audit.user}
              placeholder={TEXT.audit.allUsers}
              withSystem={!orders}
              value={state.system ? { id: null, username: TEXT.audit.system } : state.userId !== undefined ? { id: state.userId, username: state.userName ?? String(state.userId) } : null}
              onChange={(picked: PickedUser | null) =>
                update({ ...state, system: picked !== null && picked.id === null, userId: picked?.id ?? undefined, userName: picked?.id ? picked.username : undefined })
              }
            />
          </label>
          {aboutUsers && (
            <label className="time-filter__field">
              <span>{TEXT.audit.aboutUser}</span>
              <UserPicker
                use="filter"
                label={TEXT.audit.aboutUser}
                placeholder={TEXT.audit.anyUser}
                value={state.about !== undefined ? { id: state.about, username: state.aboutName ?? String(state.about) } : null}
                onChange={(picked) => update({ ...state, about: picked?.id ?? undefined, aboutName: picked?.username })}
              />
            </label>
          )}
        </div>
        <TimeFilter value={state} onChange={(time) => update({ ...state, ...time })} />
      </section>
      {orders ? (
        <OrderArea state={state} filtered={filtered} onClear={() => update({ order: state.order })} />
      ) : (
        <RecordArea area={area.key as AuditArea} state={state} filtered={filtered} onClear={() => update({ order: state.order })} />
      )}
    </>
  )
}

function filtersOf(state: AuditState): AuditFilters {
  return { action: state.action, userId: state.userId, system: state.system, aboutUserId: state.about, from: state.from, to: state.to, order: state.order }
}

function RecordArea({ area, state, filtered, onClear }: { area: AuditArea; state: AuditState; filtered: boolean; onClear: () => void }) {
  const filters = filtersOf(state)
  const list = useRecords((query: AuditFilters, page) => auditRecords(area, query, page), filters, `${area}:${JSON.stringify(filters)}`)
  const [open, setOpen] = useState<number | null>(null)
  const small = Grid.useBreakpoint().md === false

  return (
    <AreaList list={list} filtered={filtered} onClear={onClear}>
      {(rows) =>
        small ? (
          <ul className="record-stack">
            {rows.map((row) => (
              <li key={row.id} className="record-stack__row">
                <Typography.Text type="secondary">{formatTime(row.at)}</Typography.Text>
                <strong>{TEXT.audit.actions[row.action] ?? row.action}</strong>
                <span>{row.by ?? TEXT.audit.system} · {targetOf(row)}</span>
                <span>{summaryOf(row)}</span>
                <Button size="small" onClick={() => setOpen(open === row.id ? null : row.id)}>{open === row.id ? TEXT.audit.hide : TEXT.audit.view}</Button>
                {open === row.id && <Changes record={row} />}
              </li>
            ))}
          </ul>
        ) : (
          <Table<AuditRecord>
            dataSource={rows}
            rowKey="id"
            pagination={false}
            tableLayout="auto"
            scroll={{ x: 900 }}
            expandable={{
              expandedRowKeys: open === null ? [] : [open],
              showExpandColumn: false,
              expandedRowRender: (row) => <Changes record={row} />,
            }}
            columns={[
              { key: 'at', title: TEXT.audit.time, width: 170, render: (_: unknown, row) => formatTime(row.at) },
              { key: 'action', title: TEXT.audit.action, width: 190, render: (_: unknown, row) => TEXT.audit.actions[row.action] ?? row.action },
              { key: 'user', title: TEXT.audit.user, width: 120, render: (_: unknown, row) => row.by ?? TEXT.audit.system },
              { key: 'target', title: TEXT.audit.target, width: 220, render: (_: unknown, row) => targetOf(row) },
              { key: 'details', title: TEXT.audit.details, className: 'cell-grow', render: (_: unknown, row) => summaryOf(row) },
              {
                key: 'view',
                width: 80,
                render: (_: unknown, row) => (
                  <Button size="small" onClick={() => setOpen(open === row.id ? null : row.id)}>{open === row.id ? TEXT.audit.hide : TEXT.audit.view}</Button>
                ),
              },
            ]}
          />
        )
      }
    </AreaList>
  )
}

/** The fields of a row before and after the change, by their labels, and the address it came from. */
function Changes({ record }: { record: AuditRecord }) {
  const before = fieldsOf(record.before)
  const after = fieldsOf(record.after)
  return (
    <div className="audit-changes">
      <div className="audit-changes__side">
        <Typography.Text strong>{TEXT.audit.before}</Typography.Text>
        {before.length === 0 ? <Typography.Text type="secondary">{TEXT.audit.noValues}</Typography.Text> : before.map((line) => <span key={line}>{line}</span>)}
      </div>
      <div className="audit-changes__side">
        <Typography.Text strong>{TEXT.audit.after}</Typography.Text>
        {after.length === 0 ? <Typography.Text type="secondary">{TEXT.audit.noValues}</Typography.Text> : after.map((line) => <span key={line}>{line}</span>)}
        {record.remark && <span>{TEXT.tickets.reason}: {record.remark}</span>}
      </div>
      {record.ipAddress && <Typography.Text type="secondary" className="audit-changes__ip">{TEXT.audit.ipAddress(record.ipAddress)}</Typography.Text>}
    </div>
  )
}

function OrderArea({ state, filtered, onClear }: { state: AuditState; filtered: boolean; onClear: () => void }) {
  const filters = { ...filtersOf(state), action: undefined, result: state.action as OrderResult | undefined }
  const list = useRecords((query: typeof filters, page) => auditOrderRecords(query, page), filters, JSON.stringify(filters))
  return (
    <AreaList list={list} filtered={filtered} onClear={onClear}>
      {(rows) => (
        <Table<OrderRecord>
          dataSource={rows}
          rowKey="id"
          pagination={false}
          tableLayout="auto"
          scroll={{ x: 900 }}
          columns={[
            { key: 'at', title: TEXT.audit.time, width: 170, render: (_: unknown, row) => formatTime(row.at) },
            { key: 'result', title: TEXT.audit.result, width: 150, render: (_: unknown, row) => TEXT.logs.results[row.result] },
            { key: 'user', title: TEXT.audit.user, width: 120, render: (_: unknown, row) => row.orderedBy },
            { key: 'orderNo', title: TEXT.tickets.orderNumber, width: 330, render: (_: unknown, row) => <span className="order-number">{row.orderNo}</span> },
            {
              key: 'details',
              title: TEXT.audit.details,
              className: 'cell-grow',
              render: (_: unknown, row) => TEXT.audit.orderLine(row.productName, row.quantity, row.stockDeducted, row.result === 'COMPLETED'),
            },
          ]}
        />
      )}
    </AreaList>
  )
}

/** The states of an area list: loading, empty, nothing found, failure, the rows, and the pager. */
function AreaList<T>({ list, filtered, onClear, children }: { list: RecordList<T>; filtered: boolean; onClear: () => void; children: (rows: T[]) => ReactNode }) {
  const { page, loading, error } = list
  let body: ReactNode
  if (error) body = <ListFailure error={error} onRetry={list.reload} onClear={onClear} />
  else if (!page) {
    body = (
      <div className="list-loading" aria-busy="true">
        <Spin />
        <Typography.Text type="secondary">{TEXT.audit.loading}</Typography.Text>
      </div>
    )
  } else if (page.items.length === 0 && !loading) {
    body = filtered ? (
      <Empty description={<EmptyText title={TEXT.audit.noneFoundTitle} text={TEXT.audit.noneFoundText} />}>
        <Button onClick={onClear}>{TEXT.clearFilters}</Button>
      </Empty>
    ) : (
      <Empty description={<EmptyText title={TEXT.audit.emptyTitle} text={TEXT.audit.emptyText} />} />
    )
  } else body = <Spin spinning={loading}>{children(page.items)}</Spin>
  return (
    <section className="panel panel--list" aria-label={TEXT.audit.title}>
      {body}
      {page && !error && !loading && <CursorPager page={page} number={pageNumberOf(list.request)} onPage={list.goTo} />}
    </section>
  )
}
