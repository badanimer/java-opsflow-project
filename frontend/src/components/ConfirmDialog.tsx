import type { ReactNode } from 'react'
import { Alert, Button, Modal } from 'antd'
import type { Notice } from '../pages/failure'

export interface ConfirmDialogProps {
  open: boolean
  title: string
  children?: ReactNode
  /** The verb of the action, never "OK": "Delete", "Disable", "Remove roles". */
  confirmLabel: string
  /** What the confirm button says while the request runs, such as "Deleting…". */
  runningLabel: string
  cancelLabel: string
  /** The danger style, for an action that removes something. */
  danger?: boolean
  running: boolean
  /** A refusal that keeps the dialog open, shown under the text. */
  notice?: Notice | null
  /**
   * The action cannot go on (the target is gone, or the permission was lost): the dialog shows the
   * notice and one button, `closeLabel`, which calls `onCancel`. Used by the stock and product pages.
   */
  finished?: boolean
  closeLabel?: string
  onConfirm: () => void
  onCancel: () => void
}

/**
 * The second confirmation of an action that is hard to take back (Second Confirmation in
 * docs/frontend/page-design.md). It names the target and what will happen, its confirm button repeats
 * the verb, and Cancel is the safe choice and has the focus. While the request runs, neither button can
 * be used and the dialog cannot be closed. The later pages use it too: deleting an item, delisting a
 * product, archiving tickets.
 */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  runningLabel,
  cancelLabel,
  danger,
  running,
  notice,
  finished,
  closeLabel,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <Modal
      open={open}
      title={title}
      closable={false}
      maskClosable={false}
      keyboard={!running}
      onCancel={running ? undefined : onCancel}
      destroyOnClose
      footer={
        finished
          ? [
              <Button key="close" onClick={onCancel} autoFocus>
                {closeLabel ?? cancelLabel}
              </Button>,
            ]
          : [
              <Button key="cancel" onClick={onCancel} disabled={running} autoFocus>
                {cancelLabel}
              </Button>,
              <Button key="confirm" type="primary" danger={danger} onClick={onConfirm} loading={running}>
                {running ? runningLabel : confirmLabel}
              </Button>,
            ]
      }
    >
      <div className="dialog-body">
        {children}
        {notice && <Alert type={notice.type} showIcon message={notice.message} role="alert" />}
      </div>
    </Modal>
  )
}
