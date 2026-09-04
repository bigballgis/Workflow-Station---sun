import { ElMessage } from 'element-plus'
import { collectLeafFormFieldKeys } from '@/components/formRendererHelpers'
import { enrichChildBindingRowsFromParentsNestedSubTables } from '@/composables/tasks/shared'
import {
  resolveMiSubProcessScopeFromBpmn,
  findBindingForMiSubTableName,
  filterBindingsToMiParticipantRow,
  resolveViewerParticipantRowIdFromCollectionBinding,
  hasConfiguredPrimaryKeyFields,
  describeSubTableBindingLabel,
} from '@/composables/tasks/miSubProcessScope'
import {
  getPortalUserId,
  applyUnionFindMergeToBindingList,
  type SubTableBindingAlignable,
} from './subTableRowHelpers'
import { getActiveMiFieldNames, setActiveMiConfig } from '@/composables/tasks/useMiConfig'
import { registerMiKindTableIdsFromBindings } from '@/composables/tasks/miBindingKindFromConfig'
import type { ApplicationDetailState } from './useApplicationDetailState'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailMiScopeFns {
  warnMiMissingPrimaryKey: (binding: {
    tableName?: string
    designerTableName?: string
    bindingId?: number | string
  }) => void
  alignMainSubTableBindingsOnly: () => void
  alignProcessSubTableBindingsBySharedTable: () => void
  getMiRows: () => any[]
  hasIncompleteMiRows: () => boolean
  hasCompletedMiRows: () => boolean
  hasTaskStatusData: (rows: any[]) => boolean
  refreshActiveMiSubProcessScopeFromBpmn: () => void
  filterRunningMiBindingsByProcessDesignScope: (bindings: ApplicationDetailState['subTableBindings']['value']) => void
  getCurrentFormFieldKeys: () => string[]
  hydrateCurrentFormDataFromCompletedSubTaskRows: () => void
}

