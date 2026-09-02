import { ref, type Ref } from 'vue'
import { subTableStoreKey } from './subTableStore'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { submitTaskForm } from '@/api/processForm'
import type { FormField, FormTab } from '@/components/FormRenderer.vue'
import { collectLeafFormFieldKeys } from '@/components/formRendererHelpers'
import {
  cloneSubTableRows,
  mergeSubTableRowsByRowId,
  getSavedSubTableRows,
  flattenNestedSubTableRowsIntoPayload,
  scrubMiCorruptLinkChildRowsForParent,
  buildMiCollectionSliceKeySet,
  collapseMiLinkChildRowsToOnePerParticipant,
  isMiParticipantScopedSubTableBinding,
  isMiDashboardSubTableBinding,
  shouldSyncStaleSiblingSubTableSlice,
  syncMiLinkChildEditedRowsIntoSiblingSlices,
} from './shared'

function subTableSliceUnchanged(
  snapshot: Record<string, any>,
  bindingId: number,
  out: unknown[],
): boolean {
  const prev = snapshot[bindingId] ?? snapshot[String(bindingId)]
  try {
    return JSON.stringify(prev) === JSON.stringify(out)
  } catch {
    return false
  }
}

function stampBindingTableNameAliases(
  subTables: Record<string, any>,
  subTableData: Record<string, Array<Record<string, unknown>>>,
  binding: { tableName?: string; physicalTableName?: string },
  rows: unknown[],
) {
  // 规范 key：一张表一个 key。subTableData 是提交时的另一个字段（controller 会并进
  // __subTables__），用同一个 key 规则，避免两边再度分叉。
  const key = subTableStoreKey(binding)
  if (key) {
    subTables[key] = rows
    subTableData[key] = rows as Array<Record<string, unknown>>
  }
}

