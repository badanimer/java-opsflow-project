import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Typography } from 'antd'
import { ClockCircleOutlined } from '@ant-design/icons'
import { useAuth } from '../auth/context'
import { TopBar } from '../components/TopBar'
import { TEXT } from '../text'
import { generalFailure, type Notice } from './failure'

/**
 * The page of a logged-in user who has no role yet. Nothing in the application is open to such a user,
 * so this is the only page they see. "Check again" asks the server for the roles; when one was
 * assigned in the meantime the application opens.
 */
export function PendingPage() {
  const { refresh } = useAuth()
  const navigate = useNavigate()
  const [checking, setChecking] = useState(false)
  const [notice, setNotice] = useState<Notice | null>(null)

  async function checkAgain() {
    setChecking(true)
    setNotice(null)
    try {
      const account = await refresh()
      if (account.roles.length > 0) navigate('/', { replace: true })
      else setNotice({ type: 'info', message: TEXT.stillNoRole })
    } catch (error) {
      // A session that ended is handled by the login state, which opens the login page.
      setNotice(generalFailure(error))
    } finally {
      setChecking(false)
    }
  }

  return (
    <div className="app-page">
      <TopBar />
      <main className="app-page__center">
        <section className="pending-card">
          <span className="pending-card__icon" aria-hidden="true">
            <ClockCircleOutlined />
          </span>
          <Typography.Title level={1} className="pending-card__title">
            {TEXT.pendingTitle}
          </Typography.Title>
          <Typography.Text type="secondary">{TEXT.pendingDescription}</Typography.Text>
          {notice && <Alert type={notice.type} showIcon message={notice.message} role="alert" className="pending-card__notice" />}
          <Button size="large" onClick={checkAgain} loading={checking}>
            {TEXT.checkAgain}
          </Button>
        </section>
      </main>
    </div>
  )
}
