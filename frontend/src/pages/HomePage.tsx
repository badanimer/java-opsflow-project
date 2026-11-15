import { ShopOutlined } from '@ant-design/icons'
import { Empty } from 'antd'
import { PageHeader } from '../components/PageHeader'
import { TEXT } from '../text'

/**
 * The product page, the first page of every user with a role. It is not built yet (Phase 4), so it
 * holds the place with a notice.
 */
export function HomePage() {
  return (
    <>
      <PageHeader title={TEXT.nav.products} />
      <section className="panel">
        <Empty
          image={<ShopOutlined className="panel__empty-icon" />}
          description={
            <>
              <strong className="panel__empty-title">{TEXT.productsComingTitle}</strong>
              <span>{TEXT.productsComingDescription}</span>
            </>
          }
        />
      </section>
    </>
  )
}
