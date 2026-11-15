/**
 * The screen width the tests pretend to have, kept on the global object so that the set-up file and
 * every test read the same value. A test of the small screen calls setScreenWidth before it opens the
 * application; the set-up puts the desktop width back after each test.
 */
export const DESKTOP_WIDTH = 1280

const holder = globalThis as unknown as { __opsflowScreenWidth?: number }

export function screenWidth(): number {
  return holder.__opsflowScreenWidth ?? DESKTOP_WIDTH
}

export function setScreenWidth(width: number): void {
  holder.__opsflowScreenWidth = width
}
