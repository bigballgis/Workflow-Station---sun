import { computed, type ComputedRef } from 'vue'
import { ElMessage } from 'element-plus'
import {
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  finalizeMiCollectionSubTableBindingRows,
  miLinkChildRowBelongsToParticipant,
} from '@/composables/tasks/shared'
import {
  rowMatchesSubTablePrimaryKey,
  bindingMatchesMiSubTableName,
  resolveMiSubProcessScopeFromBpmn,
  findBindingForMiSubTableName,
  extractMiParticipantRowIdFromCurrentItem,
  hasConfiguredPrimaryKeyFields,
  describeSubTableBindingLabel,
  miParticipantRowIdsEqual,
  type MiParticipantRowId,
} from '@/composables/tasks/miSubProcessScope'
import { setActiveMiConfig } from '@/composables/tasks/useMiConfig'
import type { TaskDetailState } from './useTaskDetailState'
import type { TaskDetailCtx } from './context'

export interface TaskDetailMiScopeFns {
  refreshMiSubProcessScopeFromBpmn: () => void
  miCollectionPrimaryKeyFields: () => string[] | undefined
  warnMiMissingPrimaryKey: (binding: {
    tableName?: string
    physicalTableName?: string
    bindingId?: number | string
  }) => void
  warnMiCollectionPrimaryKeyIfNeeded: () => void
  resolveCurrentMiParticipantRowIdFromTaskVars: (
    vars?: Record<string, unknown> | null,
  ) => MiParticipantRowId | null
  currentMiRowId: ComputedRef<MiParticipantRowId | null>
  isMiSubTask: (taskData: any) => boolean
  isParticipantsBinding: (binding: { tableName: string }) => boolean
  rowBelongsToCurrentMiScope: (
    row: unknown,
    myRowId: MiParticipantRowId,
    binding: {
      tableName: string
      physicalTableName?: string
      foreignKeyField?: string | null
      primaryKeyFields?: string[]
      columns?: Array<{ field?: string }>
      bindingId?: number | string
    },
  ) => boolean
  miRowBelongsToCurrentParticipant: (
    row: any,
    myRowId: MiParticipantRowId,
    binding: {
      tableName: string
      foreignKeyField?: string
      primaryKeyFields?: string[]
      physicalTableName?: string
      bindingId?: number | string
      fieldDefinitions?: Array<{ fieldName: string; isPrimaryKey?: boolean; isForeignKey?: boolean }>
    },
  ) => boolean
  resolveMiCollectionBindingAcrossTaskForms: () => TaskDetailState['subTableBindings']['value'][0] | undefined
  isCurrentMiCollectionSubTableBinding: (binding: {
    tableName?: string
    physicalTableName?: string
    tableId?: number | null
  }) => boolean
  resolveMiCollectionParticipantPkFields: () => string[] | null
  sanitizeMiCollectionBindingsData: (bindings: TaskDetailState['subTableBindings']['value']) => void
}

