import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { GuestOnly, RequireNoRole, RequireRole } from './auth/guards'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { PendingPage } from './pages/PendingPage'
import { SignUpPage } from './pages/SignUpPage'

/**
 * The routes. Three pages are for people who are not logged in, one is for a logged-in user with no
 * role, and everything else needs a role. A new page of the application is added inside RequireRole.
 */
export function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
        <Route path="/sign-up" element={<GuestOnly><SignUpPage /></GuestOnly>} />
        <Route path="/forgot-password" element={<GuestOnly><ForgotPasswordPage /></GuestOnly>} />
        <Route path="/pending" element={<RequireNoRole><PendingPage /></RequireNoRole>} />
        <Route path="/" element={<RequireRole><HomePage /></RequireRole>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
