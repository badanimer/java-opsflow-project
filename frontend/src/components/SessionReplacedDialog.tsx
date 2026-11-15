import { Button, Modal, Typography } from 'antd'
import { TEXT } from '../text'

/**
 * Shown over any signed-in page when the account signed in on another device (Figma frame
 * Session / Signed in elsewhere). It cannot be closed or cancelled: the login here has ended, so the only
 * way on is to sign in again, which opens the login page with the reason.
 */
export function SessionReplacedDialog({ onSignInAgain }: { onSignInAgain: () => void }) {
  return (
    <Modal
      open
      title={TEXT.signedOutTitle}
      closable={false}
      maskClosable={false}
      keyboard={false}
      footer={
        <Button type="primary" onClick={onSignInAgain} autoFocus>
          {TEXT.signInAgain}
        </Button>
      }
    >
      <Typography.Paragraph className="dialog-text">{TEXT.signedOutReason}</Typography.Paragraph>
    </Modal>
  )
}
