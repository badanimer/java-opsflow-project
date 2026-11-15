import { Button, Modal, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/context'
import { can, type Permission } from '../../auth/permissions'
import { TEXT } from '../../text'

interface Choice {
  title: string
  hint: string
  path: string
  permission: Permission
}

const CHOICES: Record<'products' | 'stock', Choice[]> = {
  products: [
    { title: TEXT.logs.listingRecords, hint: TEXT.logs.listingRecordsHint, path: '/products/records/listing', permission: 'PRODUCT_VIEW' },
    { title: TEXT.logs.orderRecords, hint: TEXT.logs.orderRecordsHint, path: '/products/records/orders', permission: 'PRODUCT_VIEW' },
  ],
  stock: [
    { title: TEXT.logs.itemRecords, hint: TEXT.logs.itemRecordsHint, path: '/stock/records/items', permission: 'STOCK_VIEW' },
    { title: TEXT.logs.stockMovements, hint: TEXT.logs.stockMovementsHint, path: '/stock/records/movements', permission: 'STOCK_VIEW' },
    { title: TEXT.logs.deletedItems, hint: TEXT.logs.deletedItemsHint, path: '/stock/records/deleted', permission: 'DELETED_ITEMS_VIEW' },
  ],
}

/**
 * View records (Figma frames "Product records chooser" and "Warehouse records chooser"): which kind of
 * record to read, each opened as a page of its own. A choice is offered only with its permission.
 */
export function RecordsChooser({ area, onClose }: { area: 'products' | 'stock'; onClose: () => void }) {
  const { account } = useAuth()
  const navigate = useNavigate()
  return (
    <Modal open title={TEXT.logs.chooserTitle} onCancel={onClose} footer={<Button onClick={onClose}>{TEXT.cancel}</Button>} destroyOnClose>
      <ul className="records-chooser">
        {CHOICES[area]
          .filter((choice) => can(account, choice.permission))
          .map((choice) => (
            <li key={choice.path} className="records-chooser__choice">
              <div>
                <Typography.Text strong>{choice.title}</Typography.Text>
                <div><Typography.Text type="secondary">{choice.hint}</Typography.Text></div>
              </div>
              <Button type="link" aria-label={`${TEXT.logs.open} ${choice.title}`} onClick={() => navigate(choice.path)}>
                {TEXT.logs.open}
              </Button>
            </li>
          ))}
      </ul>
    </Modal>
  )
}
