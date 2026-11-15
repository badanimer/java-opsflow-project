import { ApiError, NetworkError } from '../api/client'
import { TEXT } from '../text'

export interface Notice {
  type: 'error' | 'warning' | 'info' | 'success'
  message: string
}

/**
 * The message for a failure that no page has a message of its own for: no answer from the server, a
 * limit, or a fault of the server, which is shown with the id of the request so that it can be reported.
 */
/** The message of a failed request on a page of the application: no answer, or a fault with its reference. */
export function failureNotice(error: unknown): Notice {
  return error instanceof NetworkError ? { type: 'error', message: TEXT.cannotReach } : generalFailure(error)
}

export function generalFailure(error: unknown): Notice {
  if (error instanceof NetworkError) return { type: 'error', message: TEXT.cannotReach }
  if (error instanceof ApiError) {
    if (error.code === 'TOO_MANY_REQUESTS') return { type: 'warning', message: TEXT.tooManyRequests }
    return { type: 'error', message: TEXT.somethingWrong(error.requestId) }
  }
  return { type: 'error', message: TEXT.somethingWrong() }
}
