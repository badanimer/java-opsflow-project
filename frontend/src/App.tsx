import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { GuestOnly, RequireNoRole, RequirePermission, RequireRole } from './auth/guards'
import { AppLayout } from './components/AppLayout'
import { ForgotPasswordPage } from './pages/account/ForgotPasswordPage'
import { LoginPage } from './pages/account/LoginPage'
import { PendingPage } from './pages/account/PendingPage'
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
          <Route path="stock" element={<RequirePermission permission="STOCK_VIEW"><StockPage /></RequirePermission>} />
          <Route path="users" element={<RequirePermission permission="USER_VIEW"><UsersPage /></RequirePermission>} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}