export function createTaskDetailMiScope(ctx: TaskDetailCtx): TaskDetailMiScopeFns {
  const {
    t,
    taskInfo,
    subTableBindings,
    previousForms,
    miSubProcessScope,
    miMissingPrimaryKeyWarned,
  } = ctx
  const { bpmnXml } = ctx.bpmn

  function refreshMiSubProcessScopeFromBpmn() {
    const xml = bpmnXml.value
    if (!xml) {
      miSubProcessScope.value = null
      // 清掉上一个 FU 的配置，否则会泄漏到下一个（跨 FU 串配置和写死一样糟）
      setActiveMiConfig(null)
      return
    }
    const ti = taskInfo.value as { taskDefinitionKey?: string; taskName?: string }
    miSubProcessScope.value = resolveMiSubProcessScopeFromBpmn(xml, {
      userTaskId: ti?.taskDefinitionKey ?? null,
      userTaskName: ti?.taskName ?? null,
    })
    // MI 列名的唯一真源：注册给深层纯函数隐式读取，避免 113 个调用点逐个传参
    setActiveMiConfig(miSubProcessScope.value)
  }

  function miCollectionPrimaryKeyFields(): string[] | undefined {
    const b = resolveMiCollectionBindingAcrossTaskForms()
    return b?.primaryKeyFields ?? undefined
  }

  function warnMiMissingPrimaryKey(binding: {
    tableName?: string
    physicalTableName?: string
    bindingId?: number | string
  }) {
    const label = describeSubTableBindingLabel(binding)
    const key = label || 'unknown'
    if (miMissingPrimaryKeyWarned.has(key)) return
    miMissingPrimaryKeyWarned.add(key)
    ElMessage.error(t('task.miPrimaryKeyNotConfigured', { table: label || key }))
  }

  function warnMiCollectionPrimaryKeyIfNeeded(): void {
    const scope = miSubProcessScope.value
    if (!scope?.subTableName) return
    const b = findBindingForMiSubTableName(subTableBindings.value, scope.subTableName)
    if (!b) return
    if (hasConfiguredPrimaryKeyFields(b.primaryKeyFields)) return
    warnMiMissingPrimaryKey(b)
  }

  /** Resolve MI participant id for task variables using designer PK + {@code _currentItem.rowKey}. */
  function resolveCurrentMiParticipantRowIdFromTaskVars(
    vars?: Record<string, unknown> | null,
  ): MiParticipantRowId | null {
    const ci = (vars?._currentItem ?? vars?.currentItem) as Record<string, unknown> | undefined
    const scope = miSubProcessScope.value
    const pk = miCollectionPrimaryKeyFields()
    return extractMiParticipantRowIdFromCurrentItem(ci, pk, {
      rowIdVariable: scope?.rowIdVariable ?? 'currentItem.rowId',
    })
  }

  /**
   * Current MI participant row id from {@code _currentItem} + designer {@code primaryKeyFields}
   * (single PK scalar or composite {@code v1|v2|...} from {@code rowKey}).
   */
  const currentMiRowId = computed<MiParticipantRowId | null>(() => {
    const vars = (taskInfo.value as { variables?: Record<string, unknown> })?.variables
    return resolveCurrentMiParticipantRowIdFromTaskVars(vars)
  })

  const isMiSubTask = (taskData: any): boolean => {
    const defKey = String(taskData?.taskDefinitionKey || '')
    if (defKey.startsWith('MI_UserTask_')) {
      return true
    }
    const vars = taskData?.variables || {}
    return !!(vars?._currentItem || vars?.currentItem)
  }

  function isParticipantsBinding(binding: { tableName: string }): boolean {
    const tn = (binding.tableName || '').toLowerCase()
    return tn === 'participants' || tn.endsWith('participants')
  }

  /** When slice binding metadata is missing, match MI rows via FK columns to the participant row id. */
  function miIncomingRowLikelyForParticipant(row: unknown, myRowId: MiParticipantRowId): boolean {
    if (!row || typeof row !== 'object') return false
    const collectionPk = miCollectionPrimaryKeyFields()
    if (hasConfiguredPrimaryKeyFields(collectionPk) && rowMatchesSubTablePrimaryKey(row, myRowId, collectionPk)) {
      return true
    }
    const rec = row as Record<string, unknown>
    const fkKeys = ['participant_id', 'participantId', 'parent_id', 'parentId', 'meeting_participant_id']
    for (const k of fkKeys) {
      const v = rec[k]
      if (v != null && v !== '' && miParticipantRowIdsEqual(v, myRowId)) return true
    }
    return false
  }

  /** MI isolation: participant-scoped sub-tables only; main-table-linked tables (e.g. attachment) stay shared. */
  function rowBelongsToCurrentMiScope(
    row: unknown,
    myRowId: MiParticipantRowId,
    binding: {
      tableName: string
      physicalTableName?: string
      foreignKeyField?: string | null
      primaryKeyFields?: string[]
      columns?: Array<{ field?: string }>
      bindingId?: number | string
    },
  ): boolean {
    const scope = miSubProcessScope.value
    if (scope && bindingMatchesMiSubTableName(binding, scope.subTableName)) {
      const pk = binding.primaryKeyFields ?? miCollectionPrimaryKeyFields()
      if (!hasConfiguredPrimaryKeyFields(pk)) {
        warnMiMissingPrimaryKey(binding)
        return false
      }
      return rowMatchesSubTablePrimaryKey(row, myRowId, pk)
    }
    if (!isMiParticipantScopedSubTableBinding(binding)) return true
    return miRowBelongsToCurrentParticipant(row, myRowId, binding)
  }

  /** MI isolation: participant rows match by PK; related sub-table rows match by FK to participant (not by sub-row id). */
  function miRowBelongsToCurrentParticipant(
    row: any,
    myRowId: MiParticipantRowId,
    binding: {
      tableName: string
      foreignKeyField?: string
      primaryKeyFields?: string[]
      physicalTableName?: string
      bindingId?: number | string
      fieldDefinitions?: Array<{ fieldName: string; isPrimaryKey?: boolean; isForeignKey?: boolean }>
    },
  ): boolean {
    if (!row || typeof row !== 'object') return false
    if (isParticipantsBinding(binding)) {
      const pks = binding.primaryKeyFields ?? miCollectionPrimaryKeyFields()
      if (!hasConfiguredPrimaryKeyFields(pks)) {
        warnMiMissingPrimaryKey(binding)
        return false
      }
      return rowMatchesSubTablePrimaryKey(row, myRowId, pks)
    }
    const scopeName = miSubProcessScope.value?.subTableName ?? ''
    if (
      isMiParticipantScopedSubTableBinding(binding)
      && scopeName
      && !bindingMatchesMiSubTableName(binding, scopeName)
    ) {
      return miLinkChildRowBelongsToParticipant(row as Record<string, unknown>, myRowId)
    }
    const fk = binding.foreignKeyField
    const fkStr = fk ? String(fk).trim() : ''
    const fkIsRowOwnPrimaryKey =
      fkStr !== ''
      && (
        (Array.isArray(binding.primaryKeyFields) &&
          binding.primaryKeyFields.some(p => String(p).trim() === fkStr))
        || binding.fieldDefinitions?.some(
          fd => fd.fieldName === fkStr && fd.isPrimaryKey === true,
        )
      )
    const fkIsMiLinkChildToParticipant =
      isMiParticipantScopedSubTableBinding(binding) &&
      fkStr.toLowerCase() === 'id' &&
      !isParticipantsBinding(binding) &&
      !fkIsRowOwnPrimaryKey
    const fkLooksLikeRowPrimaryKey =
      fkIsRowOwnPrimaryKey ||
      (fkStr.toLowerCase() === 'id' && !isParticipantsBinding(binding) && !fkIsMiLinkChildToParticipant)
    if (fk && (fkIsMiLinkChildToParticipant || !fkLooksLikeRowPrimaryKey) && row[fk] != null && row[fk] !== '') {
      if (miParticipantRowIdsEqual(row[fk], myRowId)) return true
    }
    for (const fd of binding.fieldDefinitions ?? []) {
      if (!fd.isForeignKey || !fd.fieldName) continue
      const v = row[fd.fieldName]
      if (v != null && v !== '' && miParticipantRowIdsEqual(v, myRowId)) return true
    }
    const fallbackFkKeys = [
      'sub_task_id',
      'participant_id',
      'participantId',
      'parent_id',
      'parentId',
      'meeting_participant_id',
    ]
    for (const k of fallbackFkKeys) {
      if (row[k] != null && row[k] !== '' && miParticipantRowIdsEqual(row[k], myRowId)) {
        return true
      }
    }
    const pksRel = binding.primaryKeyFields
    const collPk = miCollectionPrimaryKeyFields()
    const pkForMatch = pksRel?.length ? pksRel : collPk
    if (hasConfiguredPrimaryKeyFields(pkForMatch)) {
      return rowMatchesSubTablePrimaryKey(row, myRowId, pkForMatch)
    }
    return false
  }

  function resolveMiCollectionBindingAcrossTaskForms(): (typeof subTableBindings.value)[0] | undefined {
    const scope = miSubProcessScope.value
    if (!scope?.subTableName) return undefined
    const all = [
      ...subTableBindings.value,
      ...previousForms.value.flatMap(pf => pf.subTableBindings),
    ] as typeof subTableBindings.value
    const matches = all.filter(b => bindingMatchesMiSubTableName(b, scope.subTableName))
    if (matches.length === 0) return undefined
    return (
      matches.find(b => hasConfiguredPrimaryKeyFields(b.primaryKeyFields))
      ?? matches[0]
    )
  }

  function isCurrentMiCollectionSubTableBinding(binding: {
    tableName?: string
    physicalTableName?: string
    tableId?: number | null
  }): boolean {
    const scope = miSubProcessScope.value
    if (!scope?.subTableName) return false
    if (bindingMatchesMiSubTableName(binding, scope.subTableName)) return true
    const coll = resolveMiCollectionBindingAcrossTaskForms()
    return (
      coll?.tableId != null
      && binding.tableId != null
      && Number(binding.tableId) === Number(coll.tableId)
    )
  }

  function resolveMiCollectionParticipantPkFields(): string[] | null {
    const b = resolveMiCollectionBindingAcrossTaskForms()
    if (b && hasConfiguredPrimaryKeyFields(b.primaryKeyFields)) {
      return b.primaryKeyFields!
    }
    return null
  }

  function sanitizeMiCollectionBindingsData(bindings: typeof subTableBindings.value) {
    for (const binding of bindings) {
      if (!isMiDashboardSubTableBinding(binding)) continue
      binding.data = finalizeMiCollectionSubTableBindingRows(
        Array.isArray(binding.data) ? binding.data : [],
        binding,
      )
    }
  }

  return {
    refreshMiSubProcessScopeFromBpmn,
    miCollectionPrimaryKeyFields,
    warnMiMissingPrimaryKey,
    warnMiCollectionPrimaryKeyIfNeeded,
    resolveCurrentMiParticipantRowIdFromTaskVars,
    currentMiRowId,
    isMiSubTask,
    isParticipantsBinding,
    rowBelongsToCurrentMiScope,
    miRowBelongsToCurrentParticipant,
    resolveMiCollectionBindingAcrossTaskForms,
    isCurrentMiCollectionSubTableBinding,
    resolveMiCollectionParticipantPkFields,
    sanitizeMiCollectionBindingsData,
  }
}
