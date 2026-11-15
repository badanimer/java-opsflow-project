/**
 * Days and times as the logs show them (The time filter in docs/frontend/page-design.md). A day is a day in
 * Sydney, written `yyyy-MM-dd` as the API takes it; the server turns the days into a UTC range. A time is
 * shown in Sydney as "15 Nov 2026 15:58".
 */

export const DISPLAY_ZONE = 'Australia/Sydney'

/** Today in Sydney, as `yyyy-MM-dd`. */
export function todayInSydney(now: Date = new Date()): string {
  // en-CA writes a date as yyyy-MM-dd.
  return new Intl.DateTimeFormat('en-CA', { timeZone: DISPLAY_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now)
}

export type QuickRange = 'days1' | 'days3' | 'days7' | 'days15' | 'months1' | 'months3'

export const QUICK_RANGES: QuickRange[] = ['days1', 'days3', 'days7', 'days15', 'months1', 'months3']

function parts(day: string): [number, number, number] {
  const [year, month, date] = day.split('-').map(Number)
  return [year, month, date]
}

function write(year: number, month: number, date: number): string {
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(date).padStart(2, '0')}`
}

function addDays(day: string, days: number): string {
  const [year, month, date] = parts(day)
  const moved = new Date(Date.UTC(year, month - 1, date + days))
  return write(moved.getUTCFullYear(), moved.getUTCMonth() + 1, moved.getUTCDate())
}

/**
 * The two days of a quick range, ending today. For N days the start is today minus N−1 days, so Last 1
 * day is today. For months the start is the same day 1 or 3 months earlier (the last day of that month
 * when it is shorter), plus one day: on 15 November, Last 1 month starts on 16 October.
 */
export function quickRangeDays(range: QuickRange, today: string): { from: string; to: string } {
  if (range.startsWith('days')) {
    const days = Number(range.slice(4))
    return { from: addDays(today, -(days - 1)), to: today }
  }
  const months = Number(range.slice(6))
  const [year, month, date] = parts(today)
  const index = year * 12 + (month - 1) - months
  const startYear = Math.floor(index / 12)
  const startMonth = (index % 12) + 1
  const lastDay = new Date(Date.UTC(startYear, startMonth, 0)).getUTCDate()
  return { from: addDays(write(startYear, startMonth, Math.min(date, lastDay)), 1), to: today }
}

const TIME = new Intl.DateTimeFormat('en-AU', {
  timeZone: DISPLAY_ZONE,
  day: 'numeric',
  month: 'numeric',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** Three letters for every month: the locale writes "July" and "Sept" in full. */
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** An instant of the API (ISO 8601 in UTC) as Sydney time: "15 Nov 2026 15:58". */
export function formatTime(instant: string): string {
  const part = Object.fromEntries(TIME.formatToParts(new Date(instant)).map((p) => [p.type, p.value]))
  return `${Number(part.day)} ${MONTHS[Number(part.month) - 1]} ${part.year} ${part.hour}:${part.minute}`
}
