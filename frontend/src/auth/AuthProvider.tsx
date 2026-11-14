import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchAccount, type Account } from '../api/auth'
import { ApiError, onSessionProblem } from '../api/client'
import { AuthContext, type AuthStatus, type AuthValue } from './context'
import { clearSession, loadSession, saveSession, type Session } from './session'

/**
 * The login state of the application.
 *
 * The browser keeps only the token. Who the user is and which roles they hold is asked from the server
 * (`GET /auth/me`) at each start and after each login, because an administrator can change it at any
 * time. When any request is answered UNAUTHENTICATED or ACCOUNT_DISABLED, the token is removed and the
 * login page opens with the reason, as the page design says.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [account, setAccount] = useState<Account | null>(null)

  const forget = useCallback(() => {
    clearSession()
    setAccount(null)
    setStatus('signedOut')
  }, [])

  const load = useCallback(async () => {
    if (!loadSession()) {
      setAccount(null)
      setStatus('signedOut')
      return
    }
    setStatus('loading')
    try {
      setAccount(await fetchAccount())
      setStatus('signedIn')
    } catch (error) {
      // UNAUTHENTICATED and ACCOUNT_DISABLED have already been handled by the listener below.
      if (error instanceof ApiError && (error.code === 'UNAUTHENTICATED' || error.code === 'ACCOUNT_DISABLED')) return
      setStatus('unreachable')
    }
  }, [])

  useEffect(() => {
    onSessionProblem((reason) => {
      if (reason === 'noRole') {
        setAccount((current) => current && { ...current, roles: [], permissions: [] })
        return
      }
      forget()
      navigate('/login', { replace: true, state: { notice: reason } })
    })
    return () => onSessionProblem(null)
  }, [forget, navigate])

  useEffect(() => {
    void load()
  }, [load])

  const signIn = useCallback(async (session: Session) => {
    saveSession(session)
    try {
      const loaded = await fetchAccount()
      setAccount(loaded)
      setStatus('signedIn')
      return loaded
    } catch (error) {
      forget()
      throw error
    }
  }, [forget])

  const signOut = useCallback(() => {
    forget()
    navigate('/login', { replace: true })
  }, [forget, navigate])

  const refresh = useCallback(async () => {
    const loaded = await fetchAccount()
    setAccount(loaded)
    return loaded
  }, [])

  const value = useMemo<AuthValue>(
    () => ({ status, account, signIn, signOut, refresh, retry: () => void load() }),
    [status, account, signIn, signOut, refresh, load],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
