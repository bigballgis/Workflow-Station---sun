import { ref, type ComputedRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { functionUnitApi, type TableBinding, type BindingType } from '@/api/functionUnit'

interface UseTableBindingListOptions {
  functionUnitId: number
  getFormId: () => number
  t: (key: string, params?: Record<string, unknown>) => string
  emitUpdate: () => void
  /** PRIMARY binding is only mandatory (and thus non-deletable) for PROCESS / TASK forms. */
  restrictPrimarySubOnly: ComputedRef<boolean>
}

/**
 * Table-binding 列表状态与展示/删除逻辑：
 * 加载已绑定的表、表名/类型标签、删除绑定。
 */
export function useTableBindingList(options: UseTableBindingListOptions) {
  const { functionUnitId, getFormId, t, emitUpdate, restrictPrimarySubOnly } = options

  const loading = ref(false)
  const bindings = ref<TableBinding[]>([])

  // Load bindings
  async function loadBindings() {
    loading.value = true
    try {
      const res = await functionUnitApi.getFormBindings(functionUnitId, getFormId())
      bindings.value = res.data || []
    } catch (e: any) {
      console.error('Failed to load bindings:', e)
      bindings.value = []
    } finally {
      loading.value = false
    }
  }

  // Get table name by ID
  function getTableName(tables: { id: number | string; tableDisplayName?: string; tableName?: string }[], tableId: number, fallback?: string): string {
    const table = tables.find(t => t.id === tableId)
    return table?.tableDisplayName || table?.tableName || fallback || t('tableBinding.unknownTable')
  }

  // Binding type label
  function bindingTypeLabel(type: BindingType): string {
    const map: Record<BindingType, string> = {
      PRIMARY: t('tableBinding.primaryTable'),
      SUB: t('tableBinding.subTable'),
      RELATED: t('tableBinding.relatedTable'),
      ACTION: t('tableBinding.actionBindingTable')
    }
    return map[type] || type
  }

  // Binding type tag color
  function bindingTypeTag(type: BindingType): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
    const map: Record<BindingType, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { PRIMARY: 'primary', SUB: 'success', RELATED: 'warning', ACTION: 'danger' }
    return map[type] || 'info'
  }

  // Table type label
  function tableTypeLabel(type: string): string {
    const map: Record<string, string> = {
      MAIN: t('tableBinding.mainTableType'),
      SUB: t('tableBinding.subTableType'),
      ACTION: t('tableBinding.actionTableType'),
      RELATION: t('tableBinding.relationTableType')
    }
    // Object.hasOwn guards against prototype-chain hits (a type named "toString"
    // would otherwise return the inherited function instead of falling through).
    return Object.hasOwn(map, type) ? map[type] : type
  }

  // Delete binding
  async function handleDelete(binding: TableBinding) {
    if (binding.bindingType === 'PRIMARY' && restrictPrimarySubOnly.value) {
      ElMessage.warning(t('tableBinding.cannotDeletePrimary'))
      return
    }

    await ElMessageBox.confirm(t('tableBinding.deleteConfirm'), t('tableBinding.confirmTitle'), { type: 'warning' })

    try {
      await functionUnitApi.deleteFormBinding(functionUnitId, getFormId(), binding.id!)
      ElMessage.success(t('tableBinding.deleteSuccess'))
      loadBindings()
      emitUpdate()
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('tableBinding.deleteFailed'))
    }
  }

  return {
    loading,
    bindings,
    loadBindings,
    getTableName,
    bindingTypeLabel,
    bindingTypeTag,
    tableTypeLabel,
    handleDelete,
  }
}
