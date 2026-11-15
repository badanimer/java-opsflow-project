import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchAccount, type Account } from '../api/auth'
import { ApiError, onSessionProblem, readEventStream } from '../api/client'
import { SessionReplacedDialog } from '../components/SessionReplacedDialog'
import { AuthContext, type AuthStatus, type AuthValue } from './context'
import { clearSession, loadSession, saveSession, type Session } from './session'

/**
 * The login state of the application.
 *
 * The browser keeps only the token. Who the user is and which roles they hold is asked from the server
 * (`GET /auth/me`) at each start and after each login, because an administrator can change it at any
 * time. When any request is answered UNAUTHENTICATED or ACCOUNT_DISABLED, the token is removed and the
 * login page opens with the reason, as the page design says.
 *
 * An account is signed in on one device at a time. While signed in, the page keeps the event stream
 * `GET /auth/session-events` open and asks for the account again when the person comes back to the tab,
 * so it learns at once when the account signs in elsewhere: the server sends `session-replaced`, or
 * answers SESSION_REPLACED. The page then shows a dialog that cannot be dismissed, and its one button
 * opens the login page. A `session-replaced` for a token that is no longer the stored one means that
 * another tab of this browser signed in again; the account is then loaded again and no dialog is shown.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [account, setAccount] = useState<Account | null>(null)
  const [startupError, setStartupError] = useState<unknown>(null)
  const [replaced, setReplaced] = useState(false)

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
    setStartupError(null)
    try {
      setAccount(await fetchAccount())
      setStatus('signedIn')
    } catch (error) {
      // UNAUTHENTICATED, ACCOUNT_DISABLED, and SESSION_REPLACED have already been handled by the listener below.
      if (error instanceof ApiError && ENDS_THE_LOGIN.includes(error.code)) return
      setStartupError(error)
      setStatus('unreachable')
    }
  }, [])

  useEffect(() => {
    onSessionProblem((reason) => {
      if (reason === 'noRole') {
        setAccount((current) => current && { ...current, roles: [], permissions: [] })
        return
      }
      if (reason === 'replaced') {
        setReplaced(true)
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

  const signInAgain = useCallback(() => {
    setReplaced(false)
    forget()
    navigate('/login', { replace: true, state: { notice: 'replaced' } })
  }, [forget, navigate])

  // The event stream of the signed-in page. It is opened again when the server closes it (every 15
  // minutes), and after a lost connection with a growing pause.
  useEffect(() => {
    if (status !== 'signedIn' || replaced) return
    const controller = new AbortController()
    const token = loadSession()?.token
    void (async () => {
      let pause = FIRST_PAUSE
      while (!controller.signal.aborted) {
        try {
          await readEventStream(
            '/auth/session-events',
            (name) => {
              if (name !== 'session-replaced') return
              if (loadSession()?.token !== token) void load()
              else setReplaced(true)
            },
            controller.signal,
          )
          pause = FIRST_PAUSE
        } catch (error) {
          // A login that ended has been handled by the listener; anything else is tried again.
          if (error instanceof ApiError && ENDS_THE_LOGIN.includes(error.code)) return
          pause = Math.min(pause * 2, LONGEST_PAUSE)
        }
        await sleep(pause, controller.signal)
      }
    })()
    return () => controller.abort()
  }, [status, replaced, load])

  // Coming back to the tab asks for the account, in case the stream missed an event.
  useEffect(() => {
    if (status !== 'signedIn' || replaced) return
    let lastCheck = Date.now()
    const check = () => {
      if (document.visibilityState !== 'visible' || Date.now() - lastCheck < FOCUS_CHECK_GAP) return
      lastCheck = Date.now()
      refresh().catch(() => {})
    }
    document.addEventListener('visibilitychange', check)
    window.addEventListener('focus', check)
    return () => {
      document.removeEventListener('visibilitychange', check)
      window.removeEventListener('focus', check)
    }
  }, [status, replaced, refresh])

  const value = useMemo<AuthValue>(
    () => ({ status, account, startupError, signIn, signOut, refresh, retry: () => void load() }),
    [status, account, startupError, signIn, signOut, refresh, load],
  )

  return (
    <AuthContext.Provider value={value}>
      {children}
      {replaced && <SessionReplacedDialog onSignInAgain={signInAgain} />}
    </AuthContext.Provider>
  )
}

const ENDS_THE_LOGIN = ['UNAUTHENTICATED', 'ACCOUNT_DISABLED', 'SESSION_REPLACED']
const FIRST_PAUSE = 1000
const LONGEST_PAUSE = 60_000
/** Coming back to the tab twice within this time asks the server once. */
const FOCUS_CHECK_GAP = 5000

function sleep(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms)
    signal.addEventListener('abort', () => {
      clearTimeout(timer)
      resolve()
    })
  })
}
