import { Typography } from 'antd'
import { useAuth } from '../auth/context'
import { TopBar } from '../components/TopBar'

/**
 * The place of the product page, which is the first page of every user with a role. It is not built
 * yet: this page only shows that the login worked and which roles the server reports.
 */
export function HomePage() {
  const { account } = useAuth()
  return (
    <div className="app-page">
      <TopBar />
      <main className="app-page__center">
        <section className="pending-card">
          <Typography.Title level={1} className="pending-card__title">
            You are signed in
          </Typography.Title>
          <Typography.Text type="secondary">
            Your roles: {account?.roles.join(', ')}. The product page is not built yet.
          </Typography.Text>
        </section>
      </main>
    </div>
  )
}
