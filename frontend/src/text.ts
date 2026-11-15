/**
 * Every text of the interface, in English. The wording is the one of the Figma design
 * (docs/frontend/page-design.md). A page takes its texts from here and never writes one inline, so a
 * change of wording is made in one place.
 */
export const TEXT = {
  // Field checks, made in the page before anything is sent
  usernameRequired: 'Enter your username.',
  usernameRequiredNew: 'Enter a username.',
  usernameInvalid: 'Use 3 to 64 letters and digits, with no spaces or other characters.',
  usernameHint: '3 to 64 letters and digits',
  emailRequired: 'Enter your email address.',
  emailInvalid: 'Enter a valid email address.',
  codeRequired: 'Enter the code from the email.',
  codeInvalidFormat: 'Enter the 6-digit code from the email.',
  passwordRequired: 'Enter your password.',
  passwordRequiredNew: 'Enter a password.',
  passwordInvalid: 'Use 8 to 24 characters, with at least one letter and one digit.',
  passwordHint: '8 to 24 characters, with at least one letter and one digit',

  // Answers of the server
  wrongCredentials: 'Wrong username or password.',
  locked: 'Too many failed attempts. Please try again in a minute.',
  disabled: 'This account has been disabled. Please contact an administrator.',
  tooManyRequests: 'Too many requests. Please try again in a minute.',
  codeWrong: 'The code is wrong or no longer valid. Please request a new one.',
  usernameTaken: 'This username is already used. Choose another one. Your code is still valid.',
  samePassword: 'The new password must differ from the current one.',
  cannotReach: 'Cannot reach the server. Check your connection and try again.',
  somethingWrong: (requestId?: string) =>
    requestId
      ? `Something went wrong. Please try again. If it keeps happening, report this reference: ${requestId}.`
      : 'Something went wrong. Please try again.',

  // Notices on the login page
  sessionExpired: 'Your session has expired. Please sign in again.',
  signedInElsewhere: 'You were signed out because your account signed in on another device. If this was not you, reset your password.',
  passwordReset: 'Your password has been reset. Sign in with your new password.',

  // Email codes
  signUpCodeSent: (email: string) => `We sent a 6-digit code to ${email}. It is valid for 10 minutes.`,
  resetCodeSent: 'If this email has an account, we sent a 6-digit code to it. It is valid for 10 minutes.',
  sendCode: 'Send code',
  sending: 'Sending…',
  resendIn: (seconds: number) => `Resend in ${seconds}s`,

  // Pending page
  pendingTitle: 'Waiting for an administrator to assign permissions',
  pendingDescription:
    'Your account is active, but it has no role yet. An administrator or a manager will assign one. ' +
    'This page checks again when you refresh it or sign in again.',
  stillNoRole: 'No role has been assigned yet. Please check again later.',
  checkAgain: 'Check again',
  signOut: 'Sign out',

  // A page whose account signed in on another device
  signedOutTitle: 'You have been signed out',
  signedOutReason:
    'Your account signed in on another device, so you were signed out here. If this was not you, sign in again and reset your password.',
  signInAgain: 'Sign in again',

  // The frame of the application
  tryAgain: 'Try again',
  loadingAccount: 'Loading your account…',
  menu: 'Menu',
  close: 'Close',
  nav: {
    products: 'Products',
    stock: 'Stock',
    tickets: 'Tickets',
    pool: 'Pool',
    inProgress: 'In progress',
    resolved: 'Resolved',
    archive: 'Archive',
    cancelled: 'Cancelled',
    users: 'Users',
    auditLog: 'Audit log',
  },
  notAllowedTitle: 'Not allowed',
  notAllowedDescription: 'You do not have permission to open this page.',
  notFoundTitle: 'Page not found',
  notFoundDescription: 'We could not find this page.',
  goToProducts: 'Go to Products',
  productsComingTitle: 'Products are coming soon',
  productsComingDescription: 'The product list will appear here when it is available.',

  // Lists
  previous: 'Previous',
  next: 'Next',
  clearFilters: 'Clear filters',
  filtersRefused: 'We could not apply these filters. Clear the filters and try again.',

  // User page
  users: {
    title: 'Users',
    listLabel: 'User list',
    searchPlaceholder: 'Search by username',
    loading: 'Loading users…',
    username: 'Username',
    status: 'Status',
    roles: 'Roles',
    actions: 'Actions',
    active: 'Active',
    disabled: 'Disabled',
    noRole: 'No role',
    you: 'You',
    noneFoundTitle: 'No users found',
    noneFoundDescription: 'Try a different username or clear the filters.',
    emptyTitle: 'No users to show',
    emptyDescription: 'Users you can manage will appear here.',
    changeRoles: 'Change roles',
    disable: 'Disable',
    enable: 'Enable',
    delete: 'Delete',
    enabling: 'Enabling…',
    disabling: 'Disabling…',
    deleting: 'Deleting…',
    saving: 'Saving…',
    cancel: 'Cancel',
    save: 'Save',
    changeRolesTitle: (username: string) => `Change roles for ${username}`,
    currentRoles: 'Current roles',
    newRoles: 'New roles',
    onlyRemoveFromDisabled: 'You can only remove roles from a disabled user.',
    enableBeforeAdding: 'Enable this user before adding a role.',
    lastAdmin: 'At least one active administrator must remain.',
    rolesRefused: 'We could not save these roles. Please review them and try again.',
    confirmRoleChangeTitle: (username: string) => `Change the roles of ${username}?`,
    adds: (role: string) => `Adds: ${role}`,
    removes: (role: string) => `Removes: ${role}`,
    confirmNoRoleTitle: (username: string) => `Remove every role from ${username}?`,
    confirmNoRoleDescription: (username: string) => `${username} will not be able to use OpsFlow until a role is given again.`,
    removeRoles: 'Remove roles',
    disableTitle: (username: string) => `Disable ${username}?`,
    disableDescription: 'This user will be signed out when they next use OpsFlow. Their roles will be kept.',
    deleteTitle: (username: string) => `Delete ${username}?`,
    deleteDescription: 'This user will disappear from the list. This action cannot be undone.',
    rolesUpdated: 'Roles updated.',
    userDisabled: 'User disabled.',
    userEnabled: 'User enabled.',
    userDeleted: 'User deleted.',
    userChanged: 'This user has changed. We refreshed the list.',
    noLongerAllowed: 'You no longer have permission to make this change.',
    userGone: 'This user is no longer available. We refreshed the list.',
  },
} as const
