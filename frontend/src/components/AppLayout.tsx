import { useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Drawer, Grid, Layout, Menu, type MenuProps } from 'antd'
import { useAuth } from '../auth/context'
import { TEXT } from '../text'
import { entryKeyFor, visibleEntries, type NavEntry } from './navigation'
import { TopBar } from './TopBar'

/**
 * The frame of every page of a user with a role: the top bar, the navigation, and the page. The
 * navigation holds only the entries the user's permissions open. Below 768 px it moves into a drawer
 * that a Menu button in the top bar opens, as in the Figma design.
 */
export function AppLayout() {
  const { account } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const screens = Grid.useBreakpoint()
  const small = screens.md === false
  const [drawerOpen, setDrawerOpen] = useState(false)

  const items = visibleEntries(account).map(toMenuItem)
  const selected = entryKeyFor(pathname)

  const onClick: MenuProps['onClick'] = ({ key }) => {
    const path = findPath(visibleEntries(account), key)
    if (path) navigate(path)
    setDrawerOpen(false)
  }

  const menu = (
    <Menu
      mode="inline"
      items={items}
      selectedKeys={selected ? [selected] : []}
      defaultOpenKeys={['tickets']}
      onClick={onClick}
      className="app-nav"
      aria-label="Main navigation"
    />
  )

  return (
    <div className="app-page">
      <TopBar onMenu={small ? () => setDrawerOpen(true) : undefined} />
      <Layout className="app-shell">
        {!small && (
          <Layout.Sider width={220} theme="light" className="app-shell__sider">
            {menu}
          </Layout.Sider>
        )}
        <Layout.Content className="app-shell__content">
          <Outlet />
        </Layout.Content>
      </Layout>
      {small && (
        <Drawer
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          placement="left"
          width={280}
          title={TEXT.menu}
          closeIcon={<span aria-label={TEXT.close}>×</span>}
          styles={{ body: { padding: 0 } }}
        >
          {menu}
        </Drawer>
      )}
    </div>
  )
}

function toMenuItem(entry: NavEntry): NonNullable<MenuProps['items']>[number] {
  if (entry.children) {
    return {
      key: entry.key,
      label: entry.label,
      icon: entry.icon,
      children: entry.children.map(toMenuItem),
    }
  }
  return { key: entry.key, label: entry.label, icon: entry.icon, disabled: !entry.built }
}

function findPath(entries: NavEntry[], key: string): string | undefined {
  for (const entry of entries) {
    if (entry.key === key && entry.built) return entry.path
    const child = entry.children && findPath(entry.children, key)
    if (child) return child
  }
  return undefined
}
