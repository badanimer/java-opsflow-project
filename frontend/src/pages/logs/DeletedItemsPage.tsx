import { useState } from 'react'
import { Button, Input } from 'antd'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { deletedItems, type DeletedItem } from '../../api/logs'
import { PageHeader } from '../../components/PageHeader'
import { CellText, RecordTable, type RecordColumn } from '../../components/RecordTable'
import { useRecords } from '../../hooks/useRecords'
import { formatTime } from '../../lib/days'
import { TEXT } from '../../text'
import type { OwnLogArrival } from './OwnLogPage'

const SEARCH_MAX = 200

/**
 * The Deleted items module of the warehouse log (Figma frames "Deleted items / ..."): the deleted items,
 * newest deletion first, with one box that searches the name or the SKU. View opens the own log of an item
 * in four parts, or two when it never had a product. It is the only place where a deleted item is found.
 */
export function DeletedItemsPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const search = params.get('search') ?? ''
  const [text, setText] = useState(search)
  const list = useRecords(deletedItems, search, search)
  const tooLong = text.trim().length > SEARCH_MAX

  function apply(value: string) {
    if (value.trim().length > SEARCH_MAX) return
    const next = new URLSearchParams()
    if (value.trim()) next.set('search', value.trim())
    setParams(next, { replace: true })
  }

  const columns: RecordColumn<DeletedItem>[] = [
    { key: 'name', title: TEXT.logs.name, grow: true, render: (row) => <CellText text={row.name} /> },
    { key: 'sku', title: TEXT.logs.sku, width: 120, render: (row) => row.sku },
    { key: 'deletedAt', title: TEXT.logs.deletedAt, width: 190, render: (row) => formatTime(row.deletedAt) },
    { key: 'deletedBy', title: TEXT.logs.deletedBy, width: 130, render: (row) => row.deletedBy },
    {
      key: 'view',
      title: '',
      width: 80,
      render: (row) => (
        <Button
          type="link"
          size="small"
          aria-label={`${TEXT.view} ${row.name}`}
          onClick={() => {
            const arrival: OwnLogArrival = { name: row.name, sku: row.sku, inventoryId: row.id, productId: row.productId }
            navigate(`/stock/records/deleted/${encodeURIComponent(row.sku)}`, { state: arrival })
          }}
        >
          {TEXT.view}
        </Button>
      ),
    },
  ]

  return (
    <>
      <Link to="/stock" className="back-link">{TEXT.logs.backToStock}</Link>
      <PageHeader title={TEXT.logs.deletedItems} />
      <section className="panel filter-bar" aria-label="Filters">
        <Input.Search
          placeholder={TEXT.logs.deletedSearch}
          aria-label={TEXT.logs.deletedSearch}
          value={text}
          allowClear
          status={tooLong ? 'error' : undefined}
          onChange={(event) => {
            setText(event.target.value)
            if (event.target.value === '' && search !== '') apply('')
          }}
          onSearch={apply}
          className="filter-bar__search"
        />
        {tooLong && <span className="field-error field-error--inline" role="alert">{TEXT.logs.deletedSearchTooLong}</span>}
      </section>
      <RecordTable
        label={TEXT.logs.deletedItems}
        list={list}
        columns={columns}
        filtered={search !== ''}
        onClear={() => { setText(''); apply('') }}
        empty={{ title: TEXT.logs.deletedEmptyTitle, text: TEXT.logs.deletedEmptyText }}
        noneFound={{ title: TEXT.logs.deletedNoneFoundTitle, text: TEXT.logs.noneFoundText }}
      />
    </>
  )
}
