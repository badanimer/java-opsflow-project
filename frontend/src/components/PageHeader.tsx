import type { ReactNode } from 'react'
import { Typography } from 'antd'

/** The head of every page of the application: the title, and the page's actions on the right. */
export function PageHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <div className="page-header">
      <Typography.Title level={1} className="page-header__title">
        {title}
      </Typography.Title>
      {actions && <div className="page-header__actions">{actions}</div>}
    </div>
  )
}
