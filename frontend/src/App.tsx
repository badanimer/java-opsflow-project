import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { GuestOnly, RequireNoRole, RequirePermission, RequireRole } from './auth/guards'
import { AppLayout } from './components/AppLayout'
import { ForgotPasswordPage } from './pages/account/ForgotPasswordPage'
import { LoginPage } from './pages/account/LoginPage'
import { PendingPage } from './pages/account/PendingPage'
import { DeletedItemsPage } from './pages/logs/DeletedItemsPage'
import { LogListPage } from './pages/logs/LogListPage'
import { OwnLogPage } from './pages/logs/OwnLogPage'
import { ProductsPage } from './pages/products/ProductsPage'
import { SignUpPage } from './pages/account/SignUpPage'
import { NotFoundPage } from './pages/StatusPages'
import { StockPage } from './pages/stock/StockPage'
import { UsersPage } from './pages/users/UsersPage'

/**
 * The routes. Three pages are for people who are not logged in, one is for a logged-in user with no
 * role, and everything else needs a role and sits in the frame of the application. A new page is added
 * inside that frame, with the permission that opens it, and gets its entry in components/navigation.
 */
export function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
        <Route path="/sign-up" element={<GuestOnly><SignUpPage /></GuestOnly>} />
        <Route path="/forgot-password" element={<GuestOnly><ForgotPasswordPage /></GuestOnly>} />
        <Route path="/pending" element={<RequireNoRole><PendingPage /></RequireNoRole>} />
        <Route element={<RequireRole><AppLayout /></RequireRole>}>
          <Route index element={<RequirePermission permission="PRODUCT_VIEW"><ProductsPage /></RequirePermission>} />
          <Route path="products/records/listing" element={<RequirePermission permission="PRODUCT_VIEW"><LogListPage key="listing" kind="listing" /></RequirePermission>} />
          <Route path="products/records/orders" element={<RequirePermission permission="PRODUCT_VIEW"><LogListPage key="orders" kind="orders" /></RequirePermission>} />
          <Route path="products/:id/records" element={<RequirePermission permission="PRODUCT_VIEW"><OwnLogPage key="product" kind="product" /></RequirePermission>} />
          <Route path="stock" element={<RequirePermission permission="STOCK_VIEW"><StockPage /></RequirePermission>} />
          <Route path="stock/records/items" element={<RequirePermission permission="STOCK_VIEW"><LogListPage key="items" kind="items" /></RequirePermission>} />
          <Route path="stock/records/movements" element={<RequirePermission permission="STOCK_VIEW"><LogListPage key="movements" kind="movements" /></RequirePermission>} />
          <Route path="stock/records/deleted" element={<RequirePermission permission="DELETED_ITEMS_VIEW"><DeletedItemsPage /></RequirePermission>} />
          <Route path="stock/records/deleted/:sku" element={<RequirePermission permission="DELETED_ITEMS_VIEW"><OwnLogPage key="deleted" kind="deleted" /></RequirePermission>} />
          <Route path="stock/:id/records" element={<RequirePermission permission="STOCK_VIEW"><OwnLogPage key="item" kind="item" /></RequirePermission>} />
          <Route path="users" element={<RequirePermission permission="USER_VIEW"><UsersPage /></RequirePermission>} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}
