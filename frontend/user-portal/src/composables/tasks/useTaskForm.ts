import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { submitTaskForm } from '@/api/processForm'
import type { FormField, FormTab } from '@/components/FormRenderer.vue'
import { collectLeafFormFieldKeys } from '@/components/formRendererHelpers'
import {
  cloneSubTableRows,
  mergeSubTableRowsByRowId,
  getSavedSubTableRows,
  normalizeSubTableName,
  flattenNestedSubTableRowsIntoPayload,
  scrubMiCorruptLinkChildRowsForParent,
  buildMiCollectionSliceKeySet,
  collapseMiLinkChildRowsToOnePerParticipant,
  isMiParticipantScopedSubTableBinding,
  shouldSyncStaleSiblingSubTableSlice,
  syncMiLinkChildEditedRowsIntoSiblingSlices,
} from './shared'

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
    }>,
  ) {
    for (const binding of bindings) {
      const source =
        subTables[binding.bindingId] ??
        subTables[String(binding.bindingId)] ??
        binding.data
      if (!Array.isArray(source) || source.length === 0) continue
      const pk = Array.isArray(binding.primaryKeyFields) ? binding.primaryKeyFields : null
      for (const key of Object.keys(subTables)) {
        if (!/^\d+$/.test(key)) continue
        if (Number(key) === Number(binding.bindingId)) continue
        const target = subTables[key]
        if (!Array.isArray(target) || target.length === 0) continue
        if (!shouldSyncStaleSiblingSubTableSlice(target, binding, bindings, key)) continue
        subTables[key] = mergeSubTableRowsByRowId(target, source as any[], pk)
      }
    }
  }

  function buildSubTableSubmitPayload() {
    const subTables: Record<string, any> = { ...((formData.value.__subTables__ as Record<string, any>) || {}) }
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
      subTables[binding.bindingId] = out
      subTables[String(binding.bindingId)] = out
      subTableData[String(binding.bindingId)] = out
      if (binding.tableName) {
        subTables[binding.tableName] = out
        subTables[normalizeSubTableName(binding.tableName)] = out
        subTableData[binding.tableName] = out
      }
    }

    if (!options.isMiSubTaskMode.value) {
      syncStaleSiblingSubTableSlicesFromActiveBindings(
        subTables,
        options.subTableBindings.value,
      )
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
