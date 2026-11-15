import { useState } from 'react'
import { Alert, Button, Spin, Typography, Upload } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { ApiError, NetworkError } from '../api/client'
import { uploadItemImage } from '../api/items'
import { generalFailure } from '../lib/failure'
import { TEXT } from '../text'
import { ItemThumbnail } from './ItemThumbnail'
import { checkImageFile } from '../lib/imageFile'

/** The picture in the draft of an item. `fileName` is what the person sees; `current` marks the saved one. */
export interface DraftImage {
  path: string
  url: string
  fileName: string
  current: boolean
}

interface Props {
  value: DraftImage | null
  onChange: (image: DraftImage | null) => void
  /** True while a file is being uploaded: the dialog keeps Save disabled. */
  onBusyChange: (busy: boolean) => void
  /** The permission was taken away during the upload: the dialog ends. */
  onForbidden: () => void
  /** A message set by the dialog, such as a picture refused at Save. */
  error?: string | null
  disabled?: boolean
}

/**
 * The picture of an item in the Add and Edit dialogs (Figma frames "Image upload"). A file is checked in
 * the page first, then uploaded by itself; its path is saved with the item later. A failed upload leaves
 * the picture there was, and never touches the other fields of the dialog. Removing a picture changes
 * only the draft: no file is ever deleted.
 */
export function ImageField({ value, onChange, onBusyChange, onForbidden, error, disabled }: Props) {
  const [uploading, setUploading] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [removed, setRemoved] = useState(false)

  async function take(file: File) {
    setMessage(null)
    setRemoved(false)
    const check = await checkImageFile(file).catch(() => 'wrongType' as const)
    if (check === 'tooLarge') return setMessage(TEXT.image.tooLarge)
    if (check === 'wrongType') return setMessage(TEXT.image.wrongType)
    setUploading(true)
    onBusyChange(true)
    try {
      const stored = await uploadItemImage(file)
      onChange({ path: stored.path, url: stored.url, fileName: file.name, current: false })
    } catch (failure) {
      setMessage(uploadFailure(failure, onForbidden))
    } finally {
      setUploading(false)
      onBusyChange(false)
    }
  }

  /** Antd's own upload is not used: the file is checked and sent by `take`. */
  const select = (file: File) => {
    void take(file)
    return Upload.LIST_IGNORE
  }

  const shown = message ?? error ?? null
  return (
    <div className="image-field">
      {value && !uploading ? (
        <div className="image-field__chosen">
          <ItemThumbnail url={value.url} size={48} />
          <div className="image-field__file">
            <Typography.Text strong>{value.current ? TEXT.image.current : TEXT.image.uploaded}</Typography.Text>
            <Typography.Text type="secondary" ellipsis={{ tooltip: value.fileName }}>
              {value.fileName}
            </Typography.Text>
          </div>
          <Upload accept="image/jpeg,image/png,image/webp" showUploadList={false} beforeUpload={select} disabled={disabled}>
            <Button disabled={disabled}>{TEXT.image.replace}</Button>
          </Upload>
          <Button
            type="link"
            danger
            disabled={disabled}
            onClick={() => {
              setMessage(null)
              setRemoved(true)
              onChange(null)
            }}
          >
            {TEXT.image.remove}
          </Button>
        </div>
      ) : uploading ? (
        <div className="image-field__uploading" aria-busy="true">
          {value && <ItemThumbnail url={value.url} size={48} />}
          <Spin size="small" />
          <Typography.Text>{TEXT.image.uploading}</Typography.Text>
        </div>
      ) : (
        <Upload.Dragger
          accept="image/jpeg,image/png,image/webp"
          showUploadList={false}
          beforeUpload={select}
          multiple={false}
          disabled={disabled}
          className="image-field__dragger"
        >
          <InboxOutlined className="image-field__icon" />
          <Typography.Text strong>{TEXT.image.choose}</Typography.Text>
          <Typography.Text type="secondary"> {TEXT.image.dragHint}</Typography.Text>
          <div>
            <Typography.Text type="secondary">{TEXT.image.rules}</Typography.Text>
          </div>
        </Upload.Dragger>
      )}
      {removed && !value && !shown && <Typography.Text type="secondary">{TEXT.image.removed}</Typography.Text>}
      {shown && <Alert type="error" showIcon message={shown} role="alert" className="image-field__error" />}
    </div>
  )
}

/** The message of a failed upload. A lost permission ends the dialog instead. */
function uploadFailure(failure: unknown, onForbidden: () => void): string {
  if (failure instanceof NetworkError) return TEXT.cannotReach
  if (failure instanceof ApiError) {
    switch (failure.code) {
      case 'IMAGE_TOO_LARGE':
        return TEXT.image.tooLarge
      case 'IMAGE_TYPE_NOT_ALLOWED':
        return TEXT.image.wrongType
      case 'VALIDATION_FAILED':
      case 'UNSUPPORTED_MEDIA_TYPE':
        return TEXT.image.unreadable
      case 'FORBIDDEN':
        onForbidden()
        return TEXT.noLongerAllowed
    }
  }
  return generalFailure(failure).message
}
