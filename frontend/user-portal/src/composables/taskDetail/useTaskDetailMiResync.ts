import { writeSubTableRows, subTableStoreKey } from '@/composables/tasks/subTableStore'
import {
  mergeSubTableRowsByRowId,
  coerceSubTablesVariableToMap,
  hydrateChildSubTablesFromParentsNestedRows,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
  enrichChildBindingRowsFromParentsNestedSubTables,
  stripNestedSubTablesFromRows,
  collectSubTableSliceRowsForRelationTableId,
  scrubMiCorruptLinkChildRowsForParent,
  buildMiCollectionSliceKeySet,
  mergeAllSubTableSlicesFromVariables,
  mergeAllSlicesForSharedProcessSubTableBinding,
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  applySharedAttachmentFinalizeAndMaterialize,
  isSharedAttachmentFileBinding,
  finalizeSharedProcessSubTableBindingRows,
  finalizeMiCollectionSubTableBindingRows,
  mergeMiCollectionSubTableRows,
  collapseMiLinkChildRowsToOnePerParticipant,
  backfillMiLinkChildPrimaryKeysFromVariables,
  miChildFkConfigOfBinding,
  repairMisassignedLinkChildStructuralFk,
} from '@/composables/tasks/shared'
import {
  rowMatchesSubTablePrimaryKey,
  type MiParticipantRowId,
} from '@/composables/tasks/miSubProcessScope'
import {
  cloneSubTableRows,
  cloneAndFlattenSubTablesMap,
  bindingIdsPreferStrictSubTableLookup,
  subTableBindingMatches,
  normalizeSubTableName,
} from './subTableRowUtils'
import type { TaskDetailCtx } from './context'

export interface TaskDetailMiResyncFns {
  resyncMiParticipantSubTablesFromVariables: (
    myRowId: MiParticipantRowId,
    subTablesSource?: Record<string, unknown> | null,
    preFlattened?: Record<string, unknown>,
  ) => Promise<void>
  mergePriorStepSubTablesAfterMiIsolate: (myRowId: MiParticipantRowId | null) => void
  mergeIncomingTaskFormFieldValues: (fieldValues: Record<string, any>, taskData: any) => void
}

