/**
 * BI 看板分配管理业务逻辑 composable
 *
 * 封装 DashboardAssignment.vue 页面的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref, computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { logger } from '@/utils/logger'
import { notifyConfirm, notifyError, notifySuccess } from '@/utils/notify'
import { useBiManagementStore } from '@/stores/biManagement'
import { biManagementApi } from '@/api/biManagement'
import type {
  DashboardAssignmentResponse,
  AssignmentTargetType
} from '@/api/biManagement'
import { assignmentTargetTypeKey } from '@/utils/format'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'

const ACTIONS_COL_WIDTH = 140
const ASSIGNMENT_COL_WIDTHS: Record<string, number> = {
  dashboardTitle: 180,
  targetType: 130,
  targetName: 150,
  layoutMode: 130,
  displayOrder: 120,
  isDefault: 80,
}

export function useBiAssignment() {
  const { t } = useI18n()
  const store = useBiManagementStore()

  const ASSIGNMENT_TARGET_TYPES: AssignmentTargetType[] = ['USER', 'ROLE', 'BUSINESS_UNIT']

  const loading = ref(false)
  const query = reactive({
    targetType: undefined as AssignmentTargetType | undefined,
    dashboardTitle: '',
  })

  const grid = useAdminListGrid<DashboardAssignmentResponse>({
    storageKey: 'admin-list-layout:bi-assignments',
    extraWidth: ACTIONS_COL_WIDTH,
    defaultWidthOf: (field) => ASSIGNMENT_COL_WIDTHS[field] ?? 120,
  })

  const dialogVisible = ref(false)
  const dialogMode = ref<'create' | 'edit'>('create')
  const editingRow = ref<DashboardAssignmentResponse | null>(null)

  const loadAssignments = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const page = await biManagementApi.assignment.query({
        ...grid.buildQuery(),
        targetType: query.targetType || undefined,
        dashboardTitle: query.dashboardTitle || undefined,
      })
      if (!grid.isCurrentQuery(seq)) return
      grid.applyPage(page, 'bi/assignments/query response is missing its column declaration')
    } catch (error) {
      if (!grid.isCurrentQuery(seq)) return
      logger.error('biAssignment', 'assignment query failed', error)
      notifyError(t('bi.assignment.queryFailed'))
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  const handleSearch = () => {
    void loadAssignments()
  }

  const handleReset = () => {
    query.targetType = undefined
    query.dashboardTitle = ''
    void loadAssignments()
  }

  const targetTypeFilterOptions = computed(() =>
    ASSIGNMENT_TARGET_TYPES.map((value) => ({
      value,
      label: t(assignmentTargetTypeKey(value))
    }))
  )

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

  const handleDelete = async (row: DashboardAssignmentResponse) => {
    try {
      await notifyConfirm(
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
      notifySuccess(t('bi.assignment.deleteSuccess'))
      handleSearch()
    } catch (error) {
      if (error !== 'cancel') {
        logger.error('biAssignment', 'assignment delete failed', error)
        notifyError(t(errorTranslator(AppErrorCode.BI_ASSIGNMENT_DELETE_FAILED)))
      }
    }
  }

  return {
    loading,
    query,
    dialogVisible,
    dialogMode,
    editingRow,
    targetTypeFilterOptions,
    handleSearch,
    handleReset,
    loadAssignments,
    showCreateDialog,
    showEditDialog,
    handleDelete,
    ACTIONS_COL_WIDTH,
    ...grid,
  }
}
