import { processApi } from '@/api/process'
import { writeSubTableRows } from '@/composables/tasks/subTableStore'
import {
  ensureAutoPrimaryKeysForRows,
  repairMisassignedPrimaryKeyFromParentId,
  seedLinkChildForeignKeysFromParentRow,
  type AllocatePrimaryKeysFn,
} from '@/utils/subTableRowRuntime'
import {
  mergeSubTableRowsByRowId,
  findSubTableRowByMiExpansionId,
  findMiIsolatedParentRow,
  pullNestedRowsForBindingFromParentRows,
  collectSubTableSliceRowsForRelationTableId,
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  collapseMiLinkChildRowsToOnePerParticipant,
  backfillMiLinkChildPrimaryKeysFromVariables,
  miChildFkConfigOfBinding,
  repairMisassignedLinkChildStructuralFk,
  linkChildRowIsForeignParticipantPlaceholder,
  stripForeignParticipantIdIdwFromLinkChildRow,
  scopeMiLinkChildRowsForParentRow,
} from '@/composables/tasks/shared'
import {
  rowMatchesSubTablePrimaryKey,
  bindingMatchesMiSubTableName,
  type MiParticipantRowId,
} from '@/composables/tasks/miSubProcessScope'
import {
  cloneSubTableRows,
  subTableBindingMatches,
  collectSubTableBindingMatchKeys,
  subTableRowsLackSavedFieldPayload,
} from './subTableRowUtils'
import { resolveSubTablePrimaryKeyFields } from '@/composables/tasks/useMiConfig'
import type { TaskDetailCtx } from './context'

export interface TaskDetailMiLinkChildFns {
  seedMiParticipantScopedBindingForeignKeys: (
    myRowId: MiParticipantRowId,
    options?: { allocateMissingPrimaryKeys?: boolean },
  ) => Promise<void>
  syncMiLinkChildRowsIntoParentNested: (
    childBinding: { bindingId: number; tableName: string },
    childRows: any[],
  ) => void
  hydrateMiLinkChildBindingsFromFullSnapshot: (myRowId: MiParticipantRowId) => void
}

