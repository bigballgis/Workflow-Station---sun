import { ElMessage } from 'element-plus'
import { writeSubTableRows, subTableStoreKey } from '@/composables/tasks/subTableStore'
import {
  submitTaskForm as apiSubmitTaskForm,
} from '@/api/processForm'
import {
  mergeSubTableRowsByRowId,
  isMiParticipantScopedSubTableBinding,
  isMiDashboardSubTableBinding,
  isFileOnlySubTableBinding,
  backfillMiLinkChildPrimaryKeysFromVariables,
} from '@/composables/tasks/shared'
import { mergeSubTableRowsForMiSave } from '@/composables/tasks/miSubTableSaveMerge'
import {
  bindingMatchesMiSubTableName,
} from '@/composables/tasks/miSubProcessScope'
import {
  cloneSubTableRows,
  cloneSubTableBindings,
  bindingIdsPreferStrictSubTableLookup,
} from './subTableRowUtils'
import type { TaskDetailCtx } from './context'

export interface TaskDetailMiPersistFns {
  getPrimaryTableFieldNames: () => Set<string>
  protectMainRecordScalarsInSubmitPayload: (payload: { formData: Record<string, unknown> }) => void
  mergeMiParticipantScalarsFromForm: () => Promise<void>
  saveCurrentTaskFormWithMiPersist: () => Promise<void>
  openMiFillDialog: (row: any) => void
  syncMiFillSubTableRows: (bindingId: number, rows: any[]) => void
  saveMiFillDialog: () => Promise<void>
}

