import { Button, Typography } from 'antd'
import type { Page, PageRequest } from '../api/paging'
import { TEXT } from '../text'

/**
 * Previous, the number of the page shown, and Next, centred at the foot of a list paged by key. The number
 * is the most visible part. A button that has no page to go to is disabled, so that the number does not
 * move. There is no total, because the server never counts the rows of a list (Home page and paging in
 * the database design), and a list that fits on one page shows no pager. Every list uses it.
 *
 * @param number the number of the page shown, from the request that read it
 */
export function CursorPager({ page, number, onPage }: { page: Page<unknown>; number: number; onPage: (request: PageRequest) => void }) {
  if (!page.prevCursor && !page.nextCursor) return null
  return (
    <nav className="cursor-pager" aria-label="Pages">
      <Button disabled={!page.prevCursor} onClick={() => onPage({ cursor: page.prevCursor!, direction: 'prev', number: Math.max(1, number - 1) })}>
        {TEXT.previous}
      </Button>
      <Typography.Text strong className="cursor-pager__number" aria-live="polite">
        {TEXT.pageNumber(number)}
      </Typography.Text>
      <Button disabled={!page.nextCursor} onClick={() => onPage({ cursor: page.nextCursor!, direction: 'next', number: number + 1 })}>
        {TEXT.next}
      </Button>
    </nav>
  )
}
