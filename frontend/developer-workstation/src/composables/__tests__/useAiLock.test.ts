import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useAiLock } from '@/composables/useAiLock'

const mockAcquireLock = vi.fn()
const mockReleaseLock = vi.fn()
const mockRequestForceUnlock = vi.fn()
const mockRespondForceUnlock = vi.fn()

vi.mock('@/api/aiGeneration', () => ({
  aiGenerationApi: {
    acquireLock: (...args: any[]) => mockAcquireLock(...args),
    releaseLock: (...args: any[]) => mockReleaseLock(...args),
    requestForceUnlock: (...args: any[]) => mockRequestForceUnlock(...args),
    respondForceUnlock: (...args: any[]) => mockRespondForceUnlock(...args),
  }
}))

describe('useAiLock', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('should initialize with default state', () => {
    const { lockInfo, isLocked, lockConflict, conflictLockInfo } = useAiLock()

    expect(lockInfo.value).toBeNull()
    expect(isLocked.value).toBe(false)
    expect(lockConflict.value).toBe(false)
    expect(conflictLockInfo.value).toBeNull()
  })

  it('acquireLock success should set isLocked=true', async () => {
    const lockData = {
      functionUnitId: 1,
      userId: 'user-1',
      userName: 'Test User',
      lockedAt: '2026-01-01T00:00:00Z',
      locked: true
    }
    mockAcquireLock.mockResolvedValue({ data: lockData })

    const { isLocked, lockConflict, acquireLock } = useAiLock()
    const result = await acquireLock(1)

    expect(result).toBe(true)
    expect(isLocked.value).toBe(true)
    expect(lockConflict.value).toBe(false)
    expect(mockAcquireLock).toHaveBeenCalledWith(1)
  })

  it('acquireLock 409 conflict should set lockConflict=true', async () => {
    const conflictData = {
      functionUnitId: 1,
      userId: 'other-user',
      userName: 'Other User',
      lockedAt: '2026-01-01T00:00:00Z'
    }
    mockAcquireLock.mockRejectedValue({
      response: { status: 409, data: { data: conflictData } }
    })

    const { isLocked, lockConflict, conflictLockInfo, acquireLock } = useAiLock()
    const result = await acquireLock(1)

    expect(result).toBe(false)
    expect(isLocked.value).toBe(false)
    expect(lockConflict.value).toBe(true)
    expect(conflictLockInfo.value).toEqual(conflictData)
  })

  it('acquireLock non-409 error should throw', async () => {
    mockAcquireLock.mockRejectedValue({
      response: { status: 500 }
    })

    const { acquireLock } = useAiLock()
    await expect(acquireLock(1)).rejects.toEqual({ response: { status: 500 } })
  })

  it('releaseLock should reset state', async () => {
    const lockData = {
      functionUnitId: 1,
      userId: 'user-1',
      userName: 'Test User',
      lockedAt: '2026-01-01T00:00:00Z',
      locked: true
    }
    mockAcquireLock.mockResolvedValue({ data: lockData })
    mockReleaseLock.mockResolvedValue({})

    const { isLocked, lockConflict, releaseLock, acquireLock } = useAiLock()

    await acquireLock(1)
    expect(isLocked.value).toBe(true)

    await releaseLock(1)
    expect(isLocked.value).toBe(false)
    expect(lockConflict.value).toBe(false)
    expect(mockReleaseLock).toHaveBeenCalledWith(1)
  })

  it('releaseLock should reset state even if API call fails', async () => {
    mockAcquireLock.mockResolvedValue({
      data: { functionUnitId: 1, userId: 'u1', userName: 'U', lockedAt: '', locked: true }
    })
    mockReleaseLock.mockRejectedValue(new Error('Network error'))

    const { isLocked, acquireLock, releaseLock } = useAiLock()

    await acquireLock(1)
    expect(isLocked.value).toBe(true)

    // releaseLock uses finally block, so state resets even on error
    await releaseLock(1).catch(() => {})
    expect(isLocked.value).toBe(false)
  })

  it('reset should clear all state', async () => {
    mockAcquireLock.mockResolvedValue({
      data: { functionUnitId: 1, userId: 'u1', userName: 'U', lockedAt: '', locked: true }
    })

    const { isLocked, lockInfo, lockConflict, conflictLockInfo, acquireLock, reset } = useAiLock()

    await acquireLock(1)
    expect(isLocked.value).toBe(true)

    reset()

    expect(lockInfo.value).toBeNull()
    expect(isLocked.value).toBe(false)
    expect(lockConflict.value).toBe(false)
    expect(conflictLockInfo.value).toBeNull()
  })

  it('requestForceUnlock should call API', async () => {
    mockRequestForceUnlock.mockResolvedValue({})

    const { requestForceUnlock } = useAiLock()
    await requestForceUnlock(1)

    expect(mockRequestForceUnlock).toHaveBeenCalledWith(1)
  })

  it('respondForceUnlock with accept=true should reset lock state', async () => {
    mockAcquireLock.mockResolvedValue({
      data: { functionUnitId: 1, userId: 'u1', userName: 'U', lockedAt: '', locked: true }
    })
    mockRespondForceUnlock.mockResolvedValue({})

    const { isLocked, acquireLock, respondForceUnlock } = useAiLock()

    await acquireLock(1)
    expect(isLocked.value).toBe(true)

    await respondForceUnlock(1, true)
    expect(isLocked.value).toBe(false)
    expect(mockRespondForceUnlock).toHaveBeenCalledWith(1, true)
  })

  it('respondForceUnlock with accept=false should keep lock state', async () => {
    mockAcquireLock.mockResolvedValue({
      data: { functionUnitId: 1, userId: 'u1', userName: 'U', lockedAt: '', locked: true }
    })
    mockRespondForceUnlock.mockResolvedValue({})

    const { isLocked, acquireLock, respondForceUnlock } = useAiLock()

    await acquireLock(1)
    expect(isLocked.value).toBe(true)

    await respondForceUnlock(1, false)
    // Lock should still be held since we rejected the force unlock
    expect(isLocked.value).toBe(true)
  })
})
