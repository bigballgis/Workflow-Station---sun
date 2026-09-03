import { ref, watch, onUnmounted } from 'vue'
import type { Ref } from 'vue'
import {
  prepareFormCreateRulesForPersist,
  serializeFormCreateOptionsForPersist,
} from '@/utils/formCreateDefaultEvents'
import { stripFormCreateRulesDisabledDeep } from '@/utils/formCreateRuleUtils'

export interface FormAutoSaveOptions {
  /** Reactive ref pointing to the currently selected form */
  selectedForm: Ref<any>
  /** Template ref to the main fc-designer component */
  designerRef: Ref<{ getRule: () => any[]; getOption?: () => Record<string, unknown> } | undefined>
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
  /**
   * Copy pending right-panel edits (Validate / props) onto the live canvas rule
   * before snapshotting. Auto-save polls getRule(), which otherwise misses
   * Validation+ rows that have not been blurred/emitted yet.
   */
  flushPendingCanvasEdits?: () => void
  /**
   * Designer whose getRule()/getOption() feed the poll snapshot. Defaults to
   * designerRef (main canvas). Pass the active tab instance so sub-table
   * Form Design Validation+ edits are detected.
   */
  getPollDesigner?: () => { getRule?: () => unknown[]; getOption?: () => Record<string, unknown> } | null | undefined
}

/**
 * How often the canvas is snapshotted to detect design changes. Each tick walks the whole
 * rule tree and serializes it, so this is the designer's steady-state background cost —
 * keep it comfortably above a keystroke and well under the save debounce below.
 */
const POLL_INTERVAL_MS = 3000

export function useFormAutoSave(options: FormAutoSaveOptions) {
  const {
    selectedForm, designerRef, handleSaveForm, relationViewState, t, autoSaving,
    flushPendingCanvasEdits, getPollDesigner,
  } = options

  // --- State ---
  let autoSaveTimer: ReturnType<typeof setTimeout> | null = null

  // While a form switch is in flight (cleanup called, new rules not yet loaded) the canvas
  // still holds the PREVIOUS form's rules but selectedForm already points at the new one —
  // any save fired in that window persists table A's fields under form B. Suspend scheduling
  // until setupAutoSavePolling confirms the new form's rules are on the canvas.
  let suspended = false

  // Polling state
  const lastDesignerState = ref<string>('')
  const pollTimerRef = ref<ReturnType<typeof setInterval> | null>(null)

  // --- Functions ---

  function cancelPendingAutoSave() {
    if (autoSaveTimer) {
      clearTimeout(autoSaveTimer)
      autoSaveTimer = null
    }
  }

  function scheduleAutoSave() {
    if (suspended) return
    cancelPendingAutoSave()
    autoSaveTimer = setTimeout(() => {
      console.log('[FormDesigner] Auto-save triggered')
      handleSaveForm(false)
    }, 5000)
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
    // Cancel the pending debounce too and refuse new schedules: a timer armed by edits on
    // the previous form must never fire after selectedForm has moved to another form.
    suspended = true
    cancelPendingAutoSave()
    if (pollTimerRef.value) {
      clearInterval(pollTimerRef.value)
      pollTimerRef.value = null
    }
    lastDesignerState.value = ''
  }

  function resolvePollDesigner() {
    return getPollDesigner?.() ?? designerRef.value
  }

  function buildDesignerPollSnapshot(): string {
    flushPendingCanvasEdits?.()
    const designer = resolvePollDesigner()
    const rawRule = stripFormCreateRulesDisabledDeep(designer?.getRule?.() || [])
    prepareFormCreateRulesForPersist(rawRule)
    const formOptions = serializeFormCreateOptionsForPersist(
      designer?.getOption?.() as Record<string, unknown> | undefined,
    )
    return JSON.stringify({ rule: rawRule, options: formOptions })
  }

  function setupAutoSavePolling() {
    cleanupAutoSavePolling()
    // The new form's rules are on the canvas now — saves are safe again.
    suspended = false

    if (!selectedForm.value || !designerRef.value) {
      console.log('[FormDesigner] Auto-save polling skipped: no form or designer ref')
      return
    }

    // Initialize the state tracker with current rule + form-level options (events)
    try {
      lastDesignerState.value = buildDesignerPollSnapshot()
      console.log('[FormDesigner] Auto-save polling started, initial state length:', lastDesignerState.value.length)
    } catch {
      lastDesignerState.value = ''
    }

    // Poll for changes every 3 seconds
    pollTimerRef.value = setInterval(() => {
      if (!selectedForm.value || autoSaving.value) return
      try {
        const currentState = buildDesignerPollSnapshot()
        if (currentState !== lastDesignerState.value) {
          lastDesignerState.value = currentState
          console.log('[FormDesigner] Change detected, scheduling auto-save')
          scheduleAutoSave()
        }
      } catch { /* silently ignore */ }
    }, POLL_INTERVAL_MS)
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
