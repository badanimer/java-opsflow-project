import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Grid, Input, List, Space, Spin, Table, Typography } from 'antd'
import { ApiError, NetworkError } from '../../api/client'
import { FIRST_PAGE, type Page, type PageRequest } from '../../api/paging'
import { deleteUser, disableUser, enableUser, listUsers, type ListedUser, type UserFilter } from '../../api/users'
import { useAuth } from '../../auth/context'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { CursorPager } from '../../components/CursorPager'
import { PageHeader } from '../../components/PageHeader'
import { TEXT } from '../../text'
import { generalFailure, type Notice } from '../../lib/failure'
import { ChangeRolesDialog } from './ChangeRolesDialog'
import { RoleTags, StatusTag, YouTag } from './UserTags'
import { changeFailure, filterOptions, hasActionColumn, rowActions, type Outcome } from './userRules'

const NO_FILTER: UserFilter = { search: '', roles: [] }
const SEARCH_MAX = 64

type Dialog =
  | { kind: 'roles'; user: ListedUser }
  | { kind: 'disable'; user: ListedUser }
  | { kind: 'delete'; user: ListedUser }

/**
 * The user page (User page in docs/frontend/page-design.md). It lists the users the viewer may manage,
 * A to Z, 10 a page, with a search for a part of the username and role buttons of which several can be
 * chosen. The server decides which users are listed. Each row offers only the changes the viewer's
 * permissions allow, and none on the viewer's own row.
 *
 * After every change, and after every refusal that says the row was out of date, the page loads the
 * same page of the list again with the same filters, so what is shown is what the server holds.
 */
