import { useRef, useState } from 'react'
import { Alert, Button, Input, Result, Select, Spin, Tag, Typography } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { listItems } from '../../api/items'
import { normalizeOrderNo } from '../../api/logs'
import { FIRST_PAGE, type PageRequest } from '../../api/paging'
import { createTicket, normalizeTicketNo, type Ticket, type TicketType } from '../../api/tickets'
import { useAuth } from '../../auth/context'
import { failureNotice, type Notice } from '../../lib/failure'
import { TEXT } from '../../text'
import { SCREEN_PATH } from './ticketState'

const MAX_ITEMS = 20
const TITLE_MAX = 200
const DESCRIPTION_MAX = 4000

interface ChosenItem {
  id: number
  name: string
  sku: string
}

type FieldErrors = Partial<Record<'type' | 'title' | 'description' | 'items' | 'follows' | 'orderNo', string>>

/**
 * Opening a ticket (Create ticket in docs/frontend/page-design.md; Figma section C), a page of its own so
 * that up to 20 chosen items and a long description stay in view: the items from a list searched by name,
 * the type, the title, and the description, an optional follow-up ticket number, and the order number,
 * required for an order exception. The page checks every field before anything is sent, and a refusal of
 * the server keeps what was typed. When a chosen item no longer exists, the page names it and the item
 * list is read again.
 */
