/**
 * 关联表结构业务逻辑 composable
 *
 * 封装 relation-table/structure/index.vue 页面的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRelationTableStore } from '@/stores/relationTable'
import type { RelationTableResponse } from '@/api/relationTable'

export function useRelationTable() {
  const router = useRouter()
  const store = useRelationTableStore()
  const { tableList, loading } = storeToRefs(store)

  // ==================== State ====================

  const enableLoadingMap = ref<Record<number, boolean>>({})
  const portalLoadingMap = ref<Record<number, boolean>>({})
  const currentTable = ref<RelationTableResponse | null>(null)
  const showVersionDialog = ref(false)
  const showAccessDialog = ref(false)
  const showCompareDialog = ref(false)

  // ==================== Data Fetching ====================

  const fetchTableList = async () => {
    await store.fetchTableList()
  }

  // ==================== Toggle Actions ====================

  const handleToggleEnabled = async (row: RelationTableResponse, val: boolean) => {
    enableLoadingMap.value = { ...enableLoadingMap.value, [row.id]: true }
    try {
      await store.setEnabled(row.id, val)
      ElMessage.success(val ? 'Enabled' : 'Disabled')
    } catch {
      row.enabled = !val
    } finally {
      enableLoadingMap.value = { ...enableLoadingMap.value, [row.id]: false }
    }
  }

  const handleTogglePortalVisibility = async (row: RelationTableResponse, val: boolean) => {
    portalLoadingMap.value = { ...portalLoadingMap.value, [row.id]: true }
    try {
      await store.setPortalVisibility(row.id, val)
      ElMessage.success(val ? 'Portal visible' : 'Portal hidden')
    } catch {
      row.portalVisible = !val
    } finally {
      portalLoadingMap.value = { ...portalLoadingMap.value, [row.id]: false }
    }
  }

  // ==================== Dialog Openers ====================

  const handleAccess = (row: RelationTableResponse) => {
    currentTable.value = row
    showAccessDialog.value = true
  }

  const handleVersions = (row: RelationTableResponse) => {
    currentTable.value = row
    showVersionDialog.value = true
  }

  const handleCompare = (row: RelationTableResponse) => {
    currentTable.value = row
    showCompareDialog.value = true
  }

  // ==================== Edit (router push) ====================

  const handleEdit = (row: RelationTableResponse) => {
    router.push(`/relation-tables/structure/${row.id}/edit`)
  }

  // ==================== Deploy ====================

  const handleDeploy = async (row: RelationTableResponse) => {
    try {
      await ElMessageBox.confirm(
        `Deploy table "${row.tableName}" to database?`,
        'Confirm Deploy',
        { type: 'warning' }
      )
      await store.deployTable(row.id)
      ElMessage.success('Deployed successfully')
      store.fetchTableList()
    } catch (e: any) {
      if (e !== 'cancel') {
        console.error('Deploy failed:', e)
      }
    }
  }

  // ==================== Delete ====================

  const handleDelete = async (row: RelationTableResponse) => {
    try {
      await ElMessageBox.confirm(
        `Delete table "${row.tableName}"? This action cannot be undone.`,
        'Confirm Delete',
        { type: 'warning' }
      )
      await store.deleteTable(row.id)
      ElMessage.success('Deleted successfully')
      store.fetchTableList()
    } catch (e: any) {
      if (e !== 'cancel') {
        console.error('Delete failed:', e)
      }
    }
  }

  // ==================== Rollback ====================

  const handleRollback = (row: RelationTableResponse) => {
    currentTable.value = row
    showVersionDialog.value = true
  }

  // ==================== Return ====================

  return {
    // State
    loading,
    tableList,
    enableLoadingMap,
    portalLoadingMap,
    currentTable,
    showVersionDialog,
    showAccessDialog,
    showCompareDialog,
    // Methods
    fetchTableList,
    handleToggleEnabled,
    handleTogglePortalVisibility,
    handleDeploy,
    handleDelete,
    handleAccess,
    handleVersions,
    handleEdit,
    handleRollback,
    handleCompare,
  }
}
