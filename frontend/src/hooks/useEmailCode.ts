import { useCallback, useEffect, useRef, useState } from 'react'
import { requestEmailCode, type EmailCodePurpose } from '../api/auth'

/** A new code for the same address can be requested after this many seconds. */
export const RESEND_SECONDS = 60

/**
 * The request for an email code and the wait before the next one. After an accepted request the
 * button is disabled for 60 seconds and counts down, which is the rule of the server.
 */
export function useEmailCode(purpose: EmailCodePurpose) {
  const [sending, setSending] = useState(false)
  const [secondsLeft, setSecondsLeft] = useState(0)
  const timer = useRef<ReturnType<typeof setInterval> | null>(null)

  const stop = useCallback(() => {
    if (timer.current) clearInterval(timer.current)
    timer.current = null
  }, [])

  useEffect(() => stop, [stop])

  /** Sends the request. The caller shows what the answer means; an error is thrown to it. */
  const send = useCallback(
    async (email: string) => {
      setSending(true)
      try {
        await requestEmailCode(email, purpose)
        stop()
        setSecondsLeft(RESEND_SECONDS)
        timer.current = setInterval(() => {
          setSecondsLeft((left) => {
            if (left <= 1) stop()
            return Math.max(0, left - 1)
          })
        }, 1000)
      } finally {
        setSending(false)
      }
    },
    [purpose, stop],
  )

  return { send, sending, secondsLeft, canSend: !sending && secondsLeft === 0 }
}
