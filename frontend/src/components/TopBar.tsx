import { Button } from 'antd'
import { useAuth } from '../auth/context'
import { TEXT } from '../text'
import { Logo } from './Logo'

/** The bar at the top of the pages of a logged-in user: the logo, the username, and Sign out. */
export function TopBar() {
  const { account, signOut } = useAuth()
  return (
    <header className="top-bar">
      <Logo />
      <div className="top-bar__account">
        <span className="top-bar__name">{account?.username}</span>
        <Button type="link" onClick={signOut} className="top-bar__sign-out">
          {TEXT.signOut}
        </Button>
      </div>
    </header>
  )
}
