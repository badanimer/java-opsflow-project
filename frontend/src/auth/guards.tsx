import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { Alert, Button, Spin } from 'antd'
import { TEXT } from '../text'
import { useAuth } from './context'

/** What every guard shows while the token is checked, and when the server gives no answer. */
function Waiting() {
  const { status, retry } = useAuth()
  if (status === 'unreachable') {
    return (
      <div className="center-page">
        <div className="center-page__box">
          <Alert type="error" showIcon message={TEXT.cannotReach} />
          <Button onClick={retry}>Try again</Button>
        </div>
      </div>
    )
  }
  return (
    <div className="center-page" aria-busy="true">
      <Spin size="large" />
    </div>
  )
}

/**
 * A page of the application itself. Without a login it opens the login page. A user who has no role
 * yet is sent to the pending page, as the page design says: nothing is allowed until a role is assigned.
 */
export function RequireRole({ children }: { children: ReactNode }) {
  const { status, account } = useAuth()
  if (status === 'loading' || status === 'unreachable') return <Waiting />
  if (status === 'signedOut' || !account) return <Navigate to="/login" replace />
  if (account.roles.length === 0) return <Navigate to="/pending" replace />
  return <>{children}</>
}

/** The pending page: only for a logged-in user with no role. A user with a role skips it. */
export function RequireNoRole({ children }: { children: ReactNode }) {
  const { status, account } = useAuth()
  if (status === 'loading' || status === 'unreachable') return <Waiting />
  if (status === 'signedOut' || !account) return <Navigate to="/login" replace />
  if (account.roles.length > 0) return <Navigate to="/" replace />
  return <>{children}</>
}

/** Login, sign-up, and Forgot password: a user who is already logged in goes to the application. */
export function GuestOnly({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  if (status === 'loading') return <Waiting />
  if (status === 'signedIn') return <Navigate to="/" replace />
  return <>{children}</>
}
