/**
 * Paging by key, as in Paging of docs/api/api-design.md: a page has the rows and two opaque cursors,
 * each null when there is no page in that direction. The client never builds or reads a cursor, and a
 * change of filter starts again without one. There is no total and there are no page numbers.
 */

export interface Page<T> {
  items: T[]
  nextCursor: string | null
  prevCursor: string | null
}

/**
 * Which page to read: the first page (no cursor), or the page after or before a cursor. `number` is the
 * place of that page counted from the first, for the person only: Previous and Next count it, and any new
 * search or filter starts again at the first page, which is page 1. It is never sent.
 */
export interface PageRequest {
  cursor?: string
  direction?: 'next' | 'prev'
  number?: number
}

/** The number of the page a request reads: 1 for the first page. */
export function pageNumberOf(request: PageRequest): number {
  return request.number ?? 1
}

export const FIRST_PAGE: PageRequest = {}

/** Adds the paging parameters. The first page sends none, and the server's default size is used. */
export function pageParams(params: URLSearchParams, page: PageRequest): void {
  if (!page.cursor) return
  params.append('cursor', page.cursor)
  params.append('direction', page.direction ?? 'next')
}
