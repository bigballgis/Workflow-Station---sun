import { ref, type Ref } from 'vue'
import { getTaskFormData, submitTaskForm, type TaskFormSubmitRequest } from '@/api/processForm'
import { unwrapPortalApiPayload } from '@/utils/httpErrorMessage'
import { applyServerRowVersionsToLiveRows } from './subTableRowVersionSync'

const AUTOSAVE_DELAY_MS = 400

interface SubTableSubmitPayload {
  formData: Record<string, unknown> & { __subTables__?: Record<string, unknown> }
  subTableBindingScopes: NonNullable<TaskFormSubmitRequest['subTableBindingScopes']>
  subTableBatchChanges?: TaskFormSubmitRequest['subTableBatchChanges']
}

interface StoredTaskForm {
  fieldValues?: Record<string, unknown>
  /** Present when this stage has no task form and the live store is on the process form. */
  processFormRef?: { fieldValues?: Record<string, unknown> }
}

/**
 * Serializes sub-table saves, refreshes their versions and refuses Complete
 * while a save has failed. Read versions are never refreshed merely to submit.
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
  onSaved?: () => void
  beginSave?: (payload: SubTableSubmitPayload) => () => void
  onError?: () => void
  delayMs?: number
}) {
  let timer: ReturnType<typeof setTimeout> | null = null
  let inflight: Promise<void> | null = null
  let paused = false
  const saveError = ref<unknown>(null)
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
      const previous = inflight
      const run = (async () => {
        if (previous) await previous
        if (!paused) await runAutosave()
      })()
      const tracked = run.finally(() => {
        if (inflight === tracked) inflight = null
      })
      inflight = tracked
    }, delayMs)
  }

  function resumeSubTableAutosave(): void {
    paused = false
  }

  function retrySubTableAutosave(): void {
    resumeSubTableAutosave()
    scheduleSubTableAutosave()
  }

  async function syncSubTableRowVersionsBeforeSubmit(): Promise<void> {
    if (deps.formReadOnly.value || !deps.effectiveTaskId.value) return
    if (!deps.subTableBindings.value.length) return
    await pauseSubTableAutosave()
    if (saveError.value) throw saveError.value
    // Successful saves already refresh their versions. A fresh GET here would
    // give stale business values a newer version and hide concurrent edits.
  }

  async function pauseSubTableAutosave(): Promise<void> {
    paused = true
    clearAutosaveTimer()
    if (inflight) await inflight
  }

  async function runAutosave(): Promise<void> {
    let restore: (() => void) | undefined
    let persisted = false
    try {
      const payload = deps.buildSubTableSubmitPayload()
      restore = deps.beginSave?.(payload)
      await submitTaskForm(deps.effectiveTaskId.value, {
        ...payload,
        baselineValues: {},
      })
      persisted = true
      saveError.value = null
      deps.onSaved?.()
      if (payload.subTableBindingScopes.length) await refreshSavedSubTableVersions(payload)
    } catch (error) {
      saveError.value = error
      if (!persisted) restore?.()
      deps.onError?.()
      console.error('[SubTable] autosave failed:', error)
    }
  }

  async function refreshSavedSubTableVersions(saved?: SubTableSubmitPayload): Promise<void> {
    const { dto, stored } = await readSnapshot()
    if (saved) assertSavedVersions(saved, stored)
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

  async function readSnapshot(): Promise<{ dto: StoredTaskForm; stored: Record<string, unknown> }> {
    const dto = unwrapPortalApiPayload(await getTaskFormData(deps.effectiveTaskId.value)) as StoredTaskForm | null
    const stored = storedSubTables(dto)
    if (!dto || !stored) throw new Error('Saved sub-table snapshot is missing')
    return { dto, stored }
  }

  return {
    saveError,
    retrySubTableAutosave,
    scheduleSubTableAutosave,
    clearAutosaveTimer,
    refreshSavedSubTableVersions,
    syncSubTableRowVersionsBeforeSubmit,
    resumeSubTableAutosave,
    pauseSubTableAutosave,
  }
}

/** The scope guard increments each claimed row once. Never adopt a later writer's version. */
function assertSavedVersions(saved: SubTableSubmitPayload, stored: Record<string, unknown>): void {
  for (const scope of saved.subTableBindingScopes) {
    const sent = saved.formData.__subTables__?.[scope.storeKey]
    const server = stored[scope.storeKey]
    if (!Array.isArray(sent) || !Array.isArray(server)) throw new Error('Saved sub-table snapshot is missing')
    const find = (rows: unknown[], key: Record<string, unknown>) => rows.find(raw => raw && typeof raw === 'object'
      && Object.entries(key).every(([field, value]) => String((raw as Record<string, unknown>)[field]) === String(value))) as Record<string, unknown> | undefined
    for (const key of scope.rowKeys) {
      const before = find(sent, key)
      const after = find(server, key)
      if (!before || !after || Number(after._wsRowVersion) !== Number(before._wsRowVersion ?? 0) + 1) {
        throw new Error('Sub-table changed after this save; reload before making further changes')
      }
    }
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