export function createTaskDetailMiLinkChild(ctx: TaskDetailCtx): TaskDetailMiLinkChildFns {
  const {
    subTableBindings,
    previousForms,
    miSubProcessScope,
    miFullSubTablesSnapshotRef,
    lastBindingRelationTableMap,
    functionUnitIdRef,
  } = ctx

  function patchMiParentRowsWithNestedChildSlice(
    parentRows: any[],
    myRowId: MiParticipantRowId,
    childBinding: { bindingId: number; tableName: string },
    childSlice: any[],
    childFkConfig?: Parameters<typeof scopeMiLinkChildRowsForParentRow>[2],
  ): any[] {
    if (!Array.isArray(parentRows)) return parentRows
    const collectionPk = ctx.miCollectionPrimaryKeyFields()
    return parentRows.map(row => {
      if (!row || typeof row !== 'object') return row
      const rec = row as Record<string, unknown>
      if (
        !rowMatchesSubTablePrimaryKey(row, myRowId, collectionPk) &&
        !ctx.miRowBelongsToCurrentParticipant(row, myRowId, {
          tableName: childBinding.tableName,
          primaryKeyFields: collectionPk,
        })
      ) {
        return row
      }
      const scopedSlice = scopeMiLinkChildRowsForParentRow(rec, childSlice, childFkConfig, collectionPk)
      const nest = {
        ...(rec.__subTables__ && typeof rec.__subTables__ === 'object'
          ? (rec.__subTables__ as Record<string, unknown>)
          : {})
      }
      const slice = cloneSubTableRows(scopedSlice)
      writeSubTableRows(nest, childBinding, slice)
      return { ...rec, __subTables__: nest }
    })
  }

  /** Prefer collection row {@code id_idw} (e.g. Test-000017) when seeding link-child FK {@code id}. */
  function resolveMiParticipantFkSeedValue(myRowId: MiParticipantRowId): MiParticipantRowId {
    const scopeName = miSubProcessScope.value?.subTableName ?? ''
    for (const b of subTableBindings.value) {
      if (!bindingMatchesMiSubTableName(b, scopeName)) continue
      const rows = Array.isArray(b.data) ? b.data : []
      // 按表的种类解析主键：**子任务表缺主键 = 配置错误（抛错）**；其它表没有主键是允许的
      // （关联表 / sys_users / 共享附件），返回 null 后只是跳过与主键相关的匹配。
      const pk = resolveSubTablePrimaryKeyFields(b)
      const row =
        findSubTableRowByMiExpansionId(rows, myRowId, pk)
        ?? findMiIsolatedParentRow(rows, myRowId, pk)
      if (row && row.id_idw != null && row.id_idw !== '') {
        return row.id_idw as MiParticipantRowId
      }
    }
    return myRowId
  }

  /** Pull link-child rows nested under the current MI collection parent when top-level binding.data is still empty. */
  function materializeMiLinkChildBindingRowsFromParents(myRowId: MiParticipantRowId) {
    const scopeName = miSubProcessScope.value?.subTableName ?? ''
    const parentBinding = subTableBindings.value.find(b => bindingMatchesMiSubTableName(b, scopeName))
    if (!parentBinding) return
    const parentRow = findMiIsolatedParentRow(
      Array.isArray(parentBinding.data) ? parentBinding.data : [],
      myRowId,
      resolveSubTablePrimaryKeyFields(parentBinding),
    )
    if (!parentRow) return
    const peerMap = new Map<number, number | null>()
    for (const b of subTableBindings.value) {
      const tid = b.tableId != null ? Number(b.tableId) : null
      if (tid != null && Number.isFinite(tid)) peerMap.set(b.bindingId, tid)
    }
    // collection 的 tableId 来自 parentBinding（按 subTableName 解析）—— 有了它，child 与
    // shared 才能按「字段级 FK 指向谁」区分，而不是靠 FK 列名猜。
    const miKindCtx = {
      miCollectionTableId:
        parentBinding.tableId != null && Number.isFinite(Number(parentBinding.tableId))
          ? Number(parentBinding.tableId)
          : null,
      primaryTableId: null,
    }
    for (const b of subTableBindings.value) {
      if (!isMiParticipantScopedSubTableBinding(b, miKindCtx)) continue
      if (b.bindingId === parentBinding.bindingId) continue
      const existing = Array.isArray(b.data) ? b.data : []
      const nested = pullNestedRowsForBindingFromParentRows(
        {
          bindingId: b.bindingId,
          tableName: b.tableName ?? '',
          physicalTableName: b.physicalTableName,
          tableId: b.tableId ?? null,
        },
        [parentRow],
        peerMap,
      )
      if (nested.length === 0) continue
      if (existing.length === 0) {
        b.data = cloneSubTableRows(nested)
        continue
      }
      const fieldKeys = collectSubTableBindingMatchKeys(b as any)
      if (subTableRowsLackSavedFieldPayload(existing, fieldKeys)) {
        b.data = cloneSubTableRows(
          mergeSubTableRowsByRowId(existing, nested, b.primaryKeyFields ?? null),
        )
        continue
      }
      // A child table nested inside the parent's inlineSubForm (People inside Participants) is
      // edited through the PARENT row: new rows land in `parentRow.__subTables__` and never reach
      // this binding's own `data`. The old rule only adopted the nested slice when `existing` had
      // no saved payload, so as soon as the binding held one loaded row every row added afterwards
      // was dropped here — and then `syncMiLinkChildRowsIntoParentNested` wrote this stale slice
      // back over the parent's correct one, so Save persisted 1 row out of 3 (#1546).
      //
      // Rows the nested slice has and the binding does not are additions, not deletions: the delete
      // path removes rows from the nested slice too, so a row present there is a row the user kept.
      const adopted = mergeSubTableRowsByRowId(existing, nested, b.primaryKeyFields ?? null)
      if (adopted.length > existing.length) {
        b.data = cloneSubTableRows(adopted)
      }
    }
  }

  /**
   * Seed MI link-child FK (sub_task_id ← id_idw), repair misassigned row PK.
   * PK allocate is deferred to save/submit/complete — not on page load (avoids 100+ HTTP storm on empty link-form stubs).
   */
  async function seedMiParticipantScopedBindingForeignKeys(
    myRowId: MiParticipantRowId,
    options?: { allocateMissingPrimaryKeys?: boolean },
  ) {
    const shouldAllocate = options?.allocateMissingPrimaryKeys === true
    materializeMiLinkChildBindingRowsFromParents(myRowId)
    const fkSeed = resolveMiParticipantFkSeedValue(myRowId)
    const scopeName = miSubProcessScope.value?.subTableName ?? ''
    const parentBinding = subTableBindings.value.find(b => bindingMatchesMiSubTableName(b, scopeName))
    const parentRows = parentBinding && Array.isArray(parentBinding.data) ? parentBinding.data : []
    // parentBinding 就是 MI 子任务表（按 subTableName 找到的）—— 它缺主键就是配置错误，抛错。
    const parentPk = parentBinding ? resolveSubTablePrimaryKeyFields(parentBinding) : null
    const parentRow =
      findSubTableRowByMiExpansionId(parentRows, myRowId, parentPk)
      ?? findMiIsolatedParentRow(parentRows, myRowId, parentPk)
    const parentParticipantRow: Record<string, unknown> =
      parentRow ?? ({ id_idw: fkSeed } as Record<string, unknown>)
    const parentTableId =
      parentBinding?.tableId != null && Number.isFinite(Number(parentBinding.tableId))
        ? Number(parentBinding.tableId)
        : null

    const fuId = functionUnitIdRef.value
    const allocateFn: AllocatePrimaryKeysFn | null =
      shouldAllocate && fuId && String(fuId).trim()
        ? async payload => {
            const res = await processApi.allocatePrimaryKeys(fuId, payload, ctx.taskId)
            const data = (res as { data?: { values?: string[] }; values?: string[] }).data ?? res
            return data?.values ?? []
          }
        : null

    // 同上：collection tableId 取自 parentBinding，让 child/shared 按配置区分。
    const miKindCtx = { miCollectionTableId: parentTableId, primaryTableId: null }
    for (const b of subTableBindings.value) {
      if (!isMiParticipantScopedSubTableBinding(b, miKindCtx)) continue
      /** Sub task / MI collection rows — id_idw is the real PK; only link-child bindings (People) get repair/allocate. */
      if (bindingMatchesMiSubTableName(b, scopeName) || isMiDashboardSubTableBinding(b)) {
        continue
      }
      // An empty link-child binding means the user added no rows — never fabricate one. Save used to
      // insert `[{}]` here so the inline form-below-table strip had a row to bind its fields to, but
      // that strip is gone (rows are added/edited through the Link Form modal), so the placeholder had
      // no editor to fill it: seeding stamped it with the participant FK and allocation gave it a real
      // UUID, so every Save on an empty People table persisted one blank phantom row (#1531).
      const rowCount = Array.isArray(b.data) ? b.data.length : 0
      if (rowCount === 0) continue
      const fkConfig = miChildFkConfigOfBinding(b as any, parentBinding?.tableId ?? null)
      // Sibling participants' placeholder rows live in this same binding. Seeding/allocating them with the
      // CURRENT participant FK makes them falsely claim this participant; collapse then merges all into one
      // corrupt row (cross-participant id_idw leak). Only seed rows that belong to (or are fresh for) the
      // current participant; leave foreign placeholders byte-for-byte intact. (#1444)
      const foreignRows: Record<string, unknown>[] = []
      const seedableRows: Record<string, unknown>[] = []
      for (const raw of b.data) {
        if (!raw || typeof raw !== 'object') {
          foreignRows.push(raw as Record<string, unknown>)
          continue
        }
        const row = raw as Record<string, unknown>
        if (linkChildRowIsForeignParticipantPlaceholder(row, myRowId, fkConfig)) {
          foreignRows.push(row)
          continue
        }
        let next = seedLinkChildForeignKeysFromParentRow(
          row,
          b.fieldDefinitions,
          {
            bindingForeignKeyField: b.foreignKeyField,
            bindingLinkMode: b.bindingLinkMode,
            primaryKeyFields: b.primaryKeyFields,
            parentParticipantRow,
            parentTableId,
            legacyFkSeed: fkSeed,
          },
        )
        // 第 4 个参数是**父（MI collection）**表的设计器主键：它自己的主键不能当成误copy删掉。
        // 用 ctx.miCollectionPrimaryKeyFields()（跨任务表单解析 collection binding），
        // 比就近的 parentBinding 更可靠：后者在某些节点上解析不到。
        next = repairMisassignedPrimaryKeyFromParentId(
          next,
          b.fieldDefinitions,
          fkSeed,
          parentBinding?.primaryKeyFields ?? ctx.miCollectionPrimaryKeyFields(),
        )
        next = repairMisassignedLinkChildStructuralFk(next, fkSeed, fkConfig)
        next = stripForeignParticipantIdIdwFromLinkChildRow(next, myRowId, fkConfig)
        seedableRows.push(next)
      }
      let allocated = seedableRows
      if (allocateFn && b.tableId != null && b.fieldDefinitions?.length) {
        allocated = await ensureAutoPrimaryKeysForRows(
          b.fieldDefinitions,
          b.tableId,
          seedableRows,
          allocateFn,
          fuId,
        )
      }
      b.data = cloneSubTableRows(
        collapseMiLinkChildRowsToOnePerParticipant([...foreignRows, ...allocated], fkConfig),
      )
      syncMiLinkChildRowsIntoParentNested(
        { bindingId: b.bindingId, tableName: b.tableName ?? '' },
        cloneSubTableRows(Array.isArray(b.data) ? b.data : []),
      )
    }
  }

  /** MI link-form child rows must live under {@code parentRow.__subTables__[childBindingId]} for reload / diagram, not only top-level slice. */
  function syncMiLinkChildRowsIntoParentNested(
    childBinding: { bindingId: number; tableName: string },
    childRows: any[]
  ) {
    // child binding 的 FK 配置：把子行归到哪个父行，靠设计器声明的外键列，不猜列名。
    const childFkConfig = miChildFkConfigOfBinding(
      subTableBindings.value.find(b => b.bindingId === childBinding.bindingId) as never,
    )
    const rid = ctx.currentMiRowId.value
    if (rid == null) return
    const myRowId = rid

    for (const parentBinding of subTableBindings.value) {
      if (parentBinding.bindingId === childBinding.bindingId) continue
      parentBinding.data = patchMiParentRowsWithNestedChildSlice(
        Array.isArray(parentBinding.data) ? parentBinding.data : [],
        myRowId,
        childBinding,
        childRows,
        childFkConfig,
      )
    }
  }

  /** Rebuild MI link-child bindings (People, …) solely from the flattened variables snapshot + previousForms. */
  function hydrateMiLinkChildBindingsFromFullSnapshot(myRowId: MiParticipantRowId) {
    const flat = miFullSubTablesSnapshotRef.value
    if (!flat) return
    const rtMap = lastBindingRelationTableMap.value
    ctx.refreshPreviousFormsSubTableDataFromSnapshot(flat)

    // MI collection 的 tableId：child 的 FK 必须指向它才算「指向参与者行」的结构外键。
    const scopeName = miSubProcessScope.value?.subTableName ?? ''
    const collectionTableId =
      subTableBindings.value.find(b => bindingMatchesMiSubTableName(b, scopeName))?.tableId ?? null

    for (const binding of subTableBindings.value) {
      if (!isMiParticipantScopedSubTableBinding(binding)) continue
      const ownSlice = ctx.getSavedSubTableRows(flat, binding, false, rtMap) ?? []
      const candidates: any[] = []
      const tableIdRaw =
        binding.tableId != null ? Number(binding.tableId) : rtMap.get(binding.bindingId)
      if (Number.isFinite(Number(tableIdRaw))) {
        candidates.push(
          ...collectSubTableSliceRowsForRelationTableId(
            flat,
            Number(tableIdRaw),
            rtMap,
            binding.tableName,
            binding.physicalTableName,
          ),
        )
      } else {
        candidates.push(...ownSlice)
      }
      for (const pf of previousForms.value) {
        for (const prev of pf.subTableBindings) {
          if (!subTableBindingMatches(binding, prev)) continue
          if (Array.isArray(prev.data)) candidates.push(...prev.data)
        }
      }
      const fkConfig = miChildFkConfigOfBinding(binding as any, collectionTableId)
      const scoped = candidates
        .map(r => repairMisassignedLinkChildStructuralFk(r as Record<string, unknown>, myRowId, fkConfig))
        .filter(r => ctx.rowBelongsToCurrentMiScope(r, myRowId, binding))
      let merged = collapseMiLinkChildRowsToOnePerParticipant(scoped, fkConfig)
      const ownScoped = ownSlice
        .map(r => repairMisassignedLinkChildStructuralFk(r as Record<string, unknown>, myRowId, fkConfig))
        .filter(r => ctx.rowBelongsToCurrentMiScope(r, myRowId, binding))
      if (ownScoped.length > 0) {
        // Current task binding slice wins over stale sibling slices (e.g. prior userTask binding 63 vs 30).
        merged = mergeSubTableRowsByRowId(
          merged,
          collapseMiLinkChildRowsToOnePerParticipant(ownScoped, fkConfig),
          binding.primaryKeyFields,
        )
      }
      if (merged.length > 0) {
        const temp = { ...binding, data: merged }
        backfillMiLinkChildPrimaryKeysFromVariables([temp as typeof binding], flat, myRowId)
        merged = collapseMiLinkChildRowsToOnePerParticipant(temp.data, fkConfig)
        binding.data = cloneSubTableRows(merged)
      }
    }
  }

  return {
    seedMiParticipantScopedBindingForeignKeys,
    syncMiLinkChildRowsIntoParentNested,
    hydrateMiLinkChildBindingsFromFullSnapshot,
  }
}
