import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  confirmUnsavedDesignerBeforeReload,
  registerUnsavedDesignerLeaveConfirmation,
} from '../useUnsavedDesignerNavigation'

describe('unsaved designer shell navigation', () => {
  afterEach(() => {
    registerUnsavedDesignerLeaveConfirmation(() => true)()
  })

  it('delegates a workspace reload to the mounted designer confirmation', async () => {
    const confirm = vi.fn(() => Promise.resolve(false))
    const unregister = registerUnsavedDesignerLeaveConfirmation(confirm)

    await expect(confirmUnsavedDesignerBeforeReload()).resolves.toBe(false)
    expect(confirm).toHaveBeenCalledOnce()
    unregister()
  })

  it('unregisters only the confirmation it registered', async () => {
    const unregisterFirst = registerUnsavedDesignerLeaveConfirmation(() => false)
    registerUnsavedDesignerLeaveConfirmation(() => true)
    unregisterFirst()

    await expect(confirmUnsavedDesignerBeforeReload()).resolves.toBe(true)
  })
})
