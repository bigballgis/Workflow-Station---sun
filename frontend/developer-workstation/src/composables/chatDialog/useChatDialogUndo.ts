import { ref } from 'vue'
import { aiGenerationApi } from '@/api/aiGeneration'

/**
 * Task 17.3: Undo countdown for the most recent apply.
 *
 * Owns the countdown timer + undo API call. Behavior is unchanged from the
 * previous inline implementation: a 30s countdown, undo posts to the backend
 * and asks the caller to refresh via `onUndone`.
 *
 * @param functionUnitId reactive getter for the current function unit id
 * @param t              vue-i18n translate function
 * @param onUndone       invoked after a successful undo (caller refreshes data)
 */
export function useChatDialogUndo(
  functionUnitId: () => number,
  t: (key: string) => string,
  onUndone: () => void
) {
  const showUndoButton = ref(false)
  const undoCountdown = ref(0)
  let undoTimer: ReturnType<typeof setInterval> | null = null

  function startUndoCountdown() {
    showUndoButton.value = true
    undoCountdown.value = 30
    if (undoTimer) clearInterval(undoTimer)
    undoTimer = setInterval(() => {
      undoCountdown.value--
      if (undoCountdown.value <= 0) {
        showUndoButton.value = false
        if (undoTimer) {
          clearInterval(undoTimer)
          undoTimer = null
        }
      }
    }, 1000)
  }

  async function handleUndo() {
    try {
      await aiGenerationApi.undoLastApply(functionUnitId())
      showUndoButton.value = false
      if (undoTimer) {
        clearInterval(undoTimer)
        undoTimer = null
      }
      ElMessage.success(t('ai.undo.success'))
      onUndone() // Refresh data
    } catch {
      ElMessage.error(t('ai.error.AI_UNDO_EXPIRED'))
    }
  }

  function clearUndoTimer() {
    if (undoTimer) {
      clearInterval(undoTimer)
      undoTimer = null
    }
  }

  return {
    showUndoButton,
    undoCountdown,
    startUndoCountdown,
    handleUndo,
    clearUndoTimer
  }
}