export function createTaskDetailMiPersist(ctx: TaskDetailCtx): TaskDetailMiPersistFns {
  const {
    t,
    taskInfo,
    effectiveTaskId,
    submitting,
    subTableBindings,
    isMiSubTaskMode,
    miSubProcessScope,
    miFullSubTablesSnapshotRef,
    miFillDialogVisible,
    miFillDialogData,
    miFillSubTableBindings,
    miFilled,
    miFillDialogReadOnly,
    primaryTableFieldNames,
    taskFormDTO,
  } = ctx
  const {
    formData,
    formReadOnly,
    savingTaskForm,
    buildCurrentTaskFormSubmitPayload,
    getCurrentFormFieldKeys,
  } = ctx.taskForm

  function getPrimaryTableFieldNames(): Set<string> {
    const v = primaryTableFieldNames.value
    return v instanceof Set ? v : new Set<string>()
  }

  function protectMainRecordScalarsInSubmitPayload(payload: { formData: Record<string, unknown> }) {
    const protectedKeys = getPrimaryTableFieldNames()
    if (protectedKeys.size === 0) return
    const vars = (taskInfo.value as { variables?: Record<string, unknown> })?.variables ?? {}
    const baseline = taskFormDTO.value?.fieldValues ?? {}
    for (const key of protectedKeys) {
      const canonical = vars[key] ?? baseline[key]
      if (canonical !== undefined && canonical !== null && canonical !== '') {
        payload.formData[key] = canonical
      }
    }
  }

  /** Persist editable MI sub-task scalars onto the participant collection row before Save (not top-level main id). */
  async function mergeMiParticipantScalarsFromForm() {
    const myRowId = ctx.currentMiRowId.value
    if (myRowId == null) return
    const primaryKeys = getPrimaryTableFieldNames()
    const formKeys = getCurrentFormFieldKeys()
    const miValues: Record<string, unknown> = {}
    for (const key of formKeys) {
      if (primaryKeys.has(key)) continue
      if (formData.value[key] !== undefined) {
        miValues[key] = formData.value[key]
      }
    }
    const mergeIntoRows = (rows: unknown[]) => {
      if (!Array.isArray(rows)) return
      for (const row of rows) {
        if (row && typeof row === 'object' && ctx.rowBelongsToCurrentMiScope(row, myRowId, {
          tableName: miSubProcessScope.value?.subTableName ?? 'subtable',
          primaryKeyFields: ctx.miCollectionPrimaryKeyFields(),
        })) {
          Object.assign(row as Record<string, unknown>, miValues)
        }
      }
    }
    for (const b of subTableBindings.value) {
      if (isMiParticipantScopedSubTableBinding(b) || bindingMatchesMiSubTableName(b, miSubProcessScope.value?.subTableName ?? '')) {
        mergeIntoRows(b.data)
      }
    }
    if (myRowId != null) {
      await ctx.seedMiParticipantScopedBindingForeignKeys(myRowId, { allocateMissingPrimaryKeys: true })
      backfillMiLinkChildPrimaryKeysFromVariables(
        subTableBindings.value,
        miFullSubTablesSnapshotRef.value ?? (formData.value.__subTables__ as Record<string, unknown>),
        myRowId,
      )
    }
    ctx.patchFormDataSubTablesFromCurrentBindings()
  }

  /**
   * #1446: merge the just-saved link-form rows (by row PK) back into the freshly reloaded
   * bindings. Update-or-append per binding's own slice; dashboard / file-only bindings are
   * skipped so MI collection statuses and attachments stay exactly as the reload produced them.
   */
  function reapplySavedLinkRowsToBindings(payload: { formData?: Record<string, unknown> }) {
    const saved = payload?.formData?.__subTables__ as Record<string, unknown> | undefined
    if (!saved || typeof saved !== 'object') return
    for (const binding of subTableBindings.value) {
      if (isMiDashboardSubTableBinding(binding) || isFileOnlySubTableBinding(binding)) continue
      if (!isMiParticipantScopedSubTableBinding(binding)) continue
      const savedRows = saved[String(binding.bindingId)]
      if (!Array.isArray(savedRows) || savedRows.length === 0) continue
      const pk = Array.isArray(binding.primaryKeyFields) && binding.primaryKeyFields.length > 0
        ? binding.primaryKeyFields
        : null
      binding.data = mergeSubTableRowsByRowId(
        Array.isArray(binding.data) ? cloneSubTableRows(binding.data) : [],
        cloneSubTableRows(savedRows as any[]),
        pk,
      )
    }
    ctx.patchFormDataSubTablesFromCurrentBindings()
  }

  async function saveCurrentTaskFormWithMiPersist() {
    if (formReadOnly.value || !effectiveTaskId.value) return
    if (isMiSubTaskMode.value) {
      await mergeMiParticipantScalarsFromForm()
    }
    savingTaskForm.value = true
    try {
      const payload = buildCurrentTaskFormSubmitPayload()
      if (isMiSubTaskMode.value) {
        protectMainRecordScalarsInSubmitPayload(payload)
      }
      await apiSubmitTaskForm(effectiveTaskId.value, payload)
      ElMessage.success(t('task.operationSuccess'))
      if (isMiSubTaskMode.value) {
        await ctx.loadTaskDetail()
        // #1446: deterministic last write — re-apply the server-confirmed saved link-form rows
        // after the in-place reload pipeline, so a stale prior-step/snapshot candidate can never
        // win the inline-form row pick (full refresh and in-place save then render identically).
        reapplySavedLinkRowsToBindings(payload)
      }
    } catch (error) {
      console.error('[TaskForm] save failed:', error)
      ElMessage.error(t('task.operationFailed'))
    } finally {
      savingTaskForm.value = false
    }
  }

  function openMiFillDialog(row: any) {
    miFillDialogData.value = { ...formData.value }
    miFillSubTableBindings.value = cloneSubTableBindings(subTableBindings.value)
    miFillDialogReadOnly.value = false
    miFillDialogVisible.value = true
  }

  /**
   * 归属谓词（participant-child 分片替换用）。解析不出当前参与者时返回 null，
   * 由 {@link mergeSubTableRowsForMiSave} 退回保守并集。
   */
  function ownRowPredicateFor(binding: unknown): ((row: unknown) => boolean) | null {
    const myRowId = ctx.currentMiRowId.value
    if (myRowId == null) return null
    return (row: unknown) => ctx.rowBelongsToCurrentMiScope(row, myRowId, binding as never)
  }

  function syncMiFillSubTableRows(bindingId: number, rows: any[]) {
    const target = miFillSubTableBindings.value.find(binding => binding.bindingId === bindingId)
    if (!target) return
    const nextRows = Array.isArray(rows) ? rows : []
    target.data = nextRows

    const subTables = { ...((miFillDialogData.value.__subTables__ as Record<string, any>) || {}) }
    const ambiguousMiDialog = bindingIdsPreferStrictSubTableLookup(miFillSubTableBindings.value)
    const existing = ctx.getSavedSubTableRows(subTables, target, ambiguousMiDialog.has(target.bindingId))
    const out = cloneSubTableRows(
      mergeSubTableRowsForMiSave(target as never, {
        existing,
        uiRows: nextRows,
        primaryKeyFields: target.primaryKeyFields,
        isOwnRow: ownRowPredicateFor(target),
      }) as any[],
    )
    writeSubTableRows(subTables, target, out)
    miFillDialogData.value = { ...miFillDialogData.value, __subTables__: subTables }
  }

  async function saveMiFillDialog() {
    const subTables = { ...((miFillDialogData.value.__subTables__ as Record<string, any>) || {}) }
    const subTableData: Record<string, Array<Record<string, unknown>>> = {}
    const ambiguousMiDialogSave = bindingIdsPreferStrictSubTableLookup(miFillSubTableBindings.value)

    // Persist MI form field values into the participant row so that
    // the backend stores the complete row and the Detail dialog in
    // completed-tasks view can render the filled data.
    if (isMiSubTaskMode.value) {
      const formKeys = getCurrentFormFieldKeys()
      const miValues: Record<string, any> = {}
      for (const key of formKeys) {
        if (miFillDialogData.value[key] !== undefined) {
          miValues[key] = miFillDialogData.value[key]
        }
      }
      const mergeIntoRows = (rows: any[]) => {
        if (!Array.isArray(rows)) return
        for (const row of rows) Object.assign(row, miValues)
      }
      for (const b of miFillSubTableBindings.value) mergeIntoRows(b.data)
    }

    // 本次提交里被主动删空的参与者切片 —— 与 buildSubTableSubmitPayload 同一套条件，
    // 否则「在这个弹窗里删掉自己最后一行」仍然会被后端按「切片没渲染」保住基线。
    const emptiedSubTableKeys: string[] = []

    for (const binding of miFillSubTableBindings.value) {
      const rows = cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
      // 合并规则见 mergeSubTableRowsForMiSave：shared 替换、participant-child 分片替换、判不出并集。
      const existing = ctx.getSavedSubTableRows(subTables, binding, ambiguousMiDialogSave.has(binding.bindingId))
      const isOwnRow = ownRowPredicateFor(binding)
      const out = cloneSubTableRows(
        mergeSubTableRowsForMiSave(binding as never, {
          existing,
          uiRows: rows,
          primaryKeyFields: binding.primaryKeyFields,
          isOwnRow,
        }) as any[],
      )
      const key = subTableStoreKey(binding)
      if (key) {
        subTables[key] = out
        subTableData[key] = out
        // 判据是「界面上属于我的行数为 0」，不是「切片为空」——切片里始终还有其他参与者的行。
        // 且必须「原本有我的行」，否则那不是删除。与 buildSubTableSubmitPayload 同一套条件。
        if (
          isMiSubTaskMode.value
          && isOwnRow != null
          && isMiParticipantScopedSubTableBinding(binding)
          && !out.some((row: unknown) => isOwnRow(row))
          && (Array.isArray(existing) ? existing.some((row: unknown) => isOwnRow(row)) : false)
        ) {
          emptiedSubTableKeys.push(key)
        }
      }
    }

    const nextFormData = { ...formData.value, ...miFillDialogData.value, __subTables__: subTables }

    submitting.value = true
    try {
      await apiSubmitTaskForm(effectiveTaskId.value, {
        formData: nextFormData,
        subTableData,
        baselineValues: taskFormDTO.value?.fieldValues || {},
        emptiedSubTableKeys,
      })
      formData.value = nextFormData
      miFilled.value = true
      miFillDialogVisible.value = false
      ElMessage.success(t('task.operationSuccess'))
    } catch (e) {
      ElMessage.error(t('task.operationFailed'))
    } finally {
      submitting.value = false
    }
  }

  return {
    getPrimaryTableFieldNames,
    protectMainRecordScalarsInSubmitPayload,
    mergeMiParticipantScalarsFromForm,
    saveCurrentTaskFormWithMiPersist,
    openMiFillDialog,
    syncMiFillSubTableRows,
    saveMiFillDialog,
  }
}
