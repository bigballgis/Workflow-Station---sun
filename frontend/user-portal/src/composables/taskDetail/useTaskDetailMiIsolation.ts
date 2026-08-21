import { collectLeafFormFieldKeys } from '@/components/formRendererHelpers'
import {
  mergeSubTableRowsByRowId,
  isMiParticipantScopedSubTableBinding,
  collectNestedSlicesForBindingFromSubTablesWalk,
  finalizeMiCollectionSubTableBindingRows,
  mergeMiCollectionSubTableRows,
  collapseMiLinkChildRowsToOnePerParticipant,
  backfillMiLinkChildPrimaryKeysFromVariables,
  filterRowsForMiParticipantSubTableBinding,
} from '@/composables/tasks/shared'
import {
  rowMatchesSubTablePrimaryKey,
  type MiParticipantRowId,
} from '@/composables/tasks/miSubProcessScope'
import {
  cloneSubTableRows,
  bindingIdsPreferStrictSubTableLookup,
  normalizeSubTableName,
} from './subTableRowUtils'
import { isEmptySeedableFormValue } from './seedTaskFormFromProcessValues'
import type { TaskDetailCtx } from './context'

export interface TaskDetailMiIsolationFns {
  isolateMiSubTaskData: (taskData: any) => Promise<void>
  applyMiParticipantFilterToCurrentSubTableBindings: (myRowId: MiParticipantRowId) => void
}

