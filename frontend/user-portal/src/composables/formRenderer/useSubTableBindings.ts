import { computed } from 'vue'
import { resolveAssigneeFieldForBinding } from '../../utils/subTableAssignment'
import { legacyBindingIdAliases } from '../../components/formRendererHelpers'
import { findMiIsolatedParentRow } from '../tasks/shared'
import { resolveSubTablePrimaryKeyFields } from '../tasks/useMiConfig'
import type { FormField } from '../../components/formRendererHelpers'
import type { BindingFieldDefinition } from '../../utils/subTableRowRuntime'
import type { AssignmentConfig } from '../../utils/miAssignmentConfig'

// SubTableBinding shape is intentionally loose here (mirrors FormRenderer.vue local interface).
export interface SubTableBinding {
  bindingId: number
  tableId?: number | null
  bindingType: string
  bindingMode: string
  tableName: string
  physicalTableName?: string
  /** 关联表标识：决定 __subTables__ 走 rt: 还是 dw: 命名空间 */
  relationTableId?: number | null
  relationTableName?: string | null
  tableType: string
  tableDescription: string
  columns: any[]
  /** Form-design canvas columns for Add/Edit dialog; list view may include extra audit columns. */
  dialogColumns?: any[]
  data: any[]
  formFields?: FormField[]
  formOptions?: Record<string, any>
  primaryKeyFields?: string[]
  fieldDefinitions?: BindingFieldDefinition[]
  bindingLinkMode?: string
  foreignKeyField?: string | null
  assignmentConfig?: AssignmentConfig
}

interface PrimaryTableBinding {
  tableId?: number | null
  tableName?: string
  fieldDefinitions?: SubTableBinding['fieldDefinitions']
}

interface SubTableBindingsDeps {
  subTableBindings: () => SubTableBinding[] | undefined
  linkedSubTableBindings: () => SubTableBinding[] | undefined
  primaryTableBinding: () => PrimaryTableBinding | undefined
  readonly: () => boolean
  allowSubTableAssign: () => boolean | undefined
  taskId: () => string | undefined
  currentMiRowId: () => number | string | null | undefined
}

export function useSubTableBindings(deps: SubTableBindingsDeps) {
  const bindingMap = computed(() => {
    const map = new Map<number, SubTableBinding>()
    for (const b of (deps.subTableBindings() ?? [])) {
      for (const alias of legacyBindingIdAliases(b.bindingId)) {
        if (!map.has(alias)) map.set(alias, b)
      }
    }
    return map
  })
  const linkableSubTableBindings = computed(() => deps.linkedSubTableBindings() ?? deps.subTableBindings())

  const primaryTableDisplayName = computed(() => deps.primaryTableBinding()?.tableName ?? '')

  const primaryTableId = computed(() => deps.primaryTableBinding()?.tableId ?? null)

  const parentTablesById = computed(() => {
    const out: Record<number, { fieldDefinitions: NonNullable<SubTableBinding['fieldDefinitions']> }> = {}
    const primary = deps.primaryTableBinding()
    if (primary?.tableId != null && primary.fieldDefinitions?.length) {
      out[Number(primary.tableId)] = { fieldDefinitions: primary.fieldDefinitions }
    }
    for (const b of deps.subTableBindings() ?? []) {
      if (b.tableId == null || !b.fieldDefinitions?.length) continue
      if (b.bindingType !== 'PRIMARY' && b.bindingType !== 'SUB') continue
      out[Number(b.tableId)] = { fieldDefinitions: b.fieldDefinitions }
    }
    return out
  })

  const subTableBindingsForContext = computed(() => {
    const list: Array<{ tableId?: number | null; bindingType?: string; tableName?: string }> = [
      ...(deps.subTableBindings() ?? []),
    ]
    const primary = deps.primaryTableBinding()
    if (primary?.tableId != null) {
      list.unshift({
        tableId: primary.tableId,
        bindingType: 'PRIMARY',
        tableName: primary.tableName,
      })
    }
    return list
  })
  const resolveBinding = (id?: number) => {
    if (id == null || !Number.isFinite(Number(id))) return undefined
    const direct = bindingMap.value.get(Number(id))
    if (direct) return direct
    for (const alias of legacyBindingIdAliases(id)) {
      const hit = bindingMap.value.get(alias)
      if (hit) return hit
    }
    for (const b of deps.subTableBindings() ?? []) {
      if (legacyBindingIdAliases(b.bindingId).includes(Number(id))) return b
    }
    return undefined
  }

  function isBindingModeEditable(bindingMode: string | undefined | null): boolean {
    return String(bindingMode ?? '').trim().toUpperCase() === 'EDITABLE'
  }

  /** Sub-table CRUD follows developer-workstation table binding mode; whole-form readonly wins via {@link Props.readonly}. */
  function isSubTableEditable(bindingId?: number): boolean {
    const binding = resolveBinding(bindingId)
    if (!binding || deps.readonly()) return false
    return isBindingModeEditable(binding.bindingMode)
  }

  function subTableAssigneeField(bindingId?: number): string | undefined {
    const b = resolveBinding(bindingId)
    if (!b) return undefined
    return resolveAssigneeFieldForBinding(b as never)
  }

  /** MI To Do: seed link-child / attachment Add dialog with the active collection row (e.g. row_id). */
  function resolveMiParticipantSeedForSubTableAdd(bindingId?: number): {
    rowId: string | number | null
    parentRow: Record<string, unknown> | null
    parentTableId: number | null
  } {
    const rowId = deps.currentMiRowId()
    if (rowId == null || String(rowId).trim() === '') {
      return { rowId: null, parentRow: null, parentTableId: null }
    }
    const peers = deps.subTableBindings() ?? []
    for (const b of peers) {
      if (bindingId != null && b.bindingId === bindingId) continue
      const rows = Array.isArray(b.data) ? b.data : []
      // 按表的种类解析主键：子任务表缺主键 = 配置错误（抛错）；其它表允许没有主键。
      const parent = findMiIsolatedParentRow(rows, rowId, resolveSubTablePrimaryKeyFields(b))
      if (parent) {
        return {
          rowId,
          parentRow: parent as Record<string, unknown>,
          parentTableId: b.tableId != null && Number.isFinite(Number(b.tableId)) ? Number(b.tableId) : null,
        }
      }
    }
    return { rowId, parentRow: { row_id: rowId } as Record<string, unknown>, parentTableId: null }
  }

  function showSubTableAssignColumn(bindingId?: number): boolean {
    if (deps.allowSubTableAssign() === false) {
      return false
    }
    return !!(deps.taskId() && subTableAssigneeField(bindingId))
  }

  return {
    bindingMap,
    linkableSubTableBindings,
    primaryTableDisplayName,
    primaryTableId,
    parentTablesById,
    subTableBindingsForContext,
    resolveBinding,
    isBindingModeEditable,
    isSubTableEditable,
    subTableAssigneeField,
    resolveMiParticipantSeedForSubTableAdd,
    showSubTableAssignColumn,
  }
}