export function useTaskForm(options: {
  subTableBindings: Ref<any[]>
  isMiSubTaskMode: Ref<boolean>
  isCompletedTask: Ref<boolean>
  effectiveTaskId: Ref<string>
  taskFormDTO?: Ref<{ fieldValues?: Record<string, any> } | null>
  /** Binding-id → relation-table-id map; used to protect MI collection slices from id_idw scrub on save. */
  bindingRelationTableMap?: Ref<Map<number, number | null>>
  miSubProcessScopeName?: Ref<string | null | undefined>
  onFormReadOnlyChange?: (readonly: boolean) => void
}) {
  const { t } = useI18n()

  // Form state
  const formFields = ref<FormField[]>([])
  const formTabs = ref<FormTab[]>([])
  const formFieldsAfterTabs = ref<FormField[]>([])
  const formData = ref<Record<string, any>>({})
  const currentFormName = ref('')
  const formReadOnly = ref(false)
  const formLabelWidth = ref('160px')
  const formFormOptions = ref<Record<string, unknown>>({})
  const savingTaskForm = ref(false)
  const taskFormDTO = options.taskFormDTO ?? ref<{ fieldValues?: Record<string, any> } | null>(null)
  let subTableAutosaveTimer: ReturnType<typeof setTimeout> | null = null

  /** Assignment task: merge active binding rows into stale sibling slices for the same MI collection table only. */
  function syncStaleSiblingSubTableSlicesFromActiveBindings(
    subTables: Record<string, any>,
    bindings: Array<{
      bindingId: number
      primaryKeyFields?: string[] | null
      data?: unknown[]
      tableId?: number | null
      tableName?: string
      columns?: Array<{ field?: string }> | null
      formFields?: unknown[] | null
    }>,
    snapshot?: Record<string, any>,
  ) {
    const currentIds = new Set(bindings.map(b => Number(b.bindingId)))
    for (const binding of bindings) {
      const source =
        subTables[binding.bindingId] ??
        subTables[String(binding.bindingId)] ??
        binding.data
      if (!Array.isArray(source) || source.length === 0) continue
      const pk = Array.isArray(binding.primaryKeyFields) ? binding.primaryKeyFields : null
      const sourceHasForm = Array.isArray(binding.formFields) && binding.formFields.length > 0
      const sourceIsMiDashboard = isMiDashboardSubTableBinding(binding)
      const sourceChanged = snapshot ? !subTableSliceUnchanged(snapshot, binding.bindingId, source) : sourceHasForm
      for (const key of Object.keys(subTables)) {
        if (!/^\d+$/.test(key)) continue
        if (Number(key) === Number(binding.bindingId)) continue
        // Unchanged list-only rows still hold page-load N/Y; do not copy that onto the
        // live form slice. A binding whose data actually changed this Save is the source of truth.
        if (currentIds.has(Number(key)) && !sourceIsMiDashboard && !sourceChanged) continue
        const target = subTables[key]
        if (!Array.isArray(target) || target.length === 0) continue
        if (!shouldSyncStaleSiblingSubTableSlice(
          target,
          binding,
          bindings,
          key,
          options.bindingRelationTableMap?.value,
          source,
        )) continue
        subTables[key] = mergeSubTableRowsByRowId(target, source as any[], pk)
      }
    }
  }

  function buildSubTableSubmitPayload() {
    const snapshot = (formData.value.__subTables__ as Record<string, any>) || {}
    const subTables: Record<string, any> = { ...snapshot }
    flattenNestedSubTableRowsIntoPayload(subTables as Record<string, unknown>)
    let miParentIdIdw: string | number | null = null
    let miCollectionSliceKeys: Set<string> | null = null
    if (options.isMiSubTaskMode.value) {
      const ci = (formData.value._currentItem ?? formData.value.currentItem) as
        | { rowId?: string | number; rowKey?: { id?: string | number } }
        | undefined
      const parentIdIdw = ci?.rowId ?? ci?.rowKey?.id
      if (parentIdIdw != null && String(parentIdIdw).trim() !== '') {
        miParentIdIdw = parentIdIdw
        miCollectionSliceKeys = buildMiCollectionSliceKeySet(
          options.subTableBindings.value,
          options.bindingRelationTableMap?.value ?? new Map<number, number | null>(),
          options.miSubProcessScopeName?.value,
        )
        scrubMiCorruptLinkChildRowsForParent(subTables as Record<string, unknown>, parentIdIdw, {
          skipSliceKeys: miCollectionSliceKeys,
        })
      }
    }
    const subTableData: Record<string, Array<Record<string, unknown>>> = {}

    for (const binding of options.subTableBindings.value) {
      const rows = cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
      const existing = getSavedSubTableRows(subTables, binding)
      const merged = options.isMiSubTaskMode.value
        ? mergeSubTableRowsByRowId(
            existing,
            rows,
            Array.isArray((binding as { primaryKeyFields?: string[] }).primaryKeyFields)
              ? (binding as { primaryKeyFields?: string[] }).primaryKeyFields
              : null
          )
        : rows
      let out = cloneSubTableRows(
        options.isMiSubTaskMode.value && isMiParticipantScopedSubTableBinding(binding)
          ? collapseMiLinkChildRowsToOnePerParticipant(merged)
          : merged,
      )
      // #1446: live binding rows may still carry the #1435 corrupt id_idw mirror (rows created
      // before the seed-side guard, or hydrated from corrupt persisted slices) and would reinfect
      // the payload after the snapshot scrub above. Scrub the merged binding output too — link
      // bindings only, never collection slices.
      if (
        miParentIdIdw != null
        && miCollectionSliceKeys != null
        && !miCollectionSliceKeys.has(String(binding.bindingId))
      ) {
        const wrap: Record<string, unknown> = { rows: out }
        scrubMiCorruptLinkChildRowsForParent(wrap, miParentIdIdw, { skipSliceKeys: null })
        out = wrap.rows as typeof out
      }
      // One canonical key per designer table. The previous code also wrote the bindingId key and
      // then arbitrated, via `nameMissing` / `subTableSliceUnchanged`, which binding got to be the
      // last writer of the shared table-name alias — an arbitration only needed because several
      // bindings each held their own copy. With a single key there is no second copy to lose to,
      // so an unchanged list-only binding writing the same rows is a no-op rather than a clobber.
      stampBindingTableNameAliases(subTables, subTableData, binding, out)
    }

    if (!options.isMiSubTaskMode.value) {
      syncStaleSiblingSubTableSlicesFromActiveBindings(
        subTables,
        options.subTableBindings.value,
        snapshot,
      )
      for (const binding of options.subTableBindings.value) {
        if (!Array.isArray(binding.formFields) || binding.formFields.length === 0) continue
        const live = subTables[String(binding.bindingId)]
        if (!Array.isArray(live) || live.length === 0) continue
        stampBindingTableNameAliases(subTables, subTableData, binding, live)
      }
    } else {
      // #1446: link-form (People) edits must also reach the same relation table's stale sibling
      // slices (other nodes' binding ids), or reload hydrates the old value. Update-only by row PK;
      // MI collection slices stay excluded (09be69f8 / #1442 leak guards).
      const collectionSliceKeys = buildMiCollectionSliceKeySet(
        options.subTableBindings.value,
        options.bindingRelationTableMap?.value ?? new Map<number, number | null>(),
        options.miSubProcessScopeName?.value,
      )
      for (const binding of options.subTableBindings.value) {
        syncMiLinkChildEditedRowsIntoSiblingSlices(
          subTables,
          binding,
          subTables[String(binding.bindingId)],
          collectionSliceKeys,
        )
      }
    }

    return {
      formData: { __subTables__: subTables },
      subTableData
    }
  }

  function buildCurrentTaskFormSubmitPayload() {
    const subTablePayload = buildSubTableSubmitPayload()
    return {
      formData: {
        ...formData.value,
        ...subTablePayload.formData
      },
      subTableData: subTablePayload.subTableData,
      baselineValues: taskFormDTO.value?.fieldValues || {}
    }
  }

  async function saveCurrentTaskForm() {
    if (formReadOnly.value || !options.effectiveTaskId.value) return
    savingTaskForm.value = true
    try {
      const payload = buildCurrentTaskFormSubmitPayload()
      await submitTaskForm(options.effectiveTaskId.value, payload)
      // #1446: align local slices with what was just persisted; otherwise post-save
      // re-hydration (variables resync / polling) reverts the link form to the
      // page-load snapshot until a full refresh.
      formData.value = { ...formData.value, __subTables__: payload.formData.__subTables__ }
      ElMessage.success(t('task.operationSuccess'))
    } catch (error) {
      console.error('[TaskForm] save failed:', error)
      ElMessage.error(t('task.operationFailed'))
    } finally {
      savingTaskForm.value = false
    }
  }

  function scheduleSubTableAutosave() {
    if (formReadOnly.value || options.isCompletedTask.value || options.isMiSubTaskMode.value) return
    if (!options.effectiveTaskId.value) return
    if (subTableAutosaveTimer) clearTimeout(subTableAutosaveTimer)

    subTableAutosaveTimer = setTimeout(async () => {
      subTableAutosaveTimer = null
      try {
        await submitTaskForm(options.effectiveTaskId.value, {
          ...buildSubTableSubmitPayload(),
          baselineValues: {}
        })
      } catch (error) {
        console.error('[SubTable] autosave failed:', error)
      }
    }, 400)
  }

  function getCurrentFormFieldKeys(): string[] {
    const keys = new Set(collectLeafFormFieldKeys(formFields.value, formTabs.value))
    for (const key of collectLeafFormFieldKeys(formFieldsAfterTabs.value)) {
      keys.add(key)
    }
    return Array.from(keys)
  }

  function clearAutosaveTimer() {
    if (subTableAutosaveTimer) {
      clearTimeout(subTableAutosaveTimer)
      subTableAutosaveTimer = null
    }
  }

  return {
    formFields,
    formTabs,
    formFieldsAfterTabs,
    formData,
    currentFormName,
    formReadOnly,
    formLabelWidth,
    formFormOptions,
    savingTaskForm,
    taskFormDTO,
    saveCurrentTaskForm,
    buildCurrentTaskFormSubmitPayload,
    buildSubTableSubmitPayload,
    scheduleSubTableAutosave,
    getCurrentFormFieldKeys,
    clearAutosaveTimer
  }
}