export function createTaskDetailMiIsolation(ctx: TaskDetailCtx): TaskDetailMiIsolationFns {
  const { subTableBindings, previousForms } = ctx
  const { formData, getCurrentFormFieldKeys } = ctx.taskForm

  async function isolateMiSubTaskData(taskData: any) {
    const myRowId = ctx.resolveCurrentMiParticipantRowIdFromTaskVars(taskData?.variables)
    if (myRowId == null) {
      return
    }

    ctx.warnMiCollectionPrimaryKeyIfNeeded()

    await ctx.seedMiParticipantScopedBindingForeignKeys(myRowId)

    const collectionPk = ctx.miCollectionPrimaryKeyFields()
    const ambiguousCurrentBindings = bindingIdsPreferStrictSubTableLookup(subTableBindings.value)

    // Multi-instance data isolation: only the **current** task form is scoped to this participant.
    // Previous-node forms stay read-only with full snapshot (other sub-tasks' sub form2 data must remain visible).
    for (const binding of subTableBindings.value) {
      if (ctx.isCurrentMiCollectionSubTableBinding(binding)) continue
      if (!isMiParticipantScopedSubTableBinding(binding)) continue
      const rows = Array.isArray(binding.data) ? binding.data : []
      binding.data = rows.filter((row: any) => ctx.rowBelongsToCurrentMiScope(row, myRowId, binding))
    }

    let myRow: any = undefined
    outer_myrow: for (const b of subTableBindings.value) {
      const rows = Array.isArray(b.data) ? b.data : []
      for (const row of rows) {
        if (
          rowMatchesSubTablePrimaryKey(row, myRowId, collectionPk) ||
          ctx.miRowBelongsToCurrentParticipant(row, myRowId, b)
        ) {
          myRow = row
          break outer_myrow
        }
      }
    }

    const originalFormData = { ...formData.value }

    /** Process variables often carry start-step scalars at top level; MI rows may omit them until persisted on the row. */
    if (myRow && typeof myRow === 'object') {
      const rec = myRow as Record<string, any>
      const seedFieldNames = new Set<string>()
      for (const binding of subTableBindings.value) {
        const cols = binding.columns as Array<{ field?: string }> | undefined
        if (Array.isArray(cols)) {
          for (const col of cols) {
            const fk = col?.field
            if (typeof fk === 'string' && fk.length > 0) seedFieldNames.add(fk)
          }
        }
        const ff = (binding as { formFields?: unknown }).formFields
        const walk = (arr: unknown) => {
          if (!Array.isArray(arr)) return
          for (const f of arr as Array<{ key?: unknown; children?: unknown; fields?: unknown }>) {
            if (f?.key != null && String(f.key).trim() !== '') seedFieldNames.add(String(f.key))
            walk(f.children)
            walk(f.fields)
          }
        }
        walk(ff)
      }
      for (const fk of seedFieldNames) {
        if (fk.startsWith('__')) continue
        const cur = rec[fk]
        if (cur != null && cur !== '') continue
        if (!Object.prototype.hasOwnProperty.call(originalFormData, fk)) continue
        const seed = originalFormData[fk]
        if (seed == null || seed === '') continue
        rec[fk] = seed
      }
    }

    const cleanedFormData: Record<string, any> = {}
    const systemKeys = Object.keys(originalFormData).filter(
      key =>
        key.startsWith('_') ||
        key.startsWith('__') ||
        key === 'initiator' ||
        key === 'meeting_id' ||
        key === 'mainRecordId' ||
        key === 'approval_result' ||
        key === 'approved'
    )
    for (const key of systemKeys) {
      cleanedFormData[key] = originalFormData[key]
    }

    const formKeys = getCurrentFormFieldKeys()
    const primaryKeys = ctx.getPrimaryTableFieldNames()
    for (const key of formKeys) {
      // Main PRIMARY scalars (e.g. id UUID) must stay on process variables — never copy MI collection row keys.
      if (primaryKeys.has(key)) {
        if (Object.prototype.hasOwnProperty.call(originalFormData, key)) {
          cleanedFormData[key] = originalFormData[key]
        } else {
          cleanedFormData[key] = null
        }
        continue
      }
      // Prefer non-empty MI row values; if the row only has an empty slot (common for
      // Start Process LOOKUP fields mirrored onto the assignee form), keep process/task
      // variables so readonly tags (stage / Test_status) are not wiped to "-".
      if (myRow && Object.prototype.hasOwnProperty.call(myRow, key)) {
        const fromRow = (myRow as Record<string, any>)[key]
        if (!isEmptySeedableFormValue(fromRow)) {
          cleanedFormData[key] = fromRow
        } else if (
          Object.prototype.hasOwnProperty.call(originalFormData, key) &&
          !isEmptySeedableFormValue(originalFormData[key])
        ) {
          cleanedFormData[key] = originalFormData[key]
        } else {
          cleanedFormData[key] = fromRow
        }
      } else if (Object.prototype.hasOwnProperty.call(originalFormData, key)) {
        cleanedFormData[key] = originalFormData[key]
      } else {
        cleanedFormData[key] = null
      }
    }

    // Preserve previous form field values (readonly display of parent task data)
    const prevFormFieldKeys = new Set<string>()
    previousForms.value.forEach((pf: any) => {
      for (const key of collectLeafFormFieldKeys(pf.fields || [], pf.tabs || [])) {
        prevFormFieldKeys.add(key)
      }
    })
    for (const key of prevFormFieldKeys) {
      if (!(key in cleanedFormData) && Object.prototype.hasOwnProperty.call(originalFormData, key)) {
        cleanedFormData[key] = originalFormData[key]
      }
    }

    /**
     * Designer-side diagnostic — surface main-form values that no MI sub-task or previous-form field
     * can receive. Two failure modes silently drop data without this warning:
     *   1. designer named the MI sub-task field differently from the main form (no implicit aliasing),
     *   2. BPMN parsing missed the upstream userTask (e.g. previousForms ended up empty) so the value
     *      is never displayed even read-only.
     * Only logged in dev to avoid noise in production; check console when fields appear blank.
     */
    if (import.meta.env.DEV) {
      const currentFormKeySet = new Set<string>(formKeys)
      const orphanFields: string[] = []
      for (const key of Object.keys(originalFormData)) {
        if (!key || key.startsWith('_') || key.startsWith('__')) continue
        if (key === 'initiator' || key === 'mainRecordId' || key === 'meeting_id'
            || key === 'approval_result' || key === 'approved') continue
        const value = originalFormData[key]
        if (value == null || value === '' || typeof value === 'function') continue
        if (currentFormKeySet.has(key) || prevFormFieldKeys.has(key)) continue
        orphanFields.push(key)
      }
      if (orphanFields.length > 0) {
        console.warn(
          '[MI sub-task] Main-form variables present but unreachable from the current sub-task form ' +
          'or any previous-form field. Designers: check that sub-task field keys match the main form, ' +
          'or that upstream userTasks have a formId attached. Orphan keys:',
          orphanFields,
          { taskId: (taskData?.id || taskData?.taskId), taskDefinitionKey: taskData?.taskDefinitionKey }
        )
      }
    }

    cleanedFormData.__subTables__ = ctx.rebuildIsolatedSubTablesPayload(myRowId)
    if (myRow && typeof myRow === 'object') {
      const rowRec = myRow as Record<string, unknown>
      const prevNestRaw = rowRec.__subTables__
      const prevNest: Record<string, unknown> =
        prevNestRaw && typeof prevNestRaw === 'object' ? { ...(prevNestRaw as object) } : {}
      const nextRowSub: Record<string, unknown> = {}
      for (const [k, v] of Object.entries(prevNest)) {
        if (Array.isArray(v)) {
          nextRowSub[k] = cloneSubTableRows(v as any[])
        }
      }
      const rebuilt = cleanedFormData.__subTables__ as Record<string, unknown>
      const origSt =
        originalFormData.__subTables__ && typeof originalFormData.__subTables__ === 'object'
          ? (originalFormData.__subTables__ as Record<string, unknown>)
          : null

      for (const binding of subTableBindings.value) {
        const forbid = ambiguousCurrentBindings.has(binding.bindingId)
        const saved = ctx.getSavedSubTableRows(rebuilt, binding, forbid)
        const rowsFromRebuilt = cloneSubTableRows(Array.isArray(saved) ? saved : [])
        const fromPrev = ctx.getSavedSubTableRows(nextRowSub as any, binding, forbid) ?? []
        const pk = binding.primaryKeyFields ?? null

        // Later operands win merge conflicts — put thin rebuilt first, richer prev/orig/nested last
        let merged = mergeSubTableRowsByRowId([], rowsFromRebuilt, pk)
        merged = mergeSubTableRowsByRowId(merged, fromPrev, pk)

        if (origSt) {
          const origSlice = ctx.getSavedSubTableRows(origSt, binding, forbid)
          if (Array.isArray(origSlice) && origSlice.length > 0) {
            const toMerge = isMiParticipantScopedSubTableBinding(binding)
              ? origSlice.filter((row: any) => ctx.rowBelongsToCurrentMiScope(row, myRowId, binding))
              : origSlice
            merged = mergeSubTableRowsByRowId(merged, cloneSubTableRows(toMerge), pk)
          }

          const nestedSlices = collectNestedSlicesForBindingFromSubTablesWalk(origSt, binding)
          for (const chunk of nestedSlices) {
            const toMerge = isMiParticipantScopedSubTableBinding(binding)
              ? (chunk as any[]).filter((row: any) => ctx.rowBelongsToCurrentMiScope(row, myRowId, binding))
              : (chunk as any[])
            if (toMerge.length === 0) continue
            merged = mergeSubTableRowsByRowId(merged, cloneSubTableRows(toMerge), pk)
          }
        }

        /**
         * {@code rowsFromRebuilt} carries previous-form snapshots (other participants' submitted rows) and
         * {@code fromPrev} is cloned from raw {@code variables.__subTables__} where slices may pool every
         * MI participant's data. Without filtering, the current participant's {@code __subTables__} (and the
         * link-form inline subtable2) would surface other sub-tasks' rows — see MI Subtask Demo where
         * sub form1 of participant 2 was pre-filled with participant 1's age/sex values.
         */
        const scopedMerged = isMiParticipantScopedSubTableBinding(binding)
          ? merged.filter((row: any) => ctx.rowBelongsToCurrentMiScope(row, myRowId, binding))
          : merged

        const tempBinding = { ...binding, data: scopedMerged }
        if (isMiParticipantScopedSubTableBinding(binding)) {
          backfillMiLinkChildPrimaryKeysFromVariables(
            [tempBinding as typeof binding],
            origSt ?? rebuilt,
            myRowId,
          )
        }
        const rows = cloneSubTableRows(
          ctx.isCurrentMiCollectionSubTableBinding(binding)
            ? finalizeMiCollectionSubTableBindingRows(scopedMerged, binding)
            : isMiParticipantScopedSubTableBinding(binding)
              ? collapseMiLinkChildRowsToOnePerParticipant(tempBinding.data)
              : scopedMerged,
        )
        nextRowSub[binding.bindingId] = rows
        nextRowSub[String(binding.bindingId)] = rows
        if (binding.tableName) {
          nextRowSub[binding.tableName] = rows
          nextRowSub[normalizeSubTableName(binding.tableName)] = rows
        }
      }
      rowRec.__subTables__ = nextRowSub
      for (const binding of subTableBindings.value) {
        const forbid = ambiguousCurrentBindings.has(binding.bindingId)
        const nestRows = ctx.getSavedSubTableRows(nextRowSub as any, binding, forbid)
        if (nestRows?.length) {
          binding.data = cloneSubTableRows(
            ctx.isCurrentMiCollectionSubTableBinding(binding)
              ? mergeMiCollectionSubTableRows([binding.data, nestRows], binding)
              : mergeSubTableRowsByRowId(binding.data, nestRows, binding.primaryKeyFields ?? null),
          )
        }
      }
    }
    formData.value = cleanedFormData
  }

  /**
   * {@link enrichChildBindingRowsFromParentsNestedSubTables} unions nested child slices from **every** peer parent
   * binding; in MI sub-tasks that can resurrect other instances' rows. Re-filter by {@link miRowBelongsToCurrentParticipant}.
   */
  function applyMiParticipantFilterToCurrentSubTableBindings(myRowId: MiParticipantRowId) {
    for (const binding of subTableBindings.value) {
      if (ctx.isCurrentMiCollectionSubTableBinding(binding)) continue
      if (!isMiParticipantScopedSubTableBinding(binding)) continue
      const rows = Array.isArray(binding.data) ? binding.data : []
      const scoped = rows.filter((row: any) => ctx.rowBelongsToCurrentMiScope(row, myRowId, binding))
      binding.data = cloneSubTableRows(filterRowsForMiParticipantSubTableBinding(scoped, binding))
    }
  }

  return {
    isolateMiSubTaskData,
    applyMiParticipantFilterToCurrentSubTableBindings,
  }
}
