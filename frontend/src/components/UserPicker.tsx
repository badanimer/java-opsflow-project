import { useRef, useState } from 'react'
import { Select, Spin } from 'antd'
import { FIRST_PAGE, type PageRequest } from '../api/paging'
import { userOptions, type UserOption } from '../api/tickets'
import { TEXT } from '../text'

/** A user that was chosen. `id` is null for the choice System of the audit page. */
export interface PickedUser {
  id: number | null
  username: string
}

const SYSTEM_VALUE = -1

/**
 * The user picker (The user picker in docs/database/database-design.md): usernames only, narrowed as the
 * person types, the next page loaded at the end of the list. `filter` offers every user, disabled and
 * deleted ones included; `assignee` offers only the people the caller may give a ticket to. `withSystem`
 * adds the choice System, which the audit page uses for the actions of the system. `exclude` leaves one
 * user out, such as the current assignee of a reassignment.
 */
export function UserPicker({
  use,
  label,
  placeholder,
  value,
  onChange,
  disabled,
  withSystem,
  exclude,
  status,
}: {
  use: 'filter' | 'assignee'
  label: string
  placeholder: string
  value: PickedUser | null
  onChange: (next: PickedUser | null) => void
  disabled?: boolean
  withSystem?: boolean
  exclude?: number | null
  status?: 'error'
}) {
  const [options, setOptions] = useState<UserOption[]>([])
  const [next, setNext] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const search = useRef('')
  const latest = useRef(0)

  async function fetchPage(text: string, page: PageRequest, append: boolean) {
    const call = ++latest.current
    setLoading(true)
    try {
      const found = await userOptions(use, text, page)
      if (call !== latest.current) return
      setOptions((shown) => (append ? [...shown, ...found.items] : found.items))
      setNext(found.nextCursor)
    } catch {
      if (call === latest.current && !append) setOptions([])
    } finally {
      if (call === latest.current) setLoading(false)
    }
  }

  const shown = options.filter((option) => option.id !== exclude)
  const choices = [
    ...(withSystem ? [{ value: SYSTEM_VALUE, label: TEXT.audit.system }] : []),
    ...(value && value.id !== null && !shown.some((option) => option.id === value.id) ? [{ value: value.id, label: value.username }] : []),
    ...shown.map((option) => ({ value: option.id, label: option.username })),
  ]
  const selected = value ? (value.id === null ? SYSTEM_VALUE : value.id) : undefined

  return (
    <Select<number>
      aria-label={label}
      showSearch
      allowClear
      disabled={disabled}
      status={status}
      placeholder={placeholder}
      filterOption={false}
      value={selected}
      notFoundContent={loading ? <Spin size="small" /> : TEXT.tickets.noMatchingUsers}
      options={choices}
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
        if (id === undefined || id === null) return onChange(null)
        if (id === SYSTEM_VALUE) return onChange({ id: null, username: TEXT.audit.system })
        const option = options.find((candidate) => candidate.id === id)
        onChange({ id, username: option?.username ?? value?.username ?? String(id) })
      }}
      className="user-picker"
    />
  )
}
