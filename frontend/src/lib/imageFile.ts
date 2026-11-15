/**
 * The check of a picture before it is uploaded (Item images in docs/database/database-design.md): at
 * most 2 MB, and a JPEG, PNG, or WebP judged by its first bytes, as the server judges it. The server
 * checks again; this only spares a request that would be refused.
 */
export const IMAGE_MAX_BYTES = 2 * 1024 * 1024

export type ImageCheck = 'ok' | 'tooLarge' | 'wrongType'

export async function checkImageFile(file: File): Promise<ImageCheck> {
  if (file.size > IMAGE_MAX_BYTES) return 'tooLarge'
  const head = new Uint8Array(await readHead(file, 12))
  return isJpeg(head) || isPng(head) || isWebp(head) ? 'ok' : 'wrongType'
}

function readHead(file: File, length: number): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as ArrayBuffer)
    reader.onerror = () => reject(reader.error)
    reader.readAsArrayBuffer(file.slice(0, length))
  })
}

function isJpeg(b: Uint8Array) {
  return b.length >= 3 && b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff
}

function isPng(b: Uint8Array) {
  const signature = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]
  return b.length >= 8 && signature.every((value, i) => b[i] === value)
}

function isWebp(b: Uint8Array) {
  const text = (from: number, to: number) => String.fromCharCode(...b.slice(from, to))
  return b.length >= 12 && text(0, 4) === 'RIFF' && text(8, 12) === 'WEBP'
}
