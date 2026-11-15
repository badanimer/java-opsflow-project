import { useCallback, useEffect, useState } from 'react'
import { FIRST_PAGE, type Page, type PageRequest } from '../api/paging'

export interface RecordList<T> {
  page: Page<T> | null
  /** The page asked for, with its number. */
  request: PageRequest
  loading: boolean
  error: unknown
  goTo: (request: PageRequest) => void
  reload: () => void
}

/**
 * A list of records paged by key, for the logs and the Deleted items module. `query` holds the filters: a
 * new value starts again at the first page, as Paging in docs/api/api-design.md asks. `key` is a text that
 * changes exactly when `query` does, so that an equal filter object does not reload the list.
 */
export function useRecords<Q, T>(load: (query: Q, page: PageRequest) => Promise<Page<T>>, query: Q, key: string): RecordList<T> {
  const [request, setRequest] = useState<PageRequest>(FIRST_PAGE)
  const [reloads, setReloads] = useState(0)
  const [page, setPage] = useState<Page<T> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [shownKey, setShownKey] = useState(key)

  // A change of filter starts at the first page, before anything is loaded with the old cursor.
  if (key !== shownKey) {
    setShownKey(key)
    setRequest(FIRST_PAGE)
  }

  useEffect(() => {
    let current = true
    setLoading(true)
    load(query, request)
      .then((loaded) => {
        if (!current) return
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
    // `query` is represented by `key`; `load` is a module function of the API.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, request, reloads])

  const reload = useCallback(() => setReloads((count) => count + 1), [])
  return { page, request, loading, error, goTo: setRequest, reload }
}
