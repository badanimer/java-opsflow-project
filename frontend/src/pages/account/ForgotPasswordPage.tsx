import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert, Button, Form, Input } from 'antd'
import { resetPassword } from '../../api/auth'
import { ApiError } from '../../api/client'
import { AuthLayout } from '../../components/AuthLayout'
import { TEXT } from '../../text'
import { checkCode, checkEmail, checkNewPassword, rule } from '../../lib/validation'
import { generalFailure, type Notice } from '../../lib/failure'
import { useEmailCode } from '../../hooks/useEmailCode'

interface Values {
  email: string
  code: string
  newPassword: string
}

/**
 * Forgot password: the email, a code sent to it, and the new password. It is the only way to change a
 * password. After a request for a code the page says the same sentence whether or not the address has
 * an account, because the server gives the same answer on purpose.
 */
export function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [form] = Form.useForm<Values>()
  const code = useEmailCode('RESET_PASSWORD')
  const [notice, setNotice] = useState<Notice | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function sendCode() {
    let email: string
    try {
      email = (await form.validateFields(['email'])).email
    } catch {
      return
    }
    setNotice(null)
    try {
      await code.send(email)
      setNotice({ type: 'info', message: TEXT.resetCodeSent })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
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
      await resetPassword(values.email, values.code, values.newPassword)
      navigate('/login', { replace: true, state: { notice: 'reset' } })
    } catch (error) {
      if (!showOnField(error)) setNotice(generalFailure(error))
    } finally {
      setSubmitting(false)
    }
  }

  function showOnField(error: unknown): boolean {
    if (!(error instanceof ApiError)) return false
    if (error.code === 'CODE_INVALID') {
      form.setFields([{ name: 'code', errors: [TEXT.codeWrong] }])
      return true
    }
    if (error.code === 'VALIDATION_FAILED') {
      const refused = error.errors.map((e) => e.field)
      if (refused.includes('newPassword')) {
        // The page has already checked the password rule, so the server refused it for the other
        // reason: it equals the current password. A password that breaks the rule gets the rule.
        const rules = checkNewPassword(form.getFieldValue('newPassword'))
        form.setFields([{ name: 'newPassword', errors: [rules ?? TEXT.samePassword] }])
        return true
      }
      if (refused.includes('code')) {
        form.setFields([{ name: 'code', errors: [TEXT.codeInvalidFormat] }])
        return true
      }
      if (refused.includes('email')) {
        form.setFields([{ name: 'email', errors: [TEXT.emailInvalid] }])
        return true
      }
    }
    return false
  }

  return (
    <AuthLayout title="Reset your password" subtitle="We will email you a code to set a new password.">
      {notice && <Alert type={notice.type} showIcon message={notice.message} role="alert" />}
      <Form<Values> form={form} layout="vertical" requiredMark={false} onFinish={submit} disabled={submitting} size="large">
        <Form.Item label="Email" htmlFor="email">
          <div className="code-row">
            <Form.Item name="email" rules={[rule(checkEmail)]} noStyle>
              <Input placeholder="name@example.com" autoComplete="email" maxLength={254} inputMode="email" autoFocus />
            </Form.Item>
            <Button onClick={sendCode} disabled={!code.canSend} className="code-row__button">
              {code.sending ? TEXT.sending : code.secondsLeft > 0 ? TEXT.resendIn(code.secondsLeft) : TEXT.sendCode}
            </Button>
          </div>
        </Form.Item>
        <Form.Item label="Verification code" name="code" rules={[rule(checkCode)]}>
          <Input placeholder="6-digit code from the email" autoComplete="one-time-code" maxLength={6} inputMode="numeric" />
        </Form.Item>
        <Form.Item label="New password" name="newPassword" rules={[rule(checkNewPassword)]} extra={TEXT.passwordHint}>
          <Input.Password placeholder="Choose a new password" autoComplete="new-password" maxLength={24} />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={submitting}>
          {submitting ? 'Resetting password…' : 'Reset password'}
        </Button>
      </Form>
      <p className="auth-card__footer">
        <Link to="/login">Back to sign in</Link>
      </p>
    </AuthLayout>
  )
}
