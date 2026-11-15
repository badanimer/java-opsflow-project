import { useRef, useState } from 'react'
import { Select, Spin } from 'antd'
import { listItems } from '../api/items'
import { productOptions } from '../api/logs'
import { FIRST_PAGE, type PageRequest } from '../api/paging'
import { TEXT } from '../text'

/** A product or an item that was chosen, with the label the picker shows for it. */
export interface Picked {
  id: number
  label: string
}

interface Option {
  id: number
  name: string
  sku: string
}

async function load(kind: 'product' | 'item', search: string, page: PageRequest) {
  const found = kind === 'product' ? await productOptions(search, page) : await listItems(search, page)
  return { options: found.items.map((row) => ({ id: row.id, name: row.name, sku: row.sku })), next: found.nextCursor }
}

/**
 * The searchable picker (The searchable picker in docs/frontend/page-design.md): a dropdown sorted by name
 * that narrows as the person types, and loads the next page when it is scrolled to its end. The product
 * picker offers every product ever listed except those of deleted items (`GET /product-options`), with the
 * SKU beside a name that appears twice; the item picker offers every item that is not deleted, always
 * with its SKU (`GET /items`).
 */
export function RecordPicker({ kind, value, onChange, disabled }: { kind: 'product' | 'item'; value: Picked | null; onChange: (next: Picked | null) => void; disabled?: boolean }) {
  const [options, setOptions] = useState<Option[]>([])
  const [next, setNext] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const search = useRef('')
  const latest = useRef(0)

  async function fetchPage(text: string, page: PageRequest, append: boolean) {
    const call = ++latest.current
    setLoading(true)
    try {
      const found = await load(kind, text, page)
      if (call !== latest.current) return
      setOptions((shown) => (append ? [...shown, ...found.options] : found.options))
      setNext(found.next)
    } catch {
      if (call === latest.current && !append) setOptions([])
    } finally {
      if (call === latest.current) setLoading(false)
    }
  }

  const repeated = new Set(options.map((option) => option.name).filter((name, index, all) => all.indexOf(name) !== index))
  const labelOf = (option: Option) => (kind === 'item' || repeated.has(option.name) ? `${option.name} · ${option.sku}` : option.name)
  const label = kind === 'product' ? TEXT.logs.product : TEXT.logs.item

  return (
    <label className="time-filter__field record-picker">
      <span>{label}</span>
      <Select<number>
        aria-label={label}
        showSearch
        allowClear
        disabled={disabled}
        placeholder={kind === 'product' ? TEXT.logs.selectProduct : TEXT.logs.selectItem}
        filterOption={false}
        value={value?.id}
        labelInValue={false}
        notFoundContent={loading ? <Spin size="small" /> : TEXT.logs.noMatchingItems}
        options={(value && !options.some((option) => option.id === value.id) ? [{ value: value.id, label: value.label }] : []).concat(
          options.map((option) => ({ value: option.id, label: labelOf(option) })),
        )}
        onDropdownVisibleChange={(open) => {
          if (open) void fetchPage(search.current, FIRST_PAGE, false)
        }}
        onSearch={(text) => {
          search.current = text
          void fetchPage(text, FIRST_PAGE, false)
        }}
        onPopupScroll={(event) => {
          const list = event.currentTarget as HTMLElement
          if (next && !loading && list.scrollTop + list.clientHeight >= list.scrollHeight - 8) {
            void fetchPage(search.current, { cursor: next, direction: 'next' }, true)
          }
        }}
        onChange={(id) => {
          const option = options.find((candidate) => candidate.id === id)
          onChange(id === undefined || id === null ? null : { id, label: option ? labelOf(option) : value?.label ?? String(id) })
        }}
        className="time-filter__select record-picker__select"
      />
    </label>
  )
}
