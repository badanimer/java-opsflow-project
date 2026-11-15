import { useState } from 'react'
import { Alert, Button, Empty, Grid, Input, List, Space, Spin, Table, Tag, Typography } from 'antd'
import { ApiError } from '../../api/client'
import { deleteItem, listItems, type Item } from '../../api/items'
import { useAuth } from '../../auth/context'
import { can } from '../../auth/permissions'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { CursorPager } from '../../components/CursorPager'
import { ItemThumbnail } from '../../components/ItemThumbnail'
import { EmptyText, ListFailure } from '../../components/ListStates'
import { PageHeader } from '../../components/PageHeader'
import { usePagedList } from '../../hooks/usePagedList'
import { TEXT } from '../../text'
import { failureNotice, type Notice } from '../../lib/failure'
import { AdjustQuantityDialog } from './AdjustQuantityDialog'
import { ItemDialog } from './ItemDialog'

const SEARCH_MAX = 200

/** The deletion in the dialog: running, or ended by a refusal that leaves only Close. */
interface Deletion {
  item: Item
  running: boolean
  notice: Notice | null
  finished: boolean
}

/**
 * The stock page, Phase 4 part (Stock page in docs/frontend/page-design.md): every item that is not
 * deleted, by name, with a search and paging by key. Each row shows the picture, the name, the SKU,
 * the stock, and whether the item is on sale. A manager or an administrator adds an item, edits one that
 * is not on sale (ItemDialog), and deletes one after a second confirmation. The quantity changes and the logs
 * come in Phase 5, in the space the Actions column leaves.
 */
