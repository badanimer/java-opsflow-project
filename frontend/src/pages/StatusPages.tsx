import { useNavigate } from 'react-router-dom'
import { Button, Result } from 'antd'
import { useAuth } from '../auth/context'
import { can } from '../auth/permissions'
import { TEXT } from '../text'

/** Go to Products, offered only to a user who may open the product page. */
function GoToProducts() {
  const { account } = useAuth()
  const navigate = useNavigate()
  if (!can(account, 'PRODUCT_VIEW')) return null
  return (
    <Button type="primary" onClick={() => navigate('/')}>
      {TEXT.goToProducts}
    </Button>
  )
}

/**
 * A known page that the user's permissions do not open, reached by typing its address or through an old
 * bookmark. The navigation never offers it. The server refuses its data anyway.
 */
export function NotAllowedPage() {
  return <Result status="403" title={TEXT.notAllowedTitle} subTitle={TEXT.notAllowedDescription} extra={<GoToProducts />} />
}

/** An address that is no page of the application. */
export function NotFoundPage() {
  return <Result status="404" title={TEXT.notFoundTitle} subTitle={TEXT.notFoundDescription} extra={<GoToProducts />} />
}
