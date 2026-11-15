import { Alert, Button } from 'antd'
import { ApiError } from '../api/client'
import { failureNotice } from '../pages/failure'
import { TEXT } from '../text'

/** The title and the line of an empty list. */
export function EmptyText({ title, text }: { title: string; text: string }) {
  return (
    <>
      <strong className="panel__empty-title">{title}</strong>
      <span>{text}</span>
    </>
  )
}

/**
 * A list that could not be loaded, as the stock page, the product page, and the catalog of List item
 * show it: a search the server refused (Clear filters), a lost permission, no answer, or a fault with its
 * reference (Try again). `title` heads the alert when the place needs one.
 */
export function ListFailure({ error, onRetry, onClear, title }: { error: unknown; onRetry: () => void; onClear: () => void; title?: string }) {
  if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
    return (
      <div className="list-failure">
        <Alert type="error" showIcon message={TEXT.stock.searchRefusedTitle} description={TEXT.filtersRefused} role="alert" />
        <Button onClick={onClear}>{TEXT.clearFilters}</Button>
      </div>
    )
  }
  const failure = error instanceof ApiError && error.code === 'FORBIDDEN' ? { type: 'error' as const, message: TEXT.noLongerAllowed } : failureNotice(error)
  return (
    <div className="list-failure">
      <Alert
        type={failure.type}
        showIcon
        message={title ?? failure.message}
        description={title ? failure.message : undefined}
        role="alert"
      />
      <Button onClick={onRetry}>{TEXT.tryAgain}</Button>
    </div>
  )
}
