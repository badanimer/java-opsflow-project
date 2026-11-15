import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Empty, Form, Input, Modal, Spin, Steps, Table, Typography } from 'antd'
import { ApiError } from '../../api/client'
import { listItem, listListable, type ListableItem } from '../../api/products'
import { useAuth } from '../../auth/context'
import { can } from '../../auth/permissions'
import { CursorPager } from '../../components/CursorPager'
import { ItemThumbnail } from '../../components/ItemThumbnail'
import { EmptyText, ListFailure } from '../../components/ListStates'
import { usePagedList } from '../../components/usePagedList'
import { CURRENCY_SIGN } from '../../format'
import { TEXT } from '../../text'
import { failureNotice, type Notice } from '../failure'

const NAME_MAX = 200
/** At most 10 digits before the point and two after, above 0: what the price column holds. */
const PRICE = /^\d{1,10}(\.\d{1,2})?$/

interface Props {
  onListed: () => void
  onClose: () => void
}

/**
 * List item (Figma frames "List item / ..."): step 1 chooses an item from the catalog, the items that
 * are not deleted and not on sale, searchable and paged; an item with no stock is shown and cannot be
 * chosen. Step 2 sets the product name, which starts as the item's name, and the price. Listing asks for
 * no second confirmation. A refusal that concerns the chosen item sends the person back to choose
 * another; a lost permission leaves only Close.
 */
export function ListItemDialog({ onListed, onClose }: Props) {
  const { account, refresh } = useAuth()
  const navigate = useNavigate()
  const catalog = usePagedList(listListable)
  const [searchText, setSearchText] = useState('')
  const [chosen, setChosen] = useState<ListableItem | null>(null)
  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const [errors, setErrors] = useState<{ name?: string; price?: string }>({})
  const [notice, setNotice] = useState<Notice | null>(null)
  const [saving, setSaving] = useState(false)
  const [ended, setEnded] = useState(false)

  function choose(item: ListableItem) {
    setChosen(item)
    setName(item.name)
    setPrice('')
    setErrors({})
    setNotice(null)
  }

  function back() {
    setChosen(null)
    setNotice(null)
    catalog.reload()
  }

  function forbidden() {
    refresh().catch(() => {})
    setNotice({ type: 'error', message: TEXT.noLongerAllowed })
    setEnded(true)
  }

  async function list() {
    if (!chosen) return
    setNotice(null)
    const found: { name?: string; price?: string } = {}
    const trimmed = name.trim()
    if (trimmed === '') found.name = TEXT.stock.nameRequired
    else if (trimmed.length > NAME_MAX) found.name = TEXT.stock.nameTooLong
    const typedPrice = price.trim()
    if (!PRICE.test(typedPrice) || Number(typedPrice) <= 0) found.price = TEXT.listing.priceInvalid
    setErrors(found)
    if (found.name || found.price) return

    setSaving(true)
    try {
      await listItem(chosen.inventoryId, trimmed, typedPrice)
      onListed()
    } catch (failure) {
      setSaving(false)
      const code = failure instanceof ApiError ? failure.code : null
      if (code === 'VALIDATION_FAILED') {
        const fields = (failure as ApiError).errors.map((error) => error.field)
        setErrors(fields.includes('price') ? { price: TEXT.listing.priceInvalid } : { name: trimmed === '' ? TEXT.stock.nameRequired : TEXT.stock.nameTooLong })
      } else if (code === 'NO_STOCK') {
        setNotice({ type: 'warning', message: TEXT.listing.noStock })
      } else if (code === 'ALREADY_LISTED') {
        setNotice({ type: 'warning', message: TEXT.listing.alreadyListed })
      } else if (code === 'NOT_FOUND') {
        setNotice({ type: 'warning', message: TEXT.stock.itemGone })
      } else if (code === 'FORBIDDEN') {
        forbidden()
      } else {
        setNotice(failureNotice(failure))
      }
    }
  }

  const catalogForbidden = catalog.error instanceof ApiError && catalog.error.code === 'FORBIDDEN'
  const finished = ended || catalogForbidden
  const footer = finished
    ? [
        <Button key="close" onClick={onClose} autoFocus>
          {TEXT.close}
        </Button>,
      ]
    : chosen
      ? [
          <Button key="cancel" onClick={onClose} disabled={saving}>
            {TEXT.cancel}
          </Button>,
          <Button key="back" onClick={back} disabled={saving}>
            {TEXT.listing.back}
          </Button>,
          <Button key="list" type="primary" onClick={() => void list()} disabled={price.trim() === ''} loading={saving}>
            {saving ? TEXT.listing.listing : TEXT.listing.list}
          </Button>,
        ]
      : [
          <Button key="cancel" onClick={onClose}>
            {TEXT.cancel}
          </Button>,
        ]

  return (
    <Modal
      open
      title={TEXT.products.listItem}
      width={720}
      closable={false}
      maskClosable={false}
      keyboard={!saving}
      onCancel={saving ? undefined : onClose}
      destroyOnClose
      footer={footer}
    >
      <div className="dialog-body">
        <Steps size="small" current={chosen ? 1 : 0} items={[{ title: TEXT.listing.stepChoose }, { title: TEXT.listing.stepPrice }]} />
        {chosen ? (
          <Form layout="vertical" requiredMark={false} disabled={saving || ended} component="div">
            <div className="chosen-item">
              <ItemThumbnail url={chosen.imageUrl} size={48} />
              <div>
                <Typography.Text strong>{chosen.name}</Typography.Text>
                <div>
                  <Typography.Text type="secondary">{TEXT.stock.skuLine(chosen.sku)}</Typography.Text>
                </div>
              </div>
            </div>
            <Form.Item label={TEXT.listing.productName} validateStatus={errors.name ? 'error' : undefined} help={errors.name} htmlFor="product-name">
              <Input id="product-name" value={name} onChange={(event) => setName(event.target.value)} />
            </Form.Item>
            <Form.Item label={TEXT.listing.price} validateStatus={errors.price ? 'error' : undefined} help={errors.price} htmlFor="product-price">
              <Input id="product-price" inputMode="decimal" prefix={CURRENCY_SIGN} value={price} placeholder="0.00" onChange={(event) => setPrice(event.target.value)} />
            </Form.Item>
          </Form>
        ) : catalogForbidden ? null : (
          <Catalog
            catalog={catalog}
            searchText={searchText}
            onSearchText={setSearchText}
            onChoose={choose}
            mayOpenStock={can(account, 'STOCK_VIEW')}
            onOpenStock={() => {
              onClose()
              navigate('/stock')
            }}
          />
        )}
        {(notice || catalogForbidden) && (
          <Alert type={notice?.type ?? 'error'} showIcon message={notice?.message ?? TEXT.noLongerAllowed} role="alert" />
        )}
      </div>
    </Modal>
  )
}

