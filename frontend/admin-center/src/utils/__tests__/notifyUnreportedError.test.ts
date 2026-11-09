import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/types/errors'
import { notifyError } from '@/utils/notify'
import { notifyUnreportedError } from '../notifyUnreportedError'

vi.mock('@/utils/notify', () => ({ notifyError: vi.fn() }))

describe('notifyUnreportedError', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('stays quiet for an ApiError: the request layer has already shown the backend message', () => {
    notifyUnreportedError(new ApiError('INVALID_PARAMS', 400, 'Function unit status does not allow deployment'), 'Deploy failed')
    notifyUnreportedError(new ApiError('NETWORK_ERROR', 0), 'Deploy failed')
    expect(notifyError).not.toHaveBeenCalled()
  })

  it('stays quiet for a cancelled request and for the 401 that sends the user to login', () => {
    notifyUnreportedError({ code: 'ERR_CANCELED', name: 'CanceledError' }, 'Deploy failed')
    notifyUnreportedError({ response: { status: 401, data: { message: 'Token expired' } } }, 'Deploy failed')
    expect(notifyError).not.toHaveBeenCalled()
  })

  it('shows the fallback for an error the caller itself threw', () => {
    notifyUnreportedError(new TypeError('Cannot read properties of undefined'), 'Deploy failed')
    expect(notifyError).toHaveBeenCalledTimes(1)
    expect(notifyError).toHaveBeenCalledWith('Deploy failed')
  })

  it('shows the fallback for a value that is not an error object', () => {
    notifyUnreportedError(undefined, 'Failed')
    notifyUnreportedError(null, 'Failed')
    expect(notifyError).toHaveBeenCalledTimes(2)
  })
})
