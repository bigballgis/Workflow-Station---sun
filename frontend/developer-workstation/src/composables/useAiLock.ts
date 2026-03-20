import { ref } from 'vue'
import type { LockInfo } from '@/types/aiGeneration'
import { aiGenerationApi } from '@/api/aiGeneration'

/**
 * Composable for managing AI generation lock acquire/release,
 * lock conflict handling, and force unlock interactions.
 */
export function useAiLock() {
  const lockInfo = ref<LockInfo | null>(null)
  const isLocked = ref(false)
  const lockConflict = ref(false)
  const conflictLockInfo = ref<LockInfo | null>(null)

  async function acquireLock(functionUnitId: number): Promise<boolean> {
    try {
      lockConflict.value = false
      conflictLockInfo.value = null

      const response = await aiGenerationApi.acquireLock(functionUnitId)
      lockInfo.value = response.data
      isLocked.value = true
      return true
    } catch (err: any) {
      if (err.response?.status === 409) {
        // Lock conflict — another user holds the lock
        lockConflict.value = true
        conflictLockInfo.value = err.response.data?.data || err.response.data || null
        isLocked.value = false
        return false
      }
      throw err
    }
  }

  async function releaseLock(functionUnitId: number): Promise<void> {
    try {
      await aiGenerationApi.releaseLock(functionUnitId)
    } finally {
      lockInfo.value = null
      isLocked.value = false
      lockConflict.value = false
      conflictLockInfo.value = null
    }
  }

  async function requestForceUnlock(functionUnitId: number): Promise<void> {
    await aiGenerationApi.requestForceUnlock(functionUnitId)
  }

  async function respondForceUnlock(functionUnitId: number, accept: boolean): Promise<void> {
    await aiGenerationApi.respondForceUnlock(functionUnitId, accept)
    if (accept) {
      // Lock was released by accepting force unlock
      lockInfo.value = null
      isLocked.value = false
    }
  }

  function reset() {
    lockInfo.value = null
    isLocked.value = false
    lockConflict.value = false
    conflictLockInfo.value = null
  }

  return {
    lockInfo,
    isLocked,
    lockConflict,
    conflictLockInfo,
    acquireLock,
    releaseLock,
    requestForceUnlock,
    respondForceUnlock,
    reset
  }
}