export function CreateTicketPage() {
  const navigate = useNavigate()
  const { refresh } = useAuth()
  const [items, setItems] = useState<ChosenItem[]>([])
  const [type, setType] = useState<TicketType | undefined>()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [follows, setFollows] = useState('')
  const [orderNo, setOrderNo] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [creating, setCreating] = useState(false)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [goneIds, setGoneIds] = useState<number[]>([])
  const [created, setCreated] = useState<Ticket | null>(null)
  const [pickerKey, setPickerKey] = useState(0)

  const orderRequired = type === 'ORDER_EXCEPTION'

  function check(): FieldErrors {
    const found: FieldErrors = {}
    if (!type) found.type = TEXT.tickets.chooseType
    const trimmedTitle = title.trim()
    if (!trimmedTitle) found.title = TEXT.tickets.enterTitle
    else if (trimmedTitle.length > TITLE_MAX) found.title = TEXT.tickets.titleTooLong
    const trimmedDescription = description.trim()
    if (!trimmedDescription) found.description = TEXT.tickets.enterDescription
    else if (trimmedDescription.length > DESCRIPTION_MAX) found.description = TEXT.tickets.descriptionTooLong
    if (type === 'INVENTORY_EXCEPTION' && items.length === 0) found.items = TEXT.tickets.chooseItem
    if (follows.trim() && !normalizeTicketNo(follows)) found.follows = TEXT.tickets.ticketNumberInvalid
    if (orderNo.trim() && !normalizeOrderNo(orderNo)) found.orderNo = TEXT.tickets.orderNumberInvalid
    else if (orderRequired && !orderNo.trim()) found.orderNo = TEXT.tickets.enterOrder
    return found
  }

  async function submit() {
    const found = check()
    setErrors(found)
    setNotice(null)
    if (Object.keys(found).length > 0) return
    setCreating(true)
    try {
      const ticket = await createTicket({
        type: type!,
        title: title.trim(),
        description: description.trim(),
        inventoryIds: items.map((item) => item.id),
        // The server writes the number as the numbers are made, whatever case was typed.
        orderNo: orderNo.trim() ? orderNo.trim() : null,
        followsTicketNo: follows.trim() ? normalizeTicketNo(follows) : null,
      })
      setCreated(ticket)
    } catch (error) {
      refused(error)
    } finally {
      setCreating(false)
    }
  }

  function refused(error: unknown) {
    const code = error instanceof ApiError ? error.code : null
    if (code === 'FOLLOW_NOT_FOUND') setErrors({ follows: TEXT.tickets.followNotFound })
    else if (code === 'FOLLOW_NOT_ALLOWED') setErrors({ follows: TEXT.tickets.followNotAllowed })
    else if (code === 'ORDER_NOT_FOUND') setErrors({ orderNo: TEXT.tickets.orderNotFound })
    else if (code === 'ITEM_CHANGED' && error instanceof ApiError) {
      const gone = Array.isArray(error.extras.items) ? (error.extras.items as { inventoryId: number; name: string | null }[]) : []
      setGoneIds(gone.map((item) => item.inventoryId))
      const names = gone.map((item) => TEXT.tickets.itemGone(item.name ?? items.find((chosen) => chosen.id === item.inventoryId)?.name ?? '')).join(' ')
      setNotice({ type: 'warning', message: names || TEXT.tickets.someItemGone })
    } else if (code === 'LIMIT_REACHED') setNotice({ type: 'warning', message: TEXT.tickets.youAreOpenFull })
    else if (code === 'VALIDATION_FAILED' && error instanceof ApiError) {
      const fields = error.errors.map((field) => field.field)
      setErrors({
        ...(fields.includes('title') ? { title: TEXT.tickets.refusedTitle } : {}),
        ...(fields.includes('description') ? { description: TEXT.tickets.descriptionTooLong } : {}),
        ...(fields.includes('inventoryIds') ? { items: TEXT.tickets.chooseItem } : {}),
        ...(fields.includes('orderNo') ? { orderNo: TEXT.tickets.enterOrder } : {}),
      })
      if (fields.length === 0) setNotice({ type: 'error', message: TEXT.tickets.refusedTitle })
    } else if (code === 'FORBIDDEN') {
      refresh().catch(() => {})
      setNotice({ type: 'error', message: TEXT.tickets.mayNotCreate })
    } else setNotice(failureNotice(error))
  }

  function reloadItems() {
    setItems((chosen) => chosen.filter((item) => !goneIds.includes(item.id)))
    setGoneIds([])
    setPickerKey((key) => key + 1)
    setNotice({ type: 'info', message: TEXT.tickets.itemsReloaded })
  }

  if (created) {
    return (
      <Result
        status="success"
        title={TEXT.tickets.opened(created.ticketNo)}
        extra={[
          <Button key="view" type="primary" onClick={() => navigate(`/tickets/${created.id}`)}>{TEXT.tickets.viewTicket}</Button>,
          <Button key="pool" onClick={() => navigate(SCREEN_PATH.OPEN)}>{TEXT.tickets.backTo(TEXT.nav.pool)}</Button>,
        ]}
      />
    )
  }

  const titleLength = title.trim().length
  const descriptionLength = description.trim().length
  return (
    <>
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link to={SCREEN_PATH.OPEN}>{TEXT.nav.tickets} / {TEXT.nav.pool}</Link> / {TEXT.tickets.createTitle}
      </nav>
      <Typography.Title level={1} className="page-header__title">{TEXT.tickets.createTitle}</Typography.Title>
      <Typography.Paragraph type="secondary">{TEXT.tickets.usageRule}</Typography.Paragraph>

      <section className="panel create-ticket__section" aria-label={TEXT.tickets.warehouseItems}>
        <Typography.Title level={2} className="ticket-detail__title">{TEXT.tickets.warehouseItems}</Typography.Title>
        <Typography.Text type="secondary">{TEXT.tickets.itemsHint}</Typography.Text>
        {items.length > 0 && (
          <div className="chosen-items">
            {items.map((item) => (
              <Tag
                key={item.id}
                closable={!creating}
                color={goneIds.includes(item.id) ? 'error' : undefined}
                onClose={(event) => { event.preventDefault(); setItems((chosen) => chosen.filter((other) => other.id !== item.id)) }}
                closeIcon={<span aria-label={TEXT.tickets.removeItem(item.name)}>×</span>}
              >
                {item.name} · {item.sku}
              </Tag>
            ))}
          </div>
        )}
        <ItemChooser
          key={pickerKey}
          chosen={items}
          disabled={creating}
          error={errors.items}
          onPick={(item) => {
            if (items.length >= MAX_ITEMS) {
              setErrors({ ...errors, items: TEXT.tickets.tooManyItems })
              return
            }
            setErrors({ ...errors, items: undefined })
            setItems([...items, item])
          }}
        />
      </section>

      <section className="panel create-ticket__section" aria-label={TEXT.tickets.ticketDetails}>
        <Typography.Title level={2} className="ticket-detail__title">{TEXT.tickets.ticketDetails}</Typography.Title>
        <div className="adjust-field">
          <label htmlFor="ticket-type">{TEXT.tickets.typeRequired}</label>
          <Select<TicketType>
            id="ticket-type"
            aria-label={TEXT.tickets.typeRequired}
            placeholder={TEXT.tickets.selectType}
            value={type}
            disabled={creating}
            status={errors.type ? 'error' : undefined}
            options={(['ORDER_EXCEPTION', 'INVENTORY_EXCEPTION', 'OTHER'] as const).map((value) => ({ value, label: TEXT.tickets.types[value] }))}
            onChange={(value) => { setType(value); setErrors({ ...errors, type: undefined, items: undefined, orderNo: undefined }) }}
          />
          {errors.type && <div className="field-error field-error--inline" role="alert">{errors.type}</div>}
        </div>
        <div className="adjust-field">
          <label htmlFor="ticket-title">{TEXT.tickets.titleRequired}</label>
          <Input
            id="ticket-title"
            placeholder={TEXT.tickets.titlePlaceholder}
            value={title}
            disabled={creating}
            status={errors.title || titleLength > TITLE_MAX ? 'error' : undefined}
            onChange={(event) => { setTitle(event.target.value); setErrors({ ...errors, title: undefined }) }}
          />
          {(errors.title || titleLength > TITLE_MAX) && (
            <div className="field-error field-error--inline" role="alert">{errors.title ?? TEXT.tickets.titleTooLong}</div>
          )}
        </div>
        <div className="adjust-field">
          <label htmlFor="ticket-description">{TEXT.tickets.descriptionRequired}</label>
          <Input.TextArea
            id="ticket-description"
            rows={6}
            placeholder={TEXT.tickets.descriptionPlaceholder}
            value={description}
            disabled={creating}
            status={errors.description || descriptionLength > DESCRIPTION_MAX ? 'error' : undefined}
            onChange={(event) => { setDescription(event.target.value); setErrors({ ...errors, description: undefined }) }}
          />
          <Typography.Text type="secondary" className="field-counter">{TEXT.tickets.counter(descriptionLength, DESCRIPTION_MAX)}</Typography.Text>
          {(errors.description || descriptionLength > DESCRIPTION_MAX) && (
            <div className="field-error field-error--inline" role="alert">{errors.description ?? TEXT.tickets.descriptionTooLong}</div>
          )}
        </div>
      </section>

      <section className="panel create-ticket__section" aria-label={TEXT.tickets.relatedRecords}>
        <Typography.Title level={2} className="ticket-detail__title">{TEXT.tickets.relatedRecords}</Typography.Title>
        <div className="adjust-field">
          <label htmlFor="ticket-follows">{TEXT.tickets.followUpOptional}</label>
          <Input
            id="ticket-follows"
            placeholder={TEXT.tickets.ticketNumberPlaceholder}
            value={follows}
            maxLength={8}
            disabled={creating}
            status={errors.follows ? 'error' : undefined}
            onChange={(event) => { setFollows(event.target.value.toUpperCase()); setErrors({ ...errors, follows: undefined }) }}
          />
          {errors.follows && <div className="field-error field-error--inline" role="alert">{errors.follows}</div>}
        </div>
        <div className="adjust-field">
          <label htmlFor="ticket-order">{orderRequired ? TEXT.tickets.orderRequired : TEXT.tickets.orderOptional}</label>
          <Input
            id="ticket-order"
            placeholder={TEXT.tickets.orderNumberPlaceholder}
            value={orderNo}
            maxLength={64}
            disabled={creating}
            status={errors.orderNo ? 'error' : undefined}
            onChange={(event) => { setOrderNo(event.target.value); setErrors({ ...errors, orderNo: undefined }) }}
          />
          {errors.orderNo && <div className="field-error field-error--inline" role="alert">{errors.orderNo}</div>}
        </div>
      </section>

      {notice && (
        <Alert
          type={notice.type}
          showIcon
          message={notice.message}
          role="alert"
          className="page-alert"
          action={goneIds.length > 0 ? <Button size="small" onClick={reloadItems}>{TEXT.tickets.reloadItems}</Button> : undefined}
        />
      )}
      <div className="form-actions">
        <Button onClick={() => navigate(SCREEN_PATH.OPEN)} disabled={creating}>{TEXT.cancel}</Button>
        <Button type="primary" loading={creating} onClick={() => void submit()}>{creating ? TEXT.tickets.creating : TEXT.tickets.createTicket}</Button>
      </div>
    </>
  )
}