export function createTaskDetailMiResync(ctx: TaskDetailCtx): TaskDetailMiResyncFns {
  const {
    subTableBindings,
    previousForms,
    miSubProcessScope,
    miFullSubTablesSnapshotRef,
    lastBindingRelationTableMap,
  } = ctx
  const { formData } = ctx.taskForm

  /**
   * After MI isolate/resync, merge sub form1 (and earlier) sub-table rows into the current sub form2 bindings.
   * Previous step rows are merged first; current binding.data wins on conflicts so sub form2 edits are kept.
   */
  function mergePriorStepSubTablesAfterMiIsolate(myRowId: MiParticipantRowId | null) {
    const flat = miFullSubTablesSnapshotRef.value
    ctx.refreshPreviousFormsSubTableDataFromSnapshot(flat)

    for (const current of subTableBindings.value) {
      const pk = current.primaryKeyFields ?? null

      if (
        myRowId != null
        && flat
        && ctx.isCurrentMiCollectionSubTableBinding(current)
      ) {
        const participantPk = pk ?? ctx.miCollectionPrimaryKeyFields()
        const existingRows: any[] = cloneSubTableRows(Array.isArray(current.data) ? current.data : [])
        const existingHasOwnRow = existingRows.some(row =>
          rowMatchesSubTablePrimaryKey(row, myRowId, participantPk),
        )
        let candidates: any[] = existingRows
        // Same cross-binding PK collision as resyncMiParticipantSubTablesFromVariables above: sibling
        // BPMN nodes duplicate this MI collection under their OWN bindingId, and their rows can share
        // this binding's exact PK. Only scrape those siblings when this binding's own data doesn't
        // already have the current participant's row — otherwise a stale sibling duplicate can win
        // the merge purely by array order.
        if (!existingHasOwnRow) {
          const tableIdRaw =
            current.tableId != null
              ? Number(current.tableId)
              : lastBindingRelationTableMap.value.get(current.bindingId)
          if (Number.isFinite(Number(tableIdRaw))) {
            candidates = [
              ...candidates,
              ...collectSubTableSliceRowsForRelationTableId(
                flat,
                Number(tableIdRaw),
                lastBindingRelationTableMap.value,
                current.tableName,
                current.designerTableName,
              ),
            ]
          } else {
            candidates = [...candidates, ...(ctx.getSavedSubTableRows(flat, current) ?? [])]
          }
        }
        const scoped = candidates.filter(row =>
          rowMatchesSubTablePrimaryKey(row, myRowId, participantPk),
        )
        if (scoped.length > 0) {
          current.data = cloneSubTableRows(mergeMiCollectionSubTableRows([scoped], current))
        }
        continue
      }

      if (isMiParticipantScopedSubTableBinding(current) && myRowId != null) {
        // FK 列名按设计器字段定义解析，不猜列名。
        const fkConfig = miChildFkConfigOfBinding(current as any)
        const candidates: any[] = cloneSubTableRows(Array.isArray(current.data) ? current.data : [])
        for (const pf of previousForms.value) {
          for (const prev of pf.subTableBindings) {
            if (!subTableBindingMatches(current, prev)) continue
            if (!Array.isArray(prev.data) || prev.data.length === 0) continue
            const prevRows = prev.data
              .map(r => repairMisassignedLinkChildStructuralFk(r as Record<string, unknown>, myRowId, fkConfig))
              .filter(r => ctx.rowBelongsToCurrentMiScope(r, myRowId, current))
            candidates.push(...cloneSubTableRows(prevRows))
          }
        }
        if (flat) {
          const tableIdRaw =
            current.tableId != null
              ? Number(current.tableId)
              : lastBindingRelationTableMap.value.get(current.bindingId)
          if (Number.isFinite(Number(tableIdRaw))) {
            const slices = collectSubTableSliceRowsForRelationTableId(
              flat,
              Number(tableIdRaw),
              lastBindingRelationTableMap.value,
              current.tableName,
              current.designerTableName,
            )
            const scoped = slices
              .map(r => repairMisassignedLinkChildStructuralFk(r as Record<string, unknown>, myRowId, fkConfig))
              .filter(r => ctx.rowBelongsToCurrentMiScope(r, myRowId, current))
            candidates.push(...cloneSubTableRows(scoped))
          }
        }
        let merged = collapseMiLinkChildRowsToOnePerParticipant(candidates, fkConfig)
        if (flat && merged.length > 0) {
          const tempBinding = { ...current, data: merged } as typeof current
          backfillMiLinkChildPrimaryKeysFromVariables([tempBinding], flat, myRowId)
          merged = collapseMiLinkChildRowsToOnePerParticipant(tempBinding.data, fkConfig)
        }
        if (merged.length > 0) {
          current.data = cloneSubTableRows(merged)
        }
        continue
      }

      let merged = cloneSubTableRows(Array.isArray(current.data) ? current.data : [])

      if (isSharedAttachmentFileBinding(current)) {
        for (const pf of previousForms.value) {
          for (const prev of pf.subTableBindings) {
            if (!subTableBindingMatches(current, prev)) continue
            if (!Array.isArray(prev.data) || prev.data.length === 0) continue
            merged = mergeSubTableRowsByRowId(merged, cloneSubTableRows(prev.data), pk)
          }
        }
        if (flat) {
          const snap = mergeAllSlicesForSharedProcessSubTableBinding(
            flat,
            current,
            lastBindingRelationTableMap.value,
          )
          merged = mergeSubTableRowsByRowId(merged, snap, pk)
        }
        current.data = cloneSubTableRows(
          finalizeSharedProcessSubTableBindingRows(merged, current),
        )
      }
    }

    applySharedAttachmentFinalizeAndMaterialize(
      subTableBindings.value,
      formData.value as Record<string, unknown>,
      { flattened: flat, bindingTableById: lastBindingRelationTableMap.value },
    )

    if (myRowId != null) {
      for (const b of subTableBindings.value) {
        if (!isMiParticipantScopedSubTableBinding(b)) continue
        ctx.syncMiLinkChildRowsIntoParentNested(
          { bindingId: b.bindingId, tableName: b.tableName ?? '' },
          cloneSubTableRows(Array.isArray(b.data) ? b.data : []),
        )
      }
    }
  }

  /**
   * Merge portal Task Form API {@code fieldValues} into {@link formData}.
   * Non-MI: same shallow merge as before (omit null {@code __subTables__} from API).
   * MI: merge {@code __subTables__} slices row-wise for the current participant so link-form / nested rows are not dropped
   * (Flowable variables often only carry thin MI expansion rows; persisted form state may live on the task form DTO).
   */
  function mergeIncomingTaskFormFieldValues(fieldValues: Record<string, any>, taskData: any) {
    if (!fieldValues || typeof fieldValues !== 'object') return
    const miSubTask = ctx.isMiSubTask(taskData)

    if (!miSubTask) {
      const incoming = { ...fieldValues }
      const incomingSub = incoming.__subTables__
      if (incomingSub && typeof incomingSub === 'object' && !Array.isArray(incomingSub)) {
        const mergedSub: Record<string, unknown> = {
          ...((formData.value.__subTables__ as Record<string, unknown>) || {}),
        }
        for (const [sliceKey, val] of Object.entries(incomingSub)) {
          if (!Array.isArray(val)) continue
          const kid = Number(sliceKey)
          const bindingHint =
            (Number.isFinite(kid) ? subTableBindings.value.find(b => b.bindingId === kid) : null) ??
            subTableBindings.value.find(
              b => normalizeSubTableName(b.tableName) === normalizeSubTableName(String(sliceKey)),
            )
          if (bindingHint && isMiParticipantScopedSubTableBinding(bindingHint)) {
            const prevRaw = mergedSub[sliceKey] ?? mergedSub[String(sliceKey)]
            const prevRows = Array.isArray(prevRaw) ? cloneSubTableRows(prevRaw as any[]) : []
            const mergedRows = mergeSubTableRowsByRowId(prevRows, val as any[], bindingHint.primaryKeyFields ?? null)
            mergedSub[sliceKey] = mergedRows
            mergedSub[String(sliceKey)] = mergedRows
            continue
          }
          const prevRaw = mergedSub[sliceKey] ?? mergedSub[String(sliceKey)]
          const prevRows = Array.isArray(prevRaw) ? cloneSubTableRows(prevRaw as any[]) : []
          const mergedRows = mergeSubTableRowsByRowId(prevRows, val as any[], bindingHint?.primaryKeyFields ?? null)
          if (mergedRows.length === 0 && prevRows.length > 0) {
            mergedSub[sliceKey] = prevRows
            mergedSub[String(sliceKey)] = prevRows
          } else {
            mergedSub[sliceKey] = mergedRows
            mergedSub[String(sliceKey)] = mergedRows
          }
          if (bindingHint) {
            writeSubTableRows(mergedSub, bindingHint, mergedSub[sliceKey] as unknown[])
          }
        }
        delete incoming.__subTables__
        formData.value = { ...formData.value, ...incoming, __subTables__: mergedSub }
      } else {
        if (incoming.__subTables__ == null) delete incoming.__subTables__
        formData.value = { ...formData.value, ...incoming }
      }
      return
    }

    const vars = taskData?.variables || {}
    const myRowId = ctx.resolveCurrentMiParticipantRowIdFromTaskVars(vars)

    const incomingFull = { ...fieldValues }
    const incomingSub = incomingFull.__subTables__
    const mergedSub: Record<string, unknown> = {
      ...((formData.value.__subTables__ as Record<string, unknown>) || {}),
    }

    if (incomingSub && typeof incomingSub === 'object') {
      for (const [sliceKey, val] of Object.entries(incomingSub)) {
        if (!Array.isArray(val)) continue
        let rows = cloneSubTableRows(val as any[])
        const kid = Number(sliceKey)
        const bindingHint =
          (Number.isFinite(kid) ? subTableBindings.value.find(b => b.bindingId === kid) : null) ??
          subTableBindings.value.find(
            b => normalizeSubTableName(b.tableName) === normalizeSubTableName(String(sliceKey)),
          )

        if (myRowId != null) {
          const scopeToParticipant =
            bindingHint && isMiParticipantScopedSubTableBinding(bindingHint)
          if (scopeToParticipant) {
            const filt = rows.filter((row: any) =>
              ctx.rowBelongsToCurrentMiScope(row, myRowId, bindingHint),
            )
            rows = filt.length > 0 ? filt : rows.length === 1 ? rows : filt
          }
        }

        const prevRaw = mergedSub[sliceKey] ?? mergedSub[String(sliceKey)]
        const prevRows = Array.isArray(prevRaw) ? cloneSubTableRows(prevRaw as any[]) : []
        const pk = bindingHint?.primaryKeyFields ?? null
        const mergedRows = mergeSubTableRowsByRowId(prevRows, rows, pk)
        if (bindingHint) {
          writeSubTableRows(mergedSub, bindingHint, mergedRows)
        } else {
          mergedSub[String(sliceKey)] = mergedRows
        }
      }
    }

    delete incomingFull.__subTables__
    formData.value = {
      ...formData.value,
      ...incomingFull,
      __subTables__: mergedSub,
    }
  }

  /**
   * After MI isolation + task-form merge, copied-form binding ids may still miss {@code __subTables__} slices that
   * hold prior-step link-child fields (sub form1 → subtable2). Re-hydrate from the full variables snapshot and
   * nest link-child rows under the participant parent row for inline form-below-table.
   */
  async function resyncMiParticipantSubTablesFromVariables(
    myRowId: MiParticipantRowId,
    subTablesSource?: Record<string, unknown> | null,
    /** Reuse flattened snapshot from loadTaskDetail to skip a second deep clone. */
    preFlattened?: Record<string, unknown>,
  ): Promise<void> {
    const savedMap = coerceSubTablesVariableToMap(subTablesSource ?? formData.value.__subTables__)
    if (!savedMap) return

    const flattened = preFlattened ?? cloneAndFlattenSubTablesMap(savedMap)
    scrubMiCorruptLinkChildRowsForParent(flattened, myRowId, {
      skipSliceKeys: buildMiCollectionSliceKeySet(
        [...subTableBindings.value, ...previousForms.value.flatMap(pf => pf.subTableBindings)],
        lastBindingRelationTableMap.value,
        miSubProcessScope.value?.subTableName,
      ),
    })
    // 每个切片按表名找回它的 binding，才能拿到该表的 FK 配置。找不到 binding 就**不修**这条切片
    // —— 修 FK 必须知道哪一列是 FK，没有配置时猜列名正是本次要根除的做法。
    for (const [sliceKey, slice] of Object.entries(flattened)) {
      if (!Array.isArray(slice)) continue
      const owner = subTableBindings.value.find(
        b => subTableStoreKey(b as Parameters<typeof subTableStoreKey>[0]) === sliceKey,
      )
      if (!owner) continue
      const sliceFkConfig = miChildFkConfigOfBinding(owner as any)
      for (let i = 0; i < slice.length; i++) {
        const r = slice[i]
        if (r && typeof r === 'object') {
          slice[i] = repairMisassignedLinkChildStructuralFk(
            r as Record<string, unknown>,
            myRowId,
            sliceFkConfig,
          )
        }
      }
    }
    // Strip nested __subTables__ from rows to prevent circular deep-reactivity freeze
    for (const _slice of Object.values(flattened)) {
      if (Array.isArray(_slice)) stripNestedSubTablesFromRows(_slice as any[])
    }

    const rtMap = lastBindingRelationTableMap.value
    const ambiguous = bindingIdsPreferStrictSubTableLookup(subTableBindings.value)

    for (const binding of subTableBindings.value) {
      const isCollection = ctx.isCurrentMiCollectionSubTableBinding(binding)
      const byKey = ctx.getSavedSubTableRows(flattened, binding, ambiguous.has(binding.bindingId)) ?? []
      const participantScoped = isMiParticipantScopedSubTableBinding(binding) && !isCollection
      const tableIdRaw =
        binding.tableId != null ? Number(binding.tableId) : rtMap.get(binding.bindingId)
      const participantPk = ctx.resolveMiCollectionParticipantPkFields()
      // MI collection rows can share the same designer PK across sibling BPMN nodes' OWN binding
      // aliases of the very same logical table (each node duplicates the whole participant
      // collection under its own bindingId). Unlike link-child tables, that duplication means a
      // stale sibling binding's row can carry the identical PK as this binding's fresh one — the
      // per-row scope filter below cannot tell them apart. Only fall back to the cross-tableId
      // sibling scrape when this binding's OWN resolution has nothing for the current participant;
      // once `byKey` already has our row, unioning in siblings risks a stale duplicate winning the
      // merge purely by array order (#1524-class: reload showing another participant's old value).
      const byKeyHasOwnRow = isCollection
        && byKey.some((row: unknown) => rowMatchesSubTablePrimaryKey(row, myRowId, participantPk ?? binding.primaryKeyFields))
      const siblingSlices =
        isCollection
          ? byKeyHasOwnRow
            ? []
            : Number.isFinite(Number(tableIdRaw))
              ? collectSubTableSliceRowsForRelationTableId(
                  flattened,
                  Number(tableIdRaw),
                  rtMap,
                  binding.tableName,
                  binding.designerTableName,
                )
              : (ctx.getSavedSubTableRows(flattened, binding, ambiguous.has(binding.bindingId)) ?? [])
          : !participantScoped
            ? isMiDashboardSubTableBinding(binding)
              ? []
              : mergeAllSlicesForSharedProcessSubTableBinding(flattened, binding, rtMap)
            : Number.isFinite(Number(tableIdRaw))
              ? collectSubTableSliceRowsForRelationTableId(
                  flattened,
                  Number(tableIdRaw),
                  rtMap,
                  binding.tableName,
                  binding.designerTableName,
                )
              : mergeAllSubTableSlicesFromVariables(flattened, binding.primaryKeyFields ?? null)
      const candidateRows = isCollection ? [...byKey, ...siblingSlices] : [...byKey, ...siblingSlices]
      const scoped = isCollection
        ? candidateRows.filter((row: unknown) =>
            rowMatchesSubTablePrimaryKey(row, myRowId, participantPk ?? binding.primaryKeyFields),
          )
        : participantScoped
          ? candidateRows.filter((row: unknown) => ctx.rowBelongsToCurrentMiScope(row, myRowId, binding))
          : candidateRows
      const existing = Array.isArray(binding.data) ? binding.data : []
      if (isCollection) {
        binding.data = cloneSubTableRows(
          mergeMiCollectionSubTableRows([existing, scoped], {
            ...binding,
            primaryKeyFields: participantPk ?? binding.primaryKeyFields,
          }),
        )
      } else if (participantScoped) {
        binding.data = cloneSubTableRows(
          collapseMiLinkChildRowsToOnePerParticipant(
            [...existing, ...scoped],
            miChildFkConfigOfBinding(binding as any),
          ),
        )
      } else {
        let merged = cloneSubTableRows(
          mergeSubTableRowsByRowId(existing, scoped, binding.primaryKeyFields ?? null),
        )
        if (isMiDashboardSubTableBinding(binding)) {
          merged = finalizeMiCollectionSubTableBindingRows(merged, binding)
        }
        binding.data = merged
      }
    }

    hydrateChildSubTablesFromParentsNestedRows(
      subTableBindings.value,
      flattened,
      rtMap.size > 0 ? rtMap : undefined,
    )
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(subTableBindings.value, flattened, rtMap, {
      // MI participant-scoped bindings (e.g. People link child) already hydrated their own slice
      // filtered to this participant above. Pulling sibling same-tableId slices here would merge
      // other participants' PK-colliding rows back in and overwrite the current participant's data.
      excludeBinding: bb => isMiParticipantScopedSubTableBinding(bb as any),
    })
    enrichChildBindingRowsFromParentsNestedSubTables(subTableBindings.value)
    // Strip nested __subTables__ from binding rows to prevent Vue deep-reactivity freeze
    for (const b of subTableBindings.value) {
      if (Array.isArray(b.data)) stripNestedSubTablesFromRows(b.data)
    }
    await ctx.seedMiParticipantScopedBindingForeignKeys(myRowId)
    backfillMiLinkChildPrimaryKeysFromVariables(subTableBindings.value, flattened, myRowId)
    for (const binding of subTableBindings.value) {
      if (ctx.isCurrentMiCollectionSubTableBinding(binding)) continue
      if (!isMiParticipantScopedSubTableBinding(binding)) continue
      binding.data = cloneSubTableRows(
        collapseMiLinkChildRowsToOnePerParticipant(binding.data, miChildFkConfigOfBinding(binding as any)),
      )
    }
    ctx.applyMiParticipantFilterToCurrentSubTableBindings(myRowId)

    applySharedAttachmentFinalizeAndMaterialize(
      subTableBindings.value,
      formData.value as Record<string, unknown>,
      { flattened, bindingTableById: rtMap },
    )
    for (const binding of subTableBindings.value) {
      if (isMiParticipantScopedSubTableBinding(binding)) continue
      if (isSharedAttachmentFileBinding(binding)) continue
      binding.data = finalizeSharedProcessSubTableBindingRows(binding.data, binding)
    }

    for (const binding of subTableBindings.value) {
      const rows = Array.isArray(binding.data) ? binding.data : []
      /**
       * MI link-form child bindings (e.g. subtable2) must be synced even when their MI-filtered slice is
       * empty — otherwise stale rows from other participants linger under parentRow.__subTables__ and
       * pre-fill the inline link form on first open (MI Subtask Demo, sub form1 of participant 2).
       */
      if (rows.length > 0 || isMiParticipantScopedSubTableBinding(binding)) {
        ctx.syncMiLinkChildRowsIntoParentNested(
          { bindingId: binding.bindingId, tableName: binding.tableName },
          cloneSubTableRows(rows),
        )
      }
    }
    // NOTE: patchFormDataSubTablesFromCurrentBindings() removed here —
    // caller (MI isolate IIFE) calls it once after rehydrateSharedAttachmentBindings
    // to avoid triggering Vue reactivity twice (~3s savings per call).
  }

  return {
    resyncMiParticipantSubTablesFromVariables,
    mergePriorStepSubTablesAfterMiIsolate,
    mergeIncomingTaskFormFieldValues,
  }
}
