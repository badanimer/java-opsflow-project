import { useState } from 'react'
import { DatePicker, Select } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { QUICK_RANGES, quickRangeDays, todayInSydney, type QuickRange } from '../lib/days'
import { TEXT } from '../text'

/** The days of a filter, `yyyy-MM-dd` in Sydney, the quick range that filled them, and the direction. */
export interface TimeFilterValue {
  from?: string
  to?: string
  quick?: QuickRange
  order: 'desc' | 'asc'
}

const DAY = 'YYYY-MM-DD'

/**
 * The shared time filter (The time filter in docs/frontend/page-design.md; Figma component "Time filter"):
 * a start day and an end day, or a start day alone for "until now"; a quick range that fills both; and
 * newest first or oldest first. Days after today in Sydney cannot be picked. An end day before the start
 * day is shown under the field and not applied. Changing a day by hand clears the quick range.
 */
export function TimeFilter({ value, onChange, disabled }: { value: TimeFilterValue; onChange: (next: TimeFilterValue) => void; disabled?: boolean }) {
  const [refused, setRefused] = useState(false)
  const today = todayInSydney()

  function setDays(from: string | undefined, to: string | undefined) {
    if (from && to && to < from) {
      setRefused(true)
      return
    }
    setRefused(false)
    onChange({ ...value, from, to, quick: undefined })
  }

  const afterToday = (day: Dayjs) => day.format(DAY) > today
  const asDay = (day: string | undefined) => (day ? dayjs(day, DAY) : null)

  return (
    <div className="time-filter">
      <label className="time-filter__field">
        <span>{TEXT.logs.startDate}</span>
        <DatePicker
          aria-label={TEXT.logs.startDate}
          placeholder={TEXT.logs.selectStart}
          format={DAY}
          value={asDay(value.from)}
          disabledDate={afterToday}
          disabled={disabled}
          onChange={(day) => setDays(day ? day.format(DAY) : undefined, day ? value.to : undefined)}
        />
      </label>
      <label className="time-filter__field">
        <span>{TEXT.logs.endDate}</span>
        <DatePicker
          aria-label={TEXT.logs.endDate}
          placeholder={TEXT.logs.selectEnd}
          format={DAY}
          value={asDay(value.to)}
          disabledDate={afterToday}
          disabled={disabled || !value.from}
          status={refused ? 'error' : undefined}
          onChange={(day) => setDays(value.from, day ? day.format(DAY) : undefined)}
        />
        {refused && <span className="field-error field-error--inline" role="alert">{TEXT.logs.endBeforeStart}</span>}
      </label>
      <label className="time-filter__field">
        <span>{TEXT.logs.quickRange}</span>
        <Select<QuickRange>
          aria-label={TEXT.logs.quickRange}
          placeholder={TEXT.logs.selectRange}
          value={value.quick}
          allowClear
          disabled={disabled}
          options={QUICK_RANGES.map((range) => ({ value: range, label: TEXT.logs.quickRanges[range] }))}
          onChange={(range) => {
            setRefused(false)
            if (!range) onChange({ ...value, from: undefined, to: undefined, quick: undefined })
            else onChange({ ...value, ...quickRangeDays(range, today), quick: range })
          }}
          className="time-filter__select"
        />
      </label>
      <label className="time-filter__field">
        <span>{TEXT.logs.sortOrder}</span>
        <Select<'desc' | 'asc'>
          aria-label={TEXT.logs.sortOrder}
          value={value.order}
          disabled={disabled}
          options={[
            { value: 'desc', label: TEXT.logs.newestFirst },
            { value: 'asc', label: TEXT.logs.oldestFirst },
          ]}
          onChange={(order) => onChange({ ...value, order })}
          className="time-filter__select"
        />
      </label>
    </div>
  )
}
