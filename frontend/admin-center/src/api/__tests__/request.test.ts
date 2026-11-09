import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ notifyError: vi.fn() }))

vi.mock('@/utils/notify', () => ({ notifyError: mocks.notifyError }))
vi.mock('@/utils/sso', () => ({ redirectToUnifiedLogin: vi.fn(), setSsoReturnPath: vi.fn() }))

import request from '../request'

/** Answers every request with `status` and `data`, as the server or a proxy in front of it would. */
function respondWith(status: number, data: unknown): AxiosAdapter {
  return async (config: InternalAxiosRequestConfig) => {
    throw new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_RESPONSE', config, null, {
      status,
      statusText: '',
      headers: {},
      config,
      data,
    })
  }
}

describe('request: 503 Service Unavailable', () => {
  beforeEach(() => {
    mocks.notifyError.mockClear()
  })

  it('shows the message of an Admin API error, which says what is unavailable', async () => {
    const message = 'The LDAP sync did not start: its lock service (Redis) cannot be reached. Try again later, '
      + 'or ask the platform operator to check Redis.'
    request.defaults.adapter = respondWith(503, {
      success: false,
      error: { code: 'LDAP_SYNC_LOCK_UNAVAILABLE', message },
    })

    await expect(request.post('/ldap-sync/full')).rejects.toMatchObject({ status: 503 })

    expect(mocks.notifyError).toHaveBeenCalledTimes(1)
    expect(mocks.notifyError).toHaveBeenCalledWith(message)
  })

  it('keeps the generic text for a proxy page', async () => {
    request.defaults.adapter = respondWith(503, '<html><body>503 Service Temporarily Unavailable</body></html>')

    await expect(request.get('/dashboard')).rejects.toMatchObject({ status: 503 })

    expect(mocks.notifyError).toHaveBeenCalledWith('Service under maintenance, please try again later')
  })
})
