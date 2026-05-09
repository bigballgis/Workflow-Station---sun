import { ref, watch, onUnmounted } from 'vue'
import type { Ref } from 'vue'

export interface FormAutoSaveOptions {
  /** Reactive ref pointing to the currently selected form */
  selectedForm: Ref<any>
  /** Template ref to the main fc-designer component */
  designerRef: Ref<{ getRule: () => any[] } | undefined>
  /** The save function to call on auto-save (should set autoSaving appropriately) */
  handleSaveForm: (isManual: boolean) => Promise<void>
  /** Reactive state for relation table views (triggers auto-save on change) */
  relationViewState: Ref<Record<string, any>>
  /** i18n translate function */
  t: (key: string, options?: Record<string, any>) => string
  /** External ref for autoSaving — mutated by handleSaveForm, read by polling */
  autoSaving: Ref<boolean>
  /** External ref for lastAutoSaveTime — set after successful save */
  lastAutoSaveTime: Ref<Date | null>
}

export function useFormAutoSave(options: FormAutoSaveOptions) {
  const { selectedForm, designerRef, handleSaveForm, relationViewState, t, autoSaving, lastAutoSaveTime } = options

  // --- State ---
  let autoSaveTimer: ReturnType<typeof setTimeout> | null = null

  // Polling state
  const lastDesignerState = ref<string>('')
  const pollTimerRef = ref<ReturnType<typeof setInterval> | null>(null)

  // --- Functions ---

  function scheduleAutoSave() {
    if (autoSaveTimer) {
      clearTimeout(autoSaveTimer)
    }
    autoSaveTimer = setTimeout(() => {
      console.log('[FormDesigner] Auto-save triggered')
      handleSaveForm(false)
    }, 2000)
  }

  function formatAutoSaveTime(time: Date): string {
    const now = new Date()
    const diff = Math.floor((now.getTime() - time.getTime()) / 1000)
    if (diff < 60) {
      return t('process.justNow')
    } else if (diff < 3600) {
      const minutes = Math.floor(diff / 60)
      return t('process.minutesAgo', { count: minutes })
    } else {
      return time.toLocaleTimeString()
    }
  }

  function cleanupAutoSavePolling() {
    if (pollTimerRef.value) {
      clearInterval(pollTimerRef.value)
      pollTimerRef.value = null
    }
    lastDesignerState.value = ''
  }

  function setupAutoSavePolling() {
    cleanupAutoSavePolling()

    if (!selectedForm.value || !designerRef.value) {
      console.log('[FormDesigner] Auto-save polling skipped: no form or designer ref')
      return
    }

    // Initialize the state tracker with current rule
    try {
      lastDesignerState.value = JSON.stringify(designerRef.value.getRule() || [])
      console.log('[FormDesigner] Auto-save polling started, initial state length:', lastDesignerState.value.length)
    } catch {
      lastDesignerState.value = ''
    }

    // Poll for changes every 1 second
    pollTimerRef.value = setInterval(() => {
      if (!selectedForm.value || autoSaving.value) return
      try {
        const currentRule = JSON.stringify(designerRef.value?.getRule() || [])
        if (currentRule !== lastDesignerState.value) {
          lastDesignerState.value = currentRule
          console.log('[FormDesigner] Change detected, scheduling auto-save')
          scheduleAutoSave()
        }
      } catch { /* silently ignore */ }
    }, 1000)
  }

  // --- Cleanup ---
  onUnmounted(() => {
    if (autoSaveTimer) {
      clearTimeout(autoSaveTimer)
      autoSaveTimer = null
    }
    cleanupAutoSavePolling()
  })

  // --- Watcher ---
  watch(
    relationViewState,
    () => {
      if (selectedForm.value) {
        scheduleAutoSave()
      }
    },
    { deep: true }
  )

  return {
    formatAutoSaveTime,
    scheduleAutoSave,
    setupAutoSavePolling,
    cleanupAutoSavePolling,
  }
}
