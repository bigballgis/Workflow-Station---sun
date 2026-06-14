import type { Ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'

// ---------------------------------------------------------------------------
// Task 7.5: Auto-save to localStorage
// ---------------------------------------------------------------------------

interface AutoSaveDeps {
  functionUnitId: () => string | undefined
  formId: () => string | undefined
  readonly: () => boolean
  formData: Ref<Record<string, any>>
  setInternalUpdate: (v: boolean) => void
  emitModelValue: (value: Record<string, any>) => void
  /** Re-run engine evaluation per restored field (Req 12.1, 12.2). */
  onRestored: (data: Record<string, any>) => void
}

export function useFormAutoSave(deps: AutoSaveDeps) {
  const { t } = useI18n()
  const AUTO_SAVE_INTERVAL = 30_000 // 30 seconds
  let autoSaveTimer: ReturnType<typeof setInterval> | null = null

  function getAutoSaveKey(): string | null {
    const functionUnitId = deps.functionUnitId()
    const formId = deps.formId()
    if (functionUnitId && formId) {
      return `form_autosave_${functionUnitId}_${formId}`
    }
    return null
  }

  function autoSave() {
    const key = getAutoSaveKey()
    if (!key || deps.readonly()) return
    try {
      localStorage.setItem(key, JSON.stringify(deps.formData.value))
    } catch (err) {
      console.warn('[FormRenderer] Auto-save to localStorage failed:', err)
    }
  }

  function startAutoSave() {
    stopAutoSave()
    if (getAutoSaveKey() && !deps.readonly()) {
      autoSaveTimer = setInterval(autoSave, AUTO_SAVE_INTERVAL)
    }
  }

  function stopAutoSave() {
    if (autoSaveTimer) {
      clearInterval(autoSaveTimer)
      autoSaveTimer = null
    }
  }

  function clearAutoSave() {
    const key = getAutoSaveKey()
    if (key) {
      try {
        localStorage.removeItem(key)
      } catch (err) {
        console.warn('[FormRenderer] Failed to clear auto-save:', err)
      }
    }
    stopAutoSave()
  }

  async function checkAutoSaveRestore() {
    const key = getAutoSaveKey()
    if (!key || deps.readonly()) return

    try {
      const saved = localStorage.getItem(key)
      if (!saved) return

      const savedData = JSON.parse(saved)
      if (!savedData || typeof savedData !== 'object') return

      await ElMessageBox.confirm(
        t('formRenderer.autoSaveRestorePrompt'),
        t('formRenderer.autoSaveTitle'),
        {
          confirmButtonText: t('formRenderer.restore'),
          cancelButtonText: t('formRenderer.discard'),
          type: 'info',
        }
      )
      // User chose to restore
      deps.setInternalUpdate(true)
      deps.formData.value = { ...deps.formData.value, ...savedData }
      setTimeout(() => { deps.setInternalUpdate(false) }, 0)
      deps.emitModelValue({ ...deps.formData.value })

      // Trigger engine re-evaluation for all restored fields (Req 12.1, 12.2)
      deps.onRestored(deps.formData.value)
    } catch {
      // User chose to discard or parse error — clear saved data
      clearAutoSave()
    }
  }

  return {
    getAutoSaveKey,
    autoSave,
    startAutoSave,
    stopAutoSave,
    clearAutoSave,
    checkAutoSaveRestore,
  }
}