export function StockPage() {
  const { account, refresh } = useAuth()
  const small = Grid.useBreakpoint().md === false
  const list = usePagedList(listItems)
  const [searchText, setSearchText] = useState('')
  const [notice, setNotice] = useState<Notice | null>(null)
  const [deletion, setDeletion] = useState<Deletion | null>(null)
  /** The Add or Edit dialog: `null` item adds one. */
  const [editing, setEditing] = useState<{ item: Item | null } | null>(null)

  const [adjusting, setAdjusting] = useState<Item | null>(null)

  const mayManage = can(account, 'ITEM_MANAGE')
  const mayDelete = can(account, 'ITEM_DELETE')
  const mayAdjust = can(account, 'STOCK_IN') || can(account, 'STOCK_WRITE_OFF') || can(account, 'STOCK_CORRECT')
  const showActions = mayManage || mayDelete || mayAdjust

  /** Reads the page shown again, as the dialog of a quantity change asks, and returns the item from it. */
  async function reloadItem(id: number): Promise<Item | null> {
    const fresh = await listItems(list.search, list.request)
    list.reload()
    return fresh.items.find((item) => item.id === id) ?? null
  }

  function clearSearch() {
    setSearchText('')
    list.applySearch('')
  }

  async function confirmDelete() {
    if (!deletion) return
    const item = deletion.item
    setDeletion({ item, running: true, notice: null, finished: false })
    try {
      await deleteItem(item.id)
      setDeletion(null)
      setNotice({ type: 'success', message: TEXT.stock.itemDeleted })
      list.reload()
    } catch (error) {
      if (error instanceof ApiError && error.code === 'NOT_FOUND') {
        setDeletion({ item, running: false, notice: { type: 'warning', message: TEXT.stock.itemGone }, finished: true })
      } else if (error instanceof ApiError && error.code === 'FORBIDDEN') {
        refresh().catch(() => {})
        setDeletion({ item, running: false, notice: { type: 'error', message: TEXT.noLongerAllowed }, finished: true })
      } else {
        setDeletion({ item, running: false, notice: failureNotice(error), finished: false })
      }
    }
  }

  function closeDeletion() {
    const ended = deletion?.finished
    setDeletion(null)
    if (ended) list.reload()
  }

  function statusOf(item: Item) {
    return (
      <div className="listing-status">
        {item.listed ? <Tag color="success">{TEXT.stock.onSale}</Tag> : <Tag>{TEXT.stock.notOnSale}</Tag>}
        {item.listed && mayManage && (
          <Typography.Text type="secondary" className="listing-status__note">
            {TEXT.stock.delistToEdit}
          </Typography.Text>
        )}
      </div>
    )
  }

  function actionsOf(item: Item) {
    return (
      <Space size={4} wrap className="row-actions">
        {mayManage && !item.listed && (
          <Button type="link" size="small" onClick={() => { setNotice(null); setEditing({ item }) }}>
            {TEXT.stock.edit}
          </Button>
        )}
        {mayDelete && (
          <Button type="link" size="small" danger onClick={() => { setNotice(null); setDeletion({ item, running: false, notice: null, finished: false }) }}>
            {TEXT.stock.delete}
          </Button>
        )}
        {mayAdjust && (
          <Button size="small" onClick={() => { setNotice(null); setAdjusting(item) }}>
            {TEXT.adjust.button}
          </Button>
        )}
      </Space>
    )
  }

  function nameOf(item: Item) {
    return (
      <Typography.Text ellipsis={{ tooltip: item.name }} className="item-name">
        {item.name}
      </Typography.Text>
    )
  }

  const { page, loading, error } = list
  return (
    <>
      <PageHeader
        title={TEXT.stock.title}
        actions={
          mayManage && (
            <Button type="primary" onClick={() => { setNotice(null); setEditing({ item: null }) }}>
              {TEXT.stock.addItem}
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

      <section className="panel panel--list" aria-label={TEXT.stock.listLabel}>
        {error ? (
          <ListFailure error={error} onRetry={() => { if (error instanceof ApiError && error.code === 'FORBIDDEN') refresh().catch(() => {}); list.reload() }} onClear={clearSearch} />
        ) : !page ? (
          <div className="list-loading" aria-busy="true">
            <Spin />
            <Typography.Text type="secondary">{TEXT.stock.loading}</Typography.Text>
          </div>
        ) : page.items.length === 0 && !loading ? (
          list.search ? (
            <Empty description={<EmptyText title={TEXT.stock.noneFoundTitle} text={TEXT.stock.noneFoundDescription} />}>
              <Button onClick={clearSearch}>{TEXT.clearFilters}</Button>
            </Empty>
          ) : (
            <Empty description={<EmptyText title={TEXT.stock.emptyTitle} text={mayManage ? TEXT.stock.emptyManage : TEXT.stock.emptyRead} />} />
          )
        ) : small ? (
          <List
            loading={loading}
            dataSource={page.items}
            rowKey="id"
            renderItem={(item) => (
              <List.Item className="stock-item">
                <ItemThumbnail url={item.imageUrl} size={48} />
                <div className="stock-item__body">
                  {nameOf(item)}
                  <Typography.Text type="secondary">{TEXT.stock.skuAndQuantity(item.sku, item.availableQuantity)}</Typography.Text>
                  {statusOf(item)}
                  {showActions && actionsOf(item)}
                </div>
              </List.Item>
            )}
          />
        ) : (
          <Table<Item>
            loading={loading}
            dataSource={page.items}
            rowKey="id"
            pagination={false}
            columns={[
              { title: TEXT.stock.image, key: 'image', width: 72, render: (_, item) => <ItemThumbnail url={item.imageUrl} /> },
              { title: TEXT.stock.name, key: 'name', render: (_, item) => nameOf(item) },
              { title: TEXT.stock.sku, dataIndex: 'sku', key: 'sku', width: 120 },
              { title: TEXT.stock.quantity, dataIndex: 'availableQuantity', key: 'quantity', width: 90 },
              { title: TEXT.stock.listingStatus, key: 'status', width: 220, render: (_, item) => statusOf(item) },
              ...(showActions ? [{ title: TEXT.stock.actions, key: 'actions', width: 280, render: (_: unknown, item: Item) => actionsOf(item) }] : []),
            ]}
          />
        )}
        {page && !error && !loading && <CursorPager page={page} onPage={list.goTo} />}
      </section>

      {adjusting && (
        <AdjustQuantityDialog
          item={adjusting}
          reloadItem={() => reloadItem(adjusting.id)}
          onSaved={(message) => {
            setAdjusting(null)
            setNotice({ type: 'success', message })
            list.reload()
          }}
          onClose={(stale) => {
            setAdjusting(null)
            if (stale) list.reload()
          }}
        />
      )}

      {editing && (
        <ItemDialog
          item={editing.item}
          onSaved={(message) => {
            setEditing(null)
            setNotice({ type: 'success', message })
            list.reload()
          }}
          onClose={(stale) => {
            setEditing(null)
            if (stale) list.reload()
          }}
        />
      )}

      <ConfirmDialog
        open={deletion !== null}
        title={deletion ? TEXT.stock.deleteTitle(deletion.item.name) : ''}
        confirmLabel={TEXT.stock.deleteItem}
        runningLabel={TEXT.stock.deleting}
        cancelLabel={TEXT.cancel}
        closeLabel={TEXT.close}
        danger
        running={deletion?.running ?? false}
        notice={deletion?.notice}
        finished={deletion?.finished}
        onConfirm={() => void confirmDelete()}
        onCancel={closeDeletion}
      >
        <Typography.Paragraph className="dialog-text">
          {deletion?.item.listed ? TEXT.stock.deleteOnSale : TEXT.stock.deleteNotOnSale}
        </Typography.Paragraph>
      </ConfirmDialog>
    </>
  )
}
