import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { Alert, Button, Spin, Typography } from 'antd'
import { generalFailure } from '../pages/failure'
import { NotAllowedPage } from '../pages/StatusPages'
import { TEXT } from '../text'
import { useAuth } from './context'
import { can, type Permission } from './permissions'

/**
 * What every guard shows while the token is checked, and when the account could not be loaded. No
 * navigation is shown before the permissions are known.
 */
function Waiting() {
  const { status, startupError, retry } = useAuth()
  if (status === 'unreachable') {
    const failure = generalFailure(startupError)
    return (
      <div className="center-page">
        <div className="center-page__box">
          <Alert type={failure.type} showIcon message={failure.message} role="alert" />
          <Button onClick={retry}>{TEXT.tryAgain}</Button>
        </div>
      </div>
    )
  }
  return (
    <div className="center-page" aria-busy="true">
      <div className="center-page__box">
        <Spin size="large" />
        <Typography.Text type="secondary">{TEXT.loadingAccount}</Typography.Text>
      </div>
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

/**
 * A page that needs a permission. Without it the page says "Not allowed" inside the frame of the
 * application: the navigation never offers such a page, so this is reached only by its address. When a
 * permission is taken away while the page is open, the page turns into this one at the next load of the
 * account.
 */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const { account } = useAuth()
  if (!can(account, permission)) return <NotAllowedPage />
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
