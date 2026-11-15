import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { DESKTOP_WIDTH, screenWidth, setScreenWidth } from './screen'

/**
 * Ant Design asks the browser about the screen width, which the test browser does not answer. This
 * answers its media queries for the width of test/screen.ts: a desktop screen unless a test sets less.
 */
function matchesWidth(query: string): boolean {
  const min = /min-width:\s*(\d+)px/.exec(query)
  const max = /max-width:\s*(\d+)px/.exec(query)
  const width = screenWidth()
  return (!min || width >= Number(min[1])) && (!max || width <= Number(max[1]))
}

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: matchesWidth(query),
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  setScreenWidth(DESKTOP_WIDTH)
  vi.unstubAllGlobals()
  vi.useRealTimers()
})
