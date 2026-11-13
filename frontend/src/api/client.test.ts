import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadSession } from '../auth/session'
import { FakeServer, TOKEN, problem, storeToken } from '../test/server'
import { ApiError, NetworkError, api, onSessionProblem } from './client'

afterEach(() => onSessionProblem(null))

describe('the API client', () => {
  it('sends JSON under /api/v1 and returns the body of the answer', async () => {
    const server = new FakeServer().on('POST /things', { status: 200, body: { id: 7 } })

    const result = await api<{ id: number }>('/things', { method: 'POST', body: { name: 'x' } })

    expect(result).toEqual({ id: 7 })
    expect(server.calls[0]).toMatchObject({ method: 'POST', path: '/things', body: { name: 'x' } })
  })

  it('returns nothing for an answer with no body', async () => {
    new FakeServer().on('POST /things', { status: 204 })

    await expect(api('/things', { method: 'POST', body: {} })).resolves.toBeUndefined()
  })

  it('carries the stored token, and leaves it out when the request needs no login', async () => {
    storeToken()
    const server = new FakeServer().on('GET /private', { status: 200, body: {} }).on('POST /public', { status: 204 })

    await api('/private')
    await api('/public', { method: 'POST', body: {}, auth: false })

    expect(server.calls[0].authorization).toBe(`Bearer ${TOKEN.token}`)
    expect(server.calls[1].authorization).toBeNull()
  })

  it('does not send a token that has expired', async () => {
    localStorage.setItem('opsflow.session', JSON.stringify({ token: 'old', expiresAt: '2000-01-01T00:00:00Z' }))
    const server = new FakeServer().on('GET /private', problem(401, 'UNAUTHENTICATED'))

    await expect(api('/private')).rejects.toBeInstanceOf(ApiError)

    expect(server.calls[0].authorization).toBeNull()
    expect(loadSession()).toBeNull()
  })

  it('turns the error body of the API into an ApiError with its code, request id, and fields', async () => {
    new FakeServer().on('POST /things', problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'name', message: 'too long' }] }))

    const error = await api('/things', { method: 'POST', body: {} }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      requestId: 'ddddc8f3b64942ff',
      errors: [{ field: 'name', message: 'too long' }],
    })
  })

  it('reports no answer, and a gateway error of a proxy, as a NetworkError', async () => {
    new FakeServer().on('GET /down', 'network-error').on('GET /proxy', { status: 502, body: 'Bad Gateway' })

    await expect(api('/down')).rejects.toBeInstanceOf(NetworkError)
    await expect(api('/proxy')).rejects.toBeInstanceOf(NetworkError)
  })

  it('reports an error that is not in the format of the API as INTERNAL_ERROR', async () => {
    new FakeServer().on('GET /odd', { status: 500, body: 'plain text' })

    await expect(api('/odd')).rejects.toMatchObject({ code: 'INTERNAL_ERROR', status: 500 })
  })

  it('tells the login state when a request with a token is refused as unauthenticated or disabled', async () => {
    storeToken()
    const ended = vi.fn()
    onSessionProblem(ended)
    new FakeServer().on('GET /a', problem(401, 'UNAUTHENTICATED')).on('GET /b', problem(403, 'ACCOUNT_DISABLED'))

    await expect(api('/a')).rejects.toBeInstanceOf(ApiError)
    await expect(api('/b')).rejects.toBeInstanceOf(ApiError)

    expect(ended.mock.calls).toEqual([['expired'], ['disabled']])
  })

  it('does not end a session for the same codes from a request without a token, such as the login', async () => {
    storeToken()
    const ended = vi.fn()
    onSessionProblem(ended)
    new FakeServer().on('POST /auth/login', problem(401, 'UNAUTHENTICATED'))

    await expect(api('/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toMatchObject({ code: 'UNAUTHENTICATED' })

    expect(ended).not.toHaveBeenCalled()
  })

  it('reports a missing role without treating a generic permission denial as a session problem', async () => {
    storeToken()
    const ended = vi.fn()
    onSessionProblem(ended)
    new FakeServer().on('GET /a', problem(403, 'FORBIDDEN')).on('GET /b', problem(403, 'ACCOUNT_NO_ROLE'))

    await expect(api('/a')).rejects.toMatchObject({ code: 'FORBIDDEN' })
    await expect(api('/b')).rejects.toMatchObject({ code: 'ACCOUNT_NO_ROLE' })

    expect(ended.mock.calls).toEqual([['noRole']])
  })
})
