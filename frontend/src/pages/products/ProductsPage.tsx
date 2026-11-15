import { useState } from 'react'
import { Alert, Button, Card, Empty, Input, Spin, Typography } from 'antd'
import { ApiError } from '../../api/client'
import { delistProduct, listProducts, type ShopProduct } from '../../api/products'
import { useAuth } from '../../auth/context'
import { can } from '../../auth/permissions'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { CursorPager } from '../../components/CursorPager'
import { ItemThumbnail } from '../../components/ItemThumbnail'
import { EmptyText, ListFailure } from '../../components/ListStates'
import { PageHeader } from '../../components/PageHeader'
import { usePagedList } from '../../hooks/usePagedList'
import { formatPrice } from '../../lib/format'
import { TEXT } from '../../text'
import { failureNotice, type Notice } from '../../lib/failure'
import { ListItemDialog } from './ListItemDialog'
import { OrderDialog } from './OrderDialog'

const SEARCH_MAX = 200

interface Delisting {
  product: ShopProduct
  running: boolean
  notice: Notice | null
  finished: boolean
}

/**
 * The product page, the home page of every role (Product page in docs/frontend/page-design.md, Figma
 * page "Stock and Products"): the products on sale, by name, as cards with the picture, the name, and the
 * price in Australian dollars, and nothing else. A manager or an administrator lists an item (List item)
 * and delists a product after a second confirmation. The Order button comes in Phase 5, in the space the
 * card leaves at its foot.
 */
export function ProductsPage() {
  const { account, refresh } = useAuth()
  const list = usePagedList(listProducts)
  const [searchText, setSearchText] = useState('')
  const [notice, setNotice] = useState<Notice | null>(null)
  const [delisting, setDelisting] = useState<Delisting | null>(null)
  const [listing, setListing] = useState(false)
  const [ordering, setOrdering] = useState<ShopProduct | null>(null)

  const mayList = can(account, 'PRODUCT_LIST')
  const mayDelist = can(account, 'PRODUCT_DELIST')
  const mayOrder = can(account, 'ORDER_PLACE')

  function clearSearch() {
    setSearchText('')
    list.applySearch('')
  }

  async function confirmDelist() {
    if (!delisting) return
    const product = delisting.product
    setDelisting({ product, running: true, notice: null, finished: false })
    try {
      await delistProduct(product.id)
      setDelisting(null)
      setNotice({ type: 'success', message: TEXT.products.productDelisted })
      list.reload()
    } catch (error) {
      const code = error instanceof ApiError ? error.code : null
      if (code === 'ALREADY_DELISTED') {
        setDelisting({ product, running: false, notice: { type: 'warning', message: TEXT.products.alreadyDelisted }, finished: true })
      } else if (code === 'NOT_FOUND') {
        setDelisting({ product, running: false, notice: { type: 'warning', message: TEXT.products.productGone }, finished: true })
      } else if (code === 'FORBIDDEN') {
        refresh().catch(() => {})
        setDelisting({ product, running: false, notice: { type: 'error', message: TEXT.noLongerAllowed }, finished: true })
      } else {
        setDelisting({ product, running: false, notice: failureNotice(error), finished: false })
      }
    }
  }

  function closeDelisting() {
    const ended = delisting?.finished
    setDelisting(null)
    if (ended) list.reload()
  }

  const { page, loading, error } = list
  return (
    <>
      <PageHeader
        title={TEXT.nav.products}
        actions={
          mayList && (
            <Button type="primary" onClick={() => { setNotice(null); setListing(true) }}>
              {TEXT.products.listItem}
            </Button>
          )
        }
      />

      <section className="panel filter-bar" aria-label="Filters">
        <Input.Search
          placeholder={TEXT.stock.searchPlaceholder}
          aria-label={TEXT.stock.searchPlaceholder}
          value={searchText}
          maxLength={SEARCH_MAX}
          allowClear
          onChange={(event) => {
            setSearchText(event.target.value)
            if (event.target.value === '' && list.search !== '') list.applySearch('')
          }}
          onSearch={(value) => list.applySearch(value)}
          className="filter-bar__search"
        />
      </section>

      {notice && <Alert type={notice.type} showIcon message={notice.message} closable onClose={() => setNotice(null)} role="alert" className="page-notice" />}

      <section className="panel panel--list" aria-label={TEXT.products.listLabel}>
        {error ? (
          <ListFailure error={error} onRetry={() => { if (error instanceof ApiError && error.code === 'FORBIDDEN') refresh().catch(() => {}); list.reload() }} onClear={clearSearch} />
        ) : !page ? (
          <div className="list-loading" aria-busy="true">
            <Spin />
            <Typography.Text type="secondary">{TEXT.products.loading}</Typography.Text>
          </div>
        ) : page.items.length === 0 && !loading ? (
          list.search ? (
            <Empty description={<EmptyText title={TEXT.products.noneFoundTitle} text={TEXT.products.noneFoundDescription} />}>
              <Button onClick={clearSearch}>{TEXT.clearFilters}</Button>
            </Empty>
          ) : (
            <Empty description={<EmptyText title={TEXT.products.emptyTitle} text={mayList ? TEXT.products.emptyManage : TEXT.products.emptyRead} />} />
          )
        ) : (
          <Spin spinning={loading}>
            <ul className="product-grid">
              {page.items.map((product) => (
                <li key={product.id}>
                  <Card className="product-card" size="small" cover={<ItemThumbnail url={product.imageUrl} size={140} />}>
                    <Typography.Paragraph className="product-card__name" ellipsis={{ rows: 2, tooltip: product.name }}>
                      {product.name}
                    </Typography.Paragraph>
                    <Typography.Text strong className="product-card__price">
                      {formatPrice(product.price)}
                    </Typography.Text>
                    <div className="product-card__foot">
                      {mayOrder && (
                        <Button type="primary" size="small" onClick={() => { setNotice(null); setOrdering(product) }}>
                          {TEXT.order.order}
                        </Button>
                      )}
                      <span className="product-card__spacer" />
                      {mayDelist && (
                        <Button type="link" size="small" danger onClick={() => { setNotice(null); setDelisting({ product, running: false, notice: null, finished: false }) }}>
                          {TEXT.products.delist}
                        </Button>
                      )}
                    </div>
                  </Card>
                </li>
              ))}
            </ul>
          </Spin>
        )}
        {page && !error && !loading && <CursorPager page={page} onPage={list.goTo} />}
      </section>

      {ordering && (
        <OrderDialog
          product={ordering}
          onClose={(reload) => {
            setOrdering(null)
            if (reload) list.reload()
          }}
        />
      )}

      {listing && (
        <ListItemDialog
          onListed={() => {
            setListing(false)
            setNotice({ type: 'success', message: TEXT.products.itemListed })
            list.reload()
          }}
          onClose={() => setListing(false)}
        />
      )}

      <ConfirmDialog
        open={delisting !== null}
        title={delisting ? TEXT.products.delistTitle(delisting.product.name) : ''}
        confirmLabel={TEXT.products.delistProduct}
        runningLabel={TEXT.products.delisting}
        cancelLabel={TEXT.cancel}
        closeLabel={TEXT.close}
        danger
        running={delisting?.running ?? false}
        notice={delisting?.notice}
        finished={delisting?.finished}
        onConfirm={() => void confirmDelist()}
        onCancel={closeDelisting}
      >
        <Typography.Paragraph className="dialog-text">{TEXT.products.delistDescription}</Typography.Paragraph>
      </ConfirmDialog>
    </>
  )
}
