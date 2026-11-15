/**
 * How values are shown. A price is in Australian dollars with two decimals, "$12.50" (page-design.md,
 * Product page). The API sends a price as a string with two decimals, so no rounding happens here; only
 * the sign is added. Another currency is a change in this one place.
 */
export const CURRENCY_SIGN = '$'

export function formatPrice(price: string): string {
  return `${CURRENCY_SIGN}${price}`
}
