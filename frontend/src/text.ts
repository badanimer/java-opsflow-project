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
} as const
