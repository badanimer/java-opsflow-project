import { useState } from 'react'
import { Alert, Button, Checkbox, Modal, Typography } from 'antd'
import { ApiError } from '../../api/client'
import { replaceRoles, type ListedUser } from '../../api/users'
import { useAuth } from '../../auth/context'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { isProtectedRole, roleLabel, sortRoles } from '../../lib/roles'
import { TEXT } from '../../text'
import type { Notice } from '../../lib/failure'
import { RoleTags } from './UserTags'
import { assignableRoles, changeFailure, type Outcome } from './userRules'

interface Props {
  user: ListedUser
  /** The roles were saved. */
  onSaved: () => void
  /** A refusal that belongs to the page: the dialog is closed and the page shows the notice. */
  onRefused: (outcome: Outcome) => void
  /** The user turned out to differ from the row: the list is loaded again behind the dialog. */
  onStale: () => void
  onClose: () => void
}

/**
 * Change roles: the person picks the whole new set, and Save sends it with the set they saw. A second
 * confirmation comes after Save when the change gives or takes away Administrator or Manager, or leaves
 * no role (Second Confirmation in docs/frontend/page-design.md); any other change is saved at once.
 *
 * A disabled user can lose roles but cannot be given one: the roles the user does not hold are offered
 * disabled, with the reason under them.
 */
export function ChangeRolesDialog({ user, onSaved, onRefused, onStale, onClose }: Props) {
  const { account } = useAuth()
  const original = user.roles
  const [selected, setSelected] = useState<string[]>(original)
  const [disabledUser, setDisabledUser] = useState(user.status === 'DISABLED')
  const [step, setStep] = useState<'edit' | 'confirm'>('edit')
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [notActive, setNotActive] = useState(false)

  const options = assignableRoles(account)
  const added = selected.filter((code) => !original.includes(code))
  const removed = original.filter((code) => !selected.includes(code))
  const changed = added.length > 0 || removed.length > 0
  const leavesNoRole = selected.length === 0
  const touchesProtected = [...added, ...removed].some(isProtectedRole)

  async function submit() {
    setSaving(true)
    setNotice(null)
    try {
      await replaceRoles(user.id, sortRoles(selected), original)
      onSaved()
    } catch (error) {
      setSaving(false)
      setStep('edit')
      if (error instanceof ApiError && error.code === 'USER_NOT_ACTIVE') {
        // The user was disabled meanwhile: only removal is possible now.
        setDisabledUser(true)
        setNotActive(true)
        setSelected((current) => current.filter((code) => original.includes(code)))
        onStale()
        return
      }
      const outcome = changeFailure(error)
      if (outcome.where === 'page') onRefused(outcome)
      else setNotice(outcome.notice)
    }
  }

  function save() {
    if (leavesNoRole || touchesProtected) setStep('confirm')
    else void submit()
  }

  if (step === 'confirm') {
    const lines = leavesNoRole
      ? removed.filter(isProtectedRole).map((code) => TEXT.users.removes(roleLabel(code)))
      : [...sortRoles(added).map((code) => TEXT.users.adds(roleLabel(code))), ...sortRoles(removed).map((code) => TEXT.users.removes(roleLabel(code)))]
    return (
      <ConfirmDialog
        open
        title={leavesNoRole ? TEXT.users.confirmNoRoleTitle(user.username) : TEXT.users.confirmRoleChangeTitle(user.username)}
        confirmLabel={leavesNoRole ? TEXT.users.removeRoles : TEXT.users.changeRoles}
        runningLabel={TEXT.users.saving}
        cancelLabel={TEXT.users.cancel}
        danger={leavesNoRole}
        running={saving}
        onConfirm={() => void submit()}
        onCancel={() => setStep('edit')}
      >
        {leavesNoRole && <Typography.Paragraph className="dialog-text">{TEXT.users.confirmNoRoleDescription(user.username)}</Typography.Paragraph>}
        {lines.map((line) => (
          <Typography.Paragraph key={line} className="dialog-text" type={line.startsWith('Removes') ? 'danger' : undefined}>
            {line}
          </Typography.Paragraph>
        ))}
      </ConfirmDialog>
    )
  }

  return (
    <Modal
      open
      title={<span className="dialog-title">{TEXT.users.changeRolesTitle(user.username)}</span>}
      closable={false}
      maskClosable={false}
      keyboard={!saving}
      onCancel={saving ? undefined : onClose}
      destroyOnClose
      footer={[
        <Button key="cancel" onClick={onClose} disabled={saving}>
          {TEXT.users.cancel}
        </Button>,
        <Button key="save" type="primary" onClick={save} disabled={!changed} loading={saving}>
          {saving ? TEXT.users.saving : TEXT.users.save}
        </Button>,
      ]}
    >
      <div className="dialog-body">
        <div>
          <Typography.Text strong>{TEXT.users.currentRoles}</Typography.Text>
          <div className="dialog-tags">
            <RoleTags roles={original} />
          </div>
        </div>
        <fieldset className="role-choice" disabled={saving}>
          <legend>
            <Typography.Text strong>{TEXT.users.newRoles}</Typography.Text>
          </legend>
          <Checkbox.Group
            value={selected}
            onChange={(values) => setSelected(values as string[])}
            options={options.map((role) => ({
              value: role.code,
              label: role.label,
              disabled: disabledUser && !original.includes(role.code),
            }))}
            className="role-choice__options"
          />
        </fieldset>
        {disabledUser && !notActive && <Typography.Text type="secondary">{TEXT.users.onlyRemoveFromDisabled}</Typography.Text>}
        {notActive && (
          <Alert
            type="warning"
            showIcon
            role="alert"
            message={TEXT.users.onlyRemoveFromDisabled}
            description={TEXT.users.enableBeforeAdding}
          />
        )}
        {notice && <Alert type={notice.type} showIcon message={notice.message} role="alert" />}
      </div>
    </Modal>
  )
}
