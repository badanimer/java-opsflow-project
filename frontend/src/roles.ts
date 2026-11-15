/**
 * The roles as the interface names them. The four roles are fixed (docs/frontend/page-design.md), and
 * which role grants which permission is known only to the server. The frontend needs the roles only to
 * show them, to offer them on the user page, and to know which ones are protected: Administrator and
 * Manager, the roles that only a holder of a `_PROTECTED` permission may give, take away, or act on.
 */
export interface RoleInfo {
  code: string
  label: string
  protected: boolean
}

export const ROLES: readonly RoleInfo[] = [
  { code: 'ADMIN', label: 'Administrator', protected: true },
  { code: 'MANAGER', label: 'Manager', protected: true },
  { code: 'OPERATOR', label: 'Operator', protected: false },
  { code: 'VIEWER', label: 'Viewer', protected: false },
]

/** The label of a role code. An unknown code is shown as it is, so a new role still shows up. */
export function roleLabel(code: string): string {
  return ROLES.find((role) => role.code === code)?.label ?? code
}

export function isProtectedRole(code: string): boolean {
  return ROLES.some((role) => role.code === code && role.protected)
}

/** The roles in the order of the list above, which is the order the interface shows them in. */
export function sortRoles(codes: readonly string[]): string[] {
  const rank = (code: string) => {
    const index = ROLES.findIndex((role) => role.code === code)
    return index === -1 ? ROLES.length : index
  }
  return [...codes].sort((a, b) => rank(a) - rank(b) || a.localeCompare(b))
}
