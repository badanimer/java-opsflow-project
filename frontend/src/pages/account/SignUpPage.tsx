import { useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert, Button, Form, Input } from 'antd'
import { signUp } from '../../api/auth'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/context'
import { AuthLayout } from '../../components/AuthLayout'
import { TEXT } from '../../text'
import { checkCode, checkEmail, checkNewPassword, checkNewUsername, rule } from '../../lib/validation'
import { generalFailure, type Notice } from '../../lib/failure'
import { useEmailCode } from '../../hooks/useEmailCode'

interface Values {
  username: string
  email: string
  code: string
  password: string
}

const EMAIL_TAKEN: ReactNode = (
  <>
    This email is already registered. <Link to="/login">Sign in</Link>, or use{' '}
    <Link to="/forgot-password">Forgot password</Link>.
  </>
)

/**
 * The sign-up page: a username, an email with a code sent to it, and a password. The sign-up logs the
 * new user in, and the guards then open the pending page, because a new account has no role.
 */
export function SignUpPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [form] = Form.useForm<Values>()
  const code = useEmailCode('REGISTER')
  const [notice, setNotice] = useState<Notice | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function sendCode() {
    let email: string
    try {
      email = (await form.validateFields(['email'])).email
    } catch {
      return // The field shows what is wrong with the address.
    }
    setNotice(null)
    try {
      await code.send(email)
      setNotice({ type: 'info', message: TEXT.signUpCodeSent(email) })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'EMAIL_TAKEN') {
        form.setFields([{ name: 'email', errors: [EMAIL_TAKEN as unknown as string] }])
      } else if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
        form.setFields([{ name: 'email', errors: [TEXT.emailInvalid] }])
      } else {
        setNotice(generalFailure(error))
      }
    }
  }

  async function submit(values: Values) {
    setSubmitting(true)
    setNotice(null)
    try {
      const created = await signUp(values.username, values.email, values.password, values.code)
      await signIn({ token: created.token, expiresAt: created.expiresAt })
      navigate('/', { replace: true })
    } catch (error) {
      if (!showOnField(error)) setNotice(generalFailure(error))
    } finally {
      setSubmitting(false)
    }
  }

  /** Puts a refusal that is about one field under that field. Returns false for any other failure. */
  function showOnField(error: unknown): boolean {
    if (!(error instanceof ApiError)) return false
    switch (error.code) {
      case 'CODE_INVALID':
        form.setFields([{ name: 'code', errors: [TEXT.codeWrong] }])
        return true
      case 'USERNAME_TAKEN':
        form.setFields([{ name: 'username', errors: [TEXT.usernameTaken] }])
        return true
      case 'EMAIL_TAKEN':
        form.setFields([{ name: 'email', errors: [EMAIL_TAKEN as unknown as string] }])
        return true
      case 'VALIDATION_FAILED': {
        // The server checks the same rules as the page. Its words are replaced by the words of the page.
        const messages: Record<string, string> = {
          username: TEXT.usernameInvalid,
          email: TEXT.emailInvalid,
          code: TEXT.codeInvalidFormat,
          password: TEXT.passwordInvalid,
        }
        const fields = error.errors.filter((e) => e.field in messages)
        if (fields.length === 0) return false
        form.setFields(fields.map((e) => ({ name: e.field as keyof Values, errors: [messages[e.field]] })))
        return true
      }
      default:
        return false
    }
  }

  return (
    <AuthLayout title="Create your account" subtitle="A new account has no role until an administrator assigns one.">
      {notice && <Alert type={notice.type} showIcon message={notice.message} role="alert" />}
      <Form<Values> form={form} layout="vertical" requiredMark={false} onFinish={submit} disabled={submitting} size="large">
        <Form.Item label="Username" name="username" rules={[rule(checkNewUsername)]} extra={TEXT.usernameHint}>
          <Input placeholder="Choose a username" autoComplete="username" maxLength={64} autoFocus />
        </Form.Item>
        <Form.Item label="Email" htmlFor="email">
          <div className="code-row">
            <Form.Item name="email" rules={[rule(checkEmail)]} noStyle>
              <Input placeholder="name@example.com" autoComplete="email" maxLength={254} inputMode="email" />
            </Form.Item>
            <Button onClick={sendCode} disabled={!code.canSend} className="code-row__button">
              {code.sending ? TEXT.sending : code.secondsLeft > 0 ? TEXT.resendIn(code.secondsLeft) : TEXT.sendCode}
            </Button>
          </div>
        </Form.Item>
        <Form.Item label="Verification code" name="code" rules={[rule(checkCode)]}>
          <Input placeholder="6-digit code from the email" autoComplete="one-time-code" maxLength={6} inputMode="numeric" />
        </Form.Item>
        <Form.Item label="Password" name="password" rules={[rule(checkNewPassword)]} extra={TEXT.passwordHint}>
          <Input.Password placeholder="Choose a password" autoComplete="new-password" maxLength={24} />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </Button>
      </Form>
      <p className="auth-card__footer">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </AuthLayout>
  )
}

