import { Segmented, Tooltip } from 'antd'
import type { ProductPageSize } from '../api/products'
import { TEXT } from '../text'

/** One square, four, or nine: the cards get smaller as the page holds more. */
function GridIcon({ cells }: { cells: 1 | 2 | 3 }) {
  const gap = 1.5
  const size = (14 - gap * (cells - 1)) / cells
  const squares = []
  for (let row = 0; row < cells; row++) {
    for (let column = 0; column < cells; column++) {
      squares.push(<rect key={`${row}-${column}`} x={column * (size + gap)} y={row * (size + gap)} width={size} height={size} rx={cells === 1 ? 2 : 1} />)
    }
  }
  return (
    <svg viewBox="0 0 14 14" width="16" height="16" fill="currentColor" aria-hidden="true" className="page-size-switch__icon">
      {squares}
    </svg>
  )
}

const CHOICES: { size: ProductPageSize; cells: 1 | 2 | 3 }[] = [
  { size: 8, cells: 1 },
  { size: 16, cells: 2 },
  { size: 24, cells: 3 },
]

/**
 * How many products a page shows (the owner's request, 2026-11-16): 8 large cards, 16, or 24 small ones,
 * as one square, four, or nine. The choice that is on is filled with the primary colour.
 */
export function PageSizeSwitch({ value, onChange }: { value: ProductPageSize; onChange: (size: ProductPageSize) => void }) {
  return (
    <Segmented<ProductPageSize>
      className="page-size-switch"
      aria-label={TEXT.products.pageSize}
      value={value}
      onChange={onChange}
      options={CHOICES.map(({ size, cells }) => ({
        value: size,
        label: (
          <Tooltip title={TEXT.products.perPage(size)}>
            <span className="page-size-switch__option" aria-label={TEXT.products.perPage(size)} role="img">
              <GridIcon cells={cells} />
            </span>
          </Tooltip>
        ),
      }))}
    />
  )
}