/** The searchable list of the warehouse items that are not deleted, by name, with the SKU beside it. */
function ItemChooser({ chosen, onPick, disabled, error }: { chosen: ChosenItem[]; onPick: (item: ChosenItem) => void; disabled: boolean; error?: string }) {
  const [options, setOptions] = useState<ChosenItem[]>([])
  const [next, setNext] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const search = useRef('')
  const latest = useRef(0)

  async function fetchPage(text: string, page: PageRequest, append: boolean) {
    const call = ++latest.current
    setLoading(true)
    try {
      const found = await listItems(text, page)
      if (call !== latest.current) return
      const rows = found.items.map((item) => ({ id: item.id, name: item.name, sku: item.sku }))
      setOptions((shown) => (append ? [...shown, ...rows] : rows))
      setNext(found.nextCursor)
    } catch {
      if (call === latest.current && !append) setOptions([])
    } finally {
      if (call === latest.current) setLoading(false)
    }
  }

  return (
    <div className="adjust-field">
      <label htmlFor="ticket-items">{TEXT.tickets.warehouseItem}</label>
      <Select<number>
        id="ticket-items"
        aria-label={TEXT.tickets.warehouseItem}
        showSearch
        value={null as unknown as number}
        placeholder={TEXT.tickets.searchItems}
        filterOption={false}
        disabled={disabled}
        status={error ? 'error' : undefined}
        notFoundContent={loading ? <Spin size="small" /> : TEXT.logs.noMatchingItems}
        options={options.filter((option) => !chosen.some((item) => item.id === option.id)).map((option) => ({ value: option.id, label: `${option.name} · ${option.sku}` }))}
        onDropdownVisibleChange={(open) => {
          if (open) void fetchPage(search.current, FIRST_PAGE, false)
        }}
        onSearch={(text) => {
          search.current = text
          void fetchPage(text, FIRST_PAGE, false)
        }}
        onPopupScroll={(event) => {
          const list = event.currentTarget as HTMLElement
          if (next && !loading && list.scrollTop + list.clientHeight >= list.scrollHeight - 8) {
            void fetchPage(search.current, { cursor: next, direction: 'next' }, true)
          }
        }}
        onChange={(id) => {
          const option = options.find((candidate) => candidate.id === id)
          if (option) onPick(option)
        }}
      />
      {error && <div className="field-error field-error--inline" role="alert">{error}</div>}
    </div>
  )
}
