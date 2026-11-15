import type { ReactNode } from 'react'
import { Alert, Button, Empty, Grid, Spin, Table, Typography } from 'antd'
import { ApiError } from '../api/client'
import type { RecordList } from '../hooks/useRecords'
import { failureNotice } from '../lib/failure'
import { TEXT } from '../text'
import { pageNumberOf } from '../api/paging'
import { CursorPager } from './CursorPager'
import { EmptyText } from './ListStates'

export interface RecordColumn<T> {
  key: string
  title: string
  render: (row: T) => ReactNode
  width?: number
  /** A column of text that takes the room left over (a name, a reason). */
  grow?: boolean
}

/**
 * A text of a cell, cut after two lines with the whole text on hover. The cut is made by CSS
 * (`line-clamp`), which measures nothing, so a long list stays fast.
 */
export function CellText({ text }: { text: string }) {
  return (
    <span className="cell-text" title={text}>
      {text}
    </span>
  )
}

/**
 * A list of records of a log or of the Deleted items module (Figma frames "... / First page", "Loading",
 * "Empty", "Filtered to nothing", "Load failed ...", "Filter refused", "Forbidden", "Small screen"): a table
 * on a wide screen and stacked rows on a small one, Previous and Next, and every state of the list.
 */
export function RecordTable<T extends { id: number }>({
  label,
  list,
  columns,
  filtered,
  onClear,
  empty = { title: TEXT.logs.emptyTitle, text: TEXT.logs.emptyText },
  noneFound = { title: TEXT.logs.noneFoundTitle, text: TEXT.logs.noneFoundText },
}: {
  label: string
  list: RecordList<T>
  columns: RecordColumn<T>[]
  /** Whether a filter narrows the list, so that an empty list means nothing was found. */
  filtered: boolean
  onClear: () => void
  empty?: { title: string; text: string }
  noneFound?: { title: string; text: string }
}) {
  const small = Grid.useBreakpoint().md === false
  const { page, loading, error } = list

  let body: ReactNode
  if (error) {
    body = <RecordFailure error={error} onRetry={list.reload} onClear={onClear} />
  } else if (!page) {
    body = (
      <div className="list-loading" aria-busy="true">
        <Spin />
        <Typography.Text type="secondary">{TEXT.logs.loading}</Typography.Text>
      </div>
    )
  } else if (page.items.length === 0 && !loading) {
    body = filtered ? (
      <Empty description={<EmptyText title={noneFound.title} text={noneFound.text} />}>
        <Button onClick={onClear}>{TEXT.clearFilters}</Button>
      </Empty>
    ) : (
      <Empty description={<EmptyText title={empty.title} text={empty.text} />} />
    )
  } else if (small) {
    body = (
      <Spin spinning={loading}>
        <ul className="record-stack">
          {page.items.map((row) => (
            <li key={row.id} className="record-stack__row">
              {columns.map((column) => (
                <div key={column.key} className="record-stack__pair">
                  <Typography.Text type="secondary">{column.title}</Typography.Text>
                  <span>{column.render(row)}</span>
                </div>
              ))}
            </li>
          ))}
        </ul>
      </Spin>
    )
  } else {
    body = (
      <Table<T>
        loading={loading}
        dataSource={page.items}
        rowKey="id"
        pagination={false}
        tableLayout="auto"
        scroll={{ x: 900 }}
        columns={columns.map((column) => ({
          key: column.key,
          title: column.title,
          width: column.width,
          className: column.grow ? 'cell-grow' : undefined,
          render: (_: unknown, row: T) => column.render(row),
        }))}
      />
    )
  }

  return (
    <section className="panel panel--list" aria-label={label}>
      {body}
      {page && !error && !loading && <CursorPager page={page} number={pageNumberOf(list.request)} onPage={list.goTo} />}
    </section>
  )
}

/** A list that could not be loaded: refused filters, no permission, no answer, or a fault with its reference. */
function RecordFailure({ error, onRetry, onClear }: { error: unknown; onRetry: () => void; onClear: () => void }) {
  if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
    return (
      <div className="list-failure">
        <Alert type="error" showIcon message={TEXT.filtersRefused} role="alert" />
        <Button onClick={onClear}>{TEXT.clearFilters}</Button>
      </div>
    )
  }
  if (error instanceof ApiError && error.code === 'FORBIDDEN') {
    return (
      <div className="list-failure">
        <Alert type="error" showIcon message={TEXT.logs.notAllowed} role="alert" />
      </div>
    )
  }
  const failure = failureNotice(error)
  return (
    <div className="list-failure">
      <Alert type={failure.type} showIcon message={failure.message} role="alert" />
      <Button onClick={onRetry}>{TEXT.tryAgain}</Button>
    </div>
  )
}
