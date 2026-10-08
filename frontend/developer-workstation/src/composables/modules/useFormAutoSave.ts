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
  /** Reactive state for relation table views (triggers auto-save on change) */
  relationViewState: Ref<Record<string, any>>
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
    selectedForm, designerRef, relationViewState,
    getPollDesigner,
  } = options

  // --- State ---
  const isDirty = ref(false)

  // While a form switch is in flight (cleanup called, new rules not yet loaded) the canvas
  // still holds the PREVIOUS form's rules but selectedForm already points at the new one —
  // any save fired in that window persists table A's fields under form B. Suspend scheduling
  // until setupAutoSavePolling confirms the new form's rules are on the canvas.
  let suspended = false

  // Polling state
  const savedState = ref<string>('')
  const pollTimerRef = ref<ReturnType<typeof setInterval> | null>(null)

  // --- Functions ---

  function markSaved() {
    try {
      savedState.value = buildDesignerPollSnapshot()
      isDirty.value = false
    } catch {
      savedState.value = ''
      isDirty.value = false
    }
  }

  function cleanupAutoSavePolling() {
    // Refuse new dirty checks while a different form is being hydrated.
    suspended = true
    if (pollTimerRef.value) {
      clearInterval(pollTimerRef.value)
      pollTimerRef.value = null
    }
    savedState.value = ''
    isDirty.value = false
  }

  function resolvePollDesigner() {
    return getPollDesigner?.() ?? designerRef.value
  }

  function isDesignerPanelInputFocused(): boolean {
    if (typeof document === 'undefined') return false
    const active = document.activeElement
    if (!(active instanceof HTMLElement)) return false
    if (!active.matches('input, textarea, [contenteditable="true"]')) return false
    return !!active.closest('._fc-m-con, ._fc-r, ._fd-config, .form-editor-view')
  }

  function buildDesignerPollSnapshot(): string {
    const designer = resolvePollDesigner()
    const rawRule = stripFormCreateRulesDisabledDeep(designer?.getRule?.() || [])
    prepareFormCreateRulesForPersist(rawRule)
    const formOptions = serializeFormCreateOptionsForPersist(
      designer?.getOption?.() as Record<string, unknown> | undefined,
    )
    return JSON.stringify({
      rule: rawRule,
      options: formOptions,
      relationViews: relationViewState.value,
    })
  }

  function refreshDirtyState() {
    if (suspended || !selectedForm.value) return
    try {
      isDirty.value = buildDesignerPollSnapshot() !== savedState.value
    } catch { /* silently ignore */ }
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
      savedState.value = buildDesignerPollSnapshot()
      isDirty.value = false
      console.log('[FormDesigner] Change tracking started, initial state length:', savedState.value.length)
    } catch {
      savedState.value = ''
    }

    // Poll for changes every 3 seconds
    pollTimerRef.value = setInterval(() => {
      if (!selectedForm.value) return
      // A property-panel input is still being edited. Do not blur it or snapshot its
      // transient value; the next tick after the user clicks elsewhere will detect and save it.
      if (isDesignerPanelInputFocused()) return
      try {
        refreshDirtyState()
      } catch { /* silently ignore */ }
    }, POLL_INTERVAL_MS)
  }

  // --- Cleanup ---
  onUnmounted(() => {
    cleanupAutoSavePolling()
  })

  // --- Watcher ---
  watch(
    relationViewState,
    () => {
      refreshDirtyState()
    },
    { deep: true }
  )

  return {
    isDirty,
    markSaved,
    refreshDirtyState,
    setupAutoSavePolling,
    cleanupAutoSavePolling,
  }
}