export function UsersPage() {
  const { account, refresh } = useAuth()
  const small = Grid.useBreakpoint().md === false

  const [searchText, setSearchText] = useState('')
  const [filter, setFilter] = useState<UserFilter>(NO_FILTER)
  const [request, setRequest] = useState<PageRequest>(FIRST_PAGE)
  const [reloads, setReloads] = useState(0)
  const [page, setPage] = useState<Page<ListedUser> | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<unknown>(null)

  const [notice, setNotice] = useState<Notice | null>(null)
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const [running, setRunning] = useState(false)
  const [dialogNotice, setDialogNotice] = useState<Notice | null>(null)
  const [enabling, setEnabling] = useState<number | null>(null)

  useEffect(() => {
    let current = true
    setLoading(true)
    listUsers(filter, request)
      .then((loaded) => {
        if (!current) return
        // A page that was emptied, for example by a deletion, falls back to the first page.
        if (loaded.items.length === 0 && request.cursor) {
          setRequest(FIRST_PAGE)
          return
        }
        setPage(loaded)
        setLoadError(null)
        setLoading(false)
      })
      .catch((error: unknown) => {
        if (!current) return
        setLoadError(error)
        setLoading(false)
      })
    return () => {
      current = false
    }
  }, [filter, request, reloads])

  const reload = useCallback(() => setReloads((count) => count + 1), [])

  function applyFilter(next: UserFilter) {
    setFilter(next)
    setRequest(FIRST_PAGE)
  }

  function clearFilters() {
    setSearchText('')
    applyFilter(NO_FILTER)
  }

  function toggleRole(code: string, checked: boolean) {
    applyFilter({ ...filter, roles: checked ? [...filter.roles, code] : filter.roles.filter((role) => role !== code) })
  }

  /** A refusal that closes the dialog: the page says why and shows the list as it is now. */
  function refusedOnPage(outcome: Outcome) {
    setDialog(null)
    setDialogNotice(null)
    setNotice(outcome.notice)
    if (outcome.reloadAccount) refresh().catch(() => {})
    reload()
  }

  function done(message: string) {
    setDialog(null)
    setDialogNotice(null)
    setNotice({ type: 'success', message })
    reload()
  }

  async function runConfirmed(action: () => Promise<unknown>, message: string) {
    setRunning(true)
    setDialogNotice(null)
    try {
      await action()
      done(message)
    } catch (error) {
      const outcome = changeFailure(error)
      if (outcome.where === 'page') refusedOnPage(outcome)
      else setDialogNotice(outcome.notice)
    } finally {
      setRunning(false)
    }
  }

  /** Enable gives back what was there and Disable undoes it, so it runs at once, without a confirmation. */
  async function enable(user: ListedUser) {
    setEnabling(user.id)
    setNotice(null)
    try {
      await enableUser(user.id)
      done(TEXT.users.userEnabled)
    } catch (error) {
      const outcome = changeFailure(error)
      if (outcome.where === 'page') refusedOnPage(outcome)
      else setNotice(outcome.notice)
    } finally {
      setEnabling(null)
    }
  }

  function openDialog(next: Dialog) {
    setNotice(null)
    setDialogNotice(null)
    setDialog(next)
  }

  const showActions = hasActionColumn(account)
  const filtered = filter.search.trim() !== '' || filter.roles.length > 0

  function actionsOf(user: ListedUser) {
    const allowed = rowActions(account, user)
    if (enabling === user.id) {
      return (
        <Button type="link" size="small" loading>
          {TEXT.users.enabling}
        </Button>
      )
    }
    return (
      <Space size={4} wrap className="row-actions">
        {allowed.changeRoles && (
          <Button type="link" size="small" onClick={() => openDialog({ kind: 'roles', user })}>
            {TEXT.users.changeRoles}
          </Button>
        )}
        {allowed.changeStatus &&
          (user.status === 'ACTIVE' ? (
            <Button type="link" size="small" onClick={() => openDialog({ kind: 'disable', user })}>
              {TEXT.users.disable}
            </Button>
          ) : (
            <Button type="link" size="small" onClick={() => void enable(user)} disabled={enabling !== null}>
              {TEXT.users.enable}
            </Button>
          ))}
        {allowed.delete && (
          <Button type="link" size="small" danger onClick={() => openDialog({ kind: 'delete', user })}>
            {TEXT.users.delete}
          </Button>
        )}
      </Space>
    )
  }

  function usernameOf(user: ListedUser) {
    return (
      <span className="username-cell">
        <Typography.Text ellipsis={{ tooltip: user.username }} className="username-cell__name">
          {user.username}
        </Typography.Text>
        {user.id === account?.id && <YouTag />}
      </span>
    )
  }

  return (
    <>
      <PageHeader title={TEXT.users.title} />

      <section className="panel filter-bar" aria-label="Filters">
        <Input.Search
          placeholder={TEXT.users.searchPlaceholder}
          aria-label={TEXT.users.searchPlaceholder}
          value={searchText}
          maxLength={SEARCH_MAX}
          allowClear
          onChange={(event) => {
            setSearchText(event.target.value)
            if (event.target.value === '' && filter.search !== '') applyFilter({ ...filter, search: '' })
          }}
          onSearch={(value) => applyFilter({ ...filter, search: value })}
          className="filter-bar__search"
        />
        <div className="filter-bar__toggles" role="group" aria-label={TEXT.users.roles}>
          {filterOptions(account).map((option) => {
            const checked = filter.roles.includes(option.value)
            return (
              <Button
                key={option.value}
                type={checked ? 'primary' : 'default'}
                ghost={checked}
                aria-pressed={checked}
                onClick={() => toggleRole(option.value, !checked)}
              >
                {option.label}
              </Button>
            )
          })}
        </div>
      </section>

      {notice && <Alert type={notice.type} showIcon message={notice.message} closable onClose={() => setNotice(null)} role="alert" className="page-notice" />}

      <section className="panel panel--list" aria-label={TEXT.users.listLabel}>
        {loadError ? (
          <LoadFailure error={loadError} onRetry={reload} onClearFilters={clearFilters} />
        ) : !page ? (
          <div className="list-loading" aria-busy="true">
            <Spin />
            <Typography.Text type="secondary">{TEXT.users.loading}</Typography.Text>
          </div>
        ) : page.items.length === 0 && !loading ? (
          filtered ? (
            <Empty description={<EmptyText title={TEXT.users.noneFoundTitle} text={TEXT.users.noneFoundDescription} />}>
              <Button onClick={clearFilters}>{TEXT.clearFilters}</Button>
            </Empty>
          ) : (
            <Empty description={<EmptyText title={TEXT.users.emptyTitle} text={TEXT.users.emptyDescription} />} />
          )
        ) : small ? (
          <List
            loading={loading}
            dataSource={page.items}
            rowKey="id"
            renderItem={(user) => (
              <List.Item className="user-item">
                <div className="user-item__head">
                  {usernameOf(user)}
                  <StatusTag status={user.status} />
                </div>
                <div>
                  <RoleTags roles={user.roles} />
                </div>
                {showActions && actionsOf(user)}
              </List.Item>
            )}
          />
        ) : (
          <Table<ListedUser>
            loading={loading}
            dataSource={page.items}
            rowKey="id"
            pagination={false}
            columns={[
              { title: TEXT.users.username, key: 'username', render: (_, user) => usernameOf(user), width: '30%' },
              { title: TEXT.users.status, key: 'status', render: (_, user) => <StatusTag status={user.status} />, width: '14%' },
              { title: TEXT.users.roles, key: 'roles', render: (_, user) => <RoleTags roles={user.roles} /> },
              ...(showActions ? [{ title: TEXT.users.actions, key: 'actions', render: (_: unknown, user: ListedUser) => actionsOf(user) }] : []),
            ]}
          />
        )}
        {page && !loadError && !loading && <CursorPager page={page} onPage={setRequest} />}
      </section>

      {dialog?.kind === 'roles' && (
        <ChangeRolesDialog
          user={dialog.user}
          onSaved={() => done(TEXT.users.rolesUpdated)}
          onRefused={refusedOnPage}
          onStale={reload}
          onClose={() => setDialog(null)}
        />
      )}
      <ConfirmDialog
        open={dialog?.kind === 'disable'}
        title={dialog ? TEXT.users.disableTitle(dialog.user.username) : ''}
        confirmLabel={TEXT.users.disable}
        runningLabel={TEXT.users.disabling}
        cancelLabel={TEXT.users.cancel}
        danger
        running={running}
        notice={dialogNotice}
        onConfirm={() => dialog && void runConfirmed(() => disableUser(dialog.user.id), TEXT.users.userDisabled)}
        onCancel={() => setDialog(null)}
      >
        <Typography.Paragraph className="dialog-text">{TEXT.users.disableDescription}</Typography.Paragraph>
      </ConfirmDialog>
      <ConfirmDialog
        open={dialog?.kind === 'delete'}
        title={dialog ? TEXT.users.deleteTitle(dialog.user.username) : ''}
        confirmLabel={TEXT.users.delete}
        runningLabel={TEXT.users.deleting}
        cancelLabel={TEXT.users.cancel}
        danger
        running={running}
        notice={dialogNotice}
        onConfirm={() => dialog && void runConfirmed(() => deleteUser(dialog.user.id), TEXT.users.userDeleted)}
        onCancel={() => setDialog(null)}
      >
        <Typography.Paragraph className="dialog-text">{TEXT.users.deleteDescription}</Typography.Paragraph>
      </ConfirmDialog>
    </>
  )
}

function EmptyText({ title, text }: { title: string; text: string }) {
  return (
    <>
      <strong className="panel__empty-title">{title}</strong>
      <span>{text}</span>
    </>
  )
}

/** A list that could not be loaded: no answer, filters the server refused, or a fault with its reference. */
function LoadFailure({ error, onRetry, onClearFilters }: { error: unknown; onRetry: () => void; onClearFilters: () => void }) {
  if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
    return (
      <div className="list-failure">
        <Alert type="error" showIcon message={TEXT.filtersRefused} role="alert" />
        <Button onClick={onClearFilters}>{TEXT.clearFilters}</Button>
      </div>
    )
  }
  const failure = error instanceof NetworkError ? { type: 'error' as const, message: TEXT.cannotReach } : generalFailure(error)
  return (
    <div className="list-failure">
      <Alert type={failure.type} showIcon message={failure.message} role="alert" />
      <Button onClick={onRetry}>{TEXT.tryAgain}</Button>
    </div>
  )
}
