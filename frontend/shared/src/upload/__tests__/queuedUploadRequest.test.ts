import { describe, expect, it, vi } from 'vitest'
import { queuedUploadRequest } from '../queuedUploadRequest'

describe('queuedUploadRequest abort', () => {
  it('abort before send never calls onSuccess', async () => {
    const onSuccess = vi.fn()
    const onError = vi.fn()
    const xhr = queuedUploadRequest({
      action: 'http://127.0.0.1:1/does-not-exist',
      file: new File(['a'], 'a.txt', { type: 'text/plain' }),
      onSuccess,
      onError,
    })
    expect(xhr).toBeInstanceOf(XMLHttpRequest)
    xhr.abort()
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(onSuccess).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalled()
  })
})
