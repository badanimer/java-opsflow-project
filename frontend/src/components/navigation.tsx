import type { ReactNode } from 'react'
import { AuditOutlined, InboxOutlined, ProfileOutlined, ShopOutlined, TeamOutlined } from '@ant-design/icons'
import type { Account } from '../api/auth'
import { can, type Permission } from '../auth/permissions'
import { TEXT } from '../text'

/**
 * Every page of the Page Map in docs/frontend/page-design.md, with the permission that opens it. An
 * entry is shown only to a user who holds its permission, so a role never sees a page it cannot open.
 * An entry whose page is not built yet is shown disabled to the users who will be able to open it.
 * A new page is added here, and a new role needs no change: it is the permissions that decide.
 */
export interface NavEntry {
  key: string
  label: string
  path: string
  permission: Permission
  /** False while the page is not built: the entry is shown, but cannot be chosen. */
  built: boolean
  icon?: ReactNode
  children?: NavEntry[]
}

export const NAVIGATION: readonly NavEntry[] = [
  { key: 'products', label: TEXT.nav.products, path: '/', permission: 'PRODUCT_VIEW', built: true, icon: <ShopOutlined /> },
  { key: 'stock', label: TEXT.nav.stock, path: '/stock', permission: 'STOCK_VIEW', built: false, icon: <InboxOutlined /> },
  {
    key: 'tickets',
    label: TEXT.nav.tickets,
    path: '/tickets',
    permission: 'TICKET_VIEW',
    built: false,
    icon: <ProfileOutlined />,
    children: [
      { key: 'tickets-pool', label: TEXT.nav.pool, path: '/tickets/pool', permission: 'TICKET_VIEW', built: false },
      { key: 'tickets-in-progress', label: TEXT.nav.inProgress, path: '/tickets/in-progress', permission: 'TICKET_VIEW', built: false },
      { key: 'tickets-resolved', label: TEXT.nav.resolved, path: '/tickets/resolved', permission: 'TICKET_VIEW', built: false },
      { key: 'tickets-archive', label: TEXT.nav.archive, path: '/tickets/archive', permission: 'TICKET_VIEW_ALL', built: false },
      { key: 'tickets-cancelled', label: TEXT.nav.cancelled, path: '/tickets/cancelled', permission: 'TICKET_VIEW', built: false },
    ],
  },
  { key: 'users', label: TEXT.nav.users, path: '/users', permission: 'USER_VIEW', built: true, icon: <TeamOutlined /> },
  { key: 'audit', label: TEXT.nav.auditLog, path: '/audit', permission: 'AUDIT_VIEW', built: false, icon: <AuditOutlined /> },
]

/** The entries the account may see: those of its permissions, and of a group only the children it may see. */
export function visibleEntries(account: Account | null): NavEntry[] {
  return NAVIGATION.filter((entry) => can(account, entry.permission)).map((entry) =>
    entry.children ? { ...entry, children: entry.children.filter((child) => can(account, child.permission)) } : entry,
  )
}

/** The key of the entry for a path, to mark it as the current page. */
export function entryKeyFor(pathname: string): string | undefined {
  for (const entry of NAVIGATION) {
    for (const candidate of [entry, ...(entry.children ?? [])]) {
      if (candidate.built && (candidate.path === pathname || (candidate.path !== '/' && pathname.startsWith(candidate.path + '/')))) {
        return candidate.key
      }
    }
  }
  return undefined
}
