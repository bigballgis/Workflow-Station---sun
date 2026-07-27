import { describe, expect, it } from 'vitest'
import { useBlockingProgress } from '../useBlockingProgress'

describe('useBlockingProgress', () => {
  it('opens with message and detail, then clears on close', () => {
    const api = useBlockingProgress()
    expect(api.visible.value).toBe(false)

    api.open('Creating…', 'Please wait')
    expect(api.visible.value).toBe(true)
    expect(api.message.value).toBe('Creating…')
    expect(api.detail.value).toBe('Please wait')

    api.setMessage('Saving…', 'Almost done')
    expect(api.message.value).toBe('Saving…')
    expect(api.detail.value).toBe('Almost done')

    api.close()
    expect(api.visible.value).toBe(false)
    expect(api.message.value).toBe('')
    expect(api.detail.value).toBe('')
  })
})
