/**
 * BI 看板分配管理业务逻辑 composable
 *
 * 封装 DashboardAssignment.vue 页面的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useBiManagementStore } from '@/stores/biManagement'
import { usePagination } from '@/composables/usePagination'
import type {
  DashboardAssignmentResponse,
  AssignmentTargetType
} from '@/api/biManagement'
import { assignmentTargetTypeKey } from '@/utils/format'

export function useBiAssignment() {
  const { t } = useI18n()
  const store = useBiManagementStore()

  // ==================== Constants ====================

  const ASSIGNMENT_TARGET_TYPES: AssignmentTargetType[] = ['USER', 'ROLE', 'BUSINESS_UNIT']

  // ==================== Dialog State ====================

  const dialogVisible = ref(false)
  const dialogMode = ref<'create' | 'edit'>('create')
  const editingRow = ref<DashboardAssignmentResponse | null>(null)

  // ==================== Data Fetching (usePagination) ====================

  const fetchFn = async (params: any) => {
    await store.fetchAssignments(params)
    return { content: store.assignments, totalElements: store.total }
  }

  const { data: assignments, total, loading, query, handleSearch, handleReset } = usePagination(
    fetchFn,
    { targetType: undefined, dashboardTitle: '' }
  )

  // ==================== Computed ====================

  const targetTypeFilterOptions = computed(() =>
    ASSIGNMENT_TARGET_TYPES.map((value) => ({
      value,
      label: t(assignmentTargetTypeKey(value))
    }))
  )

  // ==================== Dialog Actions ====================

  const showCreateDialog = () => {
    dialogMode.value = 'create'
    editingRow.value = null
    dialogVisible.value = true
  }

  const showEditDialog = (row: DashboardAssignmentResponse) => {
    dialogMode.value = 'edit'
    editingRow.value = row
    dialogVisible.value = true
  }

  // ==================== Delete ====================

  const handleDelete = async (row: DashboardAssignmentResponse) => {
    try {
      await ElMessageBox.confirm(
        t('bi.assignment.deleteConfirm', { title: row.dashboardTitle, target: row.targetName }),
        t('bi.assignment.deleteConfirmTitle'),
        {
          type: 'warning',
          confirmButtonText: t('common.delete'),
          cancelButtonText: t('common.cancel'),
          confirmButtonClass: 'el-button--danger'
        }
      )
      await store.deleteAssignment(row.id)
      ElMessage.success(t('bi.assignment.deleteSuccess'))
      handleSearch()
    } catch (error) {
      if (error !== 'cancel') {
        console.error('assignment delete failed', error)
        ElMessage.error(t('bi.assignment.deleteFailed'))
      }
    }
  }

  // ==================== Return ====================

  return {
    // State (from usePagination)
    loading,
    assignments,
    total,
    query,
    dialogVisible,
    dialogMode,
    editingRow,
    // Computed
    targetTypeFilterOptions,
    // Methods
    handleSearch,
    handleReset,
    showCreateDialog,
    showEditDialog,
    handleDelete,
  }
}
