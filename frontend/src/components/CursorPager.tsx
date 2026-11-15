import { Button } from 'antd'
import type { Page, PageRequest } from '../api/paging'
import { TEXT } from '../text'

/**
 * Previous and Next of a list paged by key. A button is shown only when its cursor is there: the first
 * page has no Previous and the last no Next. There are no page numbers and no total. Every later list
 * uses it: products, stock, the logs, Deleted items, the ticket screens, and the audit log.
 */
export function CursorPager({ page, onPage }: { page: Page<unknown>; onPage: (request: PageRequest) => void }) {
  if (!page.prevCursor && !page.nextCursor) return null
  return (
    <nav className="cursor-pager" aria-label="Pages">
      {page.prevCursor && <Button onClick={() => onPage({ cursor: page.prevCursor!, direction: 'prev' })}>{TEXT.previous}</Button>}
      {page.nextCursor && <Button onClick={() => onPage({ cursor: page.nextCursor!, direction: 'next' })}>{TEXT.next}</Button>}
    </nav>
  )
}
