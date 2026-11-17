import { Tag } from 'antd'
import type { UserStatus } from '../../api/users'
import { isProtectedRole, roleLabel, sortRoles } from '../../lib/roles'
import { TEXT } from '../../text'

/** Active, Disabled, or Deleted. The word is always there, so the colour is never the only sign. */
export function StatusTag({ status }: { status: UserStatus }) {
  if (status === 'DELETED') return <Tag>{TEXT.users.deleted}</Tag>
  return status === 'ACTIVE' ? <Tag color="success">{TEXT.users.active}</Tag> : <Tag color="error">{TEXT.users.disabled}</Tag>
}

/** The roles of a user, or No role for a user who waits for one. */
export function RoleTags({ roles }: { roles: readonly string[] }) {
  if (roles.length === 0) return <Tag color="warning">{TEXT.users.noRole}</Tag>
  return (
    <>
      {sortRoles(roles).map((code) => (
        <Tag key={code} color={isProtectedRole(code) ? 'blue' : undefined}>
          {roleLabel(code)}
        </Tag>
      ))}
    </>
  )
}

/** The mark of the viewer's own row. */
export function YouTag() {
  return <Tag color="processing">{TEXT.users.you}</Tag>
}
