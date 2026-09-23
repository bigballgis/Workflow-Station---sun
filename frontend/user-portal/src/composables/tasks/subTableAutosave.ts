import type { Ref } from 'vue'
import { getTaskFormData, submitTaskForm } from '@/api/processForm'
import { unwrapPortalApiPayload } from '@/utils/httpErrorMessage'
import { applyServerRowVersionsToLiveRows } from './subTableRowVersionSync'

const AUTOSAVE_DELAY_MS = 400

interface SubTableSubmitPayload {
  formData: { __subTables__?: Record<string, unknown> }
  subTableBindingScopes: unknown[]
}

interface StoredTaskForm {
  fieldValues?: Record<string, unknown>
  /** Present when this stage has no task form and the live store is on the process form. */
  processFormRef?: { fieldValues?: Record<string, unknown> }
}

/**
 * Sub-table autosave, plus a submit-time version sync so Complete does not
 * need a browser reload after autosave bumped `_wsRowVersion`.
 */
export function createSubTableAutosave(deps: {
  formReadOnly: Ref<boolean>
  isCompletedTask: Ref<boolean>
  isMiSubTaskMode: Ref<boolean>
  effectiveTaskId: Ref<string>
  /** Binding rows are untyped at the task-form boundary; this list is that same ref. */
  subTableBindings: Ref<any[]>
  formData: Ref<Record<string, any>>
  taskFormDTO: Ref<StoredTaskForm | null>
  loadedSubTableBaseline: Ref<Record<string, unknown>>
  buildSubTableSubmitPayload: () => SubTableSubmitPayload
  delayMs?: number
}) {
  let timer: ReturnType<typeof setTimeout> | null = null
  let inflight: Promise<void> | null = null
  let paused = false
  const delayMs = deps.delayMs ?? AUTOSAVE_DELAY_MS

  function clearAutosaveTimer(): void {
    if (!timer) return
    clearTimeout(timer)
    timer = null
  }

  function scheduleSubTableAutosave(): void {
    if (paused || deps.formReadOnly.value || deps.isCompletedTask.value || deps.isMiSubTaskMode.value) return
    if (!deps.effectiveTaskId.value) return
    clearAutosaveTimer()
    timer = setTimeout(() => {
      timer = null
      if (paused) return
      const run = runAutosave()
      inflight = run.finally(() => {
        if (inflight === run) inflight = null
      })
    }, delayMs)
  }

  function resumeSubTableAutosave(): void {
    paused = false
  }

  async function syncSubTableRowVersionsBeforeSubmit(): Promise<void> {
    if (deps.formReadOnly.value || !deps.effectiveTaskId.value) return
    if (!deps.subTableBindings.value.length) return
    paused = true
    clearAutosaveTimer()
    if (inflight) await inflight
    const stored = await readStoredSubTables()
    if (!stored) return
    applyServerRowVersionsToLiveRows({
      bindings: deps.subTableBindings.value,
      formSubTables: deps.formData.value.__subTables__ as Record<string, unknown> | undefined,
      baseline: deps.loadedSubTableBaseline.value,
      serverSubTables: stored,
    })
  }

  async function runAutosave(): Promise<void> {
    try {
      const payload = deps.buildSubTableSubmitPayload()
      await submitTaskForm(deps.effectiveTaskId.value, {
        ...payload,
        baselineValues: {},
      })
      if (payload.subTableBindingScopes.length) await refreshSavedSubTableVersions()
    } catch (error) {
      console.error('[SubTable] autosave failed:', error)
    }
  }

  async function refreshSavedSubTableVersions(): Promise<void> {
    const { dto, stored } = await readSnapshot()
    // Baseline follows the server snapshot (deletion claims need its versions).
    // Binding rows stay as the page holds them — replacing them with the unsliced
    // store makes every filter binding claim sibling rows on the next save.
    deps.taskFormDTO.value = dto
    // Assigning the DTO recaptures baseline from task-form field values. A review
    // stage has no task form, so that recapture is empty; keep the store we just read.
    deps.loadedSubTableBaseline.value = JSON.parse(JSON.stringify(stored)) as Record<string, unknown>
    applyServerRowVersionsToLiveRows({
      bindings: deps.subTableBindings.value,
      formSubTables: deps.formData.value.__subTables__ as Record<string, unknown> | undefined,
      baseline: deps.loadedSubTableBaseline.value,
      serverSubTables: stored,
    })
  }

  async function readStoredSubTables(): Promise<Record<string, unknown> | null> {
    const dto = unwrapPortalApiPayload(await getTaskFormData(deps.effectiveTaskId.value)) as StoredTaskForm | null
    return storedSubTables(dto)
  }

  async function readSnapshot(): Promise<{ dto: StoredTaskForm; stored: Record<string, unknown> }> {
    const dto = unwrapPortalApiPayload(await getTaskFormData(deps.effectiveTaskId.value)) as StoredTaskForm | null
    const stored = storedSubTables(dto)
    if (!dto || !stored) throw new Error('Saved sub-table snapshot is missing')
    return { dto, stored }
  }

  return {
    scheduleSubTableAutosave,
    clearAutosaveTimer,
    refreshSavedSubTableVersions,
    syncSubTableRowVersionsBeforeSubmit,
    resumeSubTableAutosave,
  }
}

function storedSubTables(dto: StoredTaskForm | null): Record<string, unknown> | null {
  return subTableMap(dto?.fieldValues?.__subTables__)
    ?? subTableMap(dto?.processFormRef?.fieldValues?.__subTables__)
}

function subTableMap(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  return value as Record<string, unknown>
}
