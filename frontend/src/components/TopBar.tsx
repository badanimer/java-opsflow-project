import { Button } from 'antd'
import { MenuOutlined } from '@ant-design/icons'
import { useAuth } from '../auth/context'
import { TEXT } from '../text'
import { Logo } from './Logo'

/**
 * The bar at the top of the pages of a logged-in user: the logo, the username, and Sign out. On a small
 * screen the application passes `onMenu`, and the bar shows the Menu button that opens the navigation.
 */
export function TopBar({ onMenu }: { onMenu?: () => void }) {
  const { account, signOut } = useAuth()
  return (
    <header className="top-bar">
      <div className="top-bar__start">
        {onMenu && (
          <Button icon={<MenuOutlined />} onClick={onMenu}>
            {TEXT.menu}
          </Button>
        )}
        <Logo />
      </div>
      <div className="top-bar__account">
        <span className="top-bar__name">{account?.username}</span>
        <Button type="link" onClick={signOut} className="top-bar__sign-out">
          {TEXT.signOut}
        </Button>
      </div>
    </header>
  )
}
