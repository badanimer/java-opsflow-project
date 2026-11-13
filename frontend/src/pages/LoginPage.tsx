import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Alert, Button, Form, Input } from 'antd'
import { login } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/context'
import { AuthLayout } from '../components/AuthLayout'
import { TEXT } from '../text'
import { generalFailure, type Notice } from './failure'

interface Values {
  username: string
  password: string
}

/** Why the login page was opened, passed by the page or the event that sent the user here. */
type Reason = 'expired' | 'disabled' | 'reset'

const REASONS: Record<Reason, Notice> = {
  expired: { type: 'info', message: TEXT.sessionExpired },
  disabled: { type: 'error', message: TEXT.disabled },
  reset: { type: 'success', message: TEXT.passwordReset },
}

/**
 * The login page. The answers of the server and what the page shows for each are in
 * docs/frontend/page-design.md: one message for a wrong username or password, one for a lock, and one
 * for a disabled account, which is told only to a person who typed the right password.
 */
export function LoginPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const reason = (location.state as { notice?: Reason } | null)?.notice
  const [notice, setNotice] = useState<Notice | null>(reason ? REASONS[reason] ?? null : null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(values: Values) {
    setSubmitting(true)
    setNotice(null)
    try {
      const session = await login(values.username, values.password)
      await signIn(session)
      // The guards send a user with no role on to the pending page.
      navigate('/', { replace: true })
    } catch (error) {
      setNotice(describe(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Sign in" subtitle="Enter your username and password.">
      {notice && <Alert type={notice.type} showIcon message={notice.message} role="alert" />}
      <Form<Values> layout="vertical" requiredMark={false} onFinish={submit} disabled={submitting} size="large">
        <Form.Item label="Username" name="username" rules={[{ required: true, message: TEXT.usernameRequired }]}>
          <Input placeholder="Your username" autoComplete="username" maxLength={64} autoFocus />
        </Form.Item>
        <Form.Item label="Password" name="password" rules={[{ required: true, message: TEXT.passwordRequired }]}>
          <Input.Password placeholder="Your password" autoComplete="current-password" maxLength={24} />
        </Form.Item>
        <div className="auth-card__aside">
          <Link to="/forgot-password">Forgot password?</Link>
        </div>
        <Button type="primary" htmlType="submit" block loading={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </Form>
      <p className="auth-card__footer">
        Don&apos;t have an account? <Link to="/sign-up">Sign up</Link>
      </p>
    </AuthLayout>
  )
}

function describe(error: unknown): Notice {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'UNAUTHENTICATED':
      // A value the server refuses before any check, such as an over-long username, cannot be right.
      // eslint-disable-next-line no-fallthrough
      case 'VALIDATION_FAILED':
        return { type: 'error', message: TEXT.wrongCredentials }
      case 'LOGIN_LOCKED':
        return { type: 'warning', message: TEXT.locked }
      case 'ACCOUNT_DISABLED':
        return { type: 'error', message: TEXT.disabled }
    }
  }
  return generalFailure(error)
}
