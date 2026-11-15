import { describe, expect, it } from 'vitest'
import { formatPrice } from './format'

describe('a price', () => {
  it('is shown in Australian dollars with the two decimals the server sent', () => {
    expect(formatPrice('12.50')).toBe('$12.50')
    expect(formatPrice('9999999999.99')).toBe('$9999999999.99')
  })
})
