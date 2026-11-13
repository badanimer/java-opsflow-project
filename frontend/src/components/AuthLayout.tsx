import type { ReactNode } from 'react'
import { Typography } from 'antd'
import { Logo } from './Logo'

/**
 * The frame of the pages for people who are not logged in: one card in the middle of the page, with
 * the logo, a title, and a line under it. On a narrow screen the card fills the width.
 */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <main className="auth-page">
      <section className="auth-card">
        <Logo />
        <div className="auth-card__titles">
          <Typography.Title level={1} className="auth-card__title">
            {title}
          </Typography.Title>
          <Typography.Text type="secondary">{subtitle}</Typography.Text>
        </div>
        {children}
      </section>
    </main>
  )
}
