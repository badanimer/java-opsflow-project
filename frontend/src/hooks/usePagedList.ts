import { useCallback, useEffect, useState } from 'react'
import { FIRST_PAGE, type Page, type PageRequest } from '../api/paging'

export interface PagedList<T> {
  /** The page shown, or null before the first answer. */
  page: Page<T> | null
  loading: boolean
  /** The failure of the latest load, or null. */
  error: unknown
  /** The search that was applied. */
  search: string
  /** The page asked for: the first, or one after or before a cursor. */
  request: PageRequest
  applySearch: (search: string) => void
  goTo: (request: PageRequest) => void
  /** Loads the same page with the same search again, for example after a change. */
  reload: () => void
}

/**
 * A list paged by key with a search by name (Paging in docs/api/api-design.md): the stock page, the
 * product page, and the catalog of List item use it. A new search starts again at the first page. A
 * page that a change emptied falls back to the first page, as on the user page.
 */
export function usePagedList<T>(load: (search: string, request: PageRequest) => Promise<Page<T>>, setting = ''): PagedList<T> {
  const [search, setSearch] = useState('')
  const [request, setRequest] = useState<PageRequest>(FIRST_PAGE)
  const [shownSetting, setShownSetting] = useState(setting)
  // `setting` is a choice of the page that `load` reads, such as the size of a page: a new one starts at page 1.
  if (setting !== shownSetting) {
    setShownSetting(setting)
    setRequest(FIRST_PAGE)
  }
  const [reloads, setReloads] = useState(0)
  const [page, setPage] = useState<Page<T> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    let current = true
    setLoading(true)
    load(search, request)
      .then((loaded) => {
        if (!current) return
        if (loaded.items.length === 0 && request.cursor) {
          setRequest(FIRST_PAGE)
          return
        }
        setPage(loaded)
        setError(null)
        setLoading(false)
      })
      .catch((failure: unknown) => {
        if (!current) return
        setError(failure)
        setLoading(false)
      })
    return () => {
      current = false
    }
    // `load` is a module function of the API; the list reloads on a new search, page, or reload.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, request, reloads, setting])

  const applySearch = useCallback((next: string) => {
    setSearch(next.trim())
    setRequest(FIRST_PAGE)
  }, [])
  const reload = useCallback(() => setReloads((count) => count + 1), [])

  return { page, loading, error, search, request, applySearch, goTo: setRequest, reload }
}