interface CatalogProps {
  catalog: ReturnType<typeof usePagedList<ListableItem>>
  searchText: string
  onSearchText: (text: string) => void
  onChoose: (item: ListableItem) => void
  mayOpenStock: boolean
  onOpenStock: () => void
}

/** Step 1: the items that can be listed. */
function Catalog({ catalog, searchText, onSearchText, onChoose, mayOpenStock, onOpenStock }: CatalogProps) {
  const { page, loading, error } = catalog
  const clear = () => {
    onSearchText('')
    catalog.applySearch('')
  }
  return (
    <section className="catalog" aria-label={TEXT.listing.catalogLabel}>
      <Input.Search
        placeholder={TEXT.stock.searchPlaceholder}
        aria-label={TEXT.stock.searchPlaceholder}
        value={searchText}
        maxLength={NAME_MAX}
        allowClear
        onChange={(event) => {
          onSearchText(event.target.value)
          if (event.target.value === '' && catalog.search !== '') catalog.applySearch('')
        }}
        onSearch={(value) => catalog.applySearch(value)}
      />
      {error ? (
        <ListFailure error={error} title={TEXT.listing.loadFailedTitle} onRetry={catalog.reload} onClear={clear} />
      ) : !page ? (
        <div className="list-loading" aria-busy="true">
          <Spin />
          <Typography.Text type="secondary">{TEXT.stock.loading}</Typography.Text>
        </div>
      ) : page.items.length === 0 && !loading ? (
        catalog.search ? (
          <Empty description={<EmptyText title={TEXT.stock.noneFoundTitle} text={TEXT.stock.noneFoundDescription} />}>
            <Button onClick={clear}>{TEXT.clearFilters}</Button>
          </Empty>
        ) : (
          <Empty description={<EmptyText title={TEXT.listing.emptyTitle} text={TEXT.listing.emptyDescription} />}>
            {mayOpenStock && (
              <Button type="link" onClick={onOpenStock}>
                {TEXT.listing.goToStock}
              </Button>
            )}
          </Empty>
        )
      ) : (
        <>
          <Table<ListableItem>
            size="small"
            loading={loading}
            dataSource={page.items}
            rowKey="inventoryId"
            pagination={false}
            columns={[
              { title: TEXT.stock.image, key: 'image', width: 64, render: (_, item) => <ItemThumbnail url={item.imageUrl} size={32} /> },
              { title: TEXT.stock.name, key: 'name', render: (_, item) => <Typography.Text ellipsis={{ tooltip: item.name }}>{item.name}</Typography.Text> },
              { title: TEXT.stock.sku, dataIndex: 'sku', key: 'sku', width: 110 },
              { title: TEXT.stock.quantity, dataIndex: 'availableQuantity', key: 'quantity', width: 70 },
              {
                title: TEXT.stock.actions,
                key: 'choose',
                width: 150,
                render: (_, item) =>
                  item.availableQuantity > 0 ? (
                    <Button size="small" onClick={() => onChoose(item)}>
                      {TEXT.listing.choose}
                    </Button>
                  ) : (
                    <Typography.Text type="secondary">{TEXT.listing.noStockAvailable}</Typography.Text>
                  ),
              },
            ]}
          />
          {!loading && <CursorPager page={page} onPage={catalog.goTo} />}
        </>
      )}
    </section>
  )
}