export function createApplicationDetailMiScope(ctx: ApplicationDetailCtx): ApplicationDetailMiScopeFns {
  const {
    t,
    snapshotTaskName,
    isInitiatorMyRequestView,
    processInfo,
    bpmnXml,
    activeMiSubProcessScope,
    miMissingPrimaryKeyWarned,
    formFields,
    formTabs,
    formData,
    subTableBindings,
    previousForms,
    nodeFormMap,
  } = ctx

  function warnMiMissingPrimaryKey(binding: {
    tableName?: string
    designerTableName?: string
    bindingId?: number | string
  }) {
    const label = describeSubTableBindingLabel(binding)
    const key = label || 'unknown'
    if (miMissingPrimaryKeyWarned.has(key)) return
    miMissingPrimaryKeyWarned.add(key)
    ElMessage.error(t('task.miPrimaryKeyNotConfigured', { table: label || key }))
  }

  function alignMainSubTableBindingsOnly() {
    const main = subTableBindings.value as SubTableBindingAlignable[]
    if (main.length === 0) return
    ctx.applySharedAttachmentHydrationToAllBindings()
    applyUnionFindMergeToBindingList(main)
    enrichChildBindingRowsFromParentsNestedSubTables(subTableBindings.value)
    ctx.resyncMiDashboardFieldsFromVariablesOnBindings(main)
    ctx.hydrateMiLinkChildBindingsForInitiatorMyRequest()
    ctx.backfillSubTableBindingsFromVariables(main)
    ctx.applySharedAttachmentHydrationToAllBindings()
  }

  function alignProcessSubTableBindingsBySharedTable() {
    refreshActiveMiSubProcessScopeFromBpmn()
    // Binding 分类的表 id —— My Request 链路也必须注册，否则深层谓词拿不到 collection tableId
    // 就判不出 participant-child（To Do 在 FU loader 里注册，两条链路缺一不可）。
    registerMiKindTableIdsFromBindings(
      subTableBindings.value as never,
      (ctx.primaryTableBinding?.value as { tableId?: number | null } | undefined)?.tableId ?? null,
    )
    const nodeBindings: SubTableBindingAlignable[] = Array.from(nodeFormMap.value.values()).flatMap(
      info => info.subTableBindings as SubTableBindingAlignable[]
    )
    const all: SubTableBindingAlignable[] = [
      ...(subTableBindings.value as SubTableBindingAlignable[]),
      ...previousForms.value.flatMap(f => f.subTableBindings as SubTableBindingAlignable[]),
      ...nodeBindings
    ]
    if (all.length === 0) return

    ctx.applySharedAttachmentHydrationToAllBindings()
    applyUnionFindMergeToBindingList(all)

    ctx.backfillEmptySubTableBindingsFromVariables()
    enrichChildBindingRowsFromParentsNestedSubTables([
      ...subTableBindings.value,
      ...previousForms.value.flatMap(f => f.subTableBindings),
      ...Array.from(nodeFormMap.value.values()).flatMap(n => n.subTableBindings)
    ])
    ctx.resyncMiDashboardFieldsFromVariablesOnBindings(all)
    ctx.hydrateMiLinkChildBindingsForInitiatorMyRequest()
    filterRunningMiBindingsByProcessDesignScope(subTableBindings.value)
    for (const prevForm of previousForms.value) {
      filterRunningMiBindingsByProcessDesignScope(prevForm.subTableBindings as typeof subTableBindings.value)
    }
    for (const nodeForm of nodeFormMap.value.values()) {
      filterRunningMiBindingsByProcessDesignScope(nodeForm.subTableBindings as typeof subTableBindings.value)
    }
    ctx.applySharedAttachmentHydrationToAllBindings()
  }

  const getMiRows = (): any[] => [
    ...subTableBindings.value.flatMap(binding => binding.data || []),
    ...previousForms.value.flatMap(form => form.subTableBindings.flatMap(binding => binding.data || []))
  ]

  // 状态列名来自 Sub-Task Config（miTaskStatusField），无平台默认值：
  // 没配置就没有状态列可读，一律判定为"没有 MI 状态数据"。
  const hasIncompleteMiRows = (): boolean => {
    const sf = getActiveMiFieldNames().statusField
    if (!sf) return false
    const rows = getMiRows().filter((row: any) => row && row[sf] !== undefined)
    return rows.length > 0 && rows.some((row: any) => String(row[sf] || '').toUpperCase() !== 'COMPLETED')
  }

  const hasCompletedMiRows = (): boolean => {
    const sf = getActiveMiFieldNames().statusField
    if (!sf) return false
    const rows = getMiRows().filter((row: any) => row && row[sf] !== undefined)
    return rows.length > 0 && rows.every((row: any) => String(row[sf] || '').toUpperCase() === 'COMPLETED')
  }

  function hasTaskStatusData(rows: any[]): boolean {
    if (!Array.isArray(rows) || rows.length === 0) return false
    const sf = getActiveMiFieldNames().statusField
    if (!sf) return false
    if (snapshotTaskName) {
      return rows.some(r => r && r[sf] === 'COMPLETED')
    }
    return rows.some(r => r && r[sf] !== undefined)
  }

  function refreshActiveMiSubProcessScopeFromBpmn() {
    const xml = bpmnXml.value
    if (!xml) {
      activeMiSubProcessScope.value = null
      // 清掉上一个 FU 的配置，否则会泄漏到下一个（跨 FU 串配置和写死一样糟）
      setActiveMiConfig(null)
      return
    }
    activeMiSubProcessScope.value = resolveMiSubProcessScopeFromBpmn(xml, {
      userTaskName: snapshotTaskName || processInfo.value.currentNode || null,
    })
    // MI 列名的唯一真源：注册给深层纯函数隐式读取，避免 113 个调用点逐个传参
    setActiveMiConfig(activeMiSubProcessScope.value)
  }

  /**
   * Running MI subprocess on My Request: scope to the viewer's participant row using
   * Process Design subTableName + designer primary key (not hard-coded columns).
   * Initiators see the full case (all MI transaction rows + case attachments), not one participant slice.
   */
  function filterRunningMiBindingsByProcessDesignScope(bindings: typeof subTableBindings.value) {
    if (isInitiatorMyRequestView.value) return
    if (snapshotTaskName || processInfo.value.status !== 'RUNNING') return
    const scope = activeMiSubProcessScope.value
    if (!scope?.subTableName) return
    const viewerId = getPortalUserId()?.trim()
    if (!viewerId) return

    const collectionBinding = findBindingForMiSubTableName(bindings, scope.subTableName)
    if (!collectionBinding) return

    if (!hasConfiguredPrimaryKeyFields(collectionBinding.primaryKeyFields)) {
      warnMiMissingPrimaryKey(collectionBinding)
      return
    }

    const participantRowId = resolveViewerParticipantRowIdFromCollectionBinding(
      scope,
      collectionBinding,
      viewerId,
    )
    if (participantRowId == null) return

    filterBindingsToMiParticipantRow(bindings, scope, participantRowId)
  }

  function getCurrentFormFieldKeys(): string[] {
    return collectLeafFormFieldKeys(formFields.value, formTabs.value)
  }

  function hydrateCurrentFormDataFromCompletedSubTaskRows() {
    const formKeys = getCurrentFormFieldKeys()
    if (formKeys.length === 0) return

    const rows = [
      ...subTableBindings.value.flatMap(binding => binding.data || []),
      ...previousForms.value.flatMap(form => form.subTableBindings.flatMap(binding => binding.data || []))
    ]
    const viewerId = getPortalUserId()
    const completedRows = rows.filter((row: any) => row?.task_status === 'COMPLETED')
    const viewerRows = viewerId
      ? completedRows.filter((row: any) => row?.assignee_user_id === viewerId)
      : []
    const row = (viewerRows.length > 0 ? viewerRows : completedRows)[0]
    if (!row) return

    const nextData = { ...formData.value }
    for (const key of formKeys) {
      if (Object.prototype.hasOwnProperty.call(row, key)) {
        nextData[key] = row[key]
      }
    }
    formData.value = nextData
  }

  return {
    warnMiMissingPrimaryKey,
    alignMainSubTableBindingsOnly,
    alignProcessSubTableBindingsBySharedTable,
    getMiRows,
    hasIncompleteMiRows,
    hasCompletedMiRows,
    hasTaskStatusData,
    refreshActiveMiSubProcessScopeFromBpmn,
    filterRunningMiBindingsByProcessDesignScope,
    getCurrentFormFieldKeys,
    hydrateCurrentFormDataFromCompletedSubTaskRows,
  }
}
