import { useState } from 'react'
import { PictureOutlined } from '@ant-design/icons'

/**
 * The picture of a warehouse item, fitted whole into a square tile (contain), or a placeholder when the
 * item has no picture or the picture fails to load. The stock page, the product page, and the catalog
 * of List item use it.
 */
export function ItemThumbnail({ url, size = 40, alt = '' }: { url: string | null; size?: number; alt?: string }) {
  const [failed, setFailed] = useState(false)
  const style = { width: size, height: size }
  if (!url || failed) {
    return (
      <span className="item-thumbnail item-thumbnail--empty" style={style} aria-hidden={alt === ''}>
        <PictureOutlined />
      </span>
    )
  }
  return (
    <span className="item-thumbnail" style={style}>
      <img src={url} alt={alt} loading="lazy" onError={() => setFailed(true)} />
    </span>
  )
}
